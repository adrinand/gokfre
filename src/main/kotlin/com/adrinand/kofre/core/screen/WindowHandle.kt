package com.adrinand.kofre.core.screen

/**
 * Platform-neutral reference to a window.
 *
 * X11 sessions identify windows with a numeric XID. Wayland compositors do not
 * expose a global window ID, so Wayland backends use an opaque token that is
 * meaningful only to the backend that created it.
 */
sealed class WindowHandle {
    /**
     * An X11 window identified by its XID.
     */
    data class X11(val id: Long) : WindowHandle()

    /**
     * A native Wayland window managed by a specific backend.
     *
     * [backend] is the name of the backend (e.g., "gnome-shell", "kwin") and
     * [token] is an opaque string that the backend can use to refer back to the
     * window. Most Wayland backends can only operate on the currently-active
     * window, so the token is often a placeholder.
     */
    data class Wayland(val backend: String, val token: String) : WindowHandle()

    /**
     * A macOS window identified by the owning process ID.
     *
     * The Accessibility API does not expose a stable per-window identifier, so
     * operations are performed on the focused window of the captured process.
     */
    data class Mac(val pid: Long, val title: String = "") : WindowHandle()
}
