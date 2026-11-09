#!/bin/bash
# Sets up a real nested Wayland KDE session for E2E tests.
# This runs on GitHub-hosted Ubuntu runners. We start KWin directly with the
# gokfre KWin script pre-enabled in kwinrc so the script is loaded and exposes
# its D-Bus service, which is required for arrangement tests on Wayland.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

sudo apt-get update

# Core desktop stack. kwin-wayland is the KDE compositor; plasma-workspace
# provides KWin script helpers and schemas. xwayland lets X11 clients run.
sudo apt-get install -y \
    dbus-x11 \
    kwin-wayland \
    plasma-workspace \
    xwayland \
    xdotool \
    libappindicator3-1 \
    libayatana-appindicator3-1 \
    at-spi2-core \
    sqlite3

# Ensure a runtime directory for the nested Wayland socket.
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/gokfre-xdg-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

# Start a session D-Bus.
eval "$(dbus-launch --sh-syntax)"
export DBUS_SESSION_BUS_ADDRESS

# Install the KDE KWin helper script before starting KWin.
./gradlew installKdeScript --no-daemon

# Pre-enable the script in kwinrc so KWin loads it on startup.
KWINRC="${HOME}/.config/kwinrc"
mkdir -p "$(dirname "$KWINRC")"
if [ -f "$KWINRC" ]; then
    # Remove any stale entry to avoid duplicates.
    sed -i '/^\[Script-gokfre\.kwin\]/,/^$/d' "$KWINRC"
fi
{
    echo ""
    echo "[Script-gokfre.kwin]"
    echo "Enabled=true"
} >> "$KWINRC"

# Start KWin as a nested Wayland compositor with XWayland.
# The script engine is part of KWin, so the gokfre script is loaded here.
kwin_wayland --xwayland &
KWIN_PID=$!
sleep 8

# Discover the socket KWin created.
WAYLAND_SOCK=""
for sock in "$XDG_RUNTIME_DIR"/wayland-*; do
    if [ -S "$sock" ] && [ -w "$sock" ]; then
        WAYLAND_SOCK="$(basename "$sock")"
        break
    fi
done

if [ -z "$WAYLAND_SOCK" ]; then
    echo "ERROR: KWin did not create a Wayland socket"
    kill "$KWIN_PID" || true
    exit 1
fi

echo "KWin running on Wayland socket $WAYLAND_SOCK (PID $KWIN_PID)"

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
    echo "XDG_CURRENT_DESKTOP=kde"
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

# Verify the KWin script D-Bus service is reachable. This is the service the
# KdeWindowManager backend uses.
if gdbus introspect --session --dest org.kde.KWin.Script.Gokfre --object-path /Gokfre >/dev/null 2>&1; then
    echo "KDE KWin script D-Bus service is available"
else
    echo "WARNING: KDE KWin script D-Bus service is not reachable yet"
fi

# Keep KWin alive for the test step.
echo "KWIN_PID=$KWIN_PID" >> "$GITHUB_ENV"
