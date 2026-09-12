package me.foesio.foShop.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public record ShopItem(
        String id,
        ShopItemType type,
        Material material,
        int page,
        int slot,
        int amount,
        double buyPrice,
        double sellPrice,
        List<String> lore,
        String displayName,
        Integer customModelData,
        Map<String, Integer> enchants,
        Integer stackSize,
        ItemStack itemStack,
        String permission,
        String requiredPermission,
        List<String> commands,
        String enchantment,
        int enchantmentLevel,
        Integer stock,
        Integer buyLimit,
        Integer limitResetSeconds,
        String rawNbt
) {
    public ShopItem {
        type = type == null ? ShopItemType.ITEM : type;
        requiredPermission = requiredPermission == null ? "" : requiredPermission.trim();
        lore = lore == null ? List.of() : List.copyOf(lore);
        enchants = enchants == null ? Map.of() : Map.copyOf(enchants);
        commands = commands == null ? List.of() : List.copyOf(commands);
        itemStack = itemStack == null ? null : itemStack.clone();
    }

    public ShopItem(
            String id,
            Material material,
            int slot,
            int amount,
            double buyPrice,
            double sellPrice,
            List<String> lore
    ) {
        this(id, material, slot, amount, buyPrice, sellPrice, lore, null, null, Map.of());
    }

    public ShopItem(
            String id,
            Material material,
            int slot,
            int amount,
            double buyPrice,
            double sellPrice,
            List<String> lore,
            String displayName,
            Integer customModelData,
            Map<String, Integer> enchants
    ) {
        this(id, material, slot, amount, buyPrice, sellPrice, lore, displayName, customModelData, enchants, null);
    }

    public ShopItem(
            String id,
            Material material,
            int slot,
            int amount,
            double buyPrice,
            double sellPrice,
            List<String> lore,
            String displayName,
            Integer customModelData,
            Map<String, Integer> enchants,
            Integer stackSize
    ) {
        this(id, ShopItemType.ITEM, material, 0, slot, amount, buyPrice, sellPrice, lore, displayName, customModelData, enchants,
                stackSize, null, null, "", List.of(), null, 1, null, null, null, null);
    }

    public boolean canBuy() {
        return type != ShopItemType.DUMMY && buyPrice >= 0D;
    }

    public boolean canSell() {
        return type == ShopItemType.ITEM && sellPrice > 0D && Double.isFinite(sellPrice);
    }

    public int effectiveStackSize() {
        int vanillaMax = material == null ? 64 : material.getMaxStackSize();
        int maxAllowed = Math.max(1, Math.min(64, vanillaMax));
        if (stackSize == null) {
            return maxAllowed;
        }
        return Math.clamp(stackSize, 1, maxAllowed);
    }

    public ItemStack itemStack() {
        return itemStack == null ? null : itemStack.clone();
    }

    public boolean tracksStock() {
        return stock != null && stock >= 0;
    }

    public boolean hasBuyLimit() {
        return buyLimit != null && buyLimit >= 0;
    }
}
