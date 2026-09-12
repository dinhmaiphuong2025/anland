package com.anland.consumer.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Anland Material 3 Purist Design System (M3 Purist / Catppuccin & Linear Hybrid).
 *
 * Strict OCD-friendly specifications:
 *   - No bloated 28dp oval buttons or cartoonish shapes.
 *   - Structured corner radius scale (4dp popup, 6dp input, 8dp chip, 10dp button, 12dp card).
 *   - Subtle 1px hairline borders on all interactive surfaces (0x22FFFFFF).
 *   - Soothing, non-harsh Catppuccin Mocha color roles (Base, Mantle, Crust, Lavender, Cyan).
 *   - Genuine 3.5dp slim SeekBar tracks with 13dp circular thumbs.
 *   - Guaranteed non-overflowing text with 2-line safe wrapping and generous padding.
 */
public final class M3 {

    private M3() {}

    // ============================================================
    // COLOR PALETTE (Catppuccin Mocha / Material 3 Dark)
    // ============================================================

    public static final int COLOR_BASE             = 0xFF11111B; // Main background (OLED dark)
    public static final int COLOR_SURFACE_LOW      = 0xFF181825; // Mantle: Cards, Top bars
    public static final int COLOR_SURFACE          = 0xFF1E1E2E; // Crust: Dialogs, Popups
    public static final int COLOR_SURFACE_HIGH     = 0xFF252638; // Panel interiors, active layers
    public static final int COLOR_SURFACE_HIGHEST  = 0xFF2A2B3D; // Component buttons, text inputs

    public static final int COLOR_PRIMARY          = 0xFF80DEEA; // Cyan accent (Focus / Key actions)
    public static final int COLOR_PRIMARY_CONTAINER= 0xFF004F58; // Muted active container
    public static final int COLOR_SECONDARY        = 0xFFB4BEFE; // Lavender (Sliders / Secondary value)
    public static final int COLOR_ERROR            = 0xFFF38BA8; // Coral Red (Destructive actions)
    public static final int COLOR_SUCCESS          = 0xFFA6E3A1; // Mint Green (Online status)

    public static final int COLOR_TEXT_PRIMARY     = 0xFFFFFFFF; // High-contrast white
    public static final int COLOR_TEXT_SECONDARY   = 0xFFCDD6F4; // Sub-heading body text
    public static final int COLOR_TEXT_MUTED       = 0xFFA6ADC8; // Labels, captions, hints

    public static final int COLOR_BORDER_SUBTLE    = 0x22FFFFFF; // 1px hairline border
    public static final int COLOR_BORDER_STRONG    = 0x33FFFFFF; // Header / Dialog borders
    public static final int COLOR_BORDER_FOCUS     = 0x8880DEEA; // Focused outline

    // ============================================================
    // CORNER RADIUS TOKENS (Strict Geometry)
    // ============================================================

    public static final int RADIUS_POPUP   = 4;  // Sharp, technical dropdown popups
    public static final int RADIUS_INPUT   = 6;  // Text & numeric edit fields
    public static final int RADIUS_CHIP    = 8;  // Tags, action badges, combo slots
    public static final int RADIUS_BUTTON  = 10; // Interactive buttons (soft, not oval)
    public static final int RADIUS_CARD    = 12; // Settings cards, dialog surfaces
    public static final int RADIUS_PANEL   = 14; // Floating property inspector panel
    public static final int RADIUS_PILL    = 24; // Floating top bar, drag handles

    // ============================================================
    // SPACING & SIZING HELPERS
    // ============================================================

    public static int dp(Context ctx, float dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }

    public static int dp(View v, float dpVal) {
        return Math.round(dpVal * v.getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // DRAWABLE FACTORIES
    // ============================================================

    public static GradientDrawable shape(Context ctx, int radiusDp, int fillColor, int strokeColor, float strokeWidthDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(ctx, radiusDp));
        gd.setColor(fillColor);
        if (strokeWidthDp > 0 && strokeColor != 0) {
            gd.setStroke(Math.max(1, dp(ctx, strokeWidthDp)), strokeColor);
        }
        return gd;
    }

    public static Drawable createRippleDrawable(Context ctx, int radiusDp, int normalColor, int pressedColor, int strokeColor) {
        GradientDrawable content = shape(ctx, radiusDp, normalColor, strokeColor, 1.0f);
        GradientDrawable mask = shape(ctx, radiusDp, Color.WHITE, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(pressedColor), content, mask);
    }

    // ============================================================
    // COMPONENT STYLING HELPERS
    // ============================================================

    /** Style an interactive button with M3 Purist 10dp radius, 1px border, safe padding. */
    public static void styleButton(Button btn, int normalBg, int activeBg, int textColor) {
        Context ctx = btn.getContext();
        btn.setTextColor(textColor);
        btn.setTextSize(12.5f);
        btn.setGravity(Gravity.CENTER);
        btn.setMaxLines(2);
        btn.setSingleLine(false);
        btn.setMinHeight(dp(ctx, 42));
        btn.setMinWidth(0);
        btn.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
        btn.setBackground(createRippleDrawable(ctx, RADIUS_BUTTON, normalBg, activeBg, COLOR_BORDER_SUBTLE));
    }

    /** Style standard M3 Tonal / Neutral button (used in Settings and HUD). */
    public static void styleButton(Button btn) {
        styleButton(btn, COLOR_SURFACE_HIGHEST, 0x44FFFFFF, COLOR_TEXT_PRIMARY);
    }

    /** Style an input text field with M3 Purist 6dp radius, 1px subtle border. */
    public static void styleInput(EditText et) {
        Context ctx = et.getContext();
        et.setTextColor(COLOR_TEXT_PRIMARY);
        et.setTextSize(13);
        et.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        et.setBackground(shape(ctx, RADIUS_INPUT, COLOR_SURFACE_HIGHEST, COLOR_BORDER_SUBTLE, 1.0f));
    }

    /**
     * Style a SeekBar with a genuine 3.5dp slim capsule track and 13dp circular thumb.
     * Enforces setLayerHeight and setLayerGravity to override Android default thickness stretch.
     */
    public static void styleSeekBar(SeekBar bar, int activeColor) {
        float density = bar.getContext().getResources().getDisplayMetrics().density;
        int trackHeight = Math.max(1, Math.round(3.5f * density));

        GradientDrawable bgTrack = new GradientDrawable();
        bgTrack.setCornerRadius(trackHeight * 0.5f);
        bgTrack.setColor(0x33FFFFFF);

        GradientDrawable progressTrack = new GradientDrawable();
        progressTrack.setCornerRadius(trackHeight * 0.5f);
        progressTrack.setColor(activeColor);
        ClipDrawable clipProgress = new ClipDrawable(
                progressTrack, Gravity.START, ClipDrawable.HORIZONTAL);

        Drawable[] layers = new Drawable[] { bgTrack, clipProgress };
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        layerDrawable.setId(0, android.R.id.background);
        layerDrawable.setId(1, android.R.id.progress);

        layerDrawable.setLayerHeight(0, trackHeight);
        layerDrawable.setLayerGravity(0, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        layerDrawable.setLayerHeight(1, trackHeight);
        layerDrawable.setLayerGravity(1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);

        bar.setProgressDrawable(layerDrawable);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        int thumbSize = Math.round(13 * density);
        thumb.setSize(thumbSize, thumbSize);
        thumb.setColor(activeColor);
        bar.setThumb(thumb);
        bar.setSplitTrack(false);
    }

    /**
     * Style a Spinner with a sharp 4dp dark popup background and comfortable item padding.
     * Completely eliminates bloated oval popups.
     */
    public static void styleDropdown(Spinner spinner, String[] items) {
        Context ctx = spinner.getContext();
        spinner.setPopupBackgroundDrawable(shape(ctx, RADIUS_POPUP, COLOR_SURFACE, COLOR_BORDER_STRONG, 1.0f));

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(COLOR_TEXT_PRIMARY);
                    tv.setTextSize(13);
                    tv.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
                    tv.setBackgroundColor(COLOR_SURFACE);
                }
                return v;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(COLOR_TEXT_PRIMARY);
                    ((TextView) v).setTextSize(14);
                }
                return v;
            }
        };
        spinner.setAdapter(adapter);
    }

    /** Style a Switch with M3 Purist Cyan and subtle track tints across all devices. */
    public static void styleSwitch(Switch sw) {
        sw.setTextColor(COLOR_TEXT_PRIMARY);
        sw.setTextSize(14);
        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] { -android.R.attr.state_checked }
        };
        int[] trackColors = new int[] {
            0x6680DEEA,
            0x33FFFFFF
        };
        int[] thumbColors = new int[] {
            COLOR_PRIMARY,
            0xFF888888
        };
        sw.setTrackTintList(new ColorStateList(states, trackColors));
        sw.setThumbTintList(new ColorStateList(states, thumbColors));
    }

    // ============================================================
    // VECTOR DRAWN CHEVRON & BACK ARROW VIEWS (Anti-aliased Canvas)
    // ============================================================

    public static final class DragGripView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public DragGripView(Context ctx) {
            super(ctx);
            mPaint.setColor(COLOR_TEXT_MUTED);
            mPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float density = getResources().getDisplayMetrics().density;
            setMeasuredDimension(Math.round(24 * density), Math.round(20 * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float r = 1.6f * density;
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float dx = 3.5f * density;
            float dy = 4.0f * density;

            // 6 dots in 2 columns of 3
            canvas.drawCircle(cx - dx, cy - dy, r, mPaint);
            canvas.drawCircle(cx + dx, cy - dy, r, mPaint);
            canvas.drawCircle(cx - dx, cy, r, mPaint);
            canvas.drawCircle(cx + dx, cy, r, mPaint);
            canvas.drawCircle(cx - dx, cy + dy, r, mPaint);
            canvas.drawCircle(cx + dx, cy + dy, r, mPaint);
        }
    }

    public static final class ChevronView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ChevronView(Context ctx) {
            super(ctx);
            mPaint.setColor(COLOR_TEXT_MUTED);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float density = getResources().getDisplayMetrics().density;
            setMeasuredDimension(Math.round(10 * density), Math.round(16 * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            mPaint.setStrokeWidth(2.0f * density);
            float w = getWidth();
            float h = getHeight();
            float left = 2f * density;
            float right = w - 2.5f * density;
            float top = 3f * density;
            float midY = h * 0.5f;
            float bot = h - 3f * density;

            canvas.drawLine(left, top, right, midY, mPaint);
            canvas.drawLine(right, midY, left, bot, mPaint);
        }
    }

    public static final class BackArrowView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public BackArrowView(Context ctx) {
            super(ctx);
            mPaint.setColor(COLOR_PRIMARY);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float density = getResources().getDisplayMetrics().density;
            setMeasuredDimension(Math.round(16 * density), Math.round(16 * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            mPaint.setStrokeWidth(2.2f * density);
            float w = getWidth();
            float h = getHeight();
            float midY = h * 0.5f;
            float startX = 3f * density;
            float endX = w - 2f * density;

            canvas.drawLine(startX, midY, endX, midY, mPaint);
            float arm = 5f * density;
            canvas.drawLine(startX, midY, startX + arm, midY - arm, mPaint);
            canvas.drawLine(startX, midY, startX + arm, midY + arm, mPaint);
        }
    }
}
