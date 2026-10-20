package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WeatherForecastTest {
    /** The shape of an Open-Meteo answer for current + daily, as documented at open-meteo.com/en/docs. */
    private static final String ANSWER = "{\"latitude\":49.05,\"longitude\":2.1,\"timezone\":\"Europe/Paris\","
            + "\"current_units\":{\"temperature_2m\":\"C\"},"
            + "\"current\":{\"time\":\"2026-10-20T18:15\",\"interval\":900,\"temperature_2m\":12.4,\"weather_code\":3},"
            + "\"daily_units\":{\"temperature_2m_max\":\"C\"},"
            + "\"daily\":{\"time\":[\"2026-10-20\",\"2026-10-21\",\"2026-10-22\",\"2026-10-23\",\"2026-10-24\"],"
            + "\"weather_code\":[3,61,80,0,95],"
            + "\"temperature_2m_max\":[14.2,12.5,11.9,15.0,13.4],"
            + "\"temperature_2m_min\":[8.1,7.5,-0.4,6.0,-1.5]}}";

    @Test public void readsTheDaysAndTheTemperatureNow() throws JSONException {
        WeatherForecast f = WeatherForecast.parse(ANSWER, 5);
        assertEquals(5, f.days.size());
        assertTrue(f.hasNow);
        assertEquals(12, f.nowTemp);
        assertEquals(3, f.nowCode);
        assertEquals(LocalDate.of(2026, 10, 20), f.days.get(0).date);
        assertEquals(61, f.days.get(1).code);
    }

    @Test public void temperaturesAreRoundedToWholeDegrees() throws JSONException {
        WeatherForecast f = WeatherForecast.parse(ANSWER, 5);
        assertEquals(14, f.days.get(0).max);
        assertEquals(8, f.days.get(0).min);
        assertEquals(13, f.days.get(1).max);     // 12.5 rounds up
        assertEquals(0, f.days.get(2).min);      // -0.4 is 0, never "-0"
        assertEquals(-1, f.days.get(4).min);     // -1.5 rounds towards +infinity
    }

    @Test public void onlyTheRequestedNumberOfDaysIsKept() throws JSONException {
        assertEquals(3, WeatherForecast.parse(ANSWER, 3).days.size());
    }

    @Test public void aDayWithMissingValuesIsSkippedNotGuessed() throws JSONException {
        String text = ANSWER.replace("[14.2,12.5,11.9,15.0,13.4]", "[14.2,null,11.9,15.0,13.4]");
        WeatherForecast f = WeatherForecast.parse(text, 5);
        assertEquals(4, f.days.size());
        assertEquals(LocalDate.of(2026, 10, 22), f.days.get(1).date);
    }

    @Test public void noCurrentBlockMeansNoTemperatureNow() throws JSONException {
        String text = ANSWER.replaceAll("\"current\":\\{[^}]*\\},", "");
        WeatherForecast f = WeatherForecast.parse(text, 5);
        assertFalse(f.hasNow);
        assertEquals(5, f.days.size());
    }

    @Test public void arraysOfDifferentLengthsAreRefused() {
        String text = ANSWER.replace("[3,61,80,0,95]", "[3,61,80,0]");
        assertRefused(text);
    }

    @Test public void implausibleTemperaturesAreRefused() {
        assertRefused(ANSWER.replace("[14.2,12.5,11.9,15.0,13.4]", "[14.2,12.5,11.9,150.0,13.4]"));
        assertRefused(ANSWER.replace("[8.1,7.5,-0.4,6.0,-1.5]", "[8.1,7.5,-0.4,6.0,-99.0]"));
    }

    @Test public void notAForecastIsRefused() {
        assertRefused("{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90\"}");
        assertRefused("not json");
        assertRefused("{\"daily\":{\"time\":[],\"weather_code\":[],\"temperature_2m_max\":[],\"temperature_2m_min\":[]}}");
    }

    @Test public void aBadDateIsRefused() {
        assertRefused(ANSWER.replace("2026-10-21", "soon"));
        assertRefused(ANSWER.replace("2026-10-21", "2026-13-45"));
    }

    @Test public void daysBeforeTodayAreDropped() throws JSONException {
        WeatherForecast f = WeatherForecast.parse(ANSWER, 5);
        WeatherForecast later = f.from(LocalDate.of(2026, 10, 22));
        assertNotNull(later);
        assertEquals(3, later.days.size());
        assertEquals(LocalDate.of(2026, 10, 22), later.days.get(0).date);
        assertNull(f.from(LocalDate.of(2026, 10, 25)));
        assertEquals(5, f.from(LocalDate.of(2026, 10, 20)).days.size());
    }

    @Test public void weatherCodesMapToIcons() {
        assertEquals(WeatherForecast.Kind.CLEAR, WeatherForecast.kind(0));
        assertEquals(WeatherForecast.Kind.CLEAR, WeatherForecast.kind(1));
        assertEquals(WeatherForecast.Kind.PARTLY, WeatherForecast.kind(2));
        assertEquals(WeatherForecast.Kind.CLOUDY, WeatherForecast.kind(3));
        assertEquals(WeatherForecast.Kind.FOG, WeatherForecast.kind(45));
        assertEquals(WeatherForecast.Kind.FOG, WeatherForecast.kind(48));
        for (int code : new int[]{51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82}) {
            assertEquals("code " + code, WeatherForecast.Kind.RAIN, WeatherForecast.kind(code));
        }
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) {
            assertEquals("code " + code, WeatherForecast.Kind.SNOW, WeatherForecast.kind(code));
        }
        for (int code : new int[]{95, 96, 97, 99}) {
            assertEquals("code " + code, WeatherForecast.Kind.THUNDER, WeatherForecast.kind(code));
        }
    }

    @Test public void anUnknownCodeReadsAsCloudy() {
        assertEquals(WeatherForecast.Kind.CLOUDY, WeatherForecast.kind(42));
        assertEquals(WeatherForecast.Kind.CLOUDY, WeatherForecast.kind(-1));
    }

    private static void assertRefused(String text) {
        try {
            WeatherForecast.parse(text, 5);
            fail("should have been refused: " + text);
        } catch (JSONException expected) {
            // refused, as it should be
        }
    }
}
