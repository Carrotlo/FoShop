package me.foesio.foShop.api;

import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface FoShopSellBoostApi {
    /**
     * Adds or updates a full-multiplier sell boost for one player.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal/update
     * @param playerId player that should receive the boost
     * @param multiplier full multiplier, so 1.25 means 1.25x
     * @return true when the boost was accepted
     */
    default boolean setPlayerMultiplierBoost(Plugin owner, String boostId, UUID playerId, double multiplier) {
        if (playerId == null) {
            return false;
        }
        return setMultiplierBoost(owner, boostId, FoShopSellBoostScope.PERSONAL, playerId.toString(), multiplier, 0L);
    }

    /**
     * Adds or updates a timed full-multiplier sell boost for one player.
     *
     * @param durationSeconds duration in seconds. 0 or lower means no automatic expiry
     */
    default boolean setPlayerMultiplierBoost(Plugin owner, String boostId, UUID playerId, double multiplier, long durationSeconds) {
        if (playerId == null) {
            return false;
        }
        return setMultiplierBoost(owner, boostId, FoShopSellBoostScope.PERSONAL, playerId.toString(), multiplier, durationSeconds);
    }

    /**
     * Adds or updates a full-multiplier sell boost for a FoTeams team.
     * This only applies while FoShop team boosters are enabled and FoTeams is available.
     *
     * @param multiplier full multiplier, so 1.50 means 1.5x
     */
    default boolean setTeamMultiplierBoost(Plugin owner, String boostId, String teamId, double multiplier) {
        return setMultiplierBoost(owner, boostId, FoShopSellBoostScope.TEAM, teamId, multiplier, 0L);
    }

    /**
     * Adds or updates a timed full-multiplier sell boost for a FoTeams team.
     * This only applies while FoShop team boosters are enabled and FoTeams is available.
     *
     * @param durationSeconds duration in seconds. 0 or lower means no automatic expiry
     */
    default boolean setTeamMultiplierBoost(Plugin owner, String boostId, String teamId, double multiplier, long durationSeconds) {
        return setMultiplierBoost(owner, boostId, FoShopSellBoostScope.TEAM, teamId, multiplier, durationSeconds);
    }

    /**
     * Adds or updates a full-multiplier sell boost for all players.
     *
     * @param multiplier full multiplier, so 2.0 means 2x
     */
    default boolean setGlobalMultiplierBoost(Plugin owner, String boostId, double multiplier) {
        return setMultiplierBoost(owner, boostId, FoShopSellBoostScope.GLOBAL, "", multiplier, 0L);
    }

    /**
     * Adds or updates a timed full-multiplier sell boost for all players.
     *
     * @param durationSeconds duration in seconds. 0 or lower means no automatic expiry
     */
    default boolean setGlobalMultiplierBoost(Plugin owner, String boostId, double multiplier, long durationSeconds) {
        return setMultiplierBoost(owner, boostId, FoShopSellBoostScope.GLOBAL, "", multiplier, durationSeconds);
    }

    /**
     * Adds or updates an external sell boost for one player.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param playerId player that should receive the boost
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setPlayerSellBoost(Plugin owner, String boostId, UUID playerId, double bonusMultiplier) {
        if (playerId == null) {
            return false;
        }
        return setBoost(owner, boostId, FoShopSellBoostScope.PERSONAL, playerId.toString(), bonusMultiplier);
    }

    /**
     * Adds or updates an external team sell boost.
     * This only applies while FoShop team boosters are enabled and FoTeams is available.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param teamId FoTeams team id that should receive the boost
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setTeamSellBoost(Plugin owner, String boostId, String teamId, double bonusMultiplier) {
        return setBoost(owner, boostId, FoShopSellBoostScope.TEAM, teamId, bonusMultiplier);
    }

    /**
     * Adds or updates an external sell boost for all players.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setGlobalSellBoost(Plugin owner, String boostId, double bonusMultiplier) {
        return setBoost(owner, boostId, FoShopSellBoostScope.GLOBAL, "", bonusMultiplier);
    }

    /**
     * Adds or updates an external sell boost.
     *
     * <p>These legacy external sell boosts are additive when FoShop booster stacking is enabled. When stacking is
     * disabled, FoShop uses the highest applicable sell booster multiplier. They do not count as normal active boosters
     * and are removed by the owning plugin through removeBoost or removeBoosts.</p>
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param scope who should receive the boost
     * @param scopeId player UUID for PERSONAL, FoTeams team id for TEAM, ignored for GLOBAL
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    boolean setBoost(Plugin owner, String boostId, FoShopSellBoostScope scope, String scopeId, double bonusMultiplier);

    /**
     * Adds or updates a full-multiplier external sell boost.
     *
     * <p>This is the preferred API for new integrations. A multiplier of 1.25 gives 1.25x sell prices.
     * When FoShop booster stacking is enabled, full-multiplier API boosts multiply with other full-multiplier boosters.
     * The owning plugin can call this again with the same boostId to update the boost.</p>
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal/update
     * @param scope who should receive the boost
     * @param scopeId player UUID for PERSONAL, FoTeams team id for TEAM, ignored for GLOBAL
     * @param multiplier full multiplier, so 2.0 means 2x
     * @param durationSeconds duration in seconds. 0 or lower means no automatic expiry
     * @return true when the boost was accepted
     */
    default boolean setMultiplierBoost(Plugin owner, String boostId, FoShopSellBoostScope scope, String scopeId, double multiplier, long durationSeconds) {
        if (durationSeconds > 0L || multiplier <= 1D || !Double.isFinite(multiplier)) {
            return false;
        }
        return setBoost(owner, boostId, scope, scopeId, multiplier - 1D);
    }

    /**
     * Removes one boost owned by the given plugin.
     *
     * @param owner plugin that owns the boost
     * @param boostId id passed to any set method
     * @return true when a boost was removed
     */
    boolean removeBoost(Plugin owner, String boostId);

    /**
     * Removes all boosts owned by the given plugin.
     *
     * @param owner plugin whose boosts should be removed
     * @return amount of removed boosts
     */
    int removeBoosts(Plugin owner);

    /**
     * Checks whether this owner currently has a boost with this id.
     */
    default boolean hasBoost(Plugin owner, String boostId) {
        return false;
    }

    /**
     * Returns the effective FoShop sell multiplier for a player, including FoShop boosters and API boosts.
     */
    default double getEffectiveSellMultiplier(Player player) {
        return 1D;
    }
}
