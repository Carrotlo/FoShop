package me.foesio.foShop.shop;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.ItemKeys;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Locale;

public final class GlobalSellPriceService {

    public static final String ROTATING_SECTION_ID = "__global__";
    public static final String POTION_ROTATING_PREFIX = "potion:";
    public static final String ENCHANTMENT_ITEM_PREFIX = "enchantment:";
    private static final double DEFAULT_PRICE = 1D;
    private static final double ARMOR_TRIM_PRICE_MULTIPLIER = 5D;
    private static final Map<String, Integer> DEFAULT_ENCHANTMENT_LEVELS = defaultEnchantmentLevels();
    private static final Map<String, PriceMigration> LEGACY_DEFAULT_PRICE_MIGRATIONS = Map.ofEntries(
            Map.entry("ACTIVATOR_RAIL", new PriceMigration(5.25D, 1.35D)),
            Map.entry("AMETHYST_CLUSTER", new PriceMigration(1.5D, 25D)),
            Map.entry("AMETHYST_SHARD", new PriceMigration(2.5D, 25D)),
            Map.entry("ANVIL", new PriceMigration(174.38D, 34.44D)),
            Map.entry("AXOLOTL_BUCKET", new PriceMigration(20D, 4D)),
            Map.entry("BUCKET", new PriceMigration(16.88D, 3.33D)),
            Map.entry("CAULDRON", new PriceMigration(39.38D, 7.77D)),
            Map.entry("CHEST_MINECART", new PriceMigration(36.25D, 13.55D)),
            Map.entry("CHIPPED_ANVIL", new PriceMigration(116.25D, 22.96D)),
            Map.entry("CHISELED_QUARTZ_BLOCK", new PriceMigration(12.5D, 37.5D)),
            Map.entry("COBBLESTONE", new PriceMigration(0.62D, 0.5D)),
            Map.entry("CRAFTING_TABLE", new PriceMigration(4D, 16D)),
            Map.entry("CREAKING_HEART", new PriceMigration(1.5D, 10D)),
            Map.entry("DAMAGED_ANVIL", new PriceMigration(58.12D, 11.48D)),
            Map.entry("DEEPSLATE_IRON_ORE", new PriceMigration(10D, 7.25D, 1.45D)),
            Map.entry("DETECTOR_RAIL", new PriceMigration(5.62D, 1.25D)),
            Map.entry("DIAMOND", new PriceMigration(81.25D, 200D)),
            Map.entry("DIAMOND_AXE", new PriceMigration(1.25D, 100D)),
            Map.entry("DIAMOND_BLOCK", new PriceMigration(731.25D, 1800D)),
            Map.entry("DIAMOND_BOOTS", new PriceMigration(1.25D, 100D)),
            Map.entry("DIAMOND_CHESTPLATE", new PriceMigration(2D, 100D)),
            Map.entry("DIAMOND_HELMET", new PriceMigration(1.5D, 100D)),
            Map.entry("DIAMOND_HOE", new PriceMigration(1D, 100D)),
            Map.entry("DIAMOND_LEGGINGS", new PriceMigration(1.75D, 100D)),
            Map.entry("DIAMOND_NAUTILUS_ARMOR", new PriceMigration(568.75D, 100D)),
            Map.entry("DIAMOND_PICKAXE", new PriceMigration(1.25D, 100D)),
            Map.entry("DIAMOND_SHOVEL", new PriceMigration(0.75D, 100D)),
            Map.entry("DIAMOND_SPEAR", new PriceMigration(1.5D, 300D)),
            Map.entry("DIAMOND_SWORD", new PriceMigration(1D, 100D)),
            Map.entry("ENCHANTING_TABLE", new PriceMigration(1.5D, 50D)),
            Map.entry("END_CRYSTAL", new PriceMigration(1.5D, 10D)),
            Map.entry("FURNACE_MINECART", new PriceMigration(35.62D, 9.55D)),
            Map.entry("HOPPER", new PriceMigration(36.25D, 13.55D)),
            Map.entry("HOPPER_MINECART", new PriceMigration(57.5D, 19.1D)),
            Map.entry("IRON_BARS", new PriceMigration(1.5D, 0.42D)),
            Map.entry("IRON_BLOCK", new PriceMigration(50.62D, 10D)),
            Map.entry("IRON_CHAIN", new PriceMigration(1.5D, 1.35D)),
            Map.entry("IRON_DOOR", new PriceMigration(5D, 2.22D)),
            Map.entry("IRON_HORSE_ARMOR", new PriceMigration(39.38D, 7.77D)),
            Map.entry("IRON_INGOT", new PriceMigration(5.62D, 1.11D)),
            Map.entry("IRON_NAUTILUS_ARMOR", new PriceMigration(39.38D, 7.77D)),
            Map.entry("IRON_NUGGET", new PriceMigration(0.62D, 0.12D)),
            Map.entry("IRON_ORE", new PriceMigration(10D, 6.88D, 1.38D)),
            Map.entry("IRON_TRAPDOOR", new PriceMigration(6.25D, 4.44D)),
            Map.entry("ITEM_FRAME", new PriceMigration(0.1D, 10D)),
            Map.entry("LARGE_AMETHYST_BUD", new PriceMigration(1.5D, 25D)),
            Map.entry("LAVA_BUCKET", new PriceMigration(18.12D, 3.75D)),
            Map.entry("MACE", new PriceMigration(1.5D, 560D)),
            Map.entry("MILK_BUCKET", new PriceMigration(5.62D, 3.33D)),
            Map.entry("MEDIUM_AMETHYST_BUD", new PriceMigration(1.5D, 25D)),
            Map.entry("MINECART", new PriceMigration(28.12D, 5.55D)),
            Map.entry("NAUTILUS_SHELL", new PriceMigration(15D, 100D)),
            Map.entry("NETHER_QUARTZ_ORE", new PriceMigration(4.38D, 13.14D)),
            Map.entry("NETHERITE_AXE", new PriceMigration(3244.25D, 1000D)),
            Map.entry("NETHERITE_BOOTS", new PriceMigration(4325D, 1000D)),
            Map.entry("NETHERITE_CHESTPLATE", new PriceMigration(8650D, 1000D)),
            Map.entry("NETHERITE_HELMET", new PriceMigration(5406.25D, 1000D)),
            Map.entry("NETHERITE_HOE", new PriceMigration(2163D, 1000D)),
            Map.entry("NETHERITE_LEGGINGS", new PriceMigration(7568.75D, 1000D)),
            Map.entry("NETHERITE_NAUTILUS_ARMOR", new PriceMigration(7568.75D, 1000D)),
            Map.entry("NETHERITE_PICKAXE", new PriceMigration(3244.25D, 1000D)),
            Map.entry("NETHERITE_SHOVEL", new PriceMigration(1081.5D, 1000D)),
            Map.entry("NETHERITE_SPEAR", new PriceMigration(1.5D, 1000D)),
            Map.entry("NETHERITE_SWORD", new PriceMigration(2163D, 1000D)),
            Map.entry("OAK_BOAT", new PriceMigration(5D, 20D)),
            Map.entry("OAK_BUTTON", new PriceMigration(1D, 4D)),
            Map.entry("OAK_CHEST_BOAT", new PriceMigration(13D, 52D)),
            Map.entry("OAK_DOOR", new PriceMigration(2D, 8D)),
            Map.entry("OAK_FENCE", new PriceMigration(1.67D, 6.67D)),
            Map.entry("OAK_FENCE_GATE", new PriceMigration(4D, 16D)),
            Map.entry("OAK_HANGING_SIGN", new PriceMigration(4.13D, 16.5D)),
            Map.entry("OAK_LOG", new PriceMigration(4D, 16D)),
            Map.entry("OAK_PLANKS", new PriceMigration(1D, 4D)),
            Map.entry("OAK_PRESSURE_PLATE", new PriceMigration(2D, 8D)),
            Map.entry("OAK_SIGN", new PriceMigration(2.17D, 8.67D)),
            Map.entry("OAK_SLAB", new PriceMigration(0.5D, 2D)),
            Map.entry("OAK_STAIRS", new PriceMigration(1.5D, 6D)),
            Map.entry("OAK_TRAPDOOR", new PriceMigration(3D, 12D)),
            Map.entry("OAK_WOOD", new PriceMigration(4D, 16D)),
            Map.entry("OMINOUS_TRIAL_KEY", new PriceMigration(1.5D, 25D)),
            Map.entry("PISTON", new PriceMigration(11.62D, 7.11D)),
            Map.entry("POWDER_SNOW_BUCKET", new PriceMigration(18.75D, 3.75D)),
            Map.entry("PUFFERFISH_BUCKET", new PriceMigration(20D, 4D)),
            Map.entry("QUARTZ", new PriceMigration(3.12D, 9.36D)),
            Map.entry("QUARTZ_BLOCK", new PriceMigration(0.25D, 0.75D)),
            Map.entry("QUARTZ_BRICKS", new PriceMigration(0.25D, 0.75D)),
            Map.entry("QUARTZ_PILLAR", new PriceMigration(0.25D, 0.75D)),
            Map.entry("QUARTZ_SLAB", new PriceMigration(1.56D, 4.68D)),
            Map.entry("QUARTZ_STAIRS", new PriceMigration(3.12D, 9.36D)),
            Map.entry("RAIL", new PriceMigration(2.25D, 0.45D)),
            Map.entry("RAW_IRON", new PriceMigration(5D, 1D)),
            Map.entry("RAW_IRON_BLOCK", new PriceMigration(45D, 9D)),
            Map.entry("SALMON_BUCKET", new PriceMigration(20D, 4D)),
            Map.entry("SEA_PICKLE", new PriceMigration(7.5D, 6.5D)),
            Map.entry("BLACK_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("BLUE_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("BROWN_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("CYAN_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("GRAY_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("GREEN_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("LIGHT_BLUE_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("LIGHT_GRAY_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("LIME_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("MAGENTA_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("ORANGE_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("PINK_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("PURPLE_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("RED_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("SHULKER_BOX", new PriceMigration(1.5D, 10D)),
            Map.entry("SHULKER_SHELL", new PriceMigration(50D, 5D)),
            Map.entry("SMALL_AMETHYST_BUD", new PriceMigration(1.5D, 25D)),
            Map.entry("SMOOTH_QUARTZ", new PriceMigration(0.25D, 0.75D)),
            Map.entry("SMOOTH_QUARTZ_SLAB", new PriceMigration(1.56D, 4.68D)),
            Map.entry("SMOOTH_QUARTZ_STAIRS", new PriceMigration(3.12D, 9.36D)),
            Map.entry("STRIPPED_OAK_LOG", new PriceMigration(4D, 16D)),
            Map.entry("STRIPPED_OAK_WOOD", new PriceMigration(4D, 16D)),
            Map.entry("WHITE_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("YELLOW_SHULKER_BOX", new PriceMigration(106.25D, 15D)),
            Map.entry("STICKY_PISTON", new PriceMigration(13.88D, 9.36D)),
            Map.entry("STONE", new PriceMigration(0.62D, 5D)),
            Map.entry("STONECUTTER", new PriceMigration(6.25D, 2.98D)),
            Map.entry("TADPOLE_BUCKET", new PriceMigration(20D, 4D)),
            Map.entry("TNT_MINECART", new PriceMigration(32.5D, 9.93D)),
            Map.entry("TRIAL_KEY", new PriceMigration(1.5D, 10D)),
            Map.entry("TRIPWIRE_HOOK", new PriceMigration(7.5D, 5.61D)),
            Map.entry("TROPICAL_FISH_BUCKET", new PriceMigration(20D, 4D)),
            Map.entry("WATER_BUCKET", new PriceMigration(17.5D, 3.33D))
    );

    private final FoShop plugin;
    private volatile Map<Material, GlobalSellPriceEntry> entries = Map.of();
    private volatile Map<String, Map<Integer, GlobalEnchantmentEntry>> enchantmentEntries = Map.of();
    private volatile Map<PotionType, GlobalPotionEntry> potionEntries = Map.of();
    private volatile long priceRevision;

    public GlobalSellPriceService(FoShop plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Map<Material, GlobalSellPriceEntry> loadedEntries = new EnumMap<>(Material.class);
        Map<String, Map<Integer, GlobalEnchantmentEntry>> loadedEnchantmentEntries = new TreeMap<>();
        Map<PotionType, GlobalPotionEntry> loadedPotionEntries = new EnumMap<>(PotionType.class);

        File file = file();
        ensureDefaultFile(file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = loadDefaultConfiguration();
        Map<Material, Double> shopDefaults = plugin.getShopManager() == null ? Map.of() : plugin.getShopManager().getHighestPlainSellPrices();
        boolean changed = !file.exists();

        if (yaml.getConfigurationSection("items") == null) {
            yaml.createSection("items");
            changed = true;
        }

        for (Material material : sellableMaterials()) {
            String path = "items." + material.name();
            String defaultPath = "items." + material.name();
            boolean normallyUnobtainable = isNormallyUnobtainableDefault(material);
            double fallbackPrice = normallyUnobtainable ? 0D : shopDefaults.getOrDefault(material, DEFAULT_PRICE);
            if (yaml.getConfigurationSection(path) == null) {
                yaml.createSection(path);
                changed = true;
            }
            if (!yaml.contains(path + ".enabled")) {
                yaml.set(path + ".enabled", defaults.getBoolean(defaultPath + ".enabled", !normallyUnobtainable));
                changed = true;
            }
            if (!yaml.contains(path + ".price")) {
                yaml.set(path + ".price", normalizedPrice(defaults.getDouble(defaultPath + ".price", fallbackPrice)));
                changed = true;
            }
            if (migrateLegacyDefaultPrice(yaml, path, material)) {
                changed = true;
            }
            if (!yaml.contains(path + ".rotating-shop")) {
                yaml.set(path + ".rotating-shop", defaults.getBoolean(defaultPath + ".rotating-shop", !normallyUnobtainable));
                changed = true;
            }

            boolean enabled = yaml.getBoolean(path + ".enabled", !normallyUnobtainable);
            double price = normalizedPrice(yaml.getDouble(path + ".price", fallbackPrice));
            boolean rotatingShop = yaml.getBoolean(path + ".rotating-shop", !normallyUnobtainable);
            loadedEntries.put(material, new GlobalSellPriceEntry(material, enabled, price, rotatingShop));
        }

        if (yaml.getConfigurationSection("enchantments") == null) {
            yaml.createSection("enchantments");
            changed = true;
        }

        for (Map.Entry<String, Integer> enchantment : DEFAULT_ENCHANTMENT_LEVELS.entrySet()) {
            String enchantmentKey = enchantment.getKey();
            int maxLevel = enchantment.getValue();
            for (int level = 1; level <= maxLevel; level++) {
                String path = enchantmentPath(enchantmentKey, level);
                String defaultPath = path;
                if (yaml.getConfigurationSection(path) == null) {
                    yaml.createSection(path);
                    changed = true;
                }
                if (!yaml.contains(path + ".enabled")) {
                    yaml.set(path + ".enabled", defaults.getBoolean(defaultPath + ".enabled", true));
                    changed = true;
                }
                if (!yaml.contains(path + ".price")) {
                    yaml.set(path + ".price", normalizedPrice(defaults.getDouble(defaultPath + ".price", defaultEnchantmentPrice(enchantmentKey, level, maxLevel))));
                    changed = true;
                }
                if (migrateLegacyDefaultEnchantmentPrice(yaml, path, enchantmentKey, level, maxLevel)) {
                    changed = true;
                }
                if (!yaml.contains(path + ".rotating-shop")) {
                    yaml.set(path + ".rotating-shop", defaults.getBoolean(defaultPath + ".rotating-shop", false));
                    changed = true;
                }

                boolean enabled = yaml.getBoolean(path + ".enabled", true);
                double price = normalizedPrice(yaml.getDouble(path + ".price", defaultEnchantmentPrice(enchantmentKey, level, maxLevel)));
                boolean rotatingShop = yaml.getBoolean(path + ".rotating-shop", false);
                loadedEnchantmentEntries.computeIfAbsent(enchantmentKey, ignored -> new TreeMap<>())
                        .put(level, new GlobalEnchantmentEntry(enchantmentKey, level, enabled, price, rotatingShop));
            }
        }

        if (yaml.getConfigurationSection("potions") == null) {
            yaml.createSection("potions");
            changed = true;
        }

        for (PotionType potionType : PotionType.values()) {
            String path = potionPath(potionType);
            String defaultPath = path;
            if (yaml.getConfigurationSection(path) == null) {
                yaml.createSection(path);
                changed = true;
            }
            if (!yaml.contains(path + ".enabled")) {
                yaml.set(path + ".enabled", defaults.getBoolean(defaultPath + ".enabled", true));
                changed = true;
            }
            if (!yaml.contains(path + ".price")) {
                yaml.set(path + ".price", normalizedPrice(defaults.getDouble(defaultPath + ".price", DEFAULT_PRICE)));
                changed = true;
            }
            if (!yaml.contains(path + ".rotating-shop")) {
                yaml.set(path + ".rotating-shop", defaults.getBoolean(defaultPath + ".rotating-shop", false));
                changed = true;
            }

            boolean enabled = yaml.getBoolean(path + ".enabled", true);
            double price = normalizedPrice(yaml.getDouble(path + ".price", DEFAULT_PRICE));
            boolean rotatingShop = yaml.getBoolean(path + ".rotating-shop", false);
            loadedPotionEntries.put(potionType, new GlobalPotionEntry(potionType, enabled, price, rotatingShop));
        }

        if (changed) {
            write(file, yaml);
        }

        Map<String, Map<Integer, GlobalEnchantmentEntry>> immutableEnchantments = new TreeMap<>();
        loadedEnchantmentEntries.forEach((key, value) -> immutableEnchantments.put(key, Map.copyOf(value)));
        entries = Map.copyOf(loadedEntries);
        enchantmentEntries = Map.copyOf(immutableEnchantments);
        potionEntries = Map.copyOf(loadedPotionEntries);
        priceRevision++;
        plugin.getPriceApi().catalogChanged();
    }

    public boolean isEnabled() {
        return plugin.getFoConfig().isGlobalSellPricesEnabled();
    }

    public long priceRevision() {
        return priceRevision;
    }

    public Optional<GlobalSellPriceEntry> entry(Material material) {
        if (material == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(material));
    }

    public double price(Material material) {
        if (material == null) {
            return -1D;
        }
        GlobalSellPriceEntry entry = entries.get(material);
        return entry == null || !entry.enabled() ? -1D : entry.price();
    }

    public double price(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return -1D;
        }

        ItemMeta meta = stack.getItemMeta();
        if (isPotionMaterial(stack.getType())) {
            return potionPrice(stack, meta);
        }

        Map<Enchantment, Integer> enchantments = enchantments(meta);
        double enchantmentPrice = enchantmentPrice(enchantments);
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            return enchantmentPrice > 0D ? enchantmentPrice : price(Material.ENCHANTED_BOOK);
        }

        double basePrice = price(stack.getType());
        if (basePrice <= 0D) {
            return -1D;
        }
        return basePrice + Math.max(0D, enchantmentPrice) + trimPrice(meta);
    }

    public boolean participatesInRotatingShop(Material material) {
        GlobalSellPriceEntry entry = entries.get(material);
        return entry != null && entry.enabled() && entry.price() > 0D && entry.rotatingShop();
    }

    public List<GlobalSellPriceEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.material().name()))
                .toList();
    }

    public Optional<GlobalPotionEntry> potionEntry(PotionType potionType) {
        if (potionType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(potionEntries.get(potionType));
    }

    public Optional<GlobalPotionEntry> potionEntry(String itemId) {
        return parsePotionEntryId(itemId).flatMap(this::potionEntry);
    }

    public double potionPrice(PotionType potionType) {
        GlobalPotionEntry entry = potionEntries.get(potionType);
        return entry == null || !entry.enabled() ? -1D : entry.price();
    }

    public List<GlobalPotionEntry> potionEntries() {
        return potionEntries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.potionType().name()))
                .toList();
    }

    public boolean potionParticipatesInRotatingShop(PotionType potionType) {
        GlobalPotionEntry entry = potionEntries.get(potionType);
        return entry != null && entry.enabled() && entry.price() > 0D && entry.rotatingShop();
    }

    public String rotatingItemId(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "";
        }
        if (isPotionMaterial(stack.getType()) && stack.getItemMeta() instanceof PotionMeta potionMeta) {
            PotionType type = potionMeta.getBasePotionType();
            if (type != null) {
                return potionEntryId(type);
            }
        }
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            Map<Enchantment, Integer> enchantments = enchantments(stack.getItemMeta());
            if (enchantments.size() == 1) {
                Map.Entry<Enchantment, Integer> enchantment = enchantments.entrySet().iterator().next();
                String key = enchantmentKey(enchantment.getKey());
                if (key != null) {
                    return enchantmentEntryId(key, enchantment.getValue());
                }
            }
        }
        return stack.getType().name();
    }

    public static String potionEntryId(PotionType potionType) {
        return potionType == null ? "" : POTION_ROTATING_PREFIX + potionType.name();
    }

    public Optional<GlobalEnchantmentEntry> enchantmentEntry(Enchantment enchantment, int level) {
        String key = enchantmentKey(enchantment);
        if (key == null) {
            return Optional.empty();
        }
        return enchantmentEntry(key, level);
    }

    public Optional<GlobalEnchantmentEntry> enchantmentEntry(String itemId) {
        return parseEnchantmentEntryId(itemId)
                .flatMap(key -> enchantmentEntry(key.enchantmentKey(), key.level()));
    }

    public Optional<GlobalEnchantmentEntry> enchantmentEntry(String enchantmentKey, int level) {
        String key = normalizedEnchantmentConfigKey(enchantmentKey);
        if (key == null || level <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(enchantmentEntries.getOrDefault(key, Map.of()).get(level));
    }

    public List<GlobalEnchantmentEntry> enchantmentEntries() {
        return enchantmentEntries.values().stream()
                .flatMap(entries -> entries.values().stream())
                .sorted(Comparator
                        .comparing(GlobalEnchantmentEntry::enchantmentKey)
                        .thenComparing(Comparator.comparingInt(GlobalEnchantmentEntry::level).reversed()))
                .toList();
    }

    public boolean enchantmentParticipatesInRotatingShop(String enchantmentKey, int level) {
        GlobalEnchantmentEntry entry = enchantmentEntry(enchantmentKey, level).orElse(null);
        return entry != null && entry.enabled() && entry.price() > 0D && entry.rotatingShop();
    }

    public static String enchantmentEntryId(String enchantmentKey, int level) {
        return enchantmentKey == null ? "" : ENCHANTMENT_ITEM_PREFIX + enchantmentKey + ":" + level;
    }

    public double enchantmentPrice(Enchantment enchantment, int level) {
        if (enchantment == null || level <= 0) {
            return 0D;
        }
        String key = enchantmentKey(enchantment);
        if (key == null) {
            return 0D;
        }
        GlobalEnchantmentEntry entry = enchantmentEntries.getOrDefault(key, Map.of()).get(level);
        return entry == null || !entry.enabled() ? 0D : entry.price();
    }

    public double enchantmentPrice(Map<Enchantment, Integer> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            total += enchantmentPrice(entry.getKey(), entry.getValue());
        }
        return total;
    }

    public double enchantmentPrice(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0D;
        }
        return enchantmentPrice(enchantments(stack.getItemMeta()));
    }

    public double itemModifierPrice(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0D;
        }
        ItemMeta meta = stack.getItemMeta();
        return Math.max(0D, enchantmentPrice(enchantments(meta))) + trimPrice(meta);
    }

    public double trimPrice(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0D;
        }
        return trimPrice(stack.getItemMeta());
    }

    public double potionPrice(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0D;
        }
        return potionPrice(stack, stack.getItemMeta());
    }

    public boolean setPrice(Material material, double price) {
        if (!isValidMaterial(material) || !Double.isFinite(price)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = "items." + material.name();
        if (!yaml.contains(path + ".enabled")) {
            yaml.set(path + ".enabled", true);
        }
        yaml.set(path + ".price", Math.max(0D, price));
        if (!yaml.contains(path + ".rotating-shop")) {
            yaml.set(path + ".rotating-shop", true);
        }
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setRotatingShop(Material material, boolean rotatingShop) {
        if (!isValidMaterial(material)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = "items." + material.name();
        if (!yaml.contains(path + ".enabled")) {
            yaml.set(path + ".enabled", true);
        }
        if (!yaml.contains(path + ".price")) {
            yaml.set(path + ".price", DEFAULT_PRICE);
        }
        yaml.set(path + ".rotating-shop", rotatingShop);
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setEnabled(Material material, boolean enabled) {
        if (!isValidMaterial(material)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = "items." + material.name();
        yaml.set(path + ".enabled", enabled);
        if (!yaml.contains(path + ".price")) {
            yaml.set(path + ".price", DEFAULT_PRICE);
        }
        if (!yaml.contains(path + ".rotating-shop")) {
            yaml.set(path + ".rotating-shop", true);
        }
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setPotionPrice(PotionType potionType, double price) {
        if (potionType == null || !Double.isFinite(price)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = potionPath(potionType);
        if (!yaml.contains(path + ".enabled")) {
            yaml.set(path + ".enabled", true);
        }
        yaml.set(path + ".price", Math.max(0D, price));
        if (!yaml.contains(path + ".rotating-shop")) {
            yaml.set(path + ".rotating-shop", false);
        }
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setPotionRotatingShop(PotionType potionType, boolean rotatingShop) {
        if (potionType == null) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = potionPath(potionType);
        if (!yaml.contains(path + ".enabled")) {
            yaml.set(path + ".enabled", true);
        }
        if (!yaml.contains(path + ".price")) {
            yaml.set(path + ".price", DEFAULT_PRICE);
        }
        yaml.set(path + ".rotating-shop", rotatingShop);
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setPotionEnabled(PotionType potionType, boolean enabled) {
        if (potionType == null) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = potionPath(potionType);
        yaml.set(path + ".enabled", enabled);
        if (!yaml.contains(path + ".price")) {
            yaml.set(path + ".price", DEFAULT_PRICE);
        }
        if (!yaml.contains(path + ".rotating-shop")) {
            yaml.set(path + ".rotating-shop", false);
        }
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setEnchantmentPrice(String enchantmentKey, int level, double price) {
        String key = normalizedEnchantmentConfigKey(enchantmentKey);
        if (!isValidEnchantmentEntry(key, level) || !Double.isFinite(price)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = enchantmentPath(key, level);
        if (!yaml.contains(path + ".enabled")) {
            yaml.set(path + ".enabled", true);
        }
        yaml.set(path + ".price", Math.max(0D, price));
        if (!yaml.contains(path + ".rotating-shop")) {
            yaml.set(path + ".rotating-shop", false);
        }
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setEnchantmentRotatingShop(String enchantmentKey, int level, boolean rotatingShop) {
        String key = normalizedEnchantmentConfigKey(enchantmentKey);
        if (!isValidEnchantmentEntry(key, level)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = enchantmentPath(key, level);
        if (!yaml.contains(path + ".enabled")) {
            yaml.set(path + ".enabled", true);
        }
        if (!yaml.contains(path + ".price")) {
            yaml.set(path + ".price", defaultEnchantmentPrice(key, level, DEFAULT_ENCHANTMENT_LEVELS.getOrDefault(key, level)));
        }
        yaml.set(path + ".rotating-shop", rotatingShop);
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean setEnchantmentEnabled(String enchantmentKey, int level, boolean enabled) {
        String key = normalizedEnchantmentConfigKey(enchantmentKey);
        if (!isValidEnchantmentEntry(key, level)) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String path = enchantmentPath(key, level);
        yaml.set(path + ".enabled", enabled);
        if (!yaml.contains(path + ".price")) {
            yaml.set(path + ".price", defaultEnchantmentPrice(key, level, DEFAULT_ENCHANTMENT_LEVELS.getOrDefault(key, level)));
        }
        if (!yaml.contains(path + ".rotating-shop")) {
            yaml.set(path + ".rotating-shop", false);
        }
        if (!write(file(), yaml)) {
            return false;
        }
        reload();
        return true;
    }

    private List<Material> sellableMaterials() {
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isValidMaterial(material)) {
                materials.add(material);
            }
        }
        materials.sort(Comparator.comparing(Material::name));
        return materials;
    }

    private boolean isValidMaterial(Material material) {
        return material != null
                && material != Material.AIR
                && !material.name().startsWith("LEGACY_")
                && material.isItem();
    }

    private boolean isNormallyUnobtainableDefault(Material material) {
        if (material == null) {
            return true;
        }
        String name = material.name();
        return switch (name) {
            case "AIR", "CAVE_AIR", "VOID_AIR",
                 "BARRIER", "BEDROCK", "REINFORCED_DEEPSLATE",
                 "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
                 "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "DEBUG_STICK", "KNOWLEDGE_BOOK", "LIGHT",
                 "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
                 "SPAWNER", "TRIAL_SPAWNER", "VAULT",
                 "END_PORTAL", "NETHER_PORTAL", "END_GATEWAY", "END_PORTAL_FRAME",
                 "FIRE", "SOUL_FIRE", "WATER", "LAVA", "BUBBLE_COLUMN",
                 "PISTON_HEAD", "MOVING_PISTON",
                 "FARMLAND", "DIRT_PATH", "FROSTED_ICE",
                 "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM", "MELON_STEM", "PUMPKIN_STEM",
                 "BIG_DRIPLEAF_STEM", "BAMBOO_SAPLING",
                 "CARROTS", "POTATOES", "BEETROOTS",
                 "LAVA_CAULDRON", "WATER_CAULDRON", "POWDER_SNOW_CAULDRON" -> true;
            default -> name.endsWith("_SPAWN_EGG")
                    || name.startsWith("POTTED_")
                    || name.endsWith("_WALL_SIGN")
                    || name.endsWith("_WALL_HANGING_SIGN")
                    || name.endsWith("_WALL_BANNER")
                    || name.endsWith("_WALL_HEAD")
                    || name.endsWith("_WALL_SKULL")
                    || name.endsWith("_WALL_FAN")
                    || name.endsWith("_WALL_TORCH")
                    || name.endsWith("_CANDLE_CAKE");
        };
    }

    private double normalizedPrice(double value) {
        return Double.isFinite(value) ? Math.max(0D, value) : DEFAULT_PRICE;
    }

    private boolean migrateLegacyDefaultPrice(YamlConfiguration yaml, String path, Material material) {
        if (material == null || !yaml.contains(path + ".price")) {
            return false;
        }

        PriceMigration migration = LEGACY_DEFAULT_PRICE_MIGRATIONS.get(material.name());
        if (migration == null) {
            migration = coloredWoolDerivedMigration(material);
        }
        if (migration == null) {
            return false;
        }

        double current = yaml.getDouble(path + ".price", migration.replacementPrice());
        if (!migration.matches(current)) {
            return false;
        }

        yaml.set(path + ".price", migration.replacementPrice());
        return true;
    }

    private PriceMigration coloredWoolDerivedMigration(Material material) {
        String name = material.name();
        if (isColoredMaterial(name, "_WOOL")) {
            return new PriceMigration(0.1D, 1D);
        }
        if (isColoredMaterial(name, "_CARPET")) {
            return new PriceMigration(0.05D, 0.5D);
        }
        if (isColoredMaterial(name, "_BED")) {
            return new PriceMigration(0.2D, 2D);
        }
        if (isColoredMaterial(name, "_BANNER")) {
            return new PriceMigration(0.15D, 1.5D);
        }
        return null;
    }

    private boolean isColoredMaterial(String name, String suffix) {
        if (!name.endsWith(suffix)) {
            return false;
        }
        String color = name.substring(0, name.length() - suffix.length());
        return switch (color) {
            case "BLACK", "BLUE", "BROWN", "CYAN", "GRAY", "GREEN", "LIGHT_BLUE", "LIGHT_GRAY",
                 "LIME", "MAGENTA", "ORANGE", "PINK", "PURPLE", "RED", "WHITE", "YELLOW" -> true;
            default -> false;
        };
    }

    private Map<Enchantment, Integer> enchantments(ItemMeta meta) {
        if (meta == null) {
            return Map.of();
        }
        if (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants()) {
            return storageMeta.getStoredEnchants();
        }
        return meta.getEnchants();
    }

    private double trimPrice(ItemMeta meta) {
        if (!(meta instanceof ArmorMeta armorMeta) || !armorMeta.hasTrim()) {
            return 0D;
        }

        ArmorTrim trim = armorMeta.getTrim();
        if (trim == null) {
            return 0D;
        }

        double total = 0D;
        double materialPrice = price(trimMaterialItem(trim.getMaterial()));
        if (materialPrice > 0D) {
            total += materialPrice;
        }
        Material patternItem = trimPatternItem(trim.getPattern());
        if (patternItem != null) {
            double patternPrice = price(patternItem);
            if (patternPrice > 0D) {
                total += patternPrice;
            }
        }
        return Math.max(0D, total * ARMOR_TRIM_PRICE_MULTIPLIER);
    }

    private Material trimMaterialItem(TrimMaterial trimMaterial) {
        if (trimMaterial == null || trimMaterial.getKey() == null) {
            return null;
        }
        return switch (trimMaterial.getKey().getKey().toUpperCase(Locale.ROOT)) {
            case "AMETHYST" -> Material.AMETHYST_SHARD;
            case "COPPER" -> Material.COPPER_INGOT;
            case "DIAMOND" -> Material.DIAMOND;
            case "EMERALD" -> Material.EMERALD;
            case "GOLD" -> Material.GOLD_INGOT;
            case "IRON" -> Material.IRON_INGOT;
            case "LAPIS" -> Material.LAPIS_LAZULI;
            case "NETHERITE" -> Material.NETHERITE_INGOT;
            case "QUARTZ" -> Material.QUARTZ;
            case "REDSTONE" -> Material.REDSTONE;
            case "RESIN" -> Material.RESIN_BRICK;
            default -> null;
        };
    }

    private Material trimPatternItem(TrimPattern trimPattern) {
        if (trimPattern == null || trimPattern.getKey() == null) {
            return null;
        }
        return Material.matchMaterial(trimPattern.getKey().getKey().toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
    }

    private double potionPrice(ItemStack stack, ItemMeta meta) {
        if (!isPotionMaterial(stack.getType()) || !(meta instanceof PotionMeta potionMeta)) {
            return -1D;
        }
        PotionType type = potionMeta.getBasePotionType();
        if (type == null) {
            return -1D;
        }
        GlobalPotionEntry entry = potionEntries.get(type);
        if (entry == null || !entry.enabled() || entry.price() <= 0D) {
            return -1D;
        }

        double price = entry.price();
        double splashModifier = ingredientPortion(Material.GUNPOWDER);
        double lingeringModifier = splashModifier + ingredientPortion(Material.DRAGON_BREATH);
        return switch (stack.getType()) {
            case POTION -> price;
            case SPLASH_POTION -> price + splashModifier;
            case LINGERING_POTION -> price + lingeringModifier;
            case TIPPED_ARROW -> Math.max(0D, (price + lingeringModifier + price(Material.ARROW) * 8D) / 8D);
            default -> -1D;
        };
    }

    private double ingredientPortion(Material material) {
        double price = price(material);
        return price <= 0D ? 0D : price / 3D;
    }

    private boolean isPotionMaterial(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION
                || material == Material.TIPPED_ARROW;
    }

    private String enchantmentKey(Enchantment enchantment) {
        if (enchantment == null || enchantment.getKey() == null) {
            return null;
        }
        return enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
    }

    private String enchantmentPath(String enchantmentKey, int level) {
        return "enchantments." + enchantmentKey + "." + level;
    }

    private String potionPath(PotionType potionType) {
        return "potions." + potionType.name();
    }

    private Optional<PotionType> parsePotionEntryId(String itemId) {
        if (itemId == null || !itemId.regionMatches(true, 0, POTION_ROTATING_PREFIX, 0, POTION_ROTATING_PREFIX.length())) {
            return Optional.empty();
        }
        try {
            return Optional.of(PotionType.valueOf(itemId.substring(POTION_ROTATING_PREFIX.length()).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<EnchantmentEntryKey> parseEnchantmentEntryId(String itemId) {
        if (itemId == null || !itemId.regionMatches(true, 0, ENCHANTMENT_ITEM_PREFIX, 0, ENCHANTMENT_ITEM_PREFIX.length())) {
            return Optional.empty();
        }
        String raw = itemId.substring(ENCHANTMENT_ITEM_PREFIX.length());
        int separator = raw.lastIndexOf(':');
        if (separator <= 0 || separator >= raw.length() - 1) {
            return Optional.empty();
        }
        String key = normalizedEnchantmentConfigKey(raw.substring(0, separator));
        if (key == null) {
            return Optional.empty();
        }
        try {
            int level = Integer.parseInt(raw.substring(separator + 1));
            return isValidEnchantmentEntry(key, level)
                    ? Optional.of(new EnchantmentEntryKey(key, level))
                    : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private boolean isValidEnchantmentEntry(String enchantmentKey, int level) {
        Integer maxLevel = DEFAULT_ENCHANTMENT_LEVELS.get(enchantmentKey);
        return maxLevel != null && level >= 1 && level <= maxLevel;
    }

    private String normalizedEnchantmentConfigKey(String enchantmentKey) {
        String normalized = ItemKeys.normalizeEnchantmentKey(enchantmentKey);
        if (normalized == null) {
            return null;
        }
        String key = normalized.substring(normalized.lastIndexOf(':') + 1)
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return DEFAULT_ENCHANTMENT_LEVELS.containsKey(key) ? key : null;
    }

    private double defaultEnchantmentPrice(String enchantmentKey, int level, int maxLevel) {
        return switch (Math.max(1, level)) {
            case 1 -> 100D;
            case 2 -> 200D;
            case 3 -> 400D;
            case 4 -> 600D;
            default -> 1000D;
        };
    }

    private double legacyDefaultEnchantmentPrice(String enchantmentKey, int level, int maxLevel) {
        double maxPrice = switch (enchantmentKey) {
            case "MENDING", "SILK_TOUCH", "INFINITY", "FORTUNE", "LOOTING" -> 1D;
            case "EFFICIENCY", "UNBREAKING", "PROTECTION", "SHARPNESS", "POWER" -> 0.85D;
            case "FEATHER_FALLING", "THORNS", "SWEEPING_EDGE", "IMPALING", "DENSITY", "WIND_BURST" -> 0.75D;
            case "FIRE_PROTECTION", "BLAST_PROTECTION", "PROJECTILE_PROTECTION", "SMITE", "BANE_OF_ARTHROPODS", "BREACH" -> 0.65D;
            case "RESPIRATION", "DEPTH_STRIDER", "FROST_WALKER", "SOUL_SPEED", "SWIFT_SNEAK", "QUICK_CHARGE", "PIERCING", "LUNGE" -> 0.6D;
            case "AQUA_AFFINITY", "FLAME", "CHANNELING", "MULTISHOT" -> 0.5D;
            case "KNOCKBACK", "FIRE_ASPECT", "PUNCH", "LOYALTY", "RIPTIDE" -> 0.55D;
            case "LUCK_OF_THE_SEA", "LURE" -> 0.45D;
            case "BINDING_CURSE", "VANISHING_CURSE" -> 0.1D;
            default -> 0.4D;
        };
        if (maxLevel <= 1) {
            return maxPrice;
        }
        double progress = level / (double) maxLevel;
        return Math.round(maxPrice * progress * progress * 100D) / 100D;
    }

    private boolean migrateLegacyDefaultEnchantmentPrice(YamlConfiguration yaml, String path, String enchantmentKey, int level, int maxLevel) {
        if (!yaml.contains(path + ".price")) {
            return false;
        }

        double legacyPrice = legacyDefaultEnchantmentPrice(enchantmentKey, level, maxLevel);
        double scaledPrice = scaledDefaultEnchantmentPrice(enchantmentKey, level, maxLevel);
        double current = yaml.getDouble(path + ".price", legacyPrice);
        if (Math.abs(current - legacyPrice) > 0.0001D && Math.abs(current - scaledPrice) > 0.0001D) {
            return false;
        }

        double replacementPrice = defaultEnchantmentPrice(enchantmentKey, level, maxLevel);
        if (Math.abs(replacementPrice - current) <= 0.0001D) {
            return false;
        }

        yaml.set(path + ".price", replacementPrice);
        return true;
    }

    private double scaledDefaultEnchantmentPrice(String enchantmentKey, int level, int maxLevel) {
        double maxPrice = switch (enchantmentKey) {
            case "BINDING_CURSE", "VANISHING_CURSE" -> 100D;
            default -> 1000D;
        };
        if (maxLevel <= 1) {
            return maxPrice;
        }
        return Math.round(maxPrice * level / (double) maxLevel * 100D) / 100D;
    }

    private static Map<String, Integer> defaultEnchantmentLevels() {
        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("PROTECTION", 4);
        levels.put("FIRE_PROTECTION", 4);
        levels.put("FEATHER_FALLING", 4);
        levels.put("BLAST_PROTECTION", 4);
        levels.put("PROJECTILE_PROTECTION", 4);
        levels.put("RESPIRATION", 3);
        levels.put("AQUA_AFFINITY", 1);
        levels.put("THORNS", 3);
        levels.put("DEPTH_STRIDER", 3);
        levels.put("FROST_WALKER", 2);
        levels.put("BINDING_CURSE", 1);
        levels.put("SHARPNESS", 5);
        levels.put("SMITE", 5);
        levels.put("BANE_OF_ARTHROPODS", 5);
        levels.put("KNOCKBACK", 2);
        levels.put("FIRE_ASPECT", 2);
        levels.put("LOOTING", 3);
        levels.put("SWEEPING_EDGE", 3);
        levels.put("EFFICIENCY", 5);
        levels.put("SILK_TOUCH", 1);
        levels.put("UNBREAKING", 3);
        levels.put("FORTUNE", 3);
        levels.put("POWER", 5);
        levels.put("PUNCH", 2);
        levels.put("FLAME", 1);
        levels.put("INFINITY", 1);
        levels.put("LUCK_OF_THE_SEA", 3);
        levels.put("LURE", 3);
        levels.put("LOYALTY", 3);
        levels.put("IMPALING", 5);
        levels.put("RIPTIDE", 3);
        levels.put("CHANNELING", 1);
        levels.put("MULTISHOT", 1);
        levels.put("QUICK_CHARGE", 3);
        levels.put("PIERCING", 4);
        levels.put("DENSITY", 5);
        levels.put("BREACH", 4);
        levels.put("WIND_BURST", 3);
        levels.put("MENDING", 1);
        levels.put("VANISHING_CURSE", 1);
        levels.put("SOUL_SPEED", 3);
        levels.put("SWIFT_SNEAK", 3);
        levels.put("LUNGE", 3);
        return levels;
    }

    private boolean write(File file, YamlConfiguration yaml) {
        var result = plugin.getSafeYamlWriter().write(file, yaml);
        if (!result.success()) {
            plugin.getLogger().warning("Failed saving global sell prices: " + result.error());
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Failed saving global sell prices: " + result.error());
            }
            return false;
        }
        return true;
    }

    private void ensureDefaultFile(File file) {
        if (file.exists()) {
            return;
        }
        try (InputStream input = plugin.getResource("global-sell-prices.yml")) {
            if (input != null) {
                plugin.saveResource("global-sell-prices.yml", false);
            }
        } catch (IllegalArgumentException | IOException ex) {
            plugin.getLogger().warning("Failed copying default global sell prices: " + ex.getMessage());
        }
    }

    private YamlConfiguration loadDefaultConfiguration() {
        try (InputStream input = plugin.getResource("global-sell-prices.yml")) {
            if (input == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed loading default global sell prices: " + ex.getMessage());
            return new YamlConfiguration();
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), "global-sell-prices.yml");
    }

    public record GlobalSellPriceEntry(Material material, boolean enabled, double price, boolean rotatingShop) {
    }

    public record GlobalEnchantmentEntry(String enchantmentKey, int level, boolean enabled, double price, boolean rotatingShop) {
    }

    public record GlobalPotionEntry(PotionType potionType, boolean enabled, double price, boolean rotatingShop) {
    }

    private record PriceMigration(double replacementPrice, List<Double> legacyPrices) {

        private PriceMigration(double legacyPrice, double replacementPrice) {
            this(replacementPrice, List.of(legacyPrice));
        }

        private PriceMigration(double replacementPrice, double firstLegacyPrice, double secondLegacyPrice) {
            this(replacementPrice, List.of(firstLegacyPrice, secondLegacyPrice));
        }

        private boolean matches(double current) {
            for (double legacyPrice : legacyPrices) {
                if (Math.abs(current - legacyPrice) <= 0.0001D) {
                    return true;
                }
            }
            return false;
        }
    }

    private record EnchantmentEntryKey(String enchantmentKey, int level) {
    }
}
