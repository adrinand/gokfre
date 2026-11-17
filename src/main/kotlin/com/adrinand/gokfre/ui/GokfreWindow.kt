package com.adrinand.gokfre.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import com.adrinand.gokfre.core.hotkey.getDisplayCharFromKeyEvent
import com.adrinand.gokfre.core.screen.ArrangementController
import com.adrinand.gokfre.core.screen.focusAppWindow
import com.adrinand.gokfre.core.screen.isLinux
import com.adrinand.gokfre.core.screen.isX11Session
import com.adrinand.gokfre.core.screen.skipTaskbarX11
import com.adrinand.gokfre.core.screen.workAreaBounds
import com.adrinand.gokfre.viewmodel.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getLogger("com.adrinand.gokfre.ui.GokfreWindow")

const val PREVIEW_WINDOW_TITLE = "Gokfre Preview"

private const val SHOW_POLL_INTERVAL_MS = 10L
private const val MAP_WATCH_INTERVAL_MS = 5L
private const val MAX_SHOW_WAIT_MS = 500L
private const val ACTIVATE_WAIT_TIMEOUT_MS = 250L
private const val ACTIVATE_WAIT_POLL_MS = 10L
private const val PREPARE_WAIT_TIMEOUT_MS = 500L
private const val FULLSCREEN_WAIT_TIMEOUT_MS = 2_000L
private const val FADE_IN_MS = 50
private const val FADE_OUT_MS = 60
private const val MAX_SELECTED_KEYS = 2
private const val NANOS_PER_MILLISECOND = 1_000_000L

@Suppress("TooGenericExceptionCaught")
@Composable
fun GokfreWindow(
    visible: Boolean,
    onClose: () -> Unit,
    viewModel: SettingsViewModel,
    arrangementController: ArrangementController,
) {
    var composeWindow by remember { mutableStateOf<ComposeWindow?>(null) }
    var mapped by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(previewReady) {
        if (previewReady) {
            alphaAnim.snapTo(0f)
            alphaAnim.animateTo(1f, tween(durationMillis = FADE_IN_MS))
        } else {
            alphaAnim.animateTo(0f, tween(durationMillis = FADE_OUT_MS))
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            selectedKeys = emptyList()
        }
    }

    fun onKeyPressed(label: String) {
        val layout = viewModel.layoutSettings
        val allLabels = layout.keyLabels.flatten().map { it.uppercase() }
        if (label.uppercase() !in allLabels) {
            logger.info { "onKeyPressed: '$label' is not a layout label" }
            return
        }

        selectedKeys = (selectedKeys + label).takeLast(MAX_SELECTED_KEYS)
        logger.info { "onKeyPressed: selectedKeys=$selectedKeys" }
        if (selectedKeys.size == MAX_SELECTED_KEYS) {
            val win = composeWindow ?: return
            val workArea = arrangementController.targetWorkArea ?: win.workAreaBounds()
            val keysToArrange = selectedKeys
            logger.info { "onKeyPressed: arranging then closing preview" }
            coroutineScope.launch {
                val bounds =
                    arrangementController.arrange(
                        rowWeights = layout.rowWeights.toList(),
                        columnWeights = layout.columnWeights.toList(),
                        keyLabels = layout.keyLabels.map { row -> row.toList() },
                        selectedKeys = keysToArrange,
                        workArea = workArea,
                    )
                logger.info { "onKeyPressed: arrange returned $bounds, closing preview" }
                onClose()
            }
        }
    }

    Window(
        visible = mapped,
        onCloseRequest = onClose,
        undecorated = true,
        transparent = true,
        title = PREVIEW_WINDOW_TITLE,
        focusable = true,
        alwaysOnTop = mapped,
    ) {
        LaunchedEffect(Unit) {
            logger.info { "GokfreWindow content composed, window=$window, visible=$visible" }
            composeWindow = window

            window.addWindowFocusListener(
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent) {
                        logger.info { "Preview focus: gained, re-asserting Compose focus" }
                        coroutineScope.launch {
                            runCatching { focusRequester.requestFocus() }
                                .onFailure { logger.warning { "re-request focus failed: $it" } }
                        }
                    }
                },
            )

            if (isLinux() && isX11Session()) {
                window.skipTaskbarX11()
            }

            window.bounds = arrangementController.targetWorkArea ?: window.workAreaBounds()
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(alphaAnim.value)
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                        if (keyEvent.key == Key.Escape) {
                            onClose()
                            return@onKeyEvent true
                        }

                        if (keyEvent.key == Key.Backspace) {
                            selectedKeys = emptyList()
                            return@onKeyEvent true
                        }

                        keyEvent.getDisplayCharFromKeyEvent()?.let { label ->
                            onKeyPressed(label)
                            return@onKeyEvent true
                        }
                        false
                    }
                    .focusable(),
        ) {
            LayoutPreviewScreen(viewModel, selectedKeys = selectedKeys.toSet())
        }
    }

    LaunchedEffect(visible, composeWindow) {
        val win = composeWindow
        if (win == null) {
            logger.info { "LaunchedEffect: composeWindow is null, skipping (visible=$visible)" }
            return@LaunchedEffect
        }

        if (!visible) {
            logger.info { "LaunchedEffect: hiding preview" }
            previewReady = false
            mapped = false
            return@LaunchedEffect
        }

        val showStartedAt = System.nanoTime()
        logger.info {
            "LaunchedEffect: showing preview, isVisible=${win.isVisible}, isShowing=${win.isShowing}"
        }
        val manager = arrangementController.windowManager
        try {
            if (manager.previewRequiresMappedWindow) {
                // Capture before mapping so the preview never becomes the target.
                captureTarget(arrangementController)
                val capturedAt = elapsedMs(showStartedAt)
                val workArea = arrangementController.targetWorkArea ?: win.workAreaBounds()
                var mappedAt = -1L
                val mapWatcher =
                    launch {
                        while (!win.isShowing) {
                            delay(MAP_WATCH_INTERVAL_MS)
                        }
                        mappedAt = elapsedMs(showStartedAt)
                        logger.info { "Preview show: mapped=${mappedAt}ms" }
                    }
                mapped = true
                val placed =
                    try {
                        manager.showPreview(win, workArea, FULLSCREEN_WAIT_TIMEOUT_MS)
                    } finally {
                        mapWatcher.cancel()
                    }
                logger.info {
                    "Preview show: captured=${capturedAt}ms, mapped=${mappedAt}ms, " +
                        "placed=${elapsedMs(showStartedAt)}ms, result=$placed"
                }
                if (!placed) {
                    logger.warning { "Could not place the preview; hiding it" }
                    mapped = false
                    onClose()
                    return@LaunchedEffect
                }
            } else {
                captureTarget(arrangementController)
                val capturedAt = elapsedMs(showStartedAt)
                val workArea = arrangementController.targetWorkArea ?: win.workAreaBounds()
                val prepared =
                    try {
                        manager.preparePreview(win, workArea, PREPARE_WAIT_TIMEOUT_MS)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warning { "Could not prepare preview: $e" }
                        false
                    }
                mapped = true
                awaitShowing(win)
                val mappedAt = elapsedMs(showStartedAt)
                val onTarget =
                    runCatching { win.workAreaBounds().intersects(workArea) }.getOrDefault(false)
                if (!prepared || !onTarget) {
                    manager.placePreview(win, workArea, FULLSCREEN_WAIT_TIMEOUT_MS)
                }
                if (!isLinux()) {
                    focusAppWindow()
                }
                manager.focusPreview(win)
                logger.info {
                    "Preview show: captured=${capturedAt}ms, mapped=${mappedAt}ms, " +
                        "placed=${elapsedMs(showStartedAt)}ms"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warning { "Preview placement failed: $e" }
        }

        awaitActive(win)
        previewReady = true
        logger.info { "Preview show: revealed=${elapsedMs(showStartedAt)}ms" }
        runCatching {
            logger.info {
                "Preview state: visible=${win.isVisible}, showing=${win.isShowing}, " +
                    "active=${win.isActive}, opacity=${win.opacity}, bounds=${win.bounds}"
            }
        }.onFailure { logger.warning { "Could not read preview state: $it" } }
        runCatching { win.toFront() }
            .onFailure { logger.warning { "Could not raise the preview: $it" } }
        runCatching { focusRequester.requestFocus() }
            .onFailure { logger.warning { "re-request focus failed: $it" } }
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun captureTarget(controller: ArrangementController) {
    try {
        withContext(Dispatchers.IO) { controller.captureTargetWindow() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.warning { "Could not capture the target window: $e" }
    }
}

private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

private suspend fun awaitActive(win: Window) {
    val deadline = System.nanoTime() + ACTIVATE_WAIT_TIMEOUT_MS * NANOS_PER_MILLISECOND
    while (!win.isActive && System.nanoTime() < deadline) {
        delay(ACTIVATE_WAIT_POLL_MS.milliseconds)
    }
    if (!win.isActive) {
        logger.warning { "Preview window did not become active before reveal" }
    }
}

private suspend fun awaitShowing(win: Window) {
    var waitedMs = 0L
    while (!win.isShowing && waitedMs < MAX_SHOW_WAIT_MS) {
        delay(SHOW_POLL_INTERVAL_MS.milliseconds)
        waitedMs += SHOW_POLL_INTERVAL_MS
    }
    if (!win.isShowing) {
        logger.warning { "Preview window did not report isShowing after $waitedMs ms, continuing anyway" }
    }
}

internal fun Window.setOpacitySafely(value: Float) {
    runCatching { opacity = value }
        .onFailure { logger.warning { "Could not set opacity to $value: $it" } }
}
