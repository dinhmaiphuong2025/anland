#!/usr/bin/env bash
#
# Build niri with the anland backend for aarch64 (Arch ARM).
# Cross-compiles from the CI runner (x86_64 Ubuntu) or builds natively.
#
# Usage:
#   ./build.sh [--native]
#
#   --native    Build natively on the target device (aarch64)
#   (default)   Cross-compile for aarch64-unknown-linux-gnu
#
# Environment:
#   NIRI_VERSION   niri git tag (default: v26.04)
#   SYSROOT        aarch64 sysroot path (for cross-compile)
#   JOBS           parallel build jobs (default: nproc)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NIRI_VERSION="${NIRI_VERSION:-v26.04}"
NATIVE="${1:-}"
RUST_TARGET=""
CARGO_ARGS=()

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m[error] %s\033[0m\n' "$*" >&2; exit 1; }

# --- Determine build mode ---
if [[ "$NATIVE" == "--native" ]]; then
    log "Building natively on $(uname -m)"
    [[ "$(uname -m)" == "aarch64" ]] || die "native build requires aarch64 host"
else
    log "Cross-compiling for aarch64-unknown-linux-gnu"
    command -v aarch64-linux-gnu-gcc >/dev/null 2>&1 || {
        die "aarch64-linux-gnu-gcc not found; install gcc-aarch64-linux-gnu"
    }
    RUST_TARGET="aarch64-unknown-linux-gnu"
    rustup target add "$RUST_TARGET" 2>/dev/null || true
    CARGO_ARGS=(--target "$RUST_TARGET")

    # Configure cross-compilation linker
    export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc
    if [[ -n "${SYSROOT:-}" ]]; then
        export PKG_CONFIG_SYSROOT_DIR="$SYSROOT"
        export PKG_CONFIG_PATH="$SYSROOT/usr/lib/pkgconfig:$SYSROOT/usr/share/pkgconfig"
    fi
fi

BUILD_DIR="${BUILD_DIR:-/tmp/niri-anland-build}"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# --- Clone niri ---
log "Cloning niri $NIRI_VERSION"
git clone https://github.com/niri-wm/niri --branch "$NIRI_VERSION" --depth 1 "$BUILD_DIR/niri"

# --- Apply overlay ---
log "Applying anland overlay"
cp -r "$SCRIPT_DIR/overlay/niri/src/backend/anland.rs" "$BUILD_DIR/niri/src/backend/"
cp -r "$SCRIPT_DIR/overlay/niri/src/backend/anland_input.rs" "$BUILD_DIR/niri/src/backend/"
cp -r "$SCRIPT_DIR/overlay/niri/src/backend/mod.rs" "$BUILD_DIR/niri/src/backend/"
cp -r "$SCRIPT_DIR/overlay/niri/src/cli.rs" "$BUILD_DIR/niri/src/"
cp -r "$SCRIPT_DIR/overlay/niri/src/main.rs" "$BUILD_DIR/niri/src/"
cp -r "$SCRIPT_DIR/overlay/niri/src/niri.rs" "$BUILD_DIR/niri/src/"
cp -r "$SCRIPT_DIR/overlay/niri/Cargo.toml" "$BUILD_DIR/niri/"
cp -r "$SCRIPT_DIR/anland-sys" "$BUILD_DIR/niri/"

# --- Build ---
cd "$BUILD_DIR/niri"
log "Building niri with anland backend"
JOBS="${JOBS:-$(nproc)}"
export CARGO_BUILD_JOBS="$JOBS"

cargo build --release "${CARGO_ARGS[@]}"

# --- Output ---
if [[ -n "$RUST_TARGET" ]]; then
    BIN_PATH="target/$RUST_TARGET/release/niri"
else
    BIN_PATH="target/release/niri"
fi

if [[ -f "$BIN_PATH" ]]; then
    log "Build successful: $BIN_PATH"
    file "$BIN_PATH"
    ls -lh "$BIN_PATH"
else
    die "Build failed: niri binary not found at $BIN_PATH"
fi

# Package
OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/output}"
mkdir -p "$OUTPUT_DIR"
cp "$BIN_PATH" "$OUTPUT_DIR/niri"
cp "$SCRIPT_DIR/startup.sh" "$OUTPUT_DIR/"
log "Output: $OUTPUT_DIR/niri"