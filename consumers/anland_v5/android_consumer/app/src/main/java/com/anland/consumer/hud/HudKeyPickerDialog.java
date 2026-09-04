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
 *
 * The dialog has three tabs:
 *   - KEYS         alphanumeric + F-keys, four-column dense grid.
 *   - MODS & SYS   modifiers + nav / editing + system actions, three-column.
 *   - COMBO        the ComboBuilderView rendered inline (no nested dialog).
 */
public final class HudKeyPickerDialog {

    public interface OnActionSelectedListener {
        void onActionSelected(HudAction action, String displayLabel);
        // Optional: lets the combo builder deliver a custom-built combo
        // without changing the regular picker. Implementations can just
        // forward to onActionSelected if they do not care.
        default void onComboBuilt(HudAction action) {
            onActionSelected(action, formatComboLabel(action));
        }
    }

    // Render a combo's keycodes as "CTRL + C" / "SUPER + L" / "F12" style
    // text. The "None" branch is taken when the slot is empty.
    public static String formatComboLabel(HudAction action) {
        if (action == null || action.comboKeys == null || action.comboKeys.isEmpty()) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < action.comboKeys.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(HudPropertyInspectorView.labelForEvdev(action.comboKeys.get(i)));
        }
        return sb.toString();
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

        Button btnTabKeys = createTabButton(context, "KEYS");
        Button btnTabMods = createTabButton(context, "MODS & SYS");
        Button btnTabCombo = createTabButton(context, "COMBO");

        tabRow.addView(btnTabKeys);
        tabRow.addView(btnTabMods);
        tabRow.addView(btnTabCombo);

        // KEYS is dense 4-column; MODS & SYS is wider 3-column so the
        // longer labels still fit.
        final int colsKeys = computeColumnCount(context, 4);
        final int colsLong = computeColumnCount(context, 3);

        Runnable[] selectTab = new Runnable[3];
        selectTab[0] = () -> {
            highlightTab(tabRow, 0);
            showGrid(context, contentContainer, getStandardKeys(), listener, dialog, colsKeys);
        };
        selectTab[1] = () -> {
            highlightTab(tabRow, 1);
            showGrid(context, contentContainer, getModsAndSystemKeys(), listener, dialog, colsLong);
        };
        // The COMBO tab inlines the combo builder so the user does not get a
        // second nested dialog. Tapping APPLY calls onComboBuilt which
        // dismisses the outer dialog and forwards the new HudAction.combo
        // to the caller's onActionSelected (via the default in
        // OnActionSelectedListener).
        selectTab[2] = () -> {
            highlightTab(tabRow, 2);
            showComboTab(context, contentContainer, listener, dialog);
        };

        btnTabKeys.setOnClickListener(v -> selectTab[0].run());
        btnTabMods.setOnClickListener(v -> selectTab[1].run());
        btnTabCombo.setOnClickListener(v -> selectTab[2].run());

        selectTab[0].run();
        dialog.show();
    }

    // COMBO tab body: build a ComboBuilderView and place its content inside
    // the contentContainer. The builder's APPLY handler dismisses the outer
    // dialog (so the user is not stuck inside two layers) and reports the
    // chosen action through onComboBuilt.
    private static void showComboTab(final Context ctx, final LinearLayout container,
                                    final OnActionSelectedListener listener,
                                    final AlertDialog dialog) {
        container.removeAllViews();
        new ComboBuilderView().showInline(ctx, container, combo -> {
            dialog.dismiss();
            if (listener != null) listener.onComboBuilt(combo);
        });
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
                    View spacer = new View(ctx);
                    LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static int computeColumnCount(Context ctx, int minColumns) {
        int widthDp = ctx.getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) widthDp = 360;
        int cols = widthDp / 80;
        if (cols < minColumns) cols = minColumns;
        if (cols > minColumns + 2) cols = minColumns + 2;
        return cols;
    }

    // Modifiers, nav / editing keys, and system actions all share the
    // 3-column grid in the "MODS & SYS" tab so the user does not have to
    // switch tabs just to set a Toggle Soft KB action on a swipe.
    private static List<KeyEntry> getModsAndSystemKeys() {
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
        list.add(new KeyEntry("BKSP", HudAction.key(14)));
        list.add(new KeyEntry("SPACE", HudAction.key(57)));
        list.add(new KeyEntry("DEL", HudAction.key(111)));
        list.add(new KeyEntry("HOME", HudAction.key(102)));
        list.add(new KeyEntry("END", HudAction.key(107)));
        list.add(new KeyEntry("PGUP", HudAction.key(104)));
        list.add(new KeyEntry("PGDN", HudAction.key(109)));
        // Arrow keys
        list.add(new KeyEntry("UP", HudAction.key(103)));
        list.add(new KeyEntry("DOWN", HudAction.key(108)));
        list.add(new KeyEntry("LEFT", HudAction.key(105)));
        list.add(new KeyEntry("RIGHT", HudAction.key(106)));
        // System actions
        list.add(new KeyEntry("Toggle Soft KB", HudAction.system("toggle_ime")));
        list.add(new KeyEntry("Toggle Virtual KB", HudAction.system("toggle_vk")));
        list.add(new KeyEntry("Open Settings", HudAction.system("open_settings")));
        list.add(new KeyEntry("Mouse Left", HudAction.system("mouse_left")));
        list.add(new KeyEntry("Mouse Right", HudAction.system("mouse_right")));
        list.add(new KeyEntry("Mouse Middle", HudAction.system("mouse_middle")));
        list.add(new KeyEntry("Scroll Up", HudAction.system("mouse_scroll_up")));
        list.add(new KeyEntry("Scroll Down", HudAction.system("mouse_scroll_down")));
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

    private static int dp(Context ctx, int dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }
}
