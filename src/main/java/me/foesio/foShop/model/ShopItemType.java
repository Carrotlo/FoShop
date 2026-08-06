package me.foesio.foShop.model;

public enum ShopItemType {
    ITEM,
    PERMISSION,
    ENCHANTMENT,
    COMMAND,
    DUMMY;

    public static ShopItemType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ITEM;
        }
        return switch (raw.trim().toLowerCase()) {
            case "permission", "perm" -> PERMISSION;
            case "enchantment", "enchant" -> ENCHANTMENT;
            case "command", "commands", "cmd" -> COMMAND;
            case "dummy", "display", "decoration" -> DUMMY;
            default -> ITEM;
        };
    }
}
