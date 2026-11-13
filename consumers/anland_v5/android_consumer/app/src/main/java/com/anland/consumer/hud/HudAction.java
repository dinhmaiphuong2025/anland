package com.anland.consumer.hud;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates an action triggered by a HUD widget (single key, combo macro, text, or system command).
 */
public final class HudAction {
    public static final String TYPE_KEY = "key";
    public static final String TYPE_MODIFIER = "modifier";
    public static final String TYPE_COMBO = "combo";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_SYSTEM = "system";
    /** Pointer button (BTN_LEFT 0x110, BTN_RIGHT 0x111, BTN_MIDDLE 0x112).
     *  Dispatched on the press and release edges of the widget, so holding the
     *  key holds the button - that is what makes a two-thumb drag possible. */
    public static final String TYPE_MOUSE = "mouse";

    public String type = TYPE_KEY;
    public int code = 0; // evdev scancode for key / modifier
    public boolean repeat = false;
    public String text = "";
    public String systemCommand = ""; // e.g. "toggle_ime", "toggle_vk", "open_settings", "mouse_left", "mouse_right"
    public List<Integer> comboKeys = new ArrayList<>();

    public HudAction() {}

    public static HudAction key(int evdevCode) {
        HudAction a = new HudAction();
        a.type = TYPE_KEY;
        a.code = evdevCode;
        return a;
    }

    public static HudAction modifier(int evdevCode) {
        HudAction a = new HudAction();
        a.type = TYPE_MODIFIER;
        a.code = evdevCode;
        return a;
    }

    public static HudAction combo(int... evdevCodes) {
        HudAction a = new HudAction();
        a.type = TYPE_COMBO;
        for (int c : evdevCodes) a.comboKeys.add(c);
        return a;
    }

    public static HudAction text(String text) {
        HudAction a = new HudAction();
        a.type = TYPE_TEXT;
        a.text = text;
        return a;
    }

    public static HudAction mouse(int buttonCode) {
        HudAction a = new HudAction();
        a.type = TYPE_MOUSE;
        a.code = buttonCode;
        return a;
    }

    public static HudAction system(String command) {
        HudAction a = new HudAction();
        a.type = TYPE_SYSTEM;
        a.systemCommand = command;
        return a;
    }

    static boolean isRemovedDeckCommand(JSONObject obj) {
        if (obj == null || !TYPE_SYSTEM.equals(obj.optString("type"))) return false;
        String command = obj.optString("command");
        return "toggle_steam_deck".equals(command) || "exit_steam_deck".equals(command);
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        if (TYPE_KEY.equals(type) || TYPE_MODIFIER.equals(type) || TYPE_MOUSE.equals(type)) {
            obj.put("code", code);
            obj.put("repeat", repeat);
        } else if (TYPE_COMBO.equals(type)) {
            JSONArray arr = new JSONArray();
            for (int k : comboKeys) arr.put(k);
            obj.put("combo_keys", arr);
        } else if (TYPE_TEXT.equals(type)) {
            obj.put("text", text);
        } else if (TYPE_SYSTEM.equals(type)) {
            obj.put("command", systemCommand);
        }
        return obj;
    }

    public static HudAction fromJSON(JSONObject obj) {
        if (obj == null) return key(0);
        // Old profiles can still contain the removed mode in any action slot.
        if (isRemovedDeckCommand(obj)) return key(0);
        HudAction a = new HudAction();
        a.type = obj.optString("type", TYPE_KEY);
        if ("gamepad".equals(a.type)) return key(0);
        a.code = obj.optInt("code", 0);
        a.repeat = obj.optBoolean("repeat", false);
        a.text = obj.optString("text", "");
        a.systemCommand = obj.optString("command", "");
        JSONArray arr = obj.optJSONArray("combo_keys");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                a.comboKeys.add(arr.optInt(i));
            }
        }
        // Profiles written before pointer buttons became a real action type still carry the
        // click-only system command. Promote them so holding the key holds the button.
        if (TYPE_SYSTEM.equals(a.type)) {
            if ("mouse_left".equals(a.systemCommand)) return mouse(0x110);
            if ("mouse_right".equals(a.systemCommand)) return mouse(0x111);
            if ("mouse_middle".equals(a.systemCommand)) return mouse(0x112);
        }
        return a;
    }

    public HudAction copy() {
        HudAction c = new HudAction();
        c.type = this.type;
        c.code = this.code;
        c.repeat = this.repeat;
        c.text = this.text;
        c.systemCommand = this.systemCommand;
        c.comboKeys.addAll(this.comboKeys);
        return c;
    }
}
