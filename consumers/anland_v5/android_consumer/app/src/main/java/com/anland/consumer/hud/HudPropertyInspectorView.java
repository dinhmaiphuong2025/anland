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

    private Button mBtnPickMainAction;
    private LinearLayout mSuperGestureOptions;
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
        dragHandle.setText(":: DRAG PANEL ::");
        dragHandle.setTextColor(0xFF80DEEA);
        dragHandle.setTextSize(12);
        dragHandle.setTypeface(Typeface.DEFAULT_BOLD);
        dragHandle.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(dragHandle);

        Button btnClose = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        btnClose.setText("[X]");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(12);
        btnClose.setPadding(dp(6), 0, dp(6), 0);
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
        content.addView(createSectionLabel("Display Label:"));
        mLabelInput = new EditText(getContext());
        mLabelInput.setTextColor(Color.WHITE);
        mLabelInput.setTextSize(13);
        mLabelInput.setBackgroundColor(0x22FFFFFF);
        mLabelInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        mLabelInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (mActiveButton != null && !s.toString().equals(mActiveButton.label)) {
                    mActiveButton.label = s.toString();
                    if (mCallback != null) mCallback.onModelChanged(mActiveButton);
                }
            }
        });
        content.addView(mLabelInput);

        // Sliders with exact Numeric Edit Fields
        mWidthSeekBar = new SeekBar(getContext());
        mWidthInput = createExactNumberInput();
        content.addView(createSliderRow("Width (dp):", mWidthSeekBar, mWidthInput, 20, 240, val -> {
            if (mActiveButton != null) {
                mActiveButton.widthDp = val;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        mHeightSeekBar = new SeekBar(getContext());
        mHeightInput = createExactNumberInput();
        content.addView(createSliderRow("Height (dp):", mHeightSeekBar, mHeightInput, 20, 240, val -> {
            if (mActiveButton != null) {
                mActiveButton.heightDp = val;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        mCornerSeekBar = new SeekBar(getContext());
        mCornerInput = createExactNumberInput();
        content.addView(createSliderRow("Corner Radius (dp):", mCornerSeekBar, mCornerInput, 0, 50, val -> {
            if (mActiveButton != null) {
                mActiveButton.cornerRadiusDp = val;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        mOpacitySeekBar = new SeekBar(getContext());
        mOpacityInput = createExactNumberInput();
        content.addView(createSliderRow("Opacity (%):", mOpacitySeekBar, mOpacityInput, 10, 100, val -> {
            if (mActiveButton != null) {
                mActiveButton.opacity = val / 100f;
                if (mCallback != null) mCallback.onModelChanged(mActiveButton);
            }
        }));

        // Action Assignment
        content.addView(createSectionLabel("Action Mapping:"));
        mBtnPickMainAction = createActionButton("[ CHANGE ACTION / KEY ]");
        mBtnPickMainAction.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 0);
        });
        content.addView(mBtnPickMainAction);

        // Super Gesture Specific Options
        mSuperGestureOptions = new LinearLayout(getContext());
        mSuperGestureOptions.setOrientation(VERTICAL);
        mSuperGestureOptions.setPadding(0, dp(6), 0, 0);

        mSuperGestureOptions.addView(createSectionLabel("Swipe Actions:"));
        mBtnPickSwipeLeft = createActionButton("Swipe Left: (Change)");
        mBtnPickSwipeLeft.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 2);
        });
        mSuperGestureOptions.addView(mBtnPickSwipeLeft);

        mBtnPickSwipeRight = createActionButton("Swipe Right: (Change)");
        mBtnPickSwipeRight.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 3);
        });
        mSuperGestureOptions.addView(mBtnPickSwipeRight);

        mBtnPickSwipeUp = createActionButton("Swipe Up: (Change)");
        mBtnPickSwipeUp.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 4);
        });
        mSuperGestureOptions.addView(mBtnPickSwipeUp);

        mBtnPickSwipeDown = createActionButton("Swipe Down: (Change)");
        mBtnPickSwipeDown.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onPickActionRequested(mActiveButton, 5);
        });
        mSuperGestureOptions.addView(mBtnPickSwipeDown);
        content.addView(mSuperGestureOptions);

        // Bottom Operations: Delete & Duplicate
        LinearLayout opRow = new LinearLayout(getContext());
        opRow.setOrientation(HORIZONTAL);
        opRow.setPadding(0, dp(12), 0, 0);

        Button btnDelete = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        btnDelete.setText("DELETE");
        btnDelete.setTextColor(0xFFF38BA8);
        btnDelete.setBackgroundColor(0x33FF0000);
        btnDelete.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDelete.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onDeleteRequested(mActiveButton);
        });
        opRow.addView(btnDelete);

        Button btnDuplicate = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        btnDuplicate.setText("DUPLICATE");
        btnDuplicate.setTextColor(Color.WHITE);
        btnDuplicate.setBackgroundColor(0x33FFFFFF);
        btnDuplicate.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDuplicate.setOnClickListener(v -> {
            if (mCallback != null && mActiveButton != null) mCallback.onDuplicateRequested(mActiveButton);
        });
        opRow.addView(btnDuplicate);

        content.addView(opRow);
        scroll.addView(content);
        addView(scroll, new LayoutParams(dp(260), dp(340)));
    }

    public void bindButton(HudButton b) {
        this.mActiveButton = b;
        if (b == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mTitleText.setText(b.widgetType.toUpperCase() + " PROPERTIES");
        mLabelInput.setText(b.label != null ? b.label : "");

        syncSliderAndInput(mWidthSeekBar, mWidthInput, b.widthDp, 20, 240);
        syncSliderAndInput(mHeightSeekBar, mHeightInput, b.heightDp, 20, 240);
        syncSliderAndInput(mCornerSeekBar, mCornerInput, b.cornerRadiusDp, 0, 50);
        syncSliderAndInput(mOpacitySeekBar, mOpacityInput, Math.round(b.opacity * 100), 10, 100);

        mBtnPickMainAction.setText("Action: " + b.action.type.toUpperCase() + " (" + (b.action.code > 0 ? b.action.code : "") + ")");
        mSuperGestureOptions.setVisibility(HudButton.WIDGET_SUPER_GESTURE.equals(b.widgetType) ? VISIBLE : GONE);
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
        bar.setProgress(clamped - min);
        input.setText(String.valueOf(clamped));
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
        Button b = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setBackgroundColor(0xFF2A2B3D);
        b.setPadding(dp(6), dp(4), dp(6), dp(4));
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        b.setLayoutParams(lp);
        return b;
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
