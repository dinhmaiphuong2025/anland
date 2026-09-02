package com.anland.consumer.hud;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time snapping geometry and collision engine.
 * Computes magnetic alignment targets and guarantees non-overlapping boundaries.
 */
public final class SnapGeometryEngine {
    public static final class SnapResult {
        public float snappedX;
        public float snappedY;
        public final List<Float> verticalGuidelines = new ArrayList<>();
        public final List<Float> horizontalGuidelines = new ArrayList<>();
    }

    private final float mSnapThresholdPx;
    private final float mCollisionMarginPx;

    public SnapGeometryEngine(float density) {
        mSnapThresholdPx = 10f * density;
        mCollisionMarginPx = 4f * density;
    }

    /**
     * Calculates the snapped position for the active button bounding box against screen bounds and siblings.
     */
    public SnapResult computeSnap(
            RectF candidate,
            List<RectF> siblings,
            float screenWidth,
            float screenHeight,
            float dockTopBoundary
    ) {
        SnapResult res = new SnapResult();
        float x = candidate.left;
        float y = candidate.top;
        float w = candidate.width();
        float h = candidate.height();

        float effectiveMaxY = dockTopBoundary > 0 ? (dockTopBoundary - h) : (screenHeight - h);

        // 1. Screen edge snapping
        if (Math.abs(x) < mSnapThresholdPx) {
            x = 0;
            res.verticalGuidelines.add(0f);
        } else if (Math.abs(x + w - screenWidth) < mSnapThresholdPx) {
            x = screenWidth - w;
            res.verticalGuidelines.add(screenWidth);
        }

        if (Math.abs(y) < mSnapThresholdPx) {
            y = 0;
            res.horizontalGuidelines.add(0f);
        } else if (Math.abs(y + h - effectiveMaxY - h) < mSnapThresholdPx) {
            y = effectiveMaxY;
            res.horizontalGuidelines.add(effectiveMaxY + h);
        }

        // Screen center snapping
        float centerX = x + w * 0.5f;
        float centerY = y + h * 0.5f;
        float screenMidX = screenWidth * 0.5f;
        float screenMidY = screenHeight * 0.5f;

        if (Math.abs(centerX - screenMidX) < mSnapThresholdPx) {
            x = screenMidX - w * 0.5f;
            res.verticalGuidelines.add(screenMidX);
        }
        if (Math.abs(centerY - screenMidY) < mSnapThresholdPx) {
            y = screenMidY - h * 0.5f;
            res.horizontalGuidelines.add(screenMidY);
        }

        // 2. Sibling edge and alignment snapping
        if (siblings != null) {
            for (RectF s : siblings) {
                // Sibling left-left align
                if (Math.abs(x - s.left) < mSnapThresholdPx) {
                    x = s.left;
                    res.verticalGuidelines.add(s.left);
                }
                // Sibling right-right align
                if (Math.abs((x + w) - s.right) < mSnapThresholdPx) {
                    x = s.right - w;
                    res.verticalGuidelines.add(s.right);
                }
                // Sibling center-center vertical align
                if (Math.abs((x + w * 0.5f) - s.centerX()) < mSnapThresholdPx) {
                    x = s.centerX() - w * 0.5f;
                    res.verticalGuidelines.add(s.centerX());
                }

                // Sibling top-top align
                if (Math.abs(y - s.top) < mSnapThresholdPx) {
                    y = s.top;
                    res.horizontalGuidelines.add(s.top);
                }
                // Sibling bottom-bottom align
                if (Math.abs((y + h) - s.bottom) < mSnapThresholdPx) {
                    y = s.bottom - h;
                    res.horizontalGuidelines.add(s.bottom);
                }
                // Sibling center-center horizontal align
                if (Math.abs((y + h * 0.5f) - s.centerY()) < mSnapThresholdPx) {
                    y = s.centerY() - h * 0.5f;
                    res.horizontalGuidelines.add(s.centerY());
                }

                // Sibling adjacent edge snap (left to right)
                if (Math.abs(x - (s.right + mCollisionMarginPx)) < mSnapThresholdPx) {
                    x = s.right + mCollisionMarginPx;
                    res.verticalGuidelines.add(s.right);
                }
                // Sibling adjacent edge snap (right to left)
                if (Math.abs((x + w) - (s.left - mCollisionMarginPx)) < mSnapThresholdPx) {
                    x = s.left - mCollisionMarginPx - w;
                    res.verticalGuidelines.add(s.left);
                }
                // Sibling adjacent edge snap (top to bottom)
                if (Math.abs(y - (s.bottom + mCollisionMarginPx)) < mSnapThresholdPx) {
                    y = s.bottom + mCollisionMarginPx;
                    res.horizontalGuidelines.add(s.bottom);
                }
                // Sibling adjacent edge snap (bottom to top)
                if (Math.abs((y + h) - (s.top - mCollisionMarginPx)) < mSnapThresholdPx) {
                    y = s.top - mCollisionMarginPx - h;
                    res.horizontalGuidelines.add(s.top);
                }
            }
        }

        // Clamp to screen
        x = Math.max(0, Math.min(screenWidth - w, x));
        y = Math.max(0, Math.min(effectiveMaxY, y));

        res.snappedX = x;
        res.snappedY = y;
        return res;
    }
}
