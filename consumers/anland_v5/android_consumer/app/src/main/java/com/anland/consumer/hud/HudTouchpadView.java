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
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * On-screen trackpad, designed for one hand and one finger.
 *
 * <p>Gesture model (single pointer unless stated otherwise):
 * <ul>
 *   <li>tap = left click</li>
 *   <li>second tap within 350 ms = double click</li>
 *   <li>double tap and keep the finger down, then move = left button held, i.e. drag;
 *       lifting the finger ends it</li>
 *   <li>press and hold 400 ms without moving = right click</li>
 *   <li>two-finger tap = right click, two-finger drag = scroll (secondary, kept from before)</li>
 * </ul>
 *
 * <p>The pad has a single hit area: no zones to aim at, because it is used one-handed with
 * one finger. The old hold handler gave up as soon as the finger drifted past a 7dp slop,
 * which on a real thumb is most of the time, so the right click rarely fired; the tolerance
 * is now 12dp and only a deliberate drag cancels it.
 */
public final class HudTouchpadView extends View {

    public interface TouchpadDispatcher {
        void onPointerMove(float dx, float dy);
        void onPointerClick(int button); // 1 = left, 2 = right, 3 = middle
        void onPointerButton(int button, boolean pressed);
        void onPointerScroll(float dx, float dy);
    }

    /** Hold duration before the pad fires a right click (double-tap drag mode). */
    private static final long HOLD_RIGHT_CLICK_MS = 400L;
    /** Hold duration before the pad grabs the left button (hold drag mode). */
    private static final long HOLD_DRAG_MS = 220L;
    /** Finger drift tolerated before the hold is treated as a drag instead. */
    private static final float HOLD_DRIFT_DP = 12f;
    /** Movement past this distance starts a double-tap drag. */
    private static final float DRAG_SLOP_DP = 5f;
    private static final long DOUBLE_TAP_MS = 350L;
    private static final long TWO_FINGER_TAP_MS = 250L;

    private final HudButton mModel;
    private final TouchpadDispatcher mDispatcher;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final RectF mInner = new RectF();
    private final Path mPath = new Path();
    private final Path mInnerPath = new Path();
    /** Per-corner radii buffer: the inset lines must follow the model shape, not a radius. */
    private final float[] mInnerRadii = new float[8];

    private float mDensity = 1f;
    private boolean mInEditMode = false;
    private boolean mIsTouching = false;

    // Multi-touch tracking.
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mPointerId1 = MotionEvent.INVALID_POINTER_ID;
    private int mPointerId2 = MotionEvent.INVALID_POINTER_ID;
    private float mLastX1, mLastY1, mDownX1, mDownY1;
    private float mLastX2, mLastY2, mDownX2, mDownY2;
    private long mDownTime1 = 0L;
    private boolean mIsTwoFinger = false;
    private float mAccumulatedDistance = 0f;
    private float mScrollAccumX = 0f, mScrollAccumY = 0f;

    // Gesture state.
    private boolean mHasMoved = false;
    private boolean mHoldDragActive = false;
    private boolean mHoldFired = false;
    /** Largest distance the finger has travelled since ACTION_DOWN. */
    private float mMaxDrift = 0f;
    private boolean mDoubleTapCandidate = false;
    private long mLastTapUpTime = 0L;
    private float mLastTapUpX = 0f, mLastTapUpY = 0f;

    private final Runnable mHoldRunnable = new Runnable() {
        @Override public void run() {
            if (!mIsTouching || mIsTwoFinger || mDoubleTapCandidate) return;
            if (mMaxDrift > HOLD_DRIFT_DP * mDensity) return;
            if (mModel.dragMode == HudButton.DRAG_HOLD) {
                // Hold = grab the left button; the finger is now dragging whatever it is over.
                mHoldDragActive = true;
                mHoldFired = true;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (mDispatcher != null) mDispatcher.onPointerButton(1, true);
            } else {
                mHoldFired = true;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (mDispatcher != null) mDispatcher.onPointerClick(2);
            }
            invalidate();
        }
    };

    public HudTouchpadView(Context context, HudButton model, TouchpadDispatcher dispatcher) {
        super(context);
        this.mModel = model;
        this.mDispatcher = dispatcher;
        mDensity = context.getResources().getDisplayMetrics().density;
        setClickable(true);
    }

    public void setInEditMode(boolean editMode) {
        if (mInEditMode == editMode) return;
        mInEditMode = editMode;
        if (editMode) {
            mIsTouching = false;
            mHandler.removeCallbacks(mHoldRunnable);
            releaseHeldButton();
            mPointerId1 = MotionEvent.INVALID_POINTER_ID;
            mPointerId2 = MotionEvent.INVALID_POINTER_ID;
        }
        invalidate();
    }

    private boolean isHapticEnabled() {
        return mModel.hapticEnabled;
    }

    private void releaseHeldButton() {
        if (mHoldDragActive && mDispatcher != null) mDispatcher.onPointerButton(1, false);
        mHoldDragActive = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Math.round(mModel.widthDp * mDensity);
        int h = Math.round(mModel.heightDp * mDensity);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDensity = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float d = mDensity;
        float corner = mModel.cornerRadiusDp * d;
        mBounds.set(2f, 2f, w - 2f, h - 2f);

        int variant = mModel.styleVariant; // 0 = matte, 1 = trackpad, 2 = glass outline
        int baseBg = mModel.bgColor != 0 ? mModel.bgColor : HudColors.surfaceHigh;
        int fillColor = variant == 2 ? Color.TRANSPARENT : baseBg;
        float strokePx = mModel.borderStrokeWidthDp > 0
                ? Math.max(1f, mModel.borderStrokeWidthDp * d)
                : 0f;

        HudChrome.drawSurface(canvas, mPaint, mPath, mBounds,
                mModel.cornerShape, corner, fillColor, mModel.opacity,
                strokePx, mModel.borderColor);

        // Pressed state is an overlay rather than a fill swap: the model colour keeps its
        // opacity, and the accent reads as a glow instead of a flat lilac slab.
        if (mIsTouching) {
            int glow = mHoldDragActive
                    ? HudColors.withAlpha(HudColors.primary, 0x2E)
                    : HudColors.withAlpha(HudColors.primary, 0x1E);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(glow);
            canvas.drawPath(mPath, mPaint);

            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, d));
            mPaint.setColor(HudColors.withAlpha(HudColors.primary, 0x99));
            mInner.set(mBounds.left + 1.5f * d, mBounds.top + 1.5f * d,
                    mBounds.right - 1.5f * d, mBounds.bottom - 1.5f * d);
            mInnerPath.reset();
            HudChrome.computeRadii(mModel.cornerShape, Math.max(1f, corner - 1.5f * d), mInnerRadii);
            mInnerPath.addRoundRect(mInner, mInnerRadii, Path.Direction.CW);
            canvas.drawPath(mInnerPath, mPaint);
        }

        // The recessed hairline IS the classic bevel (variant 1); the minimal flat style has
        // nothing inside its border at all.
        if (variant == 1) {
            float inset = 6f * d;
            mInner.set(mBounds.left + inset, mBounds.top + inset,
                    mBounds.right - inset, mBounds.bottom - inset);
            float innerCorner = Math.max(3f * d, corner - inset * 0.7f);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, d));
            mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x14));
            mInnerPath.reset();
            HudChrome.computeRadii(mModel.cornerShape, Math.max(0f, innerCorner), mInnerRadii);
            mInnerPath.addRoundRect(mInner, mInnerRadii, Path.Direction.CW);
            canvas.drawPath(mInnerPath, mPaint);
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        // Variant 1 (classic bevel) draws no glyph of any kind: the recessed hairline is the
        // whole design. Variant 2 (glass) carries the quiet ring that becomes a crosshair while
        // the finger is down. Variant 0 (minimal flat) stays empty as well but without the bevel.
        if (variant == 2) {
            if (mIsTouching) {
                float arm = 5f * d;
                float gap = 2.5f * d;
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(Math.max(1f, d));
                mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x99));
                canvas.drawLine(cx - gap - arm, cy, cx - gap, cy, mPaint);
                canvas.drawLine(cx + gap, cy, cx + gap + arm, cy, mPaint);
                canvas.drawLine(cx, cy - gap - arm, cx, cy - gap, mPaint);
                canvas.drawLine(cx, cy + gap, cx, cy + gap + arm, mPaint);
            } else {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(Math.max(1f, d));
                mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x3D));
                canvas.drawCircle(cx, cy, 5f * d, mPaint);
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x59));
                canvas.drawCircle(cx, cy, 1.2f * d, mPaint);
            }
        }

        // A label the user typed is always drawn. The fallback "TOUCHPAD" is editor-only: in use
        // it reads as a watermark, which is what made it feel harsh. It also used to be bold and
        // sat right on the recessed hairline; now it is regular weight, letter-spaced, and lifted
        // clear of the inset line.
        String custom = (mModel.label != null && !mModel.label.isEmpty() && !"BTN".equals(mModel.label))
                ? mModel.label : null;
        String caption = custom != null ? custom : (mInEditMode ? "TOUCHPAD" : null);
        // The bevel style is deliberately text-free, including a label the user typed.
        if (variant == 1) caption = null;
        if (caption != null) {
            mTextPaint.setTypeface(Typeface.DEFAULT);
            mTextPaint.setLetterSpacing(0.08f);
            mTextPaint.setTextSize(HudType.MICRO_SP * d);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, custom != null ? 0xCC : 0x73));
            canvas.drawText(caption, cx, mBounds.bottom - 9f * d, mTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // In the visual editor the pad is a widget to move, not an input device.
        if (mInEditMode) return false;

        float d = mDensity;
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mPointerId1 = event.getPointerId(0);
                mDownX1 = mLastX1 = event.getX(0);
                mDownY1 = mLastY1 = event.getY(0);
                mDownTime1 = SystemClock.uptimeMillis();
                mIsTwoFinger = false;
                mHasMoved = false;
                mHoldDragActive = false;
                mIsTouching = true;
                mAccumulatedDistance = 0f;
                mHoldFired = false;
                mMaxDrift = 0f;

                long sinceTap = mDownTime1 - mLastTapUpTime;
                float distFromTap = (float) Math.hypot(mDownX1 - mLastTapUpX, mDownY1 - mLastTapUpY);
                mDoubleTapCandidate = sinceTap < DOUBLE_TAP_MS && distFromTap < 32f * d;
                if (!mDoubleTapCandidate) {
                    mHandler.postDelayed(mHoldRunnable,
                            mModel.dragMode == HudButton.DRAG_HOLD ? HOLD_DRAG_MS : HOLD_RIGHT_CLICK_MS);
                }
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                mHandler.removeCallbacks(mHoldRunnable);
                if (event.getPointerCount() >= 2) {
                    // A second finger means scroll, never drag.
                    releaseHeldButton();
                    mIsTwoFinger = true;
                    mPointerId2 = event.getPointerId(actionIndex);
                    int idx1 = event.findPointerIndex(mPointerId1);
                    int idx2 = event.findPointerIndex(mPointerId2);
                    if (idx1 >= 0) {
                        mLastX1 = event.getX(idx1);
                        mLastY1 = event.getY(idx1);
                    }
                    if (idx2 >= 0) {
                        mDownX2 = mLastX2 = event.getX(idx2);
                        mDownY2 = mLastY2 = event.getY(idx2);
                    }
                    mScrollAccumX = 0f;
                    mScrollAccumY = 0f;
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mDispatcher == null) return true;

                if (mIsTwoFinger && event.getPointerCount() >= 2) {
                    int idx1 = event.findPointerIndex(mPointerId1);
                    int idx2 = event.findPointerIndex(mPointerId2);
                    if (idx1 >= 0 && idx2 >= 0) {
                        float avgX = (event.getX(idx1) + event.getX(idx2)) * 0.5f;
                        float avgY = (event.getY(idx1) + event.getY(idx2)) * 0.5f;
                        float lastAvgX = (mLastX1 + mLastX2) * 0.5f;
                        float lastAvgY = (mLastY1 + mLastY2) * 0.5f;
                        float dx = avgX - lastAvgX;
                        float dy = avgY - lastAvgY;
                        mLastX1 = event.getX(idx1);
                        mLastY1 = event.getY(idx1);
                        mLastX2 = event.getX(idx2);
                        mLastY2 = event.getY(idx2);

                        // Two fingers have twice the travel of one; halving keeps the scroll
                        // rate close to the single-finger cursor rate.
                        mScrollAccumY += dy * 0.5f;
                        mScrollAccumX += dx * 0.5f;
                        if (Math.abs(mScrollAccumY) >= 1f) {
                            mDispatcher.onPointerScroll(0, -mScrollAccumY);
                            mScrollAccumY = 0f;
                        }
                        if (Math.abs(mScrollAccumX) >= 1f) {
                            mDispatcher.onPointerScroll(1, mScrollAccumX);
                            mScrollAccumX = 0f;
                        }
                    }
                } else if (mPointerId1 != MotionEvent.INVALID_POINTER_ID) {
                    int idx1 = event.findPointerIndex(mPointerId1);
                    if (idx1 >= 0) {
                        float curX = event.getX(idx1);
                        float curY = event.getY(idx1);
                        float dx = curX - mLastX1;
                        float dy = curY - mLastY1;
                        mLastX1 = curX;
                        mLastY1 = curY;

                        float drift = (float) Math.hypot(curX - mDownX1, curY - mDownY1);
                        if (drift > mMaxDrift) mMaxDrift = drift;
                        // A double tap that keeps moving is a drag, not a long press.
                        if (mModel.dragMode == HudButton.DRAG_DOUBLE_TAP
                                && mDoubleTapCandidate && drift > DRAG_SLOP_DP * d && !mHoldDragActive) {
                            mHoldDragActive = true;
                            mHandler.removeCallbacks(mHoldRunnable);
                            if (mDispatcher != null) mDispatcher.onPointerButton(1, true);
                            if (isHapticEnabled()) {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            }
                        }
                        if (!mHasMoved && drift > DRAG_SLOP_DP * d) mHasMoved = true;

                        float sens = (mModel.trackpointSensitivity > 0) ? mModel.trackpointSensitivity : 1.4f;
                        mDispatcher.onPointerMove(dx * sens, dy * sens);

                        mAccumulatedDistance += (float) Math.hypot(dx, dy);
                        if (mAccumulatedDistance > 18f * d) {
                            mAccumulatedDistance = 0f;
                            if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(actionIndex);
                if (pointerId == mPointerId2) {
                    long duration = SystemClock.uptimeMillis() - mDownTime1;
                    int idx2 = event.findPointerIndex(mPointerId2);
                    float moved = (idx2 >= 0)
                            ? (float) Math.hypot(event.getX(idx2) - mDownX2, event.getY(idx2) - mDownY2)
                            : 0f;
                    if (duration < TWO_FINGER_TAP_MS && moved < 18f * d && mDispatcher != null) {
                        mDispatcher.onPointerClick(2);
                        if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    mPointerId2 = MotionEvent.INVALID_POINTER_ID;
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                mHandler.removeCallbacks(mHoldRunnable);
                long now = SystemClock.uptimeMillis();
                long duration = now - mDownTime1;

                if (mHoldDragActive) {
                    // Drag finished: the release is the button up, never a click.
                    releaseHeldButton();
                    mDoubleTapCandidate = false;
                    mLastTapUpTime = 0L;
                } else if (mHoldFired) {
                    // Right click already fired at the hold timeout.
                    mHoldFired = false;
                    mDoubleTapCandidate = false;
                    mLastTapUpTime = 0L;
                } else if (!mIsTwoFinger && !mHasMoved && duration < 420L) {
                    if (mDispatcher != null) {
                        if (mDoubleTapCandidate) {
                            mDispatcher.onPointerClick(1);
                            postDelayed(() -> {
                                if (mDispatcher != null) mDispatcher.onPointerClick(1);
                            }, 45);
                            mDoubleTapCandidate = false;
                            mLastTapUpTime = 0L;
                        } else {
                            mDispatcher.onPointerClick(1);
                            mLastTapUpTime = now;
                            mLastTapUpX = mDownX1;
                            mLastTapUpY = mDownY1;
                        }
                    }
                    if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } else {
                    mLastTapUpTime = 0L;
                }

                mIsTouching = false;
                mHoldFired = false;
                mPointerId1 = MotionEvent.INVALID_POINTER_ID;
                mPointerId2 = MotionEvent.INVALID_POINTER_ID;
                mIsTwoFinger = false;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                mHandler.removeCallbacks(mHoldRunnable);
                releaseHeldButton();
                mDoubleTapCandidate = false;
                mIsTouching = false;
                mHoldFired = false;
                mHasMoved = false;
                mPointerId1 = MotionEvent.INVALID_POINTER_ID;
                mPointerId2 = MotionEvent.INVALID_POINTER_ID;
                mIsTwoFinger = false;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
