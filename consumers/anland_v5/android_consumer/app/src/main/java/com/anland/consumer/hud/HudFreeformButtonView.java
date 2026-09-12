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
    // In edit mode the parent HudOverlayView already owns the touch stream
    // (it uses an OnTouchListener to drag-to-move). We must NOT respond to
    // touch ourselves, or the parent would think we have a competing gesture
    // and we would also accidentally trigger button press on every tap.
    private boolean mInEditMode = false;

    public HudFreeformButtonView(Context context, HudButton model, ButtonActionListener listener) {
        super(context);
        this.mModel = model;
        this.mListener = listener;

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        if (editMode) mIsPressed = false;
    }

    public HudButton getModel() {
        return mModel;
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

        // Text with auto font scaling
        mTextPaint.setColor(mIsPressed ? 0xFF11111B : mModel.textColor);
        String label = mModel.label != null ? mModel.label : "BTN";
        float maxTextWidth = w - 12 * density;
        float textSize = Math.min(w, h) * 0.35f;
        mTextPaint.setTextSize(textSize);
        float textW = mTextPaint.measureText(label);
        if (textW > maxTextWidth && textW > 0) {
            mTextPaint.setTextSize(Math.max(8 * density, textSize * (maxTextWidth / textW)));
        }
        float textY = h * 0.5f - ((mTextPaint.descent() + mTextPaint.ascent()) * 0.5f);
        canvas.drawText(label, w * 0.5f, textY, mTextPaint);

        // Secondary popup glyph dot if configured
        if (mModel.popupAction != null) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(0xCC80DEEA);
            canvas.drawCircle(w - 7 * density, 7 * density, 2.5f * density, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Edit mode: the parent's OnTouchListener already owns this widget for
        // drag-to-move. Returning false here is the Android-idiomatic way of
        // saying "I don't want to handle this; my parent will".
        if (mInEditMode) return false;

        float y = event.getY();
        float density = getResources().getDisplayMetrics().density;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = y;
                mIsPressed = true;
                mPopupTriggered = false;
                if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                if (mListener != null) mListener.onButtonPress(mModel, true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mModel.popupAction != null && !mPopupTriggered && (mDownY - y) > 30 * density) {
                    mPopupTriggered = true;
                    mIsPressed = false;
                    if (isHapticEnabled()) performHapticFeedback(HapticFeedbackConstants.CONFIRM);
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
