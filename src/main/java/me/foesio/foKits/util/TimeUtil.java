package me.foesio.foKits.util;

import me.foesio.core.number.DurationParser;
import me.foesio.core.number.TickDuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static long parseDurationMillis(String input) {
        if (input == null || input.isBlank()) {
            return 0L;
        }

        try {
            return DurationParser.parse(input)
                    .map(TimeUtil::toMillis)
                    .orElse(0L);
        } catch (ArithmeticException exception) {
            return 0L;
        }
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }

        try {
            return TickDuration.ofSeconds(millis / 1000L).compact();
        } catch (ArithmeticException exception) {
            return Long.toString(millis / 1000L) + "s";
        }
    }

    /** Owner-controlled wording for duration placeholders shown to players. */
    public static String formatDuration(long millis, ConfigurationSection messages) {
        ConfigurationSection presentation = messages == null ? null
                : messages.getConfigurationSection("duration");
        if (presentation == null) {
            return "";
        }
        long seconds = Math.max(0L, millis / 1000L);
        if (seconds == 0L) {
            return presentation.getString("zero", "");
        }
        long days = seconds / 86400L;
        long hours = seconds / 3600L % 24L;
        long minutes = seconds / 60L % 60L;
        long remaining = seconds % 60L;
        List<String> parts = new ArrayList<>();
        appendPart(parts, presentation, "day", days);
        appendPart(parts, presentation, "hour", hours);
        appendPart(parts, presentation, "minute", minutes);
        appendPart(parts, presentation, "second", remaining);
        String separator = presentation.getString("separator", "");
        return String.join(separator, parts);
    }

    private static void appendPart(List<String> parts, ConfigurationSection presentation,
                                   String unit, long count) {
        if (count == 0L) {
            return;
        }
        String form = count == 1L ? "one" : "many";
        String template = presentation.getString(unit + "." + form);
        if (template != null && !template.isEmpty()) {
            parts.add(template.replace("{count}", Long.toString(count)));
        }
    }

    private static long toMillis(TickDuration duration) {
        return Math.multiplyExact(duration.secondsFloor(), 1000L);
    }
}
