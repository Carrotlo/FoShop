package me.foesio.foShop.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;

public final class VanillaItemNames {

    private VanillaItemNames() {
    }

    public static boolean isVanillaName(Material material, String input) {
        if (material == null || material == Material.AIR || input == null || input.isBlank()) {
            return false;
        }

        String normalizedInput = normalizeName(input);
        if (normalizedInput.isBlank()) {
            return false;
        }

        return normalizedInput.equals(normalizeName(prettify(material.name())))
                || normalizedInput.equals(normalizeName(prettify(material.getKey().getKey())));
    }

    public static boolean clearVanillaDisplayName(ItemStack stack, Material fallbackMaterial) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }

        Material material = fallbackMaterial == null || fallbackMaterial == Material.AIR ? stack.getType() : fallbackMaterial;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !isVanillaName(material, meta.getDisplayName())) {
            return false;
        }

        meta.setDisplayName(null);
        stack.setItemMeta(meta);
        return true;
    }

    public static boolean isVanillaEquivalentTemplate(ItemStack stack, Material fallbackMaterial) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }

        Material material = fallbackMaterial == null || fallbackMaterial == Material.AIR ? stack.getType() : fallbackMaterial;
        if (stack.getType() != material) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return true;
        }

        if (meta.hasDisplayName() && !isVanillaName(material, meta.getDisplayName())) {
            return false;
        }
        if (meta.hasLore()
                || meta.hasCustomModelData()
                || meta.hasEnchants()
                || meta.hasAttributeModifiers()
                || meta.isUnbreakable()) {
            return false;
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            return false;
        }
        return !(meta instanceof BannerMeta
                || meta instanceof BlockStateMeta
                || meta instanceof BundleMeta
                || meta instanceof EnchantmentStorageMeta
                || meta instanceof FireworkMeta
                || meta instanceof PotionMeta
                || meta instanceof SkullMeta);
    }

    private static String normalizeName(String input) {
        String stripped = input
                .replaceAll("(?i)(?:&|§)x(?:(?:&|§)[0-9A-F]){6}", "")
                .replaceAll("&#[A-Fa-f0-9]{6}", "")
                .replaceAll("(?i)(?:&|§)[0-9A-FK-OR]", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String prettify(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\- ]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
