package me.foesio.foShop.shop;

import me.foesio.core.config.ResourceFiles;
import me.foesio.foShop.FoShop;
import me.foesio.foShop.data.UserDataStore;
import me.foesio.foShop.io.SafeYamlWriter;
import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.model.ShopItemType;
import me.foesio.foShop.util.ItemKeys;
import me.foesio.foShop.util.VanillaItemNames;
import me.foesio.foShop.validation.ShopValidationService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class ShopManager {

    private final FoShop plugin;
    private final ShopValidationService validationService;
    private final SafeYamlWriter safeYamlWriter;
    private final UserDataStore userDataStore;

    private final Map<String, ShopSection> sections = new LinkedHashMap<>();
    private final Map<Material, Double> highestSellPrices = new HashMap<>();
    private final Map<Material, Double> highestPlainSellPrices = new HashMap<>();
    private final List<SellOffer> sellOffers = new ArrayList<>();
    private final Map<Material, List<SellOffer>> sellOffersByMaterial = new HashMap<>();
    private final Map<String, Integer> stockByItem = new HashMap<>();
    private final Map<UUID, Map<String, PurchaseLimitState>> purchaseLimits = new HashMap<>();
    private volatile Map<Material, List<SellOffer>> sellOffersByMaterialSnapshot = Map.of();
    private volatile long priceRevision;

    public ShopManager(FoShop plugin, ShopValidationService validationService, SafeYamlWriter safeYamlWriter, UserDataStore userDataStore) {
        this.plugin = plugin;
        this.validationService = validationService;
        this.safeYamlWriter = safeYamlWriter;
        this.userDataStore = userDataStore;
    }

    public ReloadResult reload() {
        sections.clear();
        highestSellPrices.clear();
        highestPlainSellPrices.clear();
        sellOffers.clear();
        sellOffersByMaterial.clear();
        stockByItem.clear();

        File shopFolder = getShopFolder();
        if (!shopFolder.exists() && !shopFolder.mkdirs()) {
            return new ReloadResult(0, 0, 0, List.of("Failed to create shops folder."));
        }

        ensureDefaultShop(shopFolder);
        int migratedEnchantmentItems = migrateLegacyEnchantmentItems(shopFolder);
        if (migratedEnchantmentItems > 0) {
            String message = "Migrated " + migratedEnchantmentItems + " legacy enchantment shop item(s) to copied item stacks.";
            plugin.getLogger().info(message);
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().info(message);
            }
        }
        int migratedItems = migrateVanillaNamedConvertedItems(shopFolder);
        if (migratedItems > 0) {
            String message = "Replaced " + migratedItems + " vanilla-named converted shop item(s) with vanilla item templates.";
            plugin.getLogger().info(message);
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().info(message);
            }
        }
        ReloadResult result = scanInternal(true);
        Map<Material, List<SellOffer>> snapshot = new HashMap<>();
        sellOffersByMaterial.forEach((material, offers) -> snapshot.put(material, List.copyOf(offers)));
        sellOffersByMaterialSnapshot = Map.copyOf(snapshot);
        priceRevision++;
        return result;
    }

    public ReloadResult scanValidation() {
        File shopFolder = getShopFolder();
        if (!shopFolder.exists() && !shopFolder.mkdirs()) {
            return new ReloadResult(0, 0, 0, List.of("Failed to create shops folder."));
        }

        return scanInternal(false);
    }

    private ReloadResult scanInternal(boolean apply) {
        File shopFolder = getShopFolder();
        File[] files = shopFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return new ReloadResult(0, 0, 0, List.of("No shop files found."));
        }

        List<File> sortedFiles = List.of(files).stream()
                .sorted(Comparator.comparing(File::getName))
                .toList();

        int loadedSections = 0;
        int skippedSections = 0;
        int skippedItems = 0;
        List<String> issues = new ArrayList<>();
        Set<String> sectionIds = new TreeSet<>();

        for (File file : sortedFiles) {
            ShopValidationService.SectionValidationResult validation = validationService.parseAndValidate(file);
            for (String issue : validation.issues()) {
                issues.add("[" + file.getName() + "] " + issue);
            }

            skippedItems += validation.skippedItems();

            if (!validation.validSection()) {
                skippedSections++;
                continue;
            }

            ShopSection section = validation.section();
            String idKey = section.id().toLowerCase(Locale.ROOT);
            if (!sectionIds.add(idKey)) {
                issues.add("[" + file.getName() + "] duplicate section id '" + section.id() + "' across shop files.");
                skippedSections++;
                continue;
            }

            loadedSections++;
            if (apply) {
                sections.put(section.id(), section);
                if (!section.enabled()) {
                    continue;
                }
                for (ShopItem item : section.items()) {
                    if (item.canSell()) {
                        highestSellPrices.merge(item.material(), item.sellPrice(), Math::max);
                        SellOffer offer = new SellOffer(section.id(), item.id(), item.material(), item.itemStack(), item.sellPrice());
                        sellOffers.add(offer);
                        sellOffersByMaterial.computeIfAbsent(item.material(), ignored -> new ArrayList<>()).add(offer);
                        if (!offer.hasTemplate()) {
                            highestPlainSellPrices.merge(item.material(), item.sellPrice(), Math::max);
                        }
                    }
                    if (item.tracksStock()) {
                        stockByItem.put(itemKey(section.id(), item.id()), item.stock());
                    }
                }
            }
        }

        return new ReloadResult(loadedSections, skippedSections, skippedItems, issues);
    }

    private void ensureDefaultShop(File shopFolder) {
        File defaultFile = new File(shopFolder, "blocks.yml");
        if (!defaultFile.exists()) {
            ResourceFiles.saveDefault(plugin, "shops/blocks.yml");
        }
    }

    private int migrateVanillaNamedConvertedItems(File shopFolder) {
        File[] files = shopFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        int migratedItems = 0;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items == null) {
                continue;
            }

            boolean changed = false;
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                if (itemSection == null) {
                    continue;
                }

                ItemStack itemStack = itemSection.getItemStack("item-stack");
                Material material = itemStack == null
                        ? ItemKeys.material(itemSection.getString("material")).orElse(null)
                        : itemStack.getType();
                if (material == null || material == Material.AIR) {
                    continue;
                }

                boolean itemChanged = false;
                String displayName = itemSection.getString("display-name");
                if (VanillaItemNames.isVanillaName(material, displayName)) {
                    itemSection.set("display-name", null);
                    itemChanged = true;
                }

                if (itemStack != null) {
                    ItemStack sanitized = itemStack.clone();
                    sanitized.setAmount(1);
                    boolean stackNameChanged = VanillaItemNames.clearVanillaDisplayName(sanitized, material);
                    if (VanillaItemNames.isVanillaEquivalentTemplate(sanitized, material) && !hasConfiguredCustomItemData(itemSection)) {
                        itemSection.set("item-stack", null);
                        itemChanged = true;
                    } else if (stackNameChanged) {
                        itemSection.set("item-stack", sanitized);
                        itemChanged = true;
                    }
                }

                if (itemChanged) {
                    changed = true;
                    migratedItems++;
                }
            }

            if (changed) {
                SafeYamlWriter.WriteResult writeResult = safeYamlWriter.write(file, yaml);
                if (!writeResult.success()) {
                    plugin.getLogger().warning("Failed to rewrite vanilla-named converted items in " + file.getName() + ": " + writeResult.error());
                }
            }
        }

        return migratedItems;
    }

    private int migrateLegacyEnchantmentItems(File shopFolder) {
        File[] files = shopFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        int migratedItems = 0;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items == null) {
                continue;
            }

            boolean changed = false;
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                if (itemSection == null || !isLegacyEnchantmentType(itemSection.getString("type"))) {
                    continue;
                }

                ItemStack stack = itemSection.getItemStack("item-stack");
                if (stack == null || stack.getType() == Material.AIR) {
                    stack = new ItemStack(Material.ENCHANTED_BOOK, 1);
                } else {
                    stack = stack.clone();
                    stack.setAmount(1);
                }

                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    String displayName = itemSection.getString("display-name");
                    if (displayName != null && !displayName.isBlank()) {
                        meta.setDisplayName(displayName);
                    }
                    List<String> lore = itemSection.getStringList("lore");
                    if (!lore.isEmpty()) {
                        meta.setLore(lore);
                    }
                    if (itemSection.contains("custom-model-data")) {
                        meta.setCustomModelData(itemSection.getInt("custom-model-data"));
                    }
                    addLegacyStoredEnchantments(meta, itemSection);
                    stack.setItemMeta(meta);
                }

                itemSection.set("type", "item");
                itemSection.set("item-stack", stack);
                itemSection.set("enchantment", null);
                itemSection.set("enchantment-level", null);
                itemSection.set("enchant", null);
                changed = true;
                migratedItems++;
            }

            if (changed) {
                SafeYamlWriter.WriteResult writeResult = safeYamlWriter.write(file, yaml);
                if (!writeResult.success()) {
                    plugin.getLogger().warning("Failed to migrate legacy enchantment items in " + file.getName() + ": " + writeResult.error());
                }
            }
        }

        return migratedItems;
    }

    private boolean isLegacyEnchantmentType(String rawType) {
        return rawType != null && (rawType.equalsIgnoreCase("enchantment") || rawType.equalsIgnoreCase("enchant"));
    }

    private void addLegacyStoredEnchantments(ItemMeta meta, ConfigurationSection itemSection) {
        if (!(meta instanceof EnchantmentStorageMeta storageMeta)) {
            return;
        }

        for (String path : List.of("enchants", "enchantments")) {
            ConfigurationSection enchantments = itemSection.getConfigurationSection(path);
            if (enchantments == null) {
                continue;
            }
            for (String key : enchantments.getKeys(false)) {
                Enchantment enchantment = ItemKeys.enchantment(key);
                if (enchantment != null) {
                    storageMeta.addStoredEnchant(enchantment, Math.max(1, enchantments.getInt(key, 1)), true);
                }
            }
        }

        String rawEnchantment = itemSection.getString("enchantment", itemSection.getString("enchant"));
        Enchantment enchantment = ItemKeys.enchantment(rawEnchantment);
        if (enchantment != null) {
            int level = Math.max(1, itemSection.getInt("enchantment-level", itemSection.getInt("level", 1)));
            storageMeta.addStoredEnchant(enchantment, level, true);
        }
    }

    private boolean hasConfiguredCustomItemData(ConfigurationSection itemSection) {
        ShopItemType type = ShopItemType.fromString(itemSection.getString("type", "item"));
        if (type != ShopItemType.ITEM) {
            return true;
        }
        if (!itemSection.getStringList("lore").isEmpty() || !itemSection.getStringList("enchants").isEmpty()) {
            return true;
        }
        String lore = itemSection.getString("lore");
        if (lore != null && !lore.isBlank()) {
            return true;
        }
        return itemSection.contains("custom-model-data")
                || itemSection.contains("enchants")
                || itemSection.contains("enchantments")
                || itemSection.contains("nbt")
                || itemSection.contains("raw-nbt");
    }

    public SaveResult saveSection(ShopSection section) {
        List<String> issues = validationService.validateSectionModel(section);
        String blockingIssue = validationService.firstBlockingIssue(issues);
        if (blockingIssue != null) {
            return SaveResult.failure(blockingIssue);
        }

        return saveSectionYaml(section.id(), toYaml(section));
    }

    public SaveResult saveSectionYaml(String fallbackId, YamlConfiguration yaml) {
        ShopValidationService.SectionValidationResult validation = validationService.validateSectionYaml(fallbackId, yaml);
        if (!validation.validSection()) {
            String issue = validationService.firstBlockingIssue(validation.issues());
            return SaveResult.failure(issue == null ? "Invalid shop section." : issue);
        }
        String blockingIssue = validationService.firstBlockingIssue(validation.issues());
        if (blockingIssue != null) {
            return SaveResult.failure(blockingIssue);
        }

        File target = new File(getShopFolder(), validation.section().id() + ".yml");
        SafeYamlWriter.WriteResult writeResult = safeYamlWriter.write(target, yaml);
        if (!writeResult.success()) {
            return SaveResult.failure("Write failed: " + writeResult.error());
        }

        return SaveResult.success(writeResult.backupFile());
    }

    public DeleteResult deleteSection(String sectionId) {
        if (sectionId == null || sectionId.isBlank()) {
            return DeleteResult.failure("Section id is empty.");
        }

        File target = getSectionFile(sectionId);
        if (!target.isFile()) {
            return DeleteResult.failure("Section file not found.");
        }

        try {
            Path backupDirectory = getShopFolder().toPath().resolve(".backup");
            Files.createDirectories(backupDirectory);
            Path backup = backupDirectory.resolve(target.getName() + "." + System.currentTimeMillis() + ".deleted.bak");
            Files.move(target.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            return DeleteResult.success(backup.toFile());
        } catch (Exception exception) {
            return DeleteResult.failure("Delete failed: " + exception.getMessage());
        }
    }

    private YamlConfiguration toYaml(ShopSection section) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", section.id());
        yaml.set("title", section.title());
        yaml.set("size", section.size());
        yaml.set("icon", section.icon().name());
        yaml.set("icon-item", section.iconItem());
        yaml.set("description", section.description());
        yaml.set("enabled", section.enabled());
        yaml.set("slot", section.slot());

        for (ShopItem item : section.items()) {
            String path = "items." + item.id() + ".";
            yaml.set(path + "type", item.type().name().toLowerCase(Locale.ROOT));
            yaml.set(path + "material", item.material().name());
            yaml.set(path + "page", item.page());
            yaml.set(path + "slot", item.slot());
            yaml.set(path + "amount", item.amount());
            yaml.set(path + "buy-price", item.buyPrice());
            yaml.set(path + "sell-price", item.sellPrice());
            yaml.set(path + "lore", item.lore());
            yaml.set(path + "display-name", item.displayName());
            yaml.set(path + "custom-model-data", item.customModelData());
            yaml.set(path + "enchants", item.enchants() == null || item.enchants().isEmpty() ? null : item.enchants());
            yaml.set(path + "stack-size", item.stackSize());
            yaml.set(path + "item-stack", item.itemStack());
            yaml.set(path + "permission-node", item.permission());
            yaml.set(path + "required-permission", item.requiredPermission());
            yaml.set(path + "commands", item.commands().isEmpty() ? null : item.commands());
            yaml.set(path + "enchantment", item.enchantment());
            yaml.set(path + "enchantment-level", item.enchantmentLevel() <= 1 ? null : item.enchantmentLevel());
            yaml.set(path + "stock", item.stock());
            yaml.set(path + "buy-limit", item.buyLimit());
            yaml.set(path + "limit-reset-seconds", item.limitResetSeconds());
            yaml.set(path + "nbt", item.rawNbt());
        }

        return yaml;
    }

    public File getShopFolder() {
        return new File(plugin.getDataFolder(), "shops");
    }

    public File getSectionFile(String sectionId) {
        return new File(getShopFolder(), sectionId + ".yml");
    }

    public Collection<ShopSection> getSectionsOrdered() {
        return sections.values().stream()
                .sorted(Comparator.comparingInt(ShopSection::slot))
                .toList();
    }

    public Optional<ShopSection> getSection(String id) {
        return Optional.ofNullable(sections.get(id));
    }

    public double getHighestSellPrice(Material material) {
        double shopPrice = highestSellPrices.getOrDefault(material, -1D);
        if (shopPrice > 0D) {
            return shopPrice;
        }
        if (plugin.getGlobalSellPriceService() != null && plugin.getGlobalSellPriceService().isEnabled()) {
            return plugin.getGlobalSellPriceService().price(material);
        }
        return -1D;
    }

    public double getSellPrice(ItemStack stack) {
        return getSellPrice(null, stack);
    }

    public double getSellPrice(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return -1D;
        }

        double boosterMultiplier = plugin.getSellBoosterService() == null
                ? 1D
                : plugin.getSellBoosterService().multiplier(player);
        boolean stackWithRotatingShop = plugin.getSellBoosterService() != null
                && plugin.getSellBoosterService().isStackWithRotatingShop();
        double itemModifierPrice = plugin.getGlobalSellPriceService() == null || !plugin.getGlobalSellPriceService().isEnabled()
                ? 0D
                : plugin.getGlobalSellPriceService().itemModifierPrice(stack);

        ItemStack candidate = null;
        double best = -1D;
        List<SellOffer> materialOffers = sellOffersByMaterialSnapshot.get(stack.getType());
        if (materialOffers != null && !materialOffers.isEmpty()) {
            for (SellOffer offer : materialOffers) {
                if (offer.hasTemplate()) {
                    if (candidate == null) {
                        candidate = stack.clone();
                        candidate.setAmount(1);
                    }
                    ItemStack probe = offer.comparisonTemplate();
                    if (!candidate.isSimilar(probe)) {
                        continue;
                    }
                } else if (stack.getType() != offer.material()) {
                    continue;
                }
                double multiplier = plugin.getRotatingShopService() == null
                        ? 1D
                        : plugin.getRotatingShopService().sellMultiplier(offer.sectionId(), offer.itemId());
                double combinedMultiplier = stackWithRotatingShop
                        ? multiplier * boosterMultiplier
                        : Math.max(multiplier, boosterMultiplier);
                double offerPrice = offer.hasTemplate() ? offer.price() : offer.price() + itemModifierPrice;
                best = Math.max(best, offerPrice * Math.max(1D, combinedMultiplier));
            }
        }

        if (best < 0D && plugin.getGlobalSellPriceService() != null && plugin.getGlobalSellPriceService().isEnabled()) {
            double globalPrice = plugin.getGlobalSellPriceService().price(stack);
            if (globalPrice > 0D) {
                double multiplier = plugin.getRotatingShopService() == null
                        ? 1D
                        : plugin.getRotatingShopService().sellMultiplier(stack);
                double combinedMultiplier = stackWithRotatingShop
                        ? multiplier * boosterMultiplier
                        : Math.max(multiplier, boosterMultiplier);
                best = globalPrice * Math.max(1D, combinedMultiplier);
            }
        }
        return best < 0D ? -1D : best;
    }

    public double getBaseSellPrice(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return -1D;
        }

        GlobalSellPriceService globalPrices = plugin.getGlobalSellPriceService();
        boolean globalEnabled = globalPrices != null && globalPrices.isEnabled();
        double itemModifierPrice = globalEnabled ? globalPrices.itemModifierPrice(stack) : 0D;
        ItemStack candidate = null;
        double best = -1D;

        List<SellOffer> materialOffers = sellOffersByMaterialSnapshot.get(stack.getType());
        if (materialOffers != null) {
            for (SellOffer offer : materialOffers) {
                if (offer.hasTemplate()) {
                    if (candidate == null) {
                        candidate = stack.clone();
                        candidate.setAmount(1);
                    }
                    if (!candidate.isSimilar(offer.comparisonTemplate())) {
                        continue;
                    }
                }
                double offerPrice = offer.hasTemplate() ? offer.price() : offer.price() + itemModifierPrice;
                best = Math.max(best, offerPrice);
            }
        }

        if (best < 0D && globalEnabled) {
            best = globalPrices.price(stack);
        }
        return best < 0D ? -1D : best;
    }

    public long priceRevision() {
        return priceRevision;
    }

    public Map<Material, Double> getHighestSellPrices() {
        return Map.copyOf(highestSellPrices);
    }

    public Map<Material, Double> getHighestPlainSellPrices() {
        return Map.copyOf(highestPlainSellPrices);
    }

    public boolean hasPlainSellOffer(Material material) {
        return highestPlainSellPrices.containsKey(material);
    }

    public boolean hasMatchingSellOffer(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        List<SellOffer> materialOffers = sellOffersByMaterialSnapshot.get(stack.getType());
        if (materialOffers == null) {
            return false;
        }

        ItemStack candidate = null;
        for (SellOffer offer : materialOffers) {
            if (!offer.hasTemplate()) {
                return true;
            }
            if (candidate == null) {
                candidate = stack.clone();
                candidate.setAmount(1);
            }
            if (candidate.isSimilar(offer.comparisonTemplate())) {
                return true;
            }
        }
        return false;
    }

    public double baseSellPrice(Material material) {
        if (material == null || material == Material.AIR) {
            return -1D;
        }
        double shopPrice = highestPlainSellPrices.getOrDefault(material, -1D);
        if (shopPrice > 0D) {
            return shopPrice;
        }
        return plugin.getGlobalSellPriceService() == null ? -1D : plugin.getGlobalSellPriceService().price(material);
    }

    public int getStock(String sectionId, String itemId) {
        return stockByItem.getOrDefault(itemKey(sectionId, itemId), -1);
    }

    public int getRemainingBuyLimit(UUID playerId, String sectionId, ShopItem item) {
        if (!item.hasBuyLimit()) {
            return -1;
        }
        String key = itemKey(sectionId, item.id());
        PurchaseLimitState state = getPurchaseLimitState(playerId, key);
        if (state == null || state.expired(item.limitResetSeconds())) {
            purchaseLimits.computeIfAbsent(playerId, ignored -> new HashMap<>()).remove(key);
            if (state != null && userDataStore != null) {
                userDataStore.deletePurchaseLimit(playerId, key);
            }
            return item.buyLimit();
        }
        return Math.max(0, item.buyLimit() - state.amount);
    }

    public void recordPurchase(UUID playerId, String sectionId, ShopItem item, int amount) {
        String key = itemKey(sectionId, item.id());
        if (item.tracksStock()) {
            stockByItem.computeIfPresent(key, (ignored, current) -> Math.max(0, current - amount));
        }

        if (item.hasBuyLimit()) {
            Map<String, PurchaseLimitState> playerLimits = purchaseLimits.computeIfAbsent(playerId, ignored -> new HashMap<>());
            PurchaseLimitState state = playerLimits.get(key);
            if (state == null || state.expired(item.limitResetSeconds())) {
                state = new PurchaseLimitState(0, System.currentTimeMillis());
                playerLimits.put(key, state);
            }
            state.amount += amount;
            if (userDataStore != null) {
                userDataStore.savePurchaseLimit(playerId, key, state.amount, state.startedAt);
            }
        }
    }

    private PurchaseLimitState getPurchaseLimitState(UUID playerId, String key) {
        Map<String, PurchaseLimitState> playerLimits = purchaseLimits.computeIfAbsent(playerId, ignored -> new HashMap<>());
        PurchaseLimitState cached = playerLimits.get(key);
        if (cached != null || userDataStore == null) {
            return cached;
        }

        return userDataStore.getPurchaseLimit(playerId, key)
                .map(record -> {
                    PurchaseLimitState loaded = new PurchaseLimitState(record.amount(), record.startedAt());
                    playerLimits.put(key, loaded);
                    return loaded;
                })
                .orElse(null);
    }

    private String itemKey(String sectionId, String itemId) {
        return sectionId.toLowerCase(Locale.ROOT) + ":" + itemId.toLowerCase(Locale.ROOT);
    }

    public record ReloadResult(int loadedSections, int skippedSections, int skippedItems, List<String> issues) {
    }

    public record SaveResult(boolean success, String error, File backupFile) {
        public static SaveResult success(File backupFile) {
            return new SaveResult(true, null, backupFile);
        }

        public static SaveResult failure(String reason) {
            return new SaveResult(false, reason, null);
        }
    }

    public record DeleteResult(boolean success, String error, File backupFile) {
        public static DeleteResult success(File backupFile) {
            return new DeleteResult(true, null, backupFile);
        }

        public static DeleteResult failure(String reason) {
            return new DeleteResult(false, reason, null);
        }
    }

    private static class PurchaseLimitState {
        private int amount;
        private final long startedAt;

        private PurchaseLimitState(int amount, long startedAt) {
            this.amount = amount;
            this.startedAt = startedAt;
        }

        private boolean expired(Integer resetSeconds) {
            return resetSeconds != null && resetSeconds > 0
                    && System.currentTimeMillis() - startedAt >= resetSeconds * 1000L;
        }
    }

    private record SellOffer(String sectionId, String itemId, Material material, ItemStack template, double price) {
        private SellOffer {
            if (template != null) {
                template = template.clone();
                template.setAmount(1);
            }
        }

        private boolean hasTemplate() {
            return template != null;
        }

        private ItemStack comparisonTemplate() {
            return template == null ? null : template.clone();
        }

        @Override
        public ItemStack template() {
            return comparisonTemplate();
        }
    }
}
