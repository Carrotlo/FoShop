package me.foesio.foShop.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.OptionalDouble;

/**
 * FoShop's supported market integration boundary. Call synchronously on the Bukkit server thread.
 * Player queries and GUI opening require the player to be online. Folia region-thread use is not promised.
 */
public interface FoShopMarketApi {
    /** Effective price for one non-container item, before multiplying by stack amount. Empty means unsellable. */
    OptionalDouble getSellUnitPrice(Player player, ItemStack item);

    /** Enabled sections and products visible to the player, with current stock and buy limits. */
    List<FoShopSectionSnapshot> getSections(Player player);

    /** Enabled global sell listings, excluding player-specific boosts and temporary price overrides. */
    List<FoShopGlobalPriceSnapshot> getGlobalSellPrices();

    /** Currently effective rotating offers, or an empty list when rotation is disabled. */
    List<FoShopRotatingOffer> getRotatingOffers();

    /** Unix epoch milliseconds of the next rotation, or zero when rotation is disabled. */
    long getNextRotationAtMillis();

    /** Opens a current enabled section if the player is online and has FoShop access. */
    boolean openSection(Player player, String sectionId);

    /** Replaces the current rotating offers if rotation is enabled and the requester is enabled. */
    boolean rotateNow(Plugin requester);

    /**
     * Replaces the base sell price of an otherwise sellable, exactly matching item for a duration.
     * The newest matching override wins. Overrides are runtime-only and scoped to owner plus id.
     */
    boolean setTemporarySellPrice(Plugin owner, String overrideId, ItemStack template,
                                  double unitPrice, long durationSeconds);

    /** Removes one override belonging to the given plugin. */
    boolean removeTemporarySellPrice(Plugin owner, String overrideId);

    /** Removes all overrides belonging to the given plugin. */
    int removeTemporarySellPrices(Plugin owner);
}
