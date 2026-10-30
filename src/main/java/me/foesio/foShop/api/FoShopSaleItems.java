package me.foesio.foShop.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

final class FoShopSaleItems {
    private FoShopSaleItems() { }

    static List<ItemStack> copy(List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }
}
