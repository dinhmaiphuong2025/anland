package com.anland.consumer.hud.design;

/**
 * Semantic colour roles for the HUD.
 *
 * <p>Every feature view must read colours from here by <b>role</b>, never by raw hex.
 * Roles are mapped onto {@link HudPalette} tones exactly like Now in Android's
 * {@code lightColorScheme}/{@code darkColorScheme} map tones onto Material role slots.
 *
 * <p>Re-skinning the whole HUD means editing this file only.
 */
public final class HudColors {

    private HudColors() {
    }

    // ---- Accent ------------------------------------------------------------
    /** Active, pressed, selected, checked. */
    public static final int primary = HudPalette.Lilac40;
    /** Outlines, muted accents, slider tracks, secondary glyphs. */
    public static final int primarySoft = HudPalette.Lilac60;
    /** Muted container behind selected or accent content. */
    public static final int primaryContainer = HudPalette.Lilac25;
    /** Content drawn on top of {@link #primary}. */
    public static final int onPrimary = HudPalette.Neutral100;
    /** Text or glyph drawn on an active/pressed key. */
    public static final int onActive = HudPalette.Ink900;

    // ---- Surfaces ----------------------------------------------------------
    /** Card / dialog surface. */
    public static final int surface = HudPalette.Charcoal30;
    /** Panel interiors, inspector body. */
    public static final int surfaceHigh = HudPalette.Charcoal40;
    /** Raised controls: buttons, text inputs, key chips. */
    public static final int surfaceHighest = HudPalette.Charcoal50;
    /** Deepest layer, used behind popovers and for translucent overlays. */
    public static final int surfaceDeep = HudPalette.Charcoal20;
    /** Default background for a newly created widget: the deep surface, mostly opaque. */
    public static final int surfaceTranslucent = withAlpha(HudPalette.Charcoal20, 0xE6);
    /** Deep neutral surface one step above the void. */
    public static final int surfaceLow = HudPalette.Charcoal25;
    /** Inset control face (D-pad wells). */
    public static final int surfaceInsetDeep = HudPalette.Ink800;
    /** Softer inset control face. */
    public static final int surfaceInset = HudPalette.Ink700;

    // ---- Content -----------------------------------------------------------
    public static final int textPrimary = HudPalette.Neutral100;
    public static final int textMuted = HudPalette.Neutral70;
    public static final int textDisabled = HudPalette.Neutral60;

    // ---- Outlines ----------------------------------------------------------
    /** Hairline border on interactive surfaces. */
    public static final int outlineSubtle = withAlpha(HudPalette.Neutral100, 0x22);
    /** Emphasis border, focus ring, selected container. */
    public static final int outlineStrong = HudPalette.Lilac60;
    /** Low-contrast outline for inset wells. */
    public static final int outlineMuted = HudPalette.Neutral45;
    /** Deep lilac accent used inside inset surfaces. */
    public static final int primaryDeep = HudPalette.Lilac20;

    // ---- Status ------------------------------------------------------------
    public static final int danger = HudPalette.Coral40;
    public static final int success = HudPalette.Mint40;
    public static final int warning = HudPalette.Amber40;
    /** Dark wash behind an error banner. */
    public static final int dangerContainer = HudPalette.DangerContainerDark;
    /** Dark wash behind a success banner. */
    public static final int successContainer = HudPalette.SuccessContainerDark;
    /** Alignment guide line drawn while dragging. */
    public static final int guide = HudPalette.Green40;

    // ---- Effects -----------------------------------------------------------
    /** Dimming layer behind modal panels. */
    public static final int scrim = withAlpha(HudPalette.Black, 0x33);
    /** Heavier dimming for blocking dialogs. */
    public static final int scrimStrong = withAlpha(HudPalette.Ink900, 0xBB);
    /** Medium dimming for floating quick-action bars. */
    public static final int scrimMedium = withAlpha(HudPalette.Black, 0x66);
    /** Default ripple / pressed wash. */
    public static final int ripple = withAlpha(HudPalette.Neutral100, 0x33);
    /** Subtle fill behind badges and chips. */
    public static final int surfaceTint = withAlpha(HudPalette.Black, 0x33);

    /**
     * Replace the alpha channel of {@code color} while keeping its RGB.
     *
     * @param color  source colour, its own alpha is used as the 100% reference
     * @param alpha8 target alpha, 0..255
     */
    public static int withAlpha(int color, int alpha8) {
        return (color & 0x00FFFFFF) | ((alpha8 & 0xFF) << 24);
    }

    /** Multiply the existing alpha of {@code color} by {@code factor} (0..1). */
    public static int scaleAlpha(int color, float factor) {
        int base = color >>> 24;
        int scaled = Math.round(base * factor);
        return withAlpha(color, scaled);
    }
}
