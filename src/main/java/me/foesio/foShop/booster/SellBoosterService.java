package me.foesio.foShop.booster;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.hook.FoTeamsHook;
import me.foesio.foShop.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SellBoosterService {
    private static final long CLEANUP_PERIOD_TICKS = 20L * 60L;
    private static final long TEAM_LOOKUP_CACHE_MILLIS = 250L;
    private static final double MULTIPLIER_EPSILON = 0.000_000_1D;
    private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.##");

    private final FoShop plugin;
    private final FoTeamsHook foTeamsHook;
    private final Map<String, ActiveSellBooster> active = new LinkedHashMap<>();
    private final Map<ExternalBoostKey, ExternalBoost> externalBoosts = new HashMap<>();
    private final Map<UUID, CachedTeamInfo> teamInfoCache = new LinkedHashMap<>();
    private boolean cleanupLoopActive;
    private long cleanupLoopGeneration;
    private boolean enabled;
    private boolean stackBoosters;
    private boolean stackWithRotatingShop;
    private boolean teamBoostersEnabled;

    public SellBoosterService(FoShop plugin, FoTeamsHook foTeamsHook) {
        this.plugin = plugin;
        this.foTeamsHook = foTeamsHook;
    }

    public void start() {
        reload();
        startCleanupLoop();
    }

    public void shutdown() {
        stopCleanupLoop();
        save();
    }

    private void startCleanupLoop() {
        stopCleanupLoop();
        cleanupLoopActive = true;
        long generation = ++cleanupLoopGeneration;
        scheduleCleanupLoop(generation, 20L);
    }

    private void stopCleanupLoop() {
        cleanupLoopActive = false;
        cleanupLoopGeneration++;
    }

    private void scheduleCleanupLoop(long generation, long delayTicks) {
        plugin.getCore().scheduler().runGlobalLater(() -> {
            if (!cleanupLoopActive || generation != cleanupLoopGeneration) {
                return;
            }
            cleanup(true);
            if (cleanupLoopActive && generation == cleanupLoopGeneration) {
                scheduleCleanupLoop(generation, CLEANUP_PERIOD_TICKS);
            }
        }, delayTicks);
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("sell-boosters.enabled", true);
        stackBoosters = plugin.getConfig().getBoolean("sell-boosters.stack-boosters", false);
        stackWithRotatingShop = plugin.getConfig().getBoolean("sell-boosters.stack-with-rotating-shop", false);
        teamBoostersEnabled = plugin.getConfig().getBoolean("sell-boosters.team-boosters.enabled", true);
        load();
        teamInfoCache.clear();
        cleanup(false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isStackBoosters() {
        return stackBoosters;
    }

    public boolean isStackWithRotatingShop() {
        return stackWithRotatingShop;
    }

    public boolean isTeamBoostersEnabled() {
        return teamBoostersEnabled;
    }

    public boolean isFoTeamsAvailable() {
        return foTeamsHook.available();
    }

    public List<String> teamNames() {
        return foTeamsHook.teamNames();
    }

    public Optional<FoTeamsHook.TeamInfo> teamByInput(String input) {
        return foTeamsHook.teamByInput(input);
    }

    public double multiplier(Player player) {
        if (!enabled || (active.isEmpty() && externalBoosts.isEmpty())) {
            return 1D;
        }

        long now = System.currentTimeMillis();
        String playerId = player == null ? "" : player.getUniqueId().toString();
        FoTeamsHook.TeamInfo teamInfo = player == null || !needsTeamLookup() ? null : cachedTeamInfo(player, now);
        String teamId = teamInfo == null ? "" : teamInfo.id();

        double activeMultiplier = 1D;
        for (ActiveSellBooster booster : active.values()) {
            if (booster.scope() == SellBoosterScope.TEAM && !teamBoostersEnabled) {
                continue;
            }
            if (!applies(booster, playerId, teamId, now)) {
                continue;
            }
            activeMultiplier = stackBoosters ? activeMultiplier * booster.multiplier() : Math.max(activeMultiplier, booster.multiplier());
        }
        ExternalBoostSelection externalBoost = externalBoostSelection(playerId, teamId, now);
        return Math.max(1D, combineSellBoostMultipliers(activeMultiplier, externalBoost.multiplier()));
    }

    public EffectiveSellBoost effective(Player player) {
        if (!enabled) {
            return new EffectiveSellBoost(1D, List.of());
        }

        long now = System.currentTimeMillis();
        List<ActiveSellBooster> applicable = applicable(player, now);
        String playerId = player == null ? "" : player.getUniqueId().toString();
        FoTeamsHook.TeamInfo teamInfo = player == null || !needsTeamLookup() ? null : cachedTeamInfo(player, now);
        String teamId = teamInfo == null ? "" : teamInfo.id();
        ExternalBoostSelection externalBoost = externalBoostSelection(playerId, teamId, now);
        if (applicable.isEmpty() && externalBoost.multiplier() <= 1D) {
            return new EffectiveSellBoost(1D, List.of());
        }

        double activeMultiplier = activeMultiplier(applicable);
        double multiplier = combineSellBoostMultipliers(activeMultiplier, externalBoost.multiplier());
        List<ActiveSellBooster> visibleBoosters = applicable;
        long externalStartedAt = externalBoost.startedAtMillis();
        long externalExpiresAt = externalBoost.expiresAtMillis();
        if (!stackBoosters) {
            double activeMax = maxMultiplier(applicable);
            if (activeMax > externalBoost.multiplier() + MULTIPLIER_EPSILON) {
                visibleBoosters = boostersWithMultiplier(applicable, activeMax);
                externalStartedAt = 0L;
                externalExpiresAt = 0L;
            } else if (externalBoost.multiplier() > activeMax + MULTIPLIER_EPSILON) {
                visibleBoosters = List.of();
            } else if (activeMax > 1D) {
                visibleBoosters = boostersWithMultiplier(applicable, activeMax);
            }
        }
        return new EffectiveSellBoost(Math.max(1D, multiplier), visibleBoosters, externalStartedAt, externalExpiresAt);
    }

    public List<ActiveSellBooster> activeBoosters() {
        cleanup(false);
        return active.values().stream()
                .sorted(Comparator.comparingLong(ActiveSellBooster::expiresAtMillis))
                .toList();
    }

    public ActiveSellBooster start(SellBoosterScope scope, String ownerId, String ownerName, double multiplier, long durationSeconds) {
        long now = System.currentTimeMillis();
        String id = scope.key() + "-" + UUID.randomUUID().toString().substring(0, 8);
        ActiveSellBooster booster = new ActiveSellBooster(
                id,
                scope,
                ownerId == null ? "" : ownerId,
                ownerName == null ? "" : ownerName,
                Math.max(1D, multiplier),
                now,
                expiresAtMillis(now, durationSeconds)
        );
        active.put(id, booster);
        save();
        return booster;
    }

    public boolean remove(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean removed = active.remove(id) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public int clearAll() {
        int count = active.size();
        active.clear();
        save();
        return count;
    }

    public int clearScope(SellBoosterScope scope, String ownerId) {
        String normalizedOwner = ownerId == null ? "" : ownerId.trim().toLowerCase(Locale.ROOT);
        List<String> removed = active.values().stream()
                .filter(booster -> booster.scope() == scope)
                .filter(booster -> normalizedOwner.isBlank()
                        || booster.ownerId().equalsIgnoreCase(normalizedOwner)
                        || booster.ownerName().equalsIgnoreCase(normalizedOwner))
                .map(ActiveSellBooster::id)
                .toList();
        removed.forEach(active::remove);
        if (!removed.isEmpty()) {
            save();
        }
        return removed.size();
    }

    public boolean setExternalBoostBonus(String ownerName, String boostId, SellBoosterScope scope, String scopeId, double bonusMultiplier, long durationSeconds) {
        String normalizedOwner = normalizeExternalKey(ownerName);
        String normalizedBoostId = normalizeExternalKey(boostId);
        SellBoosterScope resolvedScope = scope == null ? SellBoosterScope.GLOBAL : scope;
        String normalizedScopeId = normalizeExternalScopeId(resolvedScope, scopeId);
        if (normalizedOwner.isBlank()
                || normalizedBoostId.isBlank()
                || (resolvedScope != SellBoosterScope.GLOBAL && normalizedScopeId.isBlank())
                || bonusMultiplier <= 0D
                || !Double.isFinite(bonusMultiplier)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = expiresAtMillis(now, durationSeconds);
        ExternalBoostKey key = new ExternalBoostKey(normalizedOwner, normalizedBoostId);
        ExternalBoost boost = new ExternalBoost(normalizedOwner, normalizedBoostId, resolvedScope, normalizedScopeId, bonusMultiplier, true, now, expiresAt);
        ExternalBoost previous = externalBoosts.put(key, boost);
        if (!boost.equals(previous)) {
            teamInfoCache.clear();
        }
        return true;
    }

    public boolean setExternalBoostMultiplier(String ownerName, String boostId, SellBoosterScope scope, String scopeId, double multiplier, long durationSeconds) {
        if (multiplier <= 1D || !Double.isFinite(multiplier)) {
            return false;
        }
        String normalizedOwner = normalizeExternalKey(ownerName);
        String normalizedBoostId = normalizeExternalKey(boostId);
        SellBoosterScope resolvedScope = scope == null ? SellBoosterScope.GLOBAL : scope;
        String normalizedScopeId = normalizeExternalScopeId(resolvedScope, scopeId);
        if (normalizedOwner.isBlank()
                || normalizedBoostId.isBlank()
                || (resolvedScope != SellBoosterScope.GLOBAL && normalizedScopeId.isBlank())) {
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = expiresAtMillis(now, durationSeconds);
        ExternalBoostKey key = new ExternalBoostKey(normalizedOwner, normalizedBoostId);
        ExternalBoost boost = new ExternalBoost(normalizedOwner, normalizedBoostId, resolvedScope, normalizedScopeId, multiplier - 1D, false, now, expiresAt);
        ExternalBoost previous = externalBoosts.put(key, boost);
        if (!boost.equals(previous)) {
            teamInfoCache.clear();
        }
        return true;
    }

    public boolean removeExternalBoost(String ownerName, String boostId) {
        String normalizedOwner = normalizeExternalKey(ownerName);
        String normalizedBoostId = normalizeExternalKey(boostId);
        if (normalizedOwner.isBlank() || normalizedBoostId.isBlank()) {
            return false;
        }
        ExternalBoost removed = externalBoosts.remove(new ExternalBoostKey(normalizedOwner, normalizedBoostId));
        if (removed != null) {
            teamInfoCache.clear();
            return true;
        }
        return false;
    }

    public int removeExternalBoosts(String ownerName) {
        String normalizedOwner = normalizeExternalKey(ownerName);
        if (normalizedOwner.isBlank()) {
            return 0;
        }
        List<ExternalBoostKey> matches = externalBoosts.keySet().stream()
                .filter(key -> key.ownerName().equals(normalizedOwner))
                .toList();
        for (ExternalBoostKey match : matches) {
            externalBoosts.remove(match);
        }
        if (!matches.isEmpty()) {
            teamInfoCache.clear();
        }
        return matches.size();
    }

    public boolean hasExternalBoost(String ownerName, String boostId) {
        cleanup(false);
        String normalizedOwner = normalizeExternalKey(ownerName);
        String normalizedBoostId = normalizeExternalKey(boostId);
        if (normalizedOwner.isBlank() || normalizedBoostId.isBlank()) {
            return false;
        }
        return externalBoosts.containsKey(new ExternalBoostKey(normalizedOwner, normalizedBoostId));
    }

    public String formatMultiplier(double multiplier) {
        return MULTIPLIER_FORMAT.format(multiplier);
    }

    public String formatRemaining(ActiveSellBooster booster) {
        if (booster.expiresAtMillis() <= 0L) {
            return "Active";
        }
        return DurationUtil.format(booster.remainingSeconds(System.currentTimeMillis()));
    }

    private List<ActiveSellBooster> applicable(Player player, long now) {
        if (!enabled) {
            return List.of();
        }

        String playerId = player == null ? "" : player.getUniqueId().toString();
        FoTeamsHook.TeamInfo teamInfo = player == null || !needsTeamLookup() ? null : cachedTeamInfo(player, now);
        String teamId = teamInfo == null ? "" : teamInfo.id();

        List<ActiveSellBooster> applicable = new ArrayList<>();
        for (ActiveSellBooster booster : active.values()) {
            if (booster.scope() == SellBoosterScope.TEAM && !teamBoostersEnabled) {
                continue;
            }
            if (booster.expired(now)) {
                continue;
            }
            if (applies(booster, playerId, teamId, now)) {
                applicable.add(booster);
            }
        }
        return applicable;
    }

    private boolean applies(ActiveSellBooster booster, String playerId, String teamId, long now) {
        if (booster.expired(now)) {
            return false;
        }
        return booster.scope() == SellBoosterScope.GLOBAL
                || (booster.scope() == SellBoosterScope.PLAYER && !playerId.isBlank() && booster.ownerId().equalsIgnoreCase(playerId))
                || (booster.scope() == SellBoosterScope.TEAM && !teamId.isBlank() && booster.ownerId().equalsIgnoreCase(teamId));
    }

    private boolean needsTeamLookup() {
        if (!teamBoostersEnabled) {
            return false;
        }

        for (ActiveSellBooster booster : active.values()) {
            if (booster.scope() == SellBoosterScope.TEAM) {
                return true;
            }
        }

        long now = System.currentTimeMillis();
        for (ExternalBoost boost : externalBoosts.values()) {
            if (!boost.expired(now) && boost.scope() == SellBoosterScope.TEAM) {
                return true;
            }
        }
        return false;
    }

    private ExternalBoostSelection externalBoostSelection(String playerId, String teamId, long now) {
        double multiplier = 1D;
        double additiveBonus = 0D;
        ExternalBoost selectedWindow = null;
        for (ExternalBoost boost : externalBoosts.values()) {
            if (boost.expired(now) || !appliesExternalBoost(boost, playerId, teamId)) {
                continue;
            }

            double boostMultiplier = 1D + boost.bonusMultiplier();
            if (stackBoosters) {
                if (boost.additive()) {
                    additiveBonus += boost.bonusMultiplier();
                } else {
                    multiplier *= boostMultiplier;
                }
                selectedWindow = earlierExpiring(selectedWindow, boost);
                continue;
            }

            if (boostMultiplier > multiplier + MULTIPLIER_EPSILON) {
                multiplier = boostMultiplier;
                selectedWindow = boost;
            } else if (sameMultiplier(boostMultiplier, multiplier)) {
                selectedWindow = preferredNonStackingWindow(selectedWindow, boost);
            }
        }
        double combinedMultiplier = multiplier * (1D + additiveBonus);
        if (selectedWindow == null || selectedWindow.expiresAtMillis() <= 0L) {
            return new ExternalBoostSelection(Math.max(1D, combinedMultiplier), 0L, 0L);
        }
        return new ExternalBoostSelection(Math.max(1D, combinedMultiplier), selectedWindow.startedAtMillis(), selectedWindow.expiresAtMillis());
    }

    private double combineSellBoostMultipliers(double activeMultiplier, double externalMultiplier) {
        return stackBoosters
                ? activeMultiplier * externalMultiplier
                : Math.max(activeMultiplier, externalMultiplier);
    }

    private double activeMultiplier(List<ActiveSellBooster> boosters) {
        double multiplier = 1D;
        for (ActiveSellBooster booster : boosters) {
            multiplier = stackBoosters ? multiplier * booster.multiplier() : Math.max(multiplier, booster.multiplier());
        }
        return multiplier;
    }

    private double maxMultiplier(List<ActiveSellBooster> boosters) {
        double multiplier = 1D;
        for (ActiveSellBooster booster : boosters) {
            multiplier = Math.max(multiplier, booster.multiplier());
        }
        return multiplier;
    }

    private List<ActiveSellBooster> boostersWithMultiplier(List<ActiveSellBooster> boosters, double multiplier) {
        List<ActiveSellBooster> matching = new ArrayList<>();
        for (ActiveSellBooster booster : boosters) {
            if (sameMultiplier(booster.multiplier(), multiplier)) {
                matching.add(booster);
            }
        }
        return List.copyOf(matching);
    }

    private ExternalBoost earlierExpiring(ExternalBoost current, ExternalBoost candidate) {
        if (candidate.expiresAtMillis() <= 0L) {
            return current;
        }
        if (current == null || current.expiresAtMillis() <= 0L || candidate.expiresAtMillis() < current.expiresAtMillis()) {
            return candidate;
        }
        return current;
    }

    private ExternalBoost preferredNonStackingWindow(ExternalBoost current, ExternalBoost candidate) {
        if (current == null) {
            return candidate;
        }
        if (current.expiresAtMillis() <= 0L || candidate.expiresAtMillis() <= 0L) {
            return current.expiresAtMillis() <= 0L ? current : candidate;
        }
        return candidate.expiresAtMillis() < current.expiresAtMillis() ? candidate : current;
    }

    private boolean sameMultiplier(double left, double right) {
        return Math.abs(left - right) <= MULTIPLIER_EPSILON;
    }

    private long expiresAtMillis(long nowMillis, long durationSeconds) {
        if (durationSeconds <= 0L) {
            return 0L;
        }
        long safeSeconds = Math.max(1L, durationSeconds);
        if (safeSeconds > (Long.MAX_VALUE - nowMillis) / 1000L) {
            return Long.MAX_VALUE;
        }
        return nowMillis + safeSeconds * 1000L;
    }

    private boolean appliesExternalBoost(ExternalBoost boost, String playerId, String teamId) {
        return switch (boost.scope()) {
            case GLOBAL -> true;
            case PLAYER -> !playerId.isBlank() && boost.scopeId().equalsIgnoreCase(playerId);
            case TEAM -> teamBoostersEnabled && !teamId.isBlank() && boost.scopeId().equalsIgnoreCase(teamId);
        };
    }

    private String normalizeExternalKey(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeExternalScopeId(SellBoosterScope scope, String scopeId) {
        if (scope == SellBoosterScope.GLOBAL) {
            return "";
        }
        if (scopeId == null || scopeId.isBlank()) {
            return "";
        }
        String normalized = scopeId.trim();
        if (scope == SellBoosterScope.PLAYER) {
            try {
                return UUID.fromString(normalized).toString();
            } catch (IllegalArgumentException exception) {
                return "";
            }
        }
        return normalized;
    }

    private void cleanup(boolean persist) {
        long now = System.currentTimeMillis();
        List<String> expired = active.values().stream()
                .filter(booster -> booster.expired(now))
                .map(ActiveSellBooster::id)
                .toList();
        expired.forEach(active::remove);
        List<ExternalBoostKey> expiredExternal = externalBoosts.entrySet().stream()
                .filter(entry -> entry.getValue().expired(now))
                .map(Map.Entry::getKey)
                .toList();
        expiredExternal.forEach(externalBoosts::remove);
        if (persist && !expired.isEmpty()) {
            save();
        }
        if (!expiredExternal.isEmpty()) {
            teamInfoCache.clear();
        } else {
            teamInfoCache.entrySet().removeIf(entry -> entry.getValue().expired(now));
        }
    }

    private FoTeamsHook.TeamInfo cachedTeamInfo(Player player, long now) {
        UUID playerId = player.getUniqueId();
        CachedTeamInfo cached = teamInfoCache.get(playerId);
        if (cached != null && !cached.expired(now)) {
            return cached.teamInfo();
        }

        FoTeamsHook.TeamInfo teamInfo = foTeamsHook.teamOf(player).orElse(null);
        teamInfoCache.put(playerId, new CachedTeamInfo(teamInfo, now + TEAM_LOOKUP_CACHE_MILLIS));
        return teamInfo;
    }

    private void load() {
        active.clear();
        File file = dataFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("active");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            String path = id + ".";
            Optional<SellBoosterScope> scope = SellBoosterScope.fromInput(root.getString(path + "scope", ""));
            if (scope.isEmpty()) {
                continue;
            }
            double multiplier = root.getDouble(path + "multiplier", 1D);
            long startedAt = root.getLong(path + "started-at", System.currentTimeMillis());
            long expiresAt = root.getLong(path + "expires-at", 0L);
            if (multiplier <= 1D || (expiresAt > 0L && expiresAt <= System.currentTimeMillis())) {
                continue;
            }
            active.put(id, new ActiveSellBooster(
                    id,
                    scope.get(),
                    root.getString(path + "owner-id", ""),
                    root.getString(path + "owner-name", ""),
                    multiplier,
                    startedAt,
                    expiresAt
            ));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ActiveSellBooster booster : active.values()) {
            String path = "active." + booster.id() + ".";
            yaml.set(path + "scope", booster.scope().key());
            yaml.set(path + "owner-id", booster.ownerId());
            yaml.set(path + "owner-name", booster.ownerName());
            yaml.set(path + "multiplier", booster.multiplier());
            yaml.set(path + "started-at", booster.startedAtMillis());
            yaml.set(path + "expires-at", booster.expiresAtMillis());
        }

        File file = dataFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Failed creating FoShop data folder for sell boosters.");
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed saving sell boosters: " + exception.getMessage());
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "sell-boosters.yml");
    }

    public record EffectiveSellBoost(double multiplier, List<ActiveSellBooster> boosters, long externalStartedAtMillis, long externalExpiresAtMillis) {
        public EffectiveSellBoost(double multiplier, List<ActiveSellBooster> boosters) {
            this(multiplier, boosters, 0L, 0L);
        }
    }

    private record CachedTeamInfo(FoTeamsHook.TeamInfo teamInfo, long expiresAtMillis) {
        private boolean expired(long nowMillis) {
            return expiresAtMillis <= nowMillis;
        }
    }

    private record ExternalBoostKey(String ownerName, String boostId) {
    }

    private record ExternalBoostSelection(double multiplier, long startedAtMillis, long expiresAtMillis) {
    }

    private record ExternalBoost(
            String ownerName,
            String boostId,
            SellBoosterScope scope,
            String scopeId,
            double bonusMultiplier,
            boolean additive,
            long startedAtMillis,
            long expiresAtMillis
    ) {
        private boolean expired(long nowMillis) {
            return expiresAtMillis > 0L && expiresAtMillis <= nowMillis;
        }
    }
}
