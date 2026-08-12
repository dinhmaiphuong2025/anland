#!/bin/bash
#
# startup.sh - start niri with the anland backend
# (Arch Linux ARM, Adreno kgsl stack)
#
# Usage:
#   ./startup.sh [socket path]
#
# Env:
#   NIRI_BIN       path to niri binary (default: niri)
#   ANLAND_SOCKET  anland daemon socket path (default: /run/display.sock)
#
set -eu

SOCK="${1:-${ANLAND_SOCKET:-/run/display.sock}}"
NIRI_BIN="${NIRI_BIN:-niri}"

command -v "$NIRI_BIN" >/dev/null 2>&1 || {
    echo "niri not found in PATH; set NIRI_BIN=/path/to/niri" >&2
    exit 1
}

# --- XDG runtime directory ---
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
if [ ! -d "$XDG_RUNTIME_DIR" ]; then
    XDG_RUNTIME_DIR="$HOME/.local/run/anland-$(id -u)"
    mkdir -p "$XDG_RUNTIME_DIR"
    chmod 0700 "$XDG_RUNTIME_DIR"
fi

# --- kgsl/turnip (Adreno) GPU environment ---
export MESA_LOADER_DRIVER_OVERRIDE=kgsl
export GALLIUM_DRIVER=kgsl
export FD_FORCE_KGSL=1
export MESA_VK_DEVICE_SELECT_FORCE_DEFAULT_DEVICE=1
export FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1

# --- anland ---
export ANLAND_SOCKET="$SOCK"
export ANLAND_DRM_DEVICE="${ANLAND_DRM_DEVICE:-/dev/dri/renderD128}"

# Clear DISPLAY to prevent winit backend selection
unset DISPLAY
unset WAYLAND_DISPLAY

echo "==> $NIRI_BIN --anland (socket=$SOCK, drm=$ANLAND_DRM_DEVICE)"
"$NIRI_BIN" --anland &
NIRI_PID=$!

# Wait for Wayland socket
WAYLAND_SOCKET=""
for _ in $(seq 1 30); do
    sleep 1
    for wl in "$XDG_RUNTIME_DIR"/wayland-*; do
        [ -S "$wl" ] || continue
        case "$wl" in *.lock) continue ;; esac
        WAYLAND_SOCKET="$(basename "$wl")"
        break 2
    done
    kill -0 "$NIRI_PID" 2>/dev/null || break
done

if [ -z "$WAYLAND_SOCKET" ]; then
    echo "ERROR: no wayland socket found; niri may have failed" >&2
    wait "$NIRI_PID"
    exit 1
fi

echo "==> wayland socket: $WAYLAND_SOCKET"
export WAYLAND_DISPLAY="$WAYLAND_SOCKET"

# Launch a Wayland client (e.g., terminal)
# weston-terminal &
# or your preferred launcher

wait "$NIRI_PID"