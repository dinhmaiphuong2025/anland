package com.anland.consumer.hud;

import android.graphics.Canvas;
import com.anland.consumer.R;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Official Lucide Icons (https://lucide.dev) rendered via native Canvas and Path.
 * Uses exact 24x24 Lucide SVG geometry, 2.0dp stroke, round stroke cap & join.
 * 100% vector, zero bloat, and perfectly consistent across the whole application.
 */
public final class HudVectorIcons {

    private static final Path sPath = new Path();
    private static final RectF sRect = new RectF();

    // Toolbar and System Icons
    public static final String ICON_NONE = "none";
    public static final String ICON_SAVE = "save";
    public static final String ICON_ADD = "add";
    public static final String ICON_SNAP = "snap";
    public static final String ICON_LAYOUT = "layout";
    public static final String ICON_EXIT = "exit";
    public static final String ICON_COPY = "copy";
    public static final String ICON_PASTE = "paste";
    public static final String ICON_DELETE = "delete";
    public static final String ICON_CUSTOM = "custom";
    public static final String ICON_DETACH = "detach";
    public static final String ICON_SETTINGS = "settings";

    // HUD Key Label Icons
    public static final String ICON_UP = "up";
    public static final String ICON_DOWN = "down";
    public static final String ICON_LEFT = "left";
    public static final String ICON_RIGHT = "right";
    public static final String ICON_FULLSCREEN = "fullscreen";
    public static final String ICON_MENU = "menu";
    public static final String ICON_MOUSE_L = "mouse_l";
    public static final String ICON_MOUSE_R = "mouse_r";
    public static final String ICON_TOUCHPAD = "touchpad";
    public static final String ICON_KEYBOARD = "keyboard";
    public static final String ICON_KEYBOARD_OFF = "keyboard_off";
    public static final String ICON_SLIDER = "slider";
    public static final String ICON_EYE = "eye";

    // Extended HUD Key Label Icons
    public static final String ICON_HOUSE = "house";
    public static final String ICON_ENTER = "enter";
    public static final String ICON_BACKSPACE = "backspace";
    public static final String ICON_POWER = "power";
    public static final String ICON_LOCK = "lock";
    public static final String ICON_SEARCH = "search";
    public static final String ICON_REFRESH = "refresh";
    public static final String ICON_VOLUME_UP = "volume_up";
    public static final String ICON_MUTE = "mute";
    public static final String ICON_PLAY = "play";
    public static final String ICON_PAUSE = "pause";
    public static final String ICON_BRIGHTNESS = "brightness";
    public static final String ICON_WINDOW = "window";
    public static final String ICON_BROWSER = "browser";
    public static final String ICON_TERMINAL = "terminal";
    public static final String ICON_EDITOR = "editor";
    public static final String ICON_CLOSE = "close";
    public static final String ICON_SUPER = "super";
    public static final String ICON_ALT = "alt";
    public static final String ICON_TAB = "tab";
    public static final String ICON_DESKTOP = "desktop";
    public static final String ICON_POINTER = "pointer";
    public static final String ICON_HAND = "hand";

    public static final String[] ALL_ICONS = new String[]{
            ICON_UP, ICON_DOWN, ICON_LEFT, ICON_RIGHT,
            ICON_MOUSE_L, ICON_MOUSE_R, ICON_COPY, ICON_PASTE,
            ICON_KEYBOARD, ICON_KEYBOARD_OFF, ICON_FULLSCREEN, ICON_MENU, ICON_SETTINGS,
            ICON_HOUSE, ICON_ENTER, ICON_BACKSPACE, ICON_POWER,
            ICON_LOCK, ICON_SEARCH, ICON_REFRESH, ICON_VOLUME_UP,
            ICON_MUTE, ICON_PLAY, ICON_PAUSE, ICON_BRIGHTNESS,
            ICON_WINDOW, ICON_BROWSER, ICON_TERMINAL, ICON_EDITOR,
            ICON_CLOSE, ICON_SUPER, ICON_ALT, ICON_TAB,
            ICON_DESKTOP, ICON_POINTER, ICON_HAND
    };

    public static final int[] ICON_LABELS = new int[]{
            R.string.hud_direction_up, R.string.hud_direction_down, R.string.hud_direction_left, R.string.hud_direction_right,
            R.string.hud_editor_icon_mouse_l, R.string.hud_editor_icon_mouse_r, R.string.hud_editor_copy, R.string.hud_editor_icon_paste,
            R.string.hud_editor_icon_keyboard, R.string.hud_editor_icon_keyboard_off, R.string.hud_editor_icon_fullscreen, R.string.hud_editor_icon_menu, R.string.hud_editor_icon_settings,
            R.string.hud_editor_icon_home, R.string.hud_editor_icon_enter, R.string.hud_editor_icon_backspace, R.string.hud_editor_icon_power,
            R.string.hud_editor_icon_lock, R.string.hud_editor_icon_search, R.string.hud_editor_icon_refresh, R.string.hud_editor_icon_volume_up,
            R.string.hud_editor_icon_mute, R.string.hud_editor_icon_play, R.string.hud_editor_icon_pause, R.string.hud_editor_icon_brightness,
            R.string.hud_editor_icon_window, R.string.hud_editor_icon_browser, R.string.hud_editor_icon_terminal, R.string.hud_editor_icon_editor,
            R.string.hud_editor_icon_close, R.string.hud_editor_icon_super, R.string.hud_editor_icon_alt, R.string.hud_editor_icon_tab,
            R.string.hud_editor_icon_desktop, R.string.hud_editor_icon_pointer, R.string.hud_editor_icon_hand
    };

    /**
     * Draws a Lucide icon centered at (cx, cy) scaled to the given size.
     */
    public static void drawIcon(Canvas canvas, String iconId, float cx, float cy, float size, Paint paint) {
        if (iconId == null || ICON_NONE.equals(iconId) || iconId.isEmpty()) return;

        // Lucide SVG is defined on a 24x24 grid.
        // Transform local (0..24) coordinate to canvas:
        // x_canvas = cx + (x - 12) * scale
        // y_canvas = cy + (y - 12) * scale
        float scale = size / 24f;
        int originalColor = paint.getColor();
        Paint.Style origStyle = paint.getStyle();
        float origStroke = paint.getStrokeWidth();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * scale);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        switch (iconId) {
            case ICON_SAVE: { // Lucide: save
                // <path d="M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" />
                // <path d="M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7" />
                // <path d="M7 3v4a1 1 0 0 0 1 1h7" />
                sPath.reset();
                sPath.moveTo(p(cx, 15.2f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 6.8f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 8.8f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 3f, scale));
                sPath.close();
                canvas.drawPath(sPath, paint);

                sPath.reset();
                sPath.moveTo(p(cx, 17f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 17f, scale), p(cy, 14f, scale));
                sPath.lineTo(p(cx, 7f, scale), p(cy, 14f, scale));
                sPath.lineTo(p(cx, 7f, scale), p(cy, 21f, scale));
                canvas.drawPath(sPath, paint);

                sPath.reset();
                sPath.moveTo(p(cx, 7f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 7f, scale), p(cy, 8f, scale));
                sPath.lineTo(p(cx, 15f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_ADD: { // Lucide: plus
                // <path d="M5 12h14" />
                // <path d="M12 5v14" />
                canvas.drawLine(p(cx, 5f, scale), p(cy, 12f, scale), p(cx, 19f, scale), p(cy, 12f, scale), paint);
                canvas.drawLine(p(cx, 12f, scale), p(cy, 5f, scale), p(cx, 12f, scale), p(cy, 19f, scale), paint);
                break;
            }

            case ICON_SNAP: { // Official Lucide magnet (clean upright U horseshoe)
                // Left leg (outer x=4..8, inner x=8..16)
                sPath.reset();
                sPath.moveTo(p(cx, 4f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 13f, scale));
                sRect.set(p(cx, 4f, scale), p(cy, 5f, scale), p(cx, 20f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 180, -180, false);
                sPath.lineTo(p(cx, 20f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 16f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 16f, scale), p(cy, 13f, scale));
                sRect.set(p(cx, 8f, scale), p(cy, 8.5f, scale), p(cx, 16f, scale), p(cy, 16.5f, scale));
                sPath.arcTo(sRect, 0, 180, false);
                sPath.lineTo(p(cx, 8f, scale), p(cy, 5f, scale));
                sPath.close();
                canvas.drawPath(sPath, paint);

                // Magnetic pole tip bands (horizontal lines across pole tips)
                canvas.drawLine(p(cx, 4f, scale), p(cy, 8.5f, scale), p(cx, 8f, scale), p(cy, 8.5f, scale), paint);
                canvas.drawLine(p(cx, 16f, scale), p(cy, 8.5f, scale), p(cx, 20f, scale), p(cy, 8.5f, scale), paint);
                break;
            }

            case ICON_LAYOUT: { // Lucide: layout-grid
                drawLucideRect(canvas, cx, cy, 3f, 3f, 7f, 7f, 1.5f, scale, paint);
                drawLucideRect(canvas, cx, cy, 14f, 3f, 7f, 7f, 1.5f, scale, paint);
                drawLucideRect(canvas, cx, cy, 14f, 14f, 7f, 7f, 1.5f, scale, paint);
                drawLucideRect(canvas, cx, cy, 3f, 14f, 7f, 7f, 1.5f, scale, paint);
                break;
            }

            case ICON_EXIT: { // Lucide: log-out
                // <path d="m16 17 5-5-5-5" />
                // <path d="M21 12H9" />
                // <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                sPath.reset();
                sPath.moveTo(p(cx, 16f, scale), p(cy, 17f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 16f, scale), p(cy, 7f, scale));
                canvas.drawPath(sPath, paint);

                canvas.drawLine(p(cx, 21f, scale), p(cy, 12f, scale), p(cx, 9f, scale), p(cy, 12f, scale), paint);

                sPath.reset();
                sPath.moveTo(p(cx, 9f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 9f, scale), p(cy, 3f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_COPY: { // Lucide: copy
                // <rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
                // <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
                drawLucideRect(canvas, cx, cy, 8f, 8f, 13f, 13f, 2f, scale, paint);
                sPath.reset();
                sPath.moveTo(p(cx, 4f, scale), p(cy, 16f, scale));
                sPath.lineTo(p(cx, 2f, scale), p(cy, 14f, scale));
                sPath.lineTo(p(cx, 2f, scale), p(cy, 4f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 2f, scale));
                sPath.lineTo(p(cx, 14f, scale), p(cy, 2f, scale));
                sPath.lineTo(p(cx, 16f, scale), p(cy, 4f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_PASTE: { // Lucide: clipboard
                drawLucideRect(canvas, cx, cy, 5f, 4f, 14f, 17f, 2f, scale, paint);
                drawLucideRect(canvas, cx, cy, 9f, 2f, 6f, 4f, 1f, scale, paint);
                canvas.drawLine(p(cx, 8f, scale), p(cy, 11f, scale), p(cx, 16f, scale), p(cy, 11f, scale), paint);
                canvas.drawLine(p(cx, 8f, scale), p(cy, 15f, scale), p(cx, 16f, scale), p(cy, 15f, scale), paint);
                break;
            }

            case ICON_DELETE: { // Lucide: trash
                // <path d="M10 11v6" /><path d="M14 11v6" />
                // <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                // <path d="M3 6h18" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                canvas.drawLine(p(cx, 3f, scale), p(cy, 6f, scale), p(cx, 21f, scale), p(cy, 6f, scale), paint);
                canvas.drawLine(p(cx, 10f, scale), p(cy, 11f, scale), p(cx, 10f, scale), p(cy, 17f, scale), paint);
                canvas.drawLine(p(cx, 14f, scale), p(cy, 11f, scale), p(cx, 14f, scale), p(cy, 17f, scale), paint);

                sPath.reset();
                sPath.moveTo(p(cx, 19f, scale), p(cy, 6f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 17f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 7f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 6f, scale));
                canvas.drawPath(sPath, paint);

                sPath.reset();
                sPath.moveTo(p(cx, 9f, scale), p(cy, 6f, scale));
                sPath.lineTo(p(cx, 9f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 15f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 15f, scale), p(cy, 6f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_CUSTOM: { // Lucide: pencil
                sPath.reset();
                sPath.moveTo(p(cx, 17.19f, scale), p(cy, 2.82f, scale));
                sPath.lineTo(p(cx, 21.17f, scale), p(cy, 6.81f, scale));
                sPath.lineTo(p(cx, 7.34f, scale), p(cy, 20.65f, scale));
                sPath.lineTo(p(cx, 2.52f, scale), p(cy, 21.98f, scale));
                sPath.lineTo(p(cx, 3.84f, scale), p(cy, 17.17f, scale));
                sPath.close();
                canvas.drawPath(sPath, paint);
                canvas.drawLine(p(cx, 15f, scale), p(cy, 5f, scale), p(cx, 19f, scale), p(cy, 9f, scale), paint);
                break;
            }

            case ICON_DETACH: { // Lucide: external-link / eject / detach
                // Top-right arrow pointing outward
                sPath.reset();
                sPath.moveTo(p(cx, 15f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 9f, scale));
                canvas.drawPath(sPath, paint);
                canvas.drawLine(p(cx, 10f, scale), p(cy, 14f, scale), p(cx, 21f, scale), p(cy, 3f, scale), paint);

                // Container box with top-right opening
                sPath.reset();
                sPath.moveTo(p(cx, 13f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 7f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 17f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 11f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_SETTINGS: { // Official Lucide: settings (cog)
                // <path d="M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915" />
                // <circle cx="12" cy="12" r="3" />
                canvas.drawCircle(cx, cy, 3f * scale, paint);

                // 6 cog teeth around the gear perimeter with rounded peaks
                sPath.reset();
                final float rInner = 6.2f * scale;
                final float rOuter = 9.2f * scale;
                final float rValley = 6.8f * scale;

                for (int i = 0; i < 6; i++) {
                    double angleMid = Math.toRadians(i * 60.0);
                    double a0 = angleMid - Math.toRadians(20.0);
                    double a1 = angleMid - Math.toRadians(11.0);
                    double a2 = angleMid + Math.toRadians(11.0);
                    double a3 = angleMid + Math.toRadians(20.0);

                    float x0 = cx + (float) (Math.cos(a0) * rValley);
                    float y0 = cy + (float) (Math.sin(a0) * rValley);
                    float x1 = cx + (float) (Math.cos(a1) * rOuter);
                    float y1 = cy + (float) (Math.sin(a1) * rOuter);
                    float x2 = cx + (float) (Math.cos(a2) * rOuter);
                    float y2 = cy + (float) (Math.sin(a2) * rOuter);
                    float x3 = cx + (float) (Math.cos(a3) * rValley);
                    float y3 = cy + (float) (Math.sin(a3) * rValley);

                    if (i == 0) {
                        sPath.moveTo(x0, y0);
                    } else {
                        sPath.lineTo(x0, y0);
                    }
                    sPath.lineTo(x1, y1);
                    sPath.lineTo(x2, y2);
                    sPath.lineTo(x3, y3);
                }
                sPath.close();
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_UP: { // Lucide: arrow-up
                sPath.reset();
                sPath.moveTo(p(cx, 5f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                canvas.drawLine(p(cx, 12f, scale), p(cy, 19f, scale), p(cx, 12f, scale), p(cy, 5f, scale), paint);
                break;
            }

            case ICON_DOWN: { // Lucide: arrow-down
                sPath.reset();
                sPath.moveTo(p(cx, 5f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                canvas.drawLine(p(cx, 12f, scale), p(cy, 5f, scale), p(cx, 12f, scale), p(cy, 19f, scale), paint);
                break;
            }

            case ICON_LEFT: { // Lucide: arrow-left
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 5f, scale));
                canvas.drawPath(sPath, paint);
                canvas.drawLine(p(cx, 19f, scale), p(cy, 12f, scale), p(cx, 5f, scale), p(cy, 12f, scale), paint);
                break;
            }

            case ICON_RIGHT: { // Lucide: arrow-right
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 19f, scale));
                canvas.drawPath(sPath, paint);
                canvas.drawLine(p(cx, 5f, scale), p(cy, 12f, scale), p(cx, 19f, scale), p(cy, 12f, scale), paint);
                break;
            }

            case ICON_MOUSE_L: { // Official Lucide: mouse-left
                // <path d="M12 7.318V10" />
                // <path d="M5 10v5a7 7 0 0 0 14 0V9c0-3.527-2.608-6.515-6-7" />
                // <circle cx="7" cy="4" r="2" />
                canvas.drawLine(p(cx, 12f, scale), p(cy, 7.32f, scale), p(cx, 12f, scale), p(cy, 10f, scale), paint);

                sPath.reset();
                sPath.moveTo(p(cx, 5f, scale), p(cy, 10f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 15f, scale));
                sRect.set(p(cx, 5f, scale), p(cy, 8f, scale), p(cx, 19f, scale), p(cy, 22f, scale));
                sPath.arcTo(sRect, 180, -180, false);
                sPath.lineTo(p(cx, 19f, scale), p(cy, 9f, scale));
                sRect.set(p(cx, 7f, scale), p(cy, 2f, scale), p(cx, 19f, scale), p(cy, 14f, scale));
                sPath.arcTo(sRect, 0, -90, false);
                canvas.drawPath(sPath, paint);

                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(p(cx, 7f, scale), p(cy, 4f, scale), 2f * scale, paint);
                break;
            }

            case ICON_MOUSE_R: { // Official Lucide: mouse-right
                // <path d="M12 7.318V10" />
                // <path d="M19 10v5a7 7 0 0 1-14 0V9c0-3.527 2.608-6.515 6-7" />
                // <circle cx="17" cy="4" r="2" />
                canvas.drawLine(p(cx, 12f, scale), p(cy, 7.32f, scale), p(cx, 12f, scale), p(cy, 10f, scale), paint);

                sPath.reset();
                sPath.moveTo(p(cx, 19f, scale), p(cy, 10f, scale));
                sPath.lineTo(p(cx, 19f, scale), p(cy, 15f, scale));
                sRect.set(p(cx, 5f, scale), p(cy, 8f, scale), p(cx, 19f, scale), p(cy, 22f, scale));
                sPath.arcTo(sRect, 0, 180, false);
                sPath.lineTo(p(cx, 5f, scale), p(cy, 9f, scale));
                sRect.set(p(cx, 5f, scale), p(cy, 2f, scale), p(cx, 17f, scale), p(cy, 14f, scale));
                sPath.arcTo(sRect, 180, 90, false);
                canvas.drawPath(sPath, paint);

                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(p(cx, 17f, scale), p(cy, 4f, scale), 2f * scale, paint);
                break;
            }

            case ICON_FULLSCREEN: { // Lucide: expand
                // <path d="m15 15 6 6" /><path d="m15 9 6-6" />
                // <path d="M21 16v5h-5" /><path d="M21 8V3h-5" />
                // <path d="M3 16v5h5" /><path d="m3 21 6-6" />
                // <path d="M3 8V3h5" /><path d="M9 9 3 3" />
                // Top-right
                canvas.drawLine(p(cx, 15f, scale), p(cy, 9f, scale), p(cx, 21f, scale), p(cy, 3f, scale), paint);
                sPath.reset();
                sPath.moveTo(p(cx, 16f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);

                // Bottom-right
                canvas.drawLine(p(cx, 15f, scale), p(cy, 15f, scale), p(cx, 21f, scale), p(cy, 21f, scale), paint);
                sPath.reset();
                sPath.moveTo(p(cx, 21f, scale), p(cy, 16f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 16f, scale), p(cy, 21f, scale));
                canvas.drawPath(sPath, paint);

                // Bottom-left
                canvas.drawLine(p(cx, 9f, scale), p(cy, 15f, scale), p(cx, 3f, scale), p(cy, 21f, scale), paint);
                sPath.reset();
                sPath.moveTo(p(cx, 3f, scale), p(cy, 16f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 8f, scale), p(cy, 21f, scale));
                canvas.drawPath(sPath, paint);

                // Top-left
                canvas.drawLine(p(cx, 9f, scale), p(cy, 9f, scale), p(cx, 3f, scale), p(cy, 3f, scale), paint);
                sPath.reset();
                sPath.moveTo(p(cx, 8f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_MENU: { // Lucide: menu
                canvas.drawLine(p(cx, 4f, scale), p(cy, 6f, scale), p(cx, 20f, scale), p(cy, 6f, scale), paint);
                canvas.drawLine(p(cx, 4f, scale), p(cy, 12f, scale), p(cx, 20f, scale), p(cy, 12f, scale), paint);
                canvas.drawLine(p(cx, 4f, scale), p(cy, 18f, scale), p(cx, 20f, scale), p(cy, 18f, scale), paint);
                break;
            }

            case ICON_TOUCHPAD: { // Official Lucide trackpad / touchpad rectangle with bottom button split
                // Outer touchpad surface
                drawLucideRect(canvas, cx, cy, 3f, 4f, 18f, 16f, 3f, scale, paint);
                // Horizontal divider for lower button zone
                canvas.drawLine(p(cx, 3f, scale), p(cy, 15f, scale), p(cx, 21f, scale), p(cy, 15f, scale), paint);
                // Vertical divider separating left and right click zones
                canvas.drawLine(p(cx, 12f, scale), p(cy, 15f, scale), p(cx, 12f, scale), p(cy, 20f, scale), paint);
                break;
            }

            case ICON_KEYBOARD: { // Lucide: keyboard
                drawLucideRect(canvas, cx, cy, 2f, 4f, 20f, 16f, 2.5f, scale, paint);
                canvas.drawLine(p(cx, 7f, scale), p(cy, 16f, scale), p(cx, 17f, scale), p(cy, 16f, scale), paint);
                canvas.drawCircle(p(cx, 6f, scale), p(cy, 8f, scale), 0.8f * scale, paint);
                canvas.drawCircle(p(cx, 10f, scale), p(cy, 8f, scale), 0.8f * scale, paint);
                canvas.drawCircle(p(cx, 14f, scale), p(cy, 8f, scale), 0.8f * scale, paint);
                canvas.drawCircle(p(cx, 18f, scale), p(cy, 8f, scale), 0.8f * scale, paint);
                canvas.drawCircle(p(cx, 8f, scale), p(cy, 12f, scale), 0.8f * scale, paint);
                canvas.drawCircle(p(cx, 12f, scale), p(cy, 12f, scale), 0.8f * scale, paint);
                canvas.drawCircle(p(cx, 16f, scale), p(cy, 12f, scale), 0.8f * scale, paint);
                break;
            }

            case ICON_KEYBOARD_OFF: { // Lucide: keyboard-off
                // slash line across
                canvas.drawLine(p(cx, 2f, scale), p(cy, 2f, scale), p(cx, 22f, scale), p(cy, 22f, scale), paint);
                // top-right corner
                sPath.reset();
                sPath.moveTo(p(cx, 9.69f, scale), p(cy, 4f, scale));
                sPath.lineTo(p(cx, 20f, scale), p(cy, 4f, scale));
                sPath.lineTo(p(cx, 22f, scale), p(cy, 6f, scale));
                sPath.lineTo(p(cx, 22f, scale), p(cy, 16.41f, scale));
                canvas.drawPath(sPath, paint);
                // bottom-left corner
                sPath.reset();
                sPath.moveTo(p(cx, 20f, scale), p(cy, 20f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 20f, scale));
                sPath.lineTo(p(cx, 2f, scale), p(cy, 18f, scale));
                sPath.lineTo(p(cx, 2f, scale), p(cy, 6f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 4f, scale));
                canvas.drawPath(sPath, paint);
                // partial spacebar
                canvas.drawLine(p(cx, 7f, scale), p(cy, 16f, scale), p(cx, 16f, scale), p(cy, 16f, scale), paint);
                break;
            }

            case ICON_SLIDER: { // Lucide: sliders-horizontal
                canvas.drawLine(p(cx, 3f, scale), p(cy, 6f, scale), p(cx, 10f, scale), p(cy, 6f, scale), paint);
                canvas.drawLine(p(cx, 14f, scale), p(cy, 6f, scale), p(cx, 21f, scale), p(cy, 6f, scale), paint);
                canvas.drawLine(p(cx, 12f, scale), p(cy, 3f, scale), p(cx, 12f, scale), p(cy, 9f, scale), paint);

                canvas.drawLine(p(cx, 3f, scale), p(cy, 18f, scale), p(cx, 12f, scale), p(cy, 18f, scale), paint);
                canvas.drawLine(p(cx, 16f, scale), p(cy, 18f, scale), p(cx, 21f, scale), p(cy, 18f, scale), paint);
                canvas.drawLine(p(cx, 14f, scale), p(cy, 15f, scale), p(cx, 14f, scale), p(cy, 21f, scale), paint);
                break;
            }

            case ICON_EYE: { // Lucide: eye
                canvas.drawCircle(cx, cy, 3f * scale, paint);
                sRect.set(p(cx, 2f, scale), p(cy, 4f, scale), p(cx, 22f, scale), p(cy, 20f, scale));
                canvas.drawArc(sRect, 30, 120, false, paint);
                canvas.drawArc(sRect, 210, 120, false, paint);
                break;
            }
            case ICON_HOUSE: { // Lucide: house
                // <path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8" />
                sPath.reset();
                sPath.moveTo(p(cx, 15f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 15f, scale), p(cy, 13f, scale));
                sRect.set(p(cx, 13f, scale), p(cy, 12f, scale), p(cx, 15f, scale), p(cy, 14f, scale));
                sPath.arcTo(sRect, 0f, -90f);
                sPath.lineTo(p(cx, 10f, scale), p(cy, 12f, scale));
                sRect.set(p(cx, 9f, scale), p(cy, 12f, scale), p(cx, 11f, scale), p(cy, 14f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                sPath.lineTo(p(cx, 9f, scale), p(cy, 21f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                sPath.reset();
                sPath.moveTo(p(cx, 3f, scale), p(cy, 10f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 7.9995f, scale), p(cx, 7f, scale), p(cy, 11.9995f, scale));
                sPath.arcTo(sRect, 179.9863f, 49.8106f);
                sPath.lineTo(p(cx, 10.709f, scale), p(cy, 2.472f, scale));
                sRect.set(p(cx, 10f, scale), p(cy, 1.9995f, scale), p(cx, 14f, scale), p(cy, 5.9995f, scale));
                sPath.arcTo(sRect, -130.2032f, 80.4063f);
                sPath.lineTo(p(cx, 20.291f, scale), p(cy, 8.472f, scale));
                sRect.set(p(cx, 17f, scale), p(cy, 7.9995f, scale), p(cx, 21f, scale), p(cy, 11.9995f, scale));
                sPath.arcTo(sRect, -49.7968f, 49.8106f);
                sPath.lineTo(p(cx, 21f, scale), p(cy, 19f, scale));
                sRect.set(p(cx, 17f, scale), p(cy, 17f, scale), p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 0f, 90f);
                sPath.lineTo(p(cx, 5f, scale), p(cy, 21f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 17f, scale), p(cx, 7f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 90f, 90f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_ENTER: { // Lucide: corner-down-left
                // <path d="M20 4v7a4 4 0 0 1-4 4H4" />
                sPath.reset();
                sPath.moveTo(p(cx, 20f, scale), p(cy, 4f, scale));
                sPath.lineTo(p(cx, 20f, scale), p(cy, 11f, scale));
                sRect.set(p(cx, 12f, scale), p(cy, 7f, scale), p(cx, 20f, scale), p(cy, 15f, scale));
                sPath.arcTo(sRect, 0f, 90f);
                sPath.lineTo(p(cx, 4f, scale), p(cy, 15f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m9 10-5 5 5 5" />
                sPath.reset();
                sPath.moveTo(p(cx, 9f, scale), p(cy, 10f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 15f, scale));
                sPath.lineTo(p(cx, 9f, scale), p(cy, 20f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_BACKSPACE: { // Lucide: delete
                // <path d="M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z" />
                sPath.reset();
                sPath.moveTo(p(cx, 10f, scale), p(cy, 5f, scale));
                sRect.set(p(cx, 8.0001f, scale), p(cy, 5f, scale), p(cx, 12.0001f, scale), p(cy, 9f, scale));
                sPath.arcTo(sRect, -90.0032f, -42.2227f);
                sPath.lineTo(p(cx, 2.328f, scale), p(cy, 11.259f, scale));
                sRect.set(p(cx, 2.0001f, scale), p(cy, 10.9995f, scale), p(cx, 4.0001f, scale), p(cy, 12.9995f, scale));
                sPath.arcTo(sRect, -132.226f, -95.5481f);
                sPath.lineTo(p(cx, 8.656f, scale), p(cy, 18.481f, scale));
                sRect.set(p(cx, 8.0001f, scale), p(cy, 15f, scale), p(cx, 12.0001f, scale), p(cy, 19f, scale));
                sPath.arcTo(sRect, 132.226f, -42.2227f);
                sPath.lineTo(p(cx, 20f, scale), p(cy, 19f, scale));
                sRect.set(p(cx, 18f, scale), p(cy, 15f, scale), p(cx, 22f, scale), p(cy, 19f, scale));
                sPath.arcTo(sRect, 90f, -90f);
                sPath.lineTo(p(cx, 22f, scale), p(cy, 7f, scale));
                sRect.set(p(cx, 18f, scale), p(cy, 5f, scale), p(cx, 22f, scale), p(cy, 9f, scale));
                sPath.arcTo(sRect, 0f, -90f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                // <path d="m12 9 6 6" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 9f, scale));
                sPath.lineTo(p(cx, 18f, scale), p(cy, 15f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m18 9-6 6" />
                sPath.reset();
                sPath.moveTo(p(cx, 18f, scale), p(cy, 9f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 15f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_POWER: { // Lucide: power
                // <path d="M12 2v10" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 2f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M18.4 6.6a9 9 0 1 1-12.77.04" />
                sPath.reset();
                sPath.moveTo(p(cx, 18.4f, scale), p(cy, 6.6f, scale));
                sRect.set(p(cx, 3.0349f, scale), p(cy, 3.9628f, scale), p(cx, 21.0349f, scale), p(cy, 21.9628f, scale));
                sPath.arcTo(sRect, -44.9895f, 269.62f);
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_LOCK: { // Lucide: lock
                drawLucideRect(canvas, cx, cy, 3f, 11f, 18f, 11f, 2f, scale, paint);
                // <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                sPath.reset();
                sPath.moveTo(p(cx, 7f, scale), p(cy, 11f, scale));
                sPath.lineTo(p(cx, 7f, scale), p(cy, 7f, scale));
                sRect.set(p(cx, 7f, scale), p(cy, 2f, scale), p(cx, 17f, scale), p(cy, 12f, scale));
                sPath.arcTo(sRect, 180f, 180f);
                sPath.lineTo(p(cx, 17f, scale), p(cy, 11f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_SEARCH: { // Lucide: search
                // <path d="m21 21-4.34-4.34" />
                sPath.reset();
                sPath.moveTo(p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.lineTo(p(cx, 16.66f, scale), p(cy, 16.66f, scale));
                canvas.drawPath(sPath, paint);
                canvas.drawCircle(p(cx, 11f, scale), p(cy, 11f, scale), 8f * scale, paint);
                break;
            }

            case ICON_REFRESH: { // Lucide: refresh-cw
                // <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
                sPath.reset();
                sPath.moveTo(p(cx, 3f, scale), p(cy, 12f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 3f, scale), p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 180f, 90f);
                sRect.set(p(cx, 2.2133f, scale), p(cy, 2.9999f, scale), p(cx, 21.7133f, scale), p(cy, 22.4999f, scale));
                sPath.arcTo(sRect, -89.7845f, 43.8152f);
                sPath.lineTo(p(cx, 21f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M21 3v5h-5" />
                sPath.reset();
                sPath.moveTo(p(cx, 21f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 8f, scale));
                sPath.lineTo(p(cx, 16f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
                sPath.reset();
                sPath.moveTo(p(cx, 21f, scale), p(cy, 12f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 3f, scale), p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 0f, 90f);
                sRect.set(p(cx, 2.2867f, scale), p(cy, 1.5001f, scale), p(cx, 21.7867f, scale), p(cy, 21.0001f, scale));
                sPath.arcTo(sRect, 90.2155f, 43.8152f);
                sPath.lineTo(p(cx, 3f, scale), p(cy, 16f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M8 16H3v5" />
                sPath.reset();
                sPath.moveTo(p(cx, 8f, scale), p(cy, 16f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 16f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 21f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_VOLUME_UP: { // Lucide: volume-2
                // <path d="M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z" />
                sPath.reset();
                sPath.moveTo(p(cx, 11f, scale), p(cy, 4.702f, scale));
                sRect.set(p(cx, 9.59f, scale), p(cy, 3.998f, scale), p(cx, 11f, scale), p(cy, 5.408f, scale));
                sPath.arcTo(sRect, -0.0828f, -134.8586f);
                sPath.lineTo(p(cx, 6.413f, scale), p(cy, 7.587f, scale));
                sRect.set(p(cx, 4.0201f, scale), p(cy, 5.2f, scale), p(cx, 6.8201f, scale), p(cy, 8f, scale));
                sPath.arcTo(sRect, 44.8292f, 45.3388f);
                sPath.lineTo(p(cx, 3f, scale), p(cy, 8f, scale));
                sRect.set(p(cx, 2f, scale), p(cy, 8f, scale), p(cx, 4f, scale), p(cy, 10f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                sPath.lineTo(p(cx, 2f, scale), p(cy, 15f, scale));
                sRect.set(p(cx, 2f, scale), p(cy, 14f, scale), p(cx, 4f, scale), p(cy, 16f, scale));
                sPath.arcTo(sRect, 180f, -90f);
                sPath.lineTo(p(cx, 5.416f, scale), p(cy, 16f, scale));
                sRect.set(p(cx, 4.0201f, scale), p(cy, 16f, scale), p(cx, 6.8201f, scale), p(cy, 18.8f, scale));
                sPath.arcTo(sRect, -90.168f, 45.3388f);
                sPath.lineTo(p(cx, 9.796f, scale), p(cy, 19.797f, scale));
                sRect.set(p(cx, 9.59f, scale), p(cy, 18.594f, scale), p(cx, 11f, scale), p(cy, 20.004f, scale));
                sPath.arcTo(sRect, 135.0564f, -135.1361f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                // <path d="M16 9a5 5 0 0 1 0 6" />
                sPath.reset();
                sPath.moveTo(p(cx, 16f, scale), p(cy, 9f, scale));
                sRect.set(p(cx, 7f, scale), p(cy, 7f, scale), p(cx, 17f, scale), p(cy, 17f, scale));
                sPath.arcTo(sRect, -36.8699f, 73.7398f);
                canvas.drawPath(sPath, paint);
                // <path d="M19.364 18.364a9 9 0 0 0 0-12.728" />
                sPath.reset();
                sPath.moveTo(p(cx, 19.364f, scale), p(cy, 18.364f, scale));
                sRect.set(p(cx, 4.0001f, scale), p(cy, 3f, scale), p(cx, 22.0001f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 45.0004f, -90.0007f);
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_MUTE: { // Lucide: volume-x
                // <path d="M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z" />
                sPath.reset();
                sPath.moveTo(p(cx, 11f, scale), p(cy, 4.702f, scale));
                sRect.set(p(cx, 9.6001f, scale), p(cy, 3.9907f, scale), p(cx, 11.0001f, scale), p(cy, 5.3907f, scale));
                sPath.arcTo(sRect, 0.923f, -136.8704f);
                sPath.lineTo(p(cx, 6.413f, scale), p(cy, 7.587f, scale));
                sRect.set(p(cx, 4.0201f, scale), p(cy, 5.2f, scale), p(cx, 6.8201f, scale), p(cy, 8f, scale));
                sPath.arcTo(sRect, 44.8292f, 45.3388f);
                sPath.lineTo(p(cx, 3f, scale), p(cy, 8f, scale));
                sRect.set(p(cx, 2f, scale), p(cy, 8f, scale), p(cx, 4f, scale), p(cy, 10f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                sPath.lineTo(p(cx, 2f, scale), p(cy, 15f, scale));
                sRect.set(p(cx, 2f, scale), p(cy, 14f, scale), p(cx, 4f, scale), p(cy, 16f, scale));
                sPath.arcTo(sRect, 180f, -90f);
                sPath.lineTo(p(cx, 5.416f, scale), p(cy, 16f, scale));
                sRect.set(p(cx, 4.0201f, scale), p(cy, 16f, scale), p(cx, 6.8201f, scale), p(cy, 18.8f, scale));
                sPath.arcTo(sRect, -90.168f, 45.3388f);
                sPath.lineTo(p(cx, 9.796f, scale), p(cy, 19.797f, scale));
                sRect.set(p(cx, 9.6001f, scale), p(cy, 18.6114f, scale), p(cx, 11.0001f, scale), p(cy, 20.0114f, scale));
                sPath.arcTo(sRect, 136.0695f, -137.1623f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                // <path d="m16.5 14.5 5-5" />
                sPath.reset();
                sPath.moveTo(p(cx, 16.5f, scale), p(cy, 14.5f, scale));
                sPath.lineTo(p(cx, 21.5f, scale), p(cy, 9.5f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m16.5 9.5 5 5" />
                sPath.reset();
                sPath.moveTo(p(cx, 16.5f, scale), p(cy, 9.5f, scale));
                sPath.lineTo(p(cx, 21.5f, scale), p(cy, 14.5f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_PLAY: { // Lucide: play
                // <path d="M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z" />
                sPath.reset();
                sPath.moveTo(p(cx, 5f, scale), p(cy, 5f, scale));
                sRect.set(p(cx, 5f, scale), p(cy, 2.9994f, scale), p(cx, 9f, scale), p(cy, 6.9994f, scale));
                sPath.arcTo(sRect, 179.983f, 120.282f);
                sPath.lineTo(p(cx, 20.005f, scale), p(cy, 10.27f, scale));
                sRect.set(p(cx, 17.0012f, scale), p(cy, 9.9999f, scale), p(cx, 21.0012f, scale), p(cy, 13.9999f, scale));
                sPath.arcTo(sRect, -59.8754f, 119.6514f);
                sPath.lineTo(p(cx, 8.008f, scale), p(cy, 20.728f, scale));
                sRect.set(p(cx, 5f, scale), p(cy, 17.0006f, scale), p(cx, 9f, scale), p(cy, 21.0006f, scale));
                sPath.arcTo(sRect, 59.735f, 120.282f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_PAUSE: { // Lucide: pause
                drawLucideRect(canvas, cx, cy, 14f, 3f, 5f, 18f, 1f, scale, paint);
                drawLucideRect(canvas, cx, cy, 5f, 3f, 5f, 18f, 1f, scale, paint);
                break;
            }

            case ICON_BRIGHTNESS: { // Lucide: sun
                canvas.drawCircle(p(cx, 12f, scale), p(cy, 12f, scale), 4f * scale, paint);
                // <path d="M12 2v2" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 2f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 4f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M12 20v2" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 20f, scale));
                sPath.lineTo(p(cx, 12f, scale), p(cy, 22f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m4.93 4.93 1.41 1.41" />
                sPath.reset();
                sPath.moveTo(p(cx, 4.93f, scale), p(cy, 4.93f, scale));
                sPath.lineTo(p(cx, 6.34f, scale), p(cy, 6.34f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m17.66 17.66 1.41 1.41" />
                sPath.reset();
                sPath.moveTo(p(cx, 17.66f, scale), p(cy, 17.66f, scale));
                sPath.lineTo(p(cx, 19.07f, scale), p(cy, 19.07f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M2 12h2" />
                sPath.reset();
                sPath.moveTo(p(cx, 2f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M20 12h2" />
                sPath.reset();
                sPath.moveTo(p(cx, 20f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 22f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m6.34 17.66-1.41 1.41" />
                sPath.reset();
                sPath.moveTo(p(cx, 6.34f, scale), p(cy, 17.66f, scale));
                sPath.lineTo(p(cx, 4.93f, scale), p(cy, 19.07f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m19.07 4.93-1.41 1.41" />
                sPath.reset();
                sPath.moveTo(p(cx, 19.07f, scale), p(cy, 4.93f, scale));
                sPath.lineTo(p(cx, 17.66f, scale), p(cy, 6.34f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_WINDOW: { // Lucide: app-window
                drawLucideRect(canvas, cx, cy, 2f, 4f, 20f, 16f, 2f, scale, paint);
                // <path d="M10 4v4" />
                sPath.reset();
                sPath.moveTo(p(cx, 10f, scale), p(cy, 4f, scale));
                sPath.lineTo(p(cx, 10f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M2 8h20" />
                sPath.reset();
                sPath.moveTo(p(cx, 2f, scale), p(cy, 8f, scale));
                sPath.lineTo(p(cx, 22f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M6 4v4" />
                sPath.reset();
                sPath.moveTo(p(cx, 6f, scale), p(cy, 4f, scale));
                sPath.lineTo(p(cx, 6f, scale), p(cy, 8f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_BROWSER: { // Lucide: globe
                canvas.drawCircle(p(cx, 12f, scale), p(cy, 12f, scale), 10f * scale, paint);
                // <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 2f, scale));
                sRect.set(p(cx, 8f, scale), p(cy, -2.5f, scale), p(cx, 37f, scale), p(cy, 26.5f, scale));
                sPath.arcTo(sRect, -136.3972f, -87.2056f);
                sRect.set(p(cx, -13f, scale), p(cy, -2.5f, scale), p(cx, 16f, scale), p(cy, 26.5f, scale));
                sPath.arcTo(sRect, 43.6028f, -87.2056f);
                canvas.drawPath(sPath, paint);
                // <path d="M2 12h20" />
                sPath.reset();
                sPath.moveTo(p(cx, 2f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 22f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_TERMINAL: { // Lucide: terminal
                // <path d="M12 19h8" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 19f, scale));
                sPath.lineTo(p(cx, 20f, scale), p(cy, 19f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m4 17 6-6-6-6" />
                sPath.reset();
                sPath.moveTo(p(cx, 4f, scale), p(cy, 17f, scale));
                sPath.lineTo(p(cx, 10f, scale), p(cy, 11f, scale));
                sPath.lineTo(p(cx, 4f, scale), p(cy, 5f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_EDITOR: { // Lucide: square-pen
                // <path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                sPath.reset();
                sPath.moveTo(p(cx, 12f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 5f, scale), p(cy, 3f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 3f, scale), p(cx, 7f, scale), p(cy, 7f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                sPath.lineTo(p(cx, 3f, scale), p(cy, 19f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 17f, scale), p(cx, 7f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 180f, -90f);
                sPath.lineTo(p(cx, 19f, scale), p(cy, 21f, scale));
                sRect.set(p(cx, 17f, scale), p(cy, 17f, scale), p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 90f, -90f);
                sPath.lineTo(p(cx, 21f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z" />
                sPath.reset();
                sPath.moveTo(p(cx, 18.375f, scale), p(cy, 2.625f, scale));
                sRect.set(p(cx, 17.7537f, scale), p(cy, 2.0037f, scale), p(cx, 21.9963f, scale), p(cy, 6.2463f, scale));
                sPath.arcTo(sRect, -135f, 180f);
                sPath.lineTo(p(cx, 12.362f, scale), p(cy, 14.639f, scale));
                sRect.set(p(cx, 8.9484f, scale), p(cy, 11.2242f, scale), p(cx, 12.9484f, scale), p(cy, 15.2242f, scale));
                sPath.arcTo(sRect, 45.0247f, 28.6971f);
                sPath.lineTo(p(cx, 8.636f, scale), p(cy, 15.984f, scale));
                sRect.set(p(cx, 7.996f, scale), p(cy, 15.004f, scale), p(cx, 8.996f, scale), p(cy, 16.004f, scale));
                sPath.arcTo(sRect, 73.7398f, 122.5204f);
                sPath.lineTo(p(cx, 8.856f, scale), p(cy, 12.491f, scale));
                sRect.set(p(cx, 8.775f, scale), p(cy, 11.0544f, scale), p(cx, 12.775f, scale), p(cy, 15.0544f, scale));
                sPath.arcTo(sRect, -163.6374f, 28.6867f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_CLOSE: { // Lucide: circle-x
                canvas.drawCircle(p(cx, 12f, scale), p(cy, 12f, scale), 10f * scale, paint);
                // <path d="m15 9-6 6" />
                sPath.reset();
                sPath.moveTo(p(cx, 15f, scale), p(cy, 9f, scale));
                sPath.lineTo(p(cx, 9f, scale), p(cy, 15f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m9 9 6 6" />
                sPath.reset();
                sPath.moveTo(p(cx, 9f, scale), p(cy, 9f, scale));
                sPath.lineTo(p(cx, 15f, scale), p(cy, 15f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_SUPER: { // Lucide: command
                // <path d="M15 6v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3" />
                sPath.reset();
                sPath.moveTo(p(cx, 15f, scale), p(cy, 6f, scale));
                sPath.lineTo(p(cx, 15f, scale), p(cy, 18f, scale));
                sRect.set(p(cx, 15f, scale), p(cy, 15f, scale), p(cx, 21f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 180f, -270f);
                sPath.lineTo(p(cx, 6f, scale), p(cy, 15f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 15f, scale), p(cx, 9f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, -90f, -270f);
                sPath.lineTo(p(cx, 9f, scale), p(cy, 6f, scale));
                sRect.set(p(cx, 3f, scale), p(cy, 3f, scale), p(cx, 9f, scale), p(cy, 9f, scale));
                sPath.arcTo(sRect, 0f, -270f);
                sPath.lineTo(p(cx, 18f, scale), p(cy, 9f, scale));
                sRect.set(p(cx, 15f, scale), p(cy, 3f, scale), p(cx, 21f, scale), p(cy, 9f, scale));
                sPath.arcTo(sRect, 90f, -270f);
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_ALT: { // Lucide: option
                // <path d="M14 3h7" />
                sPath.reset();
                sPath.moveTo(p(cx, 14f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 3f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M3 3h5.28a1 1 0 0 1 .948.684l5.544 16.632a1 1 0 0 0 .949.684H21" />
                sPath.reset();
                sPath.moveTo(p(cx, 3f, scale), p(cy, 3f, scale));
                sPath.lineTo(p(cx, 8.28f, scale), p(cy, 3f, scale));
                sRect.set(p(cx, 7.2792f, scale), p(cy, 3f, scale), p(cx, 9.2792f, scale), p(cy, 5f, scale));
                sPath.arcTo(sRect, -89.9565f, 71.5353f);
                sPath.lineTo(p(cx, 14.772f, scale), p(cy, 20.316f, scale));
                sRect.set(p(cx, 14.7208f, scale), p(cy, 19f, scale), p(cx, 16.7208f, scale), p(cy, 21f, scale));
                sPath.arcTo(sRect, 161.5788f, -71.5926f);
                sPath.lineTo(p(cx, 21f, scale), p(cy, 21f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_TAB: { // Lucide: arrow-right-to-line
                // <path d="M17 12H3" />
                sPath.reset();
                sPath.moveTo(p(cx, 17f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 3f, scale), p(cy, 12f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="m11 18 6-6-6-6" />
                sPath.reset();
                sPath.moveTo(p(cx, 11f, scale), p(cy, 18f, scale));
                sPath.lineTo(p(cx, 17f, scale), p(cy, 12f, scale));
                sPath.lineTo(p(cx, 11f, scale), p(cy, 6f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M21 5v14" />
                sPath.reset();
                sPath.moveTo(p(cx, 21f, scale), p(cy, 5f, scale));
                sPath.lineTo(p(cx, 21f, scale), p(cy, 19f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_DESKTOP: { // Lucide: monitor
                drawLucideRect(canvas, cx, cy, 2f, 3f, 20f, 14f, 2f, scale, paint);
                canvas.drawLine(p(cx, 8f, scale), p(cy, 21f, scale), p(cx, 16f, scale), p(cy, 21f, scale), paint);
                canvas.drawLine(p(cx, 12f, scale), p(cy, 17f, scale), p(cx, 12f, scale), p(cy, 21f, scale), paint);
                break;
            }

            case ICON_POINTER: { // Lucide: mouse-pointer-2
                // <path d="M4.037 4.688a.495.495 0 0 1 .651-.651l16 6.5a.5.5 0 0 1-.063.947l-6.124 1.58a2 2 0 0 0-1.438 1.435l-1.579 6.126a.5.5 0 0 1-.947.063z" />
                sPath.reset();
                sPath.moveTo(p(cx, 4.037f, scale), p(cy, 4.688f, scale));
                sRect.set(p(cx, 3.9962f, scale), p(cy, 3.9962f, scale), p(cx, 4.9862f, scale), p(cy, 4.9862f, scale));
                sPath.arcTo(sRect, 156.5726f, 136.8548f);
                sPath.lineTo(p(cx, 20.688f, scale), p(cy, 10.537f, scale));
                sRect.set(p(cx, 19.9993f, scale), p(cy, 10.5f, scale), p(cx, 20.9993f, scale), p(cy, 11.5f, scale));
                sPath.arcTo(sRect, -67.8334f, 143.2789f);
                sPath.lineTo(p(cx, 14.501f, scale), p(cy, 13.064f, scale));
                sRect.set(p(cx, 12.999f, scale), p(cy, 13.001f, scale), p(cx, 16.999f, scale), p(cy, 17.001f, scale));
                sPath.arcTo(sRect, -104.4175f, -61.0454f);
                sPath.lineTo(p(cx, 11.484f, scale), p(cy, 20.625f, scale));
                sRect.set(p(cx, 10.5f, scale), p(cy, 19.9993f, scale), p(cx, 11.5f, scale), p(cy, 20.9993f, scale));
                sPath.arcTo(sRect, 14.5545f, 143.2789f);
                sPath.close();
                canvas.drawPath(sPath, paint);
                break;
            }

            case ICON_HAND: { // Lucide: hand
                // <path d="M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2" />
                sPath.reset();
                sPath.moveTo(p(cx, 18f, scale), p(cy, 11f, scale));
                sPath.lineTo(p(cx, 18f, scale), p(cy, 6f, scale));
                sRect.set(p(cx, 14f, scale), p(cy, 4f, scale), p(cx, 18f, scale), p(cy, 8f, scale));
                sPath.arcTo(sRect, 0f, -90f);
                sRect.set(p(cx, 14f, scale), p(cy, 4f, scale), p(cx, 18f, scale), p(cy, 8f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                canvas.drawPath(sPath, paint);
                // <path d="M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2" />
                sPath.reset();
                sPath.moveTo(p(cx, 14f, scale), p(cy, 10f, scale));
                sPath.lineTo(p(cx, 14f, scale), p(cy, 4f, scale));
                sRect.set(p(cx, 10f, scale), p(cy, 2f, scale), p(cx, 14f, scale), p(cy, 6f, scale));
                sPath.arcTo(sRect, 0f, -90f);
                sRect.set(p(cx, 10f, scale), p(cy, 2f, scale), p(cx, 14f, scale), p(cy, 6f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                sPath.lineTo(p(cx, 10f, scale), p(cy, 6f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8" />
                sPath.reset();
                sPath.moveTo(p(cx, 10f, scale), p(cy, 10.5f, scale));
                sPath.lineTo(p(cx, 10f, scale), p(cy, 6f, scale));
                sRect.set(p(cx, 6f, scale), p(cy, 4f, scale), p(cx, 10f, scale), p(cy, 8f, scale));
                sPath.arcTo(sRect, 0f, -90f);
                sRect.set(p(cx, 6f, scale), p(cy, 4f, scale), p(cx, 10f, scale), p(cy, 8f, scale));
                sPath.arcTo(sRect, -90f, -90f);
                sPath.lineTo(p(cx, 6f, scale), p(cy, 14f, scale));
                canvas.drawPath(sPath, paint);
                // <path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15" />
                sPath.reset();
                sPath.moveTo(p(cx, 18f, scale), p(cy, 8f, scale));
                sRect.set(p(cx, 18f, scale), p(cy, 6f, scale), p(cx, 22f, scale), p(cy, 10f, scale));
                sPath.arcTo(sRect, 180f, 180f);
                sPath.lineTo(p(cx, 22f, scale), p(cy, 14f, scale));
                sRect.set(p(cx, 6f, scale), p(cy, 6f, scale), p(cx, 22f, scale), p(cy, 22f, scale));
                sPath.arcTo(sRect, 0f, 90f);
                sPath.lineTo(p(cx, 12f, scale), p(cy, 22f, scale));
                sPath.cubicTo(p(cx, 9.2f, scale), p(cy, 22f, scale), p(cx, 7.5f, scale), p(cy, 21.14f, scale), p(cx, 6.01f, scale), p(cy, 19.66f, scale));
                sPath.lineTo(p(cx, 2.41f, scale), p(cy, 16.06f, scale));
                sRect.set(p(cx, 1.8944f, scale), p(cy, 12.7197f, scale), p(cx, 5.8944f, scale), p(cy, 16.7197f, scale));
                sPath.arcTo(sRect, 137.9204f, 174.362f);
                sPath.lineTo(p(cx, 7f, scale), p(cy, 15f, scale));
                canvas.drawPath(sPath, paint);
                break;
            }
        }

        paint.setColor(originalColor);
        paint.setStyle(origStyle);
        paint.setStrokeWidth(origStroke);
    }

    private static float p(float center, float svgCoord, float scale) {
        return center + (svgCoord - 12f) * scale;
    }

    private static void drawLucideRect(Canvas canvas, float cx, float cy, float x, float y, float w, float h, float r, float scale, Paint paint) {
        float l = p(cx, x, scale);
        float t = p(cy, y, scale);
        float right = l + w * scale;
        float bottom = t + h * scale;
        sRect.set(l, t, right, bottom);
        canvas.drawRoundRect(sRect, r * scale, r * scale, paint);
    }
}
