package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class GreetingScheduleTest {
    private static final int MON = 1, TUE = 2, WED = 3, THU = 4, FRI = 5, SAT = 6, SUN = 7;

    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    /** Three made-up names stand in for the family's; the real ones live in a file that stays off GitHub. */
    private static List<GreetingSchedule.Rule> weekly() {
        List<GreetingSchedule.Rule> rules = new ArrayList<>();
        rules.add(GreetingSchedule.rule("Bonjour Alice", Arrays.asList("sat"), "06:00", "12:00"));
        rules.add(GreetingSchedule.rule("Bonjour Bob et bon appetit", Arrays.asList("mon", "tue", "thu", "fri"), "12:00", "14:00"));
        rules.add(GreetingSchedule.rule("Bonjour Carl", Arrays.asList("mon", "tue", "wed", "thu", "fri", "sat", "sun"), "00:00", "00:00"));
        return rules;
    }

    @Test public void saturdayMorningIsTheFirstRule() {
        assertEquals("Bonjour Alice", GreetingSchedule.pick(weekly(), SAT, at(9, 0)));
    }

    @Test public void lunchOnAWorkingDayIsTheSecondRule() {
        assertEquals("Bonjour Bob et bon appetit", GreetingSchedule.pick(weekly(), MON, at(12, 0)));
        assertEquals("Bonjour Bob et bon appetit", GreetingSchedule.pick(weekly(), FRI, at(13, 59)));
    }

    @Test public void theWindowEndIsExclusive() {
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), THU, at(14, 0)));
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), SAT, at(12, 0)));
    }

    @Test public void wednesdayLunchIsNotInTheLunchRule() {
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), WED, at(13, 0)));
    }

    @Test public void theCatchAllCoversTheRestOfTheWeek() {
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), SUN, at(9, 0)));
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), MON, at(22, 30)));
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), SAT, at(3, 0)));
    }

    @Test public void saturdayBeforeSixIsNotTheMorningRule() {
        assertEquals("Bonjour Carl", GreetingSchedule.pick(weekly(), SAT, at(5, 59)));
    }

    @Test public void noRulesKeepsTheNormalGreeting() {
        assertNull(GreetingSchedule.pick(new ArrayList<>(), MON, at(9, 0)));
        assertNull(GreetingSchedule.pick(null, MON, at(9, 0)));
    }

    @Test public void aWindowOverMidnightBelongsToTheDayItStarted() {
        List<GreetingSchedule.Rule> rules = new ArrayList<>();
        rules.add(GreetingSchedule.rule("Bonne nuit", Arrays.asList("fri"), "22:00", "02:00"));
        assertEquals("Bonne nuit", GreetingSchedule.pick(rules, FRI, at(23, 0)));
        assertEquals("Bonne nuit", GreetingSchedule.pick(rules, SAT, at(1, 0)));
        assertNull(GreetingSchedule.pick(rules, FRI, at(1, 0)));
    }

    @Test public void malformedEntriesAreRejectedOnTheirOwn() {
        assertNull(GreetingSchedule.rule("", Arrays.asList("mon"), "09:00", "10:00"));
        assertNull(GreetingSchedule.rule(null, Arrays.asList("mon"), "09:00", "10:00"));
        assertNull(GreetingSchedule.rule("Salut", Arrays.asList("xyz"), "09:00", "10:00"));
        assertNull(GreetingSchedule.rule("Salut", Arrays.asList(), "09:00", "10:00"));
        assertNull(GreetingSchedule.rule("Salut", Arrays.asList("mon"), "25:00", "10:00"));
        assertNull(GreetingSchedule.rule("Salut", Arrays.asList("mon"), "09:00", null));
        assertNotNull(GreetingSchedule.rule("Salut", Arrays.asList("Monday"), "9:00", "10:00"));
    }

    @Test public void theTextIsCleaned() {
        assertEquals("Bonjour Alice et bon appetit", GreetingSchedule.clean("  Bonjour\tAlice \n et   bon appetit  "));
        assertEquals("", GreetingSchedule.clean("   "));
        assertEquals("a b", GreetingSchedule.clean("a\u0000b"));
    }

    @Test public void theTextIsCutToTheLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('x');
        assertEquals(GreetingSchedule.MAX_TEXT, GreetingSchedule.clean(sb.toString()).length());
    }
}
