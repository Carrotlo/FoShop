package me.foesio.foShop.api;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Read-only view of one enabled section and its visible products. */
public record FoShopSectionSnapshot(String id, String title, int slot, ItemStack icon,
                                    List<FoShopProductSnapshot> products) {
    public FoShopSectionSnapshot {
        icon = icon == null ? null : icon.clone();
        products = List.copyOf(products);
    }

    @Override
    public ItemStack icon() {
        return icon == null ? null : icon.clone();
    }
}
