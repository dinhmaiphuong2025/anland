package com.anland.consumer.hud;

import com.anland.consumer.hud.design.HudColors;

import com.anland.consumer.hud.design.HudChrome;
import com.anland.consumer.hud.design.HudType;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Visual Freeform Button View with touch down/up states and swipe-up popup support.
 */
public final class HudFreeformButtonView extends View {

    public interface ButtonActionListener {
        void onButtonPress(HudButton button, boolean isDown);
        void onPopupTrigger(HudButton button);
        /** A configured long-press action was triggered. */
        default void onButtonLongPress(HudButton button) {}
    }

    /** Movement that cancels a pending long press, in dp. */
    private static final float LONG_PRESS_SLOP_DP = 12f;

    private final HudButton mModel;
    private final ButtonActionListener mListener;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final Path mPath = new Path();
    private final float[] mRadii = new float[8];
    private final HudButtonPress mPress = new HudButtonPress();

    private boolean mIsPressed;
    private float mDownX;
    private float mDownY;
    private boolean mPopupTriggered;
    // A configured long-press action competes with the release and swipe actions.
    private boolean mLongPressArmed = false;
    private boolean mLongPressFired = false;
    // In edit mode the parent HudOverlayView already owns the touch stream
    // (it uses an OnTouchListener to drag-to-move). We must NOT respond to
    // touch ourselves, or the parent would think we have a competing gesture
    // and we would also accidentally trigger button press on every tap.
    private boolean mInEditMode = false;
    /** True while a pointer button is held: it must live until the finger lifts. */
    private boolean mHoldingMouse = false;

    // Hold-to-repeat: the legacy extra-keys bar repeated directional and
    // editing keys while held. HudAction.repeat carries that intent.
    private static final long REPEAT_INITIAL_DELAY_MS = 320L;
    private static final long REPEAT_INTERVAL_MS = 60L;
    /** Scrolling repeats slower than key repeat; one notch every 60ms overshoots. */
    private static final long REPEAT_SCROLL_INTERVAL_MS = 130L;
    private final Handler mRepeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRepeatTick = new Runnable() {
        @Override public void run() {
            if (!mIsPressed || mListener == null) return;
            mPress.repeat(HudFreeformButtonView.this::sendPrimaryTap);
            mRepeatHandler.postDelayed(this,
                    isScrollAction() ? REPEAT_SCROLL_INTERVAL_MS : REPEAT_INTERVAL_MS);
        }
    };

    private final Runnable mLongPressTick = new Runnable() {
        @Override public void run() {
            if (!mIsPressed || !mLongPressArmed) return;
            mLongPressFired = true;
            mPress.cancel();
            stopRepeat();
            if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (mListener != null) mListener.onButtonLongPress(mModel);
        }
    };

    private boolean hasLongPress() {
        return mModel != null && mModel.longPressAction != null;
    }

    private void dropLongPress() {
        mLongPressArmed = false;
        mRepeatHandler.removeCallbacks(mLongPressTick);
    }

    public HudFreeformButtonView(Context context, HudButton model, ButtonActionListener listener) {
        super(context);
        this.mModel = model;
        this.mListener = listener;

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(android.graphics.Typeface.SANS_SERIF);
        mTextPaint.setFakeBoldText(true);
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode) {
            stopRepeat();
            releaseMouseIfHeld();
        }
    }

    private boolean isMouseAction() {
        return mModel != null && mModel.action != null
                && HudAction.TYPE_MOUSE.equals(mModel.action.type) && mModel.action.code > 0;
    }

    private void releaseMouseIfHeld() {
        if (!mHoldingMouse) return;
        mHoldingMouse = false;
        if (mListener != null) mListener.onButtonPress(mModel, false);
    }

    private void sendPrimaryTap() {
        if (mListener == null) return;
        mListener.onButtonPress(mModel, true);
        mListener.onButtonPress(mModel, false);
    }

    @Override
    protected void onDetachedFromWindow() {
        mIsPressed = false;
        releaseMouseIfHeld();
        mPress.cancel();
        stopRepeat();
        dropLongPress();
        super.onDetachedFromWindow();
    }

    private boolean isRepeatable() {
        if (mModel == null || mModel.sendOnRelease || mModel.action == null) return false;
        if (HudAction.TYPE_KEY.equals(mModel.action.type))
            return mModel.action.code > 0 && mModel.action.repeat;
        // Scroll notches were one-shot: holding the key did nothing after the first step.
        if (HudAction.TYPE_SYSTEM.equals(mModel.action.type))
            return "mouse_scroll_up".equals(mModel.action.systemCommand)
                    || "mouse_scroll_down".equals(mModel.action.systemCommand);
        return false;
    }

    private boolean isScrollAction() {
        return mModel != null && mModel.action != null
                && HudAction.TYPE_SYSTEM.equals(mModel.action.type)
                && ("mouse_scroll_up".equals(mModel.action.systemCommand)
                    || "mouse_scroll_down".equals(mModel.action.systemCommand));
    }

    private void startRepeat() {
        mRepeatHandler.removeCallbacks(mRepeatTick);
        if (!isRepeatable()) return;
        mRepeatHandler.postDelayed(mRepeatTick, REPEAT_INITIAL_DELAY_MS);
    }

    private void stopRepeat() {
        mRepeatHandler.removeCallbacks(mRepeatTick);
    }

    public HudButton getModel() {
        return mModel;
    }

    private boolean isHapticEnabled() {
        return mModel.hapticEnabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int w = Math.round(mModel.widthDp * density);
        int h = Math.round(mModel.heightDp * density);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        mBounds.set(2, 2, w - 2, h - 2);

        // Check if this button is an active modifier key (CTRL, ALT, SHIFT, SUPER)
        boolean isModifierActive = false;
        if (mModel != null && HudAction.TYPE_MODIFIER.equals(mModel.action.type)) {
            if (getContext() instanceof IModifierProvider) {
                isModifierActive = ((IModifierProvider) getContext()).isModifierActive(mModel.action.code);
            } else if (getParent() instanceof View) {
                View p = (View) getParent();
                while (p != null) {
                    if (p instanceof IModifierProvider) {
                        isModifierActive = ((IModifierProvider) p).isModifierActive(mModel.action.code);
                        break;
                    }
                    p = (p.getParent() instanceof View) ? (View) p.getParent() : null;
                }
            }
        }

        // Fill and stroke are owned by the design layer so every widget renders identically.
        int baseColor = (mIsPressed || isModifierActive) ? mModel.activeColor : mModel.bgColor;
        float corner = mModel.cornerRadiusDp * density;
        float strokePx = mModel.borderStrokeWidthDp > 0
                ? Math.max(1f, mModel.borderStrokeWidthDp * density)
                : 0f;
        HudChrome.drawSurface(canvas, mPaint, mPath, mBounds,
                mModel.cornerShape, corner, baseColor, mModel.opacity,
                strokePx, mModel.borderColor);

        // Text or Icon
        if (mModel.iconId != null && !HudVectorIcons.ICON_NONE.equals(mModel.iconId) && !mModel.iconId.isEmpty()) {
            mPaint.setColor(mIsPressed ? HudColors.onActive : mModel.textColor);
            float iconSize = Math.min(w, h) * 0.46f;
            HudVectorIcons.drawIcon(canvas, mModel.iconId, w * 0.5f, h * 0.5f, iconSize, mPaint);
        } else {
            // Text with auto font scaling and alignment
            mTextPaint.setColor(mIsPressed ? HudColors.onActive : mModel.textColor);
            String label = mModel.label != null ? mModel.label : "BTN";
            float maxTextWidth = w - 4 * density; // Compact 2dp margin
            // Target font size: 12dp, scaling gently with button size
            float baseTextSize = Math.max(HudType.CAPTION_SP * density,
                    Math.min(HudType.BODY_SP * density, Math.min(w, h) * 0.40f));
            mTextPaint.setTextSize(baseTextSize);
            float textW = mTextPaint.measureText(label);
            if (textW > maxTextWidth && textW > 0) {
                // If text overflows, scale down but never below the legibility floor
                // so HOME/PGDN/PGUP stay readable.
                mTextPaint.setTextSize(Math.max(HudType.KEY_LABEL_FLOOR_SP * density,
                        baseTextSize * (maxTextWidth / textW)));
            }
            float textY = h * 0.5f - ((mTextPaint.descent() + mTextPaint.ascent()) * 0.5f);

            float textX;
            if (mModel.textAlign == HudButton.ALIGN_LEFT) {
                mTextPaint.setTextAlign(Paint.Align.LEFT);
                textX = 10 * density;
            } else if (mModel.textAlign == HudButton.ALIGN_RIGHT) {
                mTextPaint.setTextAlign(Paint.Align.RIGHT);
                textX = w - 10 * density;
            } else {
                mTextPaint.setTextAlign(Paint.Align.CENTER);
                textX = w * 0.5f;
            }
            canvas.drawText(label, textX, textY, mTextPaint);
        }

        // Secondary popup glyph dot if configured
        if (mModel.popupAction != null) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(HudColors.withAlpha(HudColors.primary, 0xCC));
            canvas.drawCircle(w - 7 * density, 7 * density, 2.5f * density, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Edit mode: the parent's OnTouchListener already owns this widget for
        // drag-to-move. Returning false here is the Android-idiomatic way of
        // saying "I don't want to handle this; my parent will".
        if (mInEditMode) return false;

        float y = event.getY();
        float density = getResources().getDisplayMetrics().density;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = y;
                mIsPressed = true;
                mPopupTriggered = false;
                mLongPressFired = false;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                if (hasLongPress()) {
                    mLongPressArmed = true;
                    mRepeatHandler.postDelayed(mLongPressTick, Math.max(HudButton.LONG_PRESS_MIN_MS,
                            Math.min(HudButton.LONG_PRESS_MAX_MS, mModel.longPressMs)));
                }
                if (isMouseAction()) {
                    // A mouse button is a held state, not a tap: press now, release on lift.
                    mHoldingMouse = true;
                    if (mListener != null) mListener.onButtonPress(mModel, true);
                } else {
                    mPress.down(mModel.sendOnRelease, this::sendPrimaryTap);
                    startRepeat();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mLongPressArmed && Math.hypot(event.getX() - mDownX, y - mDownY) > LONG_PRESS_SLOP_DP * density) {
                    dropLongPress();
                }
                if (!mLongPressFired && mModel.popupAction != null && !mPopupTriggered && (mDownY - y) > 30 * density) {
                    mPopupTriggered = true;
                    mIsPressed = false;
                    stopRepeat();
                    dropLongPress();
                    mPress.cancel();
                    releaseMouseIfHeld();
                    if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    invalidate();
                    if (mListener != null) {
                        mListener.onPopupTrigger(mModel);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean canceled = event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                mIsPressed = false;
                stopRepeat();
                dropLongPress();
                invalidate();
                if (mHoldingMouse) {
                    releaseMouseIfHeld();
                    return true;
                }
                if (mLongPressFired) {
                    mLongPressFired = false;
                    return true;   // the hold already did its job
                }
                // A cancellation must not trigger a deferred tap.
                if (canceled) {
                    mPress.cancel();
                    return true;
                }
                if (mPopupTriggered) return true;
                mPress.up(this::sendPrimaryTap);
                return true;
        }
        return super.onTouchEvent(event);
    }
}
