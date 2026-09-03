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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        // True when the daemon socket is live and the native pipeline is
        // connected. False during editor-only mode when the daemon is offline.
        boolean isNativeReady();
    }

    private static final String PREF_KEY_PROFILE = "hud_layout_profile_v2";

    private final HudHost mHost;
    private final SharedPreferences mPrefs;
    private HudLayoutProfile mProfile;

    private boolean mIsEditMode = false;
    private HudButton mSelectedButton = null;
    private View mSelectedView = null;

    private final SnapGeometryEngine mSnapEngine;
    private final Paint mGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSelectBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBannerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBannerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    // Modifier latch state (same semantics as ExtraKeysBar: tap = toggle, long-press = lock)
    private static final class ModState {
        boolean active;
        boolean locked;
        int evdev;
    }
    private final Map<Integer, ModState> mModifiers = new LinkedHashMap<>();

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

        mGridPaint.setColor(0x22FFFFFF);
        mGridPaint.setStrokeWidth(1f);
        mGridPaint.setStyle(Paint.Style.STROKE);

        mBannerTextPaint.setColor(Color.WHITE);
        mBannerTextPaint.setFakeBoldText(true);
        mBannerTextPaint.setTextAlign(Paint.Align.CENTER);

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
        if (editMode) {
            setVisibility(VISIBLE);
        }
        if (editMode && !mProfile.firstTimeNoticeShown) {
            showFirstTimeNoticeDialog();
        }
        mTopToolbar.setVisibility(editMode ? VISIBLE : GONE);
        if (mDockStripView != null) mDockStripView.setEditMode(editMode);
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
        setClipChildren(false);
        setClipToPadding(false);

        mFloatingContainer = new FrameLayout(getContext());
        mFloatingContainer.setClipChildren(false);
        mFloatingContainer.setClipToPadding(false);
        addView(mFloatingContainer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Dock Strip Container (Pinned at bottom)
        mDockStripView = new HudDockStripView(getContext(), getActiveLayout(), new HudDockStripView.DockActionListener() {
            @Override
            public void onDockItemClick(HudButton item) {
                if (mIsEditMode) {
                    onDockItemSelectedForEdit(item);
                    return;
                }
                dispatchHudAction(item.action);
            }
            @Override
            public void onDockItemLongPress(HudButton item) {
                if (mIsEditMode) {
                    // Long-press in edit mode is a fast-path to the key picker
                    // (mirrors the freeform button's main action).
                    onDockItemActionPickedForEdit(item);
                    return;
                }
                if (item.popupAction != null) {
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

    // When the user taps a dock item in edit mode, open the property inspector
    // for it so they can rebind the key. Dock items are stored in HudLayout as
    // a List<HudButton> alongside floatingButtons, but live in their own dock
    // strip view; we synthesize a "selected" feeling by passing the HudButton
    // straight to the inspector.
    private void onDockItemSelectedForEdit(HudButton item) {
        mSelectedButton = item;
        mSelectedView = null;
        mInspectorView.bindDockItem(item);
        invalidate();
    }

    // Long-press on a dock item in edit mode opens the key picker directly,
    // skipping the inspector entirely (common case is "I just want to change
    // what this key sends").
    private void onDockItemActionPickedForEdit(HudButton item) {
        HudKeyPickerDialog.show(getContext(), (action, displayLabel) -> {
            item.action = action;
            item.label = displayLabel;
            mDockStripView.rebuildItems();
        });
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

    // ---- IModifierProvider: modifier latch (same semantics as ExtraKeysBar) ----

    @Override
    public boolean hasActiveModifier() {
        for (ModState m : mModifiers.values())
            if (m.active) return true;
        return false;
    }

    @Override
    public void sendKeyComboFromExternal(int evdevScancode) {
        sendWithModifiers(() -> {
            if (mHost != null) {
                mHost.sendKey(0, evdevScancode);
                mHost.sendKey(1, evdevScancode);
            }
        });
    }

    @Override
    public void reset() {
        for (ModState m : mModifiers.values()) {
            if (m.active && mHost != null) mHost.sendKey(1, m.evdev);
            m.active = false;
            m.locked = false;
        }
    }

    private void toggleModifier(int evdev) {
        ModState state = mModifiers.get(evdev);
        if (state == null) {
            state = new ModState();
            state.evdev = evdev;
            mModifiers.put(evdev, state);
        }
        setModifierActive(state, !state.active);
        if (!state.active) state.locked = false;
    }

    private void setModifierActive(ModState state, boolean active) {
        state.active = active;
        if (!active) state.locked = false;
        if (mDockStripView != null) {
            mDockStripView.setModifierActiveState(state.evdev, active);
        }
    }

    private void sendWithModifiers(Runnable emit) {
        List<ModState> held = new ArrayList<>();
        for (ModState m : mModifiers.values())
            if (m.active) held.add(m);

        for (ModState m : held) {
            if (mHost != null) mHost.sendKey(0, m.evdev);
        }
        emit.run();
        for (int i = held.size() - 1; i >= 0; i--) {
            if (mHost != null) mHost.sendKey(1, held.get(i).evdev);
        }

        // Auto-release modifiers that aren't locked
        for (ModState m : held) {
            if (!m.locked) setModifierActive(m, false);
        }
    }

    public void rebuildActiveLayout() {
        mFloatingContainer.removeAllViews();
        HudLayout layout = getActiveLayout();
        mDockStripView.rebuildItems();

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
                ((TrackpointNubView) widgetView).setInEditMode(mIsEditMode);
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
                ((SuperGestureButtonView) widgetView).setInEditMode(mIsEditMode);
            } else {
                widgetView = new HudFreeformButtonView(getContext(), b, new HudFreeformButtonView.ButtonActionListener() {
                    @Override
                    public void onButtonPress(HudButton btn, boolean isDown) {
                        if (!mIsEditMode) {
                            if (HudAction.TYPE_MODIFIER.equals(btn.action.type)) {
                                if (isDown) toggleModifier(btn.action.code);
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
                ((HudFreeformButtonView) widgetView).setInEditMode(mIsEditMode);
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
            float density = getResources().getDisplayMetrics().density;
            float w = getWidth();
            float h = getHeight();

            // 1. Semi-transparent dark scrim overlay (backdrop)
            canvas.drawColor(0xBB11111B);

            // 2. Blueprint subtle grid (32dp steps)
            float step = 32f * density;
            for (float gx = step; gx < w; gx += step) {
                canvas.drawLine(gx, 0, gx, h, mGridPaint);
            }
            for (float gy = step; gy < h; gy += step) {
                canvas.drawLine(0, gy, w, gy, mGridPaint);
            }

            // 3. Draw magnetic guidelines
            for (float x : mActiveVerticalGuides) {
                canvas.drawLine(x, 0, x, h, mGuidePaint);
            }
            for (float y : mActiveHorizontalGuides) {
                canvas.drawLine(0, y, w, y, mGuidePaint);
            }

            // 4. Draw bounding border around selected view
            if (mSelectedView != null) {
                RectF r = new RectF(mSelectedView.getX() - 3 * density,
                        mSelectedView.getY() - 3 * density,
                        mSelectedView.getX() + mSelectedView.getWidth() + 3 * density,
                        mSelectedView.getY() + mSelectedView.getHeight() + 3 * density);
                canvas.drawRoundRect(r, 6 * density, 6 * density, mSelectBorderPaint);
            }

            // 5. Bottom-center daemon status banner (only when we know the host)
            if (mHost != null) {
                drawDaemonBanner(canvas, w, h, density);
            }
        }
    }

    private void drawDaemonBanner(Canvas canvas, float w, float h, float density) {
        boolean nativeReady = mHost.isNativeReady();
        // The banner sits a few dp above the dock strip so it does not overlap
        // with the existing buttons. We measure the strip's height dynamically
        // to keep the gap consistent.
        int dockHeight = mDockStripView != null ? mDockStripView.getHeight() : 0;
        float bannerH = 38f * density;
        float bannerW = Math.min(w - 40f * density, 420f * density);
        float left = (w - bannerW) / 2f;
        float top = h - dockHeight - bannerH - 16f * density;
        if (top < 16f * density) top = 16f * density;

        mBannerFillPaint.setColor(nativeReady ? 0xCC2E7D32 : 0xCCE53935);
        canvas.drawRoundRect(left, top, left + bannerW, top + bannerH,
                              8f * density, 8f * density, mBannerFillPaint);

        mBannerTextPaint.setTextSize(14f * density);
        float textY = top + bannerH / 2f -
            ((mBannerTextPaint.descent() + mBannerTextPaint.ascent()) * 0.5f);
        String msg = nativeReady
            ? "DAEMON READY  ·  TAP EXIT EDIT TO USE DESKTOP"
            : "DAEMON OFFLINE  ·  EDITOR MODE ONLY";
        canvas.drawText(msg, left + bannerW / 2f, textY, mBannerTextPaint);
    }

    // MainActivity calls this whenever the native pipeline state may have
    // changed (onFallback, startNative success, surfaceChanged). It just
    // invalidates; the banner text is recomputed inside onDraw.
    public void refreshEditModeBanner() {
        if (mIsEditMode) invalidate();
    }

    private void dispatchHudAction(HudAction action) {
        if (action == null || mHost == null) return;
        // In edit mode, all action dispatch is suspended. The dock strip and
        // freeform buttons only serve as drag-to-move handles; the user picks
        // a button to rebind it via the property inspector. Without this guard
        // tapping ESC/Tab/Super in edit mode would still send real keys to
        // the desktop, surprising the user mid-design.
        if (mIsEditMode) return;
        if (HudAction.TYPE_KEY.equals(action.type)) {
            sendWithModifiers(() -> {
                if (mHost != null) {
                    mHost.sendKey(0, action.code);
                    mHost.sendKey(1, action.code);
                }
            });
        } else if (HudAction.TYPE_MODIFIER.equals(action.type)) {
            toggleModifier(action.code);
        } else if (HudAction.TYPE_COMBO.equals(action.type)) {
            for (int k : action.comboKeys) mHost.sendKey(0, k);
            postDelayed(() -> {
                for (int i = action.comboKeys.size() - 1; i >= 0; i--) {
                    mHost.sendKey(1, action.comboKeys.get(i));
                }
            }, 50);
        } else if (HudAction.TYPE_TEXT.equals(action.type)) {
            sendWithModifiers(() -> {
                if (mHost != null) {
                    mHost.sendTextInput(action.text.getBytes());
                }
            });
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
