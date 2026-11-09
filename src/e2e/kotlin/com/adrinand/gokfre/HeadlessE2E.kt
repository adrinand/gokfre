package com.adrinand.gokfre

import com.adrinand.gokfre.core.screen.GnomeWindowManager
import com.adrinand.gokfre.core.screen.KdeWindowManager
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import java.awt.GraphicsEnvironment

private const val BACKEND_READY_TIMEOUT_MS = 5_000L
private const val BACKEND_READY_POLL_MS = 500L

fun assumeDesktopEnvironment() {
    assumeFalse(
        "Skipping E2E test: headless environment",
        GraphicsEnvironment.isHeadless(),
    )
}

fun requireArrangementEnvironment() {
    assumeDesktopEnvironment()
    val sessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase() ?: ""
    val desktop = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase() ?: ""
    when {
        sessionType == "x11" -> return
        sessionType == "wayland" && desktop.contains("gnome") -> {
            check(awaitWaylandBackend { GnomeWindowManager().isAvailable() }) {
                "Wayland arrangement on GNOME requires the gokfre@adrinand " +
                    "extension to be installed, enabled, and active on D-Bus " +
                    "(note: GNOME disables extensions while the screen is locked)"
            }
        }

        sessionType == "wayland" && desktop.contains("kde") -> {
            check(awaitWaylandBackend { KdeWindowManager().isAvailable() }) {
                "Wayland arrangement on KDE requires the gokfre.kwin script " +
                    "to be installed, enabled, and active on D-Bus"
            }
        }

        else -> {
            assumeTrue(
                "Skipping arrangement test: no supported backend for " +
                    "session=$sessionType desktop=$desktop",
                false,
            )
        }
    }
}

private fun awaitWaylandBackend(isAvailable: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + BACKEND_READY_TIMEOUT_MS
    while (true) {
        if (isAvailable()) {
            return true
        }
        if (System.currentTimeMillis() >= deadline) {
            return false
        }
        Thread.sleep(BACKEND_READY_POLL_MS)
    }
}
