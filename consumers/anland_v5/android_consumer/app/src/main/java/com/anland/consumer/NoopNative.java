package com.anland.consumer;

import android.view.Surface;

/**
 * A {@link Native} that does nothing.
 *
 * <p>Used when the window must run without a daemon, e.g. the HUD editor opened from Settings
 * while no desktop session is live. Every call site keeps its normal code path, so nothing has to
 * remember a null check — the pipeline receives the calls and drops them.
 */
public final class NoopNative extends Native {

    public NoopNative() {
        super(false);
    }

    @Override public void destroy() { }
    @Override public void configure(String socketPath, boolean useRoot, String helperPath, String bridgePath) { }
    @Override public void configure(String socketPath, boolean useRoot, String helperPath, String bridgePath, boolean topAppEnable, String topAppPath, int topAppMode, String topAppStops) { }
    @Override public void start(Surface surface, Object clipboardTarget, Object activityTarget) { }
    @Override public void stop() { }
    @Override public void setFocused(boolean focused) { }
    @Override public void setCustomResolution(int width, int height) { }
    @Override public void setScreenSize(int width, int height) { }
    @Override public void sendTouch(int action, float x, float y, int pointerId) { }
    @Override public void sendTouchFrame() { }
    @Override public void sendKey(int action, int keycode) { }
    @Override public void sendMouseMotion(float x, float y, float dx, float dy) { }
    @Override public void sendMouseButton(int button, boolean pressed) { }
    @Override public void sendMouseScroll(int axis, float value) { }
    @Override public void sendTabletSample(int toolId, int toolType, int capabilities, int deviceId, float x, float y, float pressure, float tiltX, float tiltY, float distance, int flags, int buttons, long timeMsec) { }
    @Override public void setRefreshRate(float hz) { }
    @Override public void sendClipboard(byte[] data) { }
    @Override public void sendTextInput(byte[] data) { }
    @Override public void setMicEnabled(boolean enabled) { }
    @Override public void setAudioLatency(int speakerMs, int micMs) { }
    @Override public void setAudioKeepalive(boolean enabled) { }
}
