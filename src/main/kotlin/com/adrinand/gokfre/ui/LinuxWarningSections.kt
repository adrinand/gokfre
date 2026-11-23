package com.adrinand.gokfre.ui

import com.adrinand.gokfre.core.screen.LinuxRequirement

internal fun warningSection(requirement: LinuxRequirement): WarningSection =
    when (requirement) {
        LinuxRequirement.INPUT_DEVICE_ACCESS -> inputDeviceSection()
        LinuxRequirement.HOTKEY_LISTENER -> hotkeyListenerSection()
        LinuxRequirement.GNOME_SHELL_EXTENSION -> gnomeExtensionSection()
        LinuxRequirement.GNOME_APPINDICATOR_EXTENSION -> appIndicatorSection()
        LinuxRequirement.DBUS_CLIENT -> dbusClientSection()
        LinuxRequirement.UNSUPPORTED_WAYLAND_COMPOSITOR -> unsupportedCompositorSection()
    }

private fun hotkeyListenerSection(): WarningSection =
    WarningSection(
        title = "Global hotkey unavailable",
        lines =
            listOf(
                body(
                    "Gokfre could not start its keyboard listener, so the global hotkey will not work. " +
                        "Input device permissions look correct, so the native hotkey library may not have loaded.",
                ),
                emphasis("Check the Gokfre log for details, then restart Gokfre."),
            ),
    )

private fun inputDeviceSection(): WarningSection =
    WarningSection(
        title = "Input device access required",
        lines =
            listOf(
                body(
                    "Gokfre reads keyboard input directly to register the global hotkey. " +
                        "Your user needs access to /dev/input/event* and /dev/uinput.",
                ),
                emphasis("Add your user to the input and uinput groups, then log out and back in:"),
                code("sudo usermod -aG input,uinput \$USER"),
                emphasis("If /dev/uinput is missing, load the kernel module:"),
                code("sudo modprobe uinput"),
            ),
    )

private fun gnomeExtensionSection(): WarningSection =
    WarningSection(
        title = "GNOME Shell extension required",
        lines =
            listOf(
                body(
                    "On Wayland, GNOME does not let applications move or resize native windows. " +
                        "Gokfre needs its GNOME Shell extension (gokfre@adrinand) to arrange windows.",
                ),
                emphasis("Install the extension from the project repository, then enable it and log out and back in:"),
                code("gnome-extensions enable gokfre@adrinand"),
                body("For local development, ./gradlew installGnomeExtension installs the extension."),
            ),
    )

private fun appIndicatorSection(): WarningSection =
    WarningSection(
        title = "AppIndicator extension required",
        lines =
            listOf(
                body(
                    "GNOME hides system tray icons unless the AppIndicator extension is enabled. " +
                        "Gokfre runs in the tray, so you need it to open Settings and quit the app.",
                ),
                emphasis(
                    "Install the AppIndicator extension (gnome-shell-extension-appindicator in most " +
                        "distributions), enable it, then log out and back in.",
                ),
            ),
    )

private fun dbusClientSection(): WarningSection =
    WarningSection(
        title = "D-Bus client required",
        lines =
            listOf(
                body(
                    "Gokfre talks to GNOME Shell over D-Bus to arrange native Wayland windows, " +
                        "but the gdbus command was not found.",
                ),
                emphasis(
                    "Install the GLib tools package (libglib2.0-bin on Debian/Ubuntu, glib2 on Fedora), " +
                        "then restart Gokfre.",
                ),
            ),
    )

private fun unsupportedCompositorSection(): WarningSection =
    WarningSection(
        title = "Limited Wayland support",
        lines =
            listOf(
                body(
                    "This Wayland compositor is not supported for native window management. " +
                        "Gokfre can only arrange XWayland windows here; native Wayland windows " +
                        "cannot be moved or resized.",
                ),
                emphasis("Use an X11 session or GNOME/KDE Plasma Wayland for full support."),
            ),
    )

private fun body(text: String): WarningLine = WarningLine(text)

private fun emphasis(text: String): WarningLine = WarningLine(text, WarningLineStyle.Emphasis)

private fun code(text: String): WarningLine = WarningLine(text, WarningLineStyle.Code)
