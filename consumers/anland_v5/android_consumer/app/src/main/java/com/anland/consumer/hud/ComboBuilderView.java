package com.anland.consumer.hud;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Free-form combo builder. The user picks up to three keys and the view
 * emits a HudAction.combo(int[]...) when the user accepts. The active
 * slot is highlighted; pressing a key fills the slot and advances to
 * the next empty one.
 *
 * Strict no-emoji rule: no glyph, all labels are plain Latin text.
 */
public final class ComboBuilderView {

    public interface OnComboBuiltListener {
        void onComboBuilt(HudAction action);
    }

    private static final int MAX_SLOTS = 3;

    // The keycode -> human label map. We keep it tiny on purpose: the
    // builder is a fallback for the predefined macros in
    // HudKeyPickerDialog, so the common keys are enough.
    private static final int[] MODIFIER_CODES = {
            29,   // CTRL
            56,   // ALT
            125,  // SUPER
            42,   // SHIFT
    };
    private static final String[] MODIFIER_LABELS = {"CTRL", "ALT", "SUPER", "SHIFT"};

    private final int[] mSlotCodes = new int[MAX_SLOTS];
    private int mActiveSlot = 0;
    private final TextView[] mSlotLabels = new TextView[MAX_SLOTS];

    // All selectable keycodes for the second / third rows. Modifier
    // keys go in the first column; letters + digits in the middle;
    // function keys at the end.
    private final List<Integer> mKeycodes = new ArrayList<>();
    private final List<String> mKeyLabels = new ArrayList<>();

    public ComboBuilderView() {
        // Modifiers
        for (int i = 0; i < MODIFIER_CODES.length; i++) {
            mKeycodes.add(MODIFIER_CODES[i]);
            mKeyLabels.add(MODIFIER_LABELS[i]);
        }
        // Letters A-Z (Android keycodes 29-50 are mapped in KeyCodeMapper)
        for (char c = 'A'; c <= 'Z'; c++) {
            mKeycodes.add(android.view.KeyEvent.keyCodeFromString("KEYCODE_" + c));
            mKeyLabels.add(String.valueOf(c));
        }
        // Numbers 0-9
        for (int d = 0; d <= 9; d++) {
            mKeycodes.add(android.view.KeyEvent.keyCodeFromString("KEYCODE_" + d));
            mKeyLabels.add(String.valueOf(d));
        }
        // F1-F12 (59-68, then 87, 88 for F11/F12)
        int[] fCodes = {59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 87, 88};
        for (int i = 0; i < fCodes.length; i++) {
            mKeycodes.add(fCodes[i]);
            mKeyLabels.add("F" + (i + 1));
        }
        // Common editing
        mKeycodes.add(android.view.KeyEvent.KEYCODE_SPACE);
        mKeyLabels.add("SPACE");
        mKeycodes.add(android.view.KeyEvent.KEYCODE_TAB);
        mKeyLabels.add("TAB");
        mKeycodes.add(android.view.KeyEvent.KEYCODE_ENTER);
        mKeyLabels.add("ENTER");
        mKeycodes.add(android.view.KeyEvent.KEYCODE_ESCAPE);
        mKeyLabels.add("ESC");
        mKeycodes.add(android.view.KeyEvent.KEYCODE_DEL);
        mKeyLabels.add("BKSP");
        mKeycodes.add(android.view.KeyEvent.KEYCODE_FORWARD_DEL);
        mKeyLabels.add("DEL");
    }

    /** Reset all slots to empty. */
    public void clear() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            mSlotCodes[i] = 0;
            if (mSlotLabels[i] != null) mSlotLabels[i].setText("Slot " + (i + 1));
        }
        mActiveSlot = 0;
        refreshActiveSlot();
    }

    /** Set a slot's keycode by index. */
    public void setSlot(int index, int keycode) {
        if (index < 0 || index >= MAX_SLOTS) return;
        mSlotCodes[index] = keycode;
        if (mSlotLabels[index] != null) mSlotLabels[index].setText(labelFor(keycode));
        // Auto-advance to the next empty slot.
        int next = -1;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (mSlotCodes[i] == 0) { next = i; break; }
        }
        mActiveSlot = next >= 0 ? next : (index + 1) % MAX_SLOTS;
        refreshActiveSlot();
    }

    /** Mark a slot as the user's current focus. */
    public void setActiveSlot(int index) {
        if (index < 0 || index >= MAX_SLOTS) return;
        mActiveSlot = index;
        refreshActiveSlot();
    }

    private void refreshActiveSlot() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (mSlotLabels[i] == null) continue;
            if (i == mActiveSlot) {
                mSlotLabels[i].setBackgroundColor(0xFF1F6FEB);
                mSlotLabels[i].setTextColor(Color.WHITE);
            } else if (mSlotCodes[i] != 0) {
                mSlotLabels[i].setBackgroundColor(0xFF2A2B3D);
                mSlotLabels[i].setTextColor(0xFF80DEEA);
            } else {
                mSlotLabels[i].setBackgroundColor(0xFF2A2B3D);
                mSlotLabels[i].setTextColor(0xFF888888);
            }
        }
    }

    private String labelFor(int keycode) {
        if (keycode == 0) return "Slot " + (mActiveSlot + 1);
        // Modifiers get a friendly name; otherwise the letter itself.
        for (int i = 0; i < MODIFIER_CODES.length; i++) {
            if (MODIFIER_CODES[i] == keycode) return MODIFIER_LABELS[i];
        }
        for (int i = 0; i < mKeycodes.size(); i++) {
            if (mKeycodes.get(i) == keycode) return mKeyLabels.get(i);
        }
        return "Key " + keycode;
    }

    private void commit() {
        // Collapse to only the filled slots, in order.
        int[] arr = new int[MAX_SLOTS];
        int n = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (mSlotCodes[i] != 0) arr[n++] = mSlotCodes[i];
        }
        if (n == 0) return;
        int[] packed = new int[n];
        System.arraycopy(arr, 0, packed, 0, n);
        HudAction action = HudAction.combo(packed);
        mListener.onComboBuilt(action);
    }

    private OnComboBuiltListener mListener;
    private AlertDialog mDialog;
    private TextView mPreview;

    /**
     * Show a standalone AlertDialog with three slots, a key picker, and
     * Clear / Apply buttons. Calls {@code listener} when the user accepts
     * a combo and dismisses the dialog.
     */
    public void showDialog(Context ctx, OnComboBuiltListener listener) {
        mListener = listener;
        mDialog = new AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setView(buildContent(ctx))
                .setCancelable(true)
                .create();
        mDialog.show();
    }

    /**
     * Inline variant: the dialog body is appended to {@code host} instead
     * of creating an AlertDialog. The caller dismisses its own outer
     * dialog (e.g. the HudKeyPickerDialog) by calling the onComboBuilt
     * callback. We clear {@code mDialog} so the inline flow does not try
     * to dismiss a non-existent dialog.
     */
    public void showInline(Context ctx, ViewGroup host, OnComboBuiltListener listener) {
        mListener = listener;
        mDialog = null;
        host.removeAllViews();
        host.addView(buildContent(ctx));
    }

    // Build the dialog body. Used by both the standalone AlertDialog flow
    // (showDialog) and the inline flow (showInline) so the layout is
    // consistent across both.
    private LinearLayout buildContent(final Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1E1E2E);
        root.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16));

        TextView title = new TextView(ctx);
        title.setText("COMBO BUILDER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(ctx, 12));
        root.addView(title);

        TextView hint = new TextView(ctx);
        hint.setText("Pick up to three keys. The active slot is highlighted blue; pick a key to fill it and advance to the next slot.");
        hint.setTextColor(0xFFAAAAAA);
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(ctx, 12));
        root.addView(hint);

        // Slot row
        LinearLayout slotRow = new LinearLayout(ctx);
        slotRow.setOrientation(LinearLayout.HORIZONTAL);
        slotRow.setGravity(Gravity.CENTER_VERTICAL);
        slotRow.setPadding(0, 0, 0, dp(ctx, 12));
        for (int i = 0; i < MAX_SLOTS; i++) {
            TextView slot = new TextView(ctx);
            slot.setText("Slot " + (i + 1));
            slot.setTextSize(15);
            slot.setAllCaps(false);
            slot.setTextColor(0xFF888888);
            slot.setGravity(Gravity.CENTER);
            slot.setBackgroundColor(0xFF2A2B3D);
            slot.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(ctx, 8));
            slot.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(ctx, 8);
            slotRow.addView(slot, lp);
            mSlotLabels[i] = slot;
            final int slotIndex = i;
            slot.setOnClickListener(v -> setActiveSlot(slotIndex));
        }
        root.addView(slotRow);

        // Live preview, e.g. "CTRL + C"
        mPreview = new TextView(ctx);
        mPreview.setText("");
        mPreview.setTextColor(0xFF80DEEA);
        mPreview.setTextSize(14);
        mPreview.setGravity(Gravity.CENTER);
        mPreview.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));
        // Preview is added below the key picker; it is shown next to
        // the action buttons so the user sees the result of their picks
        // before pressing APPLY.

        // Key picker grid
        ScrollView keyScroll = new ScrollView(ctx);
        keyScroll.setBackgroundColor(0xFF15151E);
        keyScroll.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        LinearLayout keyGrid = new LinearLayout(ctx);
        keyGrid.setOrientation(LinearLayout.VERTICAL);
        final int keyCols = 4;
        final int hSpacing = dp(ctx, 6);
        final int vSpacing = dp(ctx, 6);
        for (int i = 0; i < mKeycodes.size(); i += keyCols) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowLp.topMargin = vSpacing;
            keyGrid.addView(row, rowLp);
            for (int col = 0; col < keyCols; col++) {
                final int idx = i + col;
                if (idx >= mKeycodes.size()) {
                    ViewGroup.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    row.addView(new android.view.View(ctx), sp);
                    continue;
                }
                final int code = mKeycodes.get(idx);
                final String lbl = mKeyLabels.get(idx);
                Button k = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
                k.setText(lbl);
                k.setTextSize(12);
                k.setAllCaps(false);
                k.setTextColor(Color.WHITE);
                k.setBackgroundColor(0xFF2A2B3D);
                k.setMinHeight(dp(ctx, 40));
                k.setPadding(dp(ctx, 2), dp(ctx, 6), dp(ctx, 2), dp(ctx, 6));
                k.setSingleLine(true);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) blp.leftMargin = hSpacing;
                k.setLayoutParams(blp);
                k.setOnClickListener(v -> {
                    setSlot(mActiveSlot, code);
                    updatePreview();
                });
                row.addView(k);
            }
        }
        keyScroll.addView(keyGrid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(keyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(ctx, 320)));

        // Preview is shown just below the key grid, above the action row.
        root.addView(mPreview);

        // Action row: Clear All + Apply Combo
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(ctx, 12), 0, 0);
        Button btnClear = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
        btnClear.setText("CLEAR ALL");
        btnClear.setTextSize(12);
        btnClear.setAllCaps(true);
        btnClear.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable bgClear = new android.graphics.drawable.GradientDrawable();
        bgClear.setCornerRadius(dp(ctx, 8));
        bgClear.setColor(0x33FFFFFF);
        btnClear.setBackground(bgClear);
        btnClear.setOnClickListener(v -> {
            clear();
            updatePreview();
        });
        actions.addView(btnClear, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnApply = new Button(ctx, null, android.R.attr.buttonBarButtonStyle);
        btnApply.setText("APPLY COMBO");
        btnApply.setTextSize(12);
        btnApply.setAllCaps(true);
        btnApply.setTextColor(Color.BLACK);
        android.graphics.drawable.GradientDrawable bgApply = new android.graphics.drawable.GradientDrawable();
        bgApply.setCornerRadius(dp(ctx, 8));
        bgApply.setColor(0xFF80DEEA);
        btnApply.setBackground(bgApply);
        btnApply.setOnClickListener(v -> {
            commit();
            if (mDialog != null) mDialog.dismiss();
        });
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        applyLp.leftMargin = dp(ctx, 8);
        actions.addView(btnApply, applyLp);

        root.addView(actions);

        refreshActiveSlot();
        updatePreview();

        return root;
    }

    private void updatePreview() {
        if (mPreview == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (mSlotCodes[i] == 0) continue;
            if (sb.length() > 0) sb.append(" + ");
            sb.append(labelFor(mSlotCodes[i]));
        }
        if (sb.length() == 0) sb.append("(empty)");
        mPreview.setText(sb.toString());
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
