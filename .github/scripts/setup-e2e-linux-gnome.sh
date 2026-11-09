#!/bin/bash
# Sets up a headless Wayland GNOME session for E2E tests.
# This runs on GitHub-hosted Ubuntu runners. We start GNOME Shell with the
# headless backend so the gokfre companion extension can be loaded and expose
# its D-Bus service, which is required for arrangement tests on Wayland.
#
# We cannot use `--display-server`: it takes over a KMS seat and needs a
# logind session, which GitHub Actions runners do not provide. The headless
# backend needs no session and still starts XWayland for Swing/AWT clients.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

sudo apt-get update

# Core desktop stack. gnome-shell includes Mutter; xwayland lets X11 clients
# run; x11-utils provides xdpyinfo for display discovery.
sudo apt-get install -y \
    dbus-x11 \
    gnome-shell \
    gnome-shell-extension-prefs \
    xwayland \
    x11-utils \
    procps \
    xdotool \
    libgl1-mesa-dri \
    libayatana-appindicator3-1 \
    at-spi2-core \
    sqlite3 \
    xdg-utils

# Ensure a runtime directory for the Wayland socket.
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/gokfre-xdg-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

# Start a session D-Bus.
eval "$(dbus-launch --sh-syntax)"
export DBUS_SESSION_BUS_ADDRESS

export XDG_SESSION_TYPE=wayland
export XDG_CURRENT_DESKTOP=GNOME

# Install the GNOME Shell helper extension before starting the shell.
./gradlew installGnomeExtension --no-daemon

# Pre-enable the extension in gsettings so GNOME Shell loads it on startup.
gsettings set org.gnome.shell enabled-extensions "['gokfre@adrinand']" || true

# Start GNOME Shell headless with a virtual monitor. Detach it so it survives
# the end of this workflow step (GitHub Actions reaps orphaned processes).
RUNNER_TRACKING_ID="" setsid gnome-shell \
    --wayland --headless --virtual-monitor 1920x1080 \
    > /tmp/gnome-shell.log 2>&1 < /dev/null &
SHELL_PID=$!

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
    echo "ERROR: GNOME Shell did not create a Wayland socket"
    cat /tmp/gnome-shell.log || true
    kill "$SHELL_PID" 2>/dev/null || true
    exit 1
fi

echo "GNOME Shell running on Wayland socket $WAYLAND_SOCK (PID $SHELL_PID)"

# Find the XWayland server the compositor started. Parse its command line to
# learn the display number and the Xauthority file it was started with: the
# compositor starts XWayland with an auth cookie, so clients outside the
# session need XAUTHORITY to connect.
discover_xwayland() {
    local pid args display auth
    for pid in $(pgrep -x Xwayland || true); do
        args="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
        display="$(printf '%s' "$args" | grep -oE ' :[0-9]+' | head -n1 | tr -d ' ')"
        auth="$(printf '%s' "$args" | grep -oE '\-auth [^ ]+' | head -n1 | cut -d' ' -f2)"
        if [ -n "$display" ]; then
            printf '%s\n%s\n' "$display" "$auth"
            return 0
        fi
    done
    return 1
}

XINFO=""
for _ in $(seq 1 60); do
    if XINFO="$(discover_xwayland)"; then
        break
    fi
    sleep 0.5
done

DISPLAY_NUM=""
XAUTHORITY_PATH=""
if [ -n "$XINFO" ]; then
    DISPLAY_NUM="$(printf '%s\n' "$XINFO" | sed -n '1p')"
    XAUTHORITY_PATH="$(printf '%s\n' "$XINFO" | sed -n '2p')"
fi

# Fallback: probe displays; useful if the XWayland command line is unreadable.
if [ -z "$DISPLAY_NUM" ]; then
    for n in $(seq 0 9); do
        if [ -S "/tmp/.X11-unix/X$n" ] && xdpyinfo -display ":$n" >/dev/null 2>&1; then
            DISPLAY_NUM=":$n"
            break
        fi
    done
fi

if [ -z "$DISPLAY_NUM" ]; then
    echo "ERROR: XWayland display not found"
    cat /tmp/gnome-shell.log || true
    kill "$SHELL_PID" 2>/dev/null || true
    exit 1
fi

export DISPLAY="$DISPLAY_NUM"
if [ -n "$XAUTHORITY_PATH" ]; then
    export XAUTHORITY="$XAUTHORITY_PATH"
fi

if ! xdpyinfo >/dev/null 2>&1; then
    echo "ERROR: xdpyinfo failed for DISPLAY=$DISPLAY (XAUTHORITY=${XAUTHORITY:-})"
    cat /tmp/gnome-shell.log || true
    exit 1
fi

# Persist environment variables for subsequent workflow steps.
{
    echo "XDG_SESSION_TYPE=wayland"
    echo "XDG_CURRENT_DESKTOP=gnome"
    echo "WAYLAND_DISPLAY=$WAYLAND_SOCK"
    echo "DISPLAY=$DISPLAY_NUM"
    echo "DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS"
    echo "XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR"
    echo "LIBGL_ALWAYS_SOFTWARE=1"
} >> "$GITHUB_ENV"
if [ -n "$XAUTHORITY_PATH" ]; then
    echo "XAUTHORITY=$XAUTHORITY_PATH" >> "$GITHUB_ENV"
fi

# Verify the extension D-Bus service is reachable. This is the service the
# GnomeWindowManager backend uses.
service_ready=0
for _ in $(seq 1 60); do
    if gdbus introspect --session --dest org.gnome.Shell.Extensions.Gokfre --object-path /org/gnome/Shell/Extensions/Gokfre >/dev/null 2>&1; then
        service_ready=1
        break
    fi
    sleep 0.5
done

if [ "$service_ready" -ne 1 ]; then
    echo "ERROR: GNOME extension D-Bus service is not reachable"
    gnome-extensions info gokfre@adrinand || true
    cat /tmp/gnome-shell.log || true
    kill "$SHELL_PID" 2>/dev/null || true
    exit 1
fi

echo "GNOME extension D-Bus service is available"

# Keep GNOME Shell alive for the test step.
echo "SHELL_PID=$SHELL_PID" >> "$GITHUB_ENV"
