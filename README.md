# kofre
A desktop tiling window manager for Linux and macOS. Press the global hotkey to show the layout and pick a tile combination to arrange the currently focused window accordingly.

<img width="2400" height="1350" alt="Screenshot From 2026-08-16 22-57-52" src="https://github.com/user-attachments/assets/178af25c-2913-4c7e-bd84-6a58cfc517bc" />

## Requirements

- Linux (X11 or Wayland) or macOS
- Java 17+
- Rust toolchain (only needed for Linux builds; the hotkey library is written in Rust)
- On Linux, your user must be in the `input` and `uinput` groups for the global hotkey to work reliably on Wayland

## Setup

### Linux

1. Add your user to the `input` and `uinput` groups:

   ```bash
   sudo usermod -aG input,uinput "$USER"
   ```

   Then log out and back in (or run `newgrp input` and `newgrp uinput` to apply the change in the current shell).

2. Verify permissions:

   ```bash
   ls -l /dev/input/event* | head -1
   ls -l /dev/uinput
   ```

   `/dev/input/event*` should be readable by the `input` group and `/dev/uinput` should be writable by the `uinput` group.

3. Build and run:

   ```bash
   ./gradlew run
   ```

   Kofre starts hidden in the system tray. Press **Super+K** to toggle the preview window.

### macOS

1. Build and run:

   ```bash
   ./gradlew run
   ```

   Kofre uses JNativeHook for global hotkeys and the Accessibility API to arrange windows. The first run may prompt for accessibility and input-monitoring permissions; both must be granted for Kofre to work.

## Global hotkey

- Default: **Super+K**
- On Linux, the hotkey is registered via the `kbd-global` Rust crate reading from `/dev/input/event*`; this works on both X11 and Wayland.
- On macOS, the hotkey is registered with JNativeHook.

### Wayland note

On Wayland, `Super+K` can conflict with the compositor's own Super binding (e.g., opening the Activities overview). The built-in hotkey uses the `evdev` listener, which requires access to `/dev/input/event*` and a virtual device via `/dev/uinput`. Both are usually granted by the `input` and `uinput` groups.

If the default hotkey conflicts with your compositor, change it in Kofre's settings to a combination that is not already bound.

### Native Wayland window management

X11 provides a universal window-management API, but Wayland intentionally does not. Kofre therefore uses a different backend depending on the session:

| Session | Backend | Notes |
|---|---|---|
| X11 | X11 | Works out of the box. |
| Wayland + GNOME | GNOME Shell extension | Install `kofre@adrinand` (see below). |
| Wayland + KDE Plasma | KWin script | Install `kofre.kwin` (see below). |
| Wayland + other | X11 fallback | Only XWayland windows can be tiled. |
| macOS | Accessibility API | Grants Accessibility permission when prompted. |

#### Installing the GNOME Shell extension

For local development:

```bash
./gradlew installGnomeExtension
```

Then enable it and restart GNOME Shell (on Wayland this usually requires logging out and back in):

```bash
gnome-extensions enable kofre@adrinand
```

#### Installing the KDE KWin script

For local development:

```bash
./gradlew installKdeScript
```

Then enable it in KWin's script settings or with:

```bash
kwriteconfig6 --file kwinrc --group Plugins --key kofreEnabled true
```

Restart KWin afterward.

## Tests

```bash
./gradlew check
```

This runs Kotlin tests, ktlint, detekt, and the Rust test suite for the hotkey library.

## Building a distribution

Local builds produce packages for the host OS:

```bash
./gradlew package      # .deb / .rpm on Linux, .dmg on macOS
```

The AppImage is built by the CD workflow because it requires `appimagetool`, which the workflow downloads automatically.

The release CD workflow (`.github/workflows/cd.yml`) builds and publishes:

- `Kofre-<version>-arm64.dmg`
- `Kofre-<version>-x86_64.dmg`
- `kofre_<version>_amd64.deb`
- `kofre-<version>-1.x86_64.rpm`
- `Kofre-<version>-x86_64.AppImage`
- `Kofre-<version>-aarch64.AppImage`

on every push to `main` for which the version in `build.gradle.kts` has changed.
