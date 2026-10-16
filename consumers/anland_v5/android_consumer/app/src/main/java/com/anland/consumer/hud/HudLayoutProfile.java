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
        portraitLayout = createDefaultPortraitLayout();
        landscapeLayout = createConsoleGamepadLayout(true);
    }

    /**
     * User's actual live Portrait Layout:
     * - Right pill Super Gesture widget (Super+Left, Super+Right, Super+F, Toggle IME)
     * - Quick action buttons: Q (Super+Q), OV (Super+O / Overview), CP (Ctrl+Shift+V / Copy-Paste)
     */
    public static HudLayout createDefaultPortraitLayout() {
        HudLayout layout = new HudLayout();
        layout.dockEnabled = false;

        // 1. Super Gesture Bar (Round Pill on right side)
        HudButton gesture = new HudButton();
        gesture.widgetType = HudButton.WIDGET_SUPER_GESTURE;
        gesture.label = "";
        gesture.posXPercent = 0.817f;
        gesture.posYPercent = 0.397f;
        gesture.widthDp = 120;
        gesture.heightDp = 30;
        gesture.cornerRadiusDp = 29;
        gesture.opacity = 0.10f;
        gesture.bgColor = 0xE62A2B3D;
        gesture.textColor = 0xFFFFFFFF;
        gesture.activeColor = 0xFF80DEEA;
        gesture.action = HudAction.key(0);
        gesture.swipeLeftAction = HudAction.combo(125, 105);   // Super + Left
        gesture.swipeRightAction = HudAction.combo(125, 106);  // Super + Right
        gesture.swipeUpAction = HudAction.combo(125, 33);      // Super + F (Fullscreen)
        gesture.swipeDownAction = HudAction.system("toggle_ime"); // Toggle Soft Keyboard
        gesture.swipeThresholdDp = 30;
        layout.floatingButtons.add(gesture);

        // 2. Q button (Super + Q / Settings)
        HudButton btnQ = new HudButton();
        btnQ.label = "Q";
        btnQ.posXPercent = 0.919f;
        btnQ.posYPercent = 0.437f;
        btnQ.widthDp = 36;
        btnQ.heightDp = 36;
        btnQ.cornerRadiusDp = 8;
        btnQ.opacity = 0.10f;
        btnQ.bgColor = 0xE62A2B3D;
        btnQ.textColor = 0xFFFFFFFF;
        btnQ.activeColor = 0xFF80DEEA;
        btnQ.action = HudAction.combo(125, 16); // Super + Q (Close window)
        btnQ.popupAction = HudAction.system("open_settings");
        layout.floatingButtons.add(btnQ);

        // 3. OV button (Super + O / Enter)
        HudButton btnOv = new HudButton();
        btnOv.label = "OV";
        btnOv.posXPercent = 0.812f;
        btnOv.posYPercent = 0.437f;
        btnOv.widthDp = 36;
        btnOv.heightDp = 36;
        btnOv.cornerRadiusDp = 8;
        btnOv.opacity = 0.10f;
        btnOv.bgColor = 0xE62A2B3D;
        btnOv.textColor = 0xFFFFFFFF;
        btnOv.activeColor = 0xFF80DEEA;
        btnOv.action = HudAction.combo(125, 24); // Super + O (Overview)
        btnOv.popupAction = HudAction.key(28);   // Enter
        layout.floatingButtons.add(btnOv);

        // 4. CP button (Ctrl+Shift+V / Ctrl+Shift+C)
        HudButton btnCp = new HudButton();
        btnCp.label = "CP";
        btnCp.posXPercent = 0.715f;
        btnCp.posYPercent = 0.437f;
        btnCp.widthDp = 36;
        btnCp.heightDp = 36;
        btnCp.cornerRadiusDp = 8;
        btnCp.opacity = 0.10f;
        btnCp.bgColor = 0xE62A2B3D;
        btnCp.textColor = 0xFFFFFFFF;
        btnCp.activeColor = 0xFF80DEEA;
        btnCp.action = HudAction.combo(29, 42, 47);      // Ctrl + Shift + V (Paste)
        btnCp.popupAction = HudAction.combo(29, 42, 46); // Ctrl + Shift + C (Copy)
        layout.floatingButtons.add(btnCp);

        return layout;
    }

    /**
     * Ergonomic Console Gamepad Preset:
     * - Both sides inset by ~10% from screen borders for comfortable thumb reach and zero palm clipping.
     * - Left side: L1/L2 bumpers, D-Pad 4-way cross, KB toggle, Select.
     * - Right side: R1/R2 bumpers, ABXY diamond cluster (44dp circular buttons), Right-Stick Trackpoint, Start, Home.
     */
    public static HudLayout createConsoleGamepadLayout(boolean isLandscape) {
        HudLayout layout = new HudLayout();
        layout.dockEnabled = false;

        float leftBaseX = isLandscape ? 0.15f : 0.22f;
        float rightBaseX = isLandscape ? 0.85f : 0.78f;
        float shoulderLeftX = isLandscape ? 0.13f : 0.18f;
        float shoulderRightX = isLandscape ? 0.87f : 0.82f;

        // ===== Left Gutter (D-Pad & Bumpers) =====
        // 1. L1 (Bumper / LB)
        HudButton btnL1 = new HudButton();
        btnL1.label = "L1";
        btnL1.action = HudAction.gamepadBtn(310); // BTN_TL (LB)
        btnL1.widthDp = 52;
        btnL1.heightDp = 34;
        btnL1.cornerRadiusDp = 10;
        btnL1.posXPercent = shoulderLeftX;
        btnL1.posYPercent = 0.14f;
        layout.floatingButtons.add(btnL1);

        // 2. L2 (Trigger / LT)
        HudButton btnL2 = new HudButton();
        btnL2.label = "L2";
        btnL2.action = HudAction.gamepadBtn(312); // BTN_TL2 (LT)
        btnL2.widthDp = 52;
        btnL2.heightDp = 34;
        btnL2.cornerRadiusDp = 10;
        btnL2.posXPercent = shoulderLeftX;
        btnL2.posYPercent = 0.26f;
        layout.floatingButtons.add(btnL2);

        // 3. D-Pad (Center at leftBaseX, Y = 0.65f) - Left margin >= 0.095f (~10%)
        HudButton dpadUp = new HudButton();
        dpadUp.label = "▲";
        dpadUp.action = HudAction.gamepadBtn(544); // BTN_DPAD_UP
        dpadUp.action.repeat = true;
        dpadUp.widthDp = 44;
        dpadUp.heightDp = 44;
        dpadUp.cornerRadiusDp = 12;
        dpadUp.posXPercent = leftBaseX;
        dpadUp.posYPercent = 0.52f;
        layout.floatingButtons.add(dpadUp);

        HudButton dpadDown = new HudButton();
        dpadDown.label = "▼";
        dpadDown.action = HudAction.gamepadBtn(545); // BTN_DPAD_DOWN
        dpadDown.action.repeat = true;
        dpadDown.widthDp = 44;
        dpadDown.heightDp = 44;
        dpadDown.cornerRadiusDp = 12;
        dpadDown.posXPercent = leftBaseX;
        dpadDown.posYPercent = 0.78f;
        layout.floatingButtons.add(dpadDown);

        HudButton dpadLeft = new HudButton();
        dpadLeft.label = "◀";
        dpadLeft.action = HudAction.gamepadBtn(546); // BTN_DPAD_LEFT
        dpadLeft.action.repeat = true;
        dpadLeft.widthDp = 44;
        dpadLeft.heightDp = 44;
        dpadLeft.cornerRadiusDp = 12;
        dpadLeft.posXPercent = leftBaseX - (isLandscape ? 0.055f : 0.09f); // >= 0.095f in landscape
        dpadLeft.posYPercent = 0.65f;
        layout.floatingButtons.add(dpadLeft);

        HudButton dpadRight = new HudButton();
        dpadRight.label = "▶";
        dpadRight.action = HudAction.gamepadBtn(547); // BTN_DPAD_RIGHT
        dpadRight.action.repeat = true;
        dpadRight.widthDp = 44;
        dpadRight.heightDp = 44;
        dpadRight.cornerRadiusDp = 12;
        dpadRight.posXPercent = leftBaseX + (isLandscape ? 0.055f : 0.09f);
        dpadRight.posYPercent = 0.65f;
        layout.floatingButtons.add(dpadRight);

        // 4. Utility Controls: moved to center-bottom row so area below D-Pad is 100% clear
        HudButton btnSelect = new HudButton();
        btnSelect.label = "SELECT";
        btnSelect.action = HudAction.gamepadBtn(314); // BTN_SELECT
        btnSelect.widthDp = 50;
        btnSelect.heightDp = 30;
        btnSelect.cornerRadiusDp = 8;
        btnSelect.posXPercent = isLandscape ? 0.32f : 0.22f;
        btnSelect.posYPercent = 0.90f;
        layout.floatingButtons.add(btnSelect);

        HudButton btnVk = new HudButton();
        btnVk.label = "KB";
        btnVk.action = HudAction.system("toggle_vk");
        btnVk.widthDp = 46;
        btnVk.heightDp = 30;
        btnVk.cornerRadiusDp = 8;
        btnVk.posXPercent = isLandscape ? 0.42f : 0.38f;
        btnVk.posYPercent = 0.90f;
        layout.floatingButtons.add(btnVk);

        HudButton btnSettings = new HudButton();
        btnSettings.label = "⚙";
        btnSettings.action = HudAction.system("open_settings");
        btnSettings.widthDp = 46;
        btnSettings.heightDp = 30;
        btnSettings.cornerRadiusDp = 8;
        btnSettings.posXPercent = isLandscape ? 0.58f : 0.62f;
        btnSettings.posYPercent = 0.90f;
        layout.floatingButtons.add(btnSettings);

        HudButton btnStart = new HudButton();
        btnStart.label = "START";
        btnStart.action = HudAction.gamepadBtn(315); // BTN_START
        btnStart.widthDp = 50;
        btnStart.heightDp = 30;
        btnStart.cornerRadiusDp = 8;
        btnStart.posXPercent = isLandscape ? 0.68f : 0.78f;
        btnStart.posYPercent = 0.90f;
        layout.floatingButtons.add(btnStart);

        // ===== Right Gutter (Action Diamond & Bumpers) =====
        // 1. R1 (Bumper / RB)
        HudButton btnR1 = new HudButton();
        btnR1.label = "R1";
        btnR1.action = HudAction.gamepadBtn(311); // BTN_TR (RB)
        btnR1.widthDp = 52;
        btnR1.heightDp = 34;
        btnR1.cornerRadiusDp = 10;
        btnR1.posXPercent = shoulderRightX;
        btnR1.posYPercent = 0.14f;
        layout.floatingButtons.add(btnR1);

        // 2. R2 (Trigger / RT)
        HudButton btnR2 = new HudButton();
        btnR2.label = "R2";
        btnR2.action = HudAction.gamepadBtn(313); // BTN_TR2 (RT)
        btnR2.widthDp = 52;
        btnR2.heightDp = 34;
        btnR2.cornerRadiusDp = 10;
        btnR2.posXPercent = shoulderRightX;
        btnR2.posYPercent = 0.26f;
        layout.floatingButtons.add(btnR2);

        // 3. Right Stick TrackPoint (Mouse Look)
        HudButton rightStick = new HudButton();
        rightStick.widgetType = HudButton.WIDGET_TRACKPOINT;
        rightStick.label = "MOUSE";
        rightStick.widthDp = 56;
        rightStick.heightDp = 56;
        rightStick.cornerRadiusDp = 28;
        rightStick.posXPercent = rightBaseX - (isLandscape ? 0.12f : 0.15f);
        rightStick.posYPercent = 0.35f;
        layout.floatingButtons.add(rightStick);

        // 4. ABXY Diamond Cluster (Center at rightBaseX, Y = 0.65f) - Right margin <= 0.905f (~10%)
        HudButton btnY = new HudButton();
        btnY.label = "Y";
        btnY.action = HudAction.gamepadBtn(307); // BTN_Y
        btnY.widthDp = 44;
        btnY.heightDp = 44;
        btnY.cornerRadiusDp = 22;
        btnY.posXPercent = rightBaseX;
        btnY.posYPercent = 0.52f;
        layout.floatingButtons.add(btnY);

        HudButton btnA = new HudButton();
        btnA.label = "A";
        btnA.action = HudAction.gamepadBtn(304); // BTN_A
        btnA.widthDp = 44;
        btnA.heightDp = 44;
        btnA.cornerRadiusDp = 22;
        btnA.posXPercent = rightBaseX;
        btnA.posYPercent = 0.78f;
        layout.floatingButtons.add(btnA);

        HudButton btnX = new HudButton();
        btnX.label = "X";
        btnX.action = HudAction.gamepadBtn(308); // BTN_X
        btnX.widthDp = 44;
        btnX.heightDp = 44;
        btnX.cornerRadiusDp = 22;
        btnX.posXPercent = rightBaseX - (isLandscape ? 0.055f : 0.09f);
        btnX.posYPercent = 0.65f;
        layout.floatingButtons.add(btnX);

        HudButton btnB = new HudButton();
        btnB.label = "B";
        btnB.action = HudAction.gamepadBtn(305); // BTN_B
        btnB.widthDp = 44;
        btnB.heightDp = 44;
        btnB.cornerRadiusDp = 22;
        btnB.posXPercent = rightBaseX + (isLandscape ? 0.055f : 0.09f); // <= 0.905f in landscape
        btnB.posYPercent = 0.65f;
        layout.floatingButtons.add(btnB);

        // 5. Home
        HudButton btnHome = new HudButton();
        btnHome.label = "HOME";
        btnHome.action = HudAction.gamepadBtn(316); // BTN_MODE (Guide)
        btnHome.popupAction = HudAction.system("open_settings");
        btnHome.widthDp = 44;
        btnHome.heightDp = 30;
        btnHome.cornerRadiusDp = 8;
        btnHome.posXPercent = shoulderRightX;
        btnHome.posYPercent = 0.90f;
        layout.floatingButtons.add(btnHome);

        return layout;
    }

    /**
     * Ergonomic Productivity & Niri Desktop Preset (10% Margin):
     * - Left side: TrackPoint Mouse Nub, ESC, TAB, CTRL, ALT.
     * - Right side: Super Gesture Bar, Q (Super+Q), OV (Super+O), CP (Copy-Paste), ENTER, BKSP, KB, Settings.
     */
    public static HudLayout createProductivityLayout(boolean isLandscape) {
        HudLayout layout = new HudLayout();
        layout.dockEnabled = false;

        float leftX = isLandscape ? 0.13f : 0.18f;
        float rightX = isLandscape ? 0.87f : 0.82f;

        // Left Side Controls (10% Margin)
        HudButton mouseNub = new HudButton();
        mouseNub.widgetType = HudButton.WIDGET_TRACKPOINT;
        mouseNub.label = "MOUSE";
        mouseNub.widthDp = 62;
        mouseNub.heightDp = 62;
        mouseNub.cornerRadiusDp = 31;
        mouseNub.posXPercent = isLandscape ? 0.15f : 0.20f;
        mouseNub.posYPercent = 0.68f;
        layout.floatingButtons.add(mouseNub);

        HudButton btnEsc = new HudButton();
        btnEsc.label = "ESC";
        btnEsc.action = HudAction.key(1);
        btnEsc.widthDp = 48;
        btnEsc.heightDp = 34;
        btnEsc.cornerRadiusDp = 8;
        btnEsc.posXPercent = leftX;
        btnEsc.posYPercent = 0.16f;
        layout.floatingButtons.add(btnEsc);

        HudButton btnTab = new HudButton();
        btnTab.label = "TAB";
        btnTab.action = HudAction.key(15);
        btnTab.widthDp = 48;
        btnTab.heightDp = 34;
        btnTab.cornerRadiusDp = 8;
        btnTab.posXPercent = leftX;
        btnTab.posYPercent = 0.28f;
        layout.floatingButtons.add(btnTab);

        HudButton btnCtrl = new HudButton();
        btnCtrl.label = "CTRL";
        btnCtrl.action = HudAction.modifier(29);
        btnCtrl.widthDp = 48;
        btnCtrl.heightDp = 34;
        btnCtrl.cornerRadiusDp = 8;
        btnCtrl.posXPercent = leftX;
        btnCtrl.posYPercent = 0.40f;
        layout.floatingButtons.add(btnCtrl);

        HudButton btnAlt = new HudButton();
        btnAlt.label = "ALT";
        btnAlt.action = HudAction.modifier(56);
        btnAlt.widthDp = 48;
        btnAlt.heightDp = 34;
        btnAlt.cornerRadiusDp = 8;
        btnAlt.posXPercent = leftX + (isLandscape ? 0.07f : 0.12f);
        btnAlt.posYPercent = 0.40f;
        layout.floatingButtons.add(btnAlt);

        // Right Side Controls (10% Margin)
        HudButton superGesture = new HudButton();
        superGesture.widgetType = HudButton.WIDGET_SUPER_GESTURE;
        superGesture.label = "SUPER";
        superGesture.widthDp = 120;
        superGesture.heightDp = 32;
        superGesture.cornerRadiusDp = 29;
        superGesture.posXPercent = isLandscape ? 0.85f : 0.80f;
        superGesture.posYPercent = 0.16f;
        superGesture.swipeLeftAction = HudAction.combo(125, 105);
        superGesture.swipeRightAction = HudAction.combo(125, 106);
        superGesture.swipeUpAction = HudAction.combo(125, 33);
        superGesture.swipeDownAction = HudAction.system("toggle_ime");
        layout.floatingButtons.add(superGesture);

        HudButton btnQ = new HudButton();
        btnQ.label = "Q";
        btnQ.action = HudAction.combo(125, 16);
        btnQ.popupAction = HudAction.system("open_settings");
        btnQ.widthDp = 38;
        btnQ.heightDp = 38;
        btnQ.cornerRadiusDp = 8;
        btnQ.posXPercent = rightX;
        btnQ.posYPercent = 0.36f;
        layout.floatingButtons.add(btnQ);

        HudButton btnOv = new HudButton();
        btnOv.label = "OV";
        btnOv.action = HudAction.combo(125, 24);
        btnOv.popupAction = HudAction.key(28);
        btnOv.widthDp = 38;
        btnOv.heightDp = 38;
        btnOv.cornerRadiusDp = 8;
        btnOv.posXPercent = rightX - (isLandscape ? 0.06f : 0.10f);
        btnOv.posYPercent = 0.36f;
        layout.floatingButtons.add(btnOv);

        HudButton btnCp = new HudButton();
        btnCp.label = "CP";
        btnCp.action = HudAction.combo(29, 42, 47);
        btnCp.popupAction = HudAction.combo(29, 42, 46);
        btnCp.widthDp = 38;
        btnCp.heightDp = 38;
        btnCp.cornerRadiusDp = 8;
        btnCp.posXPercent = rightX - (isLandscape ? 0.12f : 0.20f);
        btnCp.posYPercent = 0.36f;
        layout.floatingButtons.add(btnCp);

        HudButton btnEnter = new HudButton();
        btnEnter.label = "ENTER";
        btnEnter.action = HudAction.key(28);
        btnEnter.widthDp = 58;
        btnEnter.heightDp = 38;
        btnEnter.cornerRadiusDp = 8;
        btnEnter.posXPercent = rightX;
        btnEnter.posYPercent = 0.65f;
        layout.floatingButtons.add(btnEnter);

        HudButton btnBksp = new HudButton();
        btnBksp.label = "BKSP";
        btnBksp.action = HudAction.key(14);
        btnBksp.widthDp = 58;
        btnBksp.heightDp = 38;
        btnBksp.cornerRadiusDp = 8;
        btnBksp.posXPercent = rightX - (isLandscape ? 0.09f : 0.15f);
        btnBksp.posYPercent = 0.65f;
        layout.floatingButtons.add(btnBksp);

        HudButton btnVk = new HudButton();
        btnVk.label = "KB";
        btnVk.action = HudAction.system("toggle_vk");
        btnVk.widthDp = 58;
        btnVk.heightDp = 34;
        btnVk.cornerRadiusDp = 8;
        btnVk.posXPercent = rightX;
        btnVk.posYPercent = 0.85f;
        layout.floatingButtons.add(btnVk);

        HudButton btnSet = new HudButton();
        btnSet.label = "⚙";
        btnSet.action = HudAction.system("open_settings");
        btnSet.widthDp = 58;
        btnSet.heightDp = 34;
        btnSet.cornerRadiusDp = 8;
        btnSet.posXPercent = rightX - (isLandscape ? 0.09f : 0.15f);
        btnSet.posYPercent = 0.85f;
        layout.floatingButtons.add(btnSet);

        return layout;
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
            if (p.portraitLayout.floatingButtons.isEmpty()) {
                p.portraitLayout = createDefaultPortraitLayout();
            }
            if (p.landscapeLayout.floatingButtons.isEmpty()) {
                p.landscapeLayout = createConsoleGamepadLayout(true);
            }
            return p;
        } catch (Exception e) {
            return new HudLayoutProfile();
        }
    }
}
