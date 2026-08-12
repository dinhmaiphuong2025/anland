use std::collections::HashMap;
use std::ffi::CString;
use std::mem;
use std::os::fd::OwnedFd;
use std::os::unix::io::{AsRawFd, FromRawFd, RawFd};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use anyhow::Context;
use niri_config::OutputName;
use smithay::backend::allocator::dmabuf::Dmabuf;
use smithay::backend::allocator::Fourcc;
use smithay::backend::egl::native::EGLSurfacelessDisplay;
use smithay::backend::egl::{EGLContext, EGLDisplay};
use smithay::backend::renderer::damage::OutputDamageTracker;
use smithay::backend::renderer::gles::GlesRenderer;
use smithay::backend::renderer::{DebugFlags, ImportDma};
use smithay::output::{Mode, Output, PhysicalProperties, Subpixel};
use smithay::reexports::calloop::timer::{TimeoutAction, Timer};
use smithay::reexports::calloop::{
    EventSource, Interest, Mode as PollMode, Poll, PostAction, Readiness, RegistrationToken,
    Token, TokenFactory,
};
use smithay::reexports::wayland_protocols::wp::presentation_time::server::wp_presentation_feedback;
use smithay::utils::Size;
use smithay::wayland::dmabuf::{DmabufFeedbackBuilder, DmabufGlobal};
use smithay::wayland::presentation::Refresh;

use anland_sys::*;

use super::{IpcOutputMap, OutputId, RenderResult};
use crate::niri::{Niri, RedrawState, State};
use crate::render_helpers::{resources, shaders, RenderCtx, RenderTarget};
use crate::utils::{get_monotonic_time, logical_output};

// ---------------------------------------------------------------------------
// Calloop event source for polling raw file descriptors
// ---------------------------------------------------------------------------

struct FdEventSource {
    fd: RawFd,
}

impl FdEventSource {
    fn new(fd: RawFd) -> Self {
        Self { fd }
    }
}

impl EventSource for FdEventSource {
    type Event = ();
    type Metadata = ();
    type Ret = ();
    type Error = std::io::Error;

    fn process_events<F>(
        &mut self,
        _readiness: Readiness,
        _token: Token,
        mut callback: F,
    ) -> Result<PostAction, Self::Error>
    where
        F: FnMut(Self::Event, &mut Self::Metadata) -> Self::Ret,
    {
        callback((), &mut ());
        Ok(PostAction::Continue)
    }

    fn register(
        &mut self,
        poll: &mut Poll,
        token_factory: &mut TokenFactory,
    ) -> std::io::Result<()> {
        let token = token_factory.token();
        poll.register(self.fd, Interest::READ, PollMode::Level, token)
    }

    fn reregister(
        &mut self,
        poll: &mut Poll,
        token_factory: &mut TokenFactory,
    ) -> std::io::Result<()> {
        let token = token_factory.token();
        poll.reregister(self.fd, Interest::READ, PollMode::Level, token)
    }

    fn unregister(&mut self, poll: &mut Poll) -> std::io::Result<()> {
        poll.unregister(self.fd)
    }
}

// ---------------------------------------------------------------------------
// Anland Backend
// ---------------------------------------------------------------------------

pub struct Anland {
    ctx: AnlandContext,
    _socket_path: String,

    renderer: GlesRenderer,
    output: Option<Output>,
    damage_tracker: Option<OutputDamageTracker>,
    dmabuf_global: Option<DmabufGlobal>,

    dmabuf_textures: Vec<GlesTexture>,

    reconnect_timer_token: Option<RegistrationToken>,
    buf_ready_source_token: Option<RegistrationToken>,
    data_source_token: Option<RegistrationToken>,

    ipc_outputs: Arc<Mutex<IpcOutputMap>>,

    buffer_age: u8,
    debug_tint: bool,
}

use smithay::backend::renderer::gles::GlesTexture;

impl Anland {
    pub fn new(socket_path: String) -> anyhow::Result<Self> {
        let _span = tracy_client::span!("Anland::new");

        let c_path = CString::new(socket_path.as_str())
            .map_err(|_| anyhow::anyhow!("socket path contains null byte"))?;

        let ctx = AnlandContext::connect(&c_path)
            .map_err(|e| anyhow::anyhow!("anland connect failed: {e}"))?;

        let display =
            unsafe { EGLDisplay::new(EGLSurfacelessDisplay) }
                .context("error creating EGL display")?;
        let context =
            EGLContext::new(&display).context("error creating EGL context")?;
        let renderer =
            GlesRenderer::new(context).context("error creating renderer")?;

        Ok(Self {
            ctx,
            _socket_path: socket_path,
            renderer,
            output: None,
            damage_tracker: None,
            dmabuf_global: None,
            dmabuf_textures: Vec::new(),
            reconnect_timer_token: None,
            buf_ready_source_token: None,
            data_source_token: None,
            ipc_outputs: Arc::new(Mutex::new(HashMap::new())),
            buffer_age: 0,
            debug_tint: false,
        })
    }

    pub fn init(&mut self, niri: &mut Niri) {
        let _span = tracy_client::span!("Anland::init");

        let info = self.ctx.screen_info();
        let (w, h) = (info.width as i32, info.height as i32);
        let refresh = (info.refresh / 1000) as i32;

        let output = Output::new(
            "anland".to_string(),
            PhysicalProperties {
                size: (0, 0).into(),
                subpixel: Subpixel::Unknown,
                make: "Anland".into(),
                model: "Android".into(),
                serial_number: "0".into(),
            },
        );

        let mode = Mode {
            size: Size::from((w, h)),
            refresh,
        };
        output.change_current_state(Some(mode), None, None, None);
        output.set_preferred(mode);

        output.user_data().insert_if_missing(|| OutputName {
            connector: "anland".to_string(),
            make: Some("Anland".to_string()),
            model: Some("Android".to_string()),
            serial: None,
        });

        let physical_properties = output.physical_properties();
        let mut ipc = self.ipc_outputs.lock().unwrap();
        ipc.insert(
            OutputId::next(),
            niri_ipc::Output {
                name: output.name(),
                make: physical_properties.make,
                model: physical_properties.model,
                serial: None,
                physical_size: None,
                modes: vec![niri_ipc::Mode {
                    width: w as u16,
                    height: h as u16,
                    refresh_rate: (info.refresh / 1000) as u16,
                    is_preferred: true,
                }],
                current_mode: Some(0),
                is_custom_mode: true,
                vrr_supported: false,
                vrr_enabled: false,
                logical: Some(logical_output(&output)),
            },
        );
        drop(ipc);

        resources::init(&mut self.renderer);
        shaders::init(&mut self.renderer);

        self.create_dmabuf_global(niri);

        self.damage_tracker = Some(OutputDamageTracker::from_output(&output));
        self.output = Some(output.clone());
        niri.add_output(output, None, false);

        self.start_reconnect_timer(niri);
    }

    fn create_dmabuf_global(&mut self, niri: &mut Niri) {
        let default_feedback = || -> anyhow::Result<DmabufFeedback> {
            let display = self.renderer.egl_context().display();
            let device = smithay::backend::egl::EGLDevice::device_for_display(display)
                .context("error getting EGL device")?;
            let node = device
                .try_get_render_node()
                .context("error getting EGL device render node")?
                .context("failed to query EGL device render node")?;

            let primary_formats = self.renderer.dmabuf_formats();
            DmabufFeedbackBuilder::new(node.dev_id(), primary_formats)
                .build()
                .context("error building dmabuf feedback")
        };

        let dmabuf_global = match default_feedback() {
            Ok(feedback) => niri
                .dmabuf_state
                .create_global_with_default_feedback::<State>(
                    &niri.display_handle,
                    &feedback,
                ),
            Err(err) => {
                debug!("failed building dmabuf feedback, falling back to v3: {err:?}");
                let primary_formats = self.renderer.dmabuf_formats();
                niri
                    .dmabuf_state
                    .create_global::<State>(&niri.display_handle, primary_formats)
            }
        };
        self.dmabuf_global = Some(dmabuf_global);
    }

    // -------------------------------------------------------------------
    // Reconnect
    // -------------------------------------------------------------------

    fn start_reconnect_timer(&mut self, niri: &mut Niri) {
        let timer = Timer::from_duration(Duration::from_millis(200));
        if let Ok(token) = niri.event_loop.insert_source(
            timer,
            move |_, _, state| {
                state.backend.anland().try_reconnect(&mut state.niri);
                TimeoutAction::ToDuration(Duration::from_millis(200))
            },
        ) {
            self.reconnect_timer_token = Some(token);
        }
    }

    fn try_reconnect(&mut self, niri: &mut Niri) {
        if !self.ctx.is_fallback() {
            return;
        }
        if self.ctx.try_exit_fallback().is_ok() {
            self.on_connected(niri);
        }
    }

    fn on_connected(&mut self, niri: &mut Niri) {
        let _span = tracy_client::span!("Anland::on_connected");

        let count = self.ctx.buffer_count() as usize;
        if count == 0 {
            warn!("connected but got 0 dmabufs");
            return;
        }

        self.dmabuf_textures.clear();

        for i in 0..count {
            let raw_fd = self.ctx.dmabuf_fd_at(i as i32);
            if raw_fd < 0 {
                continue;
            }
            let info = match self.ctx.dmabuf_info_at(i as i32) {
                Some(info) => info,
                None => continue,
            };
            match self.import_raw_dmabuf(raw_fd, &info) {
                Ok(texture) => self.dmabuf_textures.push(texture),
                Err(e) => warn!("failed to import dmabuf {}: {e:?}", i),
            }
        }

        info!(
            "connected to anland consumer: {} buffers, {}x{}",
            self.dmabuf_textures.len(),
            self.ctx.screen_info().width,
            self.ctx.screen_info().height,
        );

        self.register_buffer_ready_source(niri);
        self.register_input_source(niri);
    }

    fn import_raw_dmabuf(
        &mut self,
        raw_fd: RawFd,
        info: &anland_sys::buf_info,
    ) -> anyhow::Result<GlesTexture> {
        let owned_fd = unsafe { OwnedFd::from_raw_fd(raw_fd) };

        let fourcc = protocol_format_to_fourcc(info.format);

        let mut builder = Dmabuf::builder(
            info.width,
            info.height,
            fourcc,
            smithay::backend::allocator::dmabuf::DmabufFlags::empty(),
        );

        builder.add_plane(
            owned_fd,
            info.offset,
            info.stride,
            smithay::reexports::gbm::Modifier::from(info.modifier),
        );

        let dmabuf = builder
            .build()
            .context("failed to build Dmabuf from raw fd")?;

        let texture = self
            .renderer
            .import_dmabuf(&dmabuf, None)
            .map_err(|e| anyhow::anyhow!("import_dmabuf failed: {e:?}"))?;

        Ok(texture)
    }

    // -------------------------------------------------------------------
    // Event sources
    // -------------------------------------------------------------------

    fn register_buffer_ready_source(&mut self, niri: &mut Niri) {
        let fd = self.ctx.buffer_ready_fd();
        if fd < 0 {
            return;
        }
        let source = FdEventSource::new(fd);
        if let Ok(token) = niri.event_loop.insert_source(source, move |_, _, state| {
            let anland = state.backend.anland();
            let fd = anland.ctx.buffer_ready_fd();
            let mut val: u64 = 0;
            unsafe {
                libc::read(
                    fd,
                    &mut val as *mut u64 as *mut libc::c_void,
                    std::mem::size_of::<u64>(),
                );
            }
            if let Some(output) = anland.output.clone() {
                state.niri.queue_redraw(&output);
            }
        }) {
            self.buf_ready_source_token = Some(token);
        }
    }

    fn register_input_source(&mut self, niri: &mut Niri) {
        let fd = self.ctx.data_fd();
        if fd < 0 {
            return;
        }
        let source = FdEventSource::new(fd);
        if let Ok(token) = niri.event_loop.insert_source(source, move |_, _, state| {
            let anland = state.backend.anland();
            loop {
                match anland.ctx.poll_input_event(16) {
                    Some(event) => anland.handle_input_event(&mut state.niri, event),
                    None => break,
                }
            }
        }) {
            self.data_source_token = Some(token);
        }
    }

    // -------------------------------------------------------------------
    // Input dispatch
    // -------------------------------------------------------------------

    fn handle_input_event(&mut self, _niri: &mut Niri, event: InputEvent) {
        let u = unsafe {
            let u: InputEventUnion = std::mem::zeroed();
            let mut u = u;
            std::ptr::copy_nonoverlapping(
                &event.touch as *const InputTouch as *const u8,
                &mut u as *mut InputEventUnion as *mut u8,
                std::mem::size_of::<InputEventUnion>(),
            );
            u
        };

        match event.type_ {
            INPUT_TYPE_TOUCH => {
                let t = unsafe { u.touch };
                debug!(
                    "touch: action={} x={} y={} id={}",
                    t.action, t.x, t.y, t.pointer_id
                );
            }
            INPUT_TYPE_KEY => {
                let k = unsafe { u.key };
                debug!("key: action={} keycode={}", k.action, k.keycode);
            }
            INPUT_TYPE_POINTER_MOTION => {
                debug!("pointer motion");
            }
            INPUT_TYPE_POINTER_BUTTON => {
                debug!("pointer button");
            }
            INPUT_TYPE_POINTER_AXIS => {
                debug!("pointer axis");
            }
            INPUT_TYPE_TOUCH_FRAME => {}
            INPUT_TYPE_DISPLAY_REFRESH => {
                let d = unsafe { u.display };
                debug!("display refresh: {} mHz", d.refresh_mhz);
            }
            INPUT_TYPE_CLIPBOARD => {
                let c = unsafe { u.clipboard };
                if c.size > 0 {
                    let mut buf = vec![0u8; c.size as usize];
                    self.ctx.poll_input_event_extend_data(&mut buf, 1000);
                    debug!("clipboard: {} bytes", c.size);
                }
            }
            _ => {
                self.ctx.handle_unhandled_event(&event);
            }
        }
    }

    // -------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------

    pub fn render(
        &mut self,
        niri: &mut Niri,
        output: &Output,
        _target_presentation_time: Duration,
    ) -> RenderResult {
        let _span = tracy_client::span!("Anland::render");

        if self.ctx.is_fallback() {
            return RenderResult::Skipped;
        }

        let idx = self.ctx.selected_buffer_index();
        if idx < 0 || idx as usize >= self.dmabuf_textures.len() {
            return RenderResult::Skipped;
        }

        let ctx = RenderCtx {
            renderer: &mut self.renderer,
            target: RenderTarget::Output,
            xray: None,
        };
        let elements = niri.render_to_vec(ctx, output, true);

        let texture = &self.dmabuf_textures[idx as usize];
        let mut target = match self.renderer.bind(texture) {
            Ok(t) => t,
            Err(e) => {
                warn!("error binding dmabuf: {e:?}");
                return RenderResult::Skipped;
            }
        };

        let damage_tracker = self.damage_tracker.as_mut().unwrap();
        let res = match damage_tracker.render_output(
            &mut self.renderer,
            &mut target,
            self.buffer_age,
            &elements,
            [0.0, 0.0, 0.0, 1.0],
        ) {
            Ok(r) => r,
            Err(e) => {
                warn!("render error: {e:?}");
                return RenderResult::Skipped;
            }
        };

        niri.update_primary_scanout_output(output, &res.states);

        unsafe {
            gl::Flush();
        }
        self.ctx.set_render_fence(-1);
        self.ctx.trigger_refresh();

        let mut presentation_feedbacks =
            niri.take_presentation_feedbacks(output, &res.states);
        presentation_feedbacks.presented::<_, smithay::utils::Monotonic>(
            get_monotonic_time(),
            Refresh::Unknown,
            0,
            wp_presentation_feedback::Kind::empty(),
        );

        let output_state = niri.output_state.get_mut(output).unwrap();
        match mem::replace(&mut output_state.redraw_state, RedrawState::Idle) {
            RedrawState::Idle => unreachable!(),
            RedrawState::Queued => (),
            RedrawState::WaitingForVBlank { .. } => unreachable!(),
            RedrawState::WaitingForEstimatedVBlank(_) => unreachable!(),
            RedrawState::WaitingForEstimatedVBlankAndQueued(_) => unreachable!(),
        }
        output_state.frame_callback_sequence =
            output_state.frame_callback_sequence.wrapping_add(1);
        self.buffer_age = 1;

        RenderResult::Submitted
    }

    // -------------------------------------------------------------------
    // Backend trait methods
    // -------------------------------------------------------------------

    pub fn seat_name(&self) -> String {
        "anland".to_owned()
    }

    pub fn with_primary_renderer<T>(
        &mut self,
        f: impl FnOnce(&mut GlesRenderer) -> T,
    ) -> Option<T> {
        Some(f(&mut self.renderer))
    }

    pub fn toggle_debug_tint(&mut self) {
        self.renderer
            .set_debug_flags(self.renderer.debug_flags() ^ DebugFlags::TINT);
    }

    pub fn import_dmabuf(&mut self, dmabuf: &Dmabuf) -> bool {
        self.renderer.import_dmabuf(dmabuf, None).is_ok()
    }

    pub fn ipc_outputs(&self) -> Arc<Mutex<IpcOutputMap>> {
        self.ipc_outputs.clone()
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

use smithay::backend::dmabuf::DmabufFeedback;

fn protocol_format_to_fourcc(format: u32) -> Fourcc {
    match format {
        0x34325241 | 0x41425234 | 0x08 | 0x01 => Fourcc::Argb8888,
        0x34325258 | 0x58425234 | 0x0c | 0x02 => Fourcc::Xrgb8888,
        0x32335241 | 0x41425233 | 0x09 | 0x03 => Fourcc::Abgr8888,
        0x32335258 | 0x58425233 | 0x0d | 0x04 => Fourcc::Xbgr8888,
        _ => Fourcc::Argb8888,
    }
}