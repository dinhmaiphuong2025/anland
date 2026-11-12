package com.anland.consumer.hud;

import com.anland.consumer.hud.design.HudColors;

import com.anland.consumer.hud.design.HudChrome;
import com.anland.consumer.hud.design.HudType;
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
    private int mDropPreviewIndex = -1;
    private int mDropPreviewWidthPx = 0;
    private int mDropPreviewHeightPx = 0;
    private final Paint mPreviewFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPreviewBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        mPreviewFillPaint.setStyle(Paint.Style.FILL);
        mPreviewFillPaint.setColor(HudColors.withAlpha(HudColors.primarySoft, 0x33));
        mPreviewBorderPaint.setStyle(Paint.Style.STROKE);
        mPreviewBorderPaint.setStrokeWidth(dp(1.5f));
        mPreviewBorderPaint.setColor(HudColors.primarySoft);
        mPreviewBorderPaint.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(3)}, 0));
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(Math.max(1f, 1f * getResources().getDisplayMetrics().density));
        mGridPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0x1F));
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
        getLocationOnScreen(loc);
        float x = rawX - loc[0];
        float y = rawY - loc[1];

        // Use the persisted, pre-preview geometry so the reflow drawn by the
        // drop preview cannot move the insertion point around in a feedback loop.
        float density = getResources().getDisplayMetrics().density;
        int width = Math.max(1, Math.round(mModel.widthDp * density));
        int height = Math.max(1, Math.round(mModel.heightDp * density));
        int pad = padPx();
        int gap = dp(GAP_DP);
        int contentWidth = Math.max(1, width - pad * 2);
        int contentHeight = Math.max(1, height - pad * 2);
        int cellW = 0;
        int cellH = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            cellW = Math.max(cellW, child.getLayoutParams().width);
            cellH = Math.max(cellH, child.getLayoutParams().height);
        }
        cellW = Math.max(1, cellW);
        cellH = Math.max(1, cellH);
        int cols = effectiveColumnCount(contentWidth, cellW, gap, count);
        int rows = Math.max(1, (count + cols - 1) / cols);
        boolean stretch = mModel != null && (mModel.columns > 0 || mModel.rows > 0);
        if (stretch) {
            int availW = contentWidth - Math.max(0, cols - 1) * gap;
            if (availW > 0) cellW = Math.max(1, availW / cols);
            int availH = contentHeight - Math.max(0, rows - 1) * gap;
            if (availH > 0) cellH = Math.max(1, availH / rows);
        }
        int gridW = cols * cellW + Math.max(0, cols - 1) * gap;
        int gridH = rows * cellH + Math.max(0, rows - 1) * gap;
        int originX = pad + Math.max(0, (contentWidth - gridW) / 2);
        int originY = pad + Math.max(0, (contentHeight - gridH) / 2);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int childW = stretch ? cellW : child.getLayoutParams().width;
            int childH = stretch ? cellH : child.getLayoutParams().height;
            int row = i / cols;
            int col = i % cols;
            int rowItems = Math.min(cols, count - row * cols);
            int rowInset = ((cols - rowItems) * (cellW + gap)) / 2;
            float left = originX + rowInset + col * (cellW + gap) + (cellW - childW) * 0.5f;
            float top = originY + row * (cellH + gap) + (cellH - childH) * 0.5f;
            float cx = left + childW * 0.5f;
            // Insert before the first key whose bottom-right region is past the finger.
            if (y < top + childH && x < cx) return i;
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

    /** Show the empty cell where the dragged key would land; -1 hides it. */
    public void setDropPreview(int index, int widthPx, int heightPx) {
        int clamped;
        if (mModel != null && mModel.layoutMode != HudButton.LAYOUT_FREE
                && (mModel.columns > 0 || mModel.rows > 0)) {
            // In a configured grid the index is a cell slot, so it may point at any cell of the
            // grid — clamping it to the number of keys drew the hint on the wrong cell whenever
            // the target cell sat past the keys that already exist.
            Grid grid = computeGrid(Math.max(1, getChildCount()));
            int capacity = Math.max(1, grid.cols * grid.rows);
            clamped = Math.max(0, Math.min(capacity - 1, index));
        } else {
            // Legacy / FREE: an insertion index, which may be one past the last child.
            clamped = Math.max(0, Math.min(getChildCount(), index));
        }
        if (mDropPreviewIndex == clamped
                && mDropPreviewWidthPx == widthPx
                && mDropPreviewHeightPx == heightPx) return;
        mDropPreviewIndex = clamped;
        mDropPreviewWidthPx = Math.max(0, widthPx);
        mDropPreviewHeightPx = Math.max(0, heightPx);
        requestLayout();
        invalidate();
    }

    public void clearDropPreview() {
        if (mDropPreviewIndex < 0) return;
        mDropPreviewIndex = -1;
        requestLayout();
        invalidate();
    }

    /** Re-read the model after the editor added, removed or rebound a child. */
    public void rebuildChildren() {
        mDropPreviewIndex = -1;
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

    /** Resolved slot per child view, in child order. */
    public int[] slotsForChildren() {
        int count = getChildCount();
        if (count == 0) return new int[0];
        Grid grid = computeGrid(count);
        return resolveSlots(count, grid.cols);
    }

    /** FREE layout places children from their own percentages instead of filling a grid. */
    private boolean isFreeLayout() {
        return mModel != null && mModel.layoutMode == HudButton.LAYOUT_FREE;
    }

    /**
     * Grid cell under a raw screen point (row * columns + column), or -1 when the point is
     * outside this container. Used when a key is dropped or dragged to a cell.
     */
    public int findSlotAt(float rawX, float rawY) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float x = rawX - loc[0];
        float y = rawY - loc[1];
        if (x < 0 || y < 0 || x > getMeasuredWidth() || y > getMeasuredHeight()) return -1;

        Grid grid = computeGrid(Math.max(1, getChildCount()));
        int col = (int) ((x - grid.originX) / (float) (grid.cellW + grid.gap));
        int row = (int) ((y - grid.originY) / (float) (grid.cellH + grid.gap));
        col = Math.max(0, Math.min(grid.cols - 1, col));
        row = Math.max(0, Math.min(grid.rows - 1, row));
        return row * grid.cols + col;
    }

    /**
     * Slot each child occupies, parallel to {@code getChildAt}. Children with a fixed
     * {@code gridSlot} claim that cell first; the rest fill the lowest free cells in order, so
     * untouched containers keep their old packing behaviour.
     */
    private int[] resolveSlots(int count, int cols) {
        int[] slots = new int[count];
        if (count == 0) return slots;
        int capacity = Math.max(count, cols * Math.max(1, (count + cols - 1) / cols));
        // A key can be parked in a cell past the packed ones (e.g. a sparse bar); make room for
        // it instead of silently falling back to the next free cell.
        if (mModel != null) {
            for (HudButton child : mModel.children) {
                if (child.gridSlot >= 0) capacity = Math.max(capacity, child.gridSlot + 1);
            }
        }
        boolean[] taken = new boolean[capacity];
        for (int i = 0; i < count; i++) {
            slots[i] = -1;
            if (mModel == null || i >= mModel.children.size()) continue;
            int want = mModel.children.get(i).gridSlot;
            if (want >= 0 && want < capacity && !taken[want]) {
                slots[i] = want;
                taken[want] = true;
            }
        }
        int next = 0;
        for (int i = 0; i < count; i++) {
            if (slots[i] >= 0) continue;
            while (next < capacity && taken[next]) next++;
            if (next >= capacity) break;
            slots[i] = next;
            taken[next] = true;
        }
        return slots;
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
        int slotCount = count + (mDropPreviewIndex >= 0 ? 1 : 0);

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
        if (mDropPreviewIndex >= 0) {
            cellW = Math.max(cellW, mDropPreviewWidthPx);
            cellH = Math.max(cellH, mDropPreviewHeightPx);
        }

        int needed;
        if (slotCount == 0 || cellH <= 0) {
            needed = pad * 2 + dp(EMPTY_MIN_HEIGHT_DP);
        } else {
            int cols = effectiveColumnCount(contentWidth, cellW, gap, slotCount);
            int rows = Math.max(1, (slotCount + cols - 1) / cols);
            needed = pad * 2 + rows * cellH + Math.max(0, rows - 1) * gap;
        }
        // A configured grid derives its cells from the container size, so the container must not
        // grow itself to fit the children: that feedback loop kept changing the cell size.
        boolean fixedCells = mModel != null && (mModel.columns > 0 || mModel.rows > 0);
        if (!fixedCells && !isFreeLayout() && needed > height) height = needed;
        // Never allow the container to be smaller than the widest key needs.
        // FREE layout is excluded: a key may deliberately hang over the container edge.
        int minWidth = pad * 2 + cellW;
        if (!fixedCells && !isFreeLayout() && slotCount > 0 && minWidth > width) width = minWidth;

        // Keep the model in sync with what we actually render, otherwise the
        // saved profile would store a size that clips the children and the
        // group would "jump" the next time it is rebuilt. A drop preview is
        // transient, so it must never leak into the saved size.
        if (mDropPreviewIndex < 0) {
            mModel.widthDp = Math.max(1, Math.round(width / density));
            mModel.heightDp = Math.max(1, Math.round(height / density));
        }

        int wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int hSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

        // If columns/rows mode is enabled, measure children with their stretched cell size.
        // FREE layout keeps each child's own size: that is the whole point of the mode.
        if (!isFreeLayout() && mModel != null && (mModel.columns > 0 || mModel.rows > 0) && count > 0) {
            int cols = effectiveColumnCount(contentWidth, cellW, gap, slotCount);
            int rows = Math.max(1, (slotCount + cols - 1) / cols);
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

    private static final class Grid {
        int pad;
        int gap;
        int contentWidth;
        int contentHeight;
        int cellW;
        int cellH;
        int cols;
        int rows;
        int originX;
        int originY;
    }

    /** Shared geometry for laying out children and drawing the drop preview. */
    private Grid computeGrid(int slotCount) {
        Grid grid = new Grid();
        grid.pad = padPx();
        grid.gap = dp(GAP_DP);
        grid.contentWidth = Math.max(1, getMeasuredWidth() - grid.pad * 2);
        grid.contentHeight = Math.max(1, getMeasuredHeight() - grid.pad * 2);

        // A container with columns/rows configured splits its whole content box into that many
        // cells, so the cells themselves are the layout: they span the full width and height and
        // an empty cell stays empty. Nothing is packed or centred around the keys present.
        // A container that has been given explicit cells behaves the same way, using a nominal
        // cell size derived from the container itself.
        boolean fixedGrid = !isFreeLayout() && mModel != null
                && (mModel.columns > 0 || mModel.rows > 0);
        if (fixedGrid) {
            int needed = Math.max(1, Math.max(slotCount, getChildCount()));
            int cols;
            int rows;
            if (mModel.columns > 0) {
                cols = mModel.columns;
                rows = mModel.rows > 0 ? mModel.rows
                        : Math.max(1, (needed + cols - 1) / cols);
            } else if (mModel.rows > 0) {
                rows = Math.max(1, mModel.rows);
                cols = Math.max(1, (needed + rows - 1) / rows);
            } else {
                int nominal = Math.max(1, dp(48));
                cols = Math.max(1, (grid.contentWidth + grid.gap) / (nominal + grid.gap));
                rows = Math.max(1, (grid.contentHeight + grid.gap) / (nominal + grid.gap));
            }
            // A key parked beyond the configured cells grows the grid instead of vanishing.
            int maxSlot = -1;
            for (HudButton child : mModel.children) maxSlot = Math.max(maxSlot, child.gridSlot);
            if (maxSlot >= cols * rows) {
                rows = Math.max(rows, (maxSlot + 1 + cols - 1) / cols);
            }
            grid.cols = cols;
            grid.rows = rows;
            grid.cellW = Math.max(1, (grid.contentWidth - (cols - 1) * grid.gap) / cols);
            grid.cellH = Math.max(1, (grid.contentHeight - (rows - 1) * grid.gap) / rows);
            grid.originX = grid.pad;
            grid.originY = grid.pad;
            return grid;
        }

        int cellW = 0;
        int cellH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            cellW = Math.max(cellW, child.getMeasuredWidth());
            cellH = Math.max(cellH, child.getMeasuredHeight());
        }
        if (mDropPreviewIndex >= 0) {
            cellW = Math.max(cellW, mDropPreviewWidthPx);
            cellH = Math.max(cellH, mDropPreviewHeightPx);
        }
        grid.cellW = Math.max(1, cellW);
        grid.cellH = Math.max(1, cellH);

        int slots = Math.max(1, slotCount);
        grid.cols = effectiveColumnCount(grid.contentWidth, grid.cellW, grid.gap, slots);
        grid.rows = Math.max(1, (slots + grid.cols - 1) / grid.cols);

        int gridW = grid.cols * grid.cellW + Math.max(0, grid.cols - 1) * grid.gap;
        int gridH = grid.rows * grid.cellH + Math.max(0, grid.rows - 1) * grid.gap;
        grid.originX = grid.pad + Math.max(0, (grid.contentWidth - gridW) / 2);
        grid.originY = grid.pad + Math.max(0, (grid.contentHeight - gridH) / 2);
        return grid;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        // A configured grid never reserves a phantom cell for the preview: the hint is drawn on
        // the target cell while the keys stay put, so the grid must not be recomputed for it.
        boolean fixedCellsConfigured = mModel != null && (mModel.columns > 0 || mModel.rows > 0);
        int slotCount = count + (mDropPreviewIndex >= 0 && !fixedCellsConfigured ? 1 : 0);
        if (slotCount == 0) return;

        // FREE layout: each child sits at its own percentage of the content box and keeps its
        // own size. Percentages are the child's centre, matching the top-level widget model.
        if (isFreeLayout()) {
            int pad = padPx();
            float contentW = Math.max(1, getMeasuredWidth() - pad * 2);
            float contentH = Math.max(1, getMeasuredHeight() - pad * 2);
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                HudButton model = i < mModel.children.size() ? mModel.children.get(i) : null;
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                float px = model != null ? model.posXPercent : 0.5f;
                float py = model != null ? model.posYPercent : 0.5f;
                int cx = Math.round(pad + px * contentW - cw * 0.5f);
                int cy = Math.round(pad + py * contentH - ch * 0.5f);
                // Keep the key reachable: it may hang over the container edge, but never fully off it.
                cx = Math.max(-cw / 2, Math.min(getMeasuredWidth() - cw / 2, cx));
                cy = Math.max(-ch / 2, Math.min(getMeasuredHeight() - ch / 2, cy));
                child.layout(cx, cy, cx + cw, cy + ch);
            }
            return;
        }

        Grid grid = computeGrid(slotCount);
        int[] slots = resolveSlots(count, grid.cols);
        int maxSlot = -1;
        for (int s : slots) maxSlot = Math.max(maxSlot, s);
        if (maxSlot + 1 > slotCount) {
            // A fixed cell can sit past the last packed slot; size the grid to reach it.
            grid = computeGrid(maxSlot + 1);
            slots = resolveSlots(count, grid.cols);
        }
        boolean stretch = mModel != null && (mModel.columns > 0 || mModel.rows > 0);

        // How many keys land in each row, so a partially filled row can be centred.
        int rowsUsed = Math.max(1, (Math.max(slotCount, maxSlot + 1) + grid.cols - 1) / grid.cols);
        int[] perRow = new int[rowsUsed];
        for (int i = 0; i < count; i++) perRow[slots[i] / grid.cols]++;
        // A configured grid already spans the full content box: its cells are the layout, so a
        // row with empty cells must not be shifted. Only the legacy packed mode centres.
        boolean fixedCells = mModel != null && (mModel.columns > 0 || mModel.rows > 0);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            // A key sits in the cell it was assigned (or the next free one), so dropping a key
            // into a spot never reflows the keys around it.
            int slot = slots[i];
            int row = slot / grid.cols;
            int col = slot % grid.cols;
            int rowInset = fixedCells
                    ? 0
                    : ((grid.cols - Math.max(1, perRow[row])) * (grid.cellW + grid.gap)) / 2;
            int cellLeft = grid.originX + rowInset + col * (grid.cellW + grid.gap);
            int cellTop = grid.originY + row * (grid.cellH + grid.gap);
            int cw = stretch ? grid.cellW : child.getMeasuredWidth();
            int ch = stretch ? grid.cellH : child.getMeasuredHeight();
            int cx = cellLeft + (grid.cellW - cw) / 2;
            int cy = cellTop + (grid.cellH - ch) / 2;
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

        float washOpacity = Math.max(0f, Math.min(1f, mModel.opacity));
        if (mDropTarget) washOpacity = Math.min(1f, washOpacity + 70f / 255f);
        HudChrome.drawSurface(canvas, mFillPaint, mPath, mRect,
                mModel.cornerShape, corner, mModel.bgColor, washOpacity);

        if (mInEditMode && getChildCount() == 0 && mDropPreviewIndex < 0) {
            mHintPaint.setColor(HudColors.withAlpha(HudColors.textPrimary, 0xB3));
            mHintPaint.setTextSize(HudType.CAPTION_SP * getResources().getDisplayMetrics().scaledDensity);
            float textY = getHeight() * 0.5f
                    - ((mHintPaint.descent() + mHintPaint.ascent()) * 0.5f);
            canvas.drawText(getContext().getString(R.string.hud_group_drop_hint),
                    getWidth() * 0.5f, textY, mHintPaint);
        }

        // Real grid: every cell of a configured grid is outlined while editing, so the container
        // reads as "these are the cells" instead of an invisible snapping rule. Drawn here, under
        // the keys, so the outlines never sit on top of the labels.
        boolean showGrid = mInEditMode && !isFreeLayout() && mModel != null
                && (mModel.columns > 0 || mModel.rows > 0);
        if (showGrid) {
            Grid grid = computeGrid(Math.max(1, getChildCount()));
            float radius = Math.min(grid.cellW, grid.cellH) * 0.18f;
            for (int r = 0; r < grid.rows; r++) {
                for (int c = 0; c < grid.cols; c++) {
                    cellBounds(r * grid.cols + c, mRect);
                    canvas.drawRoundRect(mRect, radius, radius, mGridPaint);
                }
            }
        }
    }

    /**
     * Bounds of one grid cell in this view's coordinates.
     *
     * <p>The layout and the drop preview both read this, so the highlighted cell is exactly the
     * rectangle the key will occupy — one source of truth, not two similar formulas.
     */
    private void cellBounds(int slot, RectF out) {
        Grid grid = computeGrid(Math.max(1, getChildCount()));
        int capacity = Math.max(1, grid.cols * grid.rows);
        int s = Math.max(0, Math.min(slot, capacity - 1));
        int row = s / grid.cols;
        int col = s % grid.cols;
        float left = grid.originX + col * (grid.cellW + grid.gap);
        float top = grid.originY + row * (grid.cellH + grid.gap);
        out.set(left, top, left + grid.cellW, top + grid.cellH);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mDropPreviewIndex >= 0) {
            // Exactly the target cell, drawn from the same geometry the keys are laid out with.
            cellBounds(mDropPreviewIndex, mRect);
            float radius = Math.min(mRect.width(), mRect.height()) * 0.22f;
            canvas.drawRoundRect(mRect, radius, radius, mPreviewFillPaint);
            canvas.drawRoundRect(mRect, radius, radius, mPreviewBorderPaint);
        }
        // Border goes on top of the keys so the container stays readable even
        // when the children cover its whole surface.
        float density = getResources().getDisplayMetrics().density;
        float stroke = Math.max(1f, 1.5f * density);
        float corner = mModel.cornerRadiusDp * density;
        mRect.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
        HudChrome.computeRadii(mModel.cornerShape, corner, mRadii);
        mPath.reset();
        mPath.addRoundRect(mRect, mRadii, Path.Direction.CW);

        if (mDropTarget) {
            mBorderPaint.setPathEffect(null);
            mBorderPaint.setStrokeWidth(stroke);
            mBorderPaint.setColor(HudColors.primary);
            canvas.drawPath(mPath, mBorderPaint);
        } else if (mInEditMode) {
            // Only draw dashed outline in edit mode if borderStrokeWidthDp > 0
            if (mModel.borderStrokeWidthDp > 0) {
                mBorderPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
                mBorderPaint.setStrokeWidth(mModel.borderStrokeWidthDp * density);
                mBorderPaint.setColor(mModel.borderColor != 0 ? mModel.borderColor : HudColors.withAlpha(HudColors.primarySoft, 0x8A));
                canvas.drawPath(mPath, mBorderPaint);
            }
        } else {
            // Runtime: only draw border if borderStrokeWidthDp > 0
            if (mModel.borderStrokeWidthDp > 0) {
                mBorderPaint.setPathEffect(null);
                mBorderPaint.setStrokeWidth(mModel.borderStrokeWidthDp * density);
                mBorderPaint.setColor(mModel.borderColor != 0 ? mModel.borderColor : HudColors.ripple);
                canvas.drawPath(mPath, mBorderPaint);
            }
        }
    }
}
