package me.foesio.foShop.booster;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.DurationUtil;
import me.foesio.foShop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SellBoosterBossbarService {
    private final FoShop plugin;
    private final SellBoosterService sellBoosterService;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private boolean updateLoopActive;
    private long updateLoopGeneration;
    private boolean enabled;
    private String title;
    private BarColor color;
    private BarStyle style;
    private long updateTicks;

    public SellBoosterBossbarService(FoShop plugin, SellBoosterService sellBoosterService) {
        this.plugin = plugin;
        this.sellBoosterService = sellBoosterService;
    }

    public void start() {
        reload();
        if (enabled) {
            schedule();
        }
    }

    public void reload() {
        var config = plugin.getConfig();
        boolean requested = config.isSet("sell-boosters.bossbar.enabled")
                && config.getBoolean("sell-boosters.bossbar.enabled");
        String nextTitle = config.isSet("sell-boosters.bossbar.title")
                ? config.getString("sell-boosters.bossbar.title", "") : "";
        if (!requested || nextTitle.isBlank()) {
            enabled = false;
            stopSchedule();
            clear();
            return;
        }
        BarColor nextColor = config.isSet("sell-boosters.bossbar.color")
                ? barColor(config.getString("sell-boosters.bossbar.color")) : null;
        BarStyle nextStyle = config.isSet("sell-boosters.bossbar.style")
                ? barStyle(config.getString("sell-boosters.bossbar.style")) : null;
        if (nextColor == null || nextStyle == null) {
            plugin.getLogger().warning("Invalid bossbar color or style; previous bossbar settings remain active.");
            return;
        }
        enabled = true;
        title = nextTitle;
        color = nextColor;
        style = nextStyle;
        updateTicks = Math.max(5L, config.getLong("sell-boosters.bossbar.update-ticks", 20L));
        if (updateLoopActive) {
            schedule();
        }
        updateAll();
    }

    public void shutdown() {
        stopSchedule();
        clear();
    }

    private void schedule() {
        stopSchedule();
        updateLoopActive = true;
        long generation = ++updateLoopGeneration;
        scheduleNextUpdate(generation, updateTicks);
    }

    private void stopSchedule() {
        updateLoopActive = false;
        updateLoopGeneration++;
    }

    private void scheduleNextUpdate(long generation, long delayTicks) {
        plugin.getCore().scheduler().runGlobalLater(() -> {
            if (!updateLoopActive || generation != updateLoopGeneration) {
                return;
            }
            updateAll();
            if (updateLoopActive && generation == updateLoopGeneration && enabled) {
                scheduleNextUpdate(generation, updateTicks);
            }
        }, delayTicks);
    }

    private void updateAll() {
        if (!enabled || !plugin.isEnabled()) {
            clear();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
        var iterator = bars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BossBar> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                entry.getValue().removeAll();
                iterator.remove();
            }
        }
    }

    private void update(Player player) {
        SellBoosterService.EffectiveSellBoost boost = sellBoosterService.effective(player);
        if (boost.multiplier() <= 1D) {
            remove(player.getUniqueId());
            return;
        }

        ActiveSellBooster nextExpiring = nextExpiring(boost);
        String renderedTitle = renderTitle(boost, nextExpiring);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(renderedTitle, color, style);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        }

        bar.setTitle(renderedTitle);
        bar.setColor(color);
        bar.setStyle(style);
        bar.setProgress(progress(boost, nextExpiring));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private String renderTitle(SellBoosterService.EffectiveSellBoost boost, ActiveSellBooster nextExpiring) {
        return Text.colorize(title
                .replace("{multiplier}", sellBoosterService.formatMultiplier(boost.multiplier()))
                .replace("{time}", time(boost, nextExpiring))
                .replace("{count}", String.valueOf(boost.boosters().size())));
    }

    private String time(SellBoosterService.EffectiveSellBoost boost, ActiveSellBooster booster) {
        long expiresAt = nextExpiresAtMillis(boost, booster);
        if (expiresAt <= 0L) {
            return plugin.renderConfigured("duration.active");
        }
        return DurationUtil.formatConfigured(
                Math.max(0L, (expiresAt - System.currentTimeMillis() + 999L) / 1000L),
                plugin::renderConfigured);
    }

    private double progress(SellBoosterService.EffectiveSellBoost boost, ActiveSellBooster booster) {
        long externalExpiresAt = boost.externalExpiresAtMillis();
        if (externalExpiresAt > 0L && (booster == null || externalExpiresAt < booster.expiresAtMillis())) {
            long total = Math.max(1L, externalExpiresAt - boost.externalStartedAtMillis());
            long remaining = Math.max(0L, externalExpiresAt - System.currentTimeMillis());
            return Math.max(0D, Math.min(1D, remaining / (double) total));
        }
        if (booster == null) {
            return 1D;
        }
        long total = Math.max(1L, booster.expiresAtMillis() - booster.startedAtMillis());
        long remaining = Math.max(0L, booster.expiresAtMillis() - System.currentTimeMillis());
        return Math.max(0D, Math.min(1D, remaining / (double) total));
    }

    private long nextExpiresAtMillis(SellBoosterService.EffectiveSellBoost boost, ActiveSellBooster booster) {
        long expiresAt = booster == null ? 0L : booster.expiresAtMillis();
        long externalExpiresAt = boost.externalExpiresAtMillis();
        if (externalExpiresAt > 0L && (expiresAt == 0L || externalExpiresAt < expiresAt)) {
            expiresAt = externalExpiresAt;
        }
        return expiresAt;
    }

    private ActiveSellBooster nextExpiring(SellBoosterService.EffectiveSellBoost boost) {
        long now = System.currentTimeMillis();
        ActiveSellBooster next = null;
        for (ActiveSellBooster booster : boost.boosters()) {
            if (booster.expired(now)) {
                continue;
            }
            if (next == null || booster.expiresAtMillis() < next.expiresAtMillis()) {
                next = booster;
            }
        }
        return next;
    }

    private void remove(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void clear() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    private BarColor barColor(String input) {
        if (input == null) return null;
        try {
            return BarColor.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private BarStyle barStyle(String input) {
        if (input == null) return null;
        try {
            return BarStyle.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
