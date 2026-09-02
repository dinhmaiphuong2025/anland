package com.anland.consumer.hud;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Root Overlay View managing Execution Mode and Fullscreen HUD Editor Mode (PUBG/FreeFire style).
 * Handles separate Portrait/Landscape layouts, dynamic collision snapping, guidelines, and property inspector.
 */
public final class HudOverlayView extends FrameLayout implements IModifierProvider {

    public interface HudHost {
        void sendKey(int action, int evdevCode); // 0=down, 1=up
        void sendTextInput(byte[] utf8);
        void sendMouseMotion(float dx, float dy);
        void sendMouseButton(int button, boolean pressed);
        void sendMouseScroll(int axis, float value);
        void toggleSystemKeyboard();
        void toggleVirtualKeyboard();
        void openSettings();
        void onLayoutSaved();
    }

    private static final String PREF_KEY_PROFILE = "hud_layout_profile_v2";

    private final HudHost mHost;
    private final SharedPreferences mPrefs;
    private HudLayoutProfile mProfile;

    private boolean mIsEditMode = false;
    private HudButton mSelectedButton = null;
    private final List<HudButton> mActiveModifiers = new ArrayList<>();
    private View mSelectedView = null;

    private final SnapGeometryEngine mSnapEngine;
    private final Paint mGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSelectBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> mActiveVerticalGuides = new ArrayList<>();
    private final List<Float> mActiveHorizontalGuides = new ArrayList<>();

    // UI Sub-containers
    private FrameLayout mFloatingContainer;
    private HudDockStripView mDockStripView;
    private LinearLayout mTopToolbar;
    private HudPropertyInspectorView mInspectorView;

    private float mTouchDownX;
    private float mTouchDownY;
    private float mInitialBtnLeft;
    private float mInitialBtnTop;

    public HudOverlayView(Context context, SharedPreferences prefs, HudHost host) {
        super(context);
        this.mPrefs = prefs;
        if (!prefs.getBoolean("use_hud_overlay", false)) { setVisibility(GONE); } else { setVisibility(VISIBLE); }
        this.mHost = host;
        this.mSnapEngine = new SnapGeometryEngine(getResources().getDisplayMetrics().density);

        mGuidePaint.setColor(0xFF4CAF50); // Vibrant Green magnetic line
        mGuidePaint.setStrokeWidth(2f);
        mGuidePaint.setStyle(Paint.Style.STROKE);
        mGuidePaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        mSelectBorderPaint.setColor(0xFF80DEEA);
        mSelectBorderPaint.setStrokeWidth(3f);
        mSelectBorderPaint.setStyle(Paint.Style.STROKE);

        setWillNotDraw(false);
        loadProfile();
        initViews();
    }

    public void loadProfile() {
        String json = mPrefs.getString(PREF_KEY_PROFILE, null);
        mProfile = HudLayoutProfile.fromJSON(json);
    }

    public void saveProfile() {
        try {
            mPrefs.edit().putString(PREF_KEY_PROFILE, mProfile.toJSON().toString()).apply();
            if (mHost != null) mHost.onLayoutSaved();
            Toast.makeText(getContext(), "Layout Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error saving layout", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isEditMode() {
        return mIsEditMode;
    }

    public void setEditMode(boolean editMode) {
        this.mIsEditMode = editMode;
        if (editMode && !mProfile.firstTimeNoticeShown) {
            showFirstTimeNoticeDialog();
        }
        mTopToolbar.setVisibility(editMode ? VISIBLE : GONE);
        if (!editMode) {
            selectButton(null, null);
        }
        rebuildActiveLayout();
        invalidate();
    }

    private void showFirstTimeNoticeDialog() {
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("ORIENTATION NOTICE")
                .setMessage("Portrait and Landscape layouts are stored independently.\n\nRotate your device to customize buttons and dock positions for each orientation separately.")
                .setPositiveButton("GOT IT", (d, w) -> {
                    mProfile.firstTimeNoticeShown = true;
                    saveProfile();
                })
                .setCancelable(false)
                .show();
    }

    public HudLayout getActiveLayout() {
        int orientation = getResources().getConfiguration().orientation;
        return (orientation == Configuration.ORIENTATION_LANDSCAPE)
                ? mProfile.landscapeLayout : mProfile.portraitLayout;
    }

    private void initViews() {
        removeAllViews();

        mFloatingContainer = new FrameLayout(getContext());
        addView(mFloatingContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Dock Strip Container (Pinned at bottom)
        mDockStripView = new HudDockStripView(getContext(), getActiveLayout(), new HudDockStripView.DockActionListener() {
            @Override
            public void onDockItemClick(HudButton item) {
                if (!mIsEditMode) {
                    if (HudAction.TYPE_MODIFIER.equals(item.action.type)) {
                        if (mActiveModifiers.contains(item)) mActiveModifiers.remove(item);
                        else mActiveModifiers.add(item);
                        rebuildActiveLayout();
                    } else {
                        dispatchHudAction(item.action);
                    }
                }
            }
            @Override
            public void onDockItemLongPress(HudButton item) {
                if (!mIsEditMode && item.popupAction != null) {
                    dispatchHudAction(item.popupAction);
                }
            }
        });
        LayoutParams dockLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(getActiveLayout().dockHeightDp), Gravity.BOTTOM);
        addView(mDockStripView, dockLp);

        // Top Toolbar (Visible only in Edit Mode)
        mTopToolbar = createTopToolbar();
        mTopToolbar.setVisibility(GONE);
        addView(mTopToolbar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // Property Inspector Panel (Floating, initially hidden)
        mInspectorView = new HudPropertyInspectorView(getContext(), new HudPropertyInspectorView.InspectorCallback() {
            @Override
            public void onModelChanged(HudButton button) {
                rebuildActiveLayout();
            }
            @Override
            public void onDeleteRequested(HudButton button) {
                getActiveLayout().floatingButtons.remove(button);
                selectButton(null, null);
                rebuildActiveLayout();
            }
            @Override
            public void onDuplicateRequested(HudButton button) {
                HudButton dup = button.duplicate();
                getActiveLayout().floatingButtons.add(dup);
                rebuildActiveLayout();
            }
            @Override
            public void onPickActionRequested(HudButton button, int targetSlot) {
                HudKeyPickerDialog.show(getContext(), (action, displayLabel) -> {
                    if (targetSlot == 0) {
                        button.action = action;
                        button.label = displayLabel;
                    } else if (targetSlot == 2) button.swipeLeftAction = action;
                    else if (targetSlot == 3) button.swipeRightAction = action;
                    else if (targetSlot == 4) button.swipeUpAction = action;
                    else if (targetSlot == 5) button.swipeDownAction = action;
                    mInspectorView.bindButton(button);
                    rebuildActiveLayout();
                });
            }
            @Override
            public void onCloseRequested() {
                selectButton(null, null);
            }
        });
        mInspectorView.setVisibility(GONE);
        addView(mInspectorView, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rebuildActiveLayout();
    }

    private LinearLayout createTopToolbar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xF0181825);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));

        Button btnSave = createToolButton("[SAVE]");
        btnSave.setTextColor(0xFF80DEEA);
        btnSave.setOnClickListener(v -> saveProfile());
        bar.addView(btnSave);

        Button btnAdd = createToolButton("[+ BUTTON]");
        btnAdd.setOnClickListener(v -> {
            HudButton b = new HudButton();
            b.label = "KEY";
            b.posXPercent = 0.5f;
            b.posYPercent = 0.4f;
            b.action = HudAction.key(1);
            getActiveLayout().floatingButtons.add(b);
            rebuildActiveLayout();
            selectButton(b, null);
        });
        bar.addView(btnAdd);

        Button btnAddGesture = createToolButton("[+ GESTURE]");
        btnAddGesture.setOnClickListener(v -> {
            HudButton b = new HudButton();
            b.widgetType = HudButton.WIDGET_SUPER_GESTURE;
            b.label = "SUPER";
            b.widthDp = 58;
            b.heightDp = 58;
            b.cornerRadiusDp = 29;
            b.posXPercent = 0.5f;
            b.posYPercent = 0.5f;
            getActiveLayout().floatingButtons.add(b);
            rebuildActiveLayout();
            selectButton(b, null);
        });
        bar.addView(btnAddGesture);

        Button btnAddTrackpoint = createToolButton("[+ MOUSE]");
        btnAddTrackpoint.setOnClickListener(v -> {
            HudButton b = new HudButton();
            b.widgetType = HudButton.WIDGET_TRACKPOINT;
            b.label = "MOUSE";
            b.widthDp = 60;
            b.heightDp = 60;
            b.cornerRadiusDp = 30;
            b.posXPercent = 0.2f;
            b.posYPercent = 0.5f;
            getActiveLayout().floatingButtons.add(b);
            rebuildActiveLayout();
            selectButton(b, null);
        });
        bar.addView(btnAddTrackpoint);

        View spacer = new View(getContext());
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        Button btnExit = createToolButton("[EXIT EDIT]");
        btnExit.setTextColor(0xFFF38BA8);
        btnExit.setOnClickListener(v -> setEditMode(false));
        bar.addView(btnExit);

        return bar;
    }

    private Button createToolButton(String label) {
        Button b = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(6), dp(4), dp(6), dp(4));
        return b;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildActiveLayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            HudLayout layout = getActiveLayout();
            for (int i = 0; i < mFloatingContainer.getChildCount(); i++) {
                View view = mFloatingContainer.getChildAt(i);
                if (i < layout.floatingButtons.size()) {
                    HudButton model = layout.floatingButtons.get(i);
                    float density = getResources().getDisplayMetrics().density;
                    int bw = Math.round(model.widthDp * density);
                    int bh = Math.round(model.heightDp * density);
                    view.setX(model.posXPercent * w - bw * 0.5f);
                    view.setY(model.posYPercent * h - bh * 0.5f);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsEditMode) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                selectButton(null, null);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean hasActiveModifier() {
        return mActiveModifiers != null && !mActiveModifiers.isEmpty();
    }

    @Override
    public void sendKeyComboFromExternal(int evdevScancode) {
        if (mHost != null) {
            for (HudButton mod : mActiveModifiers) mHost.sendKey(0, mod.action.code);
            mHost.sendKey(0, evdevScancode);
            mHost.sendKey(1, evdevScancode);
            for (int i = mActiveModifiers.size() - 1; i >= 0; i--) mHost.sendKey(1, mActiveModifiers.get(i).action.code);
            mActiveModifiers.clear();
            rebuildActiveLayout();
        }
    }

    @Override
    public void reset() {
        mActiveModifiers.clear();
        rebuildActiveLayout();
    }

    public void rebuildActiveLayout() {
        mFloatingContainer.removeAllViews();
        HudLayout layout = getActiveLayout();
        mDockStripView.rebuildItems(mActiveModifiers);

        for (HudButton b : layout.floatingButtons) {
            View widgetView;
            if (HudButton.WIDGET_TRACKPOINT.equals(b.widgetType)) {
                widgetView = new TrackpointNubView(getContext(), b, new TrackpointNubView.MotionDispatcher() {
                    @Override
                    public void onPointerMove(float dx, float dy) {
                        if (!mIsEditMode && mHost != null) mHost.sendMouseMotion(dx, dy);
                    }
                    @Override
                    public void onPointerClick(int button) {
                        if (!mIsEditMode && mHost != null) {
                            mHost.sendMouseButton(button, true);
                            postDelayed(() -> mHost.sendMouseButton(button, false), 50);
                        }
                    }
                });
            } else if (HudButton.WIDGET_SUPER_GESTURE.equals(b.widgetType)) {
                widgetView = new SuperGestureButtonView(getContext(), b, new SuperGestureButtonView.GestureListener() {
                    @Override
                    public void onSingleTap(HudButton btn) {
                        if (!mIsEditMode) dispatchHudAction(btn.action);
                    }
                    @Override
                    public void onLongPress(HudButton btn) {
                        if (!mIsEditMode) dispatchHudAction(btn.action);
                    }
                    @Override
                    public void onSwipe(HudButton btn, int direction) {
                        if (mIsEditMode) return;
                        if (direction == SuperGestureButtonView.DIR_LEFT) dispatchHudAction(btn.swipeLeftAction);
                        else if (direction == SuperGestureButtonView.DIR_RIGHT) dispatchHudAction(btn.swipeRightAction);
                        else if (direction == SuperGestureButtonView.DIR_UP) dispatchHudAction(btn.swipeUpAction);
                        else if (direction == SuperGestureButtonView.DIR_DOWN) dispatchHudAction(btn.swipeDownAction);
                    }
                });
            } else {
                widgetView = new HudFreeformButtonView(getContext(), b, mActiveModifiers.contains(b), new HudFreeformButtonView.ButtonActionListener() {
                    @Override
                    public void onButtonPress(HudButton btn, boolean isDown) {
                        if (!mIsEditMode) {
                            if (HudAction.TYPE_MODIFIER.equals(btn.action.type)) {
                                if (isDown) {
                                    if (mActiveModifiers.contains(btn)) mActiveModifiers.remove(btn);
                                    else mActiveModifiers.add(btn);
                                    rebuildActiveLayout();
                                }
                            } else if (HudAction.TYPE_KEY.equals(btn.action.type)) {
                                if (mHost != null) mHost.sendKey(isDown ? 0 : 1, btn.action.code);
                            } else if (isDown) {
                                dispatchHudAction(btn.action);
                            }
                        }
                    }
                    @Override
                    public void onPopupTrigger(HudButton btn) {
                        if (!mIsEditMode && btn.popupAction != null) {
                            dispatchHudAction(btn.popupAction);
                        }
                    }
                });
            }

            attachTouchAndLayout(widgetView, b);
            mFloatingContainer.addView(widgetView);
        }
        invalidate();
    }

    private void attachTouchAndLayout(View view, HudButton model) {
        float density = getResources().getDisplayMetrics().density;
        int w = Math.round(model.widthDp * density);
        int h = Math.round(model.heightDp * density);

        view.setLayoutParams(new LayoutParams(w, h));
        post(() -> {
            int parentW = getWidth();
            int parentH = getHeight();
            if (parentW > 0 && parentH > 0) {
                view.setX(model.posXPercent * parentW - w * 0.5f);
                view.setY(model.posYPercent * parentH - h * 0.5f);
            }
        });

        view.setOnTouchListener((v, event) -> {
            if (!mIsEditMode) return false;

            float rawX = event.getRawX();
            float rawY = event.getRawY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    selectButton(model, view);
                    mTouchDownX = rawX;
                    mTouchDownY = rawY;
                    mInitialBtnLeft = view.getX();
                    mInitialBtnTop = view.getY();
                    mActiveVerticalGuides.clear();
                    mActiveHorizontalGuides.clear();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float targetX = mInitialBtnLeft + (rawX - mTouchDownX);
                    float targetY = mInitialBtnTop + (rawY - mTouchDownY);

                    RectF candidate = new RectF(targetX, targetY, targetX + view.getWidth(), targetY + view.getHeight());
                    List<RectF> siblings = getSiblingBounds(model);

                    float dockTop = (mDockStripView.getVisibility() == VISIBLE) ? mDockStripView.getY() : getHeight();
                    SnapGeometryEngine.SnapResult snap = mSnapEngine.computeSnap(candidate, siblings, getWidth(), getHeight(), dockTop);

                    view.setX(snap.snappedX);
                    view.setY(snap.snappedY);

                    model.posXPercent = (snap.snappedX + view.getWidth() * 0.5f) / getWidth();
                    model.posYPercent = (snap.snappedY + view.getHeight() * 0.5f) / getHeight();

                    mActiveVerticalGuides.clear();
                    mActiveVerticalGuides.addAll(snap.verticalGuidelines);
                    mActiveHorizontalGuides.clear();
                    mActiveHorizontalGuides.addAll(snap.horizontalGuidelines);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mActiveVerticalGuides.clear();
                    mActiveHorizontalGuides.clear();
                    invalidate();
                    return true;
            }
            return false;
        });
    }

    private List<RectF> getSiblingBounds(HudButton exclude) {
        List<RectF> list = new ArrayList<>();
        HudLayout layout = getActiveLayout();
        for (int i = 0; i < mFloatingContainer.getChildCount(); i++) {
            View child = mFloatingContainer.getChildAt(i);
            if (i < layout.floatingButtons.size()) {
                HudButton b = layout.floatingButtons.get(i);
                if (b != exclude) {
                    list.add(new RectF(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight()));
                }
            }
        }
        return list;
    }

    private void selectButton(HudButton b, View v) {
        this.mSelectedButton = b;
        this.mSelectedView = v;
        mInspectorView.bindButton(b);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mIsEditMode) {
            // Draw magnetic guidelines
            for (float x : mActiveVerticalGuides) {
                canvas.drawLine(x, 0, x, getHeight(), mGuidePaint);
            }
            for (float y : mActiveHorizontalGuides) {
                canvas.drawLine(0, y, getWidth(), y, mGuidePaint);
            }

            // Draw bounding border around selected view
            if (mSelectedView != null) {
                float density = getResources().getDisplayMetrics().density;
                RectF r = new RectF(mSelectedView.getX() - 3 * density,
                        mSelectedView.getY() - 3 * density,
                        mSelectedView.getX() + mSelectedView.getWidth() + 3 * density,
                        mSelectedView.getY() + mSelectedView.getHeight() + 3 * density);
                canvas.drawRoundRect(r, 6 * density, 6 * density, mSelectBorderPaint);
            }
        }
    }

    private void dispatchHudAction(HudAction action) {
        if (action == null || mHost == null) return;
        if (HudAction.TYPE_KEY.equals(action.type)) {
            for (HudButton mod : mActiveModifiers) mHost.sendKey(0, mod.action.code);
            mHost.sendKey(0, action.code);
            postDelayed(() -> { mHost.sendKey(1, action.code); for (int i = mActiveModifiers.size() - 1; i >= 0; i--) mHost.sendKey(1, mActiveModifiers.get(i).action.code); mActiveModifiers.clear(); rebuildActiveLayout(); }, 40);
        } else if (HudAction.TYPE_MODIFIER.equals(action.type)) {
            for (HudButton mod : mActiveModifiers) mHost.sendKey(0, mod.action.code);
            mHost.sendKey(0, action.code);
            postDelayed(() -> { mHost.sendKey(1, action.code); for (int i = mActiveModifiers.size() - 1; i >= 0; i--) mHost.sendKey(1, mActiveModifiers.get(i).action.code); mActiveModifiers.clear(); rebuildActiveLayout(); }, 40);
        } else if (HudAction.TYPE_COMBO.equals(action.type)) {
            for (HudButton mod : mActiveModifiers) mHost.sendKey(0, mod.action.code);
            for (int k : action.comboKeys) mHost.sendKey(0, k);
            postDelayed(() -> {
                for (int i = action.comboKeys.size() - 1; i >= 0; i--) {
                    mHost.sendKey(1, action.comboKeys.get(i));
                }
                for (int i = mActiveModifiers.size() - 1; i >= 0; i--) mHost.sendKey(1, mActiveModifiers.get(i).action.code);
                mActiveModifiers.clear();
                rebuildActiveLayout();
            }, 50);
        } else if (HudAction.TYPE_TEXT.equals(action.type)) {
            mHost.sendTextInput(action.text.getBytes());
        } else if (HudAction.TYPE_SYSTEM.equals(action.type)) {
            if ("toggle_ime".equals(action.systemCommand)) mHost.toggleSystemKeyboard();
            else if ("toggle_vk".equals(action.systemCommand)) mHost.toggleVirtualKeyboard();
            else if ("open_settings".equals(action.systemCommand)) mHost.openSettings();
            else if ("mouse_left".equals(action.systemCommand)) {
                mHost.sendMouseButton(1, true);
                postDelayed(() -> mHost.sendMouseButton(1, false), 40);
            } else if ("mouse_right".equals(action.systemCommand)) {
                mHost.sendMouseButton(2, true);
                postDelayed(() -> mHost.sendMouseButton(2, false), 40);
            }
        }
    }

    public void applyImeBottom(int imeBottom) {
        if (mDockStripView != null) {
            mDockStripView.setTranslationY(-imeBottom);
        }
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
