package me.foesio.foShop.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import me.foesio.foShop.FoShop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Single-owner price extension. Registration/invalidation/reporting use the server thread.
 * Quotes can run on packet threads; providers must publish immutable snapshots.
 */
public final class FoShopPriceApi {
    private record Registration(Plugin owner, FoShopPriceProvider provider) {}
    private final FoShop plugin;
    private volatile Registration registration;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong nextWarning = new AtomicLong();
    public FoShopPriceApi(FoShop plugin) { this.plugin = plugin; }
    public void register(Plugin owner, FoShopPriceProvider provider) {
        requireMainThread();
        if (owner == null || provider == null || !owner.isEnabled()) throw new IllegalArgumentException("Enabled owner and provider required");
        if (registration != null && registration.owner() != owner) throw new IllegalStateException("A price provider is already registered");
        registration = new Registration(owner, provider);
        revision.incrementAndGet();
    }
    public void unregister(Plugin owner) {
        requireMainThread();
        if (registration != null && registration.owner() == owner) { registration = null; revision.incrementAndGet(); }
    }
    public void invalidate(Plugin owner) {
        requireMainThread();
        if (registration != null && registration.owner() == owner) revision.incrementAndGet();
    }
    public long revision() { return revision.get(); }
    public void catalogChanged() {
        requireMainThread();
        revision.incrementAndGet();
        Registration current = registration;
        if (current != null) current.provider().catalogChanged();
    }
    public boolean active() { return registration != null; }
    public double apply(ItemStack item, double original, double offered) {
        Registration current = registration;
        if (current == null || offered <= 0 || original <= 0) return offered;
        try {
            double result = current.provider().price(item, original, offered);
            return Double.isFinite(result) && result > 0 ? result : -1;
        } catch (RuntimeException exception) {
            long now = System.currentTimeMillis(), previous = nextWarning.get();
            if (now >= previous && nextWarning.compareAndSet(previous, now + 60000))
                plugin.getLogger().warning("Price provider failed; affected sales denied. Check the addon configuration.");
            return -1;
        }
    }
    public List<String> lore(Player player, ItemStack item, double original, double offered) {
        Registration current = registration;
        if (current == null) return List.of();
        try { return List.copyOf(current.provider().lore(player, item, original, offered)); }
        catch (RuntimeException exception) { return List.of(); }
    }
    public double quote(Player player, ItemStack item, double externalMultiplier) {
        if (!Double.isFinite(externalMultiplier) || externalMultiplier <= 0) return -1;
        return plugin.getShopManager().getSellPrice(player, item, externalMultiplier);
    }
    public void reportSale(UUID transactionId, String source, List<ItemStack> items) {
        requireMainThread();
        if (transactionId == null || source == null || source.isBlank() || source.length() > 64 || items == null || items.size() > 8192)
            throw new IllegalArgumentException("Invalid sale report");
        if (items.stream().anyMatch(item -> item == null || item.getAmount() <= 0)) throw new IllegalArgumentException("Invalid sale item");
        Bukkit.getPluginManager().callEvent(new FoShopSaleEvent(transactionId, source, items));
    }
    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("FoShop API mutation requires the server thread");
    }
    public void reportMaterialSale(UUID transactionId, String source, java.util.Map<org.bukkit.Material,Long> amounts) {
        requireMainThread();
        if (transactionId == null || source == null || source.isBlank() || source.length()>64 || amounts == null
                || amounts.entrySet().stream().anyMatch(e -> e.getKey()==null || e.getValue()==null || e.getValue()<=0))
            throw new IllegalArgumentException("Invalid material sale report");
        Bukkit.getPluginManager().callEvent(new FoShopSaleEvent(transactionId,source,amounts));
    }
}
