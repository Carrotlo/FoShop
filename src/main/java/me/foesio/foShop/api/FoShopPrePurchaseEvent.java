package me.foesio.foShop.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired after FoShop validates a purchase, before withdrawal or delivery. Cancellation leaves it unchanged. */
public final class FoShopPrePurchaseEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String sectionId;
    private final String productId;
    private final String productType;
    private final ItemStack item;
    private final int amount;
    private final double totalPrice;
    private boolean cancelled;

    public FoShopPrePurchaseEvent(Player player, String sectionId, String productId,
                                  String productType, ItemStack item, int amount, double totalPrice) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.sectionId = sectionId;
        this.productId = productId;
        this.productType = productType;
        this.item = item == null ? null : item.clone();
        this.amount = amount;
        this.totalPrice = totalPrice;
    }

    public Player getPlayer() { return player; }
    public String getSectionId() { return sectionId; }
    public String getProductId() { return productId; }
    public String getProductType() { return productType; }
    /** Delivered item template, or null for command and permission products. */
    public ItemStack getItem() { return item == null ? null : item.clone(); }
    public int getAmount() { return amount; }
    public double getTotalPrice() { return totalPrice; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
