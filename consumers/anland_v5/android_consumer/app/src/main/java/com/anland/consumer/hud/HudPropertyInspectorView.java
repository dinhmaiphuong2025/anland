package com.anland.consumer.hud;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Draggable Property Inspector Panel (PUBG/FreeFire style).
 * Allows live fine-tuning of Width, Height, Corner Radius, and Opacity via both smooth SeekBars
 * and exact numeric EditTexts, with draggable header to prevent blocking button views.
 */
public final class HudPropertyInspectorView extends LinearLayout {

    public interface InspectorCallback {
        void onModelChanged(HudButton button);
        void onDeleteRequested(HudButton button);
        void onDuplicateRequested(HudButton button);
        void onPickActionRequested(HudButton button, int targetSlot); // 0=main, 1=popup, 2=left, 3=right, 4=up, 5=down
        void onCloseRequested();
    }

    private HudButton mActiveButton;
    private final InspectorCallback mCallback;
    // Guard flag: true while we are programmatically setting EditText / SeekBar
    // values inside bindButton(). The TextWatcher on mLabelInput and the seek
    // listeners otherwise fire on every setText(), which would call back into
    // mCallback.onModelChanged() and round-trip through HudOverlayView's
    // rebuildActiveLayout(), ultimately resulting in a re-entrant bind and
    // either a stack overflow or a runaway model update.
    private boolean mIsProgrammaticChange = false;

    private TextView mTitleText;
    private EditText mLabelInput;

    // Sliders + Exact Numeric Inputs
    private SeekBar mWidthSeekBar;
    private EditText mWidthInput;

    private SeekBar mHeightSeekBar;
    private EditText mHeightInput;

    private SeekBar mCornerSeekBar;
    private EditText mCornerInput;

    private SeekBar mOpacitySeekBar;
    private EditText mOpacityInput;

    // Container that holds the four size/opacity slider rows. Visible only for
    // floating buttons; hidden for dock items (their size is fixed by the
    // dock strip's grid policy).
    private LinearLayout mSizeSection;

    private Button mBtnPickMainAction;
    private LinearLayout mSuperGestureOptions;
    // TrackPoint mode toggle row: visible only for the trackpoint widget.
    // Two buttons (MOUSE / SCROLL) plus a small status text underneath.
    private LinearLayout mTrackPointOptions;
    private Button mBtnTrackPointMode;
    // Bottom action row; cached so bindDockItem can hide it (dock items cannot
    // be deleted or duplicated from the strip).
    private LinearLayout mOpRow;
    private Button mBtnPickSwipeLeft;
    private Button mBtnPickSwipeRight;
    private Button mBtnPickSwipeUp;
    private Button mBtnPickSwipeDown;

    private float mDragStartX;
    private float mDragStartY;

    public HudPropertyInspectorView(Context context, InspectorCallback callback) {
        super(context);
        this.mCallback = callback;
        initView();
    }

    private void initView() {
        setOrientation(VERTICAL);
        setBackgroundColor(0xF0181825); // 94% Dark backdrop
        setPadding(dp(12), dp(8), dp(12), dp(12));
        setElevation(dp(8));

        // 1. Draggable Header Bar
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0x33FFFFFF);
        header.setPadding(dp(8), dp(6), dp(8), dp(6));

        TextView dragHandle = new TextView(getContext());
        dragHandle.setText("DRAG PANEL");
        dragHandle.setTextColor(0xFF80DEEA);
        dragHandle.setTextSize(12);
        dragHandle.setTypeface(Typeface.DEFAULT_BOLD);
        dragHandle.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(dragHandle);

        // Round close button. We render "X" inside a programmatic circular
        // background so we stay consistent with the no-emoji rule.
        Button btnClose = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        btnClose.setText("X");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(13);
        btnClose.setBackground(getRoundCloseBackground());
        btnClose.setMinWidth(0);
        btnClose.setMinHeight(0);
        btnClose.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> {
            if (mCallback != null) mCallback.onCloseRequested();
        });
        header.addView(btnClose);

        // Header drag listener
        header.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDragStartX = event.getRawX() - getTranslationX();
                    mDragStartY = event.getRawY() - getTranslationY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    setTranslationX(event.getRawX() - mDragStartX);
                    setTranslationY(event.getRawY() - mDragStartY);
                    return true;
            }
            return false;
        });
        addView(header);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(0, dp(8), 0, dp(4));

        mTitleText = new TextView(getContext());
        mTitleText.setText("BUTTON PROPERTIES");
        mTitleText.setTextColor(Color.WHITE);
        mTitleText.setTextSize(14);
        mTitleText.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(mTitleText);

        // Label input
        content.addView(createLabelValueRow("Display Label",
                createLeftAlignedEditText(), mLabelInput = createLabelEditText()));
        mLabelInput.setTextColor(Color.WHITE);
        mLabelInput.setTextSize(13);
        mLabelInput.setBackgroundColor(0x22FFFFFF);
        mLabelInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        mLabelInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (mIsProgrammaticChange) return;
                if (mActiveButton != null && !s.toString().equals(mActiveButton.label)) {
                    mActiveButton.label = s.toString();
                    if (mCallback != null) mCallback.onModelChanged(mActiveButton);
                }
            }
        });

        // Sliders with exact Numeric Edit Fields. All four live inside a single
        // container so we can hide the whole block for dock items (where
        // size/corner/opacity are fixed by the strip layout) without tearing
        // down the views underneath.
        mSizeSection = new LinearLayout(getContext());
        mSizeSection.setOrientation(VERTICAL);

        mWidthSeekBar = new SeekBar(getContext());
        mWidthInput = createExactNumberInput();
        mSizeSection.addView(createSliderRow("Width (dp):", mWidthSeekBar, mWidthInput, 20, 240, val -> {
            if (mActiveButton != null) {
                mActiveButton.widthDp = val;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        mHeightSeekBar = new SeekBar(getContext());
        mHeightInput = createExactNumberInput();
        mSizeSection.addView(createSliderRow("Height (dp):", mHeightSeekBar, mHeightInput, 20, 240, val -> {
            if (mActiveButton != null) {
                mActiveButton.heightDp = val;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        mCornerSeekBar = new SeekBar(getContext());
        mCornerInput = createExactNumberInput();
        mSizeSection.addView(createSliderRow("Corner (dp):", mCornerSeekBar, mCornerInput, 0, 50, val -> {
            if (mActiveButton != null) {
                mActiveButton.cornerRadiusDp = val;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        mOpacitySeekBar = new SeekBar(getContext());
        mOpacityInput = createExactNumberInput();
        mSizeSection.addView(createSliderRow("Opacity (%):", mOpacitySeekBar, mOpacityInput, 10, 100, val -> {
            if (mActiveButton != null) {
                mActiveButton.opacity = val / 100f;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));
        content.addView(mSizeSection);

        // Action Assignment rendered as a left-right row: the "Action" label
        // on the left, a badge button on the right showing the currently
        // bound key / combo. The text is rebuilt in bindButton / bindDockItem
        // so the badge always reflects the live model.
        content.addView(createSectionLabel("Action Mapping:"));
        mBtnPickMainAction = createBadgeButton(formatMainActionLabel(null));
        mBtnPickMainAction.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 0);
        });
        content.addView(createLabelValueRow("Main Action", null, mBtnPickMainAction));

        // Super Gesture Specific Options
        mSuperGestureOptions = new LinearLayout(getContext());
        mSuperGestureOptions.setOrientation(VERTICAL);
        mSuperGestureOptions.setPadding(0, dp(6), 0, 0);

        mSuperGestureOptions.addView(createSectionLabel("Swipe Actions:"));
        mBtnPickSwipeLeft = createBadgeButton("Left: " + UNASSIGNED_LABEL);
        mBtnPickSwipeLeft.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 2);
        });
        mSuperGestureOptions.addView(createLabelValueRow("Left", null, mBtnPickSwipeLeft));

        mBtnPickSwipeRight = createBadgeButton("Right: " + UNASSIGNED_LABEL);
        mBtnPickSwipeRight.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 3);
        });
        mSuperGestureOptions.addView(createLabelValueRow("Right", null, mBtnPickSwipeRight));

        mBtnPickSwipeUp = createBadgeButton("Up: " + UNASSIGNED_LABEL);
        mBtnPickSwipeUp.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 4);
        });
        mSuperGestureOptions.addView(createLabelValueRow("Up", null, mBtnPickSwipeUp));

        mBtnPickSwipeDown = createBadgeButton("Down: " + UNASSIGNED_LABEL);
        mBtnPickSwipeDown.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 5);
        });
        mSuperGestureOptions.addView(createLabelValueRow("Down", null, mBtnPickSwipeDown));
        content.addView(mSuperGestureOptions);

        // TrackPoint specific options: a single button that cycles between
        // "MOUSE" (relative pointer motion) and "SCROLL" (wheel deltas).
        mTrackPointOptions = new LinearLayout(getContext());
        mTrackPointOptions.setOrientation(VERTICAL);
        mTrackPointOptions.setPadding(0, dp(6), 0, 0);
        mTrackPointOptions.addView(createSectionLabel("TrackPoint Mode:"));
        mBtnTrackPointMode = createBadgeButton("Mode: MOUSE");
        mBtnTrackPointMode.setOnClickListener(v -> {
            if (mActiveButton == null) return;
            String next = HudButton.MODE_MOUSE.equals(mActiveButton.trackpointMode)
                    ? HudButton.MODE_SCROLL
                    : HudButton.MODE_MOUSE;
            mActiveButton.trackpointMode = next;
            mBtnTrackPointMode.setText("Mode: " + next.toUpperCase());
            if (mCallback != null) mCallback.onModelChanged(mActiveButton);
        });
        mTrackPointOptions.addView(createLabelValueRow("Mode", null, mBtnTrackPointMode));
        content.addView(mTrackPointOptions);

        // Bottom Operations: Delete & Duplicate. Hidden for dock items since
        // they live in a fixed-size strip and don't have a per-button position
        // to delete/duplicate.
        // Bottom Operations: Delete & Duplicate. Styled with Material 12dp
        // corner radius and 16dp insets on all four sides so the pair sits
        // inside the panel instead of clinging to its borders.
        final LinearLayout opRow = new LinearLayout(getContext());
        opRow.setOrientation(HORIZONTAL);
        opRow.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams opLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        opLp.topMargin = dp(8);
        opRow.setLayoutParams(opLp);

        Button btnDelete = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        btnDelete.setText("DELETE");
        btnDelete.setAllCaps(true);
        btnDelete.setTextColor(0xFFF38BA8);
        btnDelete.setTextSize(12);
        android.graphics.drawable.GradientDrawable bgDel = new android.graphics.drawable.GradientDrawable();
        bgDel.setCornerRadius(dp(12));
        bgDel.setColor(0x33FF0000);
        btnDelete.setBackground(bgDel);
        btnDelete.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnDelete.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDelete.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onDeleteRequested(mActiveButton);
        });
        opRow.addView(btnDelete);

        Button btnDuplicate = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        btnDuplicate.setText("DUPLICATE");
        btnDuplicate.setAllCaps(true);
        btnDuplicate.setTextColor(Color.WHITE);
        btnDuplicate.setTextSize(12);
        android.graphics.drawable.GradientDrawable bgDup = new android.graphics.drawable.GradientDrawable();
        bgDup.setCornerRadius(dp(12));
        bgDup.setColor(0x33FFFFFF);
        btnDuplicate.setBackground(bgDup);
        btnDuplicate.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams dupLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dupLp.leftMargin = dp(12);
        btnDuplicate.setLayoutParams(dupLp);
        btnDuplicate.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onDuplicateRequested(mActiveButton);
        });
        opRow.addView(btnDuplicate);

        content.addView(opRow);
        scroll.addView(content);
        addView(scroll, new LayoutParams(dp(260), dp(340)));
        mOpRow = opRow;
    }

    public void bindButton(HudButton b) {
        this.mActiveButton = b;
        if (b == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mTitleText.setText(b.widgetType.toUpperCase() + " PROPERTIES");
        // Setting label text would otherwise re-trigger the TextWatcher and
        // call back into onModelChanged -> rebuildActiveLayout -> bindButton
        // in a tight loop. The flag is reset by syncSliderAndInput too, so
        // both blocks share the same suppression window.
        mIsProgrammaticChange = true;
        try {
            mLabelInput.setText(b.label != null ? b.label : "");
        } finally {
            mIsProgrammaticChange = false;
        }

        syncSliderAndInput(mWidthSeekBar, mWidthInput, b.widthDp, 20, 240);
        syncSliderAndInput(mHeightSeekBar, mHeightInput, b.heightDp, 20, 240);
        syncSliderAndInput(mCornerSeekBar, mCornerInput, b.cornerRadiusDp, 0, 50);
        syncSliderAndInput(mOpacitySeekBar, mOpacityInput, Math.round(b.opacity * 100), 10, 100);

        // Update the badges with the live values from the model.
        mBtnPickMainAction.setText(formatMainActionLabel(b));
        if (HudButton.WIDGET_SUPER_GESTURE.equals(b.widgetType)) {
            mSuperGestureOptions.setVisibility(VISIBLE);
            mBtnPickSwipeLeft.setText(swipeBadgeLabel(b.swipeLeftAction, "Left"));
            mBtnPickSwipeRight.setText(swipeBadgeLabel(b.swipeRightAction, "Right"));
            mBtnPickSwipeUp.setText(swipeBadgeLabel(b.swipeUpAction, "Up"));
            mBtnPickSwipeDown.setText(swipeBadgeLabel(b.swipeDownAction, "Down"));
        } else {
            mSuperGestureOptions.setVisibility(GONE);
        }
        boolean isTrackpoint = HudButton.WIDGET_TRACKPOINT.equals(b.widgetType);
        if (mTrackPointOptions != null) {
            mTrackPointOptions.setVisibility(isTrackpoint ? VISIBLE : GONE);
            if (isTrackpoint) {
                String mode = b.trackpointMode != null ? b.trackpointMode : HudButton.MODE_MOUSE;
                mBtnTrackPointMode.setText("Mode: " + mode.toUpperCase());
            }
        }
        // Floating buttons show size sliders and the delete/duplicate row.
        if (mSizeSection != null) mSizeSection.setVisibility(VISIBLE);
        if (mOpRow != null) mOpRow.setVisibility(VISIBLE);
    }

    // Slim inspector for a dock strip button: only the display label and the
    // action mapping matter, because dock items share a fixed size/opacity set
    // by the strip layout and cannot be deleted/duplicated from the strip.
    public void bindDockItem(HudButton b) {
        this.mActiveButton = b;
        if (b == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mTitleText.setText("DOCK BUTTON PROPERTIES");
        mIsProgrammaticChange = true;
        try {
            mLabelInput.setText(b.label != null ? b.label : "");
        } finally {
            mIsProgrammaticChange = false;
        }
        mBtnPickMainAction.setText(formatMainActionLabel(b));
        mSuperGestureOptions.setVisibility(GONE);
        if (mTrackPointOptions != null) mTrackPointOptions.setVisibility(GONE);
        if (mSizeSection != null) mSizeSection.setVisibility(GONE);
        if (mOpRow != null) mOpRow.setVisibility(GONE);
    }

    private interface ValueConsumer {
        void accept(int val);
    }

    private View createSliderRow(String title, SeekBar bar, EditText input, int min, int max, ValueConsumer consumer) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(getContext());
        t.setText(title);
        t.setTextColor(0xFFCCCCCC);
        t.setTextSize(12);
        t.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(t);
        top.addView(input);
        row.addView(top);

        bar.setMax(max - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int val = min + progress;
                    input.setText(String.valueOf(val));
                    consumer.accept(val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (mIsProgrammaticChange) return;
                try {
                    int val = Integer.parseInt(s.toString());
                    val = Math.max(min, Math.min(max, val));
                    bar.setProgress(val - min);
                    consumer.accept(val);
                } catch (Exception ignored) {}
            }
        });

        row.addView(bar);
        return row;
    }

    private void syncSliderAndInput(SeekBar bar, EditText input, int value, int min, int max) {
        int clamped = Math.max(min, Math.min(max, value));
        // Suppress the per-EditText / per-SeekBar listeners so they do not
        // re-enter the callback chain (the model already carries the same
        // value we are writing back to the views).
        mIsProgrammaticChange = true;
        try {
            bar.setProgress(clamped - min);
            input.setText(String.valueOf(clamped));
        } finally {
            mIsProgrammaticChange = false;
        }
    }

    private EditText createExactNumberInput() {
        EditText et = new EditText(getContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(0xFF80DEEA);
        et.setTextSize(12);
        et.setTypeface(Typeface.DEFAULT_BOLD);
        et.setBackgroundColor(0x22FFFFFF);
        et.setGravity(Gravity.CENTER);
        et.setPadding(dp(4), dp(2), dp(4), dp(2));
        et.setLayoutParams(new LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private Button createActionButton(String label) {
        // Keep the old name as alias to createBadgeButton for code that
        // still uses the wider button. New code should call
        // createBadgeButton directly.
        return createBadgeButton(label);
    }

    // Rounded badge-style button used for the action assignment rows.
    // Reads as "Key: SUPER" / "Type: COMBO" with a rounded background
    // that stands out against the dark panel. Text is left-aligned with
    // a 12dp padding-left so the label lines up with the title text
    // above it (Material Design body-text rhythm).
    private Button createBadgeButton(String label) {
        Button b = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setBackgroundColor(0xFF2A2B3D);
        // 12dp left padding + 6dp top/bottom + 12dp right padding keeps the
        // text left-aligned and the badge readable; gravity START so the
        // text always starts at the padding-left edge.
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1f;
        lp.setMargins(dp(8), dp(3), 0, dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    // Placeholder shown on a freshly added action slot, before the user
    // has picked a real key / combo. Spelled as a friendly sentence
    // fragment because we concatenate "Left: <placeholder>" etc.
    private static final String UNASSIGNED_LABEL = "Unassigned";

    // Build a left-aligned "Label - Value (Badge/EditText)" row. The label
    // sits at the start of the row; the second child (when provided)
    // stretches to fill the rest of the width.
    private LinearLayout createLabelValueRow(String label, View leftItem, View rightItem) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView l = new TextView(getContext());
        l.setText(label);
        l.setTextColor(0xFFCCCCCC);
        l.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(l, lp);

        if (leftItem != null) {
            LinearLayout.LayoutParams li = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            li.leftMargin = dp(8);
            row.addView(leftItem, li);
        }
        if (rightItem != null) {
            LinearLayout.LayoutParams ri = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ri.leftMargin = dp(8);
            row.addView(rightItem, ri);
        }
        return row;
    }

    // Empty left-side spacer used when the row only has a right-side
    // badge (so the badge takes the full row width).
    private View createLeftAlignedEditText() {
        View v = new View(getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
        return v;
    }

    private EditText createLabelEditText() {
        EditText et = new EditText(getContext());
        et.setTextColor(Color.WHITE);
        et.setTextSize(13);
        et.setBackgroundColor(0x22FFFFFF);
        et.setPadding(dp(8), dp(6), dp(8), dp(6));
        et.setSingleLine(true);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return et;
    }

    // Programmatic round dark background used by the panel's close button.
    // Keeps the look consistent with the no-emoji rule (no Unicode glyphs
    // or material icon dependency).
    private android.graphics.drawable.Drawable getRoundCloseBackground() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(0x33FFFFFF);
        return d;
    }

    // Format a HudAction as a short human-readable string for the action
    // badge. Falls back to "Unassigned" when nothing is bound yet.
    private String formatMainActionLabel(HudButton b) {
        if (b == null || b.action == null) return UNASSIGNED_LABEL;
        return formatActionBadge(b.action, null);
    }

    // Same as formatMainActionLabel but prefixes a direction token so
    // the four swipe rows read "Left: KEY" / "Right: Combo" etc.
    private String swipeBadgeLabel(HudAction a, String directionToken) {
        if (a == null) return directionToken + ": " + UNASSIGNED_LABEL;
        return formatActionBadge(a, directionToken);
    }

    private String formatActionBadge(HudAction a, String directionToken) {
        if (a == null) return directionToken != null
                ? (directionToken + ": " + UNASSIGNED_LABEL)
                : UNASSIGNED_LABEL;
        String prefix = directionToken != null ? (directionToken + ": ") : "";
        switch (a.type) {
            case HudAction.TYPE_KEY:
                return a.code > 0 ? (prefix + labelForEvdev(a.code)) : (prefix + UNASSIGNED_LABEL);
            case HudAction.TYPE_MODIFIER:
                return prefix + labelForEvdev(a.code);
            case HudAction.TYPE_COMBO:
                if (a.comboKeys == null || a.comboKeys.isEmpty())
                    return prefix + UNASSIGNED_LABEL;
                StringBuilder sb = new StringBuilder(prefix);
                for (int i = 0; i < a.comboKeys.size(); i++) {
                    if (i > 0) sb.append(" + ");
                    sb.append(labelForEvdev(a.comboKeys.get(i)));
                }
                return sb.toString();
            case HudAction.TYPE_TEXT:
                return a.text != null && !a.text.isEmpty() ? (prefix + "Text " + a.text) : (prefix + UNASSIGNED_LABEL);
            case HudAction.TYPE_SYSTEM:
                return prefix + "Sys " + (a.systemCommand != null ? a.systemCommand : UNASSIGNED_LABEL);
            default:
                return prefix + UNASSIGNED_LABEL;
        }
    }

    /**
     * Map a Linux evdev scancode to a short human label. The
     * ComboBuilderView produces keycodes from this same map so the
     * badge text and the live preview stay consistent. Unknown codes
     * fall back to "Key 125" so the user is never left wondering what
     * the numeric value is.
     */
    public static String labelForEvdev(int code) {
        switch (code) {
            case 29: return "CTRL";
            case 56: return "ALT";
            case 125: return "SUPER";
            case 42: return "SHIFT";
            case 1: return "ESC";
            case 15: return "TAB";
            case 28: return "ENTER";
            case 14: return "BKSP";
            case 111: return "DEL";
            case 57: return "SPACE";
            case 102: return "HOME";
            case 107: return "END";
            case 103: return "UP";
            case 108: return "DOWN";
            case 105: return "LEFT";
            case 106: return "RIGHT";
            case 104: return "PGUP";
            case 109: return "PGDN";
            case 30: return "A";
            case 48: return "B";
            case 46: return "C";
            case 32: return "D";
            case 18: return "E";
            case 33: return "F";
            case 34: return "G";
            case 35: return "H";
            case 23: return "I";
            case 36: return "J";
            case 37: return "K";
            case 38: return "L";
            case 50: return "M";
            case 49: return "N";
            case 24: return "O";
            case 25: return "P";
            case 16: return "Q";
            case 19: return "R";
            case 31: return "S";
            case 20: return "T";
            case 22: return "U";
            case 47: return "V";
            case 17: return "W";
            case 45: return "X";
            case 21: return "Y";
            case 44: return "Z";
            case 11: return "0";
            case 2: return "1";
            case 3: return "2";
            case 4: return "3";
            case 5: return "4";
            case 6: return "5";
            case 7: return "6";
            case 8: return "7";
            case 9: return "8";
            case 10: return "9";
            case 59: return "F1";
            case 60: return "F2";
            case 61: return "F3";
            case 62: return "F4";
            case 63: return "F5";
            case 64: return "F6";
            case 65: return "F7";
            case 66: return "F8";
            case 67: return "F9";
            case 68: return "F10";
            case 87: return "F11";
            case 88: return "F12";
            default: return "Key " + code;
        }
    }

    private TextView createSectionLabel(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(0xFFAAAAAA);
        t.setTextSize(11);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

    private int dp(int dpVal) {
        return Math.round(dpVal * getResources().getDisplayMetrics().density);
    }
}
