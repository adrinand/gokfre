#!/bin/bash
# Sets up a headless Wayland KDE session for E2E tests.
# This runs on GitHub-hosted Ubuntu runners. gokfre drives KWin through its
# built-in scripting D-Bus interface, so no companion script needs to be
# installed or enabled.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

echo "Installing KDE desktop stack..."
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
    libglib2.0-bin \
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
echo "Starting kwin_wayland (virtual backend)..."
RUNNER_TRACKING_ID="" KWIN_COMPOSE=Q setsid kwin_wayland \
    --virtual --xwayland --width 1920 --height 1080 \
    > /tmp/kwin.log 2>&1 < /dev/null &
KWIN_PID=$!

fail_with_log() {
    echo "ERROR: $1"
    cat /tmp/kwin.log || true
    kill "$KWIN_PID" 2>/dev/null || true
    exit 1
}

WAIT_ATTEMPTS=60
WAIT_INTERVAL=0.5

WAYLAND_SOCK=""
for _ in $(seq 1 "$WAIT_ATTEMPTS"); do
    if ! kill -0 "$KWIN_PID" 2>/dev/null; then
        fail_with_log "KWin exited before creating a Wayland socket"
    fi
    for sock in "$XDG_RUNTIME_DIR"/wayland-*; do
        if [ -S "$sock" ] && [ -w "$sock" ]; then
            WAYLAND_SOCK="$(basename "$sock")"
            break 2
        fi
    done
    sleep "$WAIT_INTERVAL"
done

if [ -z "$WAYLAND_SOCK" ]; then
    fail_with_log "KWin did not create a Wayland socket"
fi

echo "KWin running on Wayland socket $WAYLAND_SOCK (PID $KWIN_PID)"

# KWin owns the X11 listening socket and spawns Xwayland lazily on first
# connection. Probing triggers that start, so wait for a usable display rather
# than for the process. An auth-protected server rejects the first probe, so
# pick up the Xauthority file from the process it just started and retry.
xwayland_pid_for_display() {
    local display="$1" pid args
    for pid in $(pgrep -x Xwayland 2>/dev/null || true); do
        args="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
        if printf '%s' "$args" | grep -qF -- " $display "; then
            printf '%s\n' "$pid"
            return 0
        fi
    done
    return 1
}

echo "Waiting for Xwayland..."
DISPLAY_NUM=""
XWAYLAND_PID=""
XAUTHORITY_PATH=""
DEADLINE=$((SECONDS + 300))
while [ "$SECONDS" -lt "$DEADLINE" ]; do
    if ! kill -0 "$KWIN_PID" 2>/dev/null; then
        fail_with_log "KWin exited before exposing an XWayland display"
    fi
    for n in $(seq 0 9); do
        if [ ! -S "/tmp/.X11-unix/X$n" ]; then
            continue
        fi
        if timeout 5 xdpyinfo -display ":$n" >/dev/null 2>&1; then
            XWAYLAND_PID="$(xwayland_pid_for_display ":$n" || true)"
            if [ -n "$XWAYLAND_PID" ]; then
                DISPLAY_NUM=":$n"
                break 2
            fi
        elif [ -z "$XAUTHORITY_PATH" ]; then
            XWAYLAND_PID="$(xwayland_pid_for_display ":$n" || true)"
            if [ -n "$XWAYLAND_PID" ]; then
                XWAYLAND_CMDLINE="$(tr '\0' ' ' < "/proc/$XWAYLAND_PID/cmdline" 2>/dev/null || true)"
                auth="$(printf '%s' "$XWAYLAND_CMDLINE" | grep -oE '\-auth [^ ]+' | head -n1 | cut -d' ' -f2 || true)"
                if [ -n "$auth" ] && [ -f "$auth" ] && [ -O "$auth" ]; then
                    XAUTHORITY_PATH="$auth"
                    export XAUTHORITY="$XAUTHORITY_PATH"
                fi
            fi
        fi
    done
    sleep "$WAIT_INTERVAL"
done

if [ -z "$DISPLAY_NUM" ]; then
    echo "X11 sockets:"
    ls -la /tmp/.X11-unix || true
    echo "Xwayland processes:"
    pgrep -a Xwayland || true
    fail_with_log "Xwayland did not start"
fi

export DISPLAY="$DISPLAY_NUM"
echo "Using XWayland display $DISPLAY_NUM"

if ! timeout 5 xdpyinfo >/dev/null 2>&1; then
    fail_with_log "xdpyinfo failed for DISPLAY=$DISPLAY (XAUTHORITY=${XAUTHORITY:-})"
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
echo "Checking KWin scripting D-Bus interface..."
service_ready=0
for _ in $(seq 1 "$WAIT_ATTEMPTS"); do
    if timeout 10 gdbus introspect --session --dest org.kde.KWin --object-path /Scripting >/dev/null 2>&1; then
        service_ready=1
        break
    fi
    if ! kill -0 "$KWIN_PID" 2>/dev/null; then
        fail_with_log "KWin exited before exposing its scripting interface"
    fi
    sleep "$WAIT_INTERVAL"
done

if [ "$service_ready" -ne 1 ]; then
    fail_with_log "KWin scripting D-Bus interface is not reachable"
fi

echo "KWin scripting D-Bus interface is available"

# Keep KWin alive for the test step.
echo "KWIN_PID=$KWIN_PID" >> "$GITHUB_ENV"
