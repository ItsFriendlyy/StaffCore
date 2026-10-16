package com.github.staffcore.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhdwy]|mo)");

    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) return -1;

        Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());
        long totalMillis = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "s": totalMillis += TimeUnit.SECONDS.toMillis(value); break;
                case "m": totalMillis += TimeUnit.MINUTES.toMillis(value); break;
                case "h": totalMillis += TimeUnit.HOURS.toMillis(value); break;
                case "d": totalMillis += TimeUnit.DAYS.toMillis(value); break;
                case "w": totalMillis += TimeUnit.DAYS.toMillis(value * 7); break;
                case "mo": totalMillis += TimeUnit.DAYS.toMillis(value * 30); break;
                case "y": totalMillis += TimeUnit.DAYS.toMillis(value * 365); break;
            }
        }

        return found ? totalMillis : -1;
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "Permanent";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    public static String formatRemaining(long expireTime) {
        if (expireTime <= 0) return "Never";
        long diff = expireTime - System.currentTimeMillis();
        if (diff <= 0) return "Expired";
        return formatDuration(diff);
    }
}
