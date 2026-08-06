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
    private static final String DEFAULT_TITLE = "&#03fc88{multiplier}x &#a7b8b0SELL BOOSTER &8- &#ffffff{time}";

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
        enabled = plugin.getConfig().getBoolean("sell-boosters.bossbar.enabled", true);
        title = plugin.getConfig().getString("sell-boosters.bossbar.title", DEFAULT_TITLE);
        color = barColor(plugin.getConfig().getString("sell-boosters.bossbar.color", "GREEN"));
        style = barStyle(plugin.getConfig().getString("sell-boosters.bossbar.style", "SEGMENTED_10"));
        updateTicks = Math.max(5L, plugin.getConfig().getLong("sell-boosters.bossbar.update-ticks", 20L));

        if (!enabled) {
            stopSchedule();
            clear();
            return;
        }
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
            return "Active";
        }
        return DurationUtil.format(Math.max(0L, (expiresAt - System.currentTimeMillis() + 999L) / 1000L));
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
        try {
            return BarColor.valueOf((input == null ? "GREEN" : input).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarColor.GREEN;
        }
    }

    private BarStyle barStyle(String input) {
        try {
            return BarStyle.valueOf((input == null ? "SEGMENTED_10" : input).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarStyle.SEGMENTED_10;
        }
    }
}
