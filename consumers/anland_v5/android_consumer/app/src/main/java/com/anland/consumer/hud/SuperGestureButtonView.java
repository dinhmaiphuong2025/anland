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
    /** Chevron index the finger leans towards while pressed: 0 up, 1 right, 2 down, 3 left; -1 none. */
    private int mArmDirection = -1;

    // Cached so onDraw allocates nothing and does not touch the resources object per frame.
    private float mDensity = 1f;
    private final Path mChevronPath = new Path();
    private final Path mInnerPath = new Path();
    private final RectF mInner = new RectF();
    private final float[] mInnerRadii = new float[8];
    private float mChevronWing, mChevronDepth, mChevronInset;

    public SuperGestureButtonView(Context context, HudButton model, GestureListener listener) {
        super(context);
        this.mModel = model;
        this.mListener = listener;

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode) {
            mIsPressed = false;
            mGestureFired = false;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDensity = getResources().getDisplayMetrics().density;
        computeChevrons();
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

        float d = mDensity;
        int variant = mModel.styleVariant; // 0 = flat, 1 = classic bevel, 2 = glass outline
        int baseBg = mModel.bgColor != 0 ? mModel.bgColor : HudColors.surfaceHigh;
        float corner = mModel.cornerRadiusDp * d;
        float strokePx = mModel.borderStrokeWidthDp > 0
                ? Math.max(1f, mModel.borderStrokeWidthDp * d)
                : Math.max(1f, d * 0.75f);
        int strokeCol = mModel.borderStrokeWidthDp > 0 ? mModel.borderColor : HudColors.outlineMuted;

        HudChrome.drawSurface(canvas, mPaint, mPath, mBounds, mModel.cornerShape, corner,
                variant == 2 ? Color.TRANSPARENT : baseBg, mModel.opacity, strokePx, strokeCol);

        // Pressed state is a lilac wash plus a rim, not a fill swap: the label stays readable
        // and the three styles keep their own body.
        if (mIsPressed) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(HudColors.withAlpha(HudColors.primary, 0x2E));
            canvas.drawPath(mPath, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, d));
            mPaint.setColor(HudColors.withAlpha(HudColors.primary, 0x99));
            mInner.set(mBounds.left + 1.5f * d, mBounds.top + 1.5f * d,
                    mBounds.right - 1.5f * d, mBounds.bottom - 1.5f * d);
            HudChrome.computeRadii(mModel.cornerShape, Math.max(1f, corner - 1.5f * d), mInnerRadii);
            mInnerPath.reset();
            mInnerPath.addRoundRect(mInner, mInnerRadii, Path.Direction.CW);
            canvas.drawPath(mInnerPath, mPaint);
        } else if (variant == 1) {
            // Classic bevel: recessed hairline, the same treatment as the pad and the D-Pad.
            float inset = 6f * d;
            mInner.set(mBounds.left + inset, mBounds.top + inset,
                    mBounds.right - inset, mBounds.bottom - inset);
            HudChrome.computeRadii(mModel.cornerShape, Math.max(1f, corner - inset * 0.7f), mInnerRadii);
            mInnerPath.reset();
            mInnerPath.addRoundRect(mInner, mInnerRadii, Path.Direction.CW);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, d));
            mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x14));
            canvas.drawPath(mInnerPath, mPaint);
        }

        // Label: vector icon when one is set, otherwise auto-scaled text.
        boolean hasIcon = mModel.iconId != null
                && !HudVectorIcons.ICON_NONE.equals(mModel.iconId)
                && !mModel.iconId.isEmpty();

        int contentColor = mIsPressed ? HudColors.onActive : mModel.textColor;
        if (hasIcon) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(contentColor);
            float iconSize = Math.min(w, h) * 0.28f;
            float iconCx = w * 0.5f;
            if (mModel.textAlign == HudButton.ALIGN_LEFT) {
                iconCx = 10 * d + iconSize * 0.5f;
            } else if (mModel.textAlign == HudButton.ALIGN_RIGHT) {
                iconCx = w - 10 * d - iconSize * 0.5f;
            }
            HudVectorIcons.drawIcon(canvas, mModel.iconId, iconCx, h * 0.5f, iconSize, mPaint);
        } else {
            mTextPaint.setColor(contentColor);
            String label = mModel.label != null ? mModel.label : "SUPER";
            float maxTextWidth = Math.max(10 * d, w - 26 * d);
            float textSize = Math.min(w * 0.20f, Math.min(h * 0.24f, HudType.SUBTITLE_SP * d));
            mTextPaint.setTextSize(textSize);
            float textW = mTextPaint.measureText(label);
            if (textW > maxTextWidth && textW > 0) {
                textSize = Math.max(7 * d, textSize * (maxTextWidth / textW));
                mTextPaint.setTextSize(textSize);
            }
            float textY = h * 0.5f - ((mTextPaint.descent() + mTextPaint.ascent()) * 0.5f);
            float textX;
            if (mModel.textAlign == HudButton.ALIGN_LEFT) {
                mTextPaint.setTextAlign(Paint.Align.LEFT);
                textX = 12 * d;
            } else if (mModel.textAlign == HudButton.ALIGN_RIGHT) {
                mTextPaint.setTextAlign(Paint.Align.RIGHT);
                textX = w - 12 * d;
            } else {
                mTextPaint.setTextAlign(Paint.Align.CENTER);
                textX = w * 0.5f;
            }
            canvas.drawText(label, textX, textY, mTextPaint);
        }

        // Four chevrons inside the edges. The one the finger is leaning towards lights up, so the
        // gesture is readable before the swipe threshold is crossed.
        if (mChevronWing <= 0f) computeChevrons();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1f, d));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        boolean[] active = new boolean[]{
                mArmDirection == 0, mArmDirection == 1, mArmDirection == 2, mArmDirection == 3};
        float[][] tips = new float[][]{
                {cx, mChevronInset}, {w - mChevronInset, cy},
                {cx, h - mChevronInset}, {mChevronInset, cy}};
        for (int i = 0; i < 4; i++) {
            mPaint.setColor(active[i]
                    ? HudColors.primary
                    : HudColors.withAlpha(HudColors.primary, 0x66));
            canvas.save();
            canvas.translate(tips[i][0], tips[i][1]);
            canvas.rotate(i * 90f);
            canvas.drawPath(mChevronPath, mPaint);
            canvas.restore();
        }
    }

    /** One chevron with its tip at the origin opening towards +y, rotated into four edges. */
    private void computeChevrons() {
        float d = mDensity;
        mChevronWing = 2.8f * d;
        mChevronDepth = 2.5f * d;
        mChevronInset = 5f * d;
        mChevronPath.reset();
        mChevronPath.moveTo(-mChevronWing, mChevronDepth);
        mChevronPath.lineTo(0f, 0f);
        mChevronPath.lineTo(mChevronWing, mChevronDepth);
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
                mArmDirection = -1;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!mGestureFired) {
                    float dx = x - mDownX;
                    float dy = y - mDownY;
                    // Lean direction, shown as a lit chevron before the swipe fires.
                    int arm = -1;
                    if (Math.abs(dx) > 3 * density || Math.abs(dy) > 3 * density) {
                        if (Math.abs(dx) > Math.abs(dy)) arm = dx > 0 ? 1 : 3;
                        else arm = dy > 0 ? 2 : 0;
                    }
                    if (arm != mArmDirection) {
                        mArmDirection = arm;
                        invalidate();
                    }
                    if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                        mGestureFired = true;
                        mIsPressed = false;
                        mArmDirection = -1;
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
                mArmDirection = -1;
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
