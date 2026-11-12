package com.anland.consumer.hud.design;

/**
 * Corner-shape vocabulary for HUD surfaces.
 *
 * <p>This is the single source of truth for the ten shapes the editor exposes. The integer
 * values are persisted inside saved layout profiles, so they must never be renumbered.
 * {@code HudButton.CORNER_*} constants are aliases of these.
 */
public final class HudCornerShape {

    private HudCornerShape() {
    }

    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;
    public static final int BOTTOM_RIGHT = 4;
    public static final int LEFT_BOTH = 5;
    public static final int RIGHT_BOTH = 6;
    public static final int TOP_BOTH = 7;
    public static final int BOTTOM_BOTH = 8;
    public static final int ALL = 9;
    public static final int NONE = 10;

    /** Shape applied to newly created widgets. */
    public static final int DEFAULT = ALL;

    /** Number of selectable shapes, used by the shape picker grid. */
    public static final int COUNT = 10;
}
