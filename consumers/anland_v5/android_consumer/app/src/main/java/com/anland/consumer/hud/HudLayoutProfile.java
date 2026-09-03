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
        // Reset to a clean state: no floating buttons at all. The dock strip
        // was removed in a later refactor; the legacy ExtraKeysBar is what
        // the user actually wants on the bottom row now. Floating widgets
        // (TrackPoint, Super Gesture, freeform buttons) are added on demand
        // from the HUD editor toolbar, so the default profile starts blank.
        portraitLayout = new HudLayout();
        landscapeLayout = new HudLayout();
        // Note: we do NOT call initDockDefaults() any more. Old profiles with
        // dock items still load via fromJSON() for backward compatibility,
        // but new profiles start with empty dock lists.
    }

    // initDockDefaults removed: the dock strip was deleted and the legacy
    // ExtraKeysBar is what the user actually wants on the bottom row now.
    // Old profiles with non-empty dock lists still load via fromJSON() and
    // the dock items are kept in the model for backward compatibility, but
    // they are no longer rendered in the overlay (see HudOverlayView).
    // @SuppressWarnings("unused") keeps the helper method alive for any
    // future migration path that needs to seed the dock list.

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
