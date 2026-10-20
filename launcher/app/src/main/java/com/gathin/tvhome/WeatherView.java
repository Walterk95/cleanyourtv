package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The weather strip: the temperature now, then a few days, each with a small icon drawn in code, the day's
 * high and its low. Static (drawn when the forecast changes, never animated), and no pictures are loaded, so it
 * costs a TV with 2 GB of RAM almost nothing. Weather data by Open-Meteo.com (CC BY 4.0); the credit is in the
 * README, not on screen, by the owner's choice.
 */
public class WeatherView extends View {
    private static final float LEFT_DP = 132f;
    private static final String UNIT = "\u00B0C";
    private static final float COLUMN_DP = 66f;
    private static final float HEIGHT_DP = 112f;

    private final float density;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Typeface light = Typeface.SANS_SERIF;
    private Typeface regular = Typeface.SANS_SERIF;
    private Typeface medium = Typeface.SANS_SERIF;

    private WeatherForecast forecast;
    private String city = "";
    private boolean showNow;
    private final List<String> labels = new ArrayList<>();
    private final List<String> highs = new ArrayList<>();
    private final List<String> lows = new ArrayList<>();
    private String cityText = "";
    private String nowText = "";

    public WeatherView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        try {
            light = context.getResources().getFont(R.font.outfit_light);
            regular = context.getResources().getFont(R.font.outfit_regular);
            medium = context.getResources().getFont(R.font.outfit_medium);
        } catch (Exception ignored) {
            // The system font is used instead.
        }
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    /** Shows the forecast; null hides the strip. {@code showNow} says whether the temperature now is recent enough to show. */
    void setForecast(WeatherForecast forecast, String city, boolean showNow) {
        this.forecast = forecast;
        this.city = city == null ? "" : city;
        this.showNow = showNow && forecast != null && forecast.hasNow;
        labels.clear();
        highs.clear();
        lows.clear();
        // Built here, not in onDraw, so drawing allocates nothing.
        cityText = this.city;
        nowText = forecast != null ? String.valueOf(forecast.nowTemp) : "";
        if (forecast != null) {
            for (WeatherForecast.Day d : forecast.days) {
                labels.add(d.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
                highs.add(d.max + UNIT);
                lows.add(d.min + UNIT);
            }
        }
        setVisibility(forecast == null ? GONE : VISIBLE);
        requestLayout();
        invalidate();
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int days = forecast == null ? 0 : forecast.days.size();
        setMeasuredDimension(Math.round(dp(LEFT_DP + COLUMN_DP * days)), Math.round(dp(HEIGHT_DP)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (forecast == null) return;
        // Left block: place, temperature now, credit.
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(medium);
        text.setTextSize(dp(11));
        text.setLetterSpacing(0.16f);
        text.setColor(0x99FFFFFF);
        if (!cityText.isEmpty()) canvas.drawText(cityText, dp(4), dp(15), text);
        text.setLetterSpacing(0f);
        if (showNow) {
            text.setTypeface(light);
            text.setTextSize(dp(42));
            text.setColor(0xFFFFFFFF);
            canvas.drawText(nowText, dp(2), dp(66), text);
            float numberEnd = dp(2) + text.measureText(nowText);
            text.setTextSize(dp(20));
            canvas.drawText(UNIT, numberEnd, dp(46), text);
            drawIcon(canvas, WeatherForecast.kind(forecast.nowCode), dp(LEFT_DP - 26), dp(48), dp(34));
        }

        // One column per day.
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < forecast.days.size(); i++) {
            WeatherForecast.Day d = forecast.days.get(i);
            float cx = dp(LEFT_DP + COLUMN_DP * i + COLUMN_DP / 2f);
            text.setTypeface(medium);
            text.setTextSize(dp(12));
            text.setLetterSpacing(0.12f);
            text.setColor(i == 0 ? 0xFFFFFFFF : 0xB3FFFFFF);
            canvas.drawText(labels.get(i), cx, dp(18), text);
            text.setLetterSpacing(0f);
            drawIcon(canvas, WeatherForecast.kind(d.code), cx, dp(50), dp(32));
            text.setTypeface(regular);
            text.setTextSize(dp(17));
            text.setColor(0xFFFFFFFF);
            canvas.drawText(highs.get(i), cx, dp(86), text);
            text.setTextSize(dp(14));
            text.setColor(0x80FFFFFF);
            canvas.drawText(lows.get(i), cx, dp(104), text);
        }
    }

    // ---- icons, drawn from circles, lines and one polygon so no picture has to be decoded ----------------

    private void drawIcon(Canvas c, WeatherForecast.Kind kind, float cx, float cy, float s) {
        switch (kind) {
            case CLEAR:
                sun(c, cx, cy, s);
                break;
            case PARTLY:
                sun(c, cx - s * 0.14f, cy - s * 0.14f, s * 0.8f);
                cloud(c, cx + s * 0.06f, cy + s * 0.1f, s * 0.9f, 0xFFE6EAF0);
                break;
            case CLOUDY:
                cloud(c, cx, cy, s, 0xFFCBD2DC);
                break;
            case FOG:
                fog(c, cx, cy, s);
                break;
            case RAIN:
                cloud(c, cx, cy - s * 0.12f, s, 0xFFCBD2DC);
                rain(c, cx, cy, s);
                break;
            case SNOW:
                cloud(c, cx, cy - s * 0.12f, s, 0xFFE6EAF0);
                snow(c, cx, cy, s);
                break;
            case THUNDER:
                cloud(c, cx, cy - s * 0.14f, s, 0xFF9EA7B5);
                bolt(c, cx, cy, s);
                break;
        }
    }

    private void sun(Canvas c, float cx, float cy, float s) {
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(0xFFFFC94D);
        c.drawCircle(cx, cy, s * 0.22f, shape);
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeCap(Paint.Cap.ROUND);
        shape.setStrokeWidth(Math.max(2f, s * 0.06f));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * s * 0.34f, cy + sin * s * 0.34f, cx + cos * s * 0.47f, cy + sin * s * 0.47f, shape);
        }
    }

    private void cloud(Canvas c, float cx, float cy, float s, int color) {
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(color);
        c.drawCircle(cx - s * 0.16f, cy + s * 0.04f, s * 0.16f, shape);
        c.drawCircle(cx + s * 0.02f, cy - s * 0.06f, s * 0.21f, shape);
        c.drawCircle(cx + s * 0.2f, cy + s * 0.05f, s * 0.15f, shape);
        c.drawRoundRect(cx - s * 0.32f, cy + s * 0.02f, cx + s * 0.35f, cy + s * 0.2f, s * 0.09f, s * 0.09f, shape);
    }

    private void rain(Canvas c, float cx, float cy, float s) {
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeCap(Paint.Cap.ROUND);
        shape.setStrokeWidth(Math.max(2f, s * 0.06f));
        shape.setColor(0xFF6FB7FF);
        for (int i = -1; i <= 1; i++) {
            float x = cx + i * s * 0.2f;
            c.drawLine(x + s * 0.03f, cy + s * 0.22f, x - s * 0.03f, cy + s * 0.4f, shape);
        }
    }

    private void snow(Canvas c, float cx, float cy, float s) {
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(0xFFFFFFFF);
        for (int i = -1; i <= 1; i++) {
            c.drawCircle(cx + i * s * 0.2f, cy + s * 0.33f + (i == 0 ? s * 0.04f : 0f), s * 0.045f, shape);
        }
    }

    private void bolt(Canvas c, float cx, float cy, float s) {
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(0xFFFFD84D);
        path.reset();
        path.moveTo(cx + s * 0.04f, cy + s * 0.12f);
        path.lineTo(cx - s * 0.1f, cy + s * 0.34f);
        path.lineTo(cx, cy + s * 0.34f);
        path.lineTo(cx - s * 0.06f, cy + s * 0.5f);
        path.lineTo(cx + s * 0.14f, cy + s * 0.27f);
        path.lineTo(cx + s * 0.03f, cy + s * 0.27f);
        path.close();
        c.drawPath(path, shape);
    }

    private void fog(Canvas c, float cx, float cy, float s) {
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeCap(Paint.Cap.ROUND);
        shape.setStrokeWidth(Math.max(2f, s * 0.07f));
        shape.setColor(0xFFCBD2DC);
        c.drawLine(cx - s * 0.32f, cy - s * 0.14f, cx + s * 0.32f, cy - s * 0.14f, shape);
        c.drawLine(cx - s * 0.22f, cy + s * 0.02f, cx + s * 0.38f, cy + s * 0.02f, shape);
        c.drawLine(cx - s * 0.36f, cy + s * 0.18f, cx + s * 0.26f, cy + s * 0.18f, shape);
    }
}
