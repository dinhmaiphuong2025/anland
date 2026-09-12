package com.anland.consumer.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Super Gesture Button View.
 * Handles Tap (Super key/sticky), Long-press (Lock), and 4-way direction Swipes
 * (Left, Right, Up, Down) for smooth workspace and WM navigation.
 */
public final class SuperGestureButtonView extends View {

    public interface GestureListener {
        void onSingleTap(HudButton button);
        void onLongPress(HudButton button);
        void onSwipe(HudButton button, int direction); // 0=Left, 1=Right, 2=Up, 3=Down
    }

    public static final int DIR_LEFT = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_UP = 2;
    public static final int DIR_DOWN = 3;

    private final HudButton mModel;
    private final GestureListener mListener;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();

    private float mDownX;
    private float mDownY;
    private long mDownTime;
    private boolean mIsPressed;
    private boolean mGestureFired;
    private boolean mInEditMode = false;

    public SuperGestureButtonView(Context context, HudButton model, GestureListener listener) {
        super(context);
        this.mModel = model;
        this.mListener = listener;

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode) {
            mIsPressed = false;
            mGestureFired = false;
        }
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

        // Background
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mIsPressed ? 0xFF80DEEA : mModel.bgColor);
        mPaint.setAlpha(Math.round(mModel.opacity * 255));
        float corner = mModel.cornerRadiusDp * density;
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // Border Stroke
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1, Math.round(1.5f * density)));
        mPaint.setColor(0x88FFFFFF);
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // Main Center Text with auto font scaling
        mTextPaint.setColor(mIsPressed ? 0xFF11111B : mModel.textColor);
        String label = mModel.label != null ? mModel.label : "SUPER";
        float maxTextWidth = w - 16 * density;
        float textSize = Math.min(w, h) * 0.28f;
        mTextPaint.setTextSize(textSize);
        float textW = mTextPaint.measureText(label);
        if (textW > maxTextWidth && textW > 0) {
            mTextPaint.setTextSize(Math.max(8 * density, textSize * (maxTextWidth / textW)));
        }
        float textY = h * 0.5f - ((mTextPaint.descent() + mTextPaint.ascent()) * 0.5f);
        canvas.drawText(label, w * 0.5f, textY, mTextPaint);

        // Direction indicator ticks (top, bottom, left, right)
        mPaint.setColor(0xCC80DEEA);
        mPaint.setStrokeWidth(2.5f * density);
        canvas.drawLine(w * 0.5f, 4 * density, w * 0.5f, 9 * density, mPaint); // Up tick
        canvas.drawLine(w * 0.5f, h - 9 * density, w * 0.5f, h - 4 * density, mPaint); // Down tick
        canvas.drawLine(4 * density, h * 0.5f, 9 * density, h * 0.5f, mPaint); // Left tick
        canvas.drawLine(w - 9 * density, h * 0.5f, w - 4 * density, h * 0.5f, mPaint); // Right tick
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mInEditMode) return false;
        float x = event.getX();
        float y = event.getY();
        float density = getResources().getDisplayMetrics().density;
        float threshold = mModel.swipeThresholdDp * density;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                mDownTime = System.currentTimeMillis();
                mIsPressed = true;
                mGestureFired = false;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!mGestureFired) {
                    float dx = x - mDownX;
                    float dy = y - mDownY;
                    if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                        mGestureFired = true;
                        mIsPressed = false;
                        performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
                        invalidate();
                        if (mListener != null) {
                            if (Math.abs(dx) > Math.abs(dy)) {
                                mListener.onSwipe(mModel, dx > 0 ? DIR_RIGHT : DIR_LEFT);
                            } else {
                                mListener.onSwipe(mModel, dy > 0 ? DIR_DOWN : DIR_UP);
                            }
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsPressed = false;
                invalidate();
                if (!mGestureFired && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    long duration = System.currentTimeMillis() - mDownTime;
                    if (duration > 450) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        if (mListener != null) mListener.onLongPress(mModel);
                    } else {
                        if (mListener != null) mListener.onSingleTap(mModel);
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
