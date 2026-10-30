package me.foesio.foShop.shop;

import me.foesio.foShop.api.FoShopProductSnapshot;
import me.foesio.foShop.api.FoShopSectionSnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporarySellPriceOverridesTest {
    @Test
    void newestMatchingPriceExpiresAndDoesNotMutateTemplate() {
        AtomicLong now = new AtomicLong(1_000L);
        TemporarySellPriceOverrides overrides = new TemporarySellPriceOverrides(now::get);
        AtomicBoolean firstEnabled = new AtomicBoolean(true);
        Plugin first = plugin("First", firstEnabled);
        Plugin second = plugin("Second", new AtomicBoolean(true));
        ItemStack template = stack(Material.STONE);

        assertTrue(overrides.set(first, "promo", template, 4D, 10L));
        template.setType(Material.DIRT);
        assertEquals(4D, overrides.price(stack(Material.STONE)).orElseThrow());
        assertTrue(overrides.set(second, "promo", stack(Material.STONE), 0D, 2L));
        assertEquals(0D, overrides.price(stack(Material.STONE)).orElseThrow());

        now.set(3_001L);
        assertEquals(4D, overrides.price(stack(Material.STONE)).orElseThrow());
        firstEnabled.set(false);
        assertFalse(overrides.price(stack(Material.STONE)).isPresent());
    }

    @Test
    void rejectsInvalidAndRemovesOnlyOwnersOwnOverrides() {
        TemporarySellPriceOverrides overrides = new TemporarySellPriceOverrides();
        Plugin first = plugin("First", new AtomicBoolean(true));
        Plugin second = plugin("Second", new AtomicBoolean(true));
        ItemStack stone = stack(Material.STONE);

        assertFalse(overrides.set(first, "bad", stone, Double.NaN, 2L));
        assertFalse(overrides.set(first, "bad", stone, 2D, 0L));
        assertTrue(overrides.set(first, "one", stone, 2D, 10L));
        assertTrue(overrides.set(second, "two", stone, 3D, 10L));
        assertEquals(1, overrides.removeAll(first));
        assertEquals(3D, overrides.price(stone).orElseThrow());
        assertFalse(overrides.remove(first, "two"));
        assertTrue(overrides.remove(second, "two"));
    }

    @Test
    void catalogSnapshotsDoNotExposeTheirItemStacks() {
        ItemStack original = stack(Material.STONE);
        FoShopProductSnapshot product = new FoShopProductSnapshot("stone", "ITEM", 0, 10,
                original, 2D, 1D, 8, 3, "");
        FoShopSectionSnapshot section = new FoShopSectionSnapshot("blocks", "Blocks", 10,
                original, java.util.List.of(product));

        original.setType(Material.DIRT);
        assertEquals(Material.STONE, product.item().getType());
        assertEquals(Material.STONE, section.icon().getType());
        assertNotSame(product.item(), product.item());
        assertNotSame(section.icon(), section.icon());
    }

    private Plugin plugin(String name, AtomicBoolean enabled) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "isEnabled" -> enabled.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ItemStack stack(Material material) {
        ItemStack stack = mock(ItemStack.class);
        AtomicReference<Material> current = new AtomicReference<>(material);
        when(stack.getType()).thenAnswer(ignored -> current.get());
        when(stack.clone()).thenAnswer(ignored -> stack(current.get()));
        when(stack.isSimilar(any(ItemStack.class))).thenAnswer(call ->
                current.get() == ((ItemStack) call.getArgument(0)).getType());
        org.mockito.Mockito.doAnswer(call -> {
            current.set(call.getArgument(0));
            return null;
        }).when(stack).setType(any(Material.class));
        return stack;
    }
}
