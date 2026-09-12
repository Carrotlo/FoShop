package me.foesio.foShop.util;

import me.foesio.core.economy.VaultEconomyBridge;
import me.foesio.core.number.NumberFormatters;

public final class WorthPriceFormatter {

    private WorthPriceFormatter() {
    }

    public static String format(VaultEconomyBridge economyService, double price) {
        if (price <= 0D || !Double.isFinite(price)) {
            return "disabled";
        }

        if (economyService == null) {
            return formatNumber(price);
        }
        return economyService.formatCompact(price);
    }

    public static String formatNumber(double price) {
        return NumberFormatters.compact(price);
    }

}
