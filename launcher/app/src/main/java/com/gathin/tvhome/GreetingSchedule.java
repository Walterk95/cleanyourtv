package com.gathin.tvhome;

import java.util.List;

/**
 * The greeting to show at a given day and time, from a theme's "greeting_schedule": each rule has a text, the
 * days it applies to and a time window, written like the wallpaper schedule (three-letter English day names,
 * "HH:MM", equal times for the whole day, a window past midnight belongs to the day it started). The first
 * rule that matches wins; when none does the launcher keeps its time-of-day greeting. Pure maths and text, no
 * Android types, so it is unit-tested on the JVM.
 */
final class GreetingSchedule {
    private GreetingSchedule() { }

    static final int MAX_TEXT = 60;

    static final class Rule {
        final String text;
        final int days;
        final int fromMinute;
        final int toMinute;

        Rule(String text, int days, int fromMinute, int toMinute) {
            this.text = text;
            this.days = days;
            this.fromMinute = fromMinute;
            this.toMinute = toMinute;
        }
    }

    /**
     * Builds a rule from the values read out of the JSON; null when anything is missing or malformed, so one bad
     * entry never switches off the others. The text is cleaned of control characters and runs of spaces, and cut
     * to {@value #MAX_TEXT} characters.
     */
    static Rule rule(String text, List<String> days, String from, String to) {
        String clean = clean(text);
        if (clean.isEmpty()) return null;
        int mask = WallpaperSchedule.dayMask(days);
        if (mask < 0) return null;
        int f = WallpaperSchedule.minutes(from);
        int t = WallpaperSchedule.minutes(to);
        if (f < 0 || t < 0) return null;
        return new Rule(clean, mask, f, t);
    }

    /** The text to show, or null to keep the normal greeting. */
    static String pick(List<Rule> rules, int isoDay, int minute) {
        if (rules == null) return null;
        for (Rule r : rules) {
            if (WallpaperSchedule.matches(r.days, r.fromMinute, r.toMinute, isoDay, minute)) return r.text;
        }
        return null;
    }

    static String clean(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                space = sb.length() > 0;
                continue;
            }
            if (space) sb.append(' ');
            space = false;
            sb.append(c);
            if (sb.length() >= MAX_TEXT) break;
        }
        return sb.toString().trim();
    }
}
