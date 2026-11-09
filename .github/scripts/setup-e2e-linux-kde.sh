#!/bin/bash
# Sets up a headless Wayland KDE session for E2E tests.
# This runs on GitHub-hosted Ubuntu runners. gokfre drives KWin through its
# built-in scripting D-Bus interface, so no companion script needs to be
# installed or enabled.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

sudo apt-get update

# Core desktop stack. kwin-wayland is the KDE compositor; plasma-workspace
# provides its runtime helpers; xwayland lets X11 clients run; x11-utils
# provides xdpyinfo for display discovery.
sudo apt-get install -y \
    dbus-x11 \
    kwin-wayland \
    plasma-workspace \
    xwayland \
    x11-utils \
    procps \
    xdotool \
    libgl1-mesa-dri \
    libayatana-appindicator3-1 \
    at-spi2-core \
    sqlite3

# Ensure a runtime directory for the Wayland socket.
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/gokfre-xdg-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

# Start a session D-Bus.
eval "$(dbus-launch --sh-syntax)"
export DBUS_SESSION_BUS_ADDRESS

export XDG_SESSION_TYPE=wayland
export XDG_CURRENT_DESKTOP=KDE

# Start KWin with the virtual backend so it needs no GPU or logind session.
# QPainter compositing avoids requiring OpenGL on the headless runner. Detach
# it so it survives the end of this workflow step (GitHub Actions reaps
# orphaned processes). The scripting engine is part of KWin, which gokfre
# drives over org.kde.KWin.Scripting.
RUNNER_TRACKING_ID="" KWIN_COMPOSE=Q setsid kwin_wayland \
    --virtual --xwayland --width 1920 --height 1080 \
    > /tmp/kwin.log 2>&1 < /dev/null &
KWIN_PID=$!

wait_for_wayland_socket() {
    local sock
    for _ in $(seq 1 60); do
        for sock in "$XDG_RUNTIME_DIR"/wayland-*; do
            if [ -S "$sock" ] && [ -w "$sock" ]; then
                basename "$sock"
                return 0
            fi
        done
        sleep 0.5
    done
    return 1
}

if ! WAYLAND_SOCK="$(wait_for_wayland_socket)"; then
    echo "ERROR: KWin did not create a Wayland socket"
    cat /tmp/kwin.log || true
    kill "$KWIN_PID" 2>/dev/null || true
    exit 1
fi

echo "KWin running on Wayland socket $WAYLAND_SOCK (PID $KWIN_PID)"

# KWin starts Xwayland rootless with "-displayfd" and no explicit ":N", so the
# display number must be discovered by probing. It only passes "-auth" when
# launched with --xwayland-xauthority; honour it if present.
wait_for_xwayland() {
    for _ in $(seq 1 60); do
        if pgrep -x Xwayland >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.5
    done
    return 1
}

xwayland_auth_file() {
    local pid args
    for pid in $(pgrep -x Xwayland 2>/dev/null || true); do
        args="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
        printf '%s' "$args" | grep -oE '\-auth [^ ]+' | head -n1 | cut -d' ' -f2 || true
        return 0
    done
    return 0
}

find_x11_display() {
    local n
    for n in $(seq 0 9); do
        if [ -S "/tmp/.X11-unix/X$n" ] && xdpyinfo -display ":$n" >/dev/null 2>&1; then
            echo ":$n"
            return 0
        fi
    done
    return 1
}

if ! wait_for_xwayland; then
    echo "ERROR: Xwayland did not start"
    cat /tmp/kwin.log || true
    kill "$KWIN_PID" 2>/dev/null || true
    exit 1
fi

XAUTHORITY_PATH="$(xwayland_auth_file || true)"
if [ -n "$XAUTHORITY_PATH" ]; then
    export XAUTHORITY="$XAUTHORITY_PATH"
fi

DISPLAY_NUM=""
for _ in $(seq 1 60); do
    if DISPLAY_NUM="$(find_x11_display)"; then
        break
    fi
    sleep 0.5
done

if [ -z "$DISPLAY_NUM" ]; then
    echo "ERROR: XWayland display not found"
    cat /tmp/kwin.log || true
    ls -la /tmp/.X11-unix || true
    pgrep -a Xwayland || true
    kill "$KWIN_PID" 2>/dev/null || true
    exit 1
fi

export DISPLAY="$DISPLAY_NUM"

if ! xdpyinfo >/dev/null 2>&1; then
    echo "ERROR: xdpyinfo failed for DISPLAY=$DISPLAY (XAUTHORITY=${XAUTHORITY:-})"
    cat /tmp/kwin.log || true
    exit 1
fi

# Persist environment variables for subsequent workflow steps.
{
    echo "XDG_SESSION_TYPE=wayland"
    echo "XDG_CURRENT_DESKTOP=kde"
    echo "WAYLAND_DISPLAY=$WAYLAND_SOCK"
    echo "DISPLAY=$DISPLAY_NUM"
    echo "DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS"
    echo "XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR"
    echo "LIBGL_ALWAYS_SOFTWARE=1"
} >> "$GITHUB_ENV"
if [ -n "$XAUTHORITY_PATH" ]; then
    echo "XAUTHORITY=$XAUTHORITY_PATH" >> "$GITHUB_ENV"
fi

# Verify KWin's scripting D-Bus interface is reachable. gokfre uses it to run
# short window-management scripts.
service_ready=0
for _ in $(seq 1 60); do
    if gdbus introspect --session --dest org.kde.KWin --object-path /Scripting >/dev/null 2>&1; then
        service_ready=1
        break
    fi
    sleep 0.5
done

if [ "$service_ready" -ne 1 ]; then
    echo "ERROR: KWin scripting D-Bus interface is not reachable"
    cat /tmp/kwin.log || true
    kill "$KWIN_PID" 2>/dev/null || true
    exit 1
fi

echo "KWin scripting D-Bus interface is available"

# Keep KWin alive for the test step.
echo "KWIN_PID=$KWIN_PID" >> "$GITHUB_ENV"
