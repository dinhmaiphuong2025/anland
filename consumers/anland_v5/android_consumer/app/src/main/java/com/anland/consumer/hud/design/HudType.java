package com.anland.consumer.hud.design;

/**
 * Type scale for the HUD.
 *
 * <p>Replaces the twelve ad-hoc sizes (9..24sp) that had accumulated across the HUD views.
 * Roles are named by intent, mirroring Now in Android's typography roles rather than by
 * call site. Values are in sp, so {@code TextView#setTextSize(float)} accepts them directly.
 *
 * <pre>
 * display   18  dialog titles
 * title     16  section headers
 * subtitle  14  row labels, list items
 * body      13  paragraph text, inspector labels
 * label     12  buttons, key caps
 * caption   11  helper lines, badges
 * micro     10  minimum legible size for key caps
 * </pre>
 */
public final class HudType {

    private HudType() {
    }

    public static final float DISPLAY_SP = 18f;
    public static final float TITLE_SP = 16f;
    public static final float SUBTITLE_SP = 14f;
    public static final float BODY_SP = 13f;
    public static final float LABEL_SP = 12f;
    public static final float CAPTION_SP = 11f;
    public static final float MICRO_SP = 10f;

    /** Hard floor for auto-shrinking key labels, so short keys never become unreadable. */
    public static final float KEY_LABEL_FLOOR_SP = 10.2f;

    /** Decorative glyphs such as resize handles, sized above the text scale. */
    public static final float GLYPH_SP = 20f;

    /** Absolute minimum any text may be rendered at. */
    public static final float ABSOLUTE_MIN_SP = 10f;
}
