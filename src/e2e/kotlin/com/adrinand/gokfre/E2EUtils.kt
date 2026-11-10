package com.adrinand.gokfre

import com.adrinand.gokfre.ui.PREVIEW_WINDOW_TITLE
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.Window
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

private const val POLL_INTERVAL_MS = 100L
private const val DEFAULT_TIMEOUT_MS = 5_000L

private fun Window.titleMatches(title: String): Boolean = (this as? Frame)?.title == title

fun waitForPreviewWindow(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Window? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val window = Window.getWindows().find { it.titleMatches(PREVIEW_WINDOW_TITLE) && it.isShowing }
        if (window != null) {
            return window
        }
        Thread.sleep(POLL_INTERVAL_MS)
    }
    return null
}

fun waitForPreviewClosed(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val window = Window.getWindows().find { it.titleMatches(PREVIEW_WINDOW_TITLE) && it.isShowing }
        if (window == null) {
            return true
        }
        Thread.sleep(POLL_INTERVAL_MS)
    }
    return false
}

fun sendKeyToWindow(
    window: Window,
    keyCode: Int,
) {
    SwingUtilities.invokeAndWait {
        val target = findKeyEventComponent(window) ?: window.mostRecentFocusOwner ?: window
        val timestamp = System.currentTimeMillis()
        val keyChar = keyCharFor(keyCode)
        target.dispatchEvent(
            KeyEvent(target, KeyEvent.KEY_PRESSED, timestamp, 0, keyCode, keyChar, KeyEvent.KEY_LOCATION_STANDARD),
        )
        target.dispatchEvent(
            KeyEvent(target, KeyEvent.KEY_RELEASED, timestamp, 0, keyCode, keyChar, KeyEvent.KEY_LOCATION_STANDARD),
        )
    }
}

private fun findKeyEventComponent(component: Component): Component? {
    if (component.keyListeners.isNotEmpty()) {
        return component
    }
    if (component is Container) {
        for (child in component.components) {
            findKeyEventComponent(child)?.let { return it }
        }
    }
    return null
}

fun activateWindowByTitle(title: String): Boolean {
    return runCatching {
        val process =
            ProcessBuilder("xdotool", "search", "--name", title, "windowactivate")
                .redirectErrorStream(true)
                .start()
        process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)
}

fun keyCodeForChar(char: Char): Int =
    when (char.uppercaseChar()) {
        in 'A'..'Z' -> KeyEvent.VK_A + (char.uppercaseChar() - 'A')
        in '0'..'9' -> KeyEvent.VK_0 + (char - '0')
        else -> error("Unsupported E2E key character: $char")
    }

private fun keyCharFor(keyCode: Int): Char =
    when (keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> 'a' + (keyCode - KeyEvent.VK_A)
        in KeyEvent.VK_0..KeyEvent.VK_9 -> '0' + (keyCode - KeyEvent.VK_0)
        else -> KeyEvent.CHAR_UNDEFINED
    }
