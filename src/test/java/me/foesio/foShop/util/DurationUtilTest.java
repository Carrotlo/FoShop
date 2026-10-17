package me.foesio.foShop.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationUtilTest {

    @Test
    void configuredUnitsAndPairReachFinalDuration() {
        Map<String, String> text = Map.of(
                "duration.day-one", "{value} dag",
                "duration.day-other", "{value} dagen",
                "duration.hour-one", "{value} uur",
                "duration.hour-other", "{value} uren",
                "duration.second-other", "{value} seconden",
                "duration.pair", "{second} na {first}");
        BiFunction<String, Map<String, String>, String> render = (key, values) -> {
            String result = text.getOrDefault(key, "");
            for (var value : values.entrySet()) result = result.replace(value.getKey(), value.getValue());
            return result;
        };

        assertEquals("1 uur na 2 dagen", DurationUtil.formatConfigured(2 * 86400L + 3600L, render));
        assertEquals("0 seconden", DurationUtil.formatConfigured(0L, render));
    }
}
