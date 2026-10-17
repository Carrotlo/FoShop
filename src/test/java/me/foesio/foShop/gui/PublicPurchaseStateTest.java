package me.foesio.foShop.gui;

import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopSection;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicPurchaseStateTest {

    @Test
    void purchaseRequiresAnEnabledSectionAndItsCurrentProductObject() {
        ShopItem current = new ShopItem("stone", Material.STONE, 10, 1, 10, 2, List.of());
        ShopSection enabled = new ShopSection("blocks", "Blocks", 27, Material.CHEST,
                null, List.of(), true, 10, List.of(current));
        ShopSection disabled = new ShopSection("blocks", "Blocks", 27, Material.CHEST,
                null, List.of(), false, 10, List.of(current));
        ShopItem stale = new ShopItem("stone", Material.STONE, 10, 1, 10, 2, List.of());

        assertTrue(GuiService.isCurrentPublicItem(enabled, current));
        assertFalse(GuiService.isCurrentPublicItem(disabled, current));
        assertFalse(GuiService.isCurrentPublicItem(enabled, stale));
        assertFalse(GuiService.isCurrentPublicItem(enabled, null));
    }
}
