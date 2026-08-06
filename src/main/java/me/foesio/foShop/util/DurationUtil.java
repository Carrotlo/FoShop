package me.foesio.foShop.util;

import me.foesio.core.number.DurationParser;
import me.foesio.core.number.TickDuration;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

public final class DurationUtil {
    private DurationUtil() {
    }

    public static OptionalLong parseSeconds(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.isBlank()) {
            return OptionalLong.empty();
        }

        OptionalLong coreParsed = DurationParser.parse(normalized)
                .map(duration -> OptionalLong.of(duration.secondsFloor()))
                .orElseGet(OptionalLong::empty);
        if (coreParsed.isPresent()) {
            return coreParsed.getAsLong() <= 0L ? OptionalLong.empty() : coreParsed;
        }

        long multiplier = 1L;
        String number = normalized;
        for (String suffix : List.of("seconds", "second", "secs", "sec", "s", "minutes", "minute", "mins", "min", "m", "hours", "hour", "hrs", "hr", "h", "days", "day", "d")) {
            if (!normalized.endsWith(suffix)) {
                continue;
            }
            number = normalized.substring(0, normalized.length() - suffix.length());
            multiplier = switch (suffix.charAt(0)) {
                case 'd' -> TickDuration.SECONDS_PER_DAY;
                case 'h' -> TickDuration.SECONDS_PER_HOUR;
                case 'm' -> TickDuration.SECONDS_PER_MINUTE;
                default -> 1L;
            };
            break;
        }

        try {
            double value = Double.parseDouble(number);
            long seconds = Math.round(value * multiplier);
            return seconds <= 0L ? OptionalLong.empty() : OptionalLong.of(seconds);
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    public static String format(long seconds) {
        long remaining = Math.max(0L, seconds);
        long days = remaining / TickDuration.SECONDS_PER_DAY;
        remaining %= TickDuration.SECONDS_PER_DAY;
        long hours = remaining / TickDuration.SECONDS_PER_HOUR;
        remaining %= TickDuration.SECONDS_PER_HOUR;
        long minutes = remaining / TickDuration.SECONDS_PER_MINUTE;
        long secs = remaining % TickDuration.SECONDS_PER_MINUTE;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
