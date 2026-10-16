package com.anland.consumer.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Customizable On-Screen Scroll Bar widget for HUD.
 * Supports Vertical and Horizontal orientations.
 * Dragging along the bar sends continuous smooth scroll-wheel events.
 * Tapping the ends sends discrete step ticks.
 */
public final class HudScrollBarView extends View {

    public interface ScrollDispatcher {
        void onPointerScroll(float dx, float dy);
    }

    private final HudButton mModel;
    private final ScrollDispatcher mDispatcher;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final RectF mThumbBounds = new RectF();
    private final Path mArrowPath1 = new Path();
    private final Path mArrowPath2 = new Path();

    private boolean mInEditMode = false;
    private boolean mIsTouching = false;
    private float mLastTouchPos = 0f;
    private float mAccumulatedDrag = 0f;
    private float mThumbOffset = 0f;

    public HudScrollBarView(Context context, HudButton model, ScrollDispatcher dispatcher) {
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
            mThumbOffset = 0f;
        }
        invalidate();
    }

    private boolean isVertical() {
        return !"horizontal".equalsIgnoreCase(mModel.trackpointMode);
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
        boolean vert = isVertical();
        float corner = mModel.cornerRadiusDp > 0 ? mModel.cornerRadiusDp * density : Math.min(w, h) * 0.5f;
        mBounds.set(2, 2, w - 2, h - 2);

        // 1. Background Track
        mPaint.setStyle(Paint.Style.FILL);
        int baseBg = mModel.bgColor != 0 ? mModel.bgColor : 0xE61A1B26;
        mPaint.setColor(mIsTouching ? 0xF0252838 : baseBg);
        mPaint.setAlpha(Math.round(mModel.opacity * 255));
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // 2. Outer Border
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1, Math.round(1.5f * density)));
        int activeAccent = mModel.activeColor != 0 ? mModel.activeColor : 0xFF80DEEA;
        mPaint.setColor(mIsTouching ? activeAccent : 0x6680DEEA);
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // 3. Arrow indicators at ends
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mIsTouching ? activeAccent : 0xAA80DEEA);
        float arrowSize = Math.min(w, h) * 0.28f;

        if (vert) {
            float cx = w * 0.5f;
            // Top Arrow (▲)
            mArrowPath1.reset();
            mArrowPath1.moveTo(cx, 8f * density);
            mArrowPath1.lineTo(cx - arrowSize, 8f * density + arrowSize * 1.2f);
            mArrowPath1.lineTo(cx + arrowSize, 8f * density + arrowSize * 1.2f);
            mArrowPath1.close();
            canvas.drawPath(mArrowPath1, mPaint);

            // Bottom Arrow (▼)
            mArrowPath2.reset();
            mArrowPath2.moveTo(cx, h - 8f * density);
            mArrowPath2.lineTo(cx - arrowSize, h - 8f * density - arrowSize * 1.2f);
            mArrowPath2.lineTo(cx + arrowSize, h - 8f * density - arrowSize * 1.2f);
            mArrowPath2.close();
            canvas.drawPath(mArrowPath2, mPaint);

            // Center sliding Thumb Pill
            float thumbH = Math.max(24f * density, h * 0.28f);
            float thumbW = w - 8f * density;
            float thumbY = (h - thumbH) * 0.5f + mThumbOffset;
            float maxOffset = (h - thumbH) * 0.5f - 18f * density;
            thumbY = Math.max((h - thumbH) * 0.5f - maxOffset, Math.min((h - thumbH) * 0.5f + maxOffset, thumbY));
            mThumbBounds.set(4f * density, thumbY, 4f * density + thumbW, thumbY + thumbH);
            mPaint.setColor(mIsTouching ? 0xCC80DEEA : 0x5580DEEA);
            canvas.drawRoundRect(mThumbBounds, thumbW * 0.5f, thumbW * 0.5f, mPaint);
        } else {
            float cy = h * 0.5f;
            // Left Arrow (◀)
            mArrowPath1.reset();
            mArrowPath1.moveTo(8f * density, cy);
            mArrowPath1.lineTo(8f * density + arrowSize * 1.2f, cy - arrowSize);
            mArrowPath1.lineTo(8f * density + arrowSize * 1.2f, cy + arrowSize);
            mArrowPath1.close();
            canvas.drawPath(mArrowPath1, mPaint);

            // Right Arrow (▶)
            mArrowPath2.reset();
            mArrowPath2.moveTo(w - 8f * density, cy);
            mArrowPath2.lineTo(w - 8f * density - arrowSize * 1.2f, cy - arrowSize);
            mArrowPath2.lineTo(w - 8f * density - arrowSize * 1.2f, cy + arrowSize);
            mArrowPath2.close();
            canvas.drawPath(mArrowPath2, mPaint);

            // Center sliding Thumb Pill
            float thumbW = Math.max(24f * density, w * 0.28f);
            float thumbH = h - 8f * density;
            float thumbX = (w - thumbW) * 0.5f + mThumbOffset;
            float maxOffset = (w - thumbW) * 0.5f - 18f * density;
            thumbX = Math.max((w - thumbW) * 0.5f - maxOffset, Math.min((w - thumbW) * 0.5f + maxOffset, thumbX));
            mThumbBounds.set(thumbX, 4f * density, thumbX + thumbW, 4f * density + thumbH);
            mPaint.setColor(mIsTouching ? 0xCC80DEEA : 0x5580DEEA);
            canvas.drawRoundRect(mThumbBounds, thumbH * 0.5f, thumbH * 0.5f, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mInEditMode) return false;

        float density = getResources().getDisplayMetrics().density;
        boolean vert = isVertical();
        float pos = vert ? event.getY() : event.getX();
        float totalLen = vert ? getHeight() : getWidth();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mIsTouching = true;
                mLastTouchPos = pos;
                mAccumulatedDrag = 0f;
                mThumbOffset = 0f;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

                // Tap ends detection
                if (pos < totalLen * 0.25f && mDispatcher != null) {
                    // Tap top or left = scroll up/left
                    if (vert) mDispatcher.onPointerScroll(0, 2.0f);
                    else mDispatcher.onPointerScroll(1, -2.0f);
                } else if (pos > totalLen * 0.75f && mDispatcher != null) {
                    // Tap bottom or right = scroll down/right
                    if (vert) mDispatcher.onPointerScroll(0, -2.0f);
                    else mDispatcher.onPointerScroll(1, 2.0f);
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mDispatcher == null) return true;
                float delta = pos - mLastTouchPos;
                mLastTouchPos = pos;
                mThumbOffset += delta * 0.4f;

                float sens = (mModel.trackpointSensitivity > 0) ? mModel.trackpointSensitivity : 1.5f;
                if (vert) {
                    // Drag up (delta < 0) -> scroll up (+); drag down (delta > 0) -> scroll down (-)
                    mDispatcher.onPointerScroll(0, -delta * sens * 0.25f);
                } else {
                    // Drag right (delta > 0) -> scroll right (+); drag left (delta < 0) -> scroll left (-)
                    mDispatcher.onPointerScroll(1, delta * sens * 0.25f);
                }

                mAccumulatedDrag += Math.abs(delta);
                if (mAccumulatedDrag > 14f * density) {
                    mAccumulatedDrag = 0f;
                    if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mIsTouching = false;
                mThumbOffset = 0f;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
