package com.adrinand.gokfre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.adrinand.gokfre.core.hotkey.GlobalHotkeyProvider
import com.adrinand.gokfre.core.hotkey.Hotkey
import com.adrinand.gokfre.core.hotkey.InputDevicePermissionChecker
import com.adrinand.gokfre.core.hotkey.JNativeHookProvider
import com.adrinand.gokfre.core.hotkey.LinuxEvdevHotkeyProvider
import com.adrinand.gokfre.core.hotkey.toDisplayString
import com.adrinand.gokfre.core.screen.isAccessibilityTrusted
import com.adrinand.gokfre.core.screen.isLinux
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.combine
import java.awt.GraphicsEnvironment
import java.util.logging.Logger
import javax.swing.SwingUtilities

private val logger = Logger.getLogger("com.adrinand.gokfre.ui.GlobalHotkeyRegistration")

@Composable
fun globalHotkeyRegistration(
    settingsViewModel: SettingsViewModel,
    onPermissionMissing: () -> Unit,
    onToggle: () -> Unit,
) {
    var hotkeyProvider by remember { mutableStateOf<GlobalHotkeyProvider?>(null) }

    LaunchedEffect(Unit) {
        var previousHotkey = settingsViewModel.toggleHotkey
        var registeredHotkey: Hotkey? = null
        combine(
            snapshotFlow { settingsViewModel.toggleHotkey },
            snapshotFlow { settingsViewModel.isHotkeyCaptureActive },
        ) { hotkey, capturing -> hotkey to capturing }
            .collect { (newHotkey, capturing) ->
                if (hotkeyProvider == null && !capturing) {
                    hotkeyProvider = createGlobalHotkeyProvider(onPermissionMissing)
                }
                val provider = hotkeyProvider
                registeredHotkey?.let { hotkey -> provider?.unregister(hotkey) }
                registeredHotkey = null
                when {
                    provider == null -> if (!capturing) previousHotkey = newHotkey
                    capturing -> Unit
                    else -> {
                        previousHotkey =
                            registerHotkeySafely(
                                provider = provider,
                                hotkey = newHotkey,
                                previousHotkey = previousHotkey,
                                onToggle = onToggle,
                                viewModel = settingsViewModel,
                            )
                        if (previousHotkey == newHotkey) {
                            registeredHotkey = newHotkey
                        }
                    }
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            hotkeyProvider?.dispose()
            logger.info { "Global hotkey provider disposed" }
        }
    }
}

private fun registerHotkeySafely(
    provider: GlobalHotkeyProvider,
    hotkey: Hotkey,
    previousHotkey: Hotkey,
    onToggle: () -> Unit,
    viewModel: SettingsViewModel,
): Hotkey =
    try {
        provider.register(hotkey) { SwingUtilities.invokeLater { onToggle() } }
        viewModel.registrationError = null
        logger.info { "Registered hotkey: $hotkey" }
        hotkey
    } catch (e: IllegalStateException) {
        logger.warning("Failed to register hotkey $hotkey: ${e.message}")
        viewModel.registrationError = "Failed to register hotkey ${hotkey.toDisplayString()}"
        if (hotkey != previousHotkey) {
            viewModel.toggleHotkey = previousHotkey
        }
        previousHotkey
    }

private fun createGlobalHotkeyProvider(onPermissionMissing: () -> Unit): GlobalHotkeyProvider? =
    when {
        GraphicsEnvironment.isHeadless() -> null
        isLinux() -> createLinuxProvider(onPermissionMissing)
        else -> createMacProvider(onPermissionMissing)
    }

private fun createMacProvider(onPermissionMissing: () -> Unit): GlobalHotkeyProvider? {
    if (!isAccessibilityTrusted()) {
        logger.warning("macOS accessibility permission not granted, skipping global hotkey registration")
        onPermissionMissing()
        return null
    }
    return runCatching { JNativeHookProvider() }
        .onSuccess { logger.info { "Global hotkey provider initialized (JNativeHook)" } }
        .onFailure {
            logger.warning("Failed to initialize JNativeHook hotkey provider: ${it.message}")
            onPermissionMissing()
        }
        .getOrNull()
}

private fun createLinuxProvider(onPermissionMissing: () -> Unit): GlobalHotkeyProvider? =
    try {
        if (!InputDevicePermissionChecker.hasInputDeviceAccess()) {
            onPermissionMissing()
        }
        LinuxEvdevHotkeyProvider()
    } catch (e: IllegalStateException) {
        logger.warning("Failed to initialize Linux evdev hotkey provider: ${e.message}")
        onPermissionMissing()
        null
    } catch (e: UnsatisfiedLinkError) {
        logger.warning("Failed to load Linux evdev hotkey native library: ${e.message}")
        onPermissionMissing()
        null
    }
