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
cp -r "$SCRIPT_DIR/anland-sys" "$BUILD_DIR/niri/"

# --- Patch niri source ---
cd "$BUILD_DIR/niri"

# Patch src/backend/mod.rs
log "Patching src/backend/mod.rs"
sed -i '/^pub mod headless;/i pub mod anland;\npub use anland::Anland;\n' src/backend/mod.rs || {
    # Fallback: add after winit
    sed -i '/^pub use winit::Winit;/a pub mod anland;\npub use anland::Anland;' src/backend/mod.rs
}
sed -i '/^pub enum Backend {/,/^}/ {
    /Headless(Headless),/a \    Anland(Anland),
}' src/backend/mod.rs

# Add anland dispatch in init()
sed -i '/Backend::Headless(headless) => headless.init(niri),/a \            Backend::Anland(anland) => anland.init(niri),' src/backend/mod.rs

# Add anland dispatch in seat_name()
sed -i '/Backend::Headless(headless) => headless.seat_name(),/a \            Backend::Anland(anland) => anland.seat_name(),' src/backend/mod.rs

# Add anland dispatch in with_primary_renderer()
sed -i '/Backend::Headless(headless) => headless.with_primary_renderer(f),/a \            Backend::Anland(anland) => anland.with_primary_renderer(f),' src/backend/mod.rs

# Add anland dispatch in render()
sed -i '/Backend::Headless(headless) => headless.render(niri, output),/a \            Backend::Anland(anland) => anland.render(niri, output, target_presentation_time),' src/backend/mod.rs

# Add anland dispatch in mod_key()
sed -i '/Backend::Tty(_) | Backend::Headless(_) => config.input.mod_key.unwrap_or(ModKey::Super),/c\            Backend::Tty(_) | Backend::Headless(_) | Backend::Anland(_) => config.input.mod_key.unwrap_or(ModKey::Super),' src/backend/mod.rs

# Add no-op dispatches for TTY-only methods
for method in change_vt suspend set_monitors_active set_output_on_demand_vrr update_ignored_nodes_config on_output_config_changed early_import; do
    sed -i "/Backend::Headless(_) => (),/a \            Backend::Anland(_) => ()," src/backend/mod.rs
done

# gbm_device returns None
sed -i '/Backend::Headless(_) => None,/a \            Backend::Anland(_) => None,' src/backend/mod.rs

# Add toggle_debug_tint dispatch
sed -i '/Backend::Headless(_) => (),/a \            Backend::Anland(anland) => anland.toggle_debug_tint(),' src/backend/mod.rs

# Add import_dmabuf dispatch
sed -i '/Backend::Headless(headless) => headless.import_dmabuf(dmabuf),/a \            Backend::Anland(anland) => anland.import_dmabuf(dmabuf),' src/backend/mod.rs

# Add ipc_outputs dispatch
sed -i '/Backend::Headless(headless) => headless.ipc_outputs(),/a \            Backend::Anland(anland) => anland.ipc_outputs(),' src/backend/mod.rs

# Add anland() accessor
cat >> src/backend/mod.rs << 'ENDOFFILE'

impl Backend {
    pub fn anland(&mut self) -> &mut Anland {
        if let Self::Anland(v) = self {
            v
        } else {
            panic!("backend is not Anland")
        }
    }
}
ENDOFFILE

# Patch src/niri.rs - add ANLAND_SOCKET check
log "Patching src/niri.rs"
sed -i '/let has_display = /a \        let has_anland = std::env::var_os("ANLAND_SOCKET").is_some() || std::env::var_os("ANLAND_SOCKET_PATH").is_some();' src/niri.rs

# Add Anland backend selection before winit
sed -i '/} else if has_display {/i \        } else if has_anland {\n            let socket = std::env::var("ANLAND_SOCKET")\n                .or_else(|_| std::env::var("ANLAND_SOCKET_PATH"))\n                .unwrap_or_else(|_| "/run/display.sock".into());\n            let anland = Anland::new(socket)\n                .context("error initializing anland backend")?;\n            Backend::Anland(anland)' src/niri.rs

# Patch Cargo.toml
log "Patching Cargo.toml"
sed -i '/^pipewire = /i anland-sys = { path = "anland-sys" }' Cargo.toml

# --- Build ---
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