package com.anland.consumer.hud;

import com.anland.consumer.hud.design.HudColors;
import com.anland.consumer.hud.design.HudChrome;

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
 * Customizable On-Screen Scroll Bar widget for HUD.
 * Automatically adapts between Vertical and Horizontal orientations based on aspect ratio.
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
    private final RectF mBounds = new RectF();
    private final RectF mThumbBounds = new RectF();
    private final Path mPath = new Path();
    private final Path mInnerPath = new Path();
    private final RectF mInner = new RectF();
    private final float[] mInnerRadii = new float[8];
    private float mDensity = 1f;
    private final float[] mRadii = new float[8];
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

    private boolean isHorizontal() {
        if ("horizontal".equalsIgnoreCase(mModel.trackpointMode)) return true;
        if ("vertical".equalsIgnoreCase(mModel.trackpointMode)) return false;
        return mModel.widthDp > mModel.heightDp;
    }

    private boolean isHapticEnabled() {
        return mModel.hapticEnabled;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mDensity = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int w = Math.round(mModel.widthDp * density);
        int h = Math.round(mModel.heightDp * density);
        setMeasuredDimension(w, h);
    }

    /**
     * Filled chevron with the D-Pad profile: tip at the origin, notch in the middle of the
     * trailing edge, rotated into place. size is the half-span of the arms.
     */
    private void drawChevron(Canvas canvas, float cx, float cy, float size, float rotation, Paint paint) {
        float s = size;
        mArrowPath1.reset();
        mArrowPath1.moveTo(0f, -s * 0.55f);
        mArrowPath1.lineTo(-s * 0.5f, s * 0.35f);
        mArrowPath1.lineTo(-s * 0.28f, s * 0.35f);
        mArrowPath1.lineTo(0f, -s * 0.1f);
        mArrowPath1.lineTo(s * 0.28f, s * 0.35f);
        mArrowPath1.lineTo(s * 0.5f, s * 0.35f);
        mArrowPath1.close();
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        canvas.drawPath(mArrowPath1, paint);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = mDensity;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean isHoriz = isHorizontal();
        float corner = mModel.cornerRadiusDp * density;
        mBounds.set(2, 2, w - 2, h - 2);

        // Same three styles as the pad, the D-Pad and the gesture key:
        // 0 = minimal flat, 1 = classic bevel (arrows + grip), 2 = glass outline.
        int variant = mModel.styleVariant;
        boolean bevel = variant == 1;
        boolean glass = variant == 2;
        boolean showArrows = bevel;

        int baseBg = mModel.bgColor != 0 ? mModel.bgColor : HudColors.surfaceHigh;
        int accent = mModel.activeColor != 0 ? mModel.activeColor : HudColors.primary;
        float strokePx = mModel.borderStrokeWidthDp > 0
                ? Math.max(1f, mModel.borderStrokeWidthDp * density)
                : Math.max(1f, density * 0.75f);
        int strokeCol = mModel.borderStrokeWidthDp > 0 ? mModel.borderColor : HudColors.outlineMuted;

        HudChrome.drawSurface(canvas, mPaint, mPath, mBounds, mModel.cornerShape, corner,
                glass ? Color.TRANSPARENT : baseBg, mModel.opacity, strokePx, strokeCol);

        if (bevel) {
            // Recessed hairline: the bevel body the other widgets use for this style.
            float inset = 5f * density;
            mInner.set(mBounds.left + inset, mBounds.top + inset,
                    mBounds.right - inset, mBounds.bottom - inset);
            HudChrome.computeRadii(mModel.cornerShape, Math.max(1f, corner - inset), mInnerRadii);
            mInnerPath.reset();
            mInnerPath.addRoundRect(mInner, mInnerRadii, Path.Direction.CW);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, density));
            mPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x14));
            canvas.drawPath(mInnerPath, mPaint);
        }

        // Arrows belong to the bevel style only; the flat and glass styles are just the thumb.
        // They use the D-Pad chevron geometry so the whole HUD shares one arrow shape.
        if (bevel) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(HudColors.withAlpha(accent, 0x99));
            if (!isHoriz) {
                float cx = w * 0.5f;
                float arrowPad = Math.max(4f * density, h * 0.04f);
                float arrowSize = Math.min(w * 0.30f, 8f * density);
                drawChevron(canvas, cx, arrowPad, arrowSize, 0f, mPaint);
                drawChevron(canvas, cx, h - arrowPad, arrowSize, 180f, mPaint);
            } else {
                float cy = h * 0.5f;
                float arrowPad = Math.max(4f * density, w * 0.04f);
                float arrowSize = Math.min(h * 0.30f, 8f * density);
                drawChevron(canvas, arrowPad, cy, arrowSize, 270f, mPaint);
                drawChevron(canvas, w - arrowPad, cy, arrowSize, 90f, mPaint);
            }
        }

        // One slim thumb for every style: the bevel is told apart by its arrows and hairline,
        // not by a heavier grab handle.
        int thumbFill = glass
                ? (mIsTouching ? HudColors.withAlpha(accent, 0x33) : Color.TRANSPARENT)
                : HudColors.withAlpha(accent, mIsTouching ? 0xEE : 0x59);
        int thumbStroke = HudColors.withAlpha(accent, mIsTouching ? 0xEE : 0x66);

        if (!isHoriz) {
            // ===== VERTICAL =====
            float cx = w * 0.5f;
            float arrowPad = Math.max(4f * density, h * 0.04f);
            float arrowSize = Math.min(w * 0.30f, 8f * density);
            float reservedEnds = showArrows
                    ? arrowPad + arrowSize * 1.1f + 4f * density
                    : 4f * density;
            float availableTrack = h - 2 * reservedEnds;
            float thumbH = Math.max(18f * density, Math.min(availableTrack * 0.40f, 40f * density));
            float thumbW = Math.max(5f * density, w * 0.28f);
            float maxTravel = (availableTrack - thumbH) * 0.5f;
            if (maxTravel < 0) maxTravel = 0;
            mThumbOffset = Math.max(-maxTravel, Math.min(maxTravel, mThumbOffset));
            float thumbY = (h - thumbH) * 0.5f + mThumbOffset;
            float thumbX = (w - thumbW) * 0.5f;

            mThumbBounds.set(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH);
            float r = thumbW * 0.5f;
            if (thumbFill != Color.TRANSPARENT) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(thumbFill);
                canvas.drawRoundRect(mThumbBounds, r, r, mPaint);
            }
            if (glass) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(Math.max(1f, density));
                mPaint.setColor(thumbStroke);
                canvas.drawRoundRect(mThumbBounds, r, r, mPaint);
            }
        } else {
            // ===== HORIZONTAL =====
            float cy = h * 0.5f;
            float arrowPad = Math.max(4f * density, w * 0.04f);
            float arrowSize = Math.min(h * 0.30f, 8f * density);
            float reservedEnds = showArrows
                    ? arrowPad + arrowSize * 1.1f + 4f * density
                    : 4f * density;
            float availableTrack = w - 2 * reservedEnds;
            float thumbW = Math.max(18f * density, Math.min(availableTrack * 0.40f, 40f * density));
            float thumbH = Math.max(5f * density, h * 0.28f);
            float maxTravel = (availableTrack - thumbW) * 0.5f;
            if (maxTravel < 0) maxTravel = 0;
            mThumbOffset = Math.max(-maxTravel, Math.min(maxTravel, mThumbOffset));
            float thumbX = (w - thumbW) * 0.5f + mThumbOffset;
            float thumbY = (h - thumbH) * 0.5f;

            mThumbBounds.set(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH);
            float r = thumbH * 0.5f;
            if (thumbFill != Color.TRANSPARENT) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(thumbFill);
                canvas.drawRoundRect(mThumbBounds, r, r, mPaint);
            }
            if (glass) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(Math.max(1f, density));
                mPaint.setColor(thumbStroke);
                canvas.drawRoundRect(mThumbBounds, r, r, mPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mInEditMode) return false;

        float density = getResources().getDisplayMetrics().density;
        boolean isHoriz = isHorizontal();
        float pos = isHoriz ? event.getX() : event.getY();
        float totalLen = isHoriz ? getWidth() : getHeight();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mIsTouching = true;
                mLastTouchPos = pos;
                mAccumulatedDrag = 0f;
                mThumbOffset = 0f;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

                // Tap ends detection
                if (pos < totalLen * 0.25f && mDispatcher != null) {
                    // Tap top or left = scroll up
                    if (isHoriz && "horizontal".equalsIgnoreCase(mModel.trackpointMode)) {
                        mDispatcher.onPointerScroll(1, -2.0f);
                    } else {
                        mDispatcher.onPointerScroll(0, 2.0f);
                    }
                } else if (pos > totalLen * 0.75f && mDispatcher != null) {
                    // Tap bottom or right = scroll down
                    if (isHoriz && "horizontal".equalsIgnoreCase(mModel.trackpointMode)) {
                        mDispatcher.onPointerScroll(1, 2.0f);
                    } else {
                        mDispatcher.onPointerScroll(0, -2.0f);
                    }
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mDispatcher == null) return true;
                float delta = pos - mLastTouchPos;
                mLastTouchPos = pos;
                mThumbOffset += delta * 0.6f;

                float sens = (mModel.trackpointSensitivity > 0) ? mModel.trackpointSensitivity : 1.5f;
                if (!isHoriz) {
                    // Drag up (delta < 0) -> scroll up (+); drag down (delta > 0) -> scroll down (-)
                    mDispatcher.onPointerScroll(0, -delta * sens * 0.25f);
                } else {
                    // Drag left (delta < 0) -> scroll up (+); drag right (delta > 0) -> scroll down (-)
                    if ("horizontal".equalsIgnoreCase(mModel.trackpointMode)) {
                        mDispatcher.onPointerScroll(1, delta * sens * 0.25f);
                    } else {
                        mDispatcher.onPointerScroll(0, -delta * sens * 0.25f);
                    }
                }

                mAccumulatedDrag += Math.abs(delta);
                if (mAccumulatedDrag > 12f * density) {
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
