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
        // --- Portrait Default ---
        initDockDefaults(portraitLayout);
        
        // Portrait floating buttons
        HudButton pSuperGesture = new HudButton();
        pSuperGesture.widgetType = HudButton.WIDGET_SUPER_GESTURE;
        pSuperGesture.label = "SUPER";
        pSuperGesture.posXPercent = 0.88f;
        pSuperGesture.posYPercent = 0.40f;
        pSuperGesture.widthDp = 58;
        pSuperGesture.heightDp = 58;
        pSuperGesture.cornerRadiusDp = 29; // circular
        pSuperGesture.bgColor = 0xE61E1E2E;
        pSuperGesture.action = HudAction.modifier(125); // Super key
        portraitLayout.floatingButtons.add(pSuperGesture);

        HudButton pOverview = new HudButton();
        pOverview.label = "OVERVIEW";
        pOverview.posXPercent = 0.88f;
        pOverview.posYPercent = 0.50f;
        pOverview.widthDp = 68;
        pOverview.heightDp = 38;
        pOverview.action = HudAction.combo(125, 24); // Super + O
        portraitLayout.floatingButtons.add(pOverview);

        HudButton pTerm = new HudButton();
        pTerm.label = "TERM";
        pTerm.posXPercent = 0.88f;
        pTerm.posYPercent = 0.58f;
        pTerm.widthDp = 68;
        pTerm.heightDp = 38;
        pTerm.action = HudAction.combo(125, 28); // Super + Enter
        portraitLayout.floatingButtons.add(pTerm);

        HudButton pClose = new HudButton();
        pClose.label = "CLOSE";
        pClose.posXPercent = 0.88f;
        pClose.posYPercent = 0.66f;
        pClose.widthDp = 68;
        pClose.heightDp = 38;
        pClose.bgColor = 0xE6882020;
        pClose.action = HudAction.combo(125, 42, 16); // Super + Shift + Q
        portraitLayout.floatingButtons.add(pClose);

        // --- Landscape Default ---
        initDockDefaults(landscapeLayout);

        HudButton lSuperGesture = new HudButton();
        lSuperGesture.widgetType = HudButton.WIDGET_SUPER_GESTURE;
        lSuperGesture.label = "SUPER";
        lSuperGesture.posXPercent = 0.92f;
        lSuperGesture.posYPercent = 0.40f;
        lSuperGesture.widthDp = 60;
        lSuperGesture.heightDp = 60;
        lSuperGesture.cornerRadiusDp = 30;
        lSuperGesture.bgColor = 0xE61E1E2E;
        lSuperGesture.action = HudAction.modifier(125);
        landscapeLayout.floatingButtons.add(lSuperGesture);

        HudButton lOverview = new HudButton();
        lOverview.label = "OVERVIEW";
        lOverview.posXPercent = 0.92f;
        lOverview.posYPercent = 0.53f;
        lOverview.widthDp = 72;
        lOverview.heightDp = 38;
        lOverview.action = HudAction.combo(125, 24);
        landscapeLayout.floatingButtons.add(lOverview);

        HudButton lTerm = new HudButton();
        lTerm.label = "TERM";
        lTerm.posXPercent = 0.92f;
        lTerm.posYPercent = 0.63f;
        lTerm.widthDp = 72;
        lTerm.heightDp = 38;
        lTerm.action = HudAction.combo(125, 28);
        landscapeLayout.floatingButtons.add(lTerm);

        HudButton lClose = new HudButton();
        lClose.label = "CLOSE";
        lClose.posXPercent = 0.92f;
        lClose.posYPercent = 0.73f;
        lClose.widthDp = 72;
        lClose.heightDp = 38;
        lClose.bgColor = 0xE6882020;
        lClose.action = HudAction.combo(125, 42, 16);
        landscapeLayout.floatingButtons.add(lClose);

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
