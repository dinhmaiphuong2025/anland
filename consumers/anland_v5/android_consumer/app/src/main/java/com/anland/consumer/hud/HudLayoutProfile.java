package com.anland.consumer.hud;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Root profile managing separate Portrait and Landscape layouts, persistence, and default factory presets.
 */
public final class HudLayoutProfile {
    public int version = 2;
    public String profileName = "Niri WM Profile";
    public boolean firstTimeNoticeShown = false;

    public HudLayout portraitLayout = new HudLayout();
    public HudLayout landscapeLayout = new HudLayout();

    public HudLayoutProfile() {
        initDefaults();
    }

    private void initDefaults() {
        // Reset to a clean state: no leftover floating buttons, no leftover
        // dock items. The HUD editor can rebuild whatever the user wants
        // from this minimal starting point.
        portraitLayout = new HudLayout();
        landscapeLayout = new HudLayout();

        // --- Portrait Default ---
        // Only TrackPoint remains on screen by default. The dock strip is
        // hidden at runtime (see HudOverlayView.rebuildActiveLayout), so the
        // dock items below are only kept for editor-time rebinding and for
        // backward-compatible JSON migration.
        initDockDefaults(portraitLayout);

        HudButton pTrackpoint = new HudButton();
        pTrackpoint.widgetType = HudButton.WIDGET_TRACKPOINT;
        pTrackpoint.label = "MOUSE";
        pTrackpoint.posXPercent = 0.08f;
        pTrackpoint.posYPercent = 0.55f;
        pTrackpoint.widthDp = 60;
        pTrackpoint.heightDp = 60;
        pTrackpoint.cornerRadiusDp = 30;
        pTrackpoint.bgColor = 0xE6CC2222;
        portraitLayout.floatingButtons.add(pTrackpoint);

        // --- Landscape Default ---
        initDockDefaults(landscapeLayout);

        HudButton lTrackpoint = new HudButton();
        lTrackpoint.widgetType = HudButton.WIDGET_TRACKPOINT;
        lTrackpoint.label = "MOUSE";
        lTrackpoint.posXPercent = 0.08f;
        lTrackpoint.posYPercent = 0.65f;
        lTrackpoint.widthDp = 64;
        lTrackpoint.heightDp = 64;
        lTrackpoint.cornerRadiusDp = 32;
        lTrackpoint.bgColor = 0xE6CC2222;
        landscapeLayout.floatingButtons.add(lTrackpoint);
    }

    private void initDockDefaults(HudLayout l) {
        l.dockItems.clear();
        l.dockItems.add(createDockBtn("ESC", HudAction.key(1)));
        l.dockItems.add(createDockBtn("TAB", HudAction.key(15)));
        l.dockItems.add(createDockBtn("CTRL", HudAction.modifier(29)));
        l.dockItems.add(createDockBtn("ALT", HudAction.modifier(56)));
        l.dockItems.add(createDockBtn("SUPER", HudAction.modifier(125)));
        
        HudButton slash = createDockBtn("/", HudAction.text("/"));
        slash.popupAction = HudAction.text("\\");
        l.dockItems.add(slash);

        HudButton dash = createDockBtn("-", HudAction.text("-"));
        dash.popupAction = HudAction.text("|");
        l.dockItems.add(dash);

        HudButton left = createDockBtn("←", HudAction.key(105));
        left.action.repeat = true;
        l.dockItems.add(left);

        HudButton down = createDockBtn("↓", HudAction.key(108));
        down.action.repeat = true;
        l.dockItems.add(down);

        HudButton up = createDockBtn("↑", HudAction.key(103));
        up.action.repeat = true;
        l.dockItems.add(up);

        HudButton right = createDockBtn("→", HudAction.key(106));
        right.action.repeat = true;
        l.dockItems.add(right);

        HudButton ime = createDockBtn("KB", HudAction.system("toggle_ime"));
        ime.popupAction = HudAction.system("toggle_vk");
        l.dockItems.add(ime);

        l.dockItems.add(createDockBtn("SET", HudAction.system("open_settings")));
    }

    private HudButton createDockBtn(String label, HudAction action) {
        HudButton b = new HudButton();
        b.label = label;
        b.widthDp = 48;
        b.heightDp = 36;
        b.cornerRadiusDp = 4;
        b.action = action;
        return b;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", version);
        obj.put("profile_name", profileName);
        obj.put("first_time_notice_shown", firstTimeNoticeShown);
        obj.put("portrait_layout", portraitLayout.toJSON());
        obj.put("landscape_layout", landscapeLayout.toJSON());
        return obj;
    }

    public static HudLayoutProfile fromJSON(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new HudLayoutProfile();
        }
        try {
            JSONObject obj = new JSONObject(jsonStr);
            HudLayoutProfile p = new HudLayoutProfile();
            p.version = obj.optInt("version", 2);
            p.profileName = obj.optString("profile_name", "Niri WM Profile");
            p.firstTimeNoticeShown = obj.optBoolean("first_time_notice_shown", false);
            if (obj.has("portrait_layout")) {
                p.portraitLayout = HudLayout.fromJSON(obj.optJSONObject("portrait_layout"));
            }
            if (obj.has("landscape_layout")) {
                p.landscapeLayout = HudLayout.fromJSON(obj.optJSONObject("landscape_layout"));
            }
            return p;
        } catch (Exception e) {
            return new HudLayoutProfile();
        }
    }
}
