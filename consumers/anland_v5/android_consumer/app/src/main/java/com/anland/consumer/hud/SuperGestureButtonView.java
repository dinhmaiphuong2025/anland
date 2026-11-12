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
    private final Path mPath = new Path();
    private final float[] mRadii = new float[8];

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

        // Background and border are drawn by the design layer. Border width and colour come
        // from the model so the Style tab actually restyles this widget; the previous
        // hardcoded 1.5dp white stroke made both style controls look broken here.
        int targetCol = mIsPressed ? HudColors.primary : mModel.bgColor;
        float corner = mModel.cornerRadiusDp * density;
        float strokePx = mModel.borderStrokeWidthDp > 0
                ? Math.max(1f, mModel.borderStrokeWidthDp * density)
                : 0f;

        HudChrome.drawSurface(canvas, mPaint, mPath, mBounds,
                mModel.cornerShape, corner, targetCol, mModel.opacity,
                strokePx, mModel.borderColor);

        // Label: vector icon when one is set, otherwise auto-scaled text.
        boolean hasIcon = mModel.iconId != null
                && !HudVectorIcons.ICON_NONE.equals(mModel.iconId)
                && !mModel.iconId.isEmpty();

        if (hasIcon) {
            mPaint.setColor(mIsPressed ? HudColors.onActive : mModel.textColor);
            // Leave clearance for the 4-way chevrons drawn along the edges.
            float iconSize = Math.min(w, h) * 0.30f;
            float iconCx = w * 0.5f;
            if (mModel.textAlign == HudButton.ALIGN_LEFT) {
                iconCx = 8 * density + iconSize * 0.5f;
            } else if (mModel.textAlign == HudButton.ALIGN_RIGHT) {
                iconCx = w - 8 * density - iconSize * 0.5f;
            }
            HudVectorIcons.drawIcon(canvas, mModel.iconId, iconCx, h * 0.5f, iconSize, mPaint);
        } else {
            // Main Center Text with safe auto font scaling away from chevrons
            mTextPaint.setColor(mIsPressed ? HudColors.onActive : mModel.textColor);
            String label = mModel.label != null ? mModel.label : "SUPER";
            float maxTextWidth = Math.max(10 * density, w - 30 * density);
            float textSize = Math.min(w * 0.20f, Math.min(h * 0.24f, HudType.SUBTITLE_SP * density));
            mTextPaint.setTextSize(textSize);
            float textW = mTextPaint.measureText(label);
            if (textW > maxTextWidth && textW > 0) {
                textSize = Math.max(7 * density, textSize * (maxTextWidth / textW));
                mTextPaint.setTextSize(textSize);
            }
            float textY = h * 0.5f - ((mTextPaint.descent() + mTextPaint.ascent()) * 0.5f);
            float textX;
            if (mModel.textAlign == HudButton.ALIGN_LEFT) {
                mTextPaint.setTextAlign(Paint.Align.LEFT);
                textX = 14 * density;
            } else if (mModel.textAlign == HudButton.ALIGN_RIGHT) {
                mTextPaint.setTextAlign(Paint.Align.RIGHT);
                textX = w - 14 * density;
            } else {
                mTextPaint.setTextAlign(Paint.Align.CENTER);
                textX = w * 0.5f;
            }
            canvas.drawText(label, textX, textY, mTextPaint);
        }

        // 4-way outward vector chevrons (Top, Bottom, Left, Right)
        mPaint.setColor(HudColors.withAlpha(HudColors.primary, 0xCC));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.8f * density);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float wing = 2.8f * density;
        float depth = 2.5f * density;

        // Up chevron (pointing up)
        float topTip = 4f * density;
        canvas.drawLine(cx - wing, topTip + depth, cx, topTip, mPaint);
        canvas.drawLine(cx, topTip, cx + wing, topTip + depth, mPaint);

        // Down chevron (pointing down)
        float botTip = h - 4f * density;
        canvas.drawLine(cx - wing, botTip - depth, cx, botTip, mPaint);
        canvas.drawLine(cx, botTip, cx + wing, botTip - depth, mPaint);

        // Left chevron (pointing left)
        float leftTip = 4f * density;
        canvas.drawLine(leftTip + depth, cy - wing, leftTip, cy, mPaint);
        canvas.drawLine(leftTip, cy, leftTip + depth, cy + wing, mPaint);

        // Right chevron (pointing right)
        float rightTip = w - 4f * density;
        canvas.drawLine(rightTip - depth, cy - wing, rightTip, cy, mPaint);
        canvas.drawLine(rightTip, cy, rightTip - depth, cy + wing, mPaint);
    }

    private boolean isHapticEnabled() {
        return mModel.hapticEnabled;
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
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!mGestureFired) {
                    float dx = x - mDownX;
                    float dy = y - mDownY;
                    if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                        mGestureFired = true;
                        mIsPressed = false;
                        if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
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
                        if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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
