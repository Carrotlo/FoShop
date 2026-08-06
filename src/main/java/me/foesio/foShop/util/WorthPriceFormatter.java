package me.foesio.foShop.util;

import me.foesio.foShop.economy.EconomyService;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorthPriceFormatter {

    private static final Pattern FORMATTED_MONEY_NUMBER = Pattern.compile("[-+]?\\d[\\d,]*(?:\\.\\d+)?");

    private WorthPriceFormatter() {
    }

    public static String format(EconomyService economyService, double price) {
        if (price <= 0D || !Double.isFinite(price)) {
            return "disabled";
        }

        String compact = formatNumber(price);
        if (economyService == null) {
            return compact;
        }

        String formatted = economyService.format(price);
        Matcher matcher = FORMATTED_MONEY_NUMBER.matcher(formatted);
        if (!matcher.find()) {
            return compact;
        }

        return formatted.substring(0, matcher.start()) + compact + formatted.substring(trimExistingCompactSuffix(formatted, matcher.end()));
    }

    public static String formatNumber(double price) {
        double absolute = Math.abs(price);
        double value = price;
        String suffix = "";

        if (absolute >= 1_000_000_000D) {
            value = price / 1_000_000_000D;
            suffix = "B";
        } else if (absolute >= 1_000_000D) {
            value = price / 1_000_000D;
            suffix = "M";
        } else if (absolute >= 1_000D) {
            value = price / 1_000D;
            suffix = "K";
        }

        DecimalFormat format = new DecimalFormat(suffix.isEmpty() ? "#,##0.00" : "0.00");
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + suffix;
    }

    private static int trimExistingCompactSuffix(String formatted, int end) {
        if (end < formatted.length() && "kKmMbB".indexOf(formatted.charAt(end)) >= 0) {
            return end + 1;
        }
        return end;
    }
}
