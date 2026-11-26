package com.adrinand.gokfre.core.screen

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger
import kotlin.time.TimeSource

private const val PREVIEW_TIMEOUT_MS = 2_000L

/**
 * Base class for [WindowManager] implementations that manage a single backend
 * handle subtype.
 *
 * Subclasses implement the backend-specific logic with their concrete
 * [WindowHandle] type. The base class centralises the cast and the warning
 * logged when an incompatible handle is passed in.
 */
@Suppress("TooManyFunctions")
abstract class SingleBackendWindowManager<H : WindowHandle> : WindowManager {
    protected abstract val logger: Logger

    private var lastPreviewWorkArea: Rectangle? = null

    /**
     * Casts [window] to this backend's handle type, or returns `null` when the
     * handle belongs to a different backend.
     */
    protected abstract fun castHandle(window: WindowHandle): H?

    /**
     * Returns the currently active window as this backend's handle type.
     */
    protected abstract fun retrieveActiveWindow(): H?

    /**
     * Returns the current bounds of [handle].
     */
    protected abstract fun retrieveBounds(handle: H): Rectangle?

    /**
     * Moves and resizes [handle] to [bounds].
     */
    protected abstract fun applyBounds(
        handle: H,
        bounds: Rectangle,
    )

    /**
     * Requests that [handle] become the active/focused window.
     */
    protected abstract fun focusHandle(handle: H)

    final override fun getActiveWindowId(): WindowHandle? = retrieveActiveWindow()

    final override fun getWindowBounds(window: WindowHandle): Rectangle? {
        val handle = castHandle(window) ?: return null
        return retrieveBounds(handle)
    }

    final override fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    ) {
        val handle = castHandle(window)
        if (handle == null) {
            logger.warning { "Ignoring setWindowBounds for incompatible handle: $window" }
            return
        }
        applyBounds(handle, bounds)
    }

    final override fun focusWindow(window: WindowHandle) {
        val handle = castHandle(window)
        if (handle == null) {
            logger.warning { "Ignoring focusWindow for incompatible handle: $window" }
            return
        }
        focusHandle(handle)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun showAndFocusPreview(
        window: Window,
        targetWorkArea: Rectangle?,
        scope: CoroutineScope,
    ) {
        placePreviewForShow(window, targetWorkArea, scope)
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            try {
                focusPreview(window)
                logger.info { "Preview focus elapsedMs=${started.elapsedNow().inWholeMilliseconds}" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warning { "Preview focus failed: ${e.message}" }
            }
        }
    }

    /**
     * Restores, moves, and maximizes [window] for the show path.
     */
    @Suppress("TooGenericExceptionCaught")
    protected suspend fun placePreviewForShow(
        window: Window,
        targetWorkArea: Rectangle?,
        scope: CoroutineScope,
    ) {
        val workArea = targetWorkArea ?: window.workAreaBounds()
        val sameMonitor = lastPreviewWorkArea == workArea
        val awtWorkArea = window.workAreaBounds()
        logger.info { "Preview show sameMonitor=$sameMonitor bounds=${window.bounds} awtWorkArea=$awtWorkArea" }
        if (!sameMonitor) {
            val frame = window as? Frame
            frame?.isResizable = true
            try {
                window.bounds = workArea
                val placed = placePreview(window, workArea, PREVIEW_TIMEOUT_MS)
                lastPreviewWorkArea = if (placed) workArea else null
            } catch (e: CancellationException) {
                lastPreviewWorkArea = null
                throw e
            } catch (e: Exception) {
                lastPreviewWorkArea = null
                logger.warning { "Preview placement failed: ${e.message}" }
            } finally {
                frame?.isResizable = false
            }
        } else if (window.bounds != awtWorkArea) {
            logger.info { "Preview bounds drifted to ${window.bounds}; restoring $awtWorkArea" }
            window.bounds = awtWorkArea
        }
        if (sameMonitor && keepsPreviewMaximizedWhenHidden) {
            logger.info { "Preview maximize skipped; window stays maximized while hidden" }
        } else if (sameMonitor) {
            reassertPreviewMaximize(window, scope)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reassertPreviewMaximize(
        window: Window,
        scope: CoroutineScope,
    ) {
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            try {
                val maximized = enterFullscreen(window, PREVIEW_TIMEOUT_MS)
                if (!maximized) {
                    logger.info { "Preview was not maximized after re-asserting maximize" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warning { "Preview maximize failed: ${e.message}" }
            } finally {
                logger.info { "Preview maximize elapsedMs=${started.elapsedNow().inWholeMilliseconds}" }
            }
        }
    }
}
