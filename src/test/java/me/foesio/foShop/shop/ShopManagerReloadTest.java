package me.foesio.foShop.shop;

import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.validation.ShopValidationService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopManagerReloadTest {

    @Test
    void preparedShopDataReplacesLiveDataOnlyWhenPublished() {
        ShopManager manager = new ShopManager(null, new ShopValidationService(), null, null);
        ShopItem oldItem = new ShopItem("stone", Material.STONE, 10, 1, 10, 2, List.of());
        ShopSection oldSection = new ShopSection("old", "Old", 27, Material.CHEST, 10, List.of(oldItem));
        manager.publish(new ShopManager.PreparedReload(Map.of("old", oldSection),
                new ShopManager.ReloadResult(1, 0, 0, List.of()), 1));

        ShopItem newItem = new ShopItem("diamond", Material.DIAMOND, 11, 1, 100, 20, List.of());
        ShopSection newSection = new ShopSection("new", "New", 27, Material.CHEST, 11, List.of(newItem));
        ShopManager.PreparedReload proposed = new ShopManager.PreparedReload(Map.of("new", newSection),
                new ShopManager.ReloadResult(1, 0, 0, List.of()), manager.priceRevision() + 1);

        assertTrue(manager.getSection("old").isPresent());
        assertFalse(manager.getSection("new").isPresent());
        assertEquals(2, manager.getHighestSellPrices().get(Material.STONE));
        long revision = manager.priceRevision();

        manager.publish(proposed);

        assertFalse(manager.getSection("old").isPresent());
        assertTrue(manager.getSection("new").isPresent());
        assertFalse(manager.getHighestSellPrices().containsKey(Material.STONE));
        assertEquals(20, manager.getHighestSellPrices().get(Material.DIAMOND));
        assertEquals(revision + 1, manager.priceRevision());
    }
}
