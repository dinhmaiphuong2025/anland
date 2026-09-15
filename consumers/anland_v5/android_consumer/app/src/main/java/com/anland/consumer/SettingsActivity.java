package com.anland.consumer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar; // ===== 新增导入
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.anland.consumer.theme.M3;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;


public class SettingsActivity extends Activity {
    private static final String TAG = "AnlandSettings";
    private static final String PREFS_NAME = "anland_settings";
    private static final String KEY_BOUND_KEYCODE = "bound_keycode";
    private static final String KEY_SOCKET_PATH = "socket_path";
    private static final String KEY_USE_ROOT = "use_root";
    private static final String KEY_MIC_ENABLED = "mic_enabled";
    private static final String KEY_CAMERA_ENABLED = "camera_enabled";
    private static final String KEY_AUDIO_KEEPALIVE = "audio_keepalive";
    private static final String KEY_SPEAKER_LATENCY_MS = "speaker_latency_ms";
    private static final String KEY_MIC_LATENCY_MS = "mic_latency_ms";
    private static final String KEY_ACCESSIBILITY_ENABLED = "accessibility_key_intercept";
    private static final String KEY_IMMERSIVE_ENABLED = ImmersiveMode.KEY_ENABLED;
    private static final String KEY_IMMERSIVE_KEYCODE = ImmersiveMode.KEY_KEYCODE;
    private static final String KEY_IMMERSIVE_SCANCODE = ImmersiveMode.KEY_SCANCODE;
    private static final String KEY_EXTRA_KEYS_MODE = "extra_keys_mode";
    // Mapped to R.array.extra_keys_mode_options positions
    private static final String MODE_ALWAYS = "always";
    private static final String MODE_NEVER = "never";
    private static final String MODE_WITH_KEYBOARD = "with_keyboard";
    private static final String[] EXTRA_KEYS_MODES = {MODE_ALWAYS, MODE_NEVER, MODE_WITH_KEYBOARD};
    private static final String KEY_BACK_OPENS_EXTRA_KEYS = "back_opens_extra_keys";
    private static final String KEY_EXTRA_KEYS_LAYOUT = "extra_keys_layout";
    private static final String KEY_KEYBOARD_FLOATING = "keyboard_floating";
    private static final String KEY_NOTIFICATION_ENABLED = "settings_notification";
    private static final String KEY_ORIENTATION = "screen_orientation";
    private static final String[] ORIENTATION_VALUES = {"default", "landscape", "portrait"};
    public static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled";
    private static final String DEFAULT_SOCKET_PATH = "/data/local/tmp/display_daemon.sock";
    private static final int UNBOUND = -1;

    // ===== 新增：触摸板 Key =====
    private static final String KEY_TOUCHPAD_MODE = "touchpad_mode";
    private static final String KEY_MOUSE_ACCEL = "mouse_speed";
    private static final String KEY_POINTER_CAPTURE = "pointer_capture";
    private static final String KEY_SCROLL_SPEED = "scroll_speed";
    private static final String KEY_SCROLL_REVERSE = "scroll_reverse";
    private static final String KEY_SCROLL_THRESHOLD = "touchpad_scroll_threshold";
    private static final String KEY_MOVE_THRESHOLD = "touchpad_move_threshold";
    private static final String KEY_GESTURE_SCALE = "touchpad_gesture_scale";
    private static final String KEY_DISABLE_MULTI_FINGER_GESTURES =
            "disable_multi_finger_gestures";

    // Latency presets: target buffer in ms (0 = auto). The user-visible labels live
    // in the R.array.latency_labels string-array, parallel to this array.
    private static final int[] LATENCY_MS = {0, 1, 3, 5, 10, 20};

    // Which secondary page is on screen. Back returns HOME -> exits the activity.
    private enum Page { HOME, KEYBOARD, TOUCHPAD, CONNECTION, RESOLUTION, GENERAL }
    private Page currentPage = Page.HOME;

    // The key-binding row currently counting down, if any: it gets the next key
    // press. The rows themselves live in the page's view hierarchy.
    private KeyBinding listeningBinding;

    // Custom extra-keys layout editor (JSON), and the SAF file-picker request code.
    private EditText layoutInput;
    private static final int REQ_PICK_LAYOUT = 2001;
    private static final int REQ_BACKUP_SETTINGS = 2002;
    private static final int REQ_RESTORE_SETTINGS = 2003;

    // The socket path and window name of the Droidspaces-owned MainActivity
    // window that launched us. We have to forward them to every child Intent
    // that re-enters MainActivity (e.g. OPEN_HUD_EDITOR) so the editor
    // connects to the same daemon instead of the default socket path.
    private String mCurrentSocketPath = null;
    private String mCurrentWindowName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capture the daemon the user is looking at so the HUD-editor
        // entry can hand it back to MainActivity unchanged.
        Intent launched = getIntent();
        if (launched != null) {
            String sock = launched.getStringExtra(MainActivity.EXTRA_SOCKET_PATH);
            if (sock != null && !sock.trim().isEmpty()) {
                mCurrentSocketPath = sock.trim();
            }
            String name = launched.getStringExtra(MainActivity.EXTRA_WINDOW_NAME);
            if (name != null && !name.trim().isEmpty()) {
                mCurrentWindowName = name.trim();
            }
        }
        showHome();
    }

    // ============================================================
    // Navigation: a home list of categories, each opening a page.
    // Every page is a fresh LinearLayout wrapped by setContent().
    // ============================================================

    // Wrap `content` in the standard dark ScrollView, apply edge-to-edge insets,
    // and install it. Reused by the home list and every secondary page.
    private void setContent(final LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF11111B);
        scroll.addView(content);
        setContentView(scroll);

        // Edge-to-edge is enforced on Android 15+ (targetSdk 36): the system no
        // longer auto-resizes the window for the IME, so a manifest "adjustResize"
        // is ignored and the soft keyboard overlaps the bottom EditTexts. Take over
        // inset handling and pad the scrollable content by the system-bar + IME
        // insets ourselves, so the ScrollView can scroll the focused field above
        // the keyboard. Base padding (dp(24)) is preserved on all edges.
        getWindow().setDecorFitsSystemWindows(false);
        final int base = dp(24);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets in = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(base + in.left, base + in.top,
                         base + in.right, base + in.bottom);
            return insets;
        });
    }

    private void showHome() {
        stopListening();
        currentPage = Page.HOME;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout backRow = new LinearLayout(this);
        backRow.setOrientation(LinearLayout.HORIZONTAL);
        backRow.setGravity(Gravity.CENTER_VERTICAL);
        backRow.setPadding(0, 0, 0, dp(16));
        backRow.setClickable(true);
        backRow.setOnClickListener(v -> finish());

        M3.BackArrowView backArrow = new M3.BackArrowView(this);
        backRow.addView(backArrow);

        TextView backText = new TextView(this);
        backText.setText("Desktop");
        backText.setTextSize(14);
        backText.setTypeface(null, Typeface.BOLD);
        backText.setTextColor(M3.COLOR_PRIMARY);
        backText.setPadding(dp(8), 0, 0, 0);
        backRow.addView(backText);

        root.addView(backRow);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(20));
        root.addView(title);

        addCategoryRow(root, R.string.cat_keyboard_title,
            R.string.cat_keyboard_subtitle, this::showKeyboardPage);
        addCategoryRow(root, R.string.cat_touchpad_title,
            R.string.cat_touchpad_subtitle, this::showTouchpadPage);
        addCategoryRow(root, R.string.section_connection,
            R.string.cat_connection_subtitle, this::showConnectionPage);
        addCategoryRow(root, R.string.section_resolution,
            R.string.cat_resolution_subtitle, this::showResolutionPage);
        addCategoryRow(root, R.string.cat_general_title,
            R.string.cat_general_subtitle, this::showGeneralPage);

        // Build version, injected from git at build time (see app/build.gradle).
        TextView version = new TextView(this);
        version.setText(BuildConfig.VERSION_NAME
            + " (" + BuildConfig.VERSION_CODE + ")");
        version.setTextSize(12);
        version.setTextColor(0xFFA6ADC8);
        version.setGravity(Gravity.START);
        version.setPadding(0, dp(24), 0, 0);
        version.setAlpha(0.6f);
        root.addView(version);

        setContent(root);
    }

    // A tappable "title / subtitle" row styled as a refined Material card with drawn chevron.
    private void addCategoryRow(LinearLayout parent, int titleRes, int subtitleRes,
                                final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(16), dp(20), dp(16));
        row.setClickable(true);

        row.setBackground(M3.createRippleDrawable(this, M3.RADIUS_CARD, M3.COLOR_SURFACE_LOW, M3.COLOR_SURFACE_HIGH, M3.COLOR_BORDER_SUBTLE));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowLp);
        row.setOnClickListener(v -> onClick.run());

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(titleRes);
        t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(M3.COLOR_TEXT_PRIMARY);
        texts.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitleRes);
        s.setTextSize(13);
        s.setTextColor(M3.COLOR_TEXT_MUTED);
        s.setPadding(0, dp(2), 0, 0);
        texts.addView(s);

        row.addView(texts);

        M3.ChevronView chevron = new M3.ChevronView(this);
        row.addView(chevron);

        parent.addView(row);
    }

    // A fresh page root with a drawn vector back arrow and a bold page title.
    private LinearLayout newPage(int titleRes) {
        stopListening();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout backRow = new LinearLayout(this);
        backRow.setOrientation(LinearLayout.HORIZONTAL);
        backRow.setGravity(Gravity.CENTER_VERTICAL);
        backRow.setPadding(0, 0, 0, dp(16));
        backRow.setClickable(true);
        backRow.setOnClickListener(v -> showHome());

        M3.BackArrowView backArrow = new M3.BackArrowView(this);
        backRow.addView(backArrow);

        TextView backText = new TextView(this);
        backText.setText(R.string.settings_short_label);
        backText.setTextSize(14);
        backText.setTypeface(null, Typeface.BOLD);
        backText.setTextColor(M3.COLOR_PRIMARY);
        backText.setPadding(dp(8), 0, 0, 0);
        backRow.addView(backText);

        root.addView(backRow);

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(24);
        title.setTextColor(M3.COLOR_TEXT_PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(20));
        root.addView(title);

        return root;
    }

    private void showKeyboardPage() {
        currentPage = Page.KEYBOARD;
        LinearLayout root = newPage(R.string.cat_keyboard_title);
        buildVirtualKeyboardSection(root);
        buildImmersiveSection(root);
        buildHudSection(root);
        buildExtraKeysSection(root);
        buildCustomLayoutSection(root);
        buildAccessibilitySection(root);
        setContent(root);
    }

    // Custom HUD Control is a peer of the ExtraKeys bar and Immersive Mode:
    // a feature of the same level on the keyboard page, not a sub-feature of
    // the ExtraKeys row. The order is:
    //   1. Virtual Keyboard
    //   2. Immersive Mode
    //   3. Custom HUD Control   <-- this section
    //   4. ExtraKeys Bar
    //   5. Custom Layout (ExtraKeys JSON editor)
    //   6. Accessibility
    private void buildHudSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean useHud = prefs.getBoolean("use_hud_overlay", false);

        TextView header = new TextView(this);
        header.setText("Custom HUD Control");
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header);

        Switch hudSwitch = new Switch(this);
        M3.styleSwitch(hudSwitch);
        hudSwitch.setText("Enable Custom HUD Control (Floating Buttons)");
        hudSwitch.setChecked(useHud);
        hudSwitch.setPadding(0, dp(8), 0, dp(8));
        // Toggling the HUD must NOT touch KEY_EXTRA_KEYS_MODE anymore. The two
        // features are now independent peers: the user can run the floating
        // button overlay, the legacy ExtraKeys bar, or both at the same time.
        hudSwitch.setOnCheckedChangeListener((v, checked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean("use_hud_overlay", checked).apply();
            showKeyboardPage();
        });
        root.addView(hudSwitch);

        if (useHud) {
            // === Open Visual HUD Layout Editor ===
            // Styled like the existing "Bind Soft Keyboard Toggle Key" button:
            // a plain Material Button with just text + the default ripple, so
            // all the bind-style controls read as a single coherent row group.
            Button btnEditHud = new Button(this);
            styleSettingsButton(btnEditHud);
            btnEditHud.setText("Open Visual HUD Layout Editor");
            btnEditHud.setOnClickListener(v -> {
                // Re-enter the MainActivity that spawned us, passing the
                // daemon it is connected to. Without these extras the
                // editor falls back to the default socket, which is not
                // the Droidspaces-owned daemon, so Android spawns a second
                // task with a black screen. singleTask + SINGLE_TOP +
                // CLEAR_TOP brings the existing instance forward so
                // onNewIntent fires and the original task is reused.
                Intent intent = new Intent(this, MainActivity.class);
                intent.setAction("OPEN_HUD_EDITOR");
                if (mCurrentSocketPath != null) {
                    intent.putExtra(MainActivity.EXTRA_SOCKET_PATH,
                            mCurrentSocketPath);
                }
                if (mCurrentWindowName != null) {
                    intent.putExtra(MainActivity.EXTRA_WINDOW_NAME,
                            mCurrentWindowName);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
            root.addView(btnEditHud);

            TextView hudHint = new TextView(this);
            hudHint.setText("Design on-screen floating buttons. Add a TrackPoint (mouse or scroll mode) or a Super Gesture nub; each can be moved, resized and rebound to any key/combo/action with live snapping and precise numeric properties. The bottom key row is the legacy ExtraKeys bar and is unaffected by this toggle.");
            hudHint.setTextSize(12);
            hudHint.setTextColor(M3.COLOR_TEXT_MUTED);
            hudHint.setPadding(0, dp(4), 0, dp(12));
            root.addView(hudHint);
        }
    }

    private void showTouchpadPage() {
        currentPage = Page.TOUCHPAD;
        LinearLayout root = newPage(R.string.cat_touchpad_title);
        buildTouchpadSection(root);
        setContent(root);
    }

    private void showConnectionPage() {
        currentPage = Page.CONNECTION;
        LinearLayout root = newPage(R.string.section_connection);
        addConnectionSection(root);
        setContent(root);
    }

    private void showResolutionPage() {
        currentPage = Page.RESOLUTION;
        LinearLayout root = newPage(R.string.section_resolution);
        addResolutionSection(root);
        setContent(root);
    }

    private void showGeneralPage() {
        currentPage = Page.GENERAL;
        LinearLayout root = newPage(R.string.cat_general_title);
        buildOrientationSection(root);
        buildHapticSection(root);
        buildNotificationSection(root);
        buildBackupRestoreSection(root);
        setContent(root);
    }

    @Override
    public void onBackPressed() {
        // While listening for a key binding, let onKeyDown capture the Back key
        // instead of navigating back.
        if (listeningBinding != null) return;
        if (currentPage != Page.HOME) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    // ============================================================
    // Keyboard & Keys page sections
    // ============================================================

    private void buildVirtualKeyboardSection(LinearLayout root) {
        addSectionHeader(root, R.string.section_virtual_keyboard, 0);
        // Constructing the row appends it to `root`.
        new KeyBinding(root, KEY_BOUND_KEYCODE, null, R.string.bind_key_button);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Switch raiseDesktopSwitch = new Switch(this);
        M3.styleSwitch(raiseDesktopSwitch);
        raiseDesktopSwitch.setText(R.string.raise_desktop_for_soft_keyboard);
        raiseDesktopSwitch.setPadding(0, dp(8), 0, 0);
        raiseDesktopSwitch.setChecked(!prefs.getBoolean(KEY_KEYBOARD_FLOATING, false));
        raiseDesktopSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_KEYBOARD_FLOATING, !checked).apply());
        root.addView(raiseDesktopSwitch);

        TextView raiseDesktopHint = new TextView(this);
        raiseDesktopHint.setText(R.string.raise_desktop_for_soft_keyboard_hint);
        raiseDesktopHint.setTextSize(12);
        raiseDesktopHint.setTextColor(M3.COLOR_TEXT_MUTED);
        raiseDesktopHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(raiseDesktopHint);

        // Portrait IME Lock
        Switch portraitImeLockSwitch = new Switch(this);
        M3.styleSwitch(portraitImeLockSwitch);
        portraitImeLockSwitch.setText("Lock Soft Keyboard in Portrait");
        portraitImeLockSwitch.setPadding(0, dp(12), 0, 0);
        portraitImeLockSwitch.setChecked(prefs.getBoolean("portrait_ime_lock", false));
        portraitImeLockSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean("portrait_ime_lock", checked).apply());
        root.addView(portraitImeLockSwitch);

        TextView portraitImeLockHint = new TextView(this);
        portraitImeLockHint.setText("Keeps soft keyboard open in portrait mode across app switches. Automatically scales desktop back to full screen if manually dismissed.");
        portraitImeLockHint.setTextSize(12);
        portraitImeLockHint.setTextColor(M3.COLOR_TEXT_MUTED);
        portraitImeLockHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(portraitImeLockHint);

        // Landscape Split Arc Keyboard
        Switch splitArcSwitch = new Switch(this);
        M3.styleSwitch(splitArcSwitch);
        splitArcSwitch.setText("Landscape Split Arc Keyboard");
        splitArcSwitch.setPadding(0, dp(12), 0, 0);
        splitArcSwitch.setChecked(prefs.getBoolean("landscape_split_arc", true));
        splitArcSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean("landscape_split_arc", checked).apply());
        root.addView(splitArcSwitch);

        TextView splitArcHint = new TextView(this);
        splitArcHint.setText("Ergonomic dual-thumb radial keyboard with customizable beveled top wings, leaving the center desktop unobstructed.");
        splitArcHint.setTextSize(12);
        splitArcHint.setTextColor(M3.COLOR_TEXT_MUTED);
        splitArcHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(splitArcHint);

        TextView animLabel = new TextView(this);
        animLabel.setText("Arc Keyboard Animation Style");
        animLabel.setTextSize(14);
        animLabel.setTextColor(Color.WHITE);
        animLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(animLabel);

        LinearLayout animCard = new LinearLayout(this);
        animCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable aBg = new android.graphics.drawable.GradientDrawable();
        aBg.setCornerRadius(dp(8));
        aBg.setColor(0xFF181825);
        aBg.setStroke(dp(1), 0x22FFFFFF);
        animCard.setBackground(aBg);
        animCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner animSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        String[] animOptions = {"Fan Open (Quạt mở - Default)", "Corner Zoom (Phóng từ góc)"};
        setupDarkSpinner(animSpinner, animOptions);
        animSpinner.setSelection(prefs.getInt("split_arc_anim", 0));
        animSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putInt("split_arc_anim", pos).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        animCard.addView(animSpinner);
        root.addView(animCard);

        // Keyboard Handedness Layout
        TextView handLabel = new TextView(this);
        handLabel.setText("Keyboard Handedness Layout");
        handLabel.setTextSize(14);
        handLabel.setTextColor(Color.WHITE);
        handLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(handLabel);

        LinearLayout handCard = new LinearLayout(this);
        handCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable hBg = new android.graphics.drawable.GradientDrawable();
        hBg.setCornerRadius(dp(8));
        hBg.setColor(0xFF181825);
        hBg.setStroke(dp(1), 0x22FFFFFF);
        handCard.setBackground(hBg);
        handCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner handSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        String[] handOptions = {"Left-Handed (G & V on left - Default)", "Right-Handed (G & V on right)"};
        setupDarkSpinner(handSpinner, handOptions);
        String currentHand = prefs.getString("keyboard_handedness", "left");
        handSpinner.setSelection("right".equals(currentHand) ? 1 : 0);
        handSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString("keyboard_handedness", pos == 1 ? "right" : "left").apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        handCard.addView(handSpinner);
        root.addView(handCard);

        // Keyboard Theme
        TextView themeLabel = new TextView(this);
        themeLabel.setText("Keyboard Theme (Keys only, excluding Wings)");
        themeLabel.setTextSize(14);
        themeLabel.setTextColor(Color.WHITE);
        themeLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(themeLabel);

        LinearLayout themeCard = new LinearLayout(this);
        themeCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable tBg = new android.graphics.drawable.GradientDrawable();
        tBg.setCornerRadius(dp(8));
        tBg.setColor(0xFF181825);
        tBg.setStroke(dp(1), 0x22FFFFFF);
        themeCard.setBackground(tBg);
        themeCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner themeSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        String[] themeOptions = {"Filled (Default M3 Surface)", "Border Only (Transparent with border)", "Transparent Full (Text only without borders)"};
        setupDarkSpinner(themeSpinner, themeOptions);
        themeSpinner.setSelection(prefs.getInt("keyboard_theme", 0));
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putInt("keyboard_theme", pos).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        themeCard.addView(themeSpinner);
        root.addView(themeCard);

        // Keyboard Bottom Margin
        TextView marginLabel = new TextView(this);
        marginLabel.setText("Keyboard Bottom Margin (Plan A: lifts Wings synchronously)");
        marginLabel.setTextSize(14);
        marginLabel.setTextColor(Color.WHITE);
        marginLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(marginLabel);

        LinearLayout marginCard = new LinearLayout(this);
        marginCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable mBg = new android.graphics.drawable.GradientDrawable();
        mBg.setCornerRadius(dp(8));
        mBg.setColor(0xFF181825);
        mBg.setStroke(dp(1), 0x22FFFFFF);
        marginCard.setBackground(mBg);
        marginCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner marginSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        String[] marginOptions = {"8dp (Low - Default)", "16dp (Medium)", "24dp (High)", "32dp (Very High)"};
        setupDarkSpinner(marginSpinner, marginOptions);
        float currentMargin = prefs.getFloat("keyboard_bottom_margin_dp", 8f);
        int marginSel = 0;
        if (currentMargin == 16f) marginSel = 1;
        else if (currentMargin == 24f) marginSel = 2;
        else if (currentMargin == 32f) marginSel = 3;
        marginSpinner.setSelection(marginSel);
        marginSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                float val = 8f;
                if (pos == 1) val = 16f;
                else if (pos == 2) val = 24f;
                else if (pos == 3) val = 32f;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putFloat("keyboard_bottom_margin_dp", val).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        marginCard.addView(marginSpinner);
        root.addView(marginCard);

        // Key Height
        TextView heightLabel = new TextView(this);
        heightLabel.setText("Key Height");
        heightLabel.setTextSize(14);
        heightLabel.setTextColor(Color.WHITE);
        heightLabel.setPadding(0, dp(12), 0, dp(4));
        root.addView(heightLabel);

        LinearLayout heightCard = new LinearLayout(this);
        heightCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable htBg = new android.graphics.drawable.GradientDrawable();
        htBg.setCornerRadius(dp(8));
        htBg.setColor(0xFF181825);
        htBg.setStroke(dp(1), 0x22FFFFFF);
        heightCard.setBackground(htBg);
        heightCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner heightSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        String[] heightOptions = {"38dp (Compact)", "42dp (Default Gboard)", "46dp (Medium)", "50dp (Large)"};
        setupDarkSpinner(heightSpinner, heightOptions);
        float currentHeight = prefs.getFloat("keyboard_key_height_dp", 42f);
        int heightSel = 1;
        if (currentHeight == 38f) heightSel = 0;
        else if (currentHeight == 46f) heightSel = 2;
        else if (currentHeight == 50f) heightSel = 3;
        heightSpinner.setSelection(heightSel);
        heightSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                float val = 42f;
                if (pos == 0) val = 38f;
                else if (pos == 2) val = 46f;
                else if (pos == 3) val = 50f;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putFloat("keyboard_key_height_dp", val).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        heightCard.addView(heightSpinner);
        root.addView(heightCard);
    }

    /**
     * Immersive mode: a root helper takes the touchscreen, keyboard and pointer
     * away from Android for as long as the session lasts, so every input goes to
     * the Linux desktop instead. The switch is a safety gate rather than the
     * feature itself — with it off the bound key does nothing — and the binding
     * below records the key's raw scan code, which is the only thing the root
     * helper can compare while Android is no longer in the loop.
     */
    private void buildImmersiveSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        addSectionHeader(root, R.string.section_immersive, dp(24));

        Switch immersiveSwitch = new Switch(this);
        M3.styleSwitch(immersiveSwitch);
        immersiveSwitch.setText(R.string.immersive_switch);
        immersiveSwitch.setPadding(0, 0, 0, 0);
        immersiveSwitch.setChecked(prefs.getBoolean(KEY_IMMERSIVE_ENABLED, false));
        immersiveSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_IMMERSIVE_ENABLED, checked).apply());
        root.addView(immersiveSwitch);

        TextView immersiveHint = new TextView(this);
        immersiveHint.setText(R.string.immersive_hint);
        immersiveHint.setTextSize(12);
        immersiveHint.setTextColor(M3.COLOR_TEXT_MUTED);
        immersiveHint.setPadding(0, dp(4), 0, dp(12));
        root.addView(immersiveHint);

        // Constructing the row appends it to `root`.
        new KeyBinding(root, KEY_IMMERSIVE_KEYCODE, KEY_IMMERSIVE_SCANCODE,
                R.string.bind_immersive_key_button);
    }

    private void addSectionHeader(LinearLayout root, int titleRes, int topPadding) {
        TextView header = new TextView(this);
        header.setText(titleRes);
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, topPadding, 0, dp(8));
        root.addView(header);
    }

    /**
     * One "bind a key" row: a status line plus a button that listens for the next
     * key press for five seconds. Both bindings on this page use it, so the
     * listening state lives per row instead of on the activity.
     */
    private final class KeyBinding {
        private final String keyPref;
        /**
         * Where to store the raw evdev scan code, or null when only the Android
         * key code matters. Immersive mode needs it: {@link KeyCodeMapper} has no
         * entry for the volume keys, and its root helper only ever sees evdev
         * codes.
         */
        private final String scanPref;
        private final int buttonLabelRes;
        private final Button button;
        private final TextView status;
        private CountDownTimer timer;

        KeyBinding(LinearLayout root, String keyPref, String scanPref,
                   int buttonLabelRes) {
            this.keyPref = keyPref;
            this.scanPref = scanPref;
            this.buttonLabelRes = buttonLabelRes;

            status = new TextView(SettingsActivity.this);
            status.setTextSize(14);
            status.setTextColor(M3.COLOR_TEXT_MUTED);
            status.setPadding(0, 0, 0, dp(16));
            root.addView(status);

            button = new Button(SettingsActivity.this);
            styleSettingsButton(button);
            button.setText(buttonLabelRes);
            button.setOnClickListener(v -> startListening());
            root.addView(button);

            updateStatus();
        }

        private void startListening() {
            if (listeningBinding == this)
                return;
            stopListening();
            listeningBinding = this;
            button.setText(getString(R.string.listening_countdown, 5));
            timer = new CountDownTimer(5000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    button.setText(getString(R.string.listening_countdown,
                        (int) (millisUntilFinished / 1000)));
                }

                @Override
                public void onFinish() {
                    // Timed out with no key: clear the binding, matching the
                    // original behaviour of "listen, then store whatever came".
                    bind(UNBOUND, UNBOUND);
                }
            }.start();
        }

        /** Stop listening without changing what is bound. */
        void cancel() {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            button.setText(buttonLabelRes);
        }

        void bind(int keycode, int scancode) {
            cancel();
            listeningBinding = null;
            SharedPreferences.Editor edit =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            edit.putInt(keyPref, keycode);
            if (scanPref != null)
                edit.putInt(scanPref, scancode);
            edit.apply();
            updateStatus();
        }

        void updateStatus() {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int bound = prefs.getInt(keyPref, UNBOUND);
            int scan = scanPref == null ? UNBOUND : prefs.getInt(scanPref, UNBOUND);
            if (bound == UNBOUND && scan <= 0) {
                status.setText(R.string.status_current_none);
                status.setTextColor(M3.COLOR_TEXT_MUTED);
                return;
            }
            String name = KeyCodeMapper.keyName(SettingsActivity.this, bound, scan);
            // A binding that resolves to no evdev code is useless to the root
            // helper, so say so here rather than let the key quietly do nothing.
            if (scanPref != null && resolveEvdev(bound, scan) <= 0) {
                status.setText(getString(R.string.status_current_no_scancode, name));
                status.setTextColor(0xFFC62828);  // red
                return;
            }
            status.setText(getString(R.string.status_current, name));
            status.setTextColor(M3.COLOR_TEXT_MUTED);
        }

        private int resolveEvdev(int keycode, int scancode) {
            return scancode > 0 ? scancode
                    : (keycode == UNBOUND ? -1 : KeyCodeMapper.getScanCode(keycode));
        }
    }

    private void buildAccessibilitySection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Switch accessibilitySwitch = new Switch(this);
        M3.styleSwitch(accessibilitySwitch);
        accessibilitySwitch.setText(R.string.accessibility_switch);
        accessibilitySwitch.setPadding(0, dp(16), 0, 0);
        accessibilitySwitch.setChecked(prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false));
        accessibilitySwitch.setOnCheckedChangeListener((v, checked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACCESSIBILITY_ENABLED, checked).apply();
            if (checked) {
                KeyInterceptor.launch(SettingsActivity.this);
            } else {
                KeyInterceptor.shutdown(false);
            }
        });
        root.addView(accessibilitySwitch);

        TextView accessibilityHint = new TextView(this);
        accessibilityHint.setText(R.string.accessibility_hint);
        accessibilityHint.setTextSize(12);
        accessibilityHint.setTextColor(M3.COLOR_TEXT_MUTED);
        accessibilityHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(accessibilityHint);
    }

    private void buildExtraKeysSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        TextView header = new TextView(this);
        header.setText(R.string.section_extra_keys);
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header);

        // === Back key opens extra keys bar (Legacy only) ===
        Switch backOpensExtraKeysSwitch = new Switch(this);
        M3.styleSwitch(backOpensExtraKeysSwitch);
        backOpensExtraKeysSwitch.setText(R.string.back_opens_switch);
        backOpensExtraKeysSwitch.setPadding(0, dp(16), 0, 0);
        backOpensExtraKeysSwitch.setChecked(prefs.getBoolean(KEY_BACK_OPENS_EXTRA_KEYS, true));
        backOpensExtraKeysSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_BACK_OPENS_EXTRA_KEYS, checked).apply());
        root.addView(backOpensExtraKeysSwitch);

        TextView backOpensExtraKeysHint = new TextView(this);
        backOpensExtraKeysHint.setText(R.string.back_opens_hint);
        backOpensExtraKeysHint.setTextSize(12);
        backOpensExtraKeysHint.setTextColor(M3.COLOR_TEXT_MUTED);
        backOpensExtraKeysHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(backOpensExtraKeysHint);

        // === Extra keys bar mode selector (Shared) ===
        TextView modeLabel = new TextView(this);
        modeLabel.setText(R.string.extra_keys_mode_label);
        modeLabel.setTextSize(14);
        modeLabel.setTextColor(0xFFA6ADC8);
        modeLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(modeLabel);

        LinearLayout spCard = new LinearLayout(this);
        spCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable spBg = new android.graphics.drawable.GradientDrawable();
        spBg.setCornerRadius(dp(8));
        spBg.setColor(0xFF181825);
        spBg.setStroke(dp(1), 0x22FFFFFF);
        spCard.setBackground(spBg);
        spCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner modeSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        setupDarkSpinner(modeSpinner, getResources().getStringArray(R.array.extra_keys_mode_options));

        String curMode = getExtraKeysMode(prefs);
        int modeIdx = 0; // default: always
        for (int i = 0; i < EXTRA_KEYS_MODES.length; i++) {
            if (EXTRA_KEYS_MODES[i].equals(curMode)) { modeIdx = i; break; }
        }
        modeSpinner.setSelection(modeIdx);
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_EXTRA_KEYS_MODE, EXTRA_KEYS_MODES[pos]).apply();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        spCard.addView(modeSpinner);
        root.addView(spCard);

        TextView modeHint = new TextView(this);
        modeHint.setText(R.string.extra_keys_mode_hint);
        modeHint.setTextSize(12);
        modeHint.setTextColor(M3.COLOR_TEXT_MUTED);
        modeHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(modeHint);
    }

    private void buildCustomLayoutSection(LinearLayout root) {
        // Always show the ExtraKeys JSON editor, regardless of whether the
        // HUD editor is enabled. The two features are peers; hiding the
        // ExtraKeys editor just because HUD is on prevented the user from
        // re-binding the bottom key row, which is the bug that surfaced
        // as "bảng JSON chỉnh sửa ExtraKeys biến mất khi bật HUD".
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        TextView layoutHeader = new TextView(this);
        layoutHeader.setText(R.string.section_custom_layout);
        layoutHeader.setTextSize(16);
        layoutHeader.setTypeface(null, Typeface.BOLD);
        layoutHeader.setPadding(0, dp(24), 0, dp(8));
        root.addView(layoutHeader);

        layoutInput = new EditText(this);
        layoutInput.setTypeface(Typeface.MONOSPACE);
        layoutInput.setTextSize(12);
        layoutInput.setTextColor(M3.COLOR_TEXT_PRIMARY);
        layoutInput.setGravity(Gravity.TOP | Gravity.START);
        layoutInput.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        layoutInput.setHorizontallyScrolling(false);
        layoutInput.setMinLines(6);
        layoutInput.setBackground(M3.shape(this, M3.RADIUS_CARD, M3.COLOR_SURFACE_LOW, M3.COLOR_BORDER_SUBTLE, 1.0f));
        layoutInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams layoutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutLp.setMargins(0, dp(4), 0, dp(8));
        layoutInput.setLayoutParams(layoutLp);
        String savedLayout = prefs.getString(KEY_EXTRA_KEYS_LAYOUT, "");
        if (savedLayout.isEmpty()) savedLayout = ExtraKeysBar.defaultLayoutJson();
        layoutInput.setText(savedLayout);
        root.addView(layoutInput);

        final TextView layoutStatus = new TextView(this);
        layoutStatus.setTextSize(12);
        layoutStatus.setPadding(0, dp(4), 0, dp(4));
        root.addView(layoutStatus);
        updateLayoutStatus(layoutStatus, layoutInput.getText().toString());

        layoutInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_EXTRA_KEYS_LAYOUT, s.toString()).apply();
                updateLayoutStatus(layoutStatus, s.toString());
            }
        });

        LinearLayout layoutButtons = new LinearLayout(this);
        layoutButtons.setOrientation(LinearLayout.HORIZONTAL);
        layoutButtons.setPadding(0, dp(4), 0, dp(4));

        Button loadDefaultBtn = new Button(this);
        styleSettingsButton(loadDefaultBtn);
        loadDefaultBtn.setText("Load Default");
        loadDefaultBtn.setOnClickListener(v ->
            layoutInput.setText(ExtraKeysBar.defaultLayoutJson()));
        LinearLayout.LayoutParams defLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        loadDefaultBtn.setLayoutParams(defLp);
        layoutButtons.addView(loadDefaultBtn);

        Button loadFileBtn = new Button(this);
        styleSettingsButton(loadFileBtn);
        loadFileBtn.setText("Load File...");
        loadFileBtn.setOnClickListener(v -> pickLayoutFile());
        LinearLayout.LayoutParams fileLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fileLp.leftMargin = dp(10);
        loadFileBtn.setLayoutParams(fileLp);
        layoutButtons.addView(loadFileBtn);

        root.addView(layoutButtons);

        TextView layoutHint = new TextView(this);
        layoutHint.setText(R.string.layout_hint);
        layoutHint.setTextSize(12);
        layoutHint.setTextColor(M3.COLOR_TEXT_MUTED);
        layoutHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(layoutHint);
    }

    // ============================================================
    // General page sections
    // ============================================================
    private void buildOrientationSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        TextView header = new TextView(this);
        header.setText(R.string.section_orientation);
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(8));
        root.addView(header);

        TextView label = new TextView(this);
        label.setText(R.string.orientation_label);
        label.setTextSize(14);
        label.setTextColor(0xFFA6ADC8);
        label.setPadding(0, dp(4), 0, dp(8));
        root.addView(label);

        LinearLayout spinnerCard = new LinearLayout(this);
        spinnerCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable spBg = new android.graphics.drawable.GradientDrawable();
        spBg.setCornerRadius(dp(8));
        spBg.setColor(0xFF181825);
        spBg.setStroke(dp(1), 0x22FFFFFF);
        spinnerCard.setBackground(spBg);
        spinnerCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        setupDarkSpinner(spinner, getResources().getStringArray(R.array.orientation_options));

        String cur = prefs.getString(KEY_ORIENTATION, "default");
        int idx = 0;
        for (int i = 0; i < ORIENTATION_VALUES.length; i++) {
            if (ORIENTATION_VALUES[i].equals(cur)) { idx = i; break; }
        }
        spinner.setSelection(idx);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_ORIENTATION, ORIENTATION_VALUES[pos]).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        spinnerCard.addView(spinner);
        root.addView(spinnerCard);
    }

    private void buildHapticSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        TextView header = new TextView(this);
        header.setText("Haptic Feedback");
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header);

        Switch hapticSwitch = new Switch(this);
        M3.styleSwitch(hapticSwitch);
        hapticSwitch.setText("Enable Touch Vibration");
        hapticSwitch.setPadding(0, dp(8), 0, 0);
        hapticSwitch.setChecked(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true));
        hapticSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_HAPTIC_FEEDBACK, checked).apply());
        root.addView(hapticSwitch);

        TextView hapticHint = new TextView(this);
        hapticHint.setText("Vibrate on on-screen button taps, gesture swipes, and trackpoint interactions.");
        hapticHint.setTextSize(12);
        hapticHint.setTextColor(0xFFA6ADC8);
        hapticHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(hapticHint);
    }

    private void buildNotificationSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        TextView header = new TextView(this);
        header.setText(R.string.section_notification);
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(8));
        root.addView(header);

        Switch notificationSwitch = new Switch(this);
        M3.styleSwitch(notificationSwitch);
        notificationSwitch.setText(R.string.notification_switch);
        notificationSwitch.setPadding(0, dp(8), 0, 0);
        notificationSwitch.setChecked(prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true));
        notificationSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_NOTIFICATION_ENABLED, checked).apply());
        root.addView(notificationSwitch);

        TextView notificationHint = new TextView(this);
        notificationHint.setText(R.string.notification_hint);
        notificationHint.setTextSize(12);
        notificationHint.setTextColor(M3.COLOR_TEXT_MUTED);
        notificationHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(notificationHint);
    }

    private void buildBackupRestoreSection(LinearLayout root) {
        TextView header = new TextView(this);
        header.setText("Backup & Restore Settings");
        header.setTextSize(16);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText("Export all settings and HUD layouts to a JSON backup file, or restore from a previously saved file.");
        hint.setTextSize(12);
        hint.setTextColor(0xFFA6ADC8);
        hint.setPadding(0, 0, 0, dp(12));
        root.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button btnBackup = new Button(this);
        styleSettingsButton(btnBackup);
        btnBackup.setText("Backup Settings");
        btnBackup.setOnClickListener(v -> backupSettings());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnBackup.setLayoutParams(bLp);
        row.addView(btnBackup);

        Button btnRestore = new Button(this);
        styleSettingsButton(btnRestore);
        btnRestore.setText("Restore Settings");
        btnRestore.setOnClickListener(v -> restoreSettings());
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rLp.leftMargin = dp(10);
        btnRestore.setLayoutParams(rLp);
        row.addView(btnRestore);

        root.addView(row);
    }

    // ============================================================
    // ===== 触摸板设置区域 =====
    // ============================================================
    private void buildTouchpadSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 触摸板模式开关
        Switch touchpadModeSwitch = new Switch(this);
        M3.styleSwitch(touchpadModeSwitch);
        touchpadModeSwitch.setText(R.string.touchpad_mode_switch);
        touchpadModeSwitch.setPadding(0, dp(8), 0, 0);
        touchpadModeSwitch.setChecked(prefs.getBoolean(KEY_TOUCHPAD_MODE, false));
        touchpadModeSwitch.setOnCheckedChangeListener((v, checked) ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putBoolean(KEY_TOUCHPAD_MODE, checked).apply());
        root.addView(touchpadModeSwitch);

        TextView touchpadHint = new TextView(this);
        touchpadHint.setText(R.string.touchpad_hint);
        touchpadHint.setTextSize(12);
        touchpadHint.setTextColor(M3.COLOR_TEXT_MUTED);
        touchpadHint.setPadding(0, dp(4), 0, dp(12));
        root.addView(touchpadHint);

        // External mouse pointer capture.  This is opt-in because it changes
        // Android's mouse event mode from absolute coordinates to relative motion.
        Switch pointerCaptureSwitch = new Switch(this);
        M3.styleSwitch(pointerCaptureSwitch);
        pointerCaptureSwitch.setText(R.string.pointer_capture_switch);
        pointerCaptureSwitch.setPadding(0, dp(8), 0, 0);
        pointerCaptureSwitch.setChecked(prefs.getBoolean(KEY_POINTER_CAPTURE, false));
        pointerCaptureSwitch.setOnCheckedChangeListener((v, checked) ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putBoolean(KEY_POINTER_CAPTURE, checked).apply());
        root.addView(pointerCaptureSwitch);

        TextView pointerCaptureHint = new TextView(this);
        pointerCaptureHint.setText(R.string.pointer_capture_hint);
        pointerCaptureHint.setTextSize(12);
        pointerCaptureHint.setTextColor(M3.COLOR_TEXT_MUTED);
        pointerCaptureHint.setPadding(0, dp(4), 0, dp(12));
        root.addView(pointerCaptureHint);

        // 鼠标加速度（灵敏度）—— 范围 0.5 ~ 10.0
        LinearLayout accelLayout = new LinearLayout(this);
        accelLayout.setOrientation(LinearLayout.VERTICAL);
        accelLayout.setPadding(0, dp(8), 0, dp(16));

        TextView accelLabel = new TextView(this);
        accelLabel.setText(R.string.mouse_sensitivity_label);
        accelLabel.setTextSize(14);
        accelLabel.setTextColor(Color.WHITE);
        accelLayout.addView(accelLabel);

        final TextView accelValue = new TextView(this);
        accelValue.setTextSize(14);
        accelValue.setTextColor(0xFFB4BEFE);
        accelValue.setPadding(0, dp(2), 0, dp(4));
        accelLayout.addView(accelValue);

        SeekBar accelSeek = new SeekBar(this);
        styleSeekBar(accelSeek, 0xFFB4BEFE);
        accelSeek.setMax(190); // 0.5 ~ 10.0 step 0.05
        float curAccel = MainActivity.getSafeFloat(prefs, KEY_MOUSE_ACCEL, 1.0f);
        curAccel = Math.max(0.5f, Math.min(10.0f, curAccel));
        accelSeek.setProgress((int)((curAccel - 0.5f) / 0.05f));
        accelValue.setText(getString(R.string.mouse_accel_value, curAccel));
        accelSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + progress * 0.05f;
                accelValue.setText(getString(R.string.mouse_accel_value, val));
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putFloat(KEY_MOUSE_ACCEL, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        accelLayout.addView(accelSeek);
        root.addView(accelLayout);

        // ===== 双指滚动 =====
        Switch reverseScrollSwitch = new Switch(this);
        M3.styleSwitch(reverseScrollSwitch);
        reverseScrollSwitch.setText(R.string.scroll_reverse_switch);
        reverseScrollSwitch.setPadding(0, dp(8), 0, 0);
        reverseScrollSwitch.setChecked(prefs.getBoolean(KEY_SCROLL_REVERSE, false));
        reverseScrollSwitch.setOnCheckedChangeListener((v, checked) ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putBoolean(KEY_SCROLL_REVERSE, checked).apply());
        root.addView(reverseScrollSwitch);

        TextView reverseScrollHint = new TextView(this);
        reverseScrollHint.setText(R.string.scroll_reverse_hint);
        reverseScrollHint.setTextSize(12);
        reverseScrollHint.setTextColor(M3.COLOR_TEXT_MUTED);
        reverseScrollHint.setPadding(0, dp(4), 0, dp(12));
        root.addView(reverseScrollHint);

        // Disable pinch (two-finger spread) and three-or-more-finger gestures.
        Switch disableMultiFingerSwitch = new Switch(this);
        M3.styleSwitch(disableMultiFingerSwitch);
        disableMultiFingerSwitch.setText(R.string.disable_multi_finger_switch);
        disableMultiFingerSwitch.setPadding(0, dp(8), 0, 0);
        disableMultiFingerSwitch.setChecked(prefs.getBoolean(
                KEY_DISABLE_MULTI_FINGER_GESTURES, false));
        disableMultiFingerSwitch.setOnCheckedChangeListener((v, checked) ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putBoolean(KEY_DISABLE_MULTI_FINGER_GESTURES, checked).apply());
        root.addView(disableMultiFingerSwitch);

        TextView disableMultiFingerHint = new TextView(this);
        disableMultiFingerHint.setText(R.string.disable_multi_finger_hint);
        disableMultiFingerHint.setTextSize(12);
        disableMultiFingerHint.setTextColor(M3.COLOR_TEXT_MUTED);
        disableMultiFingerHint.setPadding(0, dp(4), 0, dp(12));
        root.addView(disableMultiFingerHint);

        addFloatSlider(root, R.string.scroll_speed_label, R.string.scroll_speed_value,
                null, KEY_SCROLL_SPEED, 0.05f, 3.0f, 0.05f, 0.5f);
        addFloatSlider(root, R.string.scroll_threshold_label,
                R.string.threshold_factor_value, R.string.scroll_threshold_hint,
                KEY_SCROLL_THRESHOLD, 0.05f, 3.0f, 0.05f, 0.5f);
        addFloatSlider(root, R.string.move_threshold_label,
                R.string.threshold_factor_value, R.string.move_threshold_hint,
                KEY_MOVE_THRESHOLD, 0.1f, 8.0f, 0.05f, 2.35f);
        addFloatSlider(root, R.string.gesture_scale_label, R.string.gesture_scale_value,
                R.string.gesture_scale_hint,
                KEY_GESTURE_SCALE, 100f, 3000f, 20f, 800f);
    }

    /**
     * A labelled slider over a float preference, with the live value beside the label
     * and an optional grey hint underneath.
     */
    private void addFloatSlider(LinearLayout root, int labelRes, int valueFormatRes,
                                Integer hintRes, final String key,
                                final float min, float max, final float step,
                                float defValue) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, dp(8), 0, hintRes == null ? dp(16) : 0);

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(14);
        label.setTextColor(Color.WHITE);
        layout.addView(label);

        final TextView value = new TextView(this);
        value.setTextSize(14);
        value.setTextColor(0xFFB4BEFE);
        value.setPadding(0, dp(2), 0, dp(4));
        layout.addView(value);

        SeekBar seek = new SeekBar(this);
        styleSeekBar(seek, 0xFFB4BEFE);
        seek.setMax(Math.round((max - min) / step));
        float cur = Math.max(min, Math.min(max, MainActivity.getSafeFloat(prefs, key, defValue)));
        seek.setProgress(Math.round((cur - min) / step));
        value.setText(getString(valueFormatRes, cur));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = min + progress * step;
                value.setText(getString(valueFormatRes, val));
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putFloat(key, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(seek);
        root.addView(layout);

        if (hintRes != null) {
            TextView hint = new TextView(this);
            hint.setText(hintRes);
            hint.setTextSize(12);
            hint.setTextColor(M3.COLOR_TEXT_MUTED);
            hint.setPadding(0, dp(2), 0, dp(12));
            root.addView(hint);
        }
    }

    // Connection settings: a custom daemon socket path and a "connect with root"
    // toggle. In root mode the app launches the bundled helper via `su -c`, which
    // connects to the socket and passes the fd back (see MainActivity).
    private void addConnectionSection(LinearLayout root) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Socket path
        TextView sockLabel = new TextView(this);
        sockLabel.setText(R.string.socket_path_label);
        sockLabel.setTextSize(14);
        sockLabel.setTextColor(M3.COLOR_TEXT_MUTED);
        sockLabel.setPadding(0, 0, 0, dp(4));
        root.addView(sockLabel);

        EditText socketInput = new EditText(this);
        M3.styleInput(socketInput);
        socketInput.setSingleLine(true);
        socketInput.setText(prefs.getString(KEY_SOCKET_PATH, DEFAULT_SOCKET_PATH));
        socketInput.setHint(DEFAULT_SOCKET_PATH);
        socketInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(KEY_SOCKET_PATH, s.toString().trim()).apply();
            }
        });
        root.addView(socketInput);

        // Open a second window: an independent pipeline in the same process, targeting
        // its own daemon socket and shown with its own title. Launched as a new task
        // (freeform / split-screen) via SecondaryActivity.
        TextView secLabel = new TextView(this);
        secLabel.setText(R.string.second_window_label);
        secLabel.setTextSize(14);
        secLabel.setTextColor(M3.COLOR_TEXT_MUTED);
        secLabel.setPadding(0, dp(16), 0, dp(4));
        root.addView(secLabel);

        EditText secName = new EditText(this);
        M3.styleInput(secName);
        secName.setSingleLine(true);
        secName.setHint(R.string.second_window_name_hint);
        root.addView(secName);

        EditText secSocket = new EditText(this);
        M3.styleInput(secSocket);
        secSocket.setSingleLine(true);
        secSocket.setHint(DEFAULT_SOCKET_PATH);
        LinearLayout.LayoutParams secSockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secSockLp.setMargins(0, dp(8), 0, dp(8));
        secSocket.setLayoutParams(secSockLp);
        root.addView(secSocket);

        Button secOpen = new Button(this);
        styleSettingsButton(secOpen);
        secOpen.setText(R.string.second_window_open);
        secOpen.setOnClickListener(v -> {
            Intent i = new Intent(this, SecondaryActivity.class);
            String sp = secSocket.getText().toString().trim();
            String wn = secName.getText().toString().trim();
            if (!sp.isEmpty()) i.putExtra(MainActivity.EXTRA_SOCKET_PATH, sp);
            if (!wn.isEmpty()) i.putExtra(MainActivity.EXTRA_WINDOW_NAME, wn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(i);
        });
        root.addView(secOpen);

        // Connect with root
        Switch rootSwitch = new Switch(this);
        M3.styleSwitch(rootSwitch);
        rootSwitch.setText(R.string.root_switch);
        rootSwitch.setPadding(0, dp(16), 0, 0);
        rootSwitch.setChecked(prefs.getBoolean(KEY_USE_ROOT, true));
        rootSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_USE_ROOT, checked).apply());
        root.addView(rootSwitch);

        TextView rootHint = new TextView(this);
        rootHint.setText(R.string.root_hint);
        rootHint.setTextSize(12);
        rootHint.setTextColor(M3.COLOR_TEXT_MUTED);
        rootHint.setPadding(0, dp(4), 0, 0);
        root.addView(rootHint);

        // Forward microphone: capture the device mic and expose it to the Linux
        // desktop as a recording source. Requires the RECORD_AUDIO permission, which
        // MainActivity requests when this is on.
        Switch micSwitch = new Switch(this);
        M3.styleSwitch(micSwitch);
        micSwitch.setText(R.string.mic_switch);
        micSwitch.setPadding(0, dp(16), 0, 0);
        micSwitch.setChecked(prefs.getBoolean(KEY_MIC_ENABLED, false));
        micSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_MIC_ENABLED, checked).apply());
        root.addView(micSwitch);

        TextView micHint = new TextView(this);
        micHint.setText(R.string.mic_hint);
        micHint.setTextSize(12);
        micHint.setTextColor(M3.COLOR_TEXT_MUTED);
        micHint.setPadding(0, dp(4), 0, 0);
        root.addView(micHint);

        // Forward camera: expose the device camera(s) to the Linux desktop. When on,
        // the app pre-creates the camera service resources at startup (CameraX is only
        // opened once the desktop actually requests a recording). Requires the CAMERA
        // permission, which MainActivity requests when this is enabled.
        Switch cameraSwitch = new Switch(this);
        M3.styleSwitch(cameraSwitch);
        cameraSwitch.setText(R.string.camera_switch);
        cameraSwitch.setPadding(0, dp(16), 0, 0);
        cameraSwitch.setChecked(prefs.getBoolean(KEY_CAMERA_ENABLED, false));
        cameraSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_CAMERA_ENABLED, checked).apply());
        root.addView(cameraSwitch);

        TextView cameraHint = new TextView(this);
        cameraHint.setText(R.string.camera_hint);
        cameraHint.setTextSize(12);
        cameraHint.setTextColor(M3.COLOR_TEXT_MUTED);
        cameraHint.setPadding(0, dp(4), 0, 0);
        root.addView(cameraHint);

        // Audio keep-alive: keep the AAudio output stream running (fed near-silent
        // keepalive) so short Linux UI sounds (volume ticks, key clicks) always play
        // immediately. Off by default so the audio path can sleep when the desktop is
        // silent and save standby power.
        Switch keepaliveSwitch = new Switch(this);
        M3.styleSwitch(keepaliveSwitch);
        keepaliveSwitch.setText(R.string.audio_keepalive_switch);
        keepaliveSwitch.setPadding(0, dp(16), 0, 0);
        keepaliveSwitch.setChecked(prefs.getBoolean(KEY_AUDIO_KEEPALIVE, false));
        keepaliveSwitch.setOnCheckedChangeListener((v, checked) ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_AUDIO_KEEPALIVE, checked).apply());
        root.addView(keepaliveSwitch);

        TextView keepaliveHint = new TextView(this);
        keepaliveHint.setText(R.string.audio_keepalive_hint);
        keepaliveHint.setTextSize(12);
        keepaliveHint.setTextColor(M3.COLOR_TEXT_MUTED);
        keepaliveHint.setPadding(0, dp(4), 0, 0);
        root.addView(keepaliveHint);

        // Audio latency presets, separately for the speaker (playback) and microphone
        // (capture) paths. The chosen buffer is forwarded to the producer's PipeWire
        // nodes; smaller = lower latency but more risk of audio glitches.
        TextView latTitle = new TextView(this);
        latTitle.setText(R.string.audio_latency_title);
        latTitle.setTextSize(15);
        latTitle.setTypeface(Typeface.DEFAULT_BOLD);
        latTitle.setPadding(0, dp(20), 0, 0);
        root.addView(latTitle);

        root.addView(makeLatencySpinner(getString(R.string.latency_speaker_label),
                                        KEY_SPEAKER_LATENCY_MS, prefs));
        root.addView(makeLatencySpinner(getString(R.string.latency_mic_label),
                                        KEY_MIC_LATENCY_MS, prefs));

        TextView latHint = new TextView(this);
        latHint.setText(R.string.latency_hint);
        latHint.setTextSize(12);
        latHint.setTextColor(M3.COLOR_TEXT_MUTED);
        latHint.setPadding(0, dp(4), 0, 0);
        root.addView(latHint);
    }

    private void addResolutionSection(LinearLayout root) {
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

    // Width / height fields. Created first (but added below the preset picker) so
    // the picker can populate them; their TextWatchers are the single source of
    // truth that persists custom_width/custom_height.
    final EditText widthInput = new EditText(this);
    widthInput.setSingleLine(true);
    widthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    widthInput.setHint(R.string.width_hint);
    widthInput.setText(String.valueOf(prefs.getInt("custom_width", 0)));
    widthInput.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable s) {
            try {
                int w = Integer.parseInt(s.toString().trim());
                prefs.edit().putInt("custom_width", w).apply();
            } catch (NumberFormatException e) {}
        }
    });

    final EditText heightInput = new EditText(this);
    heightInput.setSingleLine(true);
    heightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    heightInput.setHint(R.string.height_hint);
    heightInput.setText(String.valueOf(prefs.getInt("custom_height", 0)));
    heightInput.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable s) {
            try {
                int h = Integer.parseInt(s.toString().trim());
                prefs.edit().putInt("custom_height", h).apply();
            } catch (NumberFormatException e) {}
        }
    });

    // Preset picker: fills width/height (which persist via their watchers). Index
    // 0 is a no-op placeholder so the Spinner's initial auto-selection and manual
    // edits leave the fields untouched.
    LinearLayout spinnerCard = new LinearLayout(this);
    spinnerCard.setOrientation(LinearLayout.VERTICAL);
    android.graphics.drawable.GradientDrawable spBg = new android.graphics.drawable.GradientDrawable();
    spBg.setCornerRadius(dp(8));
    spBg.setColor(0xFF181825);
    spBg.setStroke(dp(1), 0x22FFFFFF);
    spinnerCard.setBackground(spBg);
    spinnerCard.setPadding(dp(12), dp(4), dp(12), dp(4));

    Spinner presetSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
    setupDarkSpinner(presetSpinner, getResources().getStringArray(R.array.res_preset_labels));
    presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
            int[] wh = resolvePreset(pos);
            if (wh == null) return;
            widthInput.setText(String.valueOf(wh[0]));
            heightInput.setText(String.valueOf(wh[1]));
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    });
    spinnerCard.addView(presetSpinner);
    root.addView(spinnerCard);

    LinearLayout inputsRow = new LinearLayout(this);
    inputsRow.setOrientation(LinearLayout.HORIZONTAL);
    inputsRow.setPadding(0, dp(10), 0, dp(4));

    M3.styleInput(widthInput);
    LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    widthInput.setLayoutParams(wLp);
    inputsRow.addView(widthInput);

    M3.styleInput(heightInput);
    LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    hLp.leftMargin = dp(10);
    heightInput.setLayoutParams(hLp);
    inputsRow.addView(heightInput);

    root.addView(inputsRow);

    TextView hint = new TextView(this);
    hint.setText(R.string.resolution_hint);
    hint.setTextSize(12);
    hint.setTextColor(M3.COLOR_TEXT_MUTED);
    hint.setPadding(0, dp(4), 0, 0);
    root.addView(hint);

    Switch autoStretchSwitch = new Switch(this);
    M3.styleSwitch(autoStretchSwitch);
    autoStretchSwitch.setText(R.string.auto_stretch_switch);
    autoStretchSwitch.setPadding(0, dp(16), 0, 0);
    autoStretchSwitch.setChecked(prefs.getBoolean("auto_stretch", true));
    autoStretchSwitch.setOnCheckedChangeListener((v, checked) ->
        prefs.edit().putBoolean("auto_stretch", checked).apply());
    root.addView(autoStretchSwitch);

    TextView autoStretchHint = new TextView(this);
    autoStretchHint.setText(R.string.auto_stretch_hint);
    autoStretchHint.setTextSize(12);
    autoStretchHint.setTextColor(M3.COLOR_TEXT_MUTED);
    autoStretchHint.setPadding(0, dp(4), 0, 0);
    root.addView(autoStretchHint);

    TextView ratioLabel = new TextView(this);
    ratioLabel.setText("Landscape Display Aspect Ratio");
    ratioLabel.setTextSize(14);
    ratioLabel.setTextColor(Color.WHITE);
    ratioLabel.setPadding(0, dp(16), 0, dp(4));
    root.addView(ratioLabel);

    LinearLayout ratioCard = new LinearLayout(this);
    ratioCard.setOrientation(LinearLayout.VERTICAL);
    android.graphics.drawable.GradientDrawable rBg = new android.graphics.drawable.GradientDrawable();
    rBg.setCornerRadius(dp(8));
    rBg.setColor(0xFF181825);
    rBg.setStroke(dp(1), 0x22FFFFFF);
    ratioCard.setBackground(rBg);
    ratioCard.setPadding(dp(12), dp(4), dp(12), dp(4));

    Spinner ratioSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
    String[] ratioOptions = {
            "Auto Stretch (Fullscreen)",
            "Fit Between Keyboards (Nằm gọn giữa hai phím)",
            "16:9 Letterbox (1920x1080)",
            "14:9 Letterbox (1680x1080)",
            "4:3 Letterbox (1440x1080)"
    };
    setupDarkSpinner(ratioSpinner, ratioOptions);
    String currentRatio = prefs.getString("landscape_aspect_ratio", null);
    int sel = 0;
    if ("fit_between".equals(currentRatio)) sel = 1;
    else if ("16_9".equals(currentRatio)) sel = 2;
    else if ("14_9".equals(currentRatio)) sel = 3;
    else if ("4_3".equals(currentRatio)) sel = 4;
    else if (prefs.getBoolean("landscape_16_9_letterbox", false)) sel = 2;

    ratioSpinner.setSelection(sel);
    ratioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
            String val = "auto";
            if (pos == 1) val = "fit_between";
            else if (pos == 2) val = "16_9";
            else if (pos == 3) val = "14_9";
            else if (pos == 4) val = "4_3";
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString("landscape_aspect_ratio", val)
                    .putBoolean("landscape_16_9_letterbox", pos != 0)
                    .apply();
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    });
    ratioCard.addView(ratioSpinner);
    root.addView(ratioCard);

    TextView ratioHint = new TextView(this);
    ratioHint.setText("Locks desktop center stage with side margins (gutters) for Switch controller HUD buttons and Split keyboard.");
    ratioHint.setTextSize(12);
    ratioHint.setTextColor(M3.COLOR_TEXT_MUTED);
    ratioHint.setPadding(0, dp(4), 0, 0);
    root.addView(ratioHint);
    }

    // Maps a res_preset_labels index to {width, height}, or null for the index-0
    // placeholder. "Screen ×" presets are derived from the live panel size.
    private int[] resolvePreset(int pos) {
        switch (pos) {
            case 1: return new int[]{0, 0};
            case 2: return new int[]{3840, 2160};
            case 3: return new int[]{2560, 1440};
            case 4: return new int[]{1920, 1080};
            case 5: return new int[]{1280, 720};
            case 6: return new int[]{854, 480};
            case 7: return scaleScreen(1.0f);
            case 8: return scaleScreen(0.8f);
            case 9: return scaleScreen(0.75f);
            case 10: return scaleScreen(0.5f);
            case 11: return scaleScreen(0.25f);
            default: return null;
        }
    }

    // Scales the device panel by `f`, normalised to landscape (long side = width)
    // and rounded down to even dimensions, which compositors/encoders expect.
    private int[] scaleScreen(float f) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Rect b = wm.getMaximumWindowMetrics().getBounds();
        int longSide = Math.max(b.width(), b.height());
        int shortSide = Math.min(b.width(), b.height());
        int w = Math.round(longSide * f) & ~1;
        int h = Math.round(shortSide * f) & ~1;
        return new int[]{w, h};
    }

    /* A labelled latency picker that persists the selected preset (ms) under `key`. */
    private View makeLatencySpinner(String label, final String key, SharedPreferences prefs) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(12), 0, 0);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(0xFFA6ADC8);
        tv.setPadding(0, 0, 0, dp(6));
        box.addView(tv);

        LinearLayout spCard = new LinearLayout(this);
        spCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable spBg = new android.graphics.drawable.GradientDrawable();
        spBg.setCornerRadius(dp(8));
        spBg.setColor(0xFF181825);
        spBg.setStroke(dp(1), 0x22FFFFFF);
        spCard.setBackground(spBg);
        spCard.setPadding(dp(12), dp(4), dp(12), dp(4));

        Spinner sp = new Spinner(this, Spinner.MODE_DROPDOWN);
        setupDarkSpinner(sp, getResources().getStringArray(R.array.latency_labels));

        int cur = prefs.getInt(key, 0);
        int idx = 0;
        for (int i = 0; i < LATENCY_MS.length; i++) {
            if (LATENCY_MS[i] == cur) { idx = i; break; }
        }
        sp.setSelection(idx);

        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putInt(key, LATENCY_MS[pos]).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        spCard.addView(sp);
        box.addView(spCard);
        return box;
    }

    /** Stop whichever row is counting down, leaving its binding untouched. */
    private void stopListening() {
        if (listeningBinding != null) {
            listeningBinding.cancel();
            listeningBinding = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (listeningBinding == null) return super.onKeyDown(keyCode, event);

        // Ignore generic Virtual Keyboard keycode (it's a placeholder)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return true;

        // The scan code is recorded alongside the key code: it is what the
        // immersive-mode root helper matches on, and it is the only identity a
        // key like Volume Up has once Android is out of the picture.
        listeningBinding.bind(keyCode, event.getScanCode());
        Log.i(TAG, "Bound keycode: " + keyCode + " scancode: " + event.getScanCode());
        return true;
    }

    // Launch the system document picker to load a layout JSON from any provider
    // (Downloads, Drive, etc.). Uses SAF, so no storage permission is required.
    private void pickLayoutFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/json", "text/plain"});
        try {
            startActivityForResult(intent, REQ_PICK_LAYOUT);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    private void backupSettings() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "anland_settings_backup.json");
        try {
            startActivityForResult(intent, REQ_BACKUP_SETTINGS);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreSettings() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"application/json", "text/plain"});
        try {
            startActivityForResult(intent, REQ_RESTORE_SETTINGS);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQ_PICK_LAYOUT) {
            String text = readTextFromUri(uri);
            if (text == null) {
                Toast.makeText(this, R.string.toast_read_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            if (layoutInput != null) layoutInput.setText(text);
        } else if (requestCode == REQ_BACKUP_SETTINGS) {
            exportSettingsToUri(uri);
        } else if (requestCode == REQ_RESTORE_SETTINGS) {
            importSettingsFromUri(uri);
        }
    }

    private void exportSettingsToUri(Uri uri) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                obj.put(entry.getKey(), entry.getValue());
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Settings Backed Up", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to backup settings", e);
            Toast.makeText(this, "Backup Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void importSettingsFromUri(Uri uri) {
        String json = readTextFromUri(uri);
        if (json == null || json.trim().isEmpty()) {
            Toast.makeText(this, "Restore Failed: Empty File", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject obj = new JSONObject(json);
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object v = obj.get(k);
                if (v instanceof Boolean) {
                    editor.putBoolean(k, (Boolean) v);
                } else if (isFloatPrefKey(k) && v instanceof Number) {
                    editor.putFloat(k, ((Number) v).floatValue());
                } else if (v instanceof Integer) {
                    editor.putInt(k, (Integer) v);
                } else if (v instanceof Long) {
                    editor.putLong(k, (Long) v);
                } else if (v instanceof Float || v instanceof Double) {
                    editor.putFloat(k, (float) obj.getDouble(k));
                } else if (v instanceof String) {
                    editor.putString(k, (String) v);
                }
            }
            editor.apply();
            Toast.makeText(this, "Settings Restored Successfully", Toast.LENGTH_SHORT).show();
            showGeneralPage();
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore settings", e);
            Toast.makeText(this, "Restore Failed: Invalid JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private String readTextFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (in == null) return null;
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "readTextFromUri failed", e);
            return null;
        }
    }

    // Reflect the validity of the custom layout JSON inline under the editor.
    private void updateLayoutStatus(TextView status, String json) {
        if (json == null || json.trim().isEmpty()) {
            status.setText(R.string.layout_status_default);
            status.setTextColor(M3.COLOR_TEXT_MUTED);
            return;
        }
        String err = ExtraKeysBar.validateLayout(json);
        if (err == null) {
            status.setText(R.string.layout_status_valid);
            status.setTextColor(0xFF2E7D32);  // green
        } else {
            status.setText(getString(R.string.layout_status_invalid, err));
            status.setTextColor(0xFFC62828);  // red
        }
    }

    public static boolean isFloatPrefKey(String k) {
        return KEY_MOUSE_ACCEL.equals(k)
                || KEY_SCROLL_SPEED.equals(k)
                || KEY_SCROLL_THRESHOLD.equals(k)
                || KEY_MOVE_THRESHOLD.equals(k)
                || KEY_GESTURE_SCALE.equals(k);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // Read the extra-keys mode, migrating from the old two-switch prefs if needed.
    private String getExtraKeysMode(SharedPreferences prefs) {
        String mode = prefs.getString(KEY_EXTRA_KEYS_MODE, null);
        if (mode != null) return mode;
        // Migrate from legacy boolean keys
        boolean autoShow = prefs.getBoolean("auto_show_extra_keys", true);
        boolean enabled = prefs.getBoolean("extra_keys_bar", false);
        mode = autoShow ? MODE_WITH_KEYBOARD : (enabled ? MODE_ALWAYS : MODE_NEVER);
        prefs.edit().putString(KEY_EXTRA_KEYS_MODE, mode)
              .remove("auto_show_extra_keys").remove("extra_keys_bar").apply();
        return mode;
    }

    private void setupDarkSpinner(Spinner spinner, String[] items) {
        M3.styleDropdown(spinner, items);
    }

    public static void styleSeekBar(SeekBar bar, int activeColor) {
        M3.styleSeekBar(bar, activeColor);
    }

    public void styleSettingsButton(Button btn) {
        M3.styleButton(btn);
    }
}
