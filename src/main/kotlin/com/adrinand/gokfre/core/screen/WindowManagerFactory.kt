package com.adrinand.gokfre.core.screen

import java.util.logging.Logger

private val logger: Logger = Logger.getLogger("com.adrinand.gokfre.core.screen.WindowManagerFactory")

/**
 * Detects the current desktop session and returns the most appropriate
 * [WindowManager] implementation.
 *
 * The order of preference is:
 * 1. A Wayland-native backend for the current desktop environment (GNOME/KDE),
 *    if its helper extension/script is installed and running.
 * 2. The X11 backend, which works on X11 sessions and for XWayland windows on
 *    Wayland sessions.
 * 3. The headless no-op backend as a last resort.
 */
fun createWindowManager(): WindowManager {
    val sessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase() ?: ""
    val desktop = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase() ?: ""

    logger.info { "Creating window manager for sessionType=$sessionType, desktop=$desktop" }

    if (java.awt.GraphicsEnvironment.isHeadless()) {
        logger.info { "Using headless window manager" }
        return HeadlessWindowManager()
    }

    if (!isLinux()) {
        logger.info { "Using macOS window manager" }
        return MacWindowManager()
    }

    return when {
        sessionType == "wayland" && desktop.contains("gnome") -> {
            val gnome = GnomeWindowManager()
            if (gnome.isAvailable()) {
                logger.info { "Using GNOME Wayland window manager" }
                gnome
            } else {
                logger.info { "GNOME Wayland backend unavailable, falling back to X11" }
                X11WindowManager
            }
        }

        sessionType == "wayland" && desktop.contains("kde") -> {
            val kde = KdeWindowManager()
            if (kde.isAvailable()) {
                logger.info { "Using KDE Wayland window manager" }
                kde
            } else {
                logger.info { "KDE Wayland backend unavailable, falling back to X11" }
                X11WindowManager
            }
        }

        else -> {
            logger.info { "Using X11 window manager" }
            X11WindowManager
        }
    }
}
