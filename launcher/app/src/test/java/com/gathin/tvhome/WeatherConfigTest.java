package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeatherConfigTest {
    @Test public void pontoiseIsAccepted() {
        WeatherConfig c = WeatherConfig.of(49.0508, 2.1008, "Pontoise", 5);
        assertNotNull(c);
        assertEquals("Pontoise", c.city);
        assertEquals(5, c.days);
    }

    @Test public void withoutCoordinatesTheStripIsOffAndNothingIsRequested() {
        assertNull(WeatherConfig.of(null, 2.1, "x", 5));
        assertNull(WeatherConfig.of(49.0, null, "x", 5));
        assertNull(WeatherConfig.of(null, null, "x", null));
    }

    @Test public void outOfRangeCoordinatesAreRefused() {
        assertNull(WeatherConfig.of(91.0, 2.1, "x", 5));
        assertNull(WeatherConfig.of(-91.0, 2.1, "x", 5));
        assertNull(WeatherConfig.of(49.0, 181.0, "x", 5));
        assertNull(WeatherConfig.of(49.0, -181.0, "x", 5));
        assertNull(WeatherConfig.of(Double.NaN, 2.1, "x", 5));
        assertNull(WeatherConfig.of(49.0, Double.POSITIVE_INFINITY, "x", 5));
    }

    @Test public void daysOutsideOneToSevenFallBackToFive() {
        assertEquals(5, WeatherConfig.of(49.0, 2.0, "", null).days);
        assertEquals(5, WeatherConfig.of(49.0, 2.0, "", 0).days);
        assertEquals(5, WeatherConfig.of(49.0, 2.0, "", 8).days);
        assertEquals(1, WeatherConfig.of(49.0, 2.0, "", 1).days);
        assertEquals(7, WeatherConfig.of(49.0, 2.0, "", 7).days);
    }

    @Test public void theCityNameIsTrimmedAndShortened() {
        assertEquals("Pontoise", WeatherConfig.of(49.0, 2.0, "  Pontoise ", 5).city);
        assertEquals("", WeatherConfig.of(49.0, 2.0, null, 5).city);
        assertEquals(40, WeatherConfig.of(49.0, 2.0, new String(new char[100]).replace('\0', 'a'), 5).city.length());
    }

    @Test public void theRequestAsksOnlyForWhatTheStripShows() {
        String url = WeatherConfig.of(49.0508, 2.1008, "Pontoise", 5).url();
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?"));
        assertTrue(url, url.contains("latitude=49.0508"));
        assertTrue(url, url.contains("longitude=2.1008"));
        assertTrue(url, url.contains("current=temperature_2m,weather_code"));
        assertTrue(url, url.contains("daily=weather_code,temperature_2m_max,temperature_2m_min"));
        assertTrue(url, url.contains("timezone=auto"));
        assertTrue(url, url.contains("forecast_days=5"));
    }

    @Test public void theRequestUsesDotsNotCommasWhateverTheLocale() {
        java.util.Locale old = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);
            String url = WeatherConfig.of(49.0508, 2.1008, "", 5).url();
            assertTrue(url, url.contains("latitude=49.0508"));
            assertTrue(url, url.contains("longitude=2.1008"));
        } finally {
            java.util.Locale.setDefault(old);
        }
    }

    @Test public void theCacheNameNamesThePlace() {
        String a = WeatherConfig.of(49.05, 2.10, "", 5).cacheName();
        String b = WeatherConfig.of(48.85, 2.35, "", 5).cacheName();
        assertTrue(!a.equals(b));
        assertTrue(a, a.matches("weather_49\\.05_2\\.10_5\\.json"));
    }
}
