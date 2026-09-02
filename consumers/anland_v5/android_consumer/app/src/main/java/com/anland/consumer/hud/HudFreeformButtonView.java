package com.anland.consumer.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Visual Freeform Button View with touch down/up states and swipe-up popup support.
 */
public final class HudFreeformButtonView extends View {

    public interface ButtonActionListener {
        void onButtonPress(HudButton button, boolean isDown);
        void onPopupTrigger(HudButton button);
    }

    private final HudButton mModel;
    private final ButtonActionListener mListener;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();

    private boolean mIsPressed;
    private float mDownY;
    private boolean mPopupTriggered;

    public HudFreeformButtonView(Context context, HudButton model, ButtonActionListener listener) {
        super(context);
        this.mModel = model;
        this.mListener = listener;

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);
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
        mBounds.set(2, 2, w - 2, h - 2);

        // Fill
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mIsPressed ? mModel.activeColor : mModel.bgColor);
        mPaint.setAlpha(Math.round(mModel.opacity * 255));
        float corner = mModel.cornerRadiusDp * density;
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // Stroke
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1, Math.round(1.0f * density)));
        mPaint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(mBounds, corner, corner, mPaint);

        // Text
        mTextPaint.setColor(mIsPressed ? 0xFF11111B : mModel.textColor);
        mTextPaint.setTextSize(Math.min(w, h) * 0.35f);
        float textY = h * 0.5f - ((mTextPaint.descent() + mTextPaint.ascent()) * 0.5f);
        canvas.drawText(mModel.label != null ? mModel.label : "BTN", w * 0.5f, textY, mTextPaint);

        // Secondary popup glyph dot if configured
        if (mModel.popupAction != null) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(0xAAFFFFFF);
            canvas.drawCircle(w - 6 * density, 6 * density, 2 * density, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = event.getY();
        float density = getResources().getDisplayMetrics().density;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = y;
                mIsPressed = true;
                mPopupTriggered = false;
                invalidate();
                if (mListener != null) mListener.onButtonPress(mModel, true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mModel.popupAction != null && !mPopupTriggered && (mDownY - y) > 30 * density) {
                    mPopupTriggered = true;
                    mIsPressed = false;
                    invalidate();
                    if (mListener != null) {
                        mListener.onButtonPress(mModel, false);
                        mListener.onPopupTrigger(mModel);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsPressed = false;
                invalidate();
                if (mListener != null && !mPopupTriggered) {
                    mListener.onButtonPress(mModel, false);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
