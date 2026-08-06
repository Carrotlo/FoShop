package me.foesio.foShop.rotating;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.shop.GlobalSellPriceService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class RotatingShopService {

    public static final List<Double> MULTIPLIERS = List.of(1.25D, 1.5D, 1.75D, 2D, 2.5D);
    private static final long DEFAULT_RESET_SECONDS = 86_400L;
    private static final long MIN_RESET_SECONDS = 60L;

    private final FoShop plugin;
    private final File stateFile;
    private List<RotatingEntry> entries = List.of();
    private long nextResetAt;
    private long resetGeneration;
    private boolean resetScheduled;

    public RotatingShopService(FoShop plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "rotating-shop.yml");
    }

    public void reload() {
        cancelTask();
        loadState();

        if (!isEnabled()) {
            return;
        }

        if (entries.isEmpty() || nextResetAt <= System.currentTimeMillis() || !entriesAreStillValid()) {
            resetNow();
            return;
        }

        scheduleNextReset();
    }

    public void shutdown() {
        cancelTask();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("rotating-shop.enabled", false);
    }

    public long resetIntervalSeconds() {
        return Math.max(MIN_RESET_SECONDS, plugin.getConfig().getLong("rotating-shop.reset-interval-seconds", DEFAULT_RESET_SECONDS));
    }

    public boolean sectionParticipates(String sectionId) {
        String path = "rotating-shop.sections." + sectionId;
        return !plugin.getConfig().contains(path) || plugin.getConfig().getBoolean(path, true);
    }

    public boolean itemParticipates(String sectionId, String itemId) {
        String path = "rotating-shop.items." + sectionId + "." + itemId;
        return !plugin.getConfig().contains(path) || plugin.getConfig().getBoolean(path, true);
    }

    public List<RotatingEntry> activeEntries() {
        return List.copyOf(entries);
    }

    public long nextResetAt() {
        return nextResetAt;
    }

    public long timeUntilResetMillis() {
        return Math.max(0L, nextResetAt - System.currentTimeMillis());
    }

    public Optional<ShopItem> findItem(RotatingEntry entry) {
        if (isGlobalEntry(entry)) {
            return Optional.empty();
        }
        return plugin.getShopManager().getSection(entry.sectionId())
                .flatMap(section -> section.items().stream()
                        .filter(item -> item.id().equalsIgnoreCase(entry.itemId()))
                        .findFirst());
    }

    public Optional<GlobalSellPriceService.GlobalSellPriceEntry> findGlobalEntry(RotatingEntry entry) {
        if (!isGlobalEntry(entry) || plugin.getGlobalSellPriceService() == null) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(entry.itemId());
        return plugin.getGlobalSellPriceService().entry(material);
    }

    public Optional<GlobalSellPriceService.GlobalPotionEntry> findGlobalPotionEntry(RotatingEntry entry) {
        if (!isGlobalEntry(entry) || plugin.getGlobalSellPriceService() == null) {
            return Optional.empty();
        }
        return plugin.getGlobalSellPriceService().potionEntry(entry.itemId());
    }

    public Optional<GlobalSellPriceService.GlobalEnchantmentEntry> findGlobalEnchantmentEntry(RotatingEntry entry) {
        if (!isGlobalEntry(entry) || plugin.getGlobalSellPriceService() == null) {
            return Optional.empty();
        }
        return plugin.getGlobalSellPriceService().enchantmentEntry(entry.itemId());
    }

    public double sellMultiplier(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.getType() == Material.AIR) {
            return 1D;
        }

        String globalItemId = plugin.getGlobalSellPriceService() == null ? stack.getType().name() : plugin.getGlobalSellPriceService().rotatingItemId(stack);
        for (RotatingEntry entry : entries) {
            if (isGlobalEntry(entry) && entry.itemId().equalsIgnoreCase(globalItemId)) {
                return entry.multiplier();
            }
            Optional<ShopItem> item = findItem(entry);
            if (item.isPresent() && matches(item.get(), stack)) {
                return entry.multiplier();
            }
        }
        return 1D;
    }

    public double sellMultiplier(String sectionId, String itemId) {
        if (!isEnabled() || sectionId == null || itemId == null) {
            return 1D;
        }

        for (RotatingEntry entry : entries) {
            if (entry.sectionId().equalsIgnoreCase(sectionId) && entry.itemId().equalsIgnoreCase(itemId)) {
                return entry.multiplier();
            }
        }
        return 1D;
    }

    public void resetNow() {
        List<RotationCandidate> candidates = eligibleCandidates();
        Collections.shuffle(candidates, ThreadLocalRandom.current());

        List<RotatingEntry> nextEntries = new ArrayList<>();
        int count = Math.min(MULTIPLIERS.size(), candidates.size());
        for (int index = 0; index < count; index++) {
            RotationCandidate candidate = candidates.get(index);
            nextEntries.add(new RotatingEntry(candidate.sectionId(), candidate.itemId(), MULTIPLIERS.get(index)));
        }

        entries = List.copyOf(nextEntries);
        nextResetAt = System.currentTimeMillis() + resetIntervalSeconds() * 1000L;
        saveState();
        scheduleNextReset();
    }

    private boolean entriesAreStillValid() {
        for (RotatingEntry entry : entries) {
            if (isGlobalEntry(entry)) {
                if (plugin.getGlobalSellPriceService() == null || !plugin.getGlobalSellPriceService().isEnabled()) {
                    return false;
                }
                Optional<GlobalSellPriceService.GlobalPotionEntry> potionEntry = plugin.getGlobalSellPriceService().potionEntry(entry.itemId());
                if (potionEntry.isPresent()) {
                    if (hasPlainPotionSellOffer() || !plugin.getGlobalSellPriceService().potionParticipatesInRotatingShop(potionEntry.get().potionType())) {
                        return false;
                    }
                    continue;
                }
                Optional<GlobalSellPriceService.GlobalEnchantmentEntry> enchantmentEntry = plugin.getGlobalSellPriceService().enchantmentEntry(entry.itemId());
                if (enchantmentEntry.isPresent()) {
                    if (hasPlainEnchantedBookSellOffer()
                            || !plugin.getGlobalSellPriceService().enchantmentParticipatesInRotatingShop(enchantmentEntry.get().enchantmentKey(), enchantmentEntry.get().level())) {
                        return false;
                    }
                    continue;
                }
                Material material = Material.matchMaterial(entry.itemId());
                if (plugin.getShopManager().hasPlainSellOffer(material)
                        || !plugin.getGlobalSellPriceService().participatesInRotatingShop(material)) {
                    return false;
                }
                continue;
            }
            Optional<ShopSection> section = plugin.getShopManager().getSection(entry.sectionId());
            if (section.isEmpty() || !section.get().enabled() || !sectionParticipates(section.get().id())) {
                return false;
            }
            Optional<ShopItem> item = findItem(entry);
            if (item.isEmpty() || !isEligible(item.get()) || !itemParticipates(entry.sectionId(), entry.itemId())) {
                return false;
            }
        }
        return true;
    }

    private List<RotationCandidate> eligibleCandidates() {
        List<RotationCandidate> candidates = new ArrayList<>();
        for (ShopSection section : plugin.getShopManager().getSectionsOrdered()) {
            if (!section.enabled() || !sectionParticipates(section.id())) {
                continue;
            }
            for (ShopItem item : section.items()) {
                if (isEligible(item) && itemParticipates(section.id(), item.id())) {
                    candidates.add(new RotationCandidate(section.id(), item.id()));
                }
            }
        }
        if (plugin.getGlobalSellPriceService() != null && plugin.getGlobalSellPriceService().isEnabled()) {
            for (GlobalSellPriceService.GlobalSellPriceEntry entry : plugin.getGlobalSellPriceService().entries()) {
                if (entry.enabled()
                        && entry.price() > 0D
                        && entry.rotatingShop()
                        && !plugin.getShopManager().hasPlainSellOffer(entry.material())) {
                    candidates.add(new RotationCandidate(GlobalSellPriceService.ROTATING_SECTION_ID, entry.material().name()));
                }
            }
            if (!hasPlainPotionSellOffer()) {
                for (GlobalSellPriceService.GlobalPotionEntry entry : plugin.getGlobalSellPriceService().potionEntries()) {
                    if (entry.enabled()
                            && entry.price() > 0D
                            && entry.rotatingShop()) {
                        candidates.add(new RotationCandidate(GlobalSellPriceService.ROTATING_SECTION_ID, GlobalSellPriceService.potionEntryId(entry.potionType())));
                    }
                }
            }
            if (!hasPlainEnchantedBookSellOffer()) {
                for (GlobalSellPriceService.GlobalEnchantmentEntry entry : plugin.getGlobalSellPriceService().enchantmentEntries()) {
                    if (entry.enabled()
                            && entry.price() > 0D
                            && entry.rotatingShop()) {
                        candidates.add(new RotationCandidate(GlobalSellPriceService.ROTATING_SECTION_ID,
                                GlobalSellPriceService.enchantmentEntryId(entry.enchantmentKey(), entry.level())));
                    }
                }
            }
        }
        return candidates;
    }

    private boolean isEligible(ShopItem item) {
        return item != null && item.canSell() && item.sellPrice() > 0D;
    }

    private boolean matches(ShopItem item, ItemStack stack) {
        if (item.material() == null || stack.getType() != item.material()) {
            return false;
        }

        ItemStack template = item.itemStack();
        if (template == null) {
            return true;
        }

        ItemStack probe = template.clone();
        probe.setAmount(1);
        ItemStack candidate = stack.clone();
        candidate.setAmount(1);
        return candidate.isSimilar(probe);
    }

    private boolean hasPlainPotionSellOffer() {
        return plugin.getShopManager().hasPlainSellOffer(Material.POTION)
                || plugin.getShopManager().hasPlainSellOffer(Material.SPLASH_POTION)
                || plugin.getShopManager().hasPlainSellOffer(Material.LINGERING_POTION)
                || plugin.getShopManager().hasPlainSellOffer(Material.TIPPED_ARROW);
    }

    private boolean hasPlainEnchantedBookSellOffer() {
        return plugin.getShopManager().hasPlainSellOffer(Material.ENCHANTED_BOOK);
    }

    private boolean isGlobalEntry(RotatingEntry entry) {
        return entry != null && GlobalSellPriceService.ROTATING_SECTION_ID.equalsIgnoreCase(entry.sectionId());
    }

    private void loadState() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        nextResetAt = yaml.getLong("next-reset-at", 0L);

        List<RotatingEntry> loaded = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("items")) {
            String sectionId = stringValue(raw.get("section"));
            String itemId = stringValue(raw.get("item"));
            double multiplier = doubleValue(raw.get("multiplier"), 1D);
            if (!sectionId.isBlank() && !itemId.isBlank() && multiplier > 1D) {
                loaded.add(new RotatingEntry(sectionId, itemId, multiplier));
            }
        }
        entries = List.copyOf(loaded);
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-reset-at", nextResetAt);

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (RotatingEntry entry : entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("section", entry.sectionId());
            item.put("item", entry.itemId());
            item.put("multiplier", entry.multiplier());
            serialized.add(item);
        }
        yaml.set("items", serialized);

        var result = plugin.getSafeYamlWriter().write(stateFile, yaml);
        if (!result.success()) {
            plugin.getLogger().warning("Failed saving rotating shop state: " + result.error());
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Failed saving rotating shop state: " + result.error());
            }
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim().toLowerCase(Locale.ROOT).replace("x", ""));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void scheduleNextReset() {
        cancelTask();
        if (!isEnabled()) {
            return;
        }

        long delayMillis = Math.max(1000L, nextResetAt - System.currentTimeMillis());
        long delayTicks = Math.max(20L, delayMillis / 50L);
        resetScheduled = true;
        long generation = ++resetGeneration;
        plugin.getCore().scheduler().runGlobalLater(() -> {
            if (!resetScheduled || generation != resetGeneration) {
                return;
            }
            resetScheduled = false;
            resetNow();
        }, delayTicks);
    }

    private void cancelTask() {
        resetScheduled = false;
        resetGeneration++;
    }

    private record RotationCandidate(String sectionId, String itemId) {
    }

    public record RotatingEntry(String sectionId, String itemId, double multiplier) {
    }
}
