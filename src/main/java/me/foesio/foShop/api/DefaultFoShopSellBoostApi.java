package me.foesio.foShop.api;

import me.foesio.foShop.booster.SellBoosterScope;
import me.foesio.foShop.booster.SellBoosterService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class DefaultFoShopSellBoostApi implements FoShopSellBoostApi {
    private final SellBoosterService sellBoosterService;

    public DefaultFoShopSellBoostApi(SellBoosterService sellBoosterService) {
        this.sellBoosterService = sellBoosterService;
    }

    @Override
    public boolean setBoost(Plugin owner, String boostId, FoShopSellBoostScope scope, String scopeId, double bonusMultiplier) {
        if (owner == null) {
            return false;
        }
        return sellBoosterService.setExternalBoostBonus(owner.getName(), boostId, toInternal(scope), scopeId, bonusMultiplier, 0L);
    }

    @Override
    public boolean setMultiplierBoost(Plugin owner, String boostId, FoShopSellBoostScope scope, String scopeId, double multiplier, long durationSeconds) {
        if (owner == null) {
            return false;
        }
        return sellBoosterService.setExternalBoostMultiplier(owner.getName(), boostId, toInternal(scope), scopeId, multiplier, durationSeconds);
    }

    @Override
    public boolean removeBoost(Plugin owner, String boostId) {
        if (owner == null) {
            return false;
        }
        return sellBoosterService.removeExternalBoost(owner.getName(), boostId);
    }

    @Override
    public int removeBoosts(Plugin owner) {
        if (owner == null) {
            return 0;
        }
        return sellBoosterService.removeExternalBoosts(owner.getName());
    }

    @Override
    public boolean hasBoost(Plugin owner, String boostId) {
        if (owner == null) {
            return false;
        }
        return sellBoosterService.hasExternalBoost(owner.getName(), boostId);
    }

    @Override
    public double getEffectiveSellMultiplier(Player player) {
        return sellBoosterService.multiplier(player);
    }

    private SellBoosterScope toInternal(FoShopSellBoostScope scope) {
        if (scope == null) {
            return SellBoosterScope.GLOBAL;
        }
        return switch (scope) {
            case PERSONAL -> SellBoosterScope.PLAYER;
            case TEAM -> SellBoosterScope.TEAM;
            case GLOBAL -> SellBoosterScope.GLOBAL;
        };
    }
}
