package me.foesio.foShop;

import me.foesio.foShop.api.FoShopGlobalPriceSnapshot;
import me.foesio.foShop.api.FoShopMarketApi;
import me.foesio.foShop.api.FoShopProductSnapshot;
import me.foesio.foShop.api.FoShopRotatingOffer;
import me.foesio.foShop.api.FoShopRotationChangedEvent;
import me.foesio.foShop.api.FoShopSectionSnapshot;
import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.shop.TemporarySellPriceOverrides;
import me.foesio.foShop.shop.GlobalSellPriceService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/** FoShop-owned implementation of the supported market service. */
final class DefaultFoShopMarketApi implements FoShopMarketApi {
    private final FoShop plugin;
    private final TemporarySellPriceOverrides overrides;

    DefaultFoShopMarketApi(FoShop plugin, TemporarySellPriceOverrides overrides) {
        this.plugin = plugin;
        this.overrides = overrides;
    }

    @Override
    public OptionalDouble getSellUnitPrice(Player player, ItemStack item) {
        if (!plugin.isEnabled() || player == null || !player.isOnline() || item == null
                || item.getItemMeta() instanceof BlockStateMeta || item.getItemMeta() instanceof BundleMeta) {
            return OptionalDouble.empty();
        }
        double price = plugin.getShopManager().getSellPrice(player, item);
        return price > 0D && Double.isFinite(price) ? OptionalDouble.of(price) : OptionalDouble.empty();
    }

    @Override
    public List<FoShopSectionSnapshot> getSections(Player player) {
        if (!plugin.isEnabled() || player == null || !player.isOnline()
                || !player.hasPermission("foshop.use")) return List.of();
        List<FoShopSectionSnapshot> sections = new ArrayList<>();
        for (ShopSection section : plugin.getShopManager().getSectionsOrdered()) {
            if (!section.enabled()) continue;
            List<FoShopProductSnapshot> products = new ArrayList<>();
            for (ShopItem item : section.items().stream()
                    .sorted(Comparator.comparingInt(ShopItem::page)
                            .thenComparingInt(ShopItem::slot).thenComparing(ShopItem::id)).toList()) {
                if (!item.requiredPermission().isBlank()
                        && !plugin.getPermissionService().has(player, item.requiredPermission())) continue;
                ItemStack stack = item.itemStack();
                if (stack == null && item.material() != null && item.material() != Material.AIR) {
                    stack = new ItemStack(item.material());
                }
                products.add(new FoShopProductSnapshot(item.id(), item.type().name(), item.page(), item.slot(), stack,
                        item.buyPrice(), item.sellPrice(), plugin.getShopManager().getStock(section.id(), item.id()),
                        plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), section.id(), item),
                        item.requiredPermission()));
            }
            ItemStack icon = section.iconItem();
            if (icon == null && section.icon() != null && section.icon() != Material.AIR) {
                icon = new ItemStack(section.icon());
            }
            sections.add(new FoShopSectionSnapshot(section.id(), section.title(), section.slot(), icon, products));
        }
        return List.copyOf(sections);
    }

    @Override
    public List<FoShopGlobalPriceSnapshot> getGlobalSellPrices() {
        GlobalSellPriceService prices = plugin.getGlobalSellPriceService();
        if (!plugin.isEnabled() || prices == null || !prices.isEnabled()) return List.of();
        List<FoShopGlobalPriceSnapshot> listings = new ArrayList<>();
        prices.entries().stream().filter(entry -> entry.enabled() && entry.price() > 0D)
                .forEach(entry -> listings.add(new FoShopGlobalPriceSnapshot("MATERIAL",
                        entry.material().name(), entry.price(), entry.rotatingShop())));
        prices.potionEntries().stream().filter(entry -> entry.enabled() && entry.price() > 0D)
                .forEach(entry -> listings.add(new FoShopGlobalPriceSnapshot("POTION",
                        GlobalSellPriceService.potionEntryId(entry.potionType()), entry.price(), entry.rotatingShop())));
        prices.enchantmentEntries().stream().filter(entry -> entry.enabled() && entry.price() > 0D)
                .forEach(entry -> listings.add(new FoShopGlobalPriceSnapshot("ENCHANTMENT",
                        GlobalSellPriceService.enchantmentEntryId(entry.enchantmentKey(), entry.level()),
                        entry.price(), entry.rotatingShop())));
        return listings.stream().sorted(Comparator.comparing(FoShopGlobalPriceSnapshot::kind)
                .thenComparing(FoShopGlobalPriceSnapshot::id)).toList();
    }

    @Override
    public List<FoShopRotatingOffer> getRotatingOffers() {
        if (!plugin.isEnabled() || !plugin.getRotatingShopService().isEnabled()) return List.of();
        return plugin.getRotatingShopService().activeEntries().stream()
                .map(entry -> new FoShopRotatingOffer(entry.sectionId(), entry.itemId(), entry.multiplier()))
                .toList();
    }

    @Override
    public long getNextRotationAtMillis() {
        return plugin.isEnabled() && plugin.getRotatingShopService().isEnabled()
                ? plugin.getRotatingShopService().nextResetAt() : 0L;
    }

    @Override
    public boolean openSection(Player player, String sectionId) {
        if (!plugin.isEnabled() || player == null || !player.isOnline() || sectionId == null
                || !player.hasPermission("foshop.use") || !plugin.getGuiService().hasPublicShopGui()) return false;
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null || !section.enabled()) return false;
        plugin.getGuiService().openShopSection(player, section);
        return true;
    }

    @Override
    public boolean rotateNow(Plugin requester) {
        return plugin.isEnabled() && requester != null && requester.isEnabled()
                && plugin.getRotatingShopService().rotateNow(FoShopRotationChangedEvent.Cause.API);
    }

    @Override
    public boolean setTemporarySellPrice(Plugin owner, String overrideId, ItemStack template,
                                         double unitPrice, long durationSeconds) {
        return plugin.isEnabled() && overrides.set(owner, overrideId, template, unitPrice, durationSeconds);
    }

    @Override
    public boolean removeTemporarySellPrice(Plugin owner, String overrideId) {
        return plugin.isEnabled() && overrides.remove(owner, overrideId);
    }

    @Override
    public int removeTemporarySellPrices(Plugin owner) {
        return plugin.isEnabled() ? overrides.removeAll(owner) : 0;
    }
}
