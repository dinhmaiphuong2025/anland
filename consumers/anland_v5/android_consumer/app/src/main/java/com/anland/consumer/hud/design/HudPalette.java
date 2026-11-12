package com.anland.consumer.hud.design;

/**
 * Raw colour tones. Reference tones only.
 *
 * <p>Mirrors the Now in Android design-system split: this class holds <b>primitive</b> values
 * with no meaning attached. Nothing in the app may reference these directly — resolve a
 * semantic role from {@link HudColors} instead. That indirection is what allows re-skinning
 * the HUD without touching a single feature view.
 *
 * <p>Tone suffixes follow the Material tone scale (lower = darker).
 */
public final class HudPalette {

    private HudPalette() {
    }

    // Lilac family — the HUD accent hue.
    public static final int Lilac25 = 0xFF504A60; // selected container behind accent content
    public static final int Lilac40 = 0xFF9D85C2; // primary accent
    public static final int Lilac60 = 0xFFB1A7C3; // soft accent, borders, muted fills

    // Charcoal family — dark surfaces, ordered dark to light.
    public static final int Ink900 = 0xFF11111B; // pressed-state ink and scrim base
    public static final int Ink800 = 0xFF1B1D2B; // inset control face
    public static final int Ink700 = 0xFF22283A; // inset control face, softer
    public static final int Charcoal25 = 0xFF2E2E2E; // deep neutral surface
    public static final int Charcoal20 = 0xFF2A2B3D; // deepest surface
    public static final int Charcoal30 = 0xFF3A3A3A; // card surface
    public static final int Charcoal40 = 0xFF424242; // panel interior
    public static final int Charcoal50 = 0xFF484848; // raised buttons and inputs

    // Neutral family — text and glyphs.
    public static final int Neutral45 = 0xFF5A5A5A; // muted outline
    public static final int Neutral60 = 0xFF888888; // disabled / placeholder
    public static final int Neutral70 = 0xFFB3B3B3; // secondary text
    public static final int Neutral100 = 0xFFFFFFFF; // high-contrast text

    // Deeper lilac used only for inset accents.
    public static final int Lilac20 = 0xFF4E495C;

    // Semantic hues.
    public static final int Coral40 = 0xFFF38BA8; // destructive
    public static final int Mint40 = 0xFFA6E3A1; // positive / online
    public static final int Green40 = 0xFF4CAF50; // alignment guides
    public static final int Amber40 = 0xFFF9E2AF; // warning

    // Dark wash backgrounds used behind status banners.
    public static final int DangerContainerDark = 0xFF301616;
    public static final int SuccessContainerDark = 0xFF14281A;

    // Absolute anchors.
    public static final int Black = 0xFF000000;
}
