package com.gathin.tvhome;

/**
 * Where the weather strip looks and how many days it shows (theme keys weather_latitude, weather_longitude,
 * weather_city and weather_days). Without a valid latitude and longitude the strip is off and the launcher makes
 * no network request at all.
 */
final class WeatherConfig {
    static final int DEFAULT_DAYS = 5;
    static final int MAX_DAYS = 7;

    final double latitude;
    final double longitude;
    /** Name shown above the temperature; empty for none. */
    final String city;
    final int days;

    private WeatherConfig(double latitude, double longitude, String city, int days) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = city;
        this.days = days;
    }

    /** Null when the coordinates are missing or out of range; days outside 1 to 7 fall back to 5. */
    static WeatherConfig of(Double latitude, Double longitude, String city, Integer days) {
        if (latitude == null || longitude == null) return null;
        if (latitude.isNaN() || longitude.isNaN() || latitude.isInfinite() || longitude.isInfinite()) return null;
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
        int n = days != null && days >= 1 && days <= MAX_DAYS ? days : DEFAULT_DAYS;
        String name = city == null ? "" : city.trim();
        if (name.length() > 40) name = name.substring(0, 40);
        return new WeatherConfig(latitude, longitude, name, n);
    }

    /** The request: current temperature plus a few days, in the place's own time zone, Celsius. */
    String url() {
        return String.format(java.util.Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=%d", latitude, longitude, days);
    }

    /** File name for the cached answer; includes the place so a theme moved elsewhere never shows old data. */
    String cacheName() {
        return String.format(java.util.Locale.US, "weather_%.2f_%.2f_%d.json", latitude, longitude, days);
    }
}
