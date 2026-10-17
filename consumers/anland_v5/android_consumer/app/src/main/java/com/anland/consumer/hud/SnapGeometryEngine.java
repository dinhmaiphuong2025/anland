package com.anland.consumer.hud;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * High-precision snapping engine inspired by PojavLauncher & Zalith Launcher.
 * Features:
 * 1. Clean Grid Snapping (aligns to 8dp/16dp step grid)
 * 2. Single Best-Match Magnetic Snapping (finds the single closest alignment target)
 * 3. Never jumps chaotically or outputs multiple conflicting guidelines
 */
public final class SnapGeometryEngine {
    public static final class SnapResult {
        public float snappedX;
        public float snappedY;
        public final List<Float> verticalGuidelines = new ArrayList<>();
        public final List<Float> horizontalGuidelines = new ArrayList<>();
    }

    private final float mDensity;
    private final float mSnapThresholdPx;
    private final float mCollisionMarginPx;
    private final float mGridStepPx;

    public SnapGeometryEngine(float density) {
        this.mDensity = density;
        this.mSnapThresholdPx = 10f * density;
        this.mCollisionMarginPx = 4f * density;
        this.mGridStepPx = 16f * density; // 16dp standard design grid
    }

    /**
     * Calculates the snapped position for the active button bounding box against screen bounds and siblings.
     * Uses nearest-match selection to avoid chaotic jumping.
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

        // --- Best Snap Candidate Tracking for X ---
        float bestDistX = mSnapThresholdPx;
        float bestSnappedX = x;
        Float bestGuideX = null;

        // --- Best Snap Candidate Tracking for Y ---
        float bestDistY = mSnapThresholdPx;
        float bestSnappedY = y;
        Float bestGuideY = null;

        // 1. Grid Snap candidate (PojavLauncher style)
        float gridX = Math.round(x / mGridStepPx) * mGridStepPx;
        float dGridX = Math.abs(x - gridX);
        if (dGridX < bestDistX) {
            bestDistX = dGridX;
            bestSnappedX = gridX;
            bestGuideX = gridX;
        }

        float gridY = Math.round(y / mGridStepPx) * mGridStepPx;
        float dGridY = Math.abs(y - gridY);
        if (dGridY < bestDistY) {
            bestDistY = dGridY;
            bestSnappedY = gridY;
            bestGuideY = gridY;
        }

        // 2. Screen Edge Snapping
        if (Math.abs(x) < bestDistX) {
            bestDistX = Math.abs(x);
            bestSnappedX = 0f;
            bestGuideX = 0f;
        }
        float rightDist = Math.abs(x + w - screenWidth);
        if (rightDist < bestDistX) {
            bestDistX = rightDist;
            bestSnappedX = screenWidth - w;
            bestGuideX = screenWidth;
        }

        if (Math.abs(y) < bestDistY) {
            bestDistY = Math.abs(y);
            bestSnappedY = 0f;
            bestGuideY = 0f;
        }
        float bottomDist = Math.abs(y + h - (effectiveMaxY + h));
        if (bottomDist < bestDistY) {
            bestDistY = bottomDist;
            bestSnappedY = effectiveMaxY;
            bestGuideY = effectiveMaxY + h;
        }

        // Screen Center
        float centerX = x + w * 0.5f;
        float centerY = y + h * 0.5f;
        float screenMidX = screenWidth * 0.5f;
        float screenMidY = screenHeight * 0.5f;

        float centerDistX = Math.abs(centerX - screenMidX);
        if (centerDistX < bestDistX) {
            bestDistX = centerDistX;
            bestSnappedX = screenMidX - w * 0.5f;
            bestGuideX = screenMidX;
        }
        float centerDistY = Math.abs(centerY - screenMidY);
        if (centerDistY < bestDistY) {
            bestDistY = centerDistY;
            bestSnappedY = screenMidY - h * 0.5f;
            bestGuideY = screenMidY;
        }

        // 3. Sibling Magnet Snapping (Find the single closest alignment target)
        if (siblings != null) {
            for (RectF s : siblings) {
                // Sibling Left-Left
                float dLL = Math.abs(x - s.left);
                if (dLL < bestDistX) {
                    bestDistX = dLL;
                    bestSnappedX = s.left;
                    bestGuideX = s.left;
                }
                // Sibling Right-Right
                float dRR = Math.abs((x + w) - s.right);
                if (dRR < bestDistX) {
                    bestDistX = dRR;
                    bestSnappedX = s.right - w;
                    bestGuideX = s.right;
                }
                // Sibling Center-Center Vertical
                float dCCX = Math.abs((x + w * 0.5f) - s.centerX());
                if (dCCX < bestDistX) {
                    bestDistX = dCCX;
                    bestSnappedX = s.centerX() - w * 0.5f;
                    bestGuideX = s.centerX();
                }
                // Sibling Adjacent Edge (Left to Sibling Right)
                float dAdjR = Math.abs(x - (s.right + mCollisionMarginPx));
                if (dAdjR < bestDistX) {
                    bestDistX = dAdjR;
                    bestSnappedX = s.right + mCollisionMarginPx;
                    bestGuideX = s.right;
                }
                // Sibling Adjacent Edge (Right to Sibling Left)
                float dAdjL = Math.abs((x + w) - (s.left - mCollisionMarginPx));
                if (dAdjL < bestDistX) {
                    bestDistX = dAdjL;
                    bestSnappedX = s.left - mCollisionMarginPx - w;
                    bestGuideX = s.left;
                }

                // Sibling Top-Top
                float dTT = Math.abs(y - s.top);
                if (dTT < bestDistY) {
                    bestDistY = dTT;
                    bestSnappedY = s.top;
                    bestGuideY = s.top;
                }
                // Sibling Bottom-Bottom
                float dBB = Math.abs((y + h) - s.bottom);
                if (dBB < bestDistY) {
                    bestDistY = dBB;
                    bestSnappedY = s.bottom - h;
                    bestGuideY = s.bottom;
                }
                // Sibling Center-Center Horizontal
                float dCCY = Math.abs((y + h * 0.5f) - s.centerY());
                if (dCCY < bestDistY) {
                    bestDistY = dCCY;
                    bestSnappedY = s.centerY() - h * 0.5f;
                    bestGuideY = s.centerY();
                }
                // Sibling Adjacent Edge (Top to Sibling Bottom)
                float dAdjB = Math.abs(y - (s.bottom + mCollisionMarginPx));
                if (dAdjB < bestDistY) {
                    bestDistY = dAdjB;
                    bestSnappedY = s.bottom + mCollisionMarginPx;
                    bestGuideY = s.bottom;
                }
                // Sibling Adjacent Edge (Bottom to Sibling Top)
                float dAdjT = Math.abs((y + h) - (s.top - mCollisionMarginPx));
                if (dAdjT < bestDistY) {
                    bestDistY = dAdjT;
                    bestSnappedY = s.top - mCollisionMarginPx - h;
                    bestGuideY = s.top;
                }
            }
        }

        // Apply best found snaps
        x = bestSnappedX;
        y = bestSnappedY;
        if (bestGuideX != null) res.verticalGuidelines.add(bestGuideX);
        if (bestGuideY != null) res.horizontalGuidelines.add(bestGuideY);

        // Clamp to screen bounds
        x = Math.max(0, Math.min(screenWidth - w, x));
        y = Math.max(0, Math.min(effectiveMaxY, y));

        res.snappedX = x;
        res.snappedY = y;
        return res;
    }
}
