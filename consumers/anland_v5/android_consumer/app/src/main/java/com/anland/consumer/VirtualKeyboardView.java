package com.anland.consumer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.anland.consumer.hud.HudAction;
import com.anland.consumer.hud.HudKeyPickerDialog;
import com.anland.consumer.theme.M3;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Virtual Keyboard supporting:
 *   1. Portrait Floating QWERTY Keyboard (movable via drag handle).
 *   2. Landscape Ergonomic Split Arc Keyboard (concentric dual-thumb radial layout).
 *   3. Beveled Top Gutter Wings (customizable action buttons seamlessly contoured to arc curvature).
 *   4. Power-user Animations: Fan Open (quạt mở - default) & Corner Zoom (phóng từ góc).
 */
public class VirtualKeyboardView extends View {

    private static final String TAG = "VirtualKeyboard";
    private static final String PREFS_NAME = "anland_settings";
    public static final String KEY_LANDSCAPE_SPLIT_ARC = "landscape_split_arc";
    public static final String KEY_SPLIT_ARC_ANIM = "split_arc_anim";
    public static final String KEY_SPLIT_ARC_TOP_WINGS = "split_arc_top_wings";

    public static final int ANIM_FAN = 0;
    public static final int ANIM_CORNER_ZOOM = 1;

    // ---------- Portrait Standard QWERTY Layout ----------
    private final String[][] keyboardRows = {
            {"ESC", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"},
            {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "BKSP"},
            {"Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"},
            {"Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"},
            {"Shift", "Z", "X", "C", "V", "B", "N", "M", ",", "UP", ".", "/", "Shift"},
            {"Ctrl", "Alt", "Space", "Alt", "Home", "LT", "DN", "RT", "End", "Ctrl"}
    };

    // ---------- Landscape Split Arc Radial Keyrings (Concentric from bottom corners) ----------
    // Ring 0 is closest to thumb pivot; Ring 4 is outermost.
    private static final String[][] LEFT_ARC_ROWS = {
            {"CtrlL", "AltL", "Space"},
            {"ShiftL", "Z", "X", "C", "V", "B"},
            {"Caps", "A", "S", "D", "F", "G"},
            {"Tab", "Q", "W", "E", "R", "T"},
            {"ESC", "1", "2", "3", "4", "5"}
    };

    private static final String[][] RIGHT_ARC_ROWS = {
            {"Space", "LT", "DN", "RT"},
            {"N", "M", ",", ".", "/", "UP", "ShiftR"},
            {"H", "J", "K", "L", ";", "'", "Enter"},
            {"Y", "U", "I", "O", "P", "[", "]", "\\"},
            {"6", "7", "8", "9", "0", "-", "=", "BKSP"}
    };

    // ---------- Symbols Map ----------
    private static final Map<String, String> SYMBOL_CHAR_MAP = new HashMap<>();
    static {
        String[] digits = {"1","2","3","4","5","6","7","8","9","0"};
        String[] digitSymbols = {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")"};
        for (int i = 0; i < digits.length; i++) {
            SYMBOL_CHAR_MAP.put(digits[i], digitSymbols[i]);
        }
        SYMBOL_CHAR_MAP.put("`", "~");
        SYMBOL_CHAR_MAP.put("-", "_");
        SYMBOL_CHAR_MAP.put("=", "+");
        SYMBOL_CHAR_MAP.put("[", "{");
        SYMBOL_CHAR_MAP.put("]", "}");
        SYMBOL_CHAR_MAP.put("\\", "|");
        SYMBOL_CHAR_MAP.put(";", ":");
        SYMBOL_CHAR_MAP.put("'", "\"");
        SYMBOL_CHAR_MAP.put(",", "<");
        SYMBOL_CHAR_MAP.put(".", ">");
        SYMBOL_CHAR_MAP.put("/", "?");
    }

    private boolean hasSymbol(String label) {
        return SYMBOL_CHAR_MAP.containsKey(label);
    }

    // ---------- Keyboard State ----------
    private boolean leftShiftOn = false;
    private boolean leftCtrlOn  = false;
    private boolean leftAltOn   = false;
    private boolean rightShiftPressed = false;
    private boolean capsLockOn = false;
    private boolean symbolLayerActive = false;

    // Standard portrait keys
    private final List<KeyData> keys = new ArrayList<>();
    // Landscape Split Arc keys
    private final List<KeyData> mArcKeys = new ArrayList<>();
    // Landscape Beveled Top Wing keys
    private final List<KeyData> mWingKeys = new ArrayList<>();

    private final SparseArray<KeyData> activePointers = new SparseArray<>();

    private float lastRawX, lastRawY;
    private boolean isDragging = false;
    private final int dragHandleHeight;

    private int screenWidth, screenHeight;
    private OnKeyEventListener listener;

    private boolean mIsSplitArcMode = false;
    private float mAnimProgress = 1.0f;
    private boolean mIsAnimating = false;
    private ValueAnimator mAnimator = null;

    private final Handler mLongPressHandler = new Handler(Looper.getMainLooper());
    private KeyData mPendingLongPressKey = null;

    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint keyBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint modActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint keyStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int keyHeight;
    private int padding = 4;
    private int cornerRadius = 6;

    private int keyColor = 0xFF2A2B3D;
    private int pressedColor = 0xFF35395E;
    private int modActiveColor = 0xFF80DEEA;
    private int textColor = Color.WHITE;
    private int bgColor = 0xEE181825;

    private static final int HANDLE_COLOR     = 0x66FFFFFF;
    private static final int KEY_STROKE_COLOR = 0x22FFFFFF;

    public interface OnKeyEventListener {
        void onKeyDown(int scanCode);
        void onKeyUp(int scanCode);
    }

    public VirtualKeyboardView(Context context) {
        super(context);
        dragHandleHeight = dpToPx(20);
        updateScreenSize();
        initKeys();
        initPaints();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipToOutline(false);
        bringToFront();
    }

    public void updateScreenSize() {
        try {
            ViewParent pp = getParent();
            if (pp instanceof View) {
                int pw = ((View) pp).getWidth();
                int ph = ((View) pp).getHeight();
                if (pw > 0 && ph > 0) {
                    screenWidth = pw;
                    screenHeight = ph;
                    checkOrientationMode();
                    return;
                }
            }
            Resources res = getResources();
            if (res != null) {
                screenWidth = res.getDisplayMetrics().widthPixels;
                screenHeight = res.getDisplayMetrics().heightPixels;
                checkOrientationMode();
            }
        } catch (Exception e) {
            Log.e(TAG, "updateScreenSize error", e);
            screenWidth = 1920;
            screenHeight = 1080;
        }
    }

    private void checkOrientationMode() {
        int orientation = getResources().getConfiguration().orientation;
        boolean isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        boolean splitPref = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LANDSCAPE_SPLIT_ARC, true);
        mIsSplitArcMode = isLandscape && splitPref;
    }

    public boolean isSplitArcMode() {
        return mIsSplitArcMode;
    }

    public void showWithAnimation() {
        cancelAnimation();
        checkOrientationMode();
        setVisibility(VISIBLE);
        bringToFront();
        mIsAnimating = true;
        mAnimProgress = 0f;

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(180); // Power-user snappy 180ms
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        mAnimator.addUpdateListener(a -> {
            mAnimProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimating = false;
                mAnimProgress = 1f;
                invalidate();
            }
        });
        mAnimator.start();
    }

    public void hideWithAnimation(Runnable onComplete) {
        cancelAnimation();
        mIsAnimating = true;
        mAnimator = ValueAnimator.ofFloat(mAnimProgress, 0f);
        mAnimator.setDuration(150);
        mAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
        mAnimator.addUpdateListener(a -> {
            mAnimProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimating = false;
                mAnimProgress = 0f;
                setVisibility(GONE);
                if (onComplete != null) onComplete.run();
            }
        });
        mAnimator.start();
    }

    private void cancelAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try {
            ViewParent parent = getParent();
            while (parent instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) parent;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
                parent = vg.getParent();
            }
            View root = getRootView();
            if (root instanceof ViewGroup) {
                ((ViewGroup) root).setClipChildren(false);
                ((ViewGroup) root).setClipToPadding(false);
            }
            bringToFront();
        } catch (Exception e) {
            Log.e(TAG, "onAttachedToWindow error", e);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        try {
            super.setVisibility(visibility);
            if (visibility == VISIBLE) {
                checkOrientationMode();
                post(() -> {
                    try {
                        setInitialPosition();
                        applySymbolLayer();
                    } catch (Exception e) {
                        Log.e(TAG, "setVisibility VISIBLE init error", e);
                    }
                });
            } else if (visibility == GONE) {
                leftShiftOn = false;
                leftCtrlOn = false;
                leftAltOn = false;
                rightShiftPressed = false;
                symbolLayerActive = false;
                if (listener != null) {
                    listener.onKeyUp(KeyCodeMapper.getScanCode(KeyEvent.KEYCODE_SHIFT_RIGHT));
                }
                for (KeyData k : keys) {
                    k.modActive = false;
                    k.pressed = false;
                }
                for (KeyData k : mArcKeys) {
                    k.modActive = false;
                    k.pressed = false;
                }
                for (KeyData k : mWingKeys) {
                    k.pressed = false;
                }
                applySymbolLayer();
                invalidate();
            }
        } catch (Exception e) {
            Log.e(TAG, "setVisibility error", e);
        }
    }

    private void initPaints() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setColor(textColor);
        textPaint.setShadowLayer(2, 0, 1, Color.BLACK);
        keyBgPaint.setAntiAlias(true);
        pressedPaint.setAntiAlias(true);
        modActivePaint.setAntiAlias(true);
        modActivePaint.setColor(modActiveColor);
        handlePaint.setColor(HANDLE_COLOR);
        handlePaint.setAntiAlias(true);
        keyBgPaint.setStyle(Paint.Style.FILL);
        keyStrokePaint.setStyle(Paint.Style.STROKE);
        keyStrokePaint.setStrokeWidth(1);
        keyStrokePaint.setColor(KEY_STROKE_COLOR);
    }

    // ========== Initialize Keys ==========
    private void initKeys() {
        // 1. Standard QWERTY keys
        keys.clear();
        for (int r = 0; r < keyboardRows.length; r++) {
            String[] row = keyboardRows[r];
            for (int c = 0; c < row.length; c++) {
                String rawLabel = row[c];
                String internalLabel = resolveInternalLabel(rawLabel, r, c, row.length);
                String displayLabel = resolveDisplayLabel(rawLabel, internalLabel);
                int keyCode = getKeyCodeForLabel(internalLabel);
                float weight = getWeightForLabel(internalLabel);
                boolean hasSym = hasSymbol(rawLabel);

                KeyData k = new KeyData(displayLabel, keyCode, weight, hasSym);
                k.internalLabel = internalLabel;
                k.symbolChar = hasSym ? SYMBOL_CHAR_MAP.get(rawLabel) : null;
                k.currentLabel = displayLabel;
                k.currentKeyCode = keyCode;
                keys.add(k);
            }
        }

        // 2. Arc keys (Left & Right)
        initArcKeys();

        // 3. Top Wing keys
        initTopWings();
    }

    private String resolveInternalLabel(String rawLabel, int r, int c, int rowLen) {
        if (rawLabel.equals("Shift")) {
            if (r == 4) return (c == 0) ? "ShiftL" : "ShiftR";
        } else if (rawLabel.equals("Ctrl")) {
            if (r == 5) return (c == 0) ? "CtrlL" : "CtrlR";
        } else if (rawLabel.equals("Alt")) {
            if (r == 5) return (c == 1) ? "AltL" : "AltR";
        }
        return rawLabel;
    }

    private String resolveDisplayLabel(String rawLabel, String internalLabel) {
        if (internalLabel.startsWith("Shift")) return "Shift";
        if (internalLabel.startsWith("Ctrl")) return "Ctrl";
        if (internalLabel.startsWith("Alt")) return "Alt";
        return rawLabel;
    }

    private void initArcKeys() {
        mArcKeys.clear();
        // Left Arc
        for (int r = 0; r < LEFT_ARC_ROWS.length; r++) {
            String[] row = LEFT_ARC_ROWS[r];
            for (int c = 0; c < row.length; c++) {
                String raw = row[c];
                String internal = raw;
                String display = resolveDisplayLabel(raw, internal);
                int keyCode = getKeyCodeForLabel(internal);
                boolean hasSym = hasSymbol(raw);

                KeyData k = new KeyData(display, keyCode, 1f, hasSym);
                k.internalLabel = internal;
                k.symbolChar = hasSym ? SYMBOL_CHAR_MAP.get(raw) : null;
                k.currentLabel = display;
                k.currentKeyCode = keyCode;
                k.isArc = true;
                k.isRight = false;
                k.ringIndex = r;
                k.colIndex = c;
                k.ringTotalKeys = row.length;
                mArcKeys.add(k);
            }
        }
        // Right Arc
        for (int r = 0; r < RIGHT_ARC_ROWS.length; r++) {
            String[] row = RIGHT_ARC_ROWS[r];
            for (int c = 0; c < row.length; c++) {
                String raw = row[c];
                String internal = raw;
                String display = resolveDisplayLabel(raw, internal);
                int keyCode = getKeyCodeForLabel(internal);
                boolean hasSym = hasSymbol(raw);

                KeyData k = new KeyData(display, keyCode, 1f, hasSym);
                k.internalLabel = internal;
                k.symbolChar = hasSym ? SYMBOL_CHAR_MAP.get(raw) : null;
                k.currentLabel = display;
                k.currentKeyCode = keyCode;
                k.isArc = true;
                k.isRight = true;
                k.ringIndex = r;
                k.colIndex = c;
                k.ringTotalKeys = row.length;
                mArcKeys.add(k);
            }
        }
    }

    private void initTopWings() {
        mWingKeys.clear();
        // Default 4 left keys, 4 right keys
        String[] leftDefLabels = {"F1", "F2", "F3", "SUPER"};
        int[] leftDefCodes = {KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_META_LEFT};

        String[] rightDefLabels = {"DEL", "HOME", "END", "SETTINGS"};
        int[] rightDefCodes = {KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_SETTINGS};

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String customJson = prefs.getString(KEY_SPLIT_ARC_TOP_WINGS, null);
        Map<Integer, String> customLabels = new HashMap<>();
        Map<Integer, HudAction> customActions = new HashMap<>();
        if (customJson != null && !customJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(customJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int idx = obj.optInt("index", -1);
                    if (idx >= 0) {
                        customLabels.put(idx, obj.optString("label", ""));
                        if (obj.has("action")) {
                            customActions.put(idx, HudAction.fromJSON(obj.optJSONObject("action")));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Left Top Wing (indices 0..3)
        for (int i = 0; i < 4; i++) {
            String lbl = customLabels.containsKey(i) ? customLabels.get(i) : leftDefLabels[i];
            HudAction act = customActions.get(i);
            int code = (act != null && act.code > 0) ? act.code : leftDefCodes[i];

            KeyData k = new KeyData(lbl, code, 1f, false);
            k.isWing = true;
            k.isRight = false;
            k.wingIndex = i;
            k.customAction = act;
            mWingKeys.add(k);
        }

        // Right Top Wing (indices 4..7)
        for (int i = 0; i < 4; i++) {
            int slotIdx = 4 + i;
            String lbl = customLabels.containsKey(slotIdx) ? customLabels.get(slotIdx) : rightDefLabels[i];
            HudAction act = customActions.get(slotIdx);
            int code = (act != null && act.code > 0) ? act.code : rightDefCodes[i];

            KeyData k = new KeyData(lbl, code, 1f, false);
            k.isWing = true;
            k.isRight = true;
            k.wingIndex = slotIdx;
            k.customAction = act;
            mWingKeys.add(k);
        }
    }

    private void saveTopWingsCustomization() {
        try {
            JSONArray arr = new JSONArray();
            for (KeyData k : mWingKeys) {
                JSONObject obj = new JSONObject();
                obj.put("index", k.wingIndex);
                obj.put("label", k.currentLabel);
                if (k.customAction != null) {
                    obj.put("action", k.customAction.toJSON());
                }
                arr.put(obj);
            }
            getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_SPLIT_ARC_TOP_WINGS, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveTopWings error", e);
        }
    }

    private int getKeyCodeForLabel(String label) {
        switch (label) {
            case "ESC": return KeyEvent.KEYCODE_ESCAPE;
            case "F1": return KeyEvent.KEYCODE_F1;
            case "F2": return KeyEvent.KEYCODE_F2;
            case "F3": return KeyEvent.KEYCODE_F3;
            case "F4": return KeyEvent.KEYCODE_F4;
            case "F5": return KeyEvent.KEYCODE_F5;
            case "F6": return KeyEvent.KEYCODE_F6;
            case "F7": return KeyEvent.KEYCODE_F7;
            case "F8": return KeyEvent.KEYCODE_F8;
            case "F9": return KeyEvent.KEYCODE_F9;
            case "F10": return KeyEvent.KEYCODE_F10;
            case "F11": return KeyEvent.KEYCODE_F11;
            case "F12": return KeyEvent.KEYCODE_F12;
            case "`": return KeyEvent.KEYCODE_GRAVE;
            case "1": return KeyEvent.KEYCODE_1;
            case "2": return KeyEvent.KEYCODE_2;
            case "3": return KeyEvent.KEYCODE_3;
            case "4": return KeyEvent.KEYCODE_4;
            case "5": return KeyEvent.KEYCODE_5;
            case "6": return KeyEvent.KEYCODE_6;
            case "7": return KeyEvent.KEYCODE_7;
            case "8": return KeyEvent.KEYCODE_8;
            case "9": return KeyEvent.KEYCODE_9;
            case "0": return KeyEvent.KEYCODE_0;
            case "-": return KeyEvent.KEYCODE_MINUS;
            case "=": return KeyEvent.KEYCODE_EQUALS;
            case "BKSP": return KeyEvent.KEYCODE_DEL;
            case "Tab": return KeyEvent.KEYCODE_TAB;
            case "Q": return KeyEvent.KEYCODE_Q;
            case "W": return KeyEvent.KEYCODE_W;
            case "E": return KeyEvent.KEYCODE_E;
            case "R": return KeyEvent.KEYCODE_R;
            case "T": return KeyEvent.KEYCODE_T;
            case "Y": return KeyEvent.KEYCODE_Y;
            case "U": return KeyEvent.KEYCODE_U;
            case "I": return KeyEvent.KEYCODE_I;
            case "O": return KeyEvent.KEYCODE_O;
            case "P": return KeyEvent.KEYCODE_P;
            case "[": return KeyEvent.KEYCODE_LEFT_BRACKET;
            case "]": return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case "\\": return KeyEvent.KEYCODE_BACKSLASH;
            case "Caps": return KeyEvent.KEYCODE_CAPS_LOCK;
            case "A": return KeyEvent.KEYCODE_A;
            case "S": return KeyEvent.KEYCODE_S;
            case "D": return KeyEvent.KEYCODE_D;
            case "F": return KeyEvent.KEYCODE_F;
            case "G": return KeyEvent.KEYCODE_G;
            case "H": return KeyEvent.KEYCODE_H;
            case "J": return KeyEvent.KEYCODE_J;
            case "K": return KeyEvent.KEYCODE_K;
            case "L": return KeyEvent.KEYCODE_L;
            case ";": return KeyEvent.KEYCODE_SEMICOLON;
            case "'": return KeyEvent.KEYCODE_APOSTROPHE;
            case "Enter": return KeyEvent.KEYCODE_ENTER;
            case "ShiftL": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case "ShiftR": return KeyEvent.KEYCODE_SHIFT_RIGHT;
            case "Z": return KeyEvent.KEYCODE_Z;
            case "X": return KeyEvent.KEYCODE_X;
            case "C": return KeyEvent.KEYCODE_C;
            case "V": return KeyEvent.KEYCODE_V;
            case "B": return KeyEvent.KEYCODE_B;
            case "N": return KeyEvent.KEYCODE_N;
            case "M": return KeyEvent.KEYCODE_M;
            case ",": return KeyEvent.KEYCODE_COMMA;
            case "UP": return KeyEvent.KEYCODE_DPAD_UP;
            case ".": return KeyEvent.KEYCODE_PERIOD;
            case "/": return KeyEvent.KEYCODE_SLASH;
            case "CtrlL": return KeyEvent.KEYCODE_CTRL_LEFT;
            case "CtrlR": return KeyEvent.KEYCODE_CTRL_RIGHT;
            case "AltL": return KeyEvent.KEYCODE_ALT_LEFT;
            case "AltR": return KeyEvent.KEYCODE_ALT_RIGHT;
            case "Home": return KeyEvent.KEYCODE_MOVE_HOME;
            case "End": return KeyEvent.KEYCODE_MOVE_END;
            case "Space": return KeyEvent.KEYCODE_SPACE;
            case "LT": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "DN": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "RT": return KeyEvent.KEYCODE_DPAD_RIGHT;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    private float getWeightForLabel(String label) {
        switch (label) {
            case "ESC":
            case "F1": case "F2": case "F3": case "F4": case "F5":
            case "F6": case "F7": case "F8": case "F9": case "F10":
            case "F11": case "F12":
            case "Home": case "End":
                return 1.0f;
            case "BKSP": return 2.0f;
            case "Tab": return 1.5f;
            case "Caps": return 1.5f;
            case "Enter": return 1.5f;
            case "ShiftL": return 1.75f;
            case "ShiftR": return 1.75f;
            case "CtrlL": return 1.5f;
            case "CtrlR": return 1.5f;
            case "AltL": return 1.5f;
            case "AltR": return 1.5f;
            case "Space": return 3.0f;
            default: return 1.0f;
        }
    }

    public void setOnKeyEventListener(OnKeyEventListener l) {
        this.listener = l;
    }

    // ========== Measure and Layout ==========
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            updateScreenSize();
            if (mIsSplitArcMode) {
                int pw = MeasureSpec.getSize(widthMeasureSpec);
                int ph = MeasureSpec.getSize(heightMeasureSpec);
                if (pw <= 0) pw = screenWidth;
                if (ph <= 0) ph = screenHeight;
                setMeasuredDimension(pw, ph);
                return;
            }

            int desiredWidth = (int) (screenWidth * 0.45f);
            if (desiredWidth < 400) desiredWidth = 400;
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
            if (maxWidth > 0 && desiredWidth > maxWidth) desiredWidth = maxWidth;
            int rowCount = keyboardRows.length;
            int keyH = dpToPx(32);
            int totalHeight = dragHandleHeight + padding + rowCount * (keyH + padding) + padding;
            if (totalHeight <= 0) totalHeight = 350;
            setMeasuredDimension(desiredWidth, totalHeight);
        } catch (Exception e) {
            Log.e(TAG, "onMeasure error", e);
            setMeasuredDimension(600, 350);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateScreenSize();
        try {
            if (mIsSplitArcMode) {
                layoutSplitArc(w, h);
            } else {
                layoutKeys(w, h);
                post(this::setInitialPosition);
            }
        } catch (Exception e) {
            Log.e(TAG, "onSizeChanged error", e);
        }
    }

    private void layoutKeys(int viewW, int viewH) {
        int totalRows = keyboardRows.length;
        if (totalRows == 0) return;

        int availH = viewH - dragHandleHeight - padding * (totalRows + 1);
        if (availH < 20) availH = 20;
        keyHeight = availH / totalRows;
        if (keyHeight < 20) keyHeight = 20;

        int keyIndex = 0;
        for (int row = 0; row < totalRows; row++) {
            String[] rowLabels = keyboardRows[row];
            int numKeys = rowLabels.length;

            float totalWeight = 0;
            for (int c = 0; c < numKeys; c++) {
                KeyData k = keys.get(keyIndex + c);
                totalWeight += k.weight;
            }
            if (totalWeight <= 0) totalWeight = 1.0f;

            int rowWidth = viewW - padding * (numKeys + 1);
            if (rowWidth < 10) rowWidth = viewW;
            float unit = rowWidth / totalWeight;

            int rowY = dragHandleHeight + padding + row * (keyHeight + padding);
            if (rowY < 0) rowY = 0;

            int x = padding;
            for (int c = 0; c < numKeys; c++) {
                KeyData k = keys.get(keyIndex + c);
                int w = (int) (unit * k.weight);
                if (w < 25) w = 25;
                if (c == numKeys - 1) {
                    w = viewW - x - padding;
                    if (w < 25) w = 25;
                }
                k.rect.set(x, rowY, x + w, rowY + keyHeight);
                x += w + padding;
            }
            keyIndex += numKeys;
        }
    }

    private float normalizeKeyRotation(float rot) {
        while (rot > 90f) rot -= 180f;
        while (rot < -90f) rot += 180f;
        return rot;
    }

    private void layoutSplitArc(int viewW, int viewH) {
        if (viewW <= 0 || viewH <= 0) return;
        float density = getResources().getDisplayMetrics().density;

        // Concentric ring radii from bottom corners
        float[] ringRadii = {
                75f * density,
                116f * density,
                157f * density,
                198f * density,
                239f * density
        };
        float keyThickness = 34f * density;
        float arcSpanDeg = 78f;

        // 1. Layout Left Arc (Pivot: 0, viewH)
        float leftPivotX = -4f * density;
        float leftPivotY = viewH + 4f * density;
        float leftStartAngle = -74f; // sweeping from top-left downwards-rightwards to +4 deg

        for (KeyData k : mArcKeys) {
            if (k.isRight) continue;
            float r = ringRadii[k.ringIndex];
            k.pivotX = leftPivotX;
            k.pivotY = leftPivotY;
            k.baseRadius = r;
            k.keyHeight = keyThickness;

            float angleStep = arcSpanDeg / k.ringTotalKeys;
            k.baseAngleDeg = leftStartAngle + (k.colIndex + 0.5f) * angleStep;

            double rad = Math.toRadians(k.baseAngleDeg);
            k.currentCx = leftPivotX + r * (float) Math.cos(rad);
            k.currentCy = leftPivotY + r * (float) Math.sin(rad);
            k.currentRotation = normalizeKeyRotation(k.baseAngleDeg + 90f);

            float arcLen = (float) (r * Math.toRadians(angleStep)) - 4f * density;
            k.keyWidth = Math.max(26f * density, Math.min(48f * density, arcLen));
        }

        // 2. Layout Right Arc (Pivot: viewW, viewH)
        float rightPivotX = viewW + 4f * density;
        float rightPivotY = viewH + 4f * density;
        float rightStartAngle = 176f; // sweeping from 176 deg to 254 deg

        for (KeyData k : mArcKeys) {
            if (!k.isRight) continue;
            float r = ringRadii[k.ringIndex];
            k.pivotX = rightPivotX;
            k.pivotY = rightPivotY;
            k.baseRadius = r;
            k.keyHeight = keyThickness;

            float angleStep = arcSpanDeg / k.ringTotalKeys;
            k.baseAngleDeg = rightStartAngle + (k.colIndex + 0.5f) * angleStep;

            double rad = Math.toRadians(k.baseAngleDeg);
            k.currentCx = rightPivotX + r * (float) Math.cos(rad);
            k.currentCy = rightPivotY + r * (float) Math.sin(rad);
            k.currentRotation = normalizeKeyRotation(k.baseAngleDeg - 90f);

            float arcLen = (float) (r * Math.toRadians(angleStep)) - 4f * density;
            k.keyWidth = Math.max(26f * density, Math.min(48f * density, arcLen));
        }

        // 3. Layout Beveled Top Wings - seamlessly meeting the outer arc below
        float wingW = 104f * density;
        float wingTop = 20f * density;
        float spacing = 6f * density;
        float cutRadiusLeft = ringRadii[4] + 24f * density;
        float cutRadiusRight = ringRadii[4] + 24f * density;

        // Left Wing: 2 cols x 2 rows
        float lwLeft = 8f * density;
        float lwColW = (wingW - spacing) / 2f;

        for (KeyData k : mWingKeys) {
            if (k.isRight) continue;
            int col = k.wingIndex % 2;
            int row = k.wingIndex / 2;
            float kLeft = lwLeft + col * (lwColW + spacing);
            float kRight = kLeft + lwColW;
            float kTop = (row == 0) ? wingTop : (wingTop + 44f * density + spacing);

            float kBottom;
            if (row == 1) {
                float midX = (kLeft + kRight) / 2f;
                float arcY = calculateArcY(midX, leftPivotX, leftPivotY, cutRadiusLeft);
                kBottom = Math.max(kTop + 36f * density, arcY);
                k.beveledPath = createBeveledPath(kLeft, kTop, kRight, kBottom, leftPivotX, leftPivotY, cutRadiusLeft, false, density);
            } else {
                kBottom = kTop + 44f * density;
                k.beveledPath = null;
            }
            k.wingBounds.set(kLeft, kTop, kRight, kBottom);
        }

        // Right Wing: 2 cols x 2 rows
        float rwRight = viewW - 8f * density;
        float rwLeft = rwRight - wingW;
        float rwColW = (wingW - spacing) / 2f;

        for (KeyData k : mWingKeys) {
            if (!k.isRight) continue;
            int idx = k.wingIndex - 4;
            int col = idx % 2;
            int row = idx / 2;
            float kLeft = rwLeft + col * (rwColW + spacing);
            float kRight = kLeft + rwColW;
            float kTop = (row == 0) ? wingTop : (wingTop + 44f * density + spacing);

            float kBottom;
            if (row == 1) {
                float midX = (kLeft + kRight) / 2f;
                float arcY = calculateArcY(midX, rightPivotX, rightPivotY, cutRadiusRight);
                kBottom = Math.max(kTop + 36f * density, arcY);
                k.beveledPath = createBeveledPath(kLeft, kTop, kRight, kBottom, rightPivotX, rightPivotY, cutRadiusRight, true, density);
            } else {
                kBottom = kTop + 44f * density;
                k.beveledPath = null;
            }
            k.wingBounds.set(kLeft, kTop, kRight, kBottom);
        }
    }

    private Path createBeveledPath(float left, float top, float right, float bottom,
                                  float pivotX, float pivotY, float radius, boolean isRightHand, float density) {
        Path path = new Path();
        float r = 6f * density;

        // Top edge with rounded corners
        path.moveTo(left, top + r);
        path.quadTo(left, top, left + r, top);
        path.lineTo(right - r, top);
        path.quadTo(right, top, right, top + r);

        float yAtRight = calculateArcY(right, pivotX, pivotY, radius);
        float yAtLeft = calculateArcY(left, pivotX, pivotY, radius);

        path.lineTo(right, yAtRight);
        path.lineTo(left, yAtLeft);
        path.lineTo(left, top + r);
        path.close();
        return path;
    }

    private float calculateArcY(float x, float pivotX, float pivotY, float radius) {
        float dx = x - pivotX;
        float underSqrt = radius * radius - dx * dx;
        if (underSqrt > 0) {
            return pivotY - (float) Math.sqrt(underSqrt);
        }
        return pivotY - radius;
    }

    public void setInitialPosition() {
        if (mIsSplitArcMode) {
            setTranslationX(0);
            setTranslationY(0);
            bringToFront();
            return;
        }

        try {
            if (getWidth() == 0 || getHeight() == 0) {
                post(this::setInitialPosition);
                return;
            }
            int w = getWidth();
            int h = getHeight();
            int pw = 0, ph = 0;
            ViewParent pp = getParent();
            if (pp instanceof View) {
                pw = ((View) pp).getWidth();
                ph = ((View) pp).getHeight();
            }
            if (pw <= 0 || ph <= 0) {
                post(this::setInitialPosition);
                return;
            }
            if (w > 0 && h > 0) {
                float x = (pw - w) / 2f;
                float y = ph - h - dpToPx(50);
                x = Math.max(0, Math.min(x, pw - w));
                y = Math.max(0, Math.min(y, ph - h));
                setTranslationX(x);
                setTranslationY(y);
                bringToFront();
            }
        } catch (Exception e) {
            Log.e(TAG, "setInitialPosition error", e);
        }
    }

    // ========== Core State Management ==========
    private void updateSymbolLayer() {
        symbolLayerActive = leftShiftOn || rightShiftPressed;
        applySymbolLayer();
    }

    private void applySymbolLayer() {
        try {
            List<KeyData> all = new ArrayList<>(keys);
            all.addAll(mArcKeys);
            for (KeyData k : all) {
                if (k.hasSymbol && k.symbolChar != null) {
                    if (symbolLayerActive) {
                        k.currentLabel = k.symbolChar;
                        k.currentKeyCode = getKeyCodeForSymbol(k.symbolChar);
                    } else {
                        k.currentLabel = k.defaultLabel;
                        k.currentKeyCode = k.defaultKeyCode;
                    }
                }
            }
            for (KeyData k : all) {
                if ("ShiftL".equals(k.internalLabel)) {
                    k.modActive = leftShiftOn;
                } else if ("ShiftR".equals(k.internalLabel)) {
                    k.modActive = rightShiftPressed;
                } else if ("CtrlL".equals(k.internalLabel)) {
                    k.modActive = leftCtrlOn;
                } else if ("AltL".equals(k.internalLabel)) {
                    k.modActive = leftAltOn;
                } else if ("Caps".equals(k.internalLabel)) {
                    k.modActive = capsLockOn;
                }
            }
            invalidate();
        } catch (Exception e) {
            Log.e(TAG, "applySymbolLayer error", e);
        }
    }

    private int getKeyCodeForSymbol(String symbol) {
        for (Map.Entry<String, String> entry : SYMBOL_CHAR_MAP.entrySet()) {
            if (entry.getValue().equals(symbol)) {
                return getKeyCodeForLabel(entry.getKey());
            }
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    // ========== Send Keys ==========
    private void sendKey(int keyCode, boolean down) {
        if (listener == null) return;
        int scan = KeyCodeMapper.getScanCode(keyCode);
        if (scan != -1) {
            if (down) listener.onKeyDown(scan);
            else listener.onKeyUp(scan);
        }
    }

    private void sendKeyWithCaps(int keyCode, boolean down) {
        if (listener == null) return;
        boolean isLetter = keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z;
        boolean shouldApplyCaps = isLetter && capsLockOn && !rightShiftPressed;

        if (shouldApplyCaps) {
            if (down) {
                sendKey(KeyEvent.KEYCODE_SHIFT_LEFT, true);
                sendKey(keyCode, true);
            } else {
                sendKey(keyCode, false);
                sendKey(KeyEvent.KEYCODE_SHIFT_LEFT, false);
            }
        } else {
            sendKey(keyCode, down);
        }
    }

    private void executeCombination(int normalKeyCode) {
        List<KeyData> mods = new ArrayList<>();
        List<KeyData> all = new ArrayList<>(keys);
        all.addAll(mArcKeys);
        for (KeyData k : all) {
            if (isLeftModifier(k) && getLeftModifierState(k)) {
                mods.add(k);
            }
        }
        if (mods.isEmpty()) return;

        for (KeyData k : mods) {
            sendKey(k.currentKeyCode, true);
        }
        sendKeyWithCaps(normalKeyCode, true);
        sendKeyWithCaps(normalKeyCode, false);
        for (int i = mods.size() - 1; i >= 0; i--) {
            sendKey(mods.get(i).currentKeyCode, false);
        }

        leftShiftOn = false;
        leftCtrlOn = false;
        leftAltOn = false;
        for (KeyData k : all) {
            if (isLeftModifier(k)) {
                k.modActive = false;
            }
        }
        updateSymbolLayer();
        invalidate();
    }

    private boolean isLeftModifier(KeyData k) {
        if (k == null) return false;
        String in = k.internalLabel;
        return "ShiftL".equals(in) || "CtrlL".equals(in) || "AltL".equals(in);
    }

    private boolean getLeftModifierState(KeyData k) {
        if (k == null) return false;
        String in = k.internalLabel;
        if ("ShiftL".equals(in)) return leftShiftOn;
        if ("CtrlL".equals(in)) return leftCtrlOn;
        if ("AltL".equals(in)) return leftAltOn;
        return false;
    }

    private void toggleLeftModifier(KeyData k) {
        if (k == null) return;
        String in = k.internalLabel;
        boolean newState;
        if ("ShiftL".equals(in)) {
            newState = !leftShiftOn;
            leftShiftOn = newState;
        } else if ("CtrlL".equals(in)) {
            newState = !leftCtrlOn;
            leftCtrlOn = newState;
        } else if ("AltL".equals(in)) {
            newState = !leftAltOn;
            leftAltOn = newState;
        } else {
            return;
        }
        k.modActive = newState;
        updateSymbolLayer();
        invalidate();
    }

    private void pressRightModifier(KeyData k) {
        if (k == null) return;
        String in = k.internalLabel;
        if ("ShiftR".equals(in)) {
            rightShiftPressed = true;
            sendKey(KeyEvent.KEYCODE_SHIFT_RIGHT, true);
            updateSymbolLayer();
        } else if ("CtrlR".equals(in)) {
            sendKey(KeyEvent.KEYCODE_CTRL_RIGHT, true);
        } else if ("AltR".equals(in)) {
            sendKey(KeyEvent.KEYCODE_ALT_RIGHT, true);
        }
        k.pressed = true;
        invalidate();
    }

    private void releaseRightModifier(KeyData k) {
        if (k == null) return;
        String in = k.internalLabel;
        if ("ShiftR".equals(in)) {
            rightShiftPressed = false;
            sendKey(KeyEvent.KEYCODE_SHIFT_RIGHT, false);
            updateSymbolLayer();
        } else if ("CtrlR".equals(in)) {
            sendKey(KeyEvent.KEYCODE_CTRL_RIGHT, false);
        } else if ("AltR".equals(in)) {
            sendKey(KeyEvent.KEYCODE_ALT_RIGHT, false);
        }
        k.pressed = false;
        invalidate();
    }

    private boolean isHapticEnabled() {
        return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("haptic_feedback_enabled", true);
    }

    // ========== onTouchEvent ==========
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            int action = event.getActionMasked();
            float x = event.getX();
            float y = event.getY();

            // Portrait drag handle
            if (!mIsSplitArcMode && action == MotionEvent.ACTION_DOWN && y < dragHandleHeight) {
                isDragging = true;
                lastRawX = x;
                lastRawY = y;
                bringToFront();
                return true;
            }

            if (!mIsSplitArcMode && isDragging) {
                switch (action) {
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastRawX;
                        float dy = event.getY() - lastRawY;
                        setTranslationX(getTranslationX() + dx);
                        setTranslationY(getTranslationY() + dy);
                        lastRawX = event.getX();
                        lastRawY = event.getY();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        return true;
                }
                return true;
            }

            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);
            float touchX = event.getX(pointerIndex);
            float touchY = event.getY(pointerIndex);
            KeyData hitKey = findKeyAt(touchX, touchY);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (hitKey == null) {
                        // In Split Arc mode: touches outside keys fall through to desktop!
                        if (mIsSplitArcMode && action == MotionEvent.ACTION_DOWN) {
                            return false;
                        }
                        break;
                    }

                    if (isHapticEnabled()) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }

                    // Top Wing key long-press check for rebind
                    if (hitKey.isWing) {
                        mPendingLongPressKey = hitKey;
                        final KeyData wingToRebind = hitKey;
                        mLongPressHandler.postDelayed(() -> {
                            if (mPendingLongPressKey == wingToRebind) {
                                mPendingLongPressKey = null;
                                if (isHapticEnabled()) {
                                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                }
                                HudKeyPickerDialog.show(getContext(), (act, displayLabel) -> {
                                    wingToRebind.customAction = act;
                                    wingToRebind.currentLabel = displayLabel;
                                    saveTopWingsCustomization();
                                    invalidate();
                                });
                            }
                        }, 450);
                    }

                    if (isLeftModifier(hitKey)) {
                        toggleLeftModifier(hitKey);
                        return true;
                    }

                    if ("Caps".equals(hitKey.internalLabel)) {
                        capsLockOn = !capsLockOn;
                        hitKey.modActive = capsLockOn;
                        invalidate();
                        return true;
                    }

                    if (isRightModifier(hitKey)) {
                        activePointers.put(pointerId, hitKey);
                        pressRightModifier(hitKey);
                        return true;
                    }

                    activePointers.put(pointerId, hitKey);
                    hitKey.pressed = true;

                    if (hitKey.isWing && hitKey.customAction != null) {
                        dispatchWingAction(hitKey.customAction, true);
                    } else {
                        int code = hitKey.currentKeyCode;
                        if (isDirectionKey(code) || code == KeyEvent.KEYCODE_MOVE_HOME || code == KeyEvent.KEYCODE_MOVE_END) {
                            sendKey(code, true);
                        } else if (leftShiftOn || leftCtrlOn || leftAltOn) {
                            executeCombination(code);
                        } else {
                            sendKeyWithCaps(code, true);
                        }
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    mPendingLongPressKey = null;
                    mLongPressHandler.removeCallbacksAndMessages(null);

                    KeyData released = activePointers.get(pointerId);
                    if (released == null) break;

                    if (released.isWing && released.customAction != null) {
                        dispatchWingAction(released.customAction, false);
                    } else {
                        int relCode = released.currentKeyCode;
                        if (isRightModifier(released)) {
                            releaseRightModifier(released);
                        } else if (isDirectionKey(relCode) || relCode == KeyEvent.KEYCODE_MOVE_HOME || relCode == KeyEvent.KEYCODE_MOVE_END) {
                            sendKey(relCode, false);
                        } else if (!isLeftModifier(released) && !"Caps".equals(released.internalLabel)) {
                            sendKeyWithCaps(relCode, false);
                        }
                    }
                    released.pressed = false;
                    activePointers.remove(pointerId);
                    invalidate();
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    for (int i = 0; i < activePointers.size(); i++) {
                        int pid = activePointers.keyAt(i);
                        KeyData key = activePointers.valueAt(i);
                        int idx = event.findPointerIndex(pid);
                        if (idx < 0) continue;
                        float px = event.getX(idx);
                        float py = event.getY(idx);

                        boolean stillHit = false;
                        if (mIsSplitArcMode && key.isArc) {
                            float dx = px - key.currentCx;
                            float dy = py - key.currentCy;
                            double rad = Math.toRadians(-key.currentRotation);
                            float lx = (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
                            float ly = (float) (dx * Math.sin(rad) + dy * Math.cos(rad));
                            stillHit = Math.abs(lx) <= key.keyWidth * 0.6f && Math.abs(ly) <= key.keyHeight * 0.6f;
                        } else if (key.isWing) {
                            stillHit = key.wingBounds.contains(px, py);
                        } else {
                            stillHit = key.rect.contains((int) px, (int) py);
                        }

                        if (!stillHit) {
                            key.pressed = false;
                            if (key.isWing && key.customAction != null) {
                                dispatchWingAction(key.customAction, false);
                            } else {
                                int kc = key.currentKeyCode;
                                if (isRightModifier(key)) {
                                    releaseRightModifier(key);
                                } else if (isDirectionKey(kc) || kc == KeyEvent.KEYCODE_MOVE_HOME || kc == KeyEvent.KEYCODE_MOVE_END) {
                                    sendKey(kc, false);
                                } else if (!isLeftModifier(key) && !"Caps".equals(key.internalLabel)) {
                                    sendKeyWithCaps(kc, false);
                                }
                            }
                            activePointers.remove(pid);
                            invalidate();
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_CANCEL: {
                    mPendingLongPressKey = null;
                    mLongPressHandler.removeCallbacksAndMessages(null);
                    for (int i = 0; i < activePointers.size(); i++) {
                        KeyData key = activePointers.valueAt(i);
                        key.pressed = false;
                        if (key.isWing && key.customAction != null) {
                            dispatchWingAction(key.customAction, false);
                        } else {
                            int kc = key.currentKeyCode;
                            if (isRightModifier(key)) {
                                releaseRightModifier(key);
                            } else if (isDirectionKey(kc) || kc == KeyEvent.KEYCODE_MOVE_HOME || kc == KeyEvent.KEYCODE_MOVE_END) {
                                sendKey(kc, false);
                            } else if (!isLeftModifier(key) && !"Caps".equals(key.internalLabel)) {
                                sendKeyWithCaps(kc, false);
                            }
                        }
                    }
                    activePointers.clear();
                    leftShiftOn = false;
                    leftCtrlOn = false;
                    leftAltOn = false;
                    rightShiftPressed = false;
                    updateSymbolLayer();
                    invalidate();
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "onTouchEvent error", e);
            return false;
        }
    }

    private void dispatchWingAction(HudAction action, boolean isDown) {
        if (action == null || listener == null) return;
        if (HudAction.TYPE_KEY.equals(action.type)) {
            sendKey(action.code, isDown);
        } else if (HudAction.TYPE_MODIFIER.equals(action.type)) {
            sendKey(action.code, isDown);
        } else if (HudAction.TYPE_COMBO.equals(action.type)) {
            if (isDown && action.comboKeys != null) {
                for (int k : action.comboKeys) {
                    listener.onKeyDown(k);
                }
                postDelayed(() -> {
                    for (int i = action.comboKeys.size() - 1; i >= 0; i--) {
                        listener.onKeyUp(action.comboKeys.get(i));
                    }
                }, 50);
            }
        }
    }

    private boolean isRightModifier(KeyData k) {
        if (k == null) return false;
        String in = k.internalLabel;
        return "ShiftR".equals(in) || "CtrlR".equals(in) || "AltR".equals(in);
    }

    private boolean isDirectionKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private KeyData findKeyAt(float x, float y) {
        if (mIsSplitArcMode) {
            // Check top wings first
            for (KeyData k : mWingKeys) {
                if (k.wingBounds.contains(x, y)) {
                    return k;
                }
            }
            // Check radial arc keys
            for (KeyData k : mArcKeys) {
                float dx = x - k.currentCx;
                float dy = y - k.currentCy;
                double rad = Math.toRadians(-k.currentRotation);
                float lx = (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
                float ly = (float) (dx * Math.sin(rad) + dy * Math.cos(rad));
                if (Math.abs(lx) <= k.keyWidth * 0.54f && Math.abs(ly) <= k.keyHeight * 0.54f) {
                    return k;
                }
            }
            return null;
        }

        // Portrait floating mode
        for (KeyData k : keys) {
            if (k.rect.contains((int) x, (int) y)) return k;
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            if (mIsSplitArcMode) {
                drawSplitArcLayout(canvas);
                return;
            }

            // Standard Portrait Draw
            if (keys.isEmpty()) return;

            bgPaint.setColor(bgColor);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius, bgPaint);

            handlePaint.setColor(HANDLE_COLOR);
            canvas.drawRoundRect(0, 0, getWidth(), dragHandleHeight, cornerRadius, cornerRadius, handlePaint);
            float dotY = dragHandleHeight / 2f;
            float dotSpacing = dpToPx(8);
            float startX = getWidth() / 2f - dotSpacing;
            for (int i = 0; i < 3; i++) {
                canvas.drawCircle(startX + i * dotSpacing, dotY, dpToPx(2), handlePaint);
            }

            for (KeyData k : keys) {
                Rect r = k.rect;
                int bg = resolveKeyBg(k);
                keyBgPaint.setColor(bg);
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cornerRadius, cornerRadius, keyBgPaint);
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cornerRadius, cornerRadius, keyStrokePaint);

                textPaint.setColor(textColor);
                float textSize = keyHeight * 0.4f;
                if (textSize <= 0) textSize = 20;
                textPaint.setTextSize(textSize);
                float cx = r.centerX();
                float cy = r.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2);
                String display = k.currentLabel != null ? k.currentLabel : k.defaultLabel;
                canvas.drawText(display, cx, cy, textPaint);
            }
        } catch (Exception e) {
            Log.e(TAG, "onDraw error", e);
        }
    }

    private int resolveKeyBg(KeyData k) {
        if (k.pressed) return pressedColor;
        if (k.modActive) return modActiveColor;
        return keyColor;
    }

    private void drawSplitArcLayout(Canvas canvas) {
        int animType = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SPLIT_ARC_ANIM, ANIM_FAN);
        int alpha = Math.max(0, Math.min(255, Math.round(255 * mAnimProgress)));

        float density = getResources().getDisplayMetrics().density;
        float viewW = getWidth();
        float viewH = getHeight();

        // 1. Draw Radial Arc Keys
        for (KeyData k : mArcKeys) {
            float cx = k.currentCx;
            float cy = k.currentCy;
            float rot = k.currentRotation;

            if (animType == ANIM_FAN) {
                float spread = (float) Math.sin(mAnimProgress * Math.PI / 2.0);
                float angle = k.isRight
                        ? (k.baseAngleDeg + (1f - spread) * 26f)
                        : (k.baseAngleDeg - (1f - spread) * 26f);
                float radius = k.baseRadius * (0.65f + 0.35f * spread);
                double rad = Math.toRadians(angle);
                cx = k.pivotX + radius * (float) Math.cos(rad);
                cy = k.pivotY + radius * (float) Math.sin(rad);
                rot = normalizeKeyRotation(k.isRight ? (angle - 90f) : (angle + 90f));
            } else if (animType == ANIM_CORNER_ZOOM) {
                float zoom = 0.35f + 0.65f * mAnimProgress;
                float pivotX = k.isRight ? viewW : 0f;
                float pivotY = viewH;
                cx = pivotX + (k.currentCx - pivotX) * zoom;
                cy = pivotY + (k.currentCy - pivotY) * zoom;
            }
            k.currentCx = cx;
            k.currentCy = cy;
            k.currentRotation = rot;

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rot);

            int bg = resolveKeyBg(k);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);

            float hw = k.keyWidth * 0.5f;
            float hh = k.keyHeight * 0.5f;
            float cr = 6f * density;

            canvas.drawRoundRect(-hw, -hh, hw, hh, cr, cr, keyBgPaint);
            keyStrokePaint.setAlpha(Math.round(0x33 * (alpha / 255f)));
            canvas.drawRoundRect(-hw, -hh, hw, hh, cr, cr, keyStrokePaint);

            textPaint.setColor(textColor);
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(12.5f * density);
            float textY = -((textPaint.descent() + textPaint.ascent()) / 2f);
            String display = k.currentLabel != null ? k.currentLabel : k.defaultLabel;
            canvas.drawText(display, 0, textY, textPaint);

            canvas.restore();
        }

        // 2. Draw Beveled Top Wings
        float wingSlideY = (animType == ANIM_FAN) ? ((1f - mAnimProgress) * -24f * density) : 0f;
        for (KeyData k : mWingKeys) {
            canvas.save();
            canvas.translate(0, wingSlideY);

            int bg = resolveKeyBg(k);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);

            if (k.beveledPath != null) {
                canvas.drawPath(k.beveledPath, keyBgPaint);
                keyStrokePaint.setAlpha(Math.round(0x33 * (alpha / 255f)));
                canvas.drawPath(k.beveledPath, keyStrokePaint);
            } else {
                RectF r = k.wingBounds;
                float cr = 8f * density;
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyBgPaint);
                keyStrokePaint.setAlpha(Math.round(0x33 * (alpha / 255f)));
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyStrokePaint);
            }

            textPaint.setColor(M3.COLOR_PRIMARY);
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(11f * density);
            float cx = k.wingBounds.centerX();
            float cy = k.wingBounds.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f);
            String display = k.currentLabel != null ? k.currentLabel : k.defaultLabel;
            canvas.drawText(display, cx, cy, textPaint);

            canvas.restore();
        }
    }

    private int dpToPx(int dp) {
        try {
            return (int) (dp * getResources().getDisplayMetrics().density);
        } catch (Exception e) {
            return dp;
        }
    }

    // ========== Internal Key Data Class ==========
    private static class KeyData {
        Rect rect = new Rect();
        String defaultLabel;
        int defaultKeyCode;
        String internalLabel;
        boolean hasSymbol;
        String symbolChar;
        String currentLabel;
        int currentKeyCode;
        float weight;
        boolean pressed = false;
        boolean modActive = false;

        // Split Arc specific attributes
        boolean isArc = false;
        boolean isRight = false;
        int ringIndex = 0;
        int colIndex = 0;
        int ringTotalKeys = 1;
        float pivotX = 0f;
        float pivotY = 0f;
        float baseRadius = 0f;
        float baseAngleDeg = 0f;
        float keyWidth = 0f;
        float keyHeight = 0f;
        float currentCx = 0f;
        float currentCy = 0f;
        float currentRotation = 0f;

        // Top Wing specific attributes
        boolean isWing = false;
        int wingIndex = -1;
        RectF wingBounds = new RectF();
        Path beveledPath = null;
        HudAction customAction = null;

        KeyData(String label, int keyCode, float weight, boolean hasSymbol) {
            this.defaultLabel = label;
            this.defaultKeyCode = keyCode;
            this.currentLabel = label;
            this.currentKeyCode = keyCode;
            this.weight = weight;
            this.hasSymbol = hasSymbol;
        }
    }
}
