package me.foesio.foShop.api;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Notification after successful payment. Not cancellable. Items are defensive copies.
 * External sellers must emit once, only after committing inventory AND payment.
 */
public final class FoShopSaleEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID transactionId;
    private final String source;
    private final List<ItemStack> items;
    private final Map<Material,Long> plainAmounts;
    public FoShopSaleEvent(UUID transactionId, String source, List<ItemStack> items) {
        this.transactionId = transactionId;
        this.source = source;
        this.items = items.stream().map(ItemStack::clone).toList();
        this.plainAmounts = Map.of();
    }
    public FoShopSaleEvent(UUID transactionId, String source, Map<Material,Long> plainAmounts) {
        this.transactionId = transactionId; this.source = source; this.items = List.of(); this.plainAmounts = Map.copyOf(plainAmounts);
    }
    /** Virtual-storage plain-material quantities. Empty for item-stack reports.
     * Consumers select the representation; item classification belongs to the addon.
     */
    public Map<Material,Long> plainAmounts() { return plainAmounts; }
    public UUID transactionId() { return transactionId; }
    public String source() { return source; }
    public List<ItemStack> items() { return items.stream().map(ItemStack::clone).toList(); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
