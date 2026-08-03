package me.foesio.foKits.util;

import me.foesio.core.number.DurationParser;
import me.foesio.core.number.TickDuration;

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

    private static long toMillis(TickDuration duration) {
        return Math.multiplyExact(duration.secondsFloor(), 1000L);
    }
}
