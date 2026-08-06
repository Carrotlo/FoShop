package me.foesio.foShop.booster;

import java.util.Locale;
import java.util.Optional;

public enum SellBoosterScope {
    GLOBAL("global", "Global"),
    PLAYER("player", "Player"),
    TEAM("team", "Team");

    private final String key;
    private final String display;

    SellBoosterScope(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public static Optional<SellBoosterScope> fromInput(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "global", "server", "all" -> Optional.of(GLOBAL);
            case "player", "personal", "user" -> Optional.of(PLAYER);
            case "team", "foteam", "foteams" -> Optional.of(TEAM);
            default -> Optional.empty();
        };
    }
}
