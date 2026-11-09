#!/bin/bash
# Sets up a real nested Wayland GNOME session for E2E tests.
# This runs on GitHub-hosted Ubuntu runners. We start a full GNOME Shell
# session so the gokfre companion extension can be loaded and expose its D-Bus
# service, which is required for arrangement tests on Wayland.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

sudo apt-get update

# Core desktop stack. gnome-shell includes Mutter; xwayland lets X11 clients
# run; gnome-shell-extension-prefs provides gnome-extensions.
sudo apt-get install -y \
    dbus-x11 \
    gnome-shell \
    gnome-shell-extension-prefs \
    xwayland \
    xdotool \
    libappindicator3-1 \
    libayatana-appindicator3-1 \
    at-spi2-core \
    sqlite3 \
    xdg-utils

# Ensure a runtime directory for the nested Wayland socket.
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/gokfre-xdg-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

# Start a session D-Bus.
eval "$(dbus-launch --sh-syntax)"
export DBUS_SESSION_BUS_ADDRESS

# Install the GNOME Shell helper extension before starting the shell.
./gradlew installGnomeExtension --no-daemon

# Pre-enable the extension in gsettings so GNOME Shell loads it on startup.
gsettings set org.gnome.shell enabled-extensions "['gokfre@adrinand']" || true

# Start GNOME Shell as a nested Wayland display server. This starts both the
# compositor and the shell, allowing extensions to load.
gnome-shell --wayland --display-server &
SHELL_PID=$!
sleep 8

# Discover the Wayland socket GNOME Shell created.
WAYLAND_SOCK=""
for sock in "$XDG_RUNTIME_DIR"/wayland-*; do
    if [ -S "$sock" ] && [ -w "$sock" ]; then
        WAYLAND_SOCK="$(basename "$sock")"
        break
    fi
done

if [ -z "$WAYLAND_SOCK" ]; then
    echo "ERROR: GNOME Shell did not create a Wayland socket"
    kill "$SHELL_PID" || true
    exit 1
fi

echo "GNOME Shell running on Wayland socket $WAYLAND_SOCK (PID $SHELL_PID)"

# XWayland usually picks the next free display.
DISPLAY_NUM=""
for lock in /tmp/.X*-lock; do
    if [ -f "$lock" ]; then
        DISPLAY_NUM="$(basename "$lock" | sed 's/.X\(.*\)--lock/:\1/')"
        break
    fi
done

if [ -z "$DISPLAY_NUM" ]; then
    DISPLAY_NUM=":0"
fi

# Persist environment variables for subsequent workflow steps.
{
    echo "XDG_SESSION_TYPE=wayland"
    echo "XDG_CURRENT_DESKTOP=gnome"
    echo "WAYLAND_DISPLAY=$WAYLAND_SOCK"
    echo "DISPLAY=$DISPLAY_NUM"
    echo "DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS"
    echo "XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR"
} >> "$GITHUB_ENV"

export WAYLAND_DISPLAY="$WAYLAND_SOCK"
export DISPLAY="$DISPLAY_NUM"

# Sanity checks.
if ! xdpyinfo >/dev/null 2>&1; then
    echo "WARNING: xdpyinfo failed; XWayland display may not be ready"
fi

# Verify the extension D-Bus service is reachable. This is the service the
# GnomeWindowManager backend uses.
if gdbus introspect --session --dest org.gnome.Shell.Extensions.Gokfre --object-path /org/gnome/Shell/Extensions/Gokfre >/dev/null 2>&1; then
    echo "GNOME extension D-Bus service is available"
else
    echo "WARNING: GNOME extension D-Bus service is not reachable yet"
fi

# Keep GNOME Shell alive for the test step.
echo "SHELL_PID=$SHELL_PID" >> "$GITHUB_ENV"
