package com.anland.consumer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
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
 *   2. Landscape Ergonomic Split Arc Keyboard (concentric dual-thumb radial layout matching Gboard QWERTY).
 *   3. Long-press numbers on top row (Q->1, W->2 ... P->0) with corner subscripts.
 *   4. Centered bottom Space Bar for dual-thumb typing.
 *   5. Slim, non-bulky Beveled Top Wings contoured to arc curve.
 *   6. Corner fan-shaped dismiss keyboard button.
 *   7. Dedicated ?123 Symbol layer.
 *   8. Snappy 180ms Fan Open and Corner Zoom animations.
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

    // ---------- Landscape Split Arc Gboard QWERTY Layout (3 Rings) ----------
    // Left Arc:
    // Ring 0 (inner): Z, X, C, V, B
    // Ring 1 (middle): A, S, D, F, G
    // Ring 2 (outer): Q, W, E, R, T
    private static final String[][] LEFT_ARC_LETTERS = {
            {"Z", "X", "C", "V", "B"},
            {"A", "S", "D", "F", "G"},
            {"Q", "W", "E", "R", "T"}
    };

    // Right Arc:
    // Ring 0 (inner): N, M (, . da doi xuong dai day 2 ben space)
    // Ring 1 (middle): H, J, K, L, Enter
    // Ring 2 (outer): Y, U, I, O, P, BKSP
    private static final String[][] RIGHT_ARC_LETTERS = {
            {"N", "M"},
            {"H", "J", "K", "L", "Enter"},
            {"Y", "U", "I", "O", "P", "BKSP"}
    };

    // Symbols Layer (?123)
    private static final String[][] LEFT_ARC_SYMBOLS = {
            {"%", "&", "*", "-", "+"},
            {"@", "#", "$", "_", "/"},
            {"1", "2", "3", "4", "5"}
    };

    private static final String[][] RIGHT_ARC_SYMBOLS = {
            {"!", "?", ":", ";"},
            {"(", ")", "\"", "'", "Enter"},
            {"6", "7", "8", "9", "0", "BKSP"}
    };

    // Map QWERTY top row to long-press numbers like Gboard
    private static final Map<String, String> NUMBER_SUBSCRIPTS = new HashMap<>();
    static {
        NUMBER_SUBSCRIPTS.put("Q", "1");
        NUMBER_SUBSCRIPTS.put("W", "2");
        NUMBER_SUBSCRIPTS.put("E", "3");
        NUMBER_SUBSCRIPTS.put("R", "4");
        NUMBER_SUBSCRIPTS.put("T", "5");
        NUMBER_SUBSCRIPTS.put("Y", "6");
        NUMBER_SUBSCRIPTS.put("U", "7");
        NUMBER_SUBSCRIPTS.put("I", "8");
        NUMBER_SUBSCRIPTS.put("O", "9");
        NUMBER_SUBSCRIPTS.put("P", "0");
    }

    // ---------- Symbols Map (Portrait) ----------
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
    private boolean leftMetaOn  = false;
    private boolean rightShiftPressed = false;
    // Caps kieu Gboard: OFF -> ONCE (hoa 1 ky tu) -> LOCK (khoa) -> OFF.
    private static final int CAPS_OFF = 0;
    private static final int CAPS_ONCE = 1;
    private static final int CAPS_LOCK = 2;
    private static final long CAPS_DOUBLE_TAP_MS = 350L;
    private int mCapsState = CAPS_OFF;
    private long mLastCapsTapTime = 0L;
    private boolean symbolLayerActive = false;
    private boolean mIsArcSymbolLayer = false;

    // Keys
    private final List<KeyData> keys = new ArrayList<>();
    private final List<KeyData> mArcKeys = new ArrayList<>();
    private final List<KeyData> mWingKeys = new ArrayList<>();
    private KeyData mLeftSpaceKey;
    private KeyData mRightSpaceKey;
    private KeyData mCommaKey;
    private KeyData mPeriodKey;
    private KeyData mCapsKey;
    private KeyData mSymbolToggleKey;
    private KeyData mCornerDismissKey;

    public interface OnDismissListener {
        void onDismiss();
    }
    private OnDismissListener mDismissListener;
    public void setOnDismissListener(OnDismissListener l) {
        mDismissListener = l;
    }

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

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int keyHeight;
    private int padding = 4;
    private int cornerRadius = 6;

    private final int keyColor = 0xFF2A2B3D;
    private final int pressedColor = 0xFF35395E;
    private final int modActiveColor = 0xFF80DEEA;
    private final int textColor = Color.WHITE;
    private final int bgColor = 0xEE181825;

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
        setRotation(0f);
        setVisibility(VISIBLE);
        bringToFront();
        mIsAnimating = true;
        mAnimProgress = 0f;

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(180); // Snappy power-user 180ms
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
                if (mDismissListener != null) mDismissListener.onDismiss();
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
                leftMetaOn = false;
                rightShiftPressed = false;
                mCapsState = CAPS_OFF;
                symbolLayerActive = false;
                mIsArcSymbolLayer = false;
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
                    k.modActive = false;
                }
                if (mLeftSpaceKey != null) mLeftSpaceKey.pressed = false;
                if (mRightSpaceKey != null) mRightSpaceKey.pressed = false;
                if (mCommaKey != null) mCommaKey.pressed = false;
                if (mPeriodKey != null) mPeriodKey.pressed = false;
                if (mCapsKey != null) mCapsKey.pressed = false;
                if (mSymbolToggleKey != null) mSymbolToggleKey.pressed = false;
                if (mCornerDismissKey != null) mCornerDismissKey.pressed = false;

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

        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setAntiAlias(true);
        subTextPaint.setColor(M3.COLOR_TEXT_MUTED);

        keyBgPaint.setAntiAlias(true);
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
        // 1. Standard QWERTY portrait keys
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

        // 3. Slim Beveled Top Wings
        initTopWings();

        // 4. Day Space che doi + , . 2 ben + Caps
        mLeftSpaceKey = new KeyData("", KeyEvent.KEYCODE_SPACE, 1f, false);
        mLeftSpaceKey.isSpace = true;
        mLeftSpaceKey.internalLabel = "SpaceL";
        mRightSpaceKey = new KeyData("", KeyEvent.KEYCODE_SPACE, 1f, false);
        mRightSpaceKey.isSpace = true;
        mRightSpaceKey.internalLabel = "SpaceR";
        mCommaKey = new KeyData(",", KeyEvent.KEYCODE_COMMA, 1f, false);
        mCommaKey.internalLabel = ",";
        mPeriodKey = new KeyData(".", KeyEvent.KEYCODE_PERIOD, 1f, false);
        mPeriodKey.internalLabel = ".";
        mCapsKey = new KeyData("Caps", KeyEvent.KEYCODE_CAPS_LOCK, 1f, false);
        mCapsKey.internalLabel = "Caps";

        // 5. Symbol Toggle Key (?123 / ABC)
        mSymbolToggleKey = new KeyData("?123", KeyEvent.KEYCODE_UNKNOWN, 1f, false);
        mSymbolToggleKey.isSymbolToggle = true;

        // 6. Corner Dismiss Key (Fan-shaped in bottom-right corner)
        mCornerDismissKey = new KeyData("DOWN", KeyEvent.KEYCODE_UNKNOWN, 1f, false);
        mCornerDismissKey.isCornerDismiss = true;
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
        String[][] leftSource = mIsArcSymbolLayer ? LEFT_ARC_SYMBOLS : LEFT_ARC_LETTERS;
        String[][] rightSource = mIsArcSymbolLayer ? RIGHT_ARC_SYMBOLS : RIGHT_ARC_LETTERS;

        // Left Arc
        for (int r = 0; r < leftSource.length; r++) {
            String[] row = leftSource[r];
            for (int c = 0; c < row.length; c++) {
                String raw = row[c];
                String internal = raw;
                String display = resolveDisplayLabel(raw, internal);
                int keyCode = getKeyCodeForLabel(internal);

                KeyData k = new KeyData(display, keyCode, 1f, false);
                k.internalLabel = internal;
                k.currentLabel = display;
                k.currentKeyCode = keyCode;
                k.isArc = true;
                k.isRight = false;
                k.ringIndex = r;
                k.colIndex = c;
                k.ringTotalKeys = row.length;
                if (!mIsArcSymbolLayer && NUMBER_SUBSCRIPTS.containsKey(display)) {
                    k.longPressNumber = NUMBER_SUBSCRIPTS.get(display);
                }
                mArcKeys.add(k);
            }
        }
        // Right Arc
        for (int r = 0; r < rightSource.length; r++) {
            String[] row = rightSource[r];
            for (int c = 0; c < row.length; c++) {
                String raw = row[c];
                String internal = raw;
                String display = resolveDisplayLabel(raw, internal);
                int keyCode = getKeyCodeForLabel(internal);

                KeyData k = new KeyData(display, keyCode, 1f, false);
                k.internalLabel = internal;
                k.currentLabel = display;
                k.currentKeyCode = keyCode;
                k.isArc = true;
                k.isRight = true;
                k.ringIndex = r;
                k.colIndex = c;
                k.ringTotalKeys = row.length;
                if (!mIsArcSymbolLayer && NUMBER_SUBSCRIPTS.containsKey(display)) {
                    k.longPressNumber = NUMBER_SUBSCRIPTS.get(display);
                }
                mArcKeys.add(k);
            }
        }
    }

    private void initTopWings() {
        mWingKeys.clear();
        // Nut phu nho gon 3x2 moi ben (12 nut), ha thap gan phim chu.
        String[] leftDefLabels = {"ESC", "TAB", "CTRL", "ALT", "SHIFT", "SUPER"};
        int[] leftDefCodes = {KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_META_LEFT};

        String[] rightDefLabels = {"DEL", "SHIFT", "UP", "HOME", "END", "PGDN"};
        int[] rightDefCodes = {KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MOVE_HOME,
                KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_DOWN};

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

        // Tuong thich layout cu 4+4 nut: map index phai cu 4..7 sang 6..9 moi.
        if (customLabels.containsKey(4) || customLabels.containsKey(5)
                || customLabels.containsKey(6) || customLabels.containsKey(7)) {
            boolean hasNew = customLabels.containsKey(8) || customLabels.containsKey(9)
                    || customLabels.containsKey(10) || customLabels.containsKey(11)
                    || customLabels.containsKey(6);
            if (!hasNew) {
                for (int old = 4; old <= 7; old++) {
                    if (customLabels.containsKey(old)) {
                        int nw = 6 + (old - 4);
                        customLabels.put(nw, customLabels.get(old));
                        if (customActions.containsKey(old)) customActions.put(nw, customActions.get(old));
                    }
                }
            }
        }

        // Left Top Wing (indices 0..5: 3 cot x 2 dong)
        for (int i = 0; i < 6; i++) {
            String lbl = customLabels.containsKey(i) ? customLabels.get(i) : leftDefLabels[i];
            HudAction act = customActions.get(i);
            int code = (act != null && act.code > 0) ? act.code : leftDefCodes[i];

            KeyData k = new KeyData(lbl, code, 1f, false);
            k.isWing = true;
            k.isRight = false;
            k.wingIndex = i;
            k.customAction = act;
            k.internalLabel = wingInternalLabel(lbl, code, false);
            mWingKeys.add(k);
        }

        // Right Top Wing (indices 6..11: 3 cot x 2 dong)
        for (int i = 0; i < 6; i++) {
            int slotIdx = 6 + i;
            String lbl = customLabels.containsKey(slotIdx) ? customLabels.get(slotIdx) : rightDefLabels[i];
            HudAction act = customActions.get(slotIdx);
            int code = (act != null && act.code > 0) ? act.code : rightDefCodes[i];

            KeyData k = new KeyData(lbl, code, 1f, false);
            k.isWing = true;
            k.isRight = true;
            k.wingIndex = slotIdx;
            k.customAction = act;
            k.internalLabel = wingInternalLabel(lbl, code, true);
            mWingKeys.add(k);
        }
    }

    // Map wing nut phu sang internal modifier de combo duoc voi phim chu.
    // Uu tien code (rebind), fallback sang label khi code la UNKNOWN.
    private String wingInternalLabel(String lbl, int code, boolean right) {
        if (code == KeyEvent.KEYCODE_CTRL_LEFT) return "CtrlL";
        if (code == KeyEvent.KEYCODE_ALT_LEFT) return "AltL";
        if (code == KeyEvent.KEYCODE_SHIFT_LEFT) return "ShiftL";
        if (code == KeyEvent.KEYCODE_META_LEFT) return "MetaL";
        if (code == KeyEvent.KEYCODE_CTRL_RIGHT) return "CtrlR";
        if (code == KeyEvent.KEYCODE_ALT_RIGHT) return "AltR";
        if (code == KeyEvent.KEYCODE_SHIFT_RIGHT) return "ShiftR";
        if (code == KeyEvent.KEYCODE_META_RIGHT) return "MetaL";
        if (code == KeyEvent.KEYCODE_CAPS_LOCK) return "Caps";
        if (lbl == null) return "Wing";
        String u = lbl.trim().toUpperCase();
        if (u.equals("CTRL")) return right ? "CtrlR" : "CtrlL";
        if (u.equals("ALT")) return right ? "AltR" : "AltL";
        if (u.equals("SHIFT")) return right ? "ShiftR" : "ShiftL";
        if (u.equals("SUPER") || u.equals("META")) return "MetaL";
        if (u.equals("CAPS")) return "Caps";
        return lbl;
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
            case "+": return KeyEvent.KEYCODE_PLUS;
            case "@": return KeyEvent.KEYCODE_AT;
            case "#": return KeyEvent.KEYCODE_POUND;
            case "$": return KeyEvent.KEYCODE_4; // Shift+4
            case "_": return KeyEvent.KEYCODE_MINUS;
            case "/": return KeyEvent.KEYCODE_SLASH;
            case "%": return KeyEvent.KEYCODE_5;
            case "&": return KeyEvent.KEYCODE_7;
            case "*": return KeyEvent.KEYCODE_STAR;
            case "(": return KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN;
            case ")": return KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN;
            case "\"": return KeyEvent.KEYCODE_APOSTROPHE;
            case "'": return KeyEvent.KEYCODE_APOSTROPHE;
            case ":": return KeyEvent.KEYCODE_SEMICOLON;
            case ";": return KeyEvent.KEYCODE_SEMICOLON;
            case "!": return KeyEvent.KEYCODE_1;
            case "?": return KeyEvent.KEYCODE_SLASH;
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
            case "Enter": return KeyEvent.KEYCODE_ENTER;
            case "Shift":
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
            case "Ctrl":
            case "CtrlL": return KeyEvent.KEYCODE_CTRL_LEFT;
            case "CtrlR": return KeyEvent.KEYCODE_CTRL_RIGHT;
            case "Alt":
            case "AltL": return KeyEvent.KEYCODE_ALT_LEFT;
            case "AltR": return KeyEvent.KEYCODE_ALT_RIGHT;
            case "Home": return KeyEvent.KEYCODE_MOVE_HOME;
            case "End": return KeyEvent.KEYCODE_MOVE_END;
            case "SUPER":
            case "Meta":
            case "MetaL": return KeyEvent.KEYCODE_META_LEFT;
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
                setRotation(0f);
            } else {
                setRotation(0f);
                layoutKeys(w, h);
                post(this::setInitialPosition);
            }
        } catch (Exception e) {
            Log.e(TAG, "onSizeChanged error", e);
        }
    }

    // Nghieng doi xung tung phim arc: trai -7, phai +7 (dau ngoai venh len).
    // Wings/SPACE khong xoay. Luu vao currentRotation de hit-test khop.
    private static final float ARC_TILT_DEG = 7f;

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

        // 3 concentric rings sweeping out from thumb corners
        float[] ringRadii = {
                92f * density,
                142f * density,
                192f * density
        };
        float arcSpanDeg = 82f;

        // 1. Layout Left Arc (Pivot: bottom-left corner near edge)
        float leftPivotX = -8f * density;
        float leftPivotY = viewH - 4f * density;
        float leftStartAngle = -80f;
        float leftPitch = arcSpanDeg / 5f;

        for (KeyData k : mArcKeys) {
            if (k.isRight) continue;
            float r = ringRadii[k.ringIndex];
            k.pivotX = leftPivotX;
            k.pivotY = leftPivotY;
            k.baseRadius = r;
            k.keyHeight = 36f * density;
            k.keyWidth = 36f * density;

            k.baseAngleDeg = leftStartAngle + (k.colIndex + 0.5f) * leftPitch;

            double rad = Math.toRadians(k.baseAngleDeg);
            k.currentCx = leftPivotX + r * (float) Math.cos(rad);
            k.currentCy = leftPivotY + r * (float) Math.sin(rad);
            k.currentRotation = 0f;
        }

        // 2. Layout Right Arc (Pivot: bottom-right corner near edge)
        float rightPivotX = viewW + 8f * density;
        float rightPivotY = viewH - 4f * density;
        float rightStartAngle = 178f;
        float rightPitch = arcSpanDeg / 6f;

        for (KeyData k : mArcKeys) {
            if (!k.isRight) continue;
            float r = ringRadii[k.ringIndex];
            k.pivotX = rightPivotX;
            k.pivotY = rightPivotY;
            k.baseRadius = r;
            k.keyHeight = 36f * density;
            k.keyWidth = 36f * density;

            k.baseAngleDeg = rightStartAngle + (k.colIndex + 0.5f) * rightPitch;

            double rad = Math.toRadians(k.baseAngleDeg);
            k.currentCx = rightPivotX + r * (float) Math.cos(rad);
            k.currentCy = rightPivotY + r * (float) Math.sin(rad);
            k.currentRotation = 0f;
        }

        // 3. Layout Slim Flat Top Wings (lowered close to letter keys, clean rounded rects)
        float wingW = 102f * density;
        float spacing = 4f * density;
        float wingBtnH = 26f * density;
        float topOfArc = viewH - 4f * density - ringRadii[2];
        float wingTop = Math.max(16f * density, topOfArc - 2f * wingBtnH - spacing - 8f * density);

        // Left Wing
        float lwLeft = 8f * density;
        float lwColW = (wingW - spacing * 2f) / 3f;

        for (KeyData k : mWingKeys) {
            if (k.isRight) continue;
            int col = k.wingIndex % 3;
            int row = k.wingIndex / 3;
            float kLeft = lwLeft + col * (lwColW + spacing);
            float kRight = kLeft + lwColW;
            float kTop = wingTop + row * (wingBtnH + spacing);
            float kBottom = kTop + wingBtnH;
            k.beveledPath = null;
            k.wingBounds.set(kLeft, kTop, kRight, kBottom);
        }

        // Right Wing
        float rwRight = viewW - 8f * density;
        float rwLeft = rwRight - wingW;
        float rwColW = (wingW - spacing * 2f) / 3f;

        for (KeyData k : mWingKeys) {
            if (!k.isRight) continue;
            int idx = k.wingIndex - 6;
            int col = idx % 3;
            int row = idx / 3;
            float kLeft = rwLeft + col * (rwColW + spacing);
            float kRight = kLeft + rwColW;
            float kTop = wingTop + row * (wingBtnH + spacing);
            float kBottom = kTop + wingBtnH;
            k.beveledPath = null;
            k.wingBounds.set(kLeft, kTop, kRight, kBottom);
        }

        // 4. Layout Bottom Rows: Split Space + Caps + Comma/Period near thumbs
        float btnH = 34f * density;
        float btnBottom = viewH - 10f * density;
        float btnTop = btnBottom - btnH;
        float gap = 6f * density;

        // Left cluster (from left edge towards center): ?123 | Caps | , | SpaceL
        float curX = 10f * density;
        float symW = 42f * density;
        if (mSymbolToggleKey != null) {
            mSymbolToggleKey.rect.set(Math.round(curX), Math.round(btnTop),
                    Math.round(curX + symW), Math.round(btnBottom));
            mSymbolToggleKey.currentLabel = mIsArcSymbolLayer ? "ABC" : "?123";
        }
        curX += symW + gap;

        float capsW = 42f * density;
        if (mCapsKey != null) {
            mCapsKey.rect.set(Math.round(curX), Math.round(btnTop),
                    Math.round(curX + capsW), Math.round(btnBottom));
        }
        curX += capsW + gap;

        float commaW = 36f * density;
        if (mCommaKey != null) {
            mCommaKey.rect.set(Math.round(curX), Math.round(btnTop),
                    Math.round(curX + commaW), Math.round(btnBottom));
            mCommaKey.currentLabel = ",";
        }
        curX += commaW + gap;

        float spaceW = 84f * density;
        if (mLeftSpaceKey != null) {
            mLeftSpaceKey.rect.set(Math.round(curX), Math.round(btnTop),
                    Math.round(curX + spaceW), Math.round(btnBottom));
            mLeftSpaceKey.currentLabel = "";
        }

        // Right cluster (from right edge towards center): SpaceR | . | Dismiss
        float curRight = viewW - 10f * density;
        float disW = 42f * density;
        if (mCornerDismissKey != null) {
            mCornerDismissKey.rect.set(Math.round(curRight - disW), Math.round(btnTop),
                    Math.round(curRight), Math.round(btnBottom));
        }
        curRight -= (disW + gap);

        float periodW = 36f * density;
        if (mPeriodKey != null) {
            mPeriodKey.rect.set(Math.round(curRight - periodW), Math.round(btnTop),
                    Math.round(curRight), Math.round(btnBottom));
            mPeriodKey.currentLabel = ".";
        }
        curRight -= (periodW + gap);

        if (mRightSpaceKey != null) {
            mRightSpaceKey.rect.set(Math.round(curRight - spaceW), Math.round(btnTop),
                    Math.round(curRight), Math.round(btnBottom));
            mRightSpaceKey.currentLabel = "";
        }
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
                    k.modActive = mCapsState != CAPS_OFF;
                }
            }
            for (KeyData k : mWingKeys) {
                if ("Caps".equals(k.internalLabel)) {
                    k.modActive = mCapsState != CAPS_OFF;
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
        boolean shouldApplyCaps = isLetter && mCapsState != CAPS_OFF && !rightShiftPressed;

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
        all.addAll(mWingKeys);
        for (KeyData k : all) {
            if (isLeftModifier(k) && getLeftModifierState(k)) {
                mods.add(k);
            }
        }
        // Wing modifier dang giu da cham (multi-touch): lay tu activePointers.
        for (int i = 0; i < activePointers.size(); i++) {
            KeyData held = activePointers.valueAt(i);
            if (held != null && held.isWing && isLeftModifier(held) && !mods.contains(held)) {
                mods.add(held);
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

    // Caps kieu Gboard: cham 1 = hoa 1 ky tu, cham 2 nhanh = khoa, cham nua = tat.
    private void tapCapsKey(KeyData k) {
        long now = android.os.SystemClock.uptimeMillis();
        if (mCapsState == CAPS_LOCK) {
            mCapsState = CAPS_OFF;
        } else if (mCapsState == CAPS_ONCE && (now - mLastCapsTapTime) <= CAPS_DOUBLE_TAP_MS) {
            mCapsState = CAPS_LOCK;
        } else {
            mCapsState = CAPS_ONCE;
        }
        mLastCapsTapTime = now;
        applySymbolLayer();
        invalidate();
    }

    // One-shot chi bi phim ky tu thuong tieu thu; space/dieu huong giu nguyen.
    private void consumeCapsOnce(int keyCode) {
        if (mCapsState == CAPS_ONCE && isPrintableKeyCode(keyCode)) {
            mCapsState = CAPS_OFF;
            applySymbolLayer();
        }
    }

    private boolean isPrintableKeyCode(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_UNKNOWN) return false;
        if (isDirectionKey(keyCode)) return false;
        if (keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_MOVE_END) return false;
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) return false;
        if (keyCode == KeyEvent.KEYCODE_ENTER) return false;
        return keyCode > 0;
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

                    // 1. Check Corner Dismiss Key
                    if (hitKey.isCornerDismiss) {
                        hideWithAnimation(null);
                        return true;
                    }

                    // 2. Check Symbol Layer Toggle (?123 / ABC)
                    if (hitKey.isSymbolToggle) {
                        mIsArcSymbolLayer = !mIsArcSymbolLayer;
                        initArcKeys();
                        layoutSplitArc(getWidth(), getHeight());
                        if (mSymbolToggleKey != null) {
                            mSymbolToggleKey.currentLabel = mIsArcSymbolLayer ? "ABC" : "?123";
                        }
                        invalidate();
                        return true;
                    }

                    // 3. Top Wing key long-press check for rebind
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

                    // 4. Letter key with long-press number (Q->1, W->2 ... P->0)
                    if (hitKey.isArc && hitKey.longPressNumber != null) {
                        mPendingLongPressKey = hitKey;
                        final KeyData numKey = hitKey;
                        mLongPressHandler.postDelayed(() -> {
                            if (mPendingLongPressKey == numKey) {
                                mPendingLongPressKey = null;
                                numKey.longPressFired = true;
                                if (isHapticEnabled()) {
                                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                }
                                int numKeyCode = getKeyCodeForLabel(numKey.longPressNumber);
                                sendKey(numKeyCode, true);
                                postDelayed(() -> sendKey(numKeyCode, false), 40);
                                numKey.pressed = false;
                                invalidate();
                            }
                        }, 350); // Snappy 350ms long-press
                    }

                    if (isLeftModifier(hitKey)) {
                        toggleLeftModifier(hitKey);
                        return true;
                    }

                    if ("Caps".equals(hitKey.internalLabel)) {
                        tapCapsKey(hitKey);
                        return true;
                    }

                    if (isRightModifier(hitKey)) {
                        activePointers.put(pointerId, hitKey);
                        pressRightModifier(hitKey);
                        return true;
                    }

                    activePointers.put(pointerId, hitKey);
                    hitKey.pressed = true;

                    // Nut phu la modifier (CTRL/ALT/SHIFT): tap = latch de combo 1 ngon,
                    // giu da cham + cham phim chu = combo nho evdev down san.
                    if (hitKey.isWing && isLeftModifier(hitKey)
                            && (hitKey.customAction == null
                                || HudAction.TYPE_MODIFIER.equals(hitKey.customAction.type))) {
                        toggleLeftModifier(hitKey);
                        return true;
                    }

                    if (hitKey.isWing && hitKey.customAction != null) {
                        dispatchWingAction(hitKey.customAction, true);
                    } else if (hitKey.longPressNumber == null) {
                        int code = hitKey.currentKeyCode;
                        if (isDirectionKey(code) || code == KeyEvent.KEYCODE_MOVE_HOME || code == KeyEvent.KEYCODE_MOVE_END) {
                            sendKey(code, true);
                        } else if (leftShiftOn || leftCtrlOn || leftAltOn) {
                            executeCombination(code);
                            consumeCapsOnce(code);
                        } else {
                            sendKeyWithCaps(code, true);
                            consumeCapsOnce(code);
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

                    if (released.isArc && released.longPressNumber != null) {
                        if (!released.longPressFired) {
                            int code = released.currentKeyCode;
                            sendKeyWithCaps(code, true);
                            sendKeyWithCaps(code, false);
                        }
                        released.longPressFired = false;
                    } else if (released.isWing && released.customAction != null) {
                        // Wing latch modifier: DOWN chi toggle latch, khong gui down
                        // nen UP khong gui up keo lech trang thai evdev.
                        if (!(isLeftModifier(released)
                                && HudAction.TYPE_MODIFIER.equals(released.customAction.type))) {
                            dispatchWingAction(released.customAction, false);
                        }
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
                            float hitRadius = 24f * getResources().getDisplayMetrics().density;
                            stillHit = (dx * dx + dy * dy <= hitRadius * hitRadius);
                        } else if (key.isWing) {
                            stillHit = key.wingBounds.contains(px, py);
                        } else {
                            stillHit = key.rect.contains((int) px, (int) py);
                        }

                        if (!stillHit) {
                            mPendingLongPressKey = null;
                            mLongPressHandler.removeCallbacksAndMessages(null);
                            key.pressed = false;
                            if (key.isWing && key.customAction != null) {
                                if (!(isLeftModifier(key)
                                        && HudAction.TYPE_MODIFIER.equals(key.customAction.type))) {
                                    dispatchWingAction(key.customAction, false);
                                }
                            } else if (!key.longPressFired) {
                                int kc = key.currentKeyCode;
                                if (isRightModifier(key)) {
                                    releaseRightModifier(key);
                                } else if (isDirectionKey(kc) || kc == KeyEvent.KEYCODE_MOVE_HOME || kc == KeyEvent.KEYCODE_MOVE_END) {
                                    sendKey(kc, false);
                                } else if (!isLeftModifier(key) && !"Caps".equals(key.internalLabel)) {
                                    sendKeyWithCaps(kc, false);
                                }
                            }
                            key.longPressFired = false;
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
                        key.longPressFired = false;
                        if (key.isWing && key.customAction != null) {
                            if (!(isLeftModifier(key)
                                    && HudAction.TYPE_MODIFIER.equals(key.customAction.type))) {
                                dispatchWingAction(key.customAction, false);
                            }
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
            float density = getResources().getDisplayMetrics().density;
            // Check bottom keys
            if (mCapsKey != null && mCapsKey.rect.contains((int) x, (int) y)) {
                return mCapsKey;
            }
            if (mLeftSpaceKey != null && mLeftSpaceKey.rect.contains((int) x, (int) y)) {
                return mLeftSpaceKey;
            }
            if (mRightSpaceKey != null && mRightSpaceKey.rect.contains((int) x, (int) y)) {
                return mRightSpaceKey;
            }
            if (mCommaKey != null && mCommaKey.rect.contains((int) x, (int) y)) {
                return mCommaKey;
            }
            if (mPeriodKey != null && mPeriodKey.rect.contains((int) x, (int) y)) {
                return mPeriodKey;
            }
            if (mSymbolToggleKey != null && mSymbolToggleKey.rect.contains((int) x, (int) y)) {
                return mSymbolToggleKey;
            }
            if (mCornerDismissKey != null && mCornerDismissKey.rect.contains((int) x, (int) y)) {
                return mCornerDismissKey;
            }
            // Check top wings
            for (KeyData k : mWingKeys) {
                if (k.wingBounds.contains(x, y)) {
                    return k;
                }
            }
            // Check radial arc keys (circles, hitRadius generous for effortless typing)
            float hitRadius = 22f * density;
            float hitRadiusSq = hitRadius * hitRadius;
            for (KeyData k : mArcKeys) {
                float dx = x - k.currentCx;
                float dy = y - k.currentCy;
                if (dx * dx + dy * dy <= hitRadiusSq) {
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

                float density = getResources().getDisplayMetrics().density;
                if (!drawKeyIcon(canvas, k, r.centerX(), r.centerY(),
                        Math.min(r.width(), keyHeight) * 0.32f, 255, density)) {
                    textPaint.setColor(textColor);
                    float textSize = keyHeight * 0.4f;
                    if (textSize <= 0) textSize = 20;
                    textPaint.setTextSize(textSize);
                    float cx = r.centerX();
                    float cy = r.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2);
                    String display = k.currentLabel != null ? k.currentLabel : k.defaultLabel;
                    canvas.drawText(display, cx, cy, textPaint);
                }
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

    // Nut day: 2 nua space hoac phim , . (bo tron + text).
    private void drawBottomPill(Canvas canvas, KeyData k, String glyph,
                                boolean roundLeft, boolean roundRight,
                                int alpha, float density) {
        if (k == null) return;
        Rect r = k.rect;
        int bg = resolveKeyBg(k);
        keyBgPaint.setColor(bg);
        keyBgPaint.setAlpha(alpha);
        float cr = 8f * density;
        canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyBgPaint);
        keyStrokePaint.setAlpha(Math.round(0x33 * (alpha / 255f)));
        canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyStrokePaint);
        if (glyph != null) {
            textPaint.setColor(M3.COLOR_TEXT_MUTED);
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(13f * density);
            float cy = r.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText(glyph, r.centerX(), cy, textPaint);
        }
    }

    // Icon vector thay text cho Caps / BKSP / Enter. Tra ve true neu da ve.
    private boolean drawKeyIcon(Canvas canvas, KeyData k, float cx, float cy,
                                float size, int alpha, float density) {
        String in = k.internalLabel;
        if ("Caps".equals(in)) {
            drawShiftIcon(canvas, cx, cy, size, alpha, density, mCapsState);
            return true;
        } else if ("BKSP".equals(in)) {
            drawBackspaceIcon(canvas, cx, cy, size, alpha, density);
            return true;
        } else if ("Enter".equals(in)) {
            drawEnterIcon(canvas, cx, cy, size, alpha, density);
            return true;
        }
        return false;
    }

    private void setupIconPaint(float density, int alpha) {
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(Math.max(1.5f, 1.6f * density));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setAlpha(alpha);
    }

    // Mui ten shift: net manh = OFF, to day = ONCE, to day + gach chan = LOCK.
    private void drawShiftIcon(Canvas canvas, float cx, float cy, float s,
                               int alpha, float density, int capsState) {
        setupIconPaint(density, alpha);
        Path p = new Path();
        p.moveTo(cx - 0.55f * s, cy + 0.35f * s);
        p.lineTo(cx, cy - 0.45f * s);
        p.lineTo(cx + 0.55f * s, cy + 0.35f * s);
        if (capsState == CAPS_OFF) {
            canvas.drawPath(p, iconPaint);
            canvas.drawLine(cx, cy - 0.45f * s, cx, cy + 0.45f * s, iconPaint);
        } else {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.WHITE);
            fill.setAlpha(alpha);
            p.lineTo(cx + 0.28f * s, cy + 0.35f * s);
            p.lineTo(cx + 0.28f * s, cy + 0.55f * s);
            p.lineTo(cx - 0.28f * s, cy + 0.55f * s);
            p.lineTo(cx - 0.28f * s, cy + 0.35f * s);
            p.close();
            canvas.drawPath(p, fill);
            if (capsState == CAPS_LOCK) {
                canvas.drawLine(cx - 0.55f * s, cy + 0.75f * s,
                        cx + 0.55f * s, cy + 0.75f * s, iconPaint);
            }
        }
    }

    // Nut xoa: khung chu nhat + X ben trong.
    private void drawBackspaceIcon(Canvas canvas, float cx, float cy, float s,
                                   int alpha, float density) {
        setupIconPaint(density, alpha);
        float hw = 0.62f * s, hh = 0.42f * s, nose = 0.22f * s;
        Path p = new Path();
        p.moveTo(cx - hw + nose, cy - hh);
        p.lineTo(cx + hw - nose, cy - hh);
        p.lineTo(cx + hw, cy);
        p.lineTo(cx + hw - nose, cy + hh);
        p.lineTo(cx - hw + nose, cy + hh);
        p.lineTo(cx - hw, cy);
        p.close();
        canvas.drawPath(p, iconPaint);
        float ix = cx + 0.02f * s, ih = 0.16f * s;
        canvas.drawLine(ix - ih, cy - ih, ix + ih, cy + ih, iconPaint);
        canvas.drawLine(ix - ih, cy + ih, ix + ih, cy - ih, iconPaint);
    }

    // Nut Enter: net xuong roi re trai + mui ten.
    private void drawEnterIcon(Canvas canvas, float cx, float cy, float s,
                               int alpha, float density) {
        setupIconPaint(density, alpha);
        Path p = new Path();
        p.moveTo(cx + 0.45f * s, cy - 0.5f * s);
        p.lineTo(cx + 0.45f * s, cy + 0.25f * s);
        p.quadTo(cx + 0.45f * s, cy + 0.45f * s, cx + 0.2f * s, cy + 0.45f * s);
        p.lineTo(cx - 0.35f * s, cy + 0.45f * s);
        canvas.drawPath(p, iconPaint);
        canvas.drawLine(cx - 0.35f * s, cy + 0.45f * s, cx - 0.05f * s, cy + 0.15f * s, iconPaint);
        canvas.drawLine(cx - 0.35f * s, cy + 0.45f * s, cx - 0.05f * s, cy + 0.75f * s, iconPaint);
    }

    private void drawSplitArcLayout(Canvas canvas) {
        int animType = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SPLIT_ARC_ANIM, ANIM_FAN);
        int alpha = Math.max(0, Math.min(255, Math.round(255 * mAnimProgress)));

        float density = getResources().getDisplayMetrics().density;
        float viewW = getWidth();
        float viewH = getHeight();

        // 1. Draw Radial Arc Keys as Circles with upright text
        float keyRadius = 18f * density;
        for (KeyData k : mArcKeys) {
            float cx = k.currentCx;
            float cy = k.currentCy;

            if (animType == ANIM_FAN) {
                float spread = (float) Math.sin(mAnimProgress * Math.PI / 2.0);
                float angle = k.isRight
                        ? (k.baseAngleDeg + (1f - spread) * 26f)
                        : (k.baseAngleDeg - (1f - spread) * 26f);
                float radius = k.baseRadius * (0.65f + 0.35f * spread);
                double rad = Math.toRadians(angle);
                cx = k.pivotX + radius * (float) Math.cos(rad);
                cy = k.pivotY + radius * (float) Math.sin(rad);
            } else if (animType == ANIM_CORNER_ZOOM) {
                float zoom = 0.35f + 0.65f * mAnimProgress;
                float pivotX = k.isRight ? viewW : 0f;
                float pivotY = viewH;
                cx = pivotX + (k.currentCx - pivotX) * zoom;
                cy = pivotY + (k.currentCy - pivotY) * zoom;
            }
            k.currentCx = cx;
            k.currentCy = cy;

            int bg = resolveKeyBg(k);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);

            canvas.drawCircle(cx, cy, keyRadius, keyBgPaint);
            keyStrokePaint.setAlpha(Math.round(0x33 * (alpha / 255f)));
            canvas.drawCircle(cx, cy, keyRadius, keyStrokePaint);

            // Icon thay text cho BKSP / Enter hoac chu thuong (thang dung, khong nghieng)
            if (!drawKeyIcon(canvas, k, cx, cy, keyRadius * 0.72f, alpha, density)) {
                textPaint.setColor(textColor);
                textPaint.setAlpha(alpha);
                textPaint.setTextSize(13.5f * density);
                float textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f);
                String display = k.currentLabel != null ? k.currentLabel : k.defaultLabel;
                canvas.drawText(display, cx, textY, textPaint);
            }

            // Subscript Number in top-right corner of circle
            if (k.longPressNumber != null) {
                subTextPaint.setAlpha(Math.round(0xCC * (alpha / 255f)));
                subTextPaint.setTextSize(8.5f * density);
                canvas.drawText(k.longPressNumber, cx + keyRadius * 0.45f, cy - keyRadius * 0.35f, subTextPaint);
            }
        }

        // 2. Draw Slim Flat Top Wings (clean rounded rects, lowered close to letter keys)
        float wingSlideY = (animType == ANIM_FAN) ? ((1f - mAnimProgress) * -20f * density) : 0f;
        for (KeyData k : mWingKeys) {
            canvas.save();
            canvas.translate(0, wingSlideY);

            int bg = resolveKeyBg(k);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);

            RectF r = k.wingBounds;
            float cr = 6f * density;
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyBgPaint);
            keyStrokePaint.setAlpha(Math.round(0x22 * (alpha / 255f)));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyStrokePaint);

            textPaint.setColor(M3.COLOR_PRIMARY);
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(10.5f * density);
            float cx = k.wingBounds.centerX();
            float cy = k.wingBounds.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f);
            String display = k.currentLabel != null ? k.currentLabel : k.defaultLabel;
            canvas.drawText(display, cx, cy, textPaint);

            canvas.restore();
        }

        // 3. Draw Split Space halves + , . beside them
        if (mLeftSpaceKey != null) drawBottomPill(canvas, mLeftSpaceKey, null, true, true, alpha, density);
        if (mRightSpaceKey != null) drawBottomPill(canvas, mRightSpaceKey, null, true, true, alpha, density);
        if (mCommaKey != null) drawBottomPill(canvas, mCommaKey, ",", false, false, alpha, density);
        if (mPeriodKey != null) drawBottomPill(canvas, mPeriodKey, ".", false, false, alpha, density);

        // 4. Draw Caps Key near ?123
        if (mCapsKey != null) {
            Rect r = mCapsKey.rect;
            int bg = resolveKeyBg(mCapsKey);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);
            float cr = 8f * density;
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyBgPaint);
            keyStrokePaint.setAlpha(Math.round(0x22 * (alpha / 255f)));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyStrokePaint);
            drawShiftIcon(canvas, r.centerX(), r.centerY(), r.height() * 0.42f, alpha, density, mCapsState);
        }

        // 5. Draw Symbol Toggle Key (?123 / ABC)
        if (mSymbolToggleKey != null) {
            Rect r = mSymbolToggleKey.rect;
            int bg = resolveKeyBg(mSymbolToggleKey);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);
            float cr = 8f * density;
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyBgPaint);
            keyStrokePaint.setAlpha(Math.round(0x22 * (alpha / 255f)));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyStrokePaint);

            textPaint.setColor(M3.COLOR_SECONDARY);
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(11.5f * density);
            float cy = r.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText(mSymbolToggleKey.currentLabel, r.centerX(), cy, textPaint);
        }

        // 6. Draw Corner Fan-shaped Dismiss Key
        if (mCornerDismissKey != null) {
            Rect r = mCornerDismissKey.rect;
            int bg = resolveKeyBg(mCornerDismissKey);
            keyBgPaint.setColor(bg);
            keyBgPaint.setAlpha(alpha);
            float cr = 8f * density;
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyBgPaint);
            keyStrokePaint.setAlpha(Math.round(0x22 * (alpha / 255f)));
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr, cr, keyStrokePaint);

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(M3.COLOR_PRIMARY);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * density);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setAlpha(alpha);

            float cx = r.centerX();
            float cy = r.centerY();
            float w = 6f * density;
            float h = 4f * density;

            canvas.drawRoundRect(cx - w, cy - h - 1.5f * density, cx + w, cy + 1.5f * density, 1.5f * density, 1.5f * density, p);
            canvas.drawLine(cx - 3.5f * density, cy + 3.5f * density, cx, cy + 5.5f * density, p);
            canvas.drawLine(cx, cy + 5.5f * density, cx + 3.5f * density, cy + 3.5f * density, p);
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

        // Gboard Long-press numbers
        String longPressNumber = null;
        boolean longPressFired = false;

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

        // Custom Special keys
        boolean isSpace = false;
        boolean isSymbolToggle = false;
        boolean isCornerDismiss = false;

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
