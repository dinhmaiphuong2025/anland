package com.anland.consumer.hud.design.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.anland.consumer.hud.design.HudColors;

/**
 * Minimal Canvas-drawn toggle switch.
 *
 * <p>A platform {@code Switch} relies on theme-provided thumb and track drawables. Inside the
 * floating HUD inspector the host activity uses a legacy platform theme, where those drawables
 * can resolve to transparent assets and leave only the ON/OFF text visible. Drawing the capsule
 * and thumb directly keeps the control readable regardless of the host theme.
 */
public final class HudToggleView extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(HudToggleView view, boolean checked);
    }

    private static final int TRACK_OFF = 0xFF666666;
    private static final int THUMB_OFF = 0xFFCFCFCF;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTrackRect = new RectF();

    private boolean mChecked;
    private OnCheckedChangeListener mListener;

    public HudToggleView(Context ctx) {
        super(ctx);
        setClickable(true);
        setFocusable(true);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mListener = listener;
    }

    public boolean isChecked() {
        return mChecked;
    }

    /** Programmatic update; never notifies the listener. */
    public void setChecked(boolean checked) {
        if (mChecked == checked) return;
        mChecked = checked;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(Math.round(42 * getResources().getDisplayMetrics().density), widthMeasureSpec);
        int height = resolveSize(Math.round(24 * getResources().getDisplayMetrics().density), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = h * 0.5f;
        mTrackRect.set(0f, 0f, w, h);

        mTrackPaint.setColor(mChecked ? HudColors.primary : TRACK_OFF);
        canvas.drawRoundRect(mTrackRect, radius, radius, mTrackPaint);

        float pad = Math.max(1f, 2.5f * getResources().getDisplayMetrics().density);
        float thumbRadius = radius - pad;
        float thumbCx = mChecked ? (w - radius) : radius;
        mThumbPaint.setColor(mChecked ? HudColors.onPrimary : THUMB_OFF);
        canvas.drawCircle(thumbCx, radius, thumbRadius, mThumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                setAlpha(0.82f);
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                setAlpha(1f);
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                setAlpha(1f);
                if (event.getX() >= 0 && event.getX() <= getWidth()
                        && event.getY() >= 0 && event.getY() <= getHeight()) {
                    performClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        mChecked = !mChecked;
        invalidate();
        if (mListener != null) mListener.onCheckedChanged(this, mChecked);
        return true;
    }
}
