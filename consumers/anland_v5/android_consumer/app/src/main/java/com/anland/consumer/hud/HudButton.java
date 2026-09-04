package com.anland.consumer.hud;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Model representing a single customizable on-screen widget (Button, TrackPoint, Super Gesture).
 */
public final class HudButton {
    public static final String WIDGET_STANDARD = "button";
    public static final String WIDGET_SUPER_GESTURE = "super_gesture";
    public static final String WIDGET_TRACKPOINT = "trackpoint";

    // Strongly-typed mirror of WIDGET_* above, used by code that switches
    // over the widget kind (e.g. the toolbar's add-widget chooser).
    public enum WidgetKind {
        KEY,           // WIDGET_STANDARD
        SUPER_GESTURE, // WIDGET_SUPER_GESTURE
        TRACKPOINT     // WIDGET_TRACKPOINT
    }

    public String id = UUID.randomUUID().toString();
    public String widgetType = WIDGET_STANDARD;
    public String label = "BTN";
    
    // Position as screen percentages [0.0 - 1.0] for resolution/rotation independence
    public float posXPercent = 0.5f;
    public float posYPercent = 0.5f;

    // Size in density-independent pixels (dp)
    public int widthDp = 64;
    public int heightDp = 40;
    public int cornerRadiusDp = 8;
    public float opacity = 0.85f;

    // Styling colors (ARGB hex)
    public int bgColor = 0xE62A2B3D;
    public int textColor = 0xFFFFFFFF;
    public int activeColor = 0xFF80DEEA;

    // Action mappings
    public HudAction action = new HudAction();
    public HudAction popupAction = null; // optional swipe-up secondary action

    // Super gesture specific mappings
    public HudAction swipeLeftAction = HudAction.combo(125, 105);   // Super + Left
    public HudAction swipeRightAction = HudAction.combo(125, 106);  // Super + Right
    public HudAction swipeUpAction = HudAction.combo(125, 24);      // Super + O (Overview)
    public HudAction swipeDownAction = HudAction.combo(125, 57);    // Super + Space (Launcher)
    public int swipeThresholdDp = 30;

    // TrackPoint specific mappings
    public float trackpointSensitivity = 1.5f;
    public int trackpointDeadzoneDp = 4;
    // TrackPoint mode: "mouse" sends relative pointer motion (default),
    // "scroll" sends scroll-wheel events instead so the same nub can be
    // repurposed as a thumb-scroll pad.
    public static final String MODE_MOUSE = "mouse";
    public static final String MODE_SCROLL = "scroll";
    public String trackpointMode = MODE_MOUSE;

    public HudButton() {}

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("widget_type", widgetType);
        obj.put("label", label);
        obj.put("pos_x_pct", posXPercent);
        obj.put("pos_y_pct", posYPercent);
        obj.put("width_dp", widthDp);
        obj.put("height_dp", heightDp);
        obj.put("corner_radius_dp", cornerRadiusDp);
        obj.put("opacity", opacity);
        obj.put("bg_color", bgColor);
        obj.put("text_color", textColor);
        obj.put("active_color", activeColor);
        obj.put("action", action.toJSON());
        if (popupAction != null) {
            obj.put("popup_action", popupAction.toJSON());
        }

        if (WIDGET_SUPER_GESTURE.equals(widgetType)) {
            obj.put("swipe_left", swipeLeftAction.toJSON());
            obj.put("swipe_right", swipeRightAction.toJSON());
            obj.put("swipe_up", swipeUpAction.toJSON());
            obj.put("swipe_down", swipeDownAction.toJSON());
            obj.put("swipe_threshold_dp", swipeThresholdDp);
        } else if (WIDGET_TRACKPOINT.equals(widgetType)) {
            obj.put("trackpoint_sensitivity", trackpointSensitivity);
            obj.put("trackpoint_deadzone_dp", trackpointDeadzoneDp);
            obj.put("trackpoint_mode", trackpointMode != null ? trackpointMode : MODE_MOUSE);
        }
        return obj;
    }

    public static HudButton fromJSON(JSONObject obj) {
        if (obj == null) return null;
        HudButton b = new HudButton();
        b.id = obj.optString("id", UUID.randomUUID().toString());
        b.widgetType = obj.optString("widget_type", WIDGET_STANDARD);
        b.label = obj.optString("label", "BTN");
        b.posXPercent = (float) obj.optDouble("pos_x_pct", 0.5);
        b.posYPercent = (float) obj.optDouble("pos_y_pct", 0.5);
        b.widthDp = obj.optInt("width_dp", 64);
        b.heightDp = obj.optInt("height_dp", 40);
        b.cornerRadiusDp = obj.optInt("corner_radius_dp", 8);
        b.opacity = (float) obj.optDouble("opacity", 0.85);
        b.bgColor = obj.optInt("bg_color", 0xE62A2B3D);
        b.textColor = obj.optInt("text_color", 0xFFFFFFFF);
        b.activeColor = obj.optInt("active_color", 0xFF80DEEA);
        b.action = HudAction.fromJSON(obj.optJSONObject("action"));
        if (obj.has("popup_action")) {
            b.popupAction = HudAction.fromJSON(obj.optJSONObject("popup_action"));
        }

        if (obj.has("swipe_left")) b.swipeLeftAction = HudAction.fromJSON(obj.optJSONObject("swipe_left"));
        if (obj.has("swipe_right")) b.swipeRightAction = HudAction.fromJSON(obj.optJSONObject("swipe_right"));
        if (obj.has("swipe_up")) b.swipeUpAction = HudAction.fromJSON(obj.optJSONObject("swipe_up"));
        if (obj.has("swipe_down")) b.swipeDownAction = HudAction.fromJSON(obj.optJSONObject("swipe_down"));
        b.swipeThresholdDp = obj.optInt("swipe_threshold_dp", 30);

        b.trackpointSensitivity = (float) obj.optDouble("trackpoint_sensitivity", 1.5);
        b.trackpointDeadzoneDp = obj.optInt("trackpoint_deadzone_dp", 4);
        // Default to mouse mode when an old profile does not record a mode.
        b.trackpointMode = obj.optString("trackpoint_mode", MODE_MOUSE);
        return b;
    }

    public HudButton duplicate() {
        HudButton d = new HudButton();
        d.id = UUID.randomUUID().toString();
        d.widgetType = this.widgetType;
        d.label = this.label;
        d.posXPercent = Math.min(0.95f, this.posXPercent + 0.04f);
        d.posYPercent = Math.min(0.95f, this.posYPercent + 0.04f);
        d.widthDp = this.widthDp;
        d.heightDp = this.heightDp;
        d.cornerRadiusDp = this.cornerRadiusDp;
        d.opacity = this.opacity;
        d.bgColor = this.bgColor;
        d.textColor = this.textColor;
        d.activeColor = this.activeColor;
        d.action = this.action.copy();
        if (this.popupAction != null) d.popupAction = this.popupAction.copy();
        d.swipeLeftAction = this.swipeLeftAction.copy();
        d.swipeRightAction = this.swipeRightAction.copy();
        d.swipeUpAction = this.swipeUpAction.copy();
        d.swipeDownAction = this.swipeDownAction.copy();
        d.swipeThresholdDp = this.swipeThresholdDp;
        d.trackpointSensitivity = this.trackpointSensitivity;
        d.trackpointDeadzoneDp = this.trackpointDeadzoneDp;
        d.trackpointMode = this.trackpointMode;
        return d;
    }
}
