package me.foesio.foShop.api;

import org.bukkit.inventory.ItemStack;

/** Read-only view of a currently loaded shop product. Prices are configured base values;
 * negative stock or limit means unlimited. */
public record FoShopProductSnapshot(String id, String type, int page, int slot, ItemStack item,
                                    double buyPrice, double sellPrice, int stock,
                                    int remainingBuyLimit, String requiredPermission) {
    public FoShopProductSnapshot {
        item = item == null ? null : item.clone();
    }

    @Override
    public ItemStack item() {
        return item == null ? null : item.clone();
    }
}
