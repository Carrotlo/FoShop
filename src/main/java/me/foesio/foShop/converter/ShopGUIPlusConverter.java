package me.foesio.foShop.converter;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopItemType;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.util.ItemKeys;
import me.foesio.foShop.util.VanillaItemNames;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class ShopGUIPlusConverter {

    private static final java.util.regex.Pattern RAW_HEX_COLOR = java.util.regex.Pattern.compile("(?<!&)(#[A-Fa-f0-9]{6})");
    private static final java.util.regex.Pattern FIRST_NUMBER = java.util.regex.Pattern.compile("\\d+");
    private static final int[] MAIN_MENU_FALLBACK_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final Set<String> SKIP_FILE_NAMES = Set.of(
            "config.yml",
            "messages.yml",
            "sellgui.yml",
            "categories.yml",
            "discounts.yml",
            "permissions.yml",
            "blacklist.yml",
            "whitelist.yml",
            "lang.yml"
    );

    private final FoShop plugin;

    public ShopGUIPlusConverter(FoShop plugin) {
        this.plugin = plugin;
    }

    public ConversionResult convert(ConversionMode mode) {
        return convert(SourcePlugin.SHOP_GUI_PLUS, mode);
    }

    public ConversionResult convert(SourcePlugin sourcePlugin, ConversionMode mode) {
        File sourceRoot = findSourceRoot(sourcePlugin);
        YamlConfiguration report = new YamlConfiguration();
        report.set("converter", sourcePlugin.displayName());
        report.set("mode", mode.name().toLowerCase(Locale.ROOT));
        report.set("created-at", System.currentTimeMillis());

        if (sourceRoot == null) {
            report.set("success", false);
            report.set("reason", sourcePlugin.displayName() + " folder was not found in plugins directory.");
            writeReport(report);
            return new ConversionResult(false, mode == ConversionMode.DRY_RUN, 0, 0, 0, 0,
                    sourcePlugin.displayName() + " folder was not found in plugins directory.", getReportFile());
        }

        report.set("source-root", sourceRoot.getAbsolutePath());
        List<String> notes = new ArrayList<>();
        Map<String, MainMenuMeta> mainMenuMetadata = sourcePlugin == SourcePlugin.ECONOMY_SHOP_GUI
                ? loadEconomyShopGuiMainMenuMetadata(sourceRoot)
                : loadMainMenuMetadata(sourceRoot);
        Map<Material, Integer> stackSizeCaps = sourcePlugin == SourcePlugin.ECONOMY_SHOP_GUI
                ? Map.of()
                : loadStackSizeCaps(sourceRoot);
        int mainMenuSize = Math.max(loadMainMenuSize(sourceRoot, sourcePlugin), requiredMainMenuSize(mainMenuMetadata));
        Set<Integer> usedMainMenuSlots = usedMainMenuSlots(mainMenuMetadata);
        int mainConfigSettings = sourcePlugin == SourcePlugin.ECONOMY_SHOP_GUI
                ? convertEconomyShopGuiMainConfig(sourceRoot, mode, notes)
                : convertMainConfig(sourceRoot, mode, notes, mainMenuSize);

        List<File> candidateFiles = sourcePlugin == SourcePlugin.ECONOMY_SHOP_GUI
                ? findEconomyShopGuiCandidateFiles(sourceRoot)
                : findCandidateYamlFiles(sourceRoot);
        report.set("files-scanned", candidateFiles.stream().map(File::getAbsolutePath).toList());

        if (candidateFiles.isEmpty()) {
            report.set("success", false);
            report.set("reason", "No convertible " + sourcePlugin.displayName() + " YAML files were found.");
            writeReport(report);
            return new ConversionResult(false, mode == ConversionMode.DRY_RUN, 0, 0, 0, 0,
                    "No convertible " + sourcePlugin.displayName() + " YAML files were found.", getReportFile());
        }

        int converted = 0;
        int convertible = 0;
        int skippedSections = 0;
        int fallbackSections = 0;
        Set<String> usedIds = new HashSet<>();

        if (sourcePlugin == SourcePlugin.ECONOMY_SHOP_GUI) {
            Map<String, File> sectionFiles = findEconomyShopGuiSectionFiles(sourceRoot);
            for (File file : candidateFiles) {
                List<ParseResult> parseResults = parseEconomyShopGuiFile(file, sectionFiles, mainMenuMetadata,
                        usedMainMenuSlots, mainMenuSize);

                for (ParseResult parseResult : parseResults) {
                    ConvertedSectionResult sectionResult = handleParsedSection(file, parseResult, usedIds, mode, notes);
                    skippedSections += sectionResult.skippedSections();
                    fallbackSections += sectionResult.fallbackSections();
                    convertible += sectionResult.convertibleSections();
                    converted += sectionResult.convertedSections();
                }
            }
        } else {
            for (File file : candidateFiles) {
                List<ParseResult> parseResults = parseShopFile(file, sourcePlugin.displayName(), mainMenuMetadata, stackSizeCaps, usedMainMenuSlots, mainMenuSize);

                for (ParseResult parseResult : parseResults) {
                    ConvertedSectionResult sectionResult = handleParsedSection(file, parseResult, usedIds, mode, notes);
                    skippedSections += sectionResult.skippedSections();
                    fallbackSections += sectionResult.fallbackSections();
                    convertible += sectionResult.convertibleSections();
                    converted += sectionResult.convertedSections();
                }
            }
        }

        File sellGuiRoot = sourcePlugin == SourcePlugin.SHOP_GUI_PLUS ? findShopGuiPlusSellGuiRoot() : null;
        int sellGuiSettings = 0;
        if (sourcePlugin == SourcePlugin.SHOP_GUI_PLUS && sellGuiRoot != null) {
            report.set("sellgui-source-root", sellGuiRoot.getAbsolutePath());
            sellGuiSettings = convertSellGuiConfig(sourceRoot, sellGuiRoot, mode, notes);
        } else if (sourcePlugin == SourcePlugin.SHOP_GUI_PLUS) {
            report.set("sellgui-source-root", null);
            notes.add("ShopGUIPlus-SellGUI folder not found; skipped SellGUI config conversion.");
        }

        report.set("success", true);
        report.set("convertible-sections", convertible);
        report.set("converted-sections", mode == ConversionMode.APPLY ? converted : 0);
        report.set("main-settings", mainConfigSettings);
        report.set("sellgui-settings", sellGuiSettings);
        report.set("skipped-sections", skippedSections);
        report.set("fallback-slot-sections", fallbackSections);
        report.set("notes", notes);

        String writeError = writeReport(report);
        if (writeError != null) {
            notes.add("Failed writing conversion report: " + writeError);
        }

        String modeText = mode == ConversionMode.DRY_RUN ? "Dry-run" : "Applied";
        String message = modeText + " conversion complete: convertible=" + convertible + ", converted=" + (mode == ConversionMode.APPLY ? converted : 0)
                + ", skipped=" + skippedSections + ", fallback-slots=" + fallbackSections + ", main-settings=" + mainConfigSettings
                + ", sellgui-settings=" + sellGuiSettings + "."
                + " Report: " + getReportFile().getName();

        return new ConversionResult(true, mode == ConversionMode.DRY_RUN, mode == ConversionMode.APPLY ? converted : convertible,
                convertible, skippedSections, fallbackSections, message, getReportFile());
    }

    public Optional<File> locateSourceFolder() {
        return Optional.ofNullable(findShopGuiPlusRoot());
    }

    public Optional<File> locateSourceFolder(SourcePlugin sourcePlugin) {
        return Optional.ofNullable(findSourceRoot(sourcePlugin));
    }

    public File getReportFile() {
        return new File(plugin.getDataFolder(), "conversion-report.yml");
    }

    private String writeReport(YamlConfiguration report) {
        var writeResult = plugin.getSafeYamlWriter().write(getReportFile(), report);
        if (!writeResult.success()) {
            return writeResult.error();
        }
        return null;
    }

    private ConvertedSectionResult handleParsedSection(File file, ParseResult parseResult, Set<String> usedIds,
                                                       ConversionMode mode, List<String> notes) {
        for (String issue : parseResult.issues()) {
            notes.add("[" + file.getName() + "] " + issue);
        }

        if (parseResult.parsedSection() == null) {
            return new ConvertedSectionResult(0, 0, 1, 0);
        }

        ParsedSection parsed = parseResult.parsedSection();
        String uniqueId = ensureUniqueId(usedIds, parsed.id());
        ShopSection section = new ShopSection(uniqueId, parsed.title(), parsed.size(), parsed.icon(), parsed.iconItem(), parsed.description(),
                parsed.enabled(), parsed.slot(), parsed.items());

        List<String> validationIssues = plugin.getValidationService().validateSectionModel(section);
        if (!validationIssues.isEmpty()) {
            for (String issue : validationIssues) {
                notes.add("[" + file.getName() + "] " + issue);
            }
            return new ConvertedSectionResult(0, 0, 1, 0);
        }

        int fallbackSections = parsed.fallbackSlotsUsed() > 0 ? 1 : 0;
        if (mode == ConversionMode.APPLY) {
            var saveResult = plugin.getShopManager().saveSection(section);
            if (!saveResult.success()) {
                notes.add("[" + file.getName() + "] failed to save section " + section.id() + ": " + saveResult.error());
                return new ConvertedSectionResult(0, 0, 1, fallbackSections);
            }
            notes.add("[" + file.getName() + "] wrote FoShop section file shops/" + section.id() + ".yml");
            return new ConvertedSectionResult(1, 1, 0, fallbackSections);
        }

        return new ConvertedSectionResult(0, 1, 0, fallbackSections);
    }

    private File findSourceRoot(SourcePlugin sourcePlugin) {
        return switch (sourcePlugin) {
            case SHOP_GUI_PLUS -> findShopGuiPlusRoot();
            case ECONOMY_SHOP_GUI -> findEconomyShopGuiRoot();
        };
    }

    private File findShopGuiPlusRoot() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File exact = new File(pluginsDir, "ShopGUIPlus");
        if (exact.exists() && exact.isDirectory()) {
            return exact;
        }

        File[] children = pluginsDir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }

        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (name.equals("shopguiplus") || name.equals("shopgui+") || (name.contains("shopguiplus") && !name.contains("sellgui"))) {
                return child;
            }
        }

        return null;
    }

    private File findEconomyShopGuiRoot() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File exact = new File(pluginsDir, "EconomyShopGUI");
        if (exact.exists() && exact.isDirectory()) {
            return exact;
        }

        File[] children = pluginsDir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }

        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
            if (name.equals("economyshopgui") || name.contains("economyshopgui")) {
                return child;
            }
        }

        return null;
    }

    private File findShopGuiPlusSellGuiRoot() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        for (String exactName : List.of("ShopGUIPlus-SellGUI", "ShopGUIPlusSellGUI", "ShopGUIPlus SellGUI")) {
            File exact = new File(pluginsDir, exactName);
            if (exact.exists() && exact.isDirectory()) {
                return exact;
            }
        }

        File[] children = pluginsDir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }

        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT).replace(" ", "");
            if (name.contains("shopguiplus") && name.contains("sellgui")) {
                return child;
            }
        }

        return null;
    }

    private int convertSellGuiConfig(File shopGuiRoot, File sellGuiRoot, ConversionMode mode, List<String> notes) {
        File configFile = new File(sellGuiRoot, "config.yml");
        if (!configFile.exists()) {
            notes.add("[ShopGUIPlus-SellGUI] config.yml not found; skipped SellGUI config conversion.");
            return 0;
        }

        YamlConfiguration source = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration shopGuiConfig = new File(shopGuiRoot, "config.yml").exists()
                ? YamlConfiguration.loadConfiguration(new File(shopGuiRoot, "config.yml"))
                : new YamlConfiguration();
        YamlConfiguration sellGuiYaml = mode == ConversionMode.APPLY ? loadDataYaml("guis/sell-gui.yml") : null;

        int converted = 0;
        int configConverted = 0;
        int sellGuiConverted = 0;

        sellGuiConverted += copyYamlValue(source, "messages.sellgui_title", sellGuiYaml, "title", mode);
        sellGuiConverted += copyYamlValue(source, "options.rows", sellGuiYaml, "rows", mode);
        sellGuiConverted += copyFirstYamlValue(source, List.of("options.decorations", "decorations"), sellGuiYaml, "decorations", mode);

        configConverted += copyConfigValue(source, "options.receipt_type", "sellgui.receipts.type", mode);
        configConverted += copyConfigValue(source, "options.sell_titles", "sellgui.titles.enabled", mode);
        configConverted += copyConfigValue(source, "messages.sell_title", "sellgui.titles.title", mode);
        configConverted += copyConfigValue(source, "messages.sell_subtitle", "sellgui.titles.subtitle", mode);
        configConverted += copyConfigValue(source, "options.action_bar_msgs", "sellgui.action-bar.enabled", mode);
        configConverted += copyConfigValue(source, "messages.action_bar_items_sold", "sellgui.action-bar.message", mode);
        configConverted += copyConfigValue(source, "options.transaction_log.enabled", "sellgui.transaction-log.enabled", mode);
        configConverted += copyConfigValue(source, "options.transaction_log.date_format", "sellgui.transaction-log.date-format", mode);
        configConverted += copyConfigValue(source, "options.rounded_pricing", "sellgui.price-format.rounded-pricing", mode);
        configConverted += copyConfigValue(source, "options.remove_trailing_zeros", "sellgui.price-format.remove-trailing-zeros", mode);
        configConverted += copyConfigValue(source, "options.abbreviate_numbers", "sellgui.price-format.abbreviate-numbers", mode);
        configConverted += copyConfigValue(source, "options.sounds.enabled", "sellgui.sounds.enabled", mode);
        configConverted += copyConfigValue(source, "options.sounds.error_notification", "sellgui.sounds.error-notification", mode);
        configConverted += copyConfigValue(source, "options.sounds.pitch", "sellgui.sounds.pitch", mode);
        configConverted += copyConfigValue(source, "options.sounds.volume", "sellgui.sounds.volume", mode);
        configConverted += copyConfigValue(source, "options.sounds.events.open", "sellgui.sounds.events.open", mode);
        configConverted += copyConfigValue(source, "options.sounds.events.success", "sellgui.sounds.events.success", mode);
        configConverted += copyConfigValue(source, "options.sounds.events.failed", "sellgui.sounds.events.failed", mode);

        configConverted += copyFirstConfigValue(shopGuiConfig, List.of(
                "disableShopsInGamemodes",
                "disabled-gamemodes",
                "disabled_game_modes",
                "disabled-gamemodes.sellgui",
                "gamemode-limitations.disabled",
                "options.disabled-gamemodes",
                "options.disabled_game_modes"
        ), "sellgui.blocked-gamemodes", mode);
        converted += configConverted + sellGuiConverted;

        if (mode == ConversionMode.APPLY && configConverted > 0) {
            plugin.saveConfig();
        }
        if (mode == ConversionMode.APPLY && sellGuiConverted > 0) {
            writeDataYaml("guis/sell-gui.yml", sellGuiYaml, notes);
        }

        notes.add("[ShopGUIPlus-SellGUI] " + (mode == ConversionMode.APPLY ? "Imported " : "Found ")
                + converted + " convertible SellGUI setting(s).");
        if (source.contains("messages")) {
            notes.add("[ShopGUIPlus-SellGUI] Skipped message text import; converter never writes messages.yml.");
        }
        return converted;
    }

    private int convertMainConfig(File shopGuiRoot, ConversionMode mode, List<String> notes, int mainMenuSize) {
        File configFile = new File(shopGuiRoot, "config.yml");
        if (!configFile.exists()) {
            return 0;
        }

        YamlConfiguration source = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration mainGuiYaml = mode == ConversionMode.APPLY ? loadDataYaml("guis/main.yml") : null;
        int converted = 0;
        int configConverted = 0;
        int mainGuiConverted = 0;
        if (source.contains("shopMenuName")) {
            if (mode == ConversionMode.APPLY) {
                String title = normalizeTextColors(source.getString("shopMenuName"));
                plugin.getConfig().set("gui.main.title", title);
                mainGuiYaml.set("title", title);
            }
            configConverted++;
            mainGuiConverted++;
        }
        if (source.contains("shopMenuSize")) {
            int rows = Math.clamp(mainMenuSize / 9, 1, 6);
            if (mode == ConversionMode.APPLY) {
                plugin.getConfig().set("gui.main.rows", rows);
                mainGuiYaml.set("rows", rows);
            }
            configConverted++;
            mainGuiConverted++;
        }
        if (source.contains("disableShopsInGamemodes")) {
            if (mode == ConversionMode.APPLY) {
                plugin.getConfig().set("sellgui.blocked-gamemodes", source.getStringList("disableShopsInGamemodes"));
            }
            configConverted++;
        }
        mainGuiConverted += copyItemLike(source, "shopMenuFillItem", mainGuiYaml, "filler", mode);
        if (source.contains("amountSelectionGUIBulkBuy")) {
            notes.add("[ShopGUIPlus] Skipped amountSelectionGUIBulkBuy import; FoShop keeps its built-in Buy More layout.");
        }
        converted = configConverted + mainGuiConverted;

        if (mode == ConversionMode.APPLY && configConverted > 0) {
            plugin.saveConfig();
        }
        if (mode == ConversionMode.APPLY && mainGuiConverted > 0) {
            writeDataYaml("guis/main.yml", mainGuiYaml, notes);
        }
        notes.add("[ShopGUIPlus] " + (mode == ConversionMode.APPLY ? "Imported " : "Found ")
                + converted + " convertible main config setting(s).");
        return converted;
    }

    private int convertEconomyShopGuiMainConfig(File sourceRoot, ConversionMode mode, List<String> notes) {
        File configFile = new File(sourceRoot, "config.yml");
        if (!configFile.exists()) {
            return 0;
        }

        YamlConfiguration source = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration mainGuiYaml = mode == ConversionMode.APPLY ? loadDataYaml("guis/main.yml") : null;
        YamlConfiguration sellGuiYaml = mode == ConversionMode.APPLY ? loadDataYaml("guis/sell-gui.yml") : null;

        int configConverted = 0;
        int mainGuiConverted = 0;
        int sellGuiConverted = 0;

        if (source.contains("main-menu.gui-rows")) {
            int rows = Math.clamp(source.getInt("main-menu.gui-rows", 3), 1, 6);
            if (mode == ConversionMode.APPLY) {
                plugin.getConfig().set("gui.main.rows", rows);
                mainGuiYaml.set("rows", rows);
            }
            configConverted++;
            mainGuiConverted++;
        }

        mainGuiConverted += copyItemLike(source, "main-menu.fill-item", mainGuiYaml, "filler", mode);

        if (source.contains("sellgui-screen.gui-rows")) {
            int rows = Math.clamp(source.getInt("sellgui-screen.gui-rows", 6), 3, 6);
            if (mode == ConversionMode.APPLY) {
                plugin.getConfig().set("gui.sell.rows", rows);
                sellGuiYaml.set("rows", rows);
            }
            configConverted++;
            sellGuiConverted++;
        }

        configConverted += copyConfigValue(source, "transaction-log", "sellgui.transaction-log.enabled", mode);
        configConverted += copyConfigValue(source, "abbreviations.enabled", "sellgui.price-format.abbreviate-numbers", mode);

        int converted = configConverted + mainGuiConverted + sellGuiConverted;
        if (mode == ConversionMode.APPLY && configConverted > 0) {
            plugin.saveConfig();
        }
        if (mode == ConversionMode.APPLY && mainGuiConverted > 0) {
            writeDataYaml("guis/main.yml", mainGuiYaml, notes);
        }
        if (mode == ConversionMode.APPLY && sellGuiConverted > 0) {
            writeDataYaml("guis/sell-gui.yml", sellGuiYaml, notes);
        }

        notes.add("[EconomyShopGUI] " + (mode == ConversionMode.APPLY ? "Imported " : "Found ")
                + converted + " convertible main config setting(s).");
        return converted;
    }

    private int loadMainMenuSize(File sourceRoot, SourcePlugin sourcePlugin) {
        File configFile = new File(sourceRoot, "config.yml");
        if (!configFile.exists()) {
            return 36;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        if (sourcePlugin == SourcePlugin.ECONOMY_SHOP_GUI) {
            return normalizeSize(yaml.getInt("main-menu.gui-rows", 3) * 9);
        }
        return normalizeSize(yaml.getInt("shopMenuSize", 36));
    }

    private int requiredMainMenuSize(Map<String, MainMenuMeta> mainMenuMetadata) {
        int highestSlot = -1;
        for (MainMenuMeta meta : mainMenuMetadata.values()) {
            if (meta.slot() >= 0 && meta.slot() <= 53) {
                highestSlot = Math.max(highestSlot, meta.slot());
            }
        }
        if (highestSlot < 0) {
            return 9;
        }
        return Math.clamp(((highestSlot + 1 + 8) / 9) * 9, 9, 54);
    }

    private Set<Integer> usedMainMenuSlots(Map<String, MainMenuMeta> mainMenuMetadata) {
        Set<Integer> used = new HashSet<>();
        for (MainMenuMeta meta : mainMenuMetadata.values()) {
            if (meta.slot() >= 0 && meta.slot() <= 53) {
                used.add(meta.slot());
            }
        }
        return used;
    }

    private int nextFreeMainMenuSlot(Set<Integer> usedSlots, int mainMenuSize) {
        int visibleSize = Math.clamp(mainMenuSize, 9, 54);
        for (int slot : MAIN_MENU_FALLBACK_SLOTS) {
            if (slot < visibleSize && usedSlots.add(slot)) {
                return slot;
            }
        }
        for (int slot = 0; slot < visibleSize; slot++) {
            if (usedSlots.add(slot)) {
                return slot;
            }
        }
        for (int slot = visibleSize; slot < 54; slot++) {
            if (usedSlots.add(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private Map<Material, Integer> loadStackSizeCaps(File sourceRoot) {
        File configFile = new File(sourceRoot, "config.yml");
        if (!configFile.exists()) {
            return Map.of();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = yaml.getConfigurationSection("itemStackSizeCappedAt");
        if (section == null) {
            return Map.of();
        }

        Map<Material, Integer> caps = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Material material = material(entry.getString("material")).orElse(null);
            int size = entry.getInt("size", -1);
            if (material != null && size > 0) {
                caps.put(material, Math.clamp(size, 1, Math.min(64, material.getMaxStackSize())));
            }
        }
        return caps;
    }

    private int copyFirstConfigValue(YamlConfiguration source, List<String> sourcePaths, String targetPath, ConversionMode mode) {
        for (String sourcePath : sourcePaths) {
            if (source.contains(sourcePath)) {
                return copyConfigValue(source, sourcePath, targetPath, mode);
            }
        }
        return 0;
    }

    private int copyConfigValue(YamlConfiguration source, String sourcePath, String targetPath, ConversionMode mode) {
        if (!source.contains(sourcePath)) {
            return 0;
        }
        if (mode == ConversionMode.APPLY) {
            plugin.getConfig().set(targetPath, serializeConfigObject(source.get(sourcePath)));
        }
        return 1;
    }

    private int copyFirstYamlValue(YamlConfiguration source, List<String> sourcePaths, YamlConfiguration target, String targetPath, ConversionMode mode) {
        for (String sourcePath : sourcePaths) {
            if (source.contains(sourcePath)) {
                return copyYamlValue(source, sourcePath, target, targetPath, mode);
            }
        }
        return 0;
    }

    private int copyYamlValue(ConfigurationSection source, String sourcePath, YamlConfiguration target, String targetPath, ConversionMode mode) {
        if (!source.contains(sourcePath)) {
            return 0;
        }
        if (mode == ConversionMode.APPLY && target != null) {
            target.set(targetPath, serializeConfigObject(source.get(sourcePath)));
        }
        return 1;
    }

    private int copyItemLike(ConfigurationSection root, String sourcePath, YamlConfiguration target, String targetPath, ConversionMode mode) {
        ConfigurationSection item = root.getConfigurationSection(sourcePath);
        return item == null ? 0 : copyItemLike(item, target, targetPath, mode);
    }

    private int copyItemLike(ConfigurationSection item, YamlConfiguration target, String targetPath, ConversionMode mode) {
        int converted = 0;

        String materialName = firstNonBlank(stringValue(item, "material"), stringValue(item, "item.material"));
        if (materialName != null) {
            if (mode == ConversionMode.APPLY && target != null) {
                target.set(targetPath + ".material", material(materialName).map(Material::name).orElse(cleanEnum(materialName)));
            }
            converted++;
        }

        Integer amount = firstNonNull(intValue(item, "amount"), intValue(item, "quantity"), intValue(item, "item.amount"), intValue(item, "item.quantity"));
        if (amount != null) {
            if (mode == ConversionMode.APPLY && target != null) {
                target.set(targetPath + ".amount", Math.max(1, amount));
            }
            converted++;
        }

        String name = firstNonBlank(stringValue(item, "name"), stringValue(item, "display-name"), stringValue(item, "displayName"),
                stringValue(item, "displayname"), stringValue(item, "item.name"), stringValue(item, "item.displayname"));
        if (name != null) {
            if (mode == ConversionMode.APPLY && target != null) {
                target.set(targetPath + ".name", normalizeTextColors(name));
            }
            converted++;
        }

        List<String> lore = firstNonEmpty(readStringList(item, "lore"), readStringList(item, "item.lore"));
        if (!lore.isEmpty()) {
            if (mode == ConversionMode.APPLY && target != null) {
                target.set(targetPath + ".lore", lore.stream().map(this::normalizeTextColors).toList());
            }
            converted++;
        }

        Integer customModelData = firstNonNull(
                intValue(item, "custom-model-data"),
                intValue(item, "customModelData"),
                intValue(item, "item.custom-model-data"),
                intValue(item, "item.customModelData")
        );
        if (customModelData != null) {
            if (mode == ConversionMode.APPLY && target != null) {
                target.set(targetPath + ".custom-model-data", customModelData);
            }
            converted++;
        }
        return converted;
    }

    private YamlConfiguration loadDataYaml(String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            try {
                plugin.saveResource(relativePath, false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void writeDataYaml(String relativePath, YamlConfiguration yaml, List<String> notes) {
        if (yaml == null) {
            return;
        }
        var writeResult = plugin.getSafeYamlWriter().write(new File(plugin.getDataFolder(), relativePath), yaml);
        if (!writeResult.success()) {
            notes.add("Failed writing " + relativePath + ": " + writeResult.error());
        }
    }

    private Object serializeConfigObject(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                out.put(key, serializeConfigObject(section.get(key)));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::serializeConfigObject).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), serializeConfigObject(entry.getValue()));
            }
            return out;
        }
        if (value instanceof String string) {
            return normalizeTextColors(string);
        }
        return value;
    }

    private List<File> findCandidateYamlFiles(File sourceRoot) {
        List<File> files = new ArrayList<>();

        File shopsFolder = new File(sourceRoot, "shops");
        if (shopsFolder.exists() && shopsFolder.isDirectory()) {
            files.addAll(walkYamlFiles(shopsFolder.toPath()));
        }

        File menusFolder = new File(sourceRoot, "menus");
        if (menusFolder.exists() && menusFolder.isDirectory()) {
            files.addAll(walkYamlFiles(menusFolder.toPath()));
        }

        if (files.isEmpty()) {
            files.addAll(walkYamlFiles(sourceRoot.toPath()));
        }

        File[] rootYamlFiles = sourceRoot.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".yml") || lower.endsWith(".yaml");
        });
        if (rootYamlFiles != null) {
            files.addAll(List.of(rootYamlFiles));
        }

        files.sort(Comparator.comparing(File::getName));
        return files.stream()
                .distinct()
                .filter(file -> !SKIP_FILE_NAMES.contains(file.getName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<File> walkYamlFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return lower.endsWith(".yml") || lower.endsWith(".yaml");
                    })
                    .map(Path::toFile)
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed walking folder " + root + ": " + exception.getMessage());
            return List.of();
        }
    }

    private List<File> findEconomyShopGuiCandidateFiles(File sourceRoot) {
        List<File> files = new ArrayList<>();
        File shopsFolder = new File(sourceRoot, "shops");
        if (shopsFolder.exists() && shopsFolder.isDirectory()) {
            files.addAll(walkYamlFiles(shopsFolder.toPath()));
        }

        File shopsFile = new File(sourceRoot, "shops.yml");
        if (shopsFile.exists() && shopsFile.isFile()) {
            files.add(shopsFile);
        }

        files.sort(Comparator.comparing(File::getAbsolutePath));
        return files.stream().distinct().toList();
    }

    private Map<String, File> findEconomyShopGuiSectionFiles(File sourceRoot) {
        File shopsFolder = new File(sourceRoot, "shops");
        if (!shopsFolder.exists() || !shopsFolder.isDirectory()) {
            return Map.of();
        }

        Map<String, File> files = new HashMap<>();
        for (File file : walkYamlFiles(shopsFolder.toPath())) {
            String id = normalizeId(stripYaml(file.getName()));
            if (!id.isBlank()) {
                files.put(id, file);
            }
        }
        return files;
    }

    private List<ParseResult> parseEconomyShopGuiFile(File file, Map<String, File> sectionFiles,
                                                      Map<String, MainMenuMeta> mainMenuMetadata,
                                                      Set<Integer> usedMainMenuSlots, int mainMenuSize) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (file.getName().equalsIgnoreCase("shops.yml")) {
            List<ParseResult> results = new ArrayList<>();
            for (String key : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(key);
                String id = normalizeId(key);
                if (section == null || id.isBlank() || sectionFiles.containsKey(id)) {
                    continue;
                }
                results.add(parseEconomyShopGuiSection(file, id, section, mainMenuMetadata, usedMainMenuSlots, mainMenuSize));
            }
            return results;
        }

        String id = normalizeId(stripYaml(file.getName()));
        if (id.isBlank()) {
            return List.of(new ParseResult(null, List.of("Invalid EconomyShopGUI shop file name.")));
        }
        return List.of(parseEconomyShopGuiSection(file, id, yaml, mainMenuMetadata, usedMainMenuSlots, mainMenuSize));
    }

    private ParseResult parseEconomyShopGuiSection(File file, String id, ConfigurationSection source,
                                                   Map<String, MainMenuMeta> mainMenuMetadata,
                                                   Set<Integer> usedMainMenuSlots, int mainMenuSize) {
        YamlConfiguration synthetic = new YamlConfiguration();
        MainMenuMeta meta = findMainMenuMeta(mainMenuMetadata, id).orElse(null);
        String title = meta == null || meta.title() == null || meta.title().isBlank() ? "&8" + id : meta.title();
        Material icon = meta == null || meta.icon() == null ? Material.CHEST : meta.icon();
        List<String> description = meta == null ? List.of() : meta.description();
        int slot = meta == null || meta.slot() < 0 || meta.slot() > 53
                ? nextFreeMainMenuSlot(usedMainMenuSlots, mainMenuSize)
                : meta.slot();
        if (slot >= 0 && slot <= 53) {
            usedMainMenuSlots.add(slot);
        }

        synthetic.set("id", id);
        synthetic.set("title", title);
        List<EconomyItemEntry> entries = extractEconomyShopGuiItems(source);

        synthetic.set("size", economyShopGuiSectionSize(source, entries));
        synthetic.set("icon", icon.name());
        synthetic.set("description", description);
        synthetic.set("enabled", meta == null || meta.enabled());
        synthetic.set("slot", slot);

        Set<String> usedItemIds = new HashSet<>();
        for (EconomyItemEntry entry : entries) {
            String itemId = ensureUniqueId(usedItemIds, normalizeId(entry.id()).isBlank() ? "item" : entry.id());
            String target = "items." + itemId + ".";
            copyEconomyShopGuiItem(entry.section(), synthetic, target, entry.page());
        }

        return parseShopRoot(file, SourcePlugin.ECONOMY_SHOP_GUI.displayName(), false, synthetic, id, synthetic, slot, mainMenuMetadata, Map.of());
    }

    private int economyShopGuiSectionSize(ConfigurationSection source, List<EconomyItemEntry> entries) {
        int rows = 0;
        ConfigurationSection pages = source.getConfigurationSection("pages");
        if (pages != null) {
            for (String key : pages.getKeys(false)) {
                ConfigurationSection page = pages.getConfigurationSection(key);
                if (page != null) {
                    rows = Math.max(rows, page.getInt("gui-rows", 0));
                }
            }
        }
        if (rows > 0) {
            return normalizeSize(rows * 9);
        }

        int highestSlot = -1;
        for (EconomyItemEntry entry : entries) {
            Integer slot = economyShopGuiSlot(entry.section());
            if (slot != null) {
                highestSlot = Math.max(highestSlot, slot);
            }
        }
        if (highestSlot >= 0) {
            return Math.clamp(((highestSlot + 1 + 8) / 9) * 9, 9, 54);
        }
        return 54;
    }

    private List<EconomyItemEntry> extractEconomyShopGuiItems(ConfigurationSection source) {
        ConfigurationSection pages = source.getConfigurationSection("pages");
        if (pages != null) {
            List<EconomyItemEntry> out = new ArrayList<>();
            List<String> pageKeys = pages.getKeys(false).stream()
                    .sorted(Comparator.comparingInt((String key) -> economyShopGuiPageNumber(key, Integer.MAX_VALUE)).thenComparing(String::compareTo))
                    .toList();
            int fallbackPage = 1;
            for (String pageKey : pageKeys) {
                ConfigurationSection page = pages.getConfigurationSection(pageKey);
                if (page == null) {
                    continue;
                }
                ConfigurationSection items = page.getConfigurationSection("items");
                if (items == null) {
                    continue;
                }
                int pageNumber = economyShopGuiPageNumber(pageKey, fallbackPage);
                for (String itemKey : items.getKeys(false)) {
                    ConfigurationSection item = items.getConfigurationSection(itemKey);
                    if (item != null && looksLikeItem(item)) {
                        out.add(new EconomyItemEntry(itemKey, item, pageNumber));
                    }
                }
                fallbackPage++;
            }
            return out;
        }

        List<EconomyItemEntry> out = new ArrayList<>();
        for (String itemKey : source.getKeys(false)) {
            ConfigurationSection item = source.getConfigurationSection(itemKey);
            if (item != null && looksLikeItem(item)) {
                out.add(new EconomyItemEntry(itemKey, item, 1));
            }
        }
        return out;
    }

    private void copyEconomyShopGuiItem(ConfigurationSection source, YamlConfiguration target, String targetPath, int page) {
        for (String path : List.of("material", "type", "buy", "sell", "amount", "stack-size", "custom-model-data", "model", "nbt")) {
            if (source.contains(path)) {
                target.set(targetPath + path, serializeConfigObject(source.get(path)));
            }
        }

        Material material = resolveProductMaterial(source).orElse(null);
        String displayName = resolveCustomProductDisplayName(source, material);
        if (displayName != null) {
            target.set(targetPath + "display-name", displayName);
        }

        List<String> lore = readStringList(source, "lore");
        if (!lore.isEmpty()) {
            target.set(targetPath + "lore", lore.stream().map(this::normalizeTextColors).toList());
        }

        Object enchantments = objectValue(source, "enchantments");
        if (enchantments != null) {
            target.set(targetPath + "enchantments", serializeConfigObject(enchantments));
        }

        Integer slot = economyShopGuiSlot(source);
        if (slot != null) {
            target.set(targetPath + "slot", slot);
        }
        target.set(targetPath + "page", page);
    }

    private int economyShopGuiPageNumber(String pageKey, int fallbackPage) {
        String digits = pageKey == null ? "" : pageKey.replaceAll("\\D+", "");
        if (!digits.isBlank()) {
            return Math.max(1, parseInt(digits, fallbackPage));
        }
        return Math.max(1, fallbackPage);
    }

    private Integer economyShopGuiSlot(ConfigurationSection section) {
        Object raw = objectValue(section, "slot");
        if (raw instanceof Number number) {
            return economyShopGuiSlot(number.intValue());
        }
        if (raw instanceof String string) {
            java.util.regex.Matcher matcher = FIRST_NUMBER.matcher(string);
            if (matcher.find()) {
                return economyShopGuiSlot(parseInt(matcher.group(), -1));
            }
        }
        return null;
    }

    private int economyShopGuiSlot(int slot) {
        if (slot <= 0) {
            return slot;
        }
        return slot - 1;
    }

    private Map<String, MainMenuMeta> loadEconomyShopGuiMainMenuMetadata(File sourceRoot) {
        Map<String, MainMenuMeta> out = new HashMap<>();

        File legacySections = new File(sourceRoot, "sections.yml");
        if (legacySections.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacySections);
            ConfigurationSection sections = yaml.getConfigurationSection("ShopSections");
            if (sections != null) {
                for (String key : sections.getKeys(false)) {
                    ConfigurationSection section = sections.getConfigurationSection(key);
                    if (section != null) {
                        out.put(normalizeId(key), economyShopGuiMainMenuMeta(section));
                    }
                }
            }
        }

        File sectionsFolder = new File(sourceRoot, "sections");
        if (sectionsFolder.exists() && sectionsFolder.isDirectory()) {
            for (File file : walkYamlFiles(sectionsFolder.toPath())) {
                String id = normalizeId(stripYaml(file.getName()));
                if (id.isBlank()) {
                    continue;
                }
                YamlConfiguration section = YamlConfiguration.loadConfiguration(file);
                out.put(id, economyShopGuiMainMenuMeta(section));
            }
        }

        return out;
    }

    private MainMenuMeta economyShopGuiMainMenuMeta(ConfigurationSection section) {
        Integer parsedSlot = economyShopGuiSlot(section);
        int slot = parsedSlot == null ? -1 : parsedSlot;
        Material icon = resolveMaterial(section).orElse(null);
        String title = normalizeTextColors(resolveDisplayName(section));
        List<String> description = firstNonEmpty(
                readStringList(section, "lore"),
                readStringList(section, "description"),
                readStringList(section, "item.lore"),
                readStringList(section, "item.description")
        ).stream().map(this::normalizeTextColors).toList();
        boolean enabled = resolveEnabled(section, true);
        return new MainMenuMeta(slot, icon, buildMainMenuIcon(section, icon, title, description), title, description, enabled);
    }

    private Map<String, MainMenuMeta> loadMainMenuMetadata(File sourceRoot) {
        File configFile = new File(sourceRoot, "config.yml");
        if (!configFile.exists()) {
            return Map.of();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        Map<String, MainMenuMeta> out = new HashMap<>();
        for (String path : List.of("shopMenuItems", "mainMenuItems", "main-menu-items", "main-menu.items", "menu.items")) {
            ConfigurationSection section = yaml.getConfigurationSection(path);
            if (section == null) {
                continue;
            }

            for (String key : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }

                String shopId = normalizeId(firstNonBlank(
                        stringValue(item, "shop"),
                        stringValue(item, "shopId"),
                        stringValue(item, "shopID"),
                        stringValue(item, "shopName"),
                        stringValue(item, "shop-id"),
                        stringValue(item, "open-shop"),
                        stringValue(item, "actions.open-shop")
                ));
                if (shopId.isBlank()) {
                    continue;
                }

                int slot = firstInt(item, -1, "slot", "display-slot", "menu-slot", "position", "item.slot");
                Material icon = resolveMaterial(item).orElse(null);
                String title = normalizeTextColors(resolveDisplayName(item));
                List<String> description = firstNonEmpty(
                        readStringList(item, "description"),
                        readStringList(item, "lore"),
                        readStringList(item, "item.lore"),
                        readStringList(item, "display-item.lore"),
                        readStringList(item, "displayItem.lore")
                ).stream().map(this::normalizeTextColors).toList();
                boolean enabled = resolveEnabled(item, true);
                out.put(shopId, new MainMenuMeta(slot, icon, buildMainMenuIcon(item, icon, title, description), title, description, enabled));
            }
        }
        return out;
    }

    private ItemStack buildMainMenuIcon(ConfigurationSection section, Material icon, String title, List<String> description) {
        Material material = icon == null ? Material.CHEST : icon;
        int amount = Math.clamp(firstInt(section, 1, "amount", "quantity", "item.amount", "item.quantity"), 1, material.getMaxStackSize());
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (title != null && !title.isBlank()) {
                meta.setDisplayName(title);
            }
            if (description != null && !description.isEmpty()) {
                meta.setLore(description);
            }
            Integer customModelData = resolveCustomModelData(section);
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<ParseResult> parseShopFile(File file, String sourceName, Map<String, MainMenuMeta> mainMenuMetadata,
                                            Map<Material, Integer> stackSizeCaps, Set<Integer> usedMainMenuSlots, int mainMenuSize) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String rawId = stripYaml(file.getName());
        List<SectionRoot> roots = findSectionRoots(yaml, rawId);
        List<ParseResult> results = new ArrayList<>();
        for (SectionRoot root : roots) {
            String id = normalizeId(firstNonBlank(
                    root.section().getString("id"),
                    yaml.getString("id"),
                    root.section().getString("shop.id"),
                    yaml.getString("shop.id"),
                    root.rawId()
            ));
            Optional<MainMenuMeta> mainMenuMeta = findMainMenuMeta(mainMenuMetadata, id, root.rawId(), rawId);
            int slot = mainMenuMeta.map(MainMenuMeta::slot)
                    .filter(value -> value >= 0 && value <= 53)
                    .orElseGet(() -> nextFreeMainMenuSlot(usedMainMenuSlots, mainMenuSize));
            if (mainMenuMeta.isPresent() && slot >= 0 && slot <= 53) {
                usedMainMenuSlots.add(slot);
            }
            results.add(parseShopRoot(file, sourceName, true, yaml, root.rawId(), root.section(), slot, mainMenuMetadata, stackSizeCaps));
        }
        return results;
    }

    private Optional<MainMenuMeta> findMainMenuMeta(Map<String, MainMenuMeta> mainMenuMetadata, String... aliases) {
        for (String alias : aliases) {
            String normalized = normalizeId(alias);
            if (normalized.isBlank()) {
                continue;
            }
            MainMenuMeta meta = mainMenuMetadata.get(normalized);
            if (meta != null) {
                return Optional.of(meta);
            }
        }
        return Optional.empty();
    }

    private List<SectionRoot> findSectionRoots(YamlConfiguration yaml, String rawId) {
        for (String path : List.of("shops", "sections", "shop-sections", "shopSections", "categories")) {
            ConfigurationSection collection = yaml.getConfigurationSection(path);
            if (collection == null) {
                continue;
            }

            List<SectionRoot> roots = new ArrayList<>();
            for (String key : collection.getKeys(false)) {
                ConfigurationSection section = collection.getConfigurationSection(key);
                if (section != null && looksLikeSection(section)) {
                    roots.add(new SectionRoot(key, section));
                }
            }
            if (!roots.isEmpty()) {
                return roots;
            }
        }

        String normalizedRawId = normalizeId(rawId);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section != null && normalizeId(key).equals(normalizedRawId)) {
                return List.of(new SectionRoot(key, section));
            }
        }

        List<SectionRoot> candidates = new ArrayList<>();
        for (String key : yaml.getKeys(false)) {
            if (isMetaKey(key)) {
                continue;
            }
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section != null && looksLikeSection(section)) {
                candidates.add(new SectionRoot(key, section));
            }
        }

        return candidates.size() == 1 ? candidates : List.of(new SectionRoot(rawId, yaml));
    }

    private boolean looksLikeSection(ConfigurationSection section) {
        return section.contains("shop-items")
                || section.contains("items")
                || section.contains("contents")
                || section.contains("gui-items")
                || section.contains("menu-settings")
                || section.contains("title")
                || section.contains("name");
    }

    private ParseResult parseShopRoot(File file, String sourceName, boolean disableWhenMissingMainMenuMeta,
                                      YamlConfiguration yaml, String rawId, ConfigurationSection root, int sectionSlot,
                                      Map<String, MainMenuMeta> mainMenuMetadata, Map<Material, Integer> stackSizeCaps) {
        List<String> issues = new ArrayList<>();
        String id = normalizeId(firstNonBlank(
                root.getString("id"),
                yaml.getString("id"),
                root.getString("shop.id"),
                yaml.getString("shop.id"),
                rawId
        ));
        MainMenuMeta mainMenuMeta = findMainMenuMeta(mainMenuMetadata, id, rawId, stripYaml(file.getName())).orElse(null);
        boolean missingMainMenuEntry = !mainMenuMetadata.isEmpty() && mainMenuMeta == null;
        if (missingMainMenuEntry) {
            String disabledText = disableWhenMissingMainMenuMeta ? " and imported it disabled" : "";
            issues.add("No " + sourceName + " main-menu entry found for section '" + id + "'; assigned fallback /foshop slot "
                    + sectionSlot + disabledText + ".");
        }

        String title = normalizeTextColors(firstNonBlank(
                root.getString("menu-settings.name"),
                root.getString("menu-settings.title"),
                root.getString("title"),
                root.getString("name"),
                yaml.getString("menu-settings.name"),
                yaml.getString("menu-settings.title"),
                yaml.getString("title"),
                yaml.getString("name"),
                "&8" + id
        ));

        int rawSize = firstInt(root,
                -1,
                "menu-settings.size",
                "menu-settings.rows",
                "size",
                "rows"
        );
        if (rawSize < 0) {
            rawSize = firstInt(yaml,
                    54,
                    "menu-settings.size",
                    "menu-settings.rows",
                    "size",
                    "rows"
            );
        }
        int size = normalizeSize(rawSize);
        Set<Integer> reservedSlots = resolveReservedSlots(root, yaml, size);

        Material icon = mainMenuMeta != null && mainMenuMeta.icon() != null
                ? mainMenuMeta.icon()
                : material(firstNonBlank(
                root.getString("menu-settings.icon"),
                root.getString("icon"),
                root.getString("menu-settings.material"),
                yaml.getString("menu-settings.icon"),
                yaml.getString("icon"),
                yaml.getString("menu-settings.material")
        )).or(() -> resolveMaterial(root)).orElse(Material.CHEST);

        List<String> description = firstNonEmpty(
                mainMenuMeta == null ? List.of() : mainMenuMeta.description(),
                resolveSectionDescription(root),
                resolveSectionDescription(yaml)
        ).stream().map(this::normalizeTextColors).toList();

        List<ItemEntry> entries = extractItemEntries(root);
        if (entries.isEmpty() && root != yaml) {
            entries = extractItemEntries(yaml);
        }
        if (entries.isEmpty()) {
            issues.add("No product entries found.");
            return new ParseResult(null, issues);
        }

        List<ShopItem> items = new ArrayList<>();
        Set<String> usedSlots = new HashSet<>();
        Set<String> usedItemIds = new HashSet<>();
        int fallbackCount = 0;

        for (ItemEntry entry : entries) {
            ShopItemType type = resolveType(entry.section());
            boolean legacyEnchantmentType = type == ShopItemType.ENCHANTMENT;
            if (legacyEnchantmentType) {
                type = ShopItemType.ITEM;
            }
            Optional<Material> resolvedMaterial = resolveProductMaterial(entry.section());
            Material material = resolvedMaterial.orElse(legacyEnchantmentType ? Material.ENCHANTED_BOOK : defaultMaterial(type));
            if (type == ShopItemType.ITEM && resolvedMaterial.isEmpty() && !legacyEnchantmentType) {
                issues.add("Skipped product " + entry.id() + ": missing material (expected item.material or material).");
                continue;
            }
            if (material == null || material == Material.AIR) {
                issues.add("Skipped product " + entry.id() + ": invalid material.");
                continue;
            }

            int page = resolveProductPage(entry.section());
            int slot = resolveItemSlot(entry.section(), size);
            if (slot < 0 || slot >= size || isReservedSlot(size, slot, reservedSlots) || usedSlots.contains(slotKey(page, slot))) {
                SlotRef fallback = firstFreeSlot(size, usedSlots, reservedSlots);
                fallbackCount++;
                if (fallback == null) {
                    slot = -1;
                } else {
                    page = fallback.page();
                    slot = fallback.slot();
                }
            }
            if (slot < 0) {
                issues.add("Skipped product " + entry.id() + ": no free slot available.");
                break;
            }

            int amount = Math.clamp(firstInt(entry.section(), 1,
                    "amount",
                    "stack",
                    "quantity",
                    "item.amount",
                    "item.quantity",
                    "item.stack"
            ), 1, material.getMaxStackSize());

            double buy = normalizeBundlePrice(resolvePrice(entry.section(), PriceKind.BUY), amount);
            double sell = normalizeBundlePrice(resolvePrice(entry.section(), PriceKind.SELL), amount);
            List<String> lore = resolveLore(entry.section()).stream().map(this::normalizeTextColors).toList();
            String displayName = resolveProductDisplayName(entry.section(), material);
            Integer customModelData = resolveCustomModelData(entry.section());
            Map<String, Integer> enchants = resolveEnchants(entry.section());
            String permission = type == ShopItemType.PERMISSION ? resolvePermission(entry.section()) : null;
            String requiredPermission = type == ShopItemType.PERMISSION ? "" : resolvePermission(entry.section());
            List<String> commands = resolveCommands(entry.section());
            String enchantment = resolveEnchantment(entry.section());
            int enchantmentLevel = Math.max(1, firstInt(entry.section(), 1,
                    "enchantment-level",
                    "enchantmentLevel",
                    "enchantment.level",
                    "item.enchantment-level",
                    "item.enchantmentLevel",
                    "level"
            ));
            Integer stock = firstNonNull(intValue(entry.section(), "stock"), intValue(entry.section(), "max-stock"), intValue(entry.section(), "maxStock"));
            Integer buyLimit = firstNonNull(intValue(entry.section(), "buy-limit"), intValue(entry.section(), "buyLimit"), intValue(entry.section(), "limit.buy"));
            Integer limitResetSeconds = firstNonNull(intValue(entry.section(), "limit-reset-seconds"), intValue(entry.section(), "limitResetSeconds"), intValue(entry.section(), "limit.reset"));
            String rawNbt = firstNonBlank(stringValue(entry.section(), "nbt"), stringValue(entry.section(), "item.nbt"));
            ItemStack itemStack = buildConvertedItemStack(type, material, displayName, lore, customModelData, enchants, enchantment,
                    enchantmentLevel, rawNbt, entry.section(), legacyEnchantmentType);

            String itemId = resolveProductId(entry, material, displayName);
            itemId = ensureUniqueId(usedItemIds, itemId);
            Integer stackSize = firstNonNull(
                    intValue(entry.section(), "stack-size"),
                    intValue(entry.section(), "stackSize"),
                    intValue(entry.section(), "item.stack-size"),
                    intValue(entry.section(), "item.stackSize"),
                    stackSizeCaps.get(material)
            );

            items.add(new ShopItem(itemId, type, material, page, slot, amount, buy, sell, lore, displayName, customModelData,
                    legacyEnchantmentType ? Map.of() : enchants, stackSize,
                    itemStack, permission, requiredPermission, commands, legacyEnchantmentType ? null : enchantment,
                    legacyEnchantmentType ? 1 : enchantmentLevel, stock, buyLimit, limitResetSeconds, rawNbt));
            usedSlots.add(slotKey(page, slot));
        }

        if (items.isEmpty()) {
            issues.add("No valid products remained after parsing.");
            return new ParseResult(null, issues);
        }

        int slot = mainMenuMeta == null || mainMenuMeta.slot() < 0 || mainMenuMeta.slot() > 53 ? sectionSlot : mainMenuMeta.slot();
        if (slot < 0 || slot > 53) {
            issues.add("No valid /foshop main-menu slot available for section '" + id + "'.");
            return new ParseResult(null, issues);
        }
        ItemStack iconItem = mainMenuMeta == null ? null : mainMenuMeta.iconItem();
        boolean enabled = mainMenuMeta == null ? resolveEnabled(root, resolveEnabled(yaml, true)) : mainMenuMeta.enabled();
        if (disableWhenMissingMainMenuMeta && missingMainMenuEntry) {
            enabled = false;
        }
        return new ParseResult(new ParsedSection(id, title, size, icon, iconItem, description, enabled, slot, items, fallbackCount), issues);
    }

    private List<ItemEntry> extractItemEntries(ConfigurationSection yaml) {
        List<ItemEntry> entries = new ArrayList<>();

        addEntriesFromPath(yaml, "shop-items", entries);
        addEntriesFromPath(yaml, "shop", entries);
        addEntriesFromPath(yaml, "items", entries);
        addEntriesFromPath(yaml, "contents", entries);
        addEntriesFromPath(yaml, "gui-items", entries);

        if (!entries.isEmpty()) {
            return entries;
        }

        for (String key : yaml.getKeys(false)) {
            if (isMetaKey(key)) {
                continue;
            }

            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            if (looksLikeItem(section)) {
                entries.add(new ItemEntry(key, section));
                continue;
            }

            int before = entries.size();
            addEntriesFromSection(section, entries);
            if (entries.size() > before) {
                break;
            }
        }

        return dedupeEntries(entries);
    }

    private List<ItemEntry> dedupeEntries(List<ItemEntry> entries) {
        List<ItemEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ItemEntry entry : entries) {
            String key = normalizeId(entry.id());
            if (key.isBlank()) {
                key = entry.id().toLowerCase(Locale.ROOT);
            }
            if (seen.add(key)) {
                out.add(entry);
            }
        }
        return out;
    }

    private void addEntriesFromPath(ConfigurationSection root, String path, List<ItemEntry> entries) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        addEntriesFromSection(section, entries);
    }

    private void addEntriesFromSection(ConfigurationSection section, List<ItemEntry> entries) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }

            if (looksLikeItem(child)) {
                entries.add(new ItemEntry(key, child));
                continue;
            }

            ConfigurationSection nested = child.getConfigurationSection("item");
            if (nested != null && looksLikeItem(child)) {
                entries.add(new ItemEntry(key, child));
            }
        }
    }

    private boolean looksLikeItem(ConfigurationSection section) {
        if (section.contains("material") || section.contains("type") || section.contains("item.type") || section.contains("item.material")) {
            return true;
        }

        if (section.contains("slot") && (section.contains("buy-price") || section.contains("buyPrice") || section.contains("sell-price") || section.contains("sellPrice"))) {
            return true;
        }

        return section.contains("item") && (section.contains("buy") || section.contains("sell") || section.contains("slot"));
    }

    private Optional<Material> resolveMaterial(ConfigurationSection section) {
        String raw = firstNonBlank(
                stringValue(section, "material"),
                stringValue(section, "type"),
                stringValue(section, "item.material"),
                stringValue(section, "item.type"),
                stringValue(section, "item.item"),
                stringValue(section, "itemstack.type"),
                stringValue(section, "display-item.material"),
                stringValue(section, "display-item.item.material"),
                stringValue(section, "displayItem.material"),
                stringValue(section, "displayItem.item.material")
        );

        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        return material(raw);
    }

    private Optional<Material> resolveProductMaterial(ConfigurationSection section) {
        Optional<Material> explicit = material(firstNonBlank(
                stringValue(section, "material"),
                stringValue(section, "item.material"),
                stringValue(section, "item.type"),
                stringValue(section, "item.item"),
                stringValue(section, "itemstack.material"),
                stringValue(section, "itemstack.type"),
                stringValue(section, "display-item.material"),
                stringValue(section, "display-item.item.material"),
                stringValue(section, "displayItem.material"),
                stringValue(section, "displayItem.item.material")
        ));
        if (explicit.isPresent()) {
            return explicit;
        }

        String rawTopLevelType = stringValue(section, "type");
        if (rawTopLevelType == null || rawTopLevelType.isBlank() || isProductTypeKeyword(rawTopLevelType)) {
            return Optional.empty();
        }
        return material(rawTopLevelType);
    }

    private boolean isProductTypeKeyword(String raw) {
        if (raw == null) {
            return false;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        return cleaned.equals("item")
                || cleaned.equals("permission")
                || cleaned.equals("perm")
                || cleaned.equals("enchantment")
                || cleaned.equals("enchant")
                || cleaned.equals("command")
                || cleaned.equals("commands")
                || cleaned.equals("cmd")
                || cleaned.equals("dummy")
                || cleaned.equals("display")
                || cleaned.equals("decoration");
    }

    private ShopItemType resolveType(ConfigurationSection section) {
        return ShopItemType.fromString(firstNonBlank(
                stringValue(section, "type"),
                stringValue(section, "item.type-kind"),
                stringValue(section, "shop-item-type")
        ));
    }

    private int resolveProductPage(ConfigurationSection section) {
        Integer rawPage = firstNonNull(
                intValue(section, "page"),
                intValue(section, "item.page")
        );
        if (rawPage == null || rawPage <= 1) {
            return 0;
        }
        return rawPage - 1;
    }

    private Material defaultMaterial(ShopItemType type) {
        return switch (type) {
            case PERMISSION -> Material.WRITABLE_BOOK;
            case ENCHANTMENT -> Material.ENCHANTED_BOOK;
            case COMMAND -> Material.COMMAND_BLOCK;
            case DUMMY -> Material.GRAY_STAINED_GLASS_PANE;
            case ITEM -> Material.STONE;
        };
    }

    private int resolveItemSlot(ConfigurationSection section, int size) {
        int slot = firstInt(section, -1,
                "slot",
                "display-slot",
                "menu-slot",
                "position",
                "item.slot"
        );

        if (slot < 0 && section.contains("row") && section.contains("column")) {
            int row = section.getInt("row", 1);
            int column = section.getInt("column", 1);
            slot = ((Math.max(1, row) - 1) * 9) + (Math.max(1, column) - 1);
        }

        if (slot >= size) {
            return -1;
        }
        return slot;
    }

    private List<String> resolveLore(ConfigurationSection section) {
        return firstNonEmpty(
                readStringList(section, "lore"),
                readStringList(section, "description"),
                readStringList(section, "item.lore"),
                readStringList(section, "item.description"),
                readStringList(section, "itemstack.lore"),
                readStringList(section, "display-item.lore"),
                readStringList(section, "display-item.item.lore"),
                readStringList(section, "displayItem.lore"),
                readStringList(section, "displayItem.item.lore")
        );
    }

    private List<String> resolveSectionDescription(ConfigurationSection section) {
        return firstNonEmpty(
                readStringList(section, "description"),
                readStringList(section, "lore"),
                readStringList(section, "menu-settings.description"),
                readStringList(section, "menu-settings.lore"),
                readStringList(section, "display-item.lore"),
                readStringList(section, "display-item.item.lore"),
                readStringList(section, "displayItem.lore"),
                readStringList(section, "displayItem.item.lore")
        );
    }

    private String resolveDisplayName(ConfigurationSection section) {
        return firstNonBlank(
                stringValue(section, "display-name"),
                stringValue(section, "displayName"),
                stringValue(section, "displayname"),
                stringValue(section, "name"),
                stringValue(section, "item.display-name"),
                stringValue(section, "item.displayName"),
                stringValue(section, "item.displayname"),
                stringValue(section, "item.name"),
                stringValue(section, "itemstack.display-name"),
                stringValue(section, "itemstack.displayname"),
                stringValue(section, "itemstack.name"),
                stringValue(section, "display-item.display-name"),
                stringValue(section, "display-item.displayName"),
                stringValue(section, "display-item.displayname"),
                stringValue(section, "display-item.name"),
                stringValue(section, "display-item.item.display-name"),
                stringValue(section, "display-item.item.name"),
                stringValue(section, "displayItem.display-name"),
                stringValue(section, "displayItem.displayName"),
                stringValue(section, "displayItem.displayname"),
                stringValue(section, "displayItem.name"),
                stringValue(section, "displayItem.item.display-name"),
                stringValue(section, "displayItem.item.name")
        );
    }

    private String resolveProductDisplayName(ConfigurationSection section, Material material) {
        String explicit = resolveCustomProductDisplayName(section, material);
        if (explicit != null) {
            return explicit;
        }

        String mob = firstNonBlank(
                stringValue(section, "mob"),
                stringValue(section, "item.mob"),
                stringValue(section, "entity"),
                stringValue(section, "item.entity"),
                stringValue(section, "spawner"),
                stringValue(section, "item.spawner")
        );
        if (mob != null && !mob.isBlank() && material == Material.SPAWNER) {
            return prettifyMaterial(mob) + " Spawner";
        }

        return null;
    }

    private String resolveCustomProductDisplayName(ConfigurationSection section, Material material) {
        String explicit = resolveDisplayName(section);
        if (explicit == null || explicit.isBlank()) {
            return null;
        }

        String normalized = normalizeTextColors(explicit);
        if (material != null && VanillaItemNames.isVanillaName(material, normalized)) {
            return null;
        }
        return normalized;
    }

    private String resolveProductId(ItemEntry entry, Material material, String displayName) {
        String raw = stripColorCodes(displayName);
        if (raw == null || raw.isBlank()) {
            raw = material.name();
        }
        String id = normalizeId(raw);
        if (id.isBlank() || id.matches("\\d+")) {
            id = normalizeId(material.name());
        }
        return id;
    }

    private Integer resolveCustomModelData(ConfigurationSection section) {
        return firstNonNull(
                intValue(section, "custom-model-data"),
                intValue(section, "customModelData"),
                intValue(section, "model"),
                intValue(section, "item.custom-model-data"),
                intValue(section, "item.customModelData"),
                intValue(section, "item.model"),
                intValue(section, "itemstack.custom-model-data"),
                intValue(section, "itemstack.customModelData"),
                intValue(section, "display-item.custom-model-data"),
                intValue(section, "display-item.customModelData"),
                intValue(section, "displayItem.custom-model-data"),
                intValue(section, "displayItem.customModelData")
        );
    }

    private Map<String, Integer> resolveEnchants(ConfigurationSection section) {
        Map<String, Integer> enchants = new HashMap<>();
        for (String path : List.of("enchants", "enchantments", "item.enchants", "item.enchantments", "itemstack.enchants", "itemstack.enchantments")) {
            Object object = objectValue(section, path);
            if (object instanceof ConfigurationSection enchantSection) {
                for (String key : enchantSection.getKeys(false)) {
                    String normalized = normalizeEnchantmentKey(key);
                    if (normalized != null) {
                        enchants.put(normalized, Math.max(1, enchantSection.getInt(key, 1)));
                    }
                }
            } else if (object instanceof List<?> list) {
                for (Object entry : list) {
                    parseEnchantEntry(String.valueOf(entry)).ifPresent(parsed -> enchants.put(parsed.name(), parsed.level()));
                }
            } else if (object instanceof String string) {
                parseEnchantEntry(string).ifPresent(parsed -> enchants.put(parsed.name(), parsed.level()));
            }
        }
        return enchants;
    }

    private Optional<ParsedEnchant> parseEnchantEntry(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String cleaned = input.trim();
        String[] split = cleaned.split("[,; ]+");
        String rawName = split.length == 0 ? "" : split[0];
        if (rawName.isBlank()) {
            return Optional.empty();
        }

        int level = 1;
        if (split.length >= 2) {
            try {
                level = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        } else {
            int lastColon = cleaned.lastIndexOf(':');
            if (lastColon > 0 && lastColon < cleaned.length() - 1) {
                String tail = cleaned.substring(lastColon + 1);
                try {
                    level = Integer.parseInt(tail);
                    rawName = cleaned.substring(0, lastColon);
                } catch (NumberFormatException ignored) {
                    level = 1;
                }
            }
        }
        String normalized = normalizeEnchantmentKey(rawName);
        return normalized == null ? Optional.empty() : Optional.of(new ParsedEnchant(normalized, Math.max(1, level)));
    }

    private String resolvePermission(ConfigurationSection section) {
        return firstNonBlank(
                stringValue(section, "permission"),
                stringValue(section, "permission-node"),
                stringValue(section, "node"),
                stringValue(section, "item.permission")
        );
    }

    private List<String> resolveCommands(ConfigurationSection section) {
        List<String> commands = new ArrayList<>();
        for (String path : List.of(
                "commands",
                "command",
                "console-commands",
                "player-commands",
                "commandsOnClick",
                "commandsOnClickConsole",
                "commandsOnClickPlayer",
                "item.commands",
                "item.commandsOnClick",
                "item.commandsOnClickConsole",
                "item.commandsOnClickPlayer"
        )) {
            Object object = objectValue(section, path);
            if (object instanceof List<?> list) {
                for (Object entry : list) {
                    String command = normalizeCommand(String.valueOf(entry).trim());
                    if (!command.isBlank()) {
                        commands.add(command);
                    }
                }
            } else if (object instanceof String string && !string.isBlank()) {
                commands.add(normalizeCommand(string));
            }
        }
        return commands;
    }

    private String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        return command.trim()
                .replace("%PLAYER%", "{player}")
                .replace("%player%", "{player}")
                .replace("%AMOUNT%", "{amount}")
                .replace("%amount%", "{amount}");
    }

    private String resolveEnchantment(ConfigurationSection section) {
        return normalizeEnchantmentKey(firstNonBlank(
                stringValue(section, "enchantment"),
                stringValue(section, "enchant"),
                stringValue(section, "item.enchantment")
        ));
    }

    private ItemStack buildConvertedItemStack(ShopItemType type, Material material, String displayName, List<String> lore,
                                              Integer customModelData, Map<String, Integer> enchants, String enchantment,
                                              int enchantmentLevel, String rawNbt, ConfigurationSection section,
                                              boolean legacyEnchantmentType) {
        ItemStack serialized = section.getItemStack("item-stack");
        if (serialized == null) {
            serialized = section.getItemStack("itemstack");
        }
        ItemStack item = serialized == null
                ? new ItemStack(legacyEnchantmentType ? Material.ENCHANTED_BOOK : material, 1)
                : serialized.clone();
        item.setAmount(1);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isBlank()) {
                meta.setDisplayName(displayName);
            } else if (meta.hasDisplayName() && VanillaItemNames.isVanillaName(material, meta.getDisplayName())) {
                meta.setDisplayName(null);
            }
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            if (booleanValue(section, "unbreakable") || booleanValue(section, "item.unbreakable")) {
                meta.setUnbreakable(true);
            }
            for (String flag : firstNonEmpty(readStringList(section, "flags"), readStringList(section, "item.flags"), readStringList(section, "item-flags"))) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                Enchantment parsed = parseEnchantment(entry.getKey());
                if (parsed != null) {
                    if (meta instanceof EnchantmentStorageMeta storageMeta) {
                        storageMeta.addStoredEnchant(parsed, entry.getValue(), true);
                        meta = storageMeta;
                    } else {
                        meta.addEnchant(parsed, entry.getValue(), true);
                    }
                }
            }
            if (legacyEnchantmentType && meta instanceof EnchantmentStorageMeta storageMeta) {
                Enchantment parsed = parseEnchantment(enchantment);
                if (parsed != null) {
                    storageMeta.addStoredEnchant(parsed, Math.max(1, enchantmentLevel), true);
                    meta = storageMeta;
                }
            }
            applyPotionMeta(meta, section);
            applyBannerMeta(meta, section);
            applyFireworkMeta(meta, section);
            applySkullMeta(meta, section);
            item.setItemMeta(meta);
        }

        if (type == ShopItemType.ITEM
                && (rawNbt == null || rawNbt.isBlank())
                && displayName == null
                && lore.isEmpty()
                && customModelData == null
                && enchants.isEmpty()
                && VanillaItemNames.isVanillaEquivalentTemplate(item, material)) {
            return null;
        }

        // Raw NBT is preserved in FoShop config and applied at runtime when Bukkit exposes the unsafe modifier.
        return item;
    }

    private void applyPotionMeta(ItemMeta meta, ConfigurationSection section) {
        if (!(meta instanceof PotionMeta potionMeta)) {
            return;
        }

        String rawType = firstNonBlank(
                stringValue(section, "potion-type"),
                stringValue(section, "potion.type"),
                stringValue(section, "potion"),
                stringValue(section, "item.potion-type"),
                stringValue(section, "item.potion.type")
        );
        PotionType type = parsePotionType(rawType);
        if (type != null) {
            boolean extended = booleanValue(section, "potion.extended") || booleanValue(section, "extended");
            boolean upgraded = booleanValue(section, "potion.upgraded") || booleanValue(section, "upgraded");
            if (extended || upgraded) {
                try {
                    potionMeta.setBasePotionData(new PotionData(type, extended, upgraded));
                } catch (IllegalArgumentException ignored) {
                    potionMeta.setBasePotionType(type);
                }
            } else {
                potionMeta.setBasePotionType(type);
            }
        }

        Color color = parseColor(firstNonBlank(
                stringValue(section, "potion-color"),
                stringValue(section, "potion.color"),
                stringValue(section, "item.potion-color"),
                stringValue(section, "item.potion.color")
        ));
        if (color != null) {
            potionMeta.setColor(color);
        }

        for (PotionEffect effect : resolvePotionEffects(section)) {
            potionMeta.addCustomEffect(effect, true);
        }
    }

    private List<PotionEffect> resolvePotionEffects(ConfigurationSection section) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String path : List.of("potion-effects", "potion.effects", "item.potion-effects", "item.potion.effects")) {
            Object object = objectValue(section, path);
            if (object instanceof ConfigurationSection effectsSection) {
                for (String key : effectsSection.getKeys(false)) {
                    ConfigurationSection child = effectsSection.getConfigurationSection(key);
                    if (child != null) {
                        parsePotionEffect(key, child).ifPresent(effects::add);
                    } else {
                        parsePotionEffectString(key + ":" + String.valueOf(effectsSection.get(key))).ifPresent(effects::add);
                    }
                }
            } else if (object instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        parsePotionEffect(map).ifPresent(effects::add);
                    } else {
                        parsePotionEffectString(String.valueOf(entry)).ifPresent(effects::add);
                    }
                }
            } else if (object instanceof String string) {
                parsePotionEffectString(string).ifPresent(effects::add);
            }
        }
        return effects;
    }

    private Optional<PotionEffect> parsePotionEffect(String fallbackType, ConfigurationSection section) {
        String rawType = firstNonBlank(stringValue(section, "type"), stringValue(section, "effect"), fallbackType);
        PotionEffectType type = parsePotionEffectType(rawType);
        if (type == null) {
            return Optional.empty();
        }
        int duration = firstInt(section, 600, "duration", "time", "ticks");
        int amplifier = firstInt(section, 0, "amplifier", "amp");
        if (section.contains("level") && !section.contains("amplifier") && !section.contains("amp")) {
            amplifier = Math.max(0, section.getInt("level", 1) - 1);
        }
        return Optional.of(new PotionEffect(type, Math.max(1, duration), Math.max(0, amplifier),
                booleanValue(section, "ambient"), !booleanValue(section, "hide-particles"), !booleanValue(section, "hide-icon")));
    }

    private Optional<PotionEffect> parsePotionEffect(Map<?, ?> map) {
        String rawType = firstNonBlank(
                mapString(map, "type"),
                mapString(map, "effect"),
                mapString(map, "name")
        );
        PotionEffectType type = parsePotionEffectType(rawType);
        if (type == null) {
            return Optional.empty();
        }
        int duration = mapInt(map, 600, "duration", "time", "ticks");
        int amplifier = map.containsKey("level") && !map.containsKey("amplifier")
                ? Math.max(0, mapInt(map, 1, "level") - 1)
                : mapInt(map, 0, "amplifier", "amp");
        return Optional.of(new PotionEffect(type, Math.max(1, duration), Math.max(0, amplifier)));
    }

    private Optional<PotionEffect> parsePotionEffectString(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String[] split = input.split("[:;, ]+");
        PotionEffectType type = parsePotionEffectType(split[0]);
        if (type == null) {
            return Optional.empty();
        }
        int duration = split.length >= 2 ? parseInt(split[1], 600) : 600;
        int amplifier = split.length >= 3 ? parseInt(split[2], 0) : 0;
        return Optional.of(new PotionEffect(type, Math.max(1, duration), Math.max(0, amplifier)));
    }

    private void applyBannerMeta(ItemMeta meta, ConfigurationSection section) {
        if (!(meta instanceof BannerMeta bannerMeta)) {
            return;
        }

        List<Pattern> patterns = resolveBannerPatterns(section);
        if (!patterns.isEmpty()) {
            bannerMeta.setPatterns(patterns);
        }
    }

    private List<Pattern> resolveBannerPatterns(ConfigurationSection section) {
        List<Pattern> patterns = new ArrayList<>();
        for (String path : List.of("patterns", "banner.patterns", "item.patterns", "item.banner.patterns")) {
            Object object = objectValue(section, path);
            if (object instanceof ConfigurationSection patternSection) {
                for (String key : patternSection.getKeys(false)) {
                    ConfigurationSection child = patternSection.getConfigurationSection(key);
                    if (child != null) {
                        parseBannerPattern(child).ifPresent(patterns::add);
                    } else {
                        parseBannerPatternString(String.valueOf(patternSection.get(key))).ifPresent(patterns::add);
                    }
                }
            } else if (object instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        parseBannerPattern(map).ifPresent(patterns::add);
                    } else {
                        parseBannerPatternString(String.valueOf(entry)).ifPresent(patterns::add);
                    }
                }
            } else if (object instanceof String string) {
                parseBannerPatternString(string).ifPresent(patterns::add);
            }
        }
        return patterns;
    }

    private Optional<Pattern> parseBannerPattern(ConfigurationSection section) {
        DyeColor color = parseDyeColor(firstNonBlank(stringValue(section, "color"), stringValue(section, "dye")));
        PatternType type = parsePatternType(firstNonBlank(stringValue(section, "pattern"), stringValue(section, "type")));
        return color == null || type == null ? Optional.empty() : Optional.of(new Pattern(color, type));
    }

    private Optional<Pattern> parseBannerPattern(Map<?, ?> map) {
        DyeColor color = parseDyeColor(firstNonBlank(mapString(map, "color"), mapString(map, "dye")));
        PatternType type = parsePatternType(firstNonBlank(mapString(map, "pattern"), mapString(map, "type")));
        return color == null || type == null ? Optional.empty() : Optional.of(new Pattern(color, type));
    }

    private Optional<Pattern> parseBannerPatternString(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String[] split = input.split("[:;, ]+");
        if (split.length < 2) {
            return Optional.empty();
        }
        DyeColor color = parseDyeColor(split[0]);
        PatternType type = parsePatternType(split[1]);
        return color == null || type == null ? Optional.empty() : Optional.of(new Pattern(color, type));
    }

    private void applyFireworkMeta(ItemMeta meta, ConfigurationSection section) {
        if (!(meta instanceof FireworkMeta fireworkMeta)) {
            return;
        }

        int power = firstInt(section, -1, "firework-power", "firework.power", "item.firework-power", "item.firework.power", "power");
        if (power >= 0) {
            fireworkMeta.setPower(Math.clamp(power, 0, 3));
        }

        for (FireworkEffect effect : resolveFireworkEffects(section)) {
            fireworkMeta.addEffect(effect);
        }
    }

    private List<FireworkEffect> resolveFireworkEffects(ConfigurationSection section) {
        List<FireworkEffect> effects = new ArrayList<>();
        for (String path : List.of("firework-effects", "firework.effects", "item.firework-effects", "item.firework.effects")) {
            Object object = objectValue(section, path);
            if (object instanceof ConfigurationSection effectsSection) {
                for (String key : effectsSection.getKeys(false)) {
                    ConfigurationSection child = effectsSection.getConfigurationSection(key);
                    if (child != null) {
                        parseFireworkEffect(child).ifPresent(effects::add);
                    } else {
                        parseFireworkEffectString(String.valueOf(effectsSection.get(key))).ifPresent(effects::add);
                    }
                }
            } else if (object instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        parseFireworkEffect(map).ifPresent(effects::add);
                    } else {
                        parseFireworkEffectString(String.valueOf(entry)).ifPresent(effects::add);
                    }
                }
            } else if (object instanceof String string) {
                parseFireworkEffectString(string).ifPresent(effects::add);
            }
        }
        return effects;
    }

    private Optional<FireworkEffect> parseFireworkEffect(ConfigurationSection section) {
        FireworkEffect.Type type = parseFireworkType(stringValue(section, "type"));
        List<Color> colors = parseColors(firstNonEmpty(readStringList(section, "colors"), readStringList(section, "color")));
        List<Color> fades = parseColors(firstNonEmpty(readStringList(section, "fade-colors"), readStringList(section, "fade")));
        return buildFireworkEffect(type, colors, fades, booleanValue(section, "flicker"), booleanValue(section, "trail"));
    }

    private Optional<FireworkEffect> parseFireworkEffect(Map<?, ?> map) {
        FireworkEffect.Type type = parseFireworkType(mapString(map, "type"));
        List<Color> colors = parseColors(mapStringList(map, "colors", "color"));
        List<Color> fades = parseColors(mapStringList(map, "fade-colors", "fade"));
        return buildFireworkEffect(type, colors, fades, mapBoolean(map, "flicker"), mapBoolean(map, "trail"));
    }

    private Optional<FireworkEffect> parseFireworkEffectString(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String[] split = input.split(":");
        FireworkEffect.Type type = parseFireworkType(split[0]);
        List<Color> colors = split.length >= 2 ? parseColors(List.of(split[1].split(","))) : List.of();
        List<Color> fades = split.length >= 3 ? parseColors(List.of(split[2].split(","))) : List.of();
        return buildFireworkEffect(type, colors, fades, false, false);
    }

    private Optional<FireworkEffect> buildFireworkEffect(FireworkEffect.Type type, List<Color> colors, List<Color> fades, boolean flicker, boolean trail) {
        try {
            FireworkEffect.Builder builder = FireworkEffect.builder().with(type == null ? FireworkEffect.Type.BALL : type);
            builder.withColor(colors.isEmpty() ? List.of(Color.WHITE) : colors);
            if (!fades.isEmpty()) {
                builder.withFade(fades);
            }
            builder.flicker(flicker);
            builder.trail(trail);
            return Optional.of(builder.build());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void applySkullMeta(ItemMeta meta, ConfigurationSection section) {
        if (!(meta instanceof SkullMeta skullMeta)) {
            return;
        }

        String owner = firstNonBlank(
                stringValue(section, "skull-owner"),
                stringValue(section, "skull.owner"),
                stringValue(section, "owner"),
                stringValue(section, "player"),
                stringValue(section, "item.skull-owner"),
                stringValue(section, "item.skull.owner")
        );
        if (owner != null && !owner.isBlank()) {
            skullMeta.setOwner(owner);
        }

        String skin = firstNonBlank(
                stringValue(section, "skull-texture"),
                stringValue(section, "skull.texture"),
                stringValue(section, "skin"),
                stringValue(section, "texture"),
                stringValue(section, "skin-url"),
                stringValue(section, "item.skull-texture"),
                stringValue(section, "item.skull.texture")
        );
        applySkullTexture(skullMeta, skin);
    }

    private void applySkullTexture(SkullMeta skullMeta, String rawSkin) {
        String skinUrl = extractSkinUrl(rawSkin);
        if (skinUrl == null || skinUrl.isBlank()) {
            return;
        }

        try {
            var profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(skinUrl.getBytes(StandardCharsets.UTF_8)));
            var textures = profile.getTextures();
            textures.setSkin(new URL(skinUrl));
            profile.setTextures(textures);
            skullMeta.setOwnerProfile(profile);
        } catch (MalformedURLException | IllegalArgumentException ignored) {
        }
    }

    private String extractSkinUrl(String rawSkin) {
        if (rawSkin == null || rawSkin.isBlank()) {
            return null;
        }
        String trimmed = rawSkin.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
            int urlIndex = decoded.indexOf("\"url\"");
            if (urlIndex < 0) {
                return null;
            }
            int colon = decoded.indexOf(':', urlIndex);
            int firstQuote = decoded.indexOf('"', colon + 1);
            int secondQuote = decoded.indexOf('"', firstQuote + 1);
            if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
                return null;
            }
            return decoded.substring(firstQuote + 1, secondQuote).replace("\\/", "/");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private PotionType parsePotionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PotionType.valueOf(cleanEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PotionEffectType parsePotionEffectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(cleaned.contains(":") ? cleaned : "minecraft:" + cleaned);
        PotionEffectType type = key == null ? null : PotionEffectType.getByKey(key);
        if (type == null) {
            type = PotionEffectType.getByName(cleanEnum(raw));
        }
        return type;
    }

    private DyeColor parseDyeColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DyeColor.valueOf(cleanEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PatternType parsePatternType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = cleanEnum(raw);
        try {
            return PatternType.valueOf(cleaned);
        } catch (IllegalArgumentException ignored) {
            return PatternType.getByIdentifier(raw.toLowerCase(Locale.ROOT).replace('_', '-'));
        }
    }

    private FireworkEffect.Type parseFireworkType(String raw) {
        if (raw == null || raw.isBlank()) {
            return FireworkEffect.Type.BALL;
        }
        try {
            return FireworkEffect.Type.valueOf(cleanEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return FireworkEffect.Type.BALL;
        }
    }

    private List<Color> parseColors(List<String> values) {
        List<Color> colors = new ArrayList<>();
        for (String value : values) {
            Color color = parseColor(value);
            if (color != null) {
                colors.add(color);
            }
        }
        return colors;
    }

    private Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.startsWith("0x")) {
            cleaned = cleaned.substring(2);
        }
        if (cleaned.contains(",")) {
            String[] split = cleaned.split(",");
            if (split.length >= 3) {
                return Color.fromRGB(clampColor(parseInt(split[0], 255)), clampColor(parseInt(split[1], 255)), clampColor(parseInt(split[2], 255)));
            }
        }
        if (cleaned.matches("[0-9a-fA-F]{6}")) {
            return Color.fromRGB(Integer.parseInt(cleaned, 16));
        }
        DyeColor dyeColor = parseDyeColor(cleaned);
        return dyeColor == null ? null : dyeColor.getFireworkColor();
    }

    private int clampColor(int value) {
        return Math.clamp(value, 0, 255);
    }

    private String cleanEnum(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String mapString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int mapInt(Map<?, ?> map, int fallback, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String string) {
                return parseInt(string, fallback);
            }
        }
        return fallback;
    }

    private boolean mapBoolean(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String string && Boolean.parseBoolean(string);
    }

    private List<String> mapStringList(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            if (value instanceof String string && !string.isBlank()) {
                if (string.contains(",")) {
                    return List.of(string.split(","));
                }
                return List.of(string);
            }
        }
        return List.of();
    }

    private Enchantment parseEnchantment(String key) {
        return ItemKeys.enchantment(key);
    }

    private String normalizeEnchantmentKey(String raw) {
        return ItemKeys.normalizeEnchantmentKey(raw);
    }

    private boolean booleanValue(ConfigurationSection section, String path) {
        Object object = objectValue(section, path);
        if (object instanceof Boolean bool) {
            return bool;
        }
        return object instanceof String string && Boolean.parseBoolean(string);
    }

    private boolean resolveEnabled(ConfigurationSection section, boolean fallback) {
        Boolean enabled = firstNonNull(
                booleanObjectValue(section, "enabled"),
                booleanObjectValue(section, "enable"),
                booleanObjectValue(section, "isEnabled")
        );
        Boolean disabled = firstNonNull(
                booleanObjectValue(section, "disabled"),
                booleanObjectValue(section, "disable"),
                booleanObjectValue(section, "hidden")
        );
        return (enabled == null ? fallback : enabled) && !Boolean.TRUE.equals(disabled);
    }

    private Boolean booleanObjectValue(ConfigurationSection section, String path) {
        Object object = objectValue(section, path);
        if (object instanceof Boolean bool) {
            return bool;
        }
        if (object instanceof String string && !string.isBlank()) {
            return Boolean.parseBoolean(string.trim());
        }
        return null;
    }

    private double resolvePrice(ConfigurationSection section, PriceKind kind) {
        List<String> paths = kind == PriceKind.BUY
                ? List.of(
                "buy-price", "buyPrice", "buy", "buy.price", "price.buy", "prices.buy", "buyprice", "cost", "cost.buy"
        )
                : List.of(
                "sell-price", "sellPrice", "sell", "sell.price", "price.sell", "prices.sell", "sellprice", "value", "value.sell"
        );

        for (String path : paths) {
            Double parsed = doubleValue(section, path);
            if (parsed != null) {
                if (parsed < 0D || !Double.isFinite(parsed)) {
                    return -1D;
                }
                return parsed;
            }
        }

        return -1D;
    }

    private double normalizeBundlePrice(double price, int amount) {
        if (price < 0D || !Double.isFinite(price) || amount <= 1) {
            return price;
        }
        return price / amount;
    }

    private Double doubleValue(ConfigurationSection section, String path) {
        Object object = objectValue(section, path);
        if (object == null) {
            return null;
        }

        if (object instanceof Number number) {
            return number.doubleValue();
        }

        if (object instanceof String string) {
            String cleaned = string.trim();
            if (cleaned.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(cleaned);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (object instanceof ConfigurationSection nested) {
            return firstNonNull(
                    doubleValue(nested, "price"),
                    doubleValue(nested, "amount"),
                    doubleValue(nested, "value")
            );
        }

        return null;
    }

    private Integer intValue(ConfigurationSection section, String path) {
        Object object = objectValue(section, path);
        if (object == null) {
            return null;
        }

        if (object instanceof Number number) {
            return number.intValue();
        }

        if (object instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String stringValue(ConfigurationSection section, String path) {
        Object object = objectValue(section, path);
        if (object == null) {
            return null;
        }
        return String.valueOf(object);
    }

    private Object objectValue(ConfigurationSection section, String path) {
        String[] parts = path.split("\\.");
        ConfigurationSection current = section;
        for (int i = 0; i < parts.length - 1; i++) {
            current = current.getConfigurationSection(parts[i]);
            if (current == null) {
                return null;
            }
        }
        return current.get(parts[parts.length - 1]);
    }

    private List<String> readStringList(ConfigurationSection section, String path) {
        List<String> list = section.getStringList(path);
        if (!list.isEmpty()) {
            return list;
        }
        String single = stringValue(section, path);
        if (single != null && !single.isBlank()) {
            return List.of(single);
        }
        return List.of();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final List<String> firstNonEmpty(List<String>... values) {
        for (List<String> value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return List.of();
    }

    private int firstInt(ConfigurationSection section, int defaultValue, String... paths) {
        for (String path : paths) {
            Object object = objectValue(section, path);
            if (object == null) {
                continue;
            }

            if (object instanceof Number number) {
                int value = number.intValue();
                if (path.endsWith("rows") || path.equals("rows")) {
                    return value * 9;
                }
                return value;
            }

            if (object instanceof String string) {
                try {
                    int value = Integer.parseInt(string.trim());
                    if (path.endsWith("rows") || path.equals("rows")) {
                        return value * 9;
                    }
                    return value;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return defaultValue;
    }

    private Optional<Material> material(String raw) {
        return ItemKeys.material(raw);
    }

    private boolean isMetaKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.equals("menu-settings")
                || lower.equals("settings")
                || lower.equals("permission")
                || lower.equals("permissions")
                || lower.equals("commands")
                || lower.equals("id")
                || lower.equals("title")
                || lower.equals("name")
                || lower.equals("icon")
                || lower.equals("size")
                || lower.equals("rows")
                || lower.equals("shop")
                || lower.equals("items")
                || lower.equals("shop-items")
                || lower.equals("contents")
                || lower.equals("gui-items")
                || lower.equals("shops")
                || lower.equals("sections")
                || lower.equals("shop-sections")
                || lower.equals("categories");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String ensureUniqueId(Set<String> usedIds, String base) {
        String normalized = normalizeId(base);
        if (normalized.isBlank()) {
            normalized = "section";
        }

        String candidate = normalized;
        int index = 2;
        while (!usedIds.add(candidate)) {
            candidate = normalized + "_" + index;
            index++;
        }
        return candidate;
    }

    private String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        normalized = normalized.replaceAll("[^a-z0-9_\\-]", "");
        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40);
        }
        return normalized;
    }

    private String stripYaml(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        if (lower.endsWith(".yaml")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private String normalizeTextColors(String input) {
        if (input == null) {
            return null;
        }
        return RAW_HEX_COLOR.matcher(input).replaceAll("&$1");
    }

    private String stripColorCodes(String input) {
        if (input == null) {
            return null;
        }
        return normalizeTextColors(input)
                .replaceAll("&#[A-Fa-f0-9]{6}", "")
                .replaceAll("(?i)&[0-9A-FK-ORX]", "")
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim();
    }

    private String prettifyMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\- ]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private int normalizeSize(int input) {
        int rows = Math.clamp(input / 9, 1, 6);
        return rows * 9;
    }

    private SlotRef firstFreeSlot(int size, Set<String> usedSlots, Set<Integer> reservedSlots) {
        for (int page = 0; page < 100; page++) {
            for (int slot = 0; slot < size; slot++) {
                if (isReservedSlot(size, slot, reservedSlots)) {
                    continue;
                }
                if (!usedSlots.contains(slotKey(page, slot))) {
                    return new SlotRef(page, slot);
                }
            }
        }
        return null;
    }

    private String slotKey(int page, int slot) {
        return page + ":" + slot;
    }

    private boolean isReservedSlot(int size, int slot, Set<Integer> reservedSlots) {
        return reservedSlots.contains(slot);
    }

    private Set<Integer> resolveReservedSlots(ConfigurationSection root, YamlConfiguration yaml, int size) {
        Set<Integer> reserved = new HashSet<>();
        reserveSlot(reserved, size, size - 9);
        reserveSlot(reserved, size, size - 5);
        reserveSlot(reserved, size, size - 1);
        addButtonReservedSlots(root, size, reserved);
        if (root != yaml) {
            addButtonReservedSlots(yaml, size, reserved);
        }
        return reserved;
    }

    private void addButtonReservedSlots(ConfigurationSection section, int size, Set<Integer> reserved) {
        for (String path : List.of(
                "buttons.goBack.slot",
                "buttons.back.slot",
                "buttons.previousPage.slot",
                "buttons.previous.slot",
                "buttons.nextPage.slot",
                "buttons.next.slot"
        )) {
            reserveSlot(reserved, size, intValue(section, path));
        }

        ConfigurationSection buttons = section.getConfigurationSection("buttons");
        if (buttons == null) {
            return;
        }
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection button = buttons.getConfigurationSection(key);
            if (button != null) {
                reserveSlot(reserved, size, intValue(button, "slot"));
            }
        }
    }

    private void reserveSlot(Set<Integer> reserved, int size, Integer slot) {
        if (slot != null && slot >= 0 && slot < size) {
            reserved.add(slot);
        }
    }

    public enum ConversionMode {
        DRY_RUN,
        APPLY
    }

    public enum SourcePlugin {
        SHOP_GUI_PLUS("ShopGUIPlus", List.of("shopguiplus", "shopgui+")),
        ECONOMY_SHOP_GUI("EconomyShopGUI", List.of("economyshopgui", "economyshop"));

        private final String displayName;
        private final List<String> normalizedAliases;

        SourcePlugin(String displayName, List<String> aliases) {
            this.displayName = displayName;
            this.normalizedAliases = aliases.stream().map(SourcePlugin::normalizeInput).toList();
        }

        public String displayName() {
            return displayName;
        }

        public static Optional<SourcePlugin> fromInput(String input) {
            String normalized = normalizeInput(input);
            for (SourcePlugin sourcePlugin : values()) {
                if (sourcePlugin.normalizedAliases.contains(normalized)) {
                    return Optional.of(sourcePlugin);
                }
            }
            return Optional.empty();
        }

        private static String normalizeInput(String input) {
            return input == null ? "" : input.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        }
    }

    public record ConversionResult(boolean success, boolean dryRun, int convertedCount, int convertibleCount,
                                   int skippedSections, int fallbackSections, String message, File reportFile) {
    }

    private record ConvertedSectionResult(int convertedSections, int convertibleSections, int skippedSections, int fallbackSections) {
    }

    private record ItemEntry(String id, ConfigurationSection section) {
    }

    private record EconomyItemEntry(String id, ConfigurationSection section, int page) {
    }

    private record SectionRoot(String rawId, ConfigurationSection section) {
    }

    private record ParsedSection(String id, String title, int size, Material icon, ItemStack iconItem, List<String> description, boolean enabled,
                                 int slot, List<ShopItem> items, int fallbackSlotsUsed) {
    }

    private record ParseResult(ParsedSection parsedSection, List<String> issues) {
    }

    private record MainMenuMeta(int slot, Material icon, ItemStack iconItem, String title, List<String> description, boolean enabled) {
        private MainMenuMeta {
            iconItem = iconItem == null ? null : iconItem.clone();
        }

        @Override
        public ItemStack iconItem() {
            return iconItem == null ? null : iconItem.clone();
        }
    }

    private record ParsedEnchant(String name, int level) {
    }

    private record SlotRef(int page, int slot) {
    }

    private enum PriceKind {
        BUY,
        SELL
    }
}
