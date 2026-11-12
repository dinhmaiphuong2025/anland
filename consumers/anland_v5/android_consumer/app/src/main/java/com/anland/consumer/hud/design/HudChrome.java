package com.anland.consumer.hud.design;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Switch;

/**
 * Surface renderer — the only place in the HUD that knows how a widget background is painted.
 *
 * <p>Before this existed, ten views each rebuilt their own fill/stroke/opacity/corner path,
 * which is why changing a corner radius or an opacity rule never applied consistently.
 * Every widget now delegates here with primitive arguments; the design layer deliberately
 * knows nothing about {@code HudButton} or any feature model.
 *
 * <p>Drawing is expected on the UI thread only; the scratch radii array is reused to keep
 * {@code onDraw} allocation-free.
 */
public final class HudChrome {

    private HudChrome() {
    }

    /** Scratch buffer for {@link #computeRadii}; UI-thread only. */
    private static final float[] sRadii = new float[8];

    /**
     * Expand a corner shape into the eight Path radii values.
     *
     * @param outRadii must hold at least 8 floats; filled with
     *                 [tlX, tlY, trX, trY, brX, brY, blX, blY]
     */
    public static void computeRadii(int cornerShape, float radiusPx, float[] outRadii) {
        java.util.Arrays.fill(outRadii, 0f);
        switch (cornerShape) {
            case HudCornerShape.TOP_LEFT:
                outRadii[0] = radiusPx;
                outRadii[1] = radiusPx;
                break;
            case HudCornerShape.TOP_RIGHT:
                outRadii[2] = radiusPx;
                outRadii[3] = radiusPx;
                break;
            case HudCornerShape.BOTTOM_LEFT:
                outRadii[6] = radiusPx;
                outRadii[7] = radiusPx;
                break;
            case HudCornerShape.BOTTOM_RIGHT:
                outRadii[4] = radiusPx;
                outRadii[5] = radiusPx;
                break;
            case HudCornerShape.LEFT_BOTH:
                outRadii[0] = radiusPx;
                outRadii[1] = radiusPx;
                outRadii[6] = radiusPx;
                outRadii[7] = radiusPx;
                break;
            case HudCornerShape.RIGHT_BOTH:
                outRadii[2] = radiusPx;
                outRadii[3] = radiusPx;
                outRadii[4] = radiusPx;
                outRadii[5] = radiusPx;
                break;
            case HudCornerShape.TOP_BOTH:
                outRadii[0] = radiusPx;
                outRadii[1] = radiusPx;
                outRadii[2] = radiusPx;
                outRadii[3] = radiusPx;
                break;
            case HudCornerShape.BOTTOM_BOTH:
                outRadii[4] = radiusPx;
                outRadii[5] = radiusPx;
                outRadii[6] = radiusPx;
                outRadii[7] = radiusPx;
                break;
            case HudCornerShape.ALL:
                java.util.Arrays.fill(outRadii, radiusPx);
                break;
            case HudCornerShape.NONE:
            default:
                break;
        }
    }

    /** Multiply the existing alpha of {@code color} by {@code opacity} (0..1). */
    public static int applyOpacity(int color, float opacity) {
        int origAlpha = color >>> 24;
        int finalAlpha = Math.round(opacity * origAlpha);
        return (color & 0x00FFFFFF) | (finalAlpha << 24);
    }

    /**
     * Paint a widget background: rounded fill at {@code opacity}, plus an optional stroke.
     *
     * <p>{@code path} is an output buffer owned by the caller so repeated draws stay
     * allocation-free; its previous contents are discarded.
     *
     * @param strokeWidthPx stroke width in pixels, 0 disables the stroke
     */
    public static void drawSurface(Canvas canvas,
                                   Paint paint,
                                   Path path,
                                   RectF bounds,
                                   int cornerShape,
                                   float radiusPx,
                                   int fillColor,
                                   float opacity,
                                   float strokeWidthPx,
                                   int strokeColor) {
        computeRadii(cornerShape, radiusPx, sRadii);
        path.reset();
        path.addRoundRect(bounds, sRadii, Path.Direction.CW);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(applyOpacity(fillColor, opacity));
        canvas.drawPath(path, paint);

        if (strokeWidthPx > 0f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidthPx);
            paint.setColor(strokeColor);
            canvas.drawPath(path, paint);
        }
    }

    /** {@link #drawSurface} without a stroke. */
    public static void drawSurface(Canvas canvas,
                                   Paint paint,
                                   Path path,
                                   RectF bounds,
                                   int cornerShape,
                                   float radiusPx,
                                   int fillColor,
                                   float opacity) {
        drawSurface(canvas, paint, path, bounds, cornerShape, radiusPx, fillColor, opacity, 0f, 0);
    }

    /**
     * Fill a rounded rectangle directly, without exposing the intermediate {@link Path}.
     * Useful for small chips and badges that do not need a reusable buffer.
     */
    public static void drawCapsule(Canvas canvas,
                                   Paint paint,
                                   RectF bounds,
                                   float radiusPx,
                                   int fillColor,
                                   float opacity) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(applyOpacity(fillColor, opacity));
        canvas.drawRoundRect(bounds, radiusPx, radiusPx, paint);
    }

    // ============================================================
    // DRAWABLE FACTORIES
    // ============================================================

    /** Rounded rectangle drawable with an optional hairline stroke. */
    public static GradientDrawable shape(Context ctx,
                                         int radiusDp,
                                         int fillColor,
                                         int strokeColor,
                                         float strokeWidthDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(HudUnits.dp(ctx, radiusDp));
        gd.setColor(fillColor);
        if (strokeWidthDp > 0f && strokeColor != 0) {
            gd.setStroke(Math.max(1, HudUnits.dp(ctx, strokeWidthDp)), strokeColor);
        }
        return gd;
    }

    /** Interactive surface: {@link #shape} wrapped in a ripple. */
    public static Drawable ripple(Context ctx,
                                  int radiusDp,
                                  int normalColor,
                                  int pressedColor,
                                  int strokeColor) {
        GradientDrawable content = shape(ctx, radiusDp, normalColor, strokeColor, 1.0f);
        GradientDrawable mask = shape(ctx, radiusDp, Color.WHITE, 0, 0f);
        return new RippleDrawable(ColorStateList.valueOf(pressedColor), content, mask);
    }

    // ============================================================
    // COMPONENT STYLING
    // ============================================================

    /**
     * Tint a platform {@link Switch} with the design accent.
     *
     * <p>Platform switches depend on theme-provided drawables; without explicit tints they
     * can render invisible inside the HUD's legacy host theme.
     */
    public static void styleSwitch(Switch sw) {
        sw.setTextColor(HudColors.textPrimary);
        sw.setTextSize(HudType.SUBTITLE_SP);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] trackColors = new int[]{
                HudColors.withAlpha(HudColors.primary, 0x66),
                HudColors.withAlpha(HudColors.textPrimary, 0x33)
        };
        int[] thumbColors = new int[]{
                HudColors.primary,
                HudPalette.Neutral60
        };
        sw.setTrackTintList(new ColorStateList(states, trackColors));
        sw.setThumbTintList(new ColorStateList(states, thumbColors));
    }
}
