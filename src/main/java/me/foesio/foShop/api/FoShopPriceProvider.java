package me.foesio.foShop.api;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Pure, thread-safe, nonblocking callbacks. Do not modify the supplied stack.
 * A negative price denies sale. Never perform I/O or query player permissions here.
 * original is FoShop's unmodified unit value; offered includes all caller bonuses.
 */
public interface FoShopPriceProvider {
    double price(ItemStack item, double original, double offered);
    /** Called on the server thread after FoShop's configured price catalog changes. */
    default void catalogChanged() {}
    default List<String> lore(Player player, ItemStack item, double original, double offered) { return List.of(); }
}
