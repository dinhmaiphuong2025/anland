package com.anland.consumer.hud;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Categorized Tabbed Dialog for picking keys, WM macros, standard alphanumeric keys, and system actions.
 * Strictly adheres to Anland design language without emojis.
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

        // Tab Navigation Bar
        LinearLayout tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_HORIZONTAL);
        tabRow.setPadding(0, 0, 0, dp(context, 12));
        root.addView(tabRow);

        // Content Container
        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setMinimumHeight(dp(context, 540));
        root.addView(contentContainer);

        AlertDialog dialog = builder.setView(root).create();

        Button btnTabWM = createTabButton(context, "WM & MODS");
        Button btnTabCombos = createTabButton(context, "COMBOS");
        Button btnTabKeys = createTabButton(context, "A-Z / NUM");
        Button btnTabSys = createTabButton(context, "SYSTEM / MOUSE");

        tabRow.addView(btnTabWM);
        tabRow.addView(btnTabCombos);
        tabRow.addView(btnTabKeys);
        tabRow.addView(btnTabSys);

        Runnable[] selectTab = new Runnable[4];

        selectTab[0] = () -> {
            highlightTab(tabRow, 0);
            showGrid(context, contentContainer, getWmKeys(), listener, dialog);
        };
        selectTab[1] = () -> {
            highlightTab(tabRow, 1);
            showGrid(context, contentContainer, getNiriCombos(), listener, dialog);
        };
        selectTab[2] = () -> {
            highlightTab(tabRow, 2);
            showGrid(context, contentContainer, getStandardKeys(), listener, dialog);
        };
        selectTab[3] = () -> {
            highlightTab(tabRow, 3);
            showGrid(context, contentContainer, getSystemActions(), listener, dialog);
        };

        btnTabWM.setOnClickListener(v -> selectTab[0].run());
        btnTabCombos.setOnClickListener(v -> selectTab[1].run());
        btnTabKeys.setOnClickListener(v -> selectTab[2].run());
        btnTabSys.setOnClickListener(v -> selectTab[3].run());

        selectTab[0].run();
        dialog.show();
    }

    private static Button createTabButton(Context ctx, String text) {
        Button b = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(0xFF888888);
        b.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
        b.setBackgroundColor(Color.TRANSPARENT);
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
            AlertDialog dialog
    ) {
        container.removeAllViews();

        // GridView was unreliable here: with a wide dialog (~90% of a
        // phone screen) it would happily compute a multi-column layout,
        // yet still render only the first row when the entries were short
        // and the parent ScrollView was MATCH_PARENT. We replace it with
        // an explicit vertical LinearLayout of horizontal LinearLayout
        // rows - the layout is dumb but always correct.
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        int hSpacing = dp(ctx, 6);
        int vSpacing = dp(ctx, 6);
        int sidePad = dp(ctx, 4);
        grid.setPadding(sidePad, 0, sidePad, 0);

        int columns = computeColumnCount(ctx);
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
                btn.setTextSize(14);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(0xFF2A2B3D);
                btn.setPadding(dp(ctx, 4), dp(ctx, 12), dp(ctx, 4), dp(ctx, 12));
                btn.setMinHeight(dp(ctx, 48));
                btn.setSingleLine(true);
                btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

    // Decide how many columns to show based on the dialog width. We avoid
    // GridView entirely so the layout is predictable across dialog sizes
    // and across older releases of the framework.
    private static int computeColumnCount(Context ctx) {
        int widthDp = ctx.getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) widthDp = 360;  // safe default
        // 100dp per cell is a comfortable touch target on phones; on
        // tablets we get more columns. Clamp to 3-6 so the buttons stay
        // readable regardless of device size.
        int cols = widthDp / 100;
        if (cols < 3) cols = 3;
        if (cols > 6) cols = 6;
        return cols;
    }

    private static List<KeyEntry> getWmKeys() {
        List<KeyEntry> list = new ArrayList<>();
        list.add(new KeyEntry("SUPER (Mod)", HudAction.modifier(125)));
        list.add(new KeyEntry("CTRL", HudAction.modifier(29)));
        list.add(new KeyEntry("ALT", HudAction.modifier(56)));
        list.add(new KeyEntry("SHIFT", HudAction.modifier(42)));
        list.add(new KeyEntry("ESC", HudAction.key(1)));
        list.add(new KeyEntry("TAB", HudAction.key(15)));
        list.add(new KeyEntry("ENTER", HudAction.key(28)));
        list.add(new KeyEntry("BACKSPACE", HudAction.key(14)));
        list.add(new KeyEntry("SPACE", HudAction.key(57)));
        list.add(new KeyEntry("DELETE", HudAction.key(111)));
        list.add(new KeyEntry("HOME", HudAction.key(102)));
        list.add(new KeyEntry("END", HudAction.key(107)));
        list.add(new KeyEntry("PAGE UP", HudAction.key(104)));
        list.add(new KeyEntry("PAGE DOWN", HudAction.key(109)));
        list.add(new KeyEntry("UP (↑)", HudAction.key(103)));
        list.add(new KeyEntry("DOWN (↓)", HudAction.key(108)));
        list.add(new KeyEntry("LEFT (←)", HudAction.key(105)));
        list.add(new KeyEntry("RIGHT (→)", HudAction.key(106)));
        return list;
    }

    private static List<KeyEntry> getNiriCombos() {
        List<KeyEntry> list = new ArrayList<>();
        list.add(new KeyEntry("Overview (Mod+O)", HudAction.combo(125, 24)));
        list.add(new KeyEntry("Terminal (Mod+Enter)", HudAction.combo(125, 28)));
        list.add(new KeyEntry("Close Window (Mod+Shift+Q)", HudAction.combo(125, 42, 16)));
        list.add(new KeyEntry("Launcher (Mod+Space)", HudAction.combo(125, 57)));
        list.add(new KeyEntry("Launcher (Mod+D)", HudAction.combo(125, 32)));
        list.add(new KeyEntry("Fullscreen (Mod+F)", HudAction.combo(125, 33)));
        list.add(new KeyEntry("Next Column (Mod+Right)", HudAction.combo(125, 106)));
        list.add(new KeyEntry("Prev Column (Mod+Left)", HudAction.combo(125, 105)));
        list.add(new KeyEntry("Next WS (Mod+PgDn)", HudAction.combo(125, 109)));
        list.add(new KeyEntry("Prev WS (Mod+PgUp)", HudAction.combo(125, 104)));
        list.add(new KeyEntry("Alt+Tab", HudAction.combo(56, 15)));
        list.add(new KeyEntry("Ctrl+C (SIGINT)", HudAction.combo(29, 46)));
        list.add(new KeyEntry("Ctrl+V (Paste)", HudAction.combo(29, 47)));
        list.add(new KeyEntry("Ctrl+Z (Undo)", HudAction.combo(29, 44)));
        list.add(new KeyEntry("Ctrl+A (Select All)", HudAction.combo(29, 30)));
        list.add(new KeyEntry("Ctrl+L (Clear)", HudAction.combo(29, 38)));
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

    private static int dp(Context ctx, int dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }
}
