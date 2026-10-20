package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the weather strip shows, read from an Open-Meteo answer (weather data by Open-Meteo.com, CC BY 4.0).
 * Only plausible numbers are accepted: anything else makes the whole answer invalid, so a broken response
 * never reaches the screen.
 */
final class WeatherForecast {
    enum Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

    static final class Day {
        final LocalDate date;
        final int code;
        final int max;
        final int min;

        Day(LocalDate date, int code, int max, int min) {
            this.date = date;
            this.code = code;
            this.max = max;
            this.min = min;
        }
    }

    /** Temperature now in whole degrees, valid only when {@link #hasNow}. */
    final int nowTemp;
    final int nowCode;
    final boolean hasNow;
    final List<Day> days;

    WeatherForecast(boolean hasNow, int nowTemp, int nowCode, List<Day> days) {
        this.hasNow = hasNow;
        this.nowTemp = nowTemp;
        this.nowCode = nowCode;
        this.days = Collections.unmodifiableList(days);
    }

    private static boolean plausible(double t) {
        return !Double.isNaN(t) && t >= -90 && t <= 60;
    }

    /** @throws JSONException when the answer is not a usable forecast */
    static WeatherForecast parse(String text, int maxDays) throws JSONException {
        JSONObject root = new JSONObject(text);
        JSONObject daily = root.getJSONObject("daily");
        JSONArray time = daily.getJSONArray("time");
        JSONArray code = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        int n = time.length();
        if (n < 1 || code.length() != n || max.length() != n || min.length() != n) {
            throw new JSONException("daily arrays differ in length");
        }
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < n && days.size() < maxDays; i++) {
            if (max.isNull(i) || min.isNull(i) || code.isNull(i)) continue;   // a missing day is skipped, not guessed
            double hi = max.getDouble(i);
            double lo = min.getDouble(i);
            if (!plausible(hi) || !plausible(lo)) throw new JSONException("implausible temperature");
            String date = time.getString(i);
            if (date.length() < 10) throw new JSONException("bad date");
            LocalDate d;
            try {
                d = LocalDate.parse(date.substring(0, 10));
            } catch (java.time.DateTimeException e) {
                throw new JSONException("bad date");
            }
            days.add(new Day(d, code.getInt(i), (int) Math.round(hi), (int) Math.round(lo)));
        }
        if (days.isEmpty()) throw new JSONException("no day");
        boolean hasNow = false;
        int nowTemp = 0;
        int nowCode = 0;
        JSONObject current = root.optJSONObject("current");
        if (current != null && !current.isNull("temperature_2m") && !current.isNull("weather_code")) {
            double t = current.getDouble("temperature_2m");
            if (plausible(t)) {
                hasNow = true;
                nowTemp = (int) Math.round(t);
                nowCode = current.getInt("weather_code");
            }
        }
        return new WeatherForecast(hasNow, nowTemp, nowCode, days);
    }

    /** The same forecast without the days before {@code today} (a cached answer ages overnight); null if none is left. */
    WeatherForecast from(LocalDate today) {
        List<Day> rest = new ArrayList<>();
        for (Day d : days) {
            if (!d.date.isBefore(today)) rest.add(d);
        }
        if (rest.isEmpty()) return null;
        return new WeatherForecast(hasNow, nowTemp, nowCode, rest);
    }

    /** WMO weather interpretation codes as listed by Open-Meteo; an unknown code reads as cloudy. */
    static Kind kind(int code) {
        switch (code) {
            case 0:
            case 1:
                return Kind.CLEAR;
            case 2:
                return Kind.PARTLY;
            case 3:
                return Kind.CLOUDY;
            case 45:
            case 48:
                return Kind.FOG;
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82:
                return Kind.RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86:
                return Kind.SNOW;
            case 95: case 96: case 97: case 99:
                return Kind.THUNDER;
            default:
                return Kind.CLOUDY;
        }
    }
}
