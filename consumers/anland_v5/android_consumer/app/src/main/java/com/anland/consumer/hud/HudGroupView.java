package com.anland.consumer.hud;

import com.anland.consumer.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A container widget that keeps several keys together so they can be moved,
 * snapped and duplicated as a single unit.
 *
 * The children are ordinary {@link HudFreeformButtonView}s. They are arranged
 * automatically in a centred grid inside the group's content box, so the user
 * never has to position them by hand: dropping a key on the group is enough.
 * Because they are real child views, dragging the group drags everything with
 * it — no per-child bookkeeping is needed.
 */
public final class HudGroupView extends FrameLayout {

    /** Gap between grid cells, in dp. */
    private static final int GAP_DP = 4;
    /** Minimum inner height kept free when the group still has no keys. */
    private static final int EMPTY_MIN_HEIGHT_DP = 48;

    private OnChildSelectListener mChildSelectListener;

    public interface OnChildSelectListener {
        void onChildSelected(HudButton model, View view);
    }

    public void setOnChildSelectListener(OnChildSelectListener l) {
        this.mChildSelectListener = l;
    }

    private final HudButton mModel;
    private final HudFreeformButtonView.ButtonActionListener mListener;
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mPath = new Path();
    private final float[] mRadii = new float[8];
    private boolean mInEditMode = false;
    private boolean mDropTarget = false;

    public HudGroupView(Context context, HudButton model,
                        HudFreeformButtonView.ButtonActionListener listener) {
        super(context);
        this.mModel = model;
        this.mListener = listener;
        setClipChildren(false);
        setClipToPadding(false);
        mFillPaint.setStyle(Paint.Style.FILL);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
        mHintPaint.setTextAlign(Paint.Align.CENTER);
        setWillNotDraw(false);
        buildChildren();
    }

    public HudButton getModel() {
        return mModel;
    }

    public int findDropIndex(float rawX, float rawY) {
        int count = getChildCount();
        if (count == 0) return 0;
        int[] loc = new int[2];
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            child.getLocationOnScreen(loc);
            float cx = loc[0] + child.getWidth() * 0.5f;
            float cy = loc[1] + child.getHeight() * 0.5f;
            // If dropping before this child horizontally in same row or above this row
            if (rawY < loc[1] + child.getHeight() && rawX < cx) {
                return i;
            }
        }
        return count;
    }

    public void setInEditMode(boolean editMode) {
        mInEditMode = editMode;
        rebuildChildren();
    }

    /** Highlight the container while a key is dragged over it. */
    public void setDropTarget(boolean dropTarget) {
        if (mDropTarget == dropTarget) return;
        mDropTarget = dropTarget;
        invalidate();
    }

    /** Re-read the model after the editor added, removed or rebound a child. */
    public void rebuildChildren() {
        buildChildren();
        requestLayout();
        invalidate();
    }

    private void buildChildren() {
        removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (HudButton child : mModel.children) {
            HudFreeformButtonView view = new HudFreeformButtonView(getContext(), child, mListener);
            view.setInEditMode(mInEditMode);
            int w = Math.max(1, Math.round(child.widthDp * density));
            int h = Math.max(1, Math.round(child.heightDp * density));
            addView(view, new FrameLayout.LayoutParams(w, h));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int padPx() {
        // Enforce 4dp outer boundary padding so the container's outer edge is clean and easy to drag
        int configured = Math.max(0, dp(mModel.padDp));
        int minMargin = dp(4);
        return Math.max(configured, minMargin);
    }

    /** Widest / tallest child, i.e. the size of one grid cell. */
    private int cellWidth() {
        int max = 0;
        for (int i = 0; i < getChildCount(); i++)
            max = Math.max(max, getChildAt(i).getMeasuredWidth());
        return max;
    }

    private int cellHeight() {
        int max = 0;
        for (int i = 0; i < getChildCount(); i++)
            max = Math.max(max, getChildAt(i).getMeasuredHeight());
        return max;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mInEditMode) {
            // In edit mode, container intercepts all touch streams so dragging ANYWHERE
            // (even on child keys) drags the entire container as one unit!
            // Child key selection is handled via Tap detection when stationary.
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private int effectiveColumnCount(int contentWidth, int cellWidth, int gap, int count) {
        if (mModel != null) {
            if (mModel.columns > 0) {
                return Math.max(1, mModel.columns);
            } else if (mModel.rows > 0) {
                // If rows specified, derive columns: ceil(count / rows)
                return Math.max(1, (count + mModel.rows - 1) / mModel.rows);
            }
        }
        if (cellWidth <= 0) return Math.max(1, count);
        int cols = (contentWidth + gap) / (cellWidth + gap);
        cols = Math.max(1, Math.min(cols, Math.max(1, count)));
        return cols;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int width = Math.max(1, Math.round(mModel.widthDp * density));
        int height = Math.max(1, Math.round(mModel.heightDp * density));
        int pad = padPx();
        int gap = dp(GAP_DP);
        int count = getChildCount();

        int contentWidth = Math.max(1, width - pad * 2);
        int cellW = 0;
        int cellH = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            // Children are measured below by super.onMeasure(), so use their
            // layout-params size here — that value is authoritative because
            // HudFreeformButtonView always measures itself from the model.
            cellW = Math.max(cellW, child.getLayoutParams().width);
            cellH = Math.max(cellH, child.getLayoutParams().height);
        }

        int needed;
        if (count == 0 || cellH <= 0) {
            needed = pad * 2 + dp(EMPTY_MIN_HEIGHT_DP);
        } else {
            int cols = effectiveColumnCount(contentWidth, cellW, gap, count);
            int rows = (count + cols - 1) / cols;
            needed = pad * 2 + rows * cellH + Math.max(0, rows - 1) * gap;
        }
        if (needed > height) height = needed;
        // Never allow the container to be smaller than the widest key needs.
        int minWidth = pad * 2 + cellW;
        if (count > 0 && minWidth > width) width = minWidth;

        // Keep the model in sync with what we actually render, otherwise the
        // saved profile would store a size that clips the children and the
        // group would "jump" the next time it is rebuilt.
        mModel.widthDp = Math.max(1, Math.round(width / density));
        mModel.heightDp = Math.max(1, Math.round(height / density));

        int wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int hSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

        // If columns/rows mode is enabled, measure children with their stretched cell size
        if (mModel != null && (mModel.columns > 0 || mModel.rows > 0) && count > 0) {
            int cols = effectiveColumnCount(contentWidth, cellW, gap, count);
            int rows = (count + cols - 1) / cols;
            int availW = contentWidth - Math.max(0, cols - 1) * gap;
            int stretchW = (cols > 0 && availW > 0) ? (availW / cols) : cellW;
            int availH = height - pad * 2 - Math.max(0, rows - 1) * gap;
            int stretchH = (rows > 0 && availH > 0) ? (availH / rows) : cellH;

            for (int i = 0; i < count; i++) {
                getChildAt(i).measure(
                        MeasureSpec.makeMeasureSpec(stretchW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(stretchH, MeasureSpec.EXACTLY)
                );
            }
        } else {
            super.onMeasure(wSpec, hSpec);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;
        int pad = padPx();
        int gap = dp(GAP_DP);
        int contentWidth = Math.max(1, getMeasuredWidth() - pad * 2);
        int contentHeight = Math.max(1, getMeasuredHeight() - pad * 2);

        int cellW = Math.max(1, cellWidth());
        int cellH = Math.max(1, cellHeight());
        int cols = effectiveColumnCount(contentWidth, cellW, gap, count);
        int rows = (count + cols - 1) / cols;

        // Auto stretch buttons to fill the container evenly when columns/rows are set
        if (mModel != null && (mModel.columns > 0 || mModel.rows > 0)) {
            if (cols > 0) {
                int availW = contentWidth - Math.max(0, cols - 1) * gap;
                if (availW > 0) cellW = Math.max(1, availW / cols);
            }
            if (rows > 0) {
                int availH = contentHeight - Math.max(0, rows - 1) * gap;
                if (availH > 0) cellH = Math.max(1, availH / rows);
            }
        }

        int gridW = cols * cellW + Math.max(0, cols - 1) * gap;
        int gridH = rows * cellH + Math.max(0, rows - 1) * gap;
        int originX = pad + Math.max(0, (contentWidth - gridW) / 2);
        int originY = pad + Math.max(0, (contentHeight - gridH) / 2);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int row = i / cols;
            int col = i % cols;
            int cellLeft = originX + col * (cellW + gap);
            int cellTop = originY + row * (cellH + gap);
            int cw = (mModel != null && (mModel.columns > 0 || mModel.rows > 0)) ? cellW : child.getMeasuredWidth();
            int ch = (mModel != null && (mModel.columns > 0 || mModel.rows > 0)) ? cellH : child.getMeasuredHeight();
            int cx = cellLeft + (cellW - cw) / 2;
            int cy = cellTop + (cellH - ch) / 2;
            child.layout(cx, cy, cx + cw, cy + ch);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // A faint container wash makes the group readable while editing without
        // hiding the desktop behind it.
        float corner = mModel.cornerRadiusDp * getResources().getDisplayMetrics().density;
        mRect.set(1, 1, getWidth() - 1, getHeight() - 1);
        HudFreeformButtonView.computeRadii(mModel.cornerShape, corner, mRadii);
        mPath.reset();
        mPath.addRoundRect(mRect, mRadii, Path.Direction.CW);

        mFillPaint.setColor(mModel.bgColor);
        int alpha = Math.round(Math.max(0.0f, Math.min(1.0f, mModel.opacity)) * 255);
        if (mDropTarget) alpha = Math.min(255, alpha + 70);
        mFillPaint.setAlpha(alpha);
        canvas.drawPath(mPath, mFillPaint);

        if (mInEditMode && getChildCount() == 0) {
            mHintPaint.setColor(0xB3FFFFFF);
            mHintPaint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
            float textY = getHeight() * 0.5f
                    - ((mHintPaint.descent() + mHintPaint.ascent()) * 0.5f);
            canvas.drawText(getContext().getString(R.string.hud_group_drop_hint),
                    getWidth() * 0.5f, textY, mHintPaint);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // Border goes on top of the keys so the container stays readable even
        // when the children cover its whole surface.
        float density = getResources().getDisplayMetrics().density;
        float stroke = Math.max(1f, 1.5f * density);
        float corner = mModel.cornerRadiusDp * density;
        mRect.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
        HudFreeformButtonView.computeRadii(mModel.cornerShape, corner, mRadii);
        mPath.reset();
        mPath.addRoundRect(mRect, mRadii, Path.Direction.CW);

        if (mDropTarget) {
            mBorderPaint.setPathEffect(null);
            mBorderPaint.setStrokeWidth(stroke);
            mBorderPaint.setColor(0xFF80DEEA);
            canvas.drawPath(mPath, mBorderPaint);
        } else if (mInEditMode) {
            // Only draw dashed outline in edit mode if borderStrokeWidthDp > 0
            if (mModel.borderStrokeWidthDp > 0) {
                mBorderPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
                mBorderPaint.setStrokeWidth(mModel.borderStrokeWidthDp * density);
                mBorderPaint.setColor(mModel.borderColor != 0 ? mModel.borderColor : 0x8A80DEEA);
                canvas.drawPath(mPath, mBorderPaint);
            }
        } else {
            // Runtime: only draw border if borderStrokeWidthDp > 0
            if (mModel.borderStrokeWidthDp > 0) {
                mBorderPaint.setPathEffect(null);
                mBorderPaint.setStrokeWidth(mModel.borderStrokeWidthDp * density);
                mBorderPaint.setColor(mModel.borderColor != 0 ? mModel.borderColor : 0x33FFFFFF);
                canvas.drawPath(mPath, mBorderPaint);
            }
        }
    }
}
