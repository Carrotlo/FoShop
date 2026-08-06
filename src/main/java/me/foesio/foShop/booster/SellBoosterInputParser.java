package me.foesio.foShop.booster;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SellBoosterInputParser {

    private SellBoosterInputParser() {
    }

    public static Optional<StartInput> parseCommandStart(SellBoosterScope scope, String[] args) {
        if (scope == SellBoosterScope.GLOBAL) {
            return args.length == 5 ? Optional.of(new StartInput("", args[3], args[4])) : Optional.empty();
        }

        if (args.length < 6) {
            return Optional.empty();
        }
        String target = String.join(" ", Arrays.copyOfRange(args, 3, args.length - 2)).trim();
        if (target.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new StartInput(target, args[args.length - 2], args[args.length - 1]));
    }

    public static Optional<StartInput> parsePromptStart(SellBoosterScope scope, String input) {
        String[] parts = input == null ? new String[0] : input.trim().split("\\s+");
        if (scope == SellBoosterScope.GLOBAL) {
            return parts.length == 2 ? Optional.of(new StartInput("", parts[0], parts[1])) : Optional.empty();
        }

        if (parts.length < 3) {
            return Optional.empty();
        }
        String target = String.join(" ", Arrays.copyOfRange(parts, 0, parts.length - 2)).trim();
        if (target.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new StartInput(target, parts[parts.length - 2], parts[parts.length - 1]));
    }

    public static Optional<Target> resolvePlayerTarget(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId().toString(), online.getName()));
        }

        try {
            UUID uuid = UUID.fromString(input);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName() == null ? uuid.toString() : offline.getName();
            return Optional.of(new Target(uuid.toString(), name));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID; fall back to Bukkit's local player cache.
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                return Optional.of(new Target(offline.getUniqueId().toString(), name));
            }
        }
        return Optional.empty();
    }

    public static Double parseMultiplier(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            double multiplier = Double.parseDouble(input.toLowerCase(Locale.ROOT).replace("x", ""));
            return Double.isFinite(multiplier) ? multiplier : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record StartInput(String target, String multiplier, String duration) {
    }

    public record Target(String ownerId, String ownerName) {
    }
}
