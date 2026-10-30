package me.foesio.foShop.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Fired once after FoShop successfully deposits earnings for a sale batch. */
public final class FoShopSaleCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final FoShopSaleSource source;
    private final List<ItemStack> soldItems;
    private final int soldUnits;
    private final double totalPrice;

    public FoShopSaleCompletedEvent(Player player, FoShopSaleSource source, List<ItemStack> soldItems,
                                    int soldUnits, double totalPrice) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.source = source;
        this.soldItems = FoShopSaleItems.copy(soldItems);
        this.soldUnits = soldUnits;
        this.totalPrice = totalPrice;
    }

    public Player getPlayer() { return player; }
    public FoShopSaleSource getSource() { return source; }
    public List<ItemStack> getSoldItems() { return FoShopSaleItems.copy(soldItems); }
    public int getSoldUnits() { return soldUnits; }
    public double getTotalPrice() { return totalPrice; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
