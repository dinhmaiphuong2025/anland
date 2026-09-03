package com.anland.consumer.hud;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Pinned Dock Strip View (Extensible Gboard tracking bar).
 * Supports horizontal scrolling, drag-reordering, popup keys, and action dispatching.
 */
public final class HudDockStripView extends HorizontalScrollView {

    public interface DockActionListener {
        void onDockItemClick(HudButton item);
        void onDockItemLongPress(HudButton item);
    }

    private final HudLayout mLayout;
    private final DockActionListener mListener;
    private final LinearLayout mRow;
    // When edit mode is on, the dock strip turns into a configuration row:
    // the buttons are visually subdued (lower opacity), drag-to-move is still
    // allowed, and tap/long-press are routed to "select for rebinding" /
    // "open key picker" instead of dispatching real key events.
    private boolean mEditMode = false;
    private int mSelectedDockIndex = -1;

    public HudDockStripView(Context context, HudLayout layout, DockActionListener listener) {
        super(context);
        this.mLayout = layout;
        this.mListener = listener;
        setHorizontalScrollBarEnabled(false);

        mRow = new LinearLayout(context);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        mRow.setGravity(Gravity.CENTER_VERTICAL);
        mRow.setBackgroundColor(layout.dockBgColor);
        mRow.setPadding(dp(4), dp(2), dp(4), dp(2));

        if (!mLayout.dockScrollable) {
            addView(mRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setFillViewport(true);
        } else {
            addView(mRow, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        rebuildItems();
    }

    public void setEditMode(boolean editMode) {
        mEditMode = editMode;
        if (!editMode) mSelectedDockIndex = -1;
        rebuildItems();
    }

    public int getSelectedDockIndex() {
        return mSelectedDockIndex;
    }

    public void rebuildItems() {
        mRow.removeAllViews();
        List<HudButton> items = mLayout.dockItems;
        for (int i = 0; i < items.size(); i++) {
            HudButton item = items.get(i);
            Button btn = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
            btn.setText(item.label != null ? item.label : "");
            btn.setTextSize(12);
            // Selected dock item gets a vivid cyan border in edit mode to mirror
            // the floating button's selection treatment.
            if (mEditMode && i == mSelectedDockIndex) {
                btn.setTextColor(0xFF11111B);
                btn.setBackgroundColor(0xFF80DEEA);
            } else if (mEditMode) {
                // Lower opacity so the strip reads as "configurable" not "active".
                btn.setTextColor(0x99FFFFFF);
                btn.setBackgroundColor(0x22FFFFFF);
            } else {
                btn.setTextColor(item.textColor);
                btn.setBackgroundColor(item.bgColor != 0 ? item.bgColor : 0x22FFFFFF);
            }
            btn.setPadding(dp(6), dp(4), dp(6), dp(4));

            int width = ViewGroup.LayoutParams.WRAP_CONTENT;
            float weight = 0f;
            if (item.widthDp > 0) {
                width = dp(item.widthDp);
            } else if (!mLayout.dockScrollable) {
                weight = 1f;
                width = 0;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(mLayout.dockHeightDp - 6), weight);
            lp.setMargins(dp(2), 0, dp(2), 0);
            btn.setLayoutParams(lp);

            final int dockIdx = i;
            btn.setOnClickListener(v -> {
                if (mEditMode) {
                    mSelectedDockIndex = dockIdx;
                    rebuildItems();
                }
                if (mListener != null) mListener.onDockItemClick(item);
            });
            btn.setOnLongClickListener(v -> {
                if (mListener != null) mListener.onDockItemLongPress(item);
                return true;
            });

            mRow.addView(btn);
        }
    }

    public void setModifierActiveState(int evdev, boolean active) {
        List<HudButton> items = mLayout.dockItems;
        for (int i = 0; i < items.size(); i++) {
            HudButton item = items.get(i);
            if (item.action != null && HudAction.TYPE_MODIFIER.equals(item.action.type) && item.action.code == evdev) {
                if (i < mRow.getChildCount()) {
                    View v = mRow.getChildAt(i);
                    if (v instanceof Button) {
                        Button btn = (Button) v;
                        // Edit mode uses the subdued palette; skip the active
                        // highlight there to avoid visual noise.
                        if (mEditMode) return;
                        btn.setTextColor(active ? 0xFF80DEEA : item.textColor);
                        btn.setBackgroundColor(active ? 0x6680DEEA : (item.bgColor != 0 ? item.bgColor : 0x22FFFFFF));
                    }
                }
            }
        }
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
