package com.anland.consumer.hud;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Categorized Tabbed Dialog for picking keys, WM macros, standard
 * alphanumeric keys, and system actions. Strictly adheres to Anland
 * design language without emojis.
 */
public final class HudKeyPickerDialog {

    public interface OnActionSelectedListener {
        void onActionSelected(HudAction action, String displayLabel);
    }

    private static final class KeyEntry {
        final String label;
        final HudAction action;
        KeyEntry(String label, HudAction action) {
            this.label = label;
            this.action = action;
        }
    }

    public static void show(Context context, OnActionSelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, AlertDialog.THEME_DEVICE_DEFAULT_DARK);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        root.setBackgroundColor(0xFF1E1E2E);

        // Header Title
        TextView title = new TextView(context);
        title.setText("SELECT KEY / ACTION");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(context, 12));
        root.addView(title);

        // Tab Navigation Bar (wrapped in HorizontalScrollView so the labels
        // never get squeezed on narrow portrait phones).
        HorizontalScrollView tabScroller = new HorizontalScrollView(context);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setFillViewport(true);
        LinearLayout tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 12));
        tabScroller.addView(tabRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(tabScroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Content Container
        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setMinimumHeight(dp(context, 480));
        root.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog dialog = builder.setView(root).create();

        // 3 tabs only: KEYS (alphanumeric + F-keys), MODS (modifiers + nav +
        // editing), COMBO & SYS (Niri macros + system actions).
        Button btnTabKeys = createTabButton(context, "KEYS");
        Button btnTabMods = createTabButton(context, "MODS");
        Button btnTabComboSys = createTabButton(context, "COMBO & SYS");

        tabRow.addView(btnTabKeys);
        tabRow.addView(btnTabMods);
        tabRow.addView(btnTabComboSys);

        // Compute grid columns per tab so letters/numbers get the wide
        // 4-column layout while longer labels stay on a 3-column grid.
        final int colsKeys = computeColumnCount(context, 4);
        final int colsLong = computeColumnCount(context, 3);

        Runnable[] selectTab = new Runnable[3];
        selectTab[0] = () -> {
            highlightTab(tabRow, 0);
            showGrid(context, contentContainer, getStandardKeys(), listener, dialog, colsKeys);
        };
        selectTab[1] = () -> {
            highlightTab(tabRow, 1);
            showGrid(context, contentContainer, getWmKeys(), listener, dialog, colsLong);
        };
        selectTab[2] = () -> {
            highlightTab(tabRow, 2);
            // Combos + system actions: same long-label grid.
            showGrid(context, contentContainer, getNiriAndSystem(listener), listener, dialog, colsLong);
        };

        btnTabKeys.setOnClickListener(v -> selectTab[0].run());
        btnTabMods.setOnClickListener(v -> selectTab[1].run());
        btnTabComboSys.setOnClickListener(v -> selectTab[2].run());

        selectTab[0].run();
        dialog.show();
    }

    private static Button createTabButton(Context ctx, String text) {
        Button b = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(0xFF888888);
        b.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setAllCaps(true);
        return b;
    }

    private static void highlightTab(LinearLayout tabRow, int activeIdx) {
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            Button b = (Button) tabRow.getChildAt(i);
            if (i == activeIdx) {
                b.setTextColor(0xFF80DEEA);
                b.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                b.setTextColor(0xFF888888);
                b.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    private static void showGrid(
            Context ctx,
            LinearLayout container,
            List<KeyEntry> entries,
            OnActionSelectedListener listener,
            AlertDialog dialog,
            int columns
    ) {
        container.removeAllViews();

        // Explicit vertical LinearLayout of horizontal rows. The GridView
        // attempt earlier only rendered the first row on this dialog size;
        // this layout is dumb but always correct and reacts well to long
        // labels because each cell is given equal share of the width via
        // LayoutParams weight=1.
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        int hSpacing = dp(ctx, 6);
        int vSpacing = dp(ctx, 6);
        int sidePad = dp(ctx, 4);
        grid.setPadding(sidePad, 0, sidePad, 0);

        for (int row = 0; row < entries.size(); row += columns) {
            LinearLayout rowView = new LinearLayout(ctx);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setBaselineAligned(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row > 0) rowLp.topMargin = vSpacing;
            grid.addView(rowView, rowLp);

            for (int col = 0; col < columns; col++) {
                int idx = row + col;
                if (idx >= entries.size()) {
                    // Pad the last row with a transparent spacer so the
                    // remaining cells do not stretch awkwardly.
                    View spacer = new View(ctx);
                    LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    spacerLp.setMargins(0, 0, 0, 0);
                    rowView.addView(spacer, spacerLp);
                    continue;
                }
                final KeyEntry entry = entries.get(idx);
                Button btn = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
                btn.setText(entry.label);
                btn.setTextSize(13);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(0xFF2A2B3D);
                btn.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 8));
                // 44dp is the Material Design minimum touch target; we keep
                // even the short letters at that height so the row rhythm
                // is consistent across all tabs.
                btn.setMinHeight(dp(ctx, 44));
                btn.setSingleLine(true);
                btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
                btn.setIncludeFontPadding(false);
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) btnLp.leftMargin = hSpacing;
                btn.setLayoutParams(btnLp);
                btn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onActionSelected(entry.action.copy(), entry.label);
                    }
                });
                rowView.addView(btn);
            }
        }

        scroll.addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // MATCH_PARENT height lets the ScrollView fill the dialog; the
        // child grid is WRAP_CONTENT so all rows are reachable by
        // vertical scrolling.
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // Decide how many columns to show based on the dialog width. We pass
    // the caller's preferred minimum (e.g. 4 for short labels) so letters
    // and numbers get a denser grid while longer labels stay on a wider
    // 3-column grid. The result is also clamped to an upper bound so a
    // 600dp+ tablet does not end up with 7 columns of cramped buttons.
    private static int computeColumnCount(Context ctx, int minColumns) {
        int widthDp = ctx.getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) widthDp = 360;  // safe default
        int cols = widthDp / 80;
        if (cols < minColumns) cols = minColumns;
        if (cols > minColumns + 2) cols = minColumns + 2;
        return cols;
    }

    private static List<KeyEntry> getWmKeys() {
        List<KeyEntry> list = new ArrayList<>();
        // Modifiers
        list.add(new KeyEntry("SUPER", HudAction.modifier(125)));
        list.add(new KeyEntry("CTRL", HudAction.modifier(29)));
        list.add(new KeyEntry("ALT", HudAction.modifier(56)));
        list.add(new KeyEntry("SHIFT", HudAction.modifier(42)));
        // Common editing keys
        list.add(new KeyEntry("ESC", HudAction.key(1)));
        list.add(new KeyEntry("TAB", HudAction.key(15)));
        list.add(new KeyEntry("ENTER", HudAction.key(28)));
        list.add(new KeyEntry("BKSP", HudAction.key(14)));     // Backspace
        list.add(new KeyEntry("SPACE", HudAction.key(57)));
        list.add(new KeyEntry("DEL", HudAction.key(111)));      // Delete
        list.add(new KeyEntry("HOME", HudAction.key(102)));
        list.add(new KeyEntry("END", HudAction.key(107)));
        list.add(new KeyEntry("PGUP", HudAction.key(104)));     // Page Up
        list.add(new KeyEntry("PGDN", HudAction.key(109)));     // Page Down
        // Arrow keys
        list.add(new KeyEntry("UP", HudAction.key(103)));
        list.add(new KeyEntry("DOWN", HudAction.key(108)));
        list.add(new KeyEntry("LEFT", HudAction.key(105)));
        list.add(new KeyEntry("RIGHT", HudAction.key(106)));
        return list;
    }

    private static List<KeyEntry> getNiriCombos() {
        List<KeyEntry> list = new ArrayList<>();
        // Labels are kept short so a portrait phone (4 columns at ~80dp
        // each) can show the full word without ellipsizing. The original
        // wording is preserved in the comment above each entry.
        list.add(new KeyEntry("Overview", HudAction.combo(125, 24)));         // Mod+O
        list.add(new KeyEntry("Terminal", HudAction.combo(125, 28)));          // Mod+Enter
        list.add(new KeyEntry("Close Win", HudAction.combo(125, 42, 16)));     // Mod+Shift+Q
        list.add(new KeyEntry("Launcher", HudAction.combo(125, 57)));          // Mod+Space
        list.add(new KeyEntry("Launcher", HudAction.combo(125, 32)));          // Mod+D
        list.add(new KeyEntry("Fullscreen", HudAction.combo(125, 33)));       // Mod+F
        list.add(new KeyEntry("Next Col", HudAction.combo(125, 106)));          // Mod+Right
        list.add(new KeyEntry("Prev Col", HudAction.combo(125, 105)));          // Mod+Left
        list.add(new KeyEntry("Next WS", HudAction.combo(125, 109)));            // Mod+PgDn
        list.add(new KeyEntry("Prev WS", HudAction.combo(125, 104)));            // Mod+PgUp
        list.add(new KeyEntry("Alt+Tab", HudAction.combo(56, 15)));
        list.add(new KeyEntry("Ctrl+C", HudAction.combo(29, 46)));              // SIGINT
        list.add(new KeyEntry("Ctrl+V", HudAction.combo(29, 47)));              // Paste
        list.add(new KeyEntry("Ctrl+Z", HudAction.combo(29, 44)));              // Undo
        list.add(new KeyEntry("Ctrl+A", HudAction.combo(29, 30)));              // Select All
        list.add(new KeyEntry("Ctrl+L", HudAction.combo(29, 38)));              // Clear
        return list;
    }

    private static List<KeyEntry> getStandardKeys() {
        List<KeyEntry> list = new ArrayList<>();
        // Letters A-Z
        int[] letterCodes = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
        for (int i = 0; i < 26; i++) {
            char ch = (char) ('A' + i);
            list.add(new KeyEntry(String.valueOf(ch), HudAction.key(letterCodes[i])));
        }
        // Numbers 0-9
        int[] numCodes = {11, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        for (int i = 0; i <= 9; i++) {
            list.add(new KeyEntry(String.valueOf(i), HudAction.key(numCodes[i])));
        }
        // F1-F12
        int[] fCodes = {59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 87, 88};
        for (int i = 1; i <= 12; i++) {
            list.add(new KeyEntry("F" + i, HudAction.key(fCodes[i - 1])));
        }
        return list;
    }

    private static List<KeyEntry> getSystemActions() {
        List<KeyEntry> list = new ArrayList<>();
        list.add(new KeyEntry("Toggle Soft KB", HudAction.system("toggle_ime")));
        list.add(new KeyEntry("Toggle Virtual KB", HudAction.system("toggle_vk")));
        list.add(new KeyEntry("Open Settings", HudAction.system("open_settings")));
        list.add(new KeyEntry("Mouse Left Click", HudAction.system("mouse_left")));
        list.add(new KeyEntry("Mouse Right Click", HudAction.system("mouse_right")));
        list.add(new KeyEntry("Mouse Middle Click", HudAction.system("mouse_middle")));
        list.add(new KeyEntry("Mouse Scroll Up", HudAction.system("mouse_scroll_up")));
        list.add(new KeyEntry("Mouse Scroll Down", HudAction.system("mouse_scroll_down")));
        return list;
    }

    // COMBO & SYS tab content: Niri compositor macros first (the
    // most common ones), then system actions like the virtual/soft
    // keyboard toggle, mouse buttons, and scroll wheel events.
    private static List<KeyEntry> getNiriAndSystem(OnActionSelectedListener listener) {
        // We cannot capture `listener` here without making it a static
        // field; the dialog is constructed in the show() call which
        // already passes `listener` through to each entry's onClick
        // callback. So this method just builds the read-only list.
        List<KeyEntry> list = new ArrayList<>(getNiriCombos());
        list.addAll(getSystemActions());
        return list;
    }

    private static int dp(Context ctx, int dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }
}
