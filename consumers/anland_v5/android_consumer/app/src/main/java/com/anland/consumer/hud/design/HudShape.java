package com.anland.consumer.hud.design;

import android.content.Context;

/**
 * Corner-radius scale.
 *
 * <p>Replaces the ten-plus scattered radii (4, 6, 8, 10, 12, 13, 14, 16, 20, 22) with four
 * named steps. Every widget picks a step by intent, so shapes stay consistent no matter which
 * view draws them.
 *
 * <pre>
 * key        6dp   child keys inside a container
 * button     8dp   standalone keys, chips, small buttons
 * container 10dp   group / extra keys / number row / fn row
 * card      12dp   dialog bodies, inspector cards
 * panel     16dp   floating panels
 * pill           = height / 2, for fully rounded capsules
 * </pre>
 */
public final class HudShape {

    private HudShape() {
    }

    public static final int KEY_DP = 6;
    public static final int BUTTON_DP = 8;
    public static final int CONTAINER_DP = 10;
    public static final int CARD_DP = 12;
    public static final int PANEL_DP = 16;

    /** Sentinel: radius resolves to half the shorter edge, producing a capsule. */
    public static final int PILL = -1;

    /** Resolve a radius step to pixels; {@link #PILL} resolves against {@code sizePx}. */
    public static int px(Context ctx, int radiusDp, float sizePx) {
        if (radiusDp == PILL) return Math.round(sizePx * 0.5f);
        return HudUnits.dp(ctx, radiusDp);
    }

    /** Standard pill radius for a capsule of the given height. */
    public static float pill(float heightPx) {
        return heightPx * 0.5f;
    }
}
