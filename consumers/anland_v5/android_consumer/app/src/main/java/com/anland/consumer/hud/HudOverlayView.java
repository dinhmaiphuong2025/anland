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
        // Notified when the user finishes HUD edit mode (via [EXIT EDIT] or
        // back-press). The host can use this to clear transient flags that
        // were set when the editor was opened, so a stray daemon-down event
        // fired right after exit does not bounce the user out of the app.
        void onEditModeExited();
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
    // Self-edge guides are drawn in a different color so the user can
    // distinguish "the canvas edge my button snapped to" (green, sibling)
    // from "the edge of the button I am dragging" (cyan, self).
    private final Paint mSelfGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSelectBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBannerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBannerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> mActiveVerticalGuides = new ArrayList<>();
    private final List<Float> mActiveHorizontalGuides = new ArrayList<>();
    // Self-edge guides (cạnh nút đang kéo). Tách riêng để vẽ khác màu với
    // sibling guides (cạnh của nút khác trên màn hình).
    private final List<Float> mActiveSelfVerticalGuides = new ArrayList<>();
    private final List<Float> mActiveSelfHorizontalGuides = new ArrayList<>();

    // UI Sub-containers
    private FrameLayout mFloatingContainer;
    // Dock strip removed. The legacy ExtraKeysBar is the bottom row now.
    // HudLayout.dockItems is still in the model for JSON backward-compat,
    // but no view renders it. See HudLayoutProfile for the migration note.
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

        mSelfGuidePaint.setColor(0xFF80DEEA); // Cyan: cạnh của nút đang kéo
        mSelfGuidePaint.setStrokeWidth(2.5f);
        mSelfGuidePaint.setStyle(Paint.Style.STROKE);
        mSelfGuidePaint.setPathEffect(new DashPathEffect(new float[]{4, 4}, 0));

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
        if (!editMode) {
            selectButton(null, null);
            // Tell the host we are leaving the editor so it can clear the
            // editor-only flags. If we do not, a deferred-finish timer
            // armed for the editor path can fire after the user is already
            // back in the production view, killing the activity and
            // creating the "ghost tab" the user reported.
            if (mHost != null) mHost.onEditModeExited();
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

        // Dock strip removed. The legacy ExtraKeysBar is the bottom row now.
        // HudLayout.dockItems is still in the model for backward-compat JSON,
        // but the strip view itself is no longer created or added.

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
                // Swipe action slots (target 2..5) get the free-form combo
                // builder so the user can pick up to three keys. The main
                // action slot (target 0) and the popup (target 1) stay on
                // the predefined key grid.
                if (targetSlot >= 2) {
                    new ComboBuilderView().showDialog(getContext(), combo -> {
                        switch (targetSlot) {
                            case 2: button.swipeLeftAction = combo; break;
                            case 3: button.swipeRightAction = combo; break;
                            case 4: button.swipeUpAction = combo; break;
                            case 5: button.swipeDownAction = combo; break;
                        }
                        mInspectorView.bindButton(button);
                        rebuildActiveLayout();
                    });
                } else {
                    HudKeyPickerDialog.show(getContext(), (action, displayLabel) -> {
                        if (targetSlot == 0) {
                            button.action = action;
                            button.label = displayLabel;
                        } else if (targetSlot == 1) {
                            button.popupAction = action;
                        }
                        mInspectorView.bindButton(button);
                        rebuildActiveLayout();
                    });
                }
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

    // The dock strip is gone. The two callbacks below used to populate it
    // from the inspector; they are now dead code. Kept (commented) for the
    // rare migration where a user still has dock items in their JSON.
    //
    // private void onDockItemSelectedForEdit(HudButton item) {
    //     mSelectedButton = item;
    //     mSelectedView = null;
    //     mInspectorView.bindDockItem(item);
    //     invalidate();
    // }
    // private void onDockItemActionPickedForEdit(HudButton item) {
    //     HudKeyPickerDialog.show(getContext(), (action, displayLabel) -> {
    //         item.action = action;
    //         item.label = displayLabel;
    //     });
    // }

    private LinearLayout createTopToolbar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xF0181825);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));

        // Save / Done stays on the right of the bar so the user can confirm
        // their changes at a glance. The action color is a calm teal/green
        // that signals "all good".
        Button btnSave = createToolButton("SAVE");
        btnSave.setTextColor(0xFF80DEEA);
        btnSave.setOnClickListener(v -> saveProfile());
        bar.addView(btnSave);

        // Single "+ ADD" button instead of three separate add buttons.
        // Tapping it opens a chooser so the user picks Standard Key,
        // Super Gesture nub, or TrackPoint (mouse / scroll). The popup
        // menu gives us more room for richer labels without eating the
        // top bar's tiny real estate on a portrait phone.
        Button btnAdd = createToolButton("+ ADD");
        btnAdd.setOnClickListener(v -> showAddWidgetChooser());
        bar.addView(btnAdd);

        // Spacer pushes the cancel button to the right edge.
        View spacer = new View(getContext());
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Cancel / Exit Edit. Calm red so it reads as a warning rather
        // than a destructive action.
        Button btnExit = createToolButton("CANCEL");
        btnExit.setTextColor(0xFFF38BA8);
        btnExit.setOnClickListener(v -> setEditMode(false));
        bar.addView(btnExit);

        return bar;
    }

    // Modal chooser for the "what kind of widget do you want to add?"
    // prompt. Replaces the old trio of separate [+] buttons which were
    // squeezed off the right edge of the bar on portrait phones.
    private void showAddWidgetChooser() {
        final String[] labels = new String[] {
                "Standard Key Button",
                "Super Gesture Nub",
                "TrackPoint (Mouse / Scroll)"
        };
        final HudButton.WidgetKind[] kinds = new HudButton.WidgetKind[] {
                HudButton.WidgetKind.KEY,
                HudButton.WidgetKind.SUPER_GESTURE,
                HudButton.WidgetKind.TRACKPOINT
        };
        new android.app.AlertDialog.Builder(getContext(),
                android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Add Widget")
                .setItems(labels, (dialog, which) -> {
                    if (mIsEditMode) addWidgetOfKind(kinds[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void addWidgetOfKind(HudButton.WidgetKind kind) {
        HudButton b = new HudButton();
        switch (kind) {
            case KEY:
                b.label = "KEY";
                b.action = HudAction.key(1);
                b.posXPercent = 0.5f;
                b.posYPercent = 0.4f;
                break;
            case SUPER_GESTURE:
                b.widgetType = HudButton.WIDGET_SUPER_GESTURE;
                b.label = "SUPER";
                b.widthDp = 58;
                b.heightDp = 58;
                b.cornerRadiusDp = 29;
                b.posXPercent = 0.5f;
                b.posYPercent = 0.5f;
                break;
            case TRACKPOINT:
                b.widgetType = HudButton.WIDGET_TRACKPOINT;
                b.label = "MOUSE";
                b.widthDp = 60;
                b.heightDp = 60;
                b.cornerRadiusDp = 30;
                b.posXPercent = 0.2f;
                b.posYPercent = 0.5f;
                break;
        }
        getActiveLayout().floatingButtons.add(b);
        rebuildActiveLayout();
        selectButton(b, null);
    }

    // Material-style flat button. All top-bar buttons share the rounded
    // 8dp Material surface, the same corner radius the rest of the
    // editor UI uses. Background is a subtle translucent white so the
    // chrome blends with the dark scrim above Niri.
    private Button createToolButton(String label) {
        Button b = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        b.setText(label);
        b.setAllCaps(true);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(0x33FFFFFF);
        b.setBackground(bg);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setMinWidth(0);
        b.setMinHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
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
        // Dock strip was removed; floating TrackPoint / SuperGesture buttons
        // do not surface modifier latch state visually today. A future
        // floating modifier indicator widget could subscribe to a
        // listener here if we ever add one.
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
        // Dock strip removed; no per-frame visibility bookkeeping needed.

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
                    @Override
                    public void onPointerScroll(float dx, float dy) {
                        // The dispatchContinuousMotion() loop already multiplies
                        // the offset by trackpointSensitivity, so the deltas
                        // arrive scaled. We only need to translate them into
                        // vertical / horizontal wheel ticks. dx maps to
                        // horizontal scroll (axis 1), dy maps to vertical
                        // (axis 0). Sign is inverted so the nub feels like
                        // a real touchpad: drag up -> page scrolls up.
                        if (!mIsEditMode && mHost != null) {
                            if (Math.abs(dy) > 0.01f) mHost.sendMouseScroll(0, -dy);
                            if (Math.abs(dx) > 0.01f) mHost.sendMouseScroll(1, dx);
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
                    // Recompute initial position from the percentage model every
                    // time the user starts dragging. View.getX() can be stale
                    // after orientation changes or after rebuildActiveLayout
                    // queued a post() that has not run yet, so trusting the
                    // model is the only way to keep the view and the touch
                    // delta in lock-step.
                    int parentW = getWidth();
                    int parentH = getHeight();
                    if (parentW > 0 && parentH > 0) {
                        mInitialBtnLeft = model.posXPercent * parentW - view.getWidth() * 0.5f;
                        mInitialBtnTop = model.posYPercent * parentH - view.getHeight() * 0.5f;
                        // Snap the view itself to the recomputed position so
                        // that subsequent getX()/getY() calls also return this
                        // value (the system View.setX() is a no-op when the
                        // new value is identical to the current one).
                        view.setX(mInitialBtnLeft);
                        view.setY(mInitialBtnTop);
                    } else {
                        mInitialBtnLeft = view.getX();
                        mInitialBtnTop = view.getY();
                    }
                    mActiveVerticalGuides.clear();
                    mActiveHorizontalGuides.clear();
                    mActiveSelfVerticalGuides.clear();
                    mActiveSelfHorizontalGuides.clear();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float targetX = mInitialBtnLeft + (rawX - mTouchDownX);
                    float targetY = mInitialBtnTop + (rawY - mTouchDownY);

                    RectF candidate = new RectF(targetX, targetY, targetX + view.getWidth(), targetY + view.getHeight());
                    List<RectF> siblings = getSiblingBounds(model);

                    // Dock strip removed - widgets can use the full screen
                    // height for snap-to-bottom alignment.
                    float dockTop = getHeight();
                    SnapGeometryEngine.SnapResult snap = mSnapEngine.computeSnap(candidate, siblings, getWidth(), getHeight(), dockTop);

                    view.setX(snap.snappedX);
                    view.setY(snap.snappedY);

                    model.posXPercent = (snap.snappedX + view.getWidth() * 0.5f) / getWidth();
                    model.posYPercent = (snap.snappedY + view.getHeight() * 0.5f) / getHeight();

                    // Sibling guides: xanh lá, snap-to-other-buttons
                    mActiveVerticalGuides.clear();
                    mActiveVerticalGuides.addAll(snap.verticalGuidelines);
                    mActiveHorizontalGuides.clear();
                    mActiveHorizontalGuides.addAll(snap.horizontalGuidelines);
                    // Self guides: cyan, các cạnh của chính nút đang kéo (left,
                    // right, center X, top, bottom, center Y). Luôn vẽ để
                    // người dùng thấy được vị trí nút khi kéo thả, kể cả khi
                    // không có sibling nào gần.
                    mActiveSelfVerticalGuides.clear();
                    mActiveSelfVerticalGuides.add(snap.snappedX);
                    mActiveSelfVerticalGuides.add(snap.snappedX + view.getWidth());
                    mActiveSelfVerticalGuides.add(snap.snappedX + view.getWidth() * 0.5f);
                    mActiveSelfHorizontalGuides.clear();
                    mActiveSelfHorizontalGuides.add(snap.snappedY);
                    mActiveSelfHorizontalGuides.add(snap.snappedY + view.getHeight());
                    mActiveSelfHorizontalGuides.add(snap.snappedY + view.getHeight() * 0.5f);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mActiveVerticalGuides.clear();
                    mActiveHorizontalGuides.clear();
                    mActiveSelfVerticalGuides.clear();
                    mActiveSelfHorizontalGuides.clear();
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
            // 3a. Sibling guides (xanh lá) - snap với nút khác / mép canvas
            for (float x : mActiveVerticalGuides) {
                canvas.drawLine(x, 0, x, h, mGuidePaint);
            }
            for (float y : mActiveHorizontalGuides) {
                canvas.drawLine(0, y, w, y, mGuidePaint);
            }
            // 3b. Self guides (cyan) - các cạnh của chính nút đang kéo
            for (float x : mActiveSelfVerticalGuides) {
                canvas.drawLine(x, 0, x, h, mSelfGuidePaint);
            }
            for (float y : mActiveSelfHorizontalGuides) {
                canvas.drawLine(0, y, w, y, mSelfGuidePaint);
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
        // The banner sits near the bottom of the screen. There is no dock
        // strip anymore, so we simply leave a small bottom margin.
        float bannerH = 38f * density;
        float bannerW = Math.min(w - 40f * density, 420f * density);
        float left = (w - bannerW) / 2f;
        float top = h - bannerH - 24f * density;
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
        // Dock strip removed. The legacy ExtraKeysBar lives in MainActivity
        // and is positioned separately when the IME is up.
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
