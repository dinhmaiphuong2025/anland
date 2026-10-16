package com.anland.consumer.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Dedicated Steam Deck style On-Screen Trackpad widget.
 * Features:
 * - High-precision 1-finger relative cursor movement
 * - Quick single-finger tap = Left Click (BTN_LEFT 0x110) with ~50ms hold
 * - Quick two-finger tap = Right Click (BTN_RIGHT 0x111) with ~50ms hold
 * - Two-finger vertical/horizontal drag = Wheel Scroll
 * - Subtle haptic pulse feedback when gliding across the trackpad surface
 */
public final class HudTouchpadView extends View {

    public interface TouchpadDispatcher {
        void onPointerMove(float dx, float dy);
        void onPointerClick(int button); // 1 = left, 2 = right, 3 = middle
        void onPointerScroll(float dx, float dy);
    }

    private final HudButton mModel;
    private final TouchpadDispatcher mDispatcher;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();

    private boolean mInEditMode = false;
    private boolean mIsTouching = false;

    // Multi-touch tracking
    private int mActivePointerId1 = MotionEvent.INVALID_POINTER_ID;
    private int mActivePointerId2 = MotionEvent.INVALID_POINTER_ID;
    private float mLastX1, mLastY1, mDownX1, mDownY1;
    private float mLastX2, mLastY2, mDownX2, mDownY2;
    private long mDownTime1 = 0;
    private boolean mIsTwoFinger = false;
    private float mAccumulatedDistance = 0f;

    public HudTouchpadView(Context context, HudButton model, TouchpadDispatcher dispatcher) {
        super(context);
        this.mModel = model;
        this.mDispatcher = dispatcher;
        setClickable(true);
    }

    public void setInEditMode(boolean editMode) {
        if (mInEditMode == editMode) return;
        mInEditMode = editMode;
        if (editMode) {
            mIsTouching = false;
            mActivePointerId1 = MotionEvent.INVALID_POINTER_ID;
            mActivePointerId2 = MotionEvent.INVALID_POINTER_ID;
        }
        invalidate();
    }

    private boolean isHapticEnabled() {
        return getContext().getSharedPreferences("anland_settings", Context.MODE_PRIVATE)
                .getBoolean("haptic_feedback_enabled", true);
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
        float corner = (mModel.cornerRadiusDp > 0 ? mModel.cornerRadiusDp : 18f) * density;
        mBounds.set(2, 2, w - 2, h - 2);

        // 1. Steam Deck Square Matte Surface with Rounded Corners
        mPaint.setStyle(Paint.Style.FILL);
        int baseBg = mModel.bgColor != 0 ? mModel.bgColor : 0xF0161822;
        mPaint.setColor(mIsTouching ? 0xF024283A : baseBg);
        mPaint.setAlpha(Math.round(mModel.opacity * 255));
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // 2. Outer Steam Deck Border Stroke (Accent Glow on touch)
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1, Math.round(1.5f * density)));
        int activeAccent = mModel.activeColor != 0 ? mModel.activeColor : 0xFF80DEEA;
        mPaint.setColor(mIsTouching ? activeAccent : 0x7780DEEA);
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // 3. Inner Tactile Recessed Boundary (authentic Steam Deck recessed bevel)
        float inset = 6f * density;
        RectF innerBounds = new RectF(mBounds.left + inset, mBounds.top + inset, mBounds.right - inset, mBounds.bottom - inset);
        float innerCorner = Math.max(4f * density, corner - inset * 0.7f);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1, Math.round(1.0f * density)));
        mPaint.setColor(mIsTouching ? 0x5580DEEA : 0x2280DEEA);
        canvas.drawRoundRect(innerBounds, innerCorner, innerCorner, mPaint);

        // 4. Center Precision Tactile Crosshair
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float armLen = 7f * density;
        float gap = 2.5f * density;
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.2f * density);
        mPaint.setColor(mIsTouching ? activeAccent : 0x8880DEEA);
        canvas.drawLine(cx - armLen, cy, cx - gap, cy, mPaint);
        canvas.drawLine(cx + gap, cy, cx + armLen, cy, mPaint);
        canvas.drawLine(cx, cy - armLen, cx, cy - gap, mPaint);
        canvas.drawLine(cx, cy + gap, cx, cy + armLen, mPaint);

        // 5. Center Touchpad Label
        mTextPaint.setColor(mIsTouching ? 0xFF80DEEA : 0xAA80DEEA);
        mTextPaint.setTextSize(Math.max(8 * density, 9.5f * density));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String label = (mModel.label != null && !mModel.label.isEmpty() && !"BTN".equals(mModel.label))
                ? mModel.label : "TOUCHPAD";
        canvas.drawText(label, cx, h - 10 * density, mTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // In visual editor mode, pass events to parent for drag & resize
        if (mInEditMode) return false;

        float density = getResources().getDisplayMetrics().density;
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mActivePointerId1 = event.getPointerId(0);
                mDownX1 = mLastX1 = event.getX(0);
                mDownY1 = mLastY1 = event.getY(0);
                mDownTime1 = SystemClock.uptimeMillis();
                mIsTwoFinger = false;
                mAccumulatedDistance = 0f;
                mIsTouching = true;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                if (event.getPointerCount() >= 2) {
                    mIsTwoFinger = true;
                    mActivePointerId2 = event.getPointerId(actionIndex);
                    int idx1 = event.findPointerIndex(mActivePointerId1);
                    int idx2 = event.findPointerIndex(mActivePointerId2);
                    if (idx1 >= 0) {
                        mLastX1 = event.getX(idx1);
                        mLastY1 = event.getY(idx1);
                    }
                    if (idx2 >= 0) {
                        mDownX2 = mLastX2 = event.getX(idx2);
                        mDownY2 = mLastY2 = event.getY(idx2);
                    }
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mDispatcher == null) return true;

                if (mIsTwoFinger && event.getPointerCount() >= 2) {
                    int idx1 = event.findPointerIndex(mActivePointerId1);
                    int idx2 = event.findPointerIndex(mActivePointerId2);
                    if (idx1 >= 0 && idx2 >= 0) {
                        float curAvgY = (event.getY(idx1) + event.getY(idx2)) * 0.5f;
                        float curAvgX = (event.getX(idx1) + event.getX(idx2)) * 0.5f;
                        float lastAvgY = (mLastY1 + mLastY2) * 0.5f;
                        float lastAvgX = (mLastX1 + mLastX2) * 0.5f;
                        float dy = curAvgY - lastAvgY;
                        float dx = curAvgX - lastAvgX;
                        mLastX1 = event.getX(idx1);
                        mLastY1 = event.getY(idx1);
                        mLastX2 = event.getX(idx2);
                        mLastY2 = event.getY(idx2);

                        // Invert dy so dragging fingers up scrolls page up
                        if (Math.abs(dy) > 1.0f) mDispatcher.onPointerScroll(0, -dy);
                        if (Math.abs(dx) > 1.0f) mDispatcher.onPointerScroll(1, dx);
                    }
                } else if (mActivePointerId1 != MotionEvent.INVALID_POINTER_ID) {
                    int idx1 = event.findPointerIndex(mActivePointerId1);
                    if (idx1 >= 0) {
                        float curX = event.getX(idx1);
                        float curY = event.getY(idx1);
                        float dx = curX - mLastX1;
                        float dy = curY - mLastY1;
                        mLastX1 = curX;
                        mLastY1 = curY;

                        float sens = (mModel.trackpointSensitivity > 0) ? mModel.trackpointSensitivity : 1.5f;
                        mDispatcher.onPointerMove(dx * sens, dy * sens);

                        mAccumulatedDistance += (float) Math.hypot(dx, dy);
                        if (mAccumulatedDistance > 20f * density) {
                            mAccumulatedDistance = 0f;
                            if (isHapticEnabled()) {
                                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            }
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(actionIndex);
                if (pointerId == mActivePointerId2) {
                    // Two finger tap detection (duration < 250ms and small displacement)
                    long duration = SystemClock.uptimeMillis() - mDownTime1;
                    int idx2 = event.findPointerIndex(mActivePointerId2);
                    float dist2 = (idx2 >= 0) ? (float) Math.hypot(event.getX(idx2) - mDownX2, event.getY(idx2) - mDownY2) : 0;
                    if (duration < 250 && dist2 < 18f * density && mDispatcher != null) {
                        mDispatcher.onPointerClick(2); // Right Click (BTN_RIGHT 0x111)
                        if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    mActivePointerId2 = MotionEvent.INVALID_POINTER_ID;
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                long duration = SystemClock.uptimeMillis() - mDownTime1;
                float totalDist = (float) Math.hypot(event.getX() - mDownX1, event.getY() - mDownY1);

                // Quick single-finger tap = Left Click (BTN_LEFT 0x110)
                if (!mIsTwoFinger && duration < 250 && totalDist < 16f * density && mDispatcher != null) {
                    mDispatcher.onPointerClick(1); // Left Click
                    if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }

                mIsTouching = false;
                mActivePointerId1 = MotionEvent.INVALID_POINTER_ID;
                mActivePointerId2 = MotionEvent.INVALID_POINTER_ID;
                mIsTwoFinger = false;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                mIsTouching = false;
                mActivePointerId1 = MotionEvent.INVALID_POINTER_ID;
                mActivePointerId2 = MotionEvent.INVALID_POINTER_ID;
                mIsTwoFinger = false;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
