package com.adrinand.gokfre.core.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val BACKEND_NAME = "kwin"
private const val POLL_INTERVAL_MS = 50L
private const val MAXIMIZE_SETTLE_MS = 50L
private const val POLL_TIMEOUT_MS = 2_000L

/**
 * [WindowManager] implementation for native Wayland windows on KDE Plasma.
 */
class KdeWindowManager : SingleBackendWindowManager<WindowHandle.Wayland>() {
    override val logger: Logger = Logger.getLogger("com.adrinand.gokfre.core.screen.KdeWindowManager")

    private var capturedId: String? = null
    private var capturedBounds: Rectangle? = null

    fun isAvailable(): Boolean = runner()?.run(KdeScriptRenderer.probe())?.ok == true

    override fun castHandle(window: WindowHandle): WindowHandle.Wayland? =
        (window as? WindowHandle.Wayland)?.takeIf { it.backend == BACKEND_NAME }

    override fun retrieveActiveWindow(): WindowHandle.Wayland? {
        val reply = runner()?.run(KdeScriptRenderer.capture()) ?: return null
        if (!reply.ok) {
            return null
        }
        capturedId = reply.id
        capturedBounds = Rectangle(reply.x, reply.y, reply.width, reply.height)
        return WindowHandle.Wayland(BACKEND_NAME, reply.id)
    }

    override fun retrieveBounds(handle: WindowHandle.Wayland): Rectangle? {
        val cached = capturedBounds?.takeIf { handle.token == capturedId }
        if (cached != null) {
            return cached
        }
        val reply = runner()?.run(KdeScriptRenderer.bounds(handle.token)) ?: return null
        return if (reply.ok) Rectangle(reply.x, reply.y, reply.width, reply.height) else null
    }

    override fun applyBounds(
        handle: WindowHandle.Wayland,
        bounds: Rectangle,
    ) {
        val reply = runner()?.run(KdeScriptRenderer.moveResize(handle.token, bounds))
        if (reply?.ok != true) {
            logger.warning { "KDE script did not apply $bounds: ${reply?.error ?: "no reply"}" }
        }
    }

    override fun focusHandle(handle: WindowHandle.Wayland) {
        val reply = runner()?.run(KdeScriptRenderer.focus(handle.token))
        if (reply?.ok != true) {
            logger.fine { "KDE script did not focus the window: ${reply?.error ?: "no reply"}" }
        }
    }

    override suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val runner = runner() ?: return@withContext false
            val reply = runner.run(KdeScriptRenderer.maximizePreview()) ?: return@withContext false
            if (!reply.ok) {
                logger.warning { "KDE script did not maximize the preview" }
                return@withContext false
            }
            waitForMaximized(runner, timeoutMs, expected = true)
        }

    override suspend fun exitFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val runner = runner() ?: return@withContext false
            val reply = runner.run(KdeScriptRenderer.restorePreview()) ?: return@withContext false
            if (!reply.ok) {
                logger.warning { "KDE script did not restore the preview" }
                return@withContext false
            }
            waitForMaximized(runner, timeoutMs, expected = false)
        }

    private suspend fun waitForMaximized(
        runner: KdeScriptRunner,
        timeoutMs: Long,
        expected: Boolean,
    ): Boolean {
        delay(MAXIMIZE_SETTLE_MS.milliseconds)
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            val reply = runner.run(KdeScriptRenderer.isPreviewMaximized(), POLL_TIMEOUT_MS)
            if (reply?.maximized == expected) {
                return true
            }
            delay(POLL_INTERVAL_MS.milliseconds)
        }
        return false
    }

    private fun runner(): KdeScriptRunner? = KdeBridge.getInstance()?.let(::KdeScriptRunner)
}
