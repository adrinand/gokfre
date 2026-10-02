package com.adrinand.kofre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.adrinand.kofre.core.hotkey.GlobalHotkeyProvider
import com.adrinand.kofre.core.hotkey.Hotkey
import com.adrinand.kofre.core.hotkey.InputDevicePermissionChecker
import com.adrinand.kofre.core.hotkey.JNativeHookProvider
import com.adrinand.kofre.core.hotkey.LinuxEvdevHotkeyProvider
import com.adrinand.kofre.core.hotkey.toDisplayString
import com.adrinand.kofre.core.screen.isLinux
import com.adrinand.kofre.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.combine
import java.awt.GraphicsEnvironment
import java.util.logging.Logger
import javax.swing.SwingUtilities

private val logger = Logger.getLogger("com.adrinand.kofre.ui.GlobalHotkeyRegistration")

@Composable
fun globalHotkeyRegistration(
    settingsViewModel: SettingsViewModel,
    onPermissionMissing: () -> Unit,
    onToggle: () -> Unit,
) {
    var hotkeyProvider by remember { mutableStateOf<GlobalHotkeyProvider?>(null) }

    LaunchedEffect(Unit) {
        var previousHotkey = settingsViewModel.toggleHotkey
        combine(
            snapshotFlow { settingsViewModel.toggleHotkey },
            snapshotFlow { settingsViewModel.isHotkeyCaptureActive },
        ) { hotkey, capturing -> hotkey to capturing }
            .collect { (newHotkey, capturing) ->
                hotkeyProvider?.dispose()
                hotkeyProvider =
                    if (capturing) {
                        null
                    } else {
                        val provider = createGlobalHotkeyProvider(onPermissionMissing)
                        if (provider != null) {
                            previousHotkey =
                                registerHotkeySafely(
                                    provider = provider,
                                    hotkey = newHotkey,
                                    previousHotkey = previousHotkey,
                                    onToggle = onToggle,
                                    viewModel = settingsViewModel,
                                )
                        } else {
                            previousHotkey = newHotkey
                        }
                        provider
                    }
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
        else -> JNativeHookProvider()
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
