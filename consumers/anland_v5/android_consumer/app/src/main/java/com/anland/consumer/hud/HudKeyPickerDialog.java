package com.anland.consumer.hud;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
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

        ScrollView scroll = new ScrollView(ctx);
        GridView grid = new GridView(ctx);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(ctx, 100));
        grid.setHorizontalSpacing(dp(ctx, 6));
        grid.setVerticalSpacing(dp(ctx, 6));
        // STRETCH_SPACING_UNIFORM keeps the inter-column gap constant and
        // shares the leftover space evenly between the outer margins. With
        // AUTO_FIT + columnWidth=100dp, a 360dp dialog gets 3 columns of
        // 114dp each; a 600dp dialog gets 6 columns. Every entry stays
        // visible without horizontal scrolling.
        grid.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);

        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int i) { return entries.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View view, ViewGroup parent) {
                Button btn = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
                KeyEntry entry = entries.get(i);
                btn.setText(entry.label);
                btn.setTextSize(14);
                btn.setTextColor(Color.WHITE);
                btn.setBackgroundColor(0xFF2A2B3D);
                btn.setPadding(dp(ctx, 4), dp(ctx, 12), dp(ctx, 4), dp(ctx, 12));
                btn.setMinHeight(dp(ctx, 48));
                btn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onActionSelected(entry.action.copy(), entry.label);
                    }
                });
                return btn;
            }
        });

        scroll.addView(grid);
        // MATCH_PARENT height lets the grid show all rows. With AUTO_FIT we
        // typically have 3-6 rows per tab; a fixed 480dp was sometimes
        // clipping the bottom row when the device was short.
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
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
