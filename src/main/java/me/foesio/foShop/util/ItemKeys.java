package me.foesio.foShop.util;

import me.foesio.core.material.MaterialTypes;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ItemKeys {

    private static final Map<String, String> LEGACY_MATERIALS = Map.ofEntries(
            Map.entry("EXP_BOTTLE", "EXPERIENCE_BOTTLE"),
            Map.entry("WORKBENCH", "CRAFTING_TABLE"),
            Map.entry("GRASS", "GRASS_BLOCK"),
            Map.entry("LONG_GRASS", "SHORT_GRASS"),
            Map.entry("YELLOW_FLOWER", "DANDELION"),
            Map.entry("RED_ROSE", "POPPY"),
            Map.entry("WOOD_STEP", "OAK_SLAB"),
            Map.entry("STEP", "STONE_SLAB"),
            Map.entry("DOUBLE_STEP", "SMOOTH_STONE"),
            Map.entry("SMOOTH_BRICK", "STONE_BRICKS"),
            Map.entry("THIN_GLASS", "GLASS_PANE"),
            Map.entry("STAINED_GLASS", "WHITE_STAINED_GLASS"),
            Map.entry("STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE"),
            Map.entry("LOG", "OAK_LOG"),
            Map.entry("LOG_2", "ACACIA_LOG"),
            Map.entry("LEAVES", "OAK_LEAVES"),
            Map.entry("LEAVES_2", "ACACIA_LEAVES"),
            Map.entry("WOOD", "OAK_WOOD"),
            Map.entry("SAPLING", "OAK_SAPLING"),
            Map.entry("SKULL", "SKELETON_SKULL"),
            Map.entry("SKULL_ITEM", "PLAYER_HEAD"),
            Map.entry("MONSTER_EGG", "SPAWNER"),
            Map.entry("ENDER_STONE", "END_STONE"),
            Map.entry("NETHER_FENCE", "NETHER_BRICK_FENCE"),
            Map.entry("HARD_CLAY", "TERRACOTTA"),
            Map.entry("STAINED_CLAY", "WHITE_TERRACOTTA"),
            Map.entry("INK_SACK", "INK_SAC"),
            Map.entry("SULPHUR", "GUNPOWDER"),
            Map.entry("GOLD_AXE", "GOLDEN_AXE"),
            Map.entry("GOLD_PICKAXE", "GOLDEN_PICKAXE"),
            Map.entry("GOLD_SPADE", "GOLDEN_SHOVEL"),
            Map.entry("GOLD_SWORD", "GOLDEN_SWORD"),
            Map.entry("GOLD_HOE", "GOLDEN_HOE")
    );

    private static final Map<String, String> LEGACY_ENCHANTMENTS = Map.ofEntries(
            Map.entry("PROTECTION_ENVIRONMENTAL", "minecraft:protection"),
            Map.entry("PROTECTION_FIRE", "minecraft:fire_protection"),
            Map.entry("PROTECTION_FALL", "minecraft:feather_falling"),
            Map.entry("PROTECTION_EXPLOSIONS", "minecraft:blast_protection"),
            Map.entry("PROTECTION_PROJECTILE", "minecraft:projectile_protection"),
            Map.entry("OXYGEN", "minecraft:respiration"),
            Map.entry("WATER_WORKER", "minecraft:aqua_affinity"),
            Map.entry("THORNS", "minecraft:thorns"),
            Map.entry("DEPTH_STRIDER", "minecraft:depth_strider"),
            Map.entry("FROST_WALKER", "minecraft:frost_walker"),
            Map.entry("BINDING_CURSE", "minecraft:binding_curse"),
            Map.entry("DAMAGE_ALL", "minecraft:sharpness"),
            Map.entry("DAMAGE_UNDEAD", "minecraft:smite"),
            Map.entry("DAMAGE_ARTHROPODS", "minecraft:bane_of_arthropods"),
            Map.entry("KNOCKBACK", "minecraft:knockback"),
            Map.entry("FIRE_ASPECT", "minecraft:fire_aspect"),
            Map.entry("LOOT_BONUS_MOBS", "minecraft:looting"),
            Map.entry("SWEEPING_EDGE", "minecraft:sweeping_edge"),
            Map.entry("DIG_SPEED", "minecraft:efficiency"),
            Map.entry("SILK_TOUCH", "minecraft:silk_touch"),
            Map.entry("DURABILITY", "minecraft:unbreaking"),
            Map.entry("LOOT_BONUS_BLOCKS", "minecraft:fortune"),
            Map.entry("ARROW_DAMAGE", "minecraft:power"),
            Map.entry("ARROW_KNOCKBACK", "minecraft:punch"),
            Map.entry("ARROW_FIRE", "minecraft:flame"),
            Map.entry("ARROW_INFINITE", "minecraft:infinity"),
            Map.entry("LUCK", "minecraft:luck_of_the_sea"),
            Map.entry("LURE", "minecraft:lure"),
            Map.entry("LOYALTY", "minecraft:loyalty"),
            Map.entry("IMPALING", "minecraft:impaling"),
            Map.entry("RIPTIDE", "minecraft:riptide"),
            Map.entry("CHANNELING", "minecraft:channeling"),
            Map.entry("MULTISHOT", "minecraft:multishot"),
            Map.entry("QUICK_CHARGE", "minecraft:quick_charge"),
            Map.entry("PIERCING", "minecraft:piercing"),
            Map.entry("MENDING", "minecraft:mending"),
            Map.entry("VANISHING_CURSE", "minecraft:vanishing_curse")
    );

    private ItemKeys() {
    }

    public static Optional<Material> material(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String cleaned = raw.trim();
        if (cleaned.contains(":")) {
            String[] split = cleaned.split(":");
            cleaned = split.length >= 2 && split[0].equalsIgnoreCase("minecraft") ? split[1] : split[0];
        }

        cleaned = cleanEnum(cleaned);
        cleaned = LEGACY_MATERIALS.getOrDefault(cleaned, cleaned);
        Material material = MaterialTypes.match(cleaned);
        if (material == null) {
            material = Material.matchMaterial(cleaned, true);
        }
        return Optional.ofNullable(material);
    }

    public static Enchantment enchantment(String raw) {
        String normalized = normalizeEnchantmentKey(raw);
        if (normalized == null) {
            return null;
        }

        NamespacedKey key = NamespacedKey.fromString(normalized);
        Enchantment enchantment = key == null ? null : Enchantment.getByKey(key);
        if (enchantment == null) {
            enchantment = Enchantment.getByName(cleanEnum(raw));
        }
        return enchantment;
    }

    public static String normalizeEnchantmentKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        String enumKey = cleanEnum(trimmed.contains(":") ? trimmed.substring(trimmed.lastIndexOf(':') + 1) : trimmed);
        String mapped = LEGACY_ENCHANTMENTS.get(enumKey);
        if (mapped != null) {
            return mapped;
        }

        String key = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return key.contains(":") ? key : "minecraft:" + key;
    }

    private static String cleanEnum(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
