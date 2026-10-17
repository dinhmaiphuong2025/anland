package com.anland.consumer.hud;

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
 * Unified D-Pad widget that supports continuous sliding between directions.
 * The user can drag their finger across the pad without lifting, and the
 * active direction changes instantly as the finger crosses sector boundaries.
 *
 * Sectors: UP (top), DOWN (bottom), LEFT (left), RIGHT (right), CENTER (dead zone).
 * Diagonal sectors fire both adjacent keys simultaneously (UP+LEFT, UP+RIGHT, etc.).
 */
public final class HudDpadView extends View {

    public static final int DIR_NONE = 0;
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
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();

    private boolean mInEditMode = false;
    private boolean mIsPressed = false;
    // Currently active directions (can have up to 2 for diagonals)
    private boolean mUpActive = false;
    private boolean mDownActive = false;
    private boolean mLeftActive = false;
    private boolean mRightActive = false;

    // Dead zone radius fraction (center of the pad)
    private static final float DEADZONE_FRAC = 0.18f;
    // Diagonal threshold: if angle is within 22.5° of a diagonal, fire both axes
    private static final float DIAG_HALF_ANGLE = 22.5f;

    public HudDpadView(Context context, HudButton model, DpadDispatcher dispatcher) {
        super(context);
        this.mModel = model;
        this.mDispatcher = dispatcher;

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);

        mArrowPaint.setColor(0xCCFFFFFF);
        mArrowPaint.setStyle(Paint.Style.FILL);
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode) releaseAll();
    }

    public HudButton getModel() {
        return mModel;
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
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        mBounds.set(2, 2, w - 2, h - 2);

        // Background circle / rounded rect
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mModel.bgColor);
        mPaint.setAlpha(Math.round(mModel.opacity * 255));
        float corner = mModel.cornerRadiusDp * density;
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // Border stroke
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1, Math.round(1.0f * density)));
        mPaint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // Draw directional arrows with active highlights
        float arrowSize = Math.min(w, h) * 0.16f;
        float arrowOffset = Math.min(w, h) * 0.30f;

        // UP arrow
        drawArrow(canvas, cx, cy - arrowOffset, arrowSize, 0, mUpActive);
        // DOWN arrow
        drawArrow(canvas, cx, cy + arrowOffset, arrowSize, 180, mDownActive);
        // LEFT arrow
        drawArrow(canvas, cx - arrowOffset, cy, arrowSize, 270, mLeftActive);
        // RIGHT arrow
        drawArrow(canvas, cx + arrowOffset, cy, arrowSize, 90, mRightActive);

        // Center dot (dead zone indicator)
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mIsPressed ? 0x44FFFFFF : 0x22FFFFFF);
        canvas.drawCircle(cx, cy, Math.min(w, h) * DEADZONE_FRAC * 0.6f, mPaint);
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float size, float rotation, boolean active) {
        Path path = new Path();
        // Upward-pointing triangle (before rotation)
        path.moveTo(0, -size * 0.6f);
        path.lineTo(-size * 0.5f, size * 0.4f);
        path.lineTo(size * 0.5f, size * 0.4f);
        path.close();

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        mArrowPaint.setColor(active ? mModel.activeColor : 0xAAFFFFFF);
        canvas.drawPath(path, mArrowPaint);
        canvas.restore();
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
            // Compute angle: 0° = right, 90° = down, etc.
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360f;

            // Cardinal + diagonal mapping with 45° sectors
            // UP: 247.5° - 292.5° (pure up)
            // RIGHT: 337.5° - 22.5° (pure right)
            // DOWN: 67.5° - 112.5° (pure down)
            // LEFT: 157.5° - 202.5° (pure left)
            // Diagonals: sectors between cardinals

            // Normalize to check sectors
            if (inRange(angle, 292.5f, 337.5f)) { newUp = true; newRight = true; }      // UP-RIGHT
            else if (inRange(angle, 247.5f, 292.5f)) { newUp = true; }                  // UP
            else if (inRange(angle, 202.5f, 247.5f)) { newUp = true; newLeft = true; }  // UP-LEFT
            else if (inRange(angle, 157.5f, 202.5f)) { newLeft = true; }                // LEFT
            else if (inRange(angle, 112.5f, 157.5f)) { newDown = true; newLeft = true; }// DOWN-LEFT
            else if (inRange(angle, 67.5f, 112.5f)) { newDown = true; }                 // DOWN
            else if (inRange(angle, 22.5f, 67.5f)) { newDown = true; newRight = true; } // DOWN-RIGHT
            else { newRight = true; }                                                     // RIGHT (337.5-360 + 0-22.5)
        }

        // Diff and dispatch changes
        boolean changed = false;
        if (newUp != mUpActive) {
            mUpActive = newUp;
            if (mDispatcher != null) mDispatcher.onDpadPress(DIR_UP, newUp);
            changed = true;
        }
        if (newDown != mDownActive) {
            mDownActive = newDown;
            if (mDispatcher != null) mDispatcher.onDpadPress(DIR_DOWN, newDown);
            changed = true;
        }
        if (newLeft != mLeftActive) {
            mLeftActive = newLeft;
            if (mDispatcher != null) mDispatcher.onDpadPress(DIR_LEFT, newLeft);
            changed = true;
        }
        if (newRight != mRightActive) {
            mRightActive = newRight;
            if (mDispatcher != null) mDispatcher.onDpadPress(DIR_RIGHT, newRight);
            changed = true;
        }

        if (changed) {
            if (mModel.hapticEnabled) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            invalidate();
        }
    }

    private boolean inRange(float angle, float lo, float hi) {
        return angle >= lo && angle < hi;
    }

    private void releaseAll() {
        if (mUpActive) { mUpActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_UP, false); }
        if (mDownActive) { mDownActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_DOWN, false); }
        if (mLeftActive) { mLeftActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_LEFT, false); }
        if (mRightActive) { mRightActive = false; if (mDispatcher != null) mDispatcher.onDpadPress(DIR_RIGHT, false); }
    }
}
