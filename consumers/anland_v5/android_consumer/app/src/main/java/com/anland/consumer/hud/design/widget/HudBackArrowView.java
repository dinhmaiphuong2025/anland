package com.anland.consumer.hud.design.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.anland.consumer.hud.design.HudColors;

/**
 * Back chevron drawn on Canvas, so it renders identically under any host theme and needs no
 * drawable asset.
 */
public final class HudBackArrowView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HudBackArrowView(Context ctx) {
        super(ctx);
        mPaint.setColor(HudColors.textPrimary);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int color) {
        mPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(16 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        mPaint.setStrokeWidth(2.2f * density);
        float w = getWidth();
        float h = getHeight();
        float midY = h * 0.5f;
        float startX = 3f * density;
        float endX = w - 2f * density;

        canvas.drawLine(startX, midY, endX, midY, mPaint);
        float arm = 5f * density;
        canvas.drawLine(startX, midY, startX + arm, midY - arm, mPaint);
        canvas.drawLine(startX, midY, startX + arm, midY + arm, mPaint);
    }
}
