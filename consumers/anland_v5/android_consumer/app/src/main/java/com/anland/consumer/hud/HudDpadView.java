package com.anland.consumer.hud;

import com.anland.consumer.hud.design.HudChrome;
import com.anland.consumer.hud.design.HudColors;

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
 * Premium Handheld Console D-Pad widget with sliding continuous direction tracking.
 * Features:
 * - Authentic 3D sculpted 4-way cross arms with tactile center dish (Steam Deck / DualSense style).
 * - Smooth sliding between directions with active sector illumination.
 * - Simultaneous diagonal sector activation (UP+LEFT, UP+RIGHT, DOWN+LEFT, DOWN+RIGHT).
 * - Per-widget haptic feedback toggle.
 */
public final class HudDpadView extends View {

    public static final int DIR_UP = 1;
    public static final int DIR_DOWN = 2;
    public static final int DIR_LEFT = 3;
    public static final int DIR_RIGHT = 4;

    public interface DpadDispatcher {
        void onDpadPress(int direction, boolean pressed);
    }

    private final HudButton mModel;
    private final DpadDispatcher mDispatcher;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();
    private final RectF mInner = new RectF();
    private final Path mPath = new Path();
    /** Single arm outline, rotated into each direction at draw time. */
    private final Path mArmPath = new Path();
    private final Path mInnerPath = new Path();
    private final Path mChevronPath = new Path();
    private final float[] mInnerRadii = new float[8];

    // Geometry is rebuilt on size change; onDraw only draws.
    private float mDensity = 1f;
    private float mCx, mCy, mRadius, mArmW, mArmLen, mArmCorner, mGap;
    private float mChevronOffset, mChevronSize;

    private boolean mInEditMode = false;
    private boolean mIsPressed = false;
    private boolean mUpActive = false;
    private boolean mDownActive = false;
    private boolean mLeftActive = false;
    private boolean mRightActive = false;

    // Dead zone fraction
    private static final float DEADZONE_FRAC = 0.18f;

    public HudDpadView(Context context, HudButton model, DpadDispatcher dispatcher) {
        super(context);
        this.mModel = model;
        this.mDispatcher = dispatcher;


        mArrowPaint.setStyle(Paint.Style.FILL);
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode) releaseAll();
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDensity = getResources().getDisplayMetrics().density;
        computeGeometry();
    }

    /** One-time geometry for the four arms, the chevrons and the recessed ring. */
    private void computeGeometry() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = mDensity;
        float minDim = Math.min(w, h);
        mCx = w * 0.5f;
        mCy = h * 0.5f;
        mRadius = minDim * 0.5f - 2f * d;
        mArmW = minDim * 0.28f;
        mArmLen = mRadius * 0.85f;
        mGap = 3f * d;
        mArmCorner = Math.min(8f * d, mArmW * 0.35f);
        mChevronOffset = mArmLen * 0.62f;
        mChevronSize = Math.min(mArmW * 0.62f, mRadius * 0.22f);

        // One arm, drawn four times by rotation. The outer end is rounded, the inner end tapers
        // to a point so the four directions read as four tips meeting at the centre instead of
        // four blocks butting against each other.
        float half = mArmW * 0.5f;
        float left = mCx - half;
        float right = mCx + half;
        float top = mCy - mArmLen;
        float innerY = mCy - mArmW * 0.7f;
        float tipY = mCy - mGap;
        mArmPath.reset();
        mArmPath.moveTo(left, top + mArmCorner);
        mArmPath.quadTo(left, top, left + mArmCorner, top);
        mArmPath.lineTo(right - mArmCorner, top);
        mArmPath.quadTo(right, top, right, top + mArmCorner);
        mArmPath.lineTo(right, innerY);
        mArmPath.lineTo(mCx, tipY);
        mArmPath.lineTo(left, innerY);
        mArmPath.close();

        float s = mChevronSize;
        mChevronPath.reset();
        mChevronPath.moveTo(0f, -s * 0.55f);
        mChevronPath.lineTo(-s * 0.5f, s * 0.35f);
        mChevronPath.lineTo(-s * 0.28f, s * 0.35f);
        mChevronPath.lineTo(0f, -s * 0.1f);
        mChevronPath.lineTo(s * 0.28f, s * 0.35f);
        mChevronPath.lineTo(s * 0.5f, s * 0.35f);
        mChevronPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (mRadius <= 0f) computeGeometry();

        float d = mDensity;
        int variant = mModel.styleVariant; // 0 = flat, 1 = classic bevel, 2 = glass outline
        int accent = mModel.activeColor != 0 ? mModel.activeColor : HudColors.primary;
        int baseBg = mModel.bgColor != 0 ? mModel.bgColor : HudColors.surfaceHigh;
        float strokePx = mModel.borderStrokeWidthDp > 0
                ? Math.max(1f, mModel.borderStrokeWidthDp * d)
                : Math.max(1f, d * 0.75f);
        int strokeCol = mModel.borderStrokeWidthDp > 0 ? mModel.borderColor : HudColors.outlineMuted;
        boolean glass = variant == 2;

        mBounds.set(2f, 2f, w - 2f, h - 2f);
        HudChrome.drawSurface(canvas, mPaint, mPath, mBounds, mModel.cornerShape, mRadius,
                glass ? Color.TRANSPARENT : baseBg, mModel.opacity, strokePx, strokeCol);

        // Classic bevel carries the recessed hairline; the flat style has nothing inside the
        // border, and the glass style is transparent by definition.
        if (variant == 1) {
            float inset = 6f * d;
            mInner.set(mBounds.left + inset, mBounds.top + inset,
                    mBounds.right - inset, mBounds.bottom - inset);
            HudChrome.computeRadii(mModel.cornerShape, Math.max(2f, mRadius - inset), mInnerRadii);
            mInnerPath.reset();
            mInnerPath.addRoundRect(mInner, mInnerRadii, Path.Direction.CW);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, d));
            mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x14));
            canvas.drawPath(mInnerPath, mPaint);
        }

        // Arms: one tapered path, rotated into the four directions.
        int armStroke = HudColors.withAlpha(HudColors.textPrimary, 0x59);
        for (int i = 0; i < 4; i++) {
            int rot = i * 90;               // 0 = up, then clockwise
            boolean active = (i == 0 && mUpActive)
                    || (i == 1 && mRightActive)
                    || (i == 2 && mDownActive)
                    || (i == 3 && mLeftActive);
            canvas.save();
            canvas.rotate(rot, mCx, mCy);
            if (glass) {
                if (active) {
                    mArmPaint.setStyle(Paint.Style.FILL);
                    mArmPaint.setColor(HudColors.withAlpha(accent, 0x33));
                    canvas.drawPath(mArmPath, mArmPaint);
                }
                mArmPaint.setStyle(Paint.Style.STROKE);
                mArmPaint.setStrokeWidth(Math.max(1f, d));
                mArmPaint.setColor(active ? accent : armStroke);
                canvas.drawPath(mArmPath, mArmPaint);
            } else {
                mArmPaint.setStyle(Paint.Style.FILL);
                mArmPaint.setColor(active ? HudColors.withAlpha(accent, 0xD9)
                        : (variant == 1 ? HudColors.surfaceInset : HudColors.surfaceTint));
                canvas.drawPath(mArmPath, mArmPaint);
                if (active) {
                    mArmPaint.setStyle(Paint.Style.STROKE);
                    mArmPaint.setStrokeWidth(Math.max(1f, d));
                    mArmPaint.setColor(accent);
                    canvas.drawPath(mArmPath, mArmPaint);
                }
            }
            canvas.restore();
        }

        // Single centre dot as the thumb's anchor; the old pivot dish and jewel were two marks
        // for the same job.
        if (!glass) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, mIsPressed ? 0x4D : 0x33));
            canvas.drawCircle(mCx, mCy, 2f * d, mPaint);
        }

        // Chevrons.
        mArrowPaint.setStyle(Paint.Style.FILL);
        for (int dir = DIR_UP; dir <= DIR_RIGHT; dir++) {
            boolean active = (dir == DIR_UP && mUpActive)
                    || (dir == DIR_DOWN && mDownActive)
                    || (dir == DIR_LEFT && mLeftActive)
                    || (dir == DIR_RIGHT && mRightActive);
            float tx = mCx;
            float ty = mCy;
            float rot = 0f;
            if (dir == DIR_UP) ty -= mChevronOffset;
            else if (dir == DIR_DOWN) { ty += mChevronOffset; rot = 180f; }
            else if (dir == DIR_LEFT) { tx -= mChevronOffset; rot = 270f; }
            else { tx += mChevronOffset; rot = 90f; }
            mArrowPaint.setColor(active ? accent : HudColors.withAlpha(HudColors.textPrimary, 0x73));
            canvas.save();
            canvas.translate(tx, ty);
            canvas.rotate(rot);
            canvas.drawPath(mChevronPath, mArrowPaint);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mInEditMode) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsPressed = true;
                updateDirection(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                updateDirection(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsPressed = false;
                releaseAll();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateDirection(float x, float y) {
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxR = Math.min(w, h) * 0.5f;

        boolean newUp = false, newDown = false, newLeft = false, newRight = false;

        if (dist > maxR * DEADZONE_FRAC) {
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360f;

            // Cardinal + diagonal mapping with 45° sectors
            if (inRange(angle, 292.5f, 337.5f)) { newUp = true; newRight = true; }      // UP-RIGHT
            else if (inRange(angle, 247.5f, 292.5f)) { newUp = true; }                  // UP
            else if (inRange(angle, 202.5f, 247.5f)) { newUp = true; newLeft = true; }  // UP-LEFT
            else if (inRange(angle, 157.5f, 202.5f)) { newLeft = true; }                // LEFT
            else if (inRange(angle, 112.5f, 157.5f)) { newDown = true; newLeft = true; }// DOWN-LEFT
            else if (inRange(angle, 67.5f, 112.5f)) { newDown = true; }                 // DOWN
            else if (inRange(angle, 22.5f, 67.5f)) { newDown = true; newRight = true; } // DOWN-RIGHT
            else { newRight = true; }                                                     // RIGHT
        }

        boolean changed = newUp != mUpActive || newDown != mDownActive
                || newLeft != mLeftActive || newRight != mRightActive;
        // Release old directions before pressing their opposites, so a release
        // cannot overwrite the new direction's hat axis with zero.
        if (mUpActive && !newUp) { mUpActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_UP, false); }
        if (mDownActive && !newDown) { mDownActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_DOWN, false); }
        if (mLeftActive && !newLeft) { mLeftActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_LEFT, false); }
        if (mRightActive && !newRight) { mRightActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_RIGHT, false); }
        if (!mUpActive && newUp) { mUpActive = true; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_UP, true); }
        if (!mDownActive && newDown) { mDownActive = true; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_DOWN, true); }
        if (!mLeftActive && newLeft) { mLeftActive = true; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_LEFT, true); }
        if (!mRightActive && newRight) { mRightActive = true; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_RIGHT, true); }

        if (changed) {
            if (isHapticEnabled()) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            invalidate();
        }
    }

    private boolean inRange(float angle, float lo, float hi) {
        return angle >= lo && angle < hi;
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    private void releaseAll() {
        if (mUpActive) { mUpActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_UP, false); }
        if (mDownActive) { mDownActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_DOWN, false); }
        if (mLeftActive) { mLeftActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_LEFT, false); }
        if (mRightActive) { mRightActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_RIGHT, false); }
    }
}
