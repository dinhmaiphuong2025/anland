package com.anland.consumer.hud.design;

/**
 * Component-level defaults.
 *
 * <p>Mirrors Now in Android's {@code NiaTagDefaults}: magic numbers that belong to a specific
 * component live next to the design system, named, instead of being inlined at each call site.
 */
public final class HudDefaults {

    private HudDefaults() {
    }

    /** Opacity for a newly created interactive widget. */
    public static final float OPACITY_WIDGET = 0.85f;

    /** Opacity for a newly created container, kept translucent so the desktop shows through. */
    public static final float OPACITY_CONTAINER = 0.25f;

    /** Default stroke width, in dp, for a widget that opts into a visible border. */
    public static final int STROKE_DP = 1;

    /** Opacity applied while the property inspector is ghosted during a slider drag. */
    public static final float GHOST_ALPHA = 0f;

    /** Standard gap between a container edge and its child keys, in dp. */
    public static final int CONTAINER_PAD_DP = 4;

    /** Minimum touch target, in dp. */
    public static final int MIN_TOUCH_DP = 24;

    /** Default hold-to-repeat delay, in milliseconds. */
    public static final int LONG_PRESS_MS = 1000;
}
