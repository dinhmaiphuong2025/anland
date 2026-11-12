package com.anland.consumer.hud.design;

import android.content.Context;
import android.view.View;

/**
 * Density conversion utilities.
 *
 * Single source of truth for dp -> px inside the HUD UI. Feature views must use these
 * helpers instead of declaring their own local {@code dp(...)} methods.
 */
public final class HudUnits {

    private HudUnits() {
    }

    public static float density(Context ctx) {
        return ctx.getResources().getDisplayMetrics().density;
    }

    public static int dp(Context ctx, float valueDp) {
        return Math.round(valueDp * density(ctx));
    }

    public static float dpF(Context ctx, float valueDp) {
        return valueDp * density(ctx);
    }

    public static int dp(View view, float valueDp) {
        return Math.round(dpF(view, valueDp));
    }

    public static float dpF(View view, float valueDp) {
        return valueDp * view.getResources().getDisplayMetrics().density;
    }
}
