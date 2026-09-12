package me.foesio.foShop.config;

import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.number.NumberFormatters;
import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FoConfig {

    private final FoShop plugin;

    private static final String DEFAULT_THEME = "#03fc88";
    private static final String DEFAULT_MUTED = "#a7b8b0";
    private static final String DEFAULT_WHITE = "#ffffff";
    private static final String DEFAULT_GOOD = "#3ecf8e";
    private static final String DEFAULT_BAD = "#ff5d73";
    private static final String DEFAULT_WORTH_LORE_FORMAT = "{white}Worth: {theme}${worth}";
    private String prefix;
    private String theme;
    private String muted;
    private String white;
    private String good;
    private String bad;

    private boolean fileLoggingEnabled;
    private boolean nativeDialogsEnabled;
    private boolean nativeDialogsWarnOnFallback;
    private boolean globalSellPricesEnabled;
    private boolean worthLoreEnabled;
    private String worthLoreFormat;

    private String mainTitle;
    private int mainRows;

    private String sellTitle;
    private int sellRows;
    private int sellReceiptType;
    private boolean sellTitlesEnabled;
    private String sellTitleMessage;
    private String sellSubtitleMessage;
    private boolean sellActionBarEnabled;
    private String sellActionBarMessage;
    private boolean sellTransactionLogEnabled;
    private String sellTransactionLogDateFormat;
    private boolean sellRoundedPricing;
    private boolean sellRemoveTrailingZeros;
    private boolean sellAbbreviateNumbers;
    private Set<String> sellBlockedGamemodes = Set.of();


    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");
    private final Map<String, YamlConfiguration> guiFiles = new HashMap<>();

    public FoConfig(FoShop plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ensureEditableDefaults();
        plugin.reloadConfig();
        ensureNativeDialogDefaults();
        loadGuiFiles();
        FileConfiguration config = plugin.getConfig();

        this.prefix = messageToken("prefix", null, DEFAULT_THEME + "FoShop &8» " + DEFAULT_MUTED);
        this.theme = messageToken("theme", config.getString("colors.theme", DEFAULT_THEME), DEFAULT_THEME);
        this.muted = messageToken("muted", config.getString("colors.muted", DEFAULT_MUTED), DEFAULT_MUTED);
        this.white = messageToken("white", config.getString("colors.white", DEFAULT_WHITE), DEFAULT_WHITE);
        this.good = messageToken("good", config.getString("colors.good", DEFAULT_GOOD), DEFAULT_GOOD);
        this.bad = messageToken("bad", config.getString("colors.bad", DEFAULT_BAD), DEFAULT_BAD);

        this.fileLoggingEnabled = config.getBoolean("file-logging", false);
        this.nativeDialogsEnabled = config.getBoolean("native-dialogs.enabled", true);
        this.nativeDialogsWarnOnFallback = config.getBoolean("native-dialogs.warn-on-fallback", true);
        this.globalSellPricesEnabled = config.getBoolean("global-sell-prices.enabled", false);
        this.worthLoreEnabled = globalSellPricesEnabled && config.getBoolean("global-sell-prices.worth-lore.enabled", false);
        String legacyWorthLoreFormat = config.getString("global-sell-prices.worth-lore.format", DEFAULT_WORTH_LORE_FORMAT);
        this.worthLoreFormat = rawMessage("worth-lore", legacyWorthLoreFormat);
        if (worthLoreFormat == null || worthLoreFormat.isBlank()) {
            worthLoreFormat = DEFAULT_WORTH_LORE_FORMAT;
        }

        this.mainTitle = config.contains("gui.main.title") ? config.getString("gui.main.title", "&8Shop") : guiString("main", "title", "&8Shop");
        this.mainRows = Math.clamp(config.contains("gui.main.rows") ? config.getInt("gui.main.rows", 3) : guiInt("main", "rows", 3), 1, 6);

        this.sellTitle = config.contains("gui.sell.title") ? config.getString("gui.sell.title", "&8Sell Gui") : guiString("sell-gui", "title", "&8Sell Gui");
        this.sellRows = Math.clamp(config.contains("gui.sell.rows") ? config.getInt("gui.sell.rows", 6) : guiInt("sell-gui", "rows", 6), 3, 6);
        this.sellReceiptType = Math.clamp(configInt("sellgui.receipts.type", "gui.sell.receipts.type", 1), 0, 1);
        this.sellTitlesEnabled = configBool("sellgui.titles.enabled", "gui.sell.titles.enabled", true);
        this.sellTitleMessage = applyStaticTokens(configString("sellgui.titles.title", "gui.sell.titles.title", "{good}+{earning}"));
        this.sellSubtitleMessage = applyStaticTokens(configString("sellgui.titles.subtitle", "gui.sell.titles.subtitle", "{muted}You sold {theme}{amount}{muted} items."));
        this.sellActionBarEnabled = configBool("sellgui.action-bar.enabled", "gui.sell.action-bar.enabled", true);
        this.sellActionBarMessage = applyStaticTokens(configString("sellgui.action-bar.message", "gui.sell.action-bar.message", "{good}Sold {theme}{amount}{good} item(s) for {theme}{earning}{good}."));
        this.sellTransactionLogEnabled = configBool("sellgui.transaction-log.enabled", "gui.sell.transaction-log.enabled", true);
        this.sellTransactionLogDateFormat = configString("sellgui.transaction-log.date-format", "gui.sell.transaction-log.date-format", "yyyy/MM/dd HH:mm:ss");
        this.sellRoundedPricing = configBool("sellgui.price-format.rounded-pricing", "gui.sell.price-format.rounded-pricing", false);
        this.sellRemoveTrailingZeros = configBool("sellgui.price-format.remove-trailing-zeros", "gui.sell.price-format.remove-trailing-zeros", false);
        this.sellAbbreviateNumbers = configBool("sellgui.price-format.abbreviate-numbers", "gui.sell.price-format.abbreviate-numbers", true);
        this.sellBlockedGamemodes = normalizeGamemodes(configStringList("sellgui.blocked-gamemodes", "gui.sell.blocked-gamemodes"));

    }

    private void ensureEditableDefaults() {
        ensureResource("guis/main.yml");
        ensureResource("guis/shop-section.yml");
        ensureResource("guis/sell-gui.yml");
        ensureResource("guis/rotating-shop.yml");
        ensureResource("guis/buy-item.yml");
        ensureResource("guis/buy-more.yml");
        ensureConfigBlock();
    }

    private void ensureResource(String path) {
        ResourceFiles.saveDefault(plugin, path);
    }

    private String messageToken(String key, String fallback, String hardFallback) {
        String value = plugin.getMessages() == null ? null : plugin.getMessages().tokenValues().get(key);
        if (value == null) {
            value = fallback;
        }
        if (value == null) {
            value = hardFallback;
        }
        return value;
    }

    private void loadGuiFiles() {
        guiFiles.clear();
        for (String name : List.of("main", "shop-section", "sell-gui", "rotating-shop", "buy-item", "buy-more")) {
            File file = new File(plugin.getDataFolder(), "guis/" + name + ".yml");
            YamlConfiguration gui = YamlConfiguration.loadConfiguration(file);
            migrateDefaultGuiStyle(name, file, gui);
            guiFiles.put(name, gui);
        }
    }

    private boolean migrateDefaultGuiStyle(String name, File file, YamlConfiguration gui) {
        YamlConfiguration defaults = loadGuiDefaults(name);
        if (defaults == null) {
            return false;
        }

        boolean changed = false;
        if (name.equals("buy-item")) {
            changed |= replaceExactDefault(gui, defaults, "buttons.remove-64.name", "&#ff5d73-64");
            changed |= replaceExactDefault(gui, defaults, "buttons.remove-64.lore", List.of("&#ffffffRemove 64."));
            changed |= replaceExactDefault(gui, defaults, "buttons.remove-10.name", "&#ff5d73-10");
            changed |= replaceExactDefault(gui, defaults, "buttons.remove-10.lore", List.of("&#ffffffRemove 10."));
            changed |= replaceExactDefault(gui, defaults, "buttons.remove-1.name", "&#ff5d73-1");
            changed |= replaceExactDefault(gui, defaults, "buttons.remove-1.lore", List.of("&#ffffffRemove 1."));
            changed |= replaceExactDefault(gui, defaults, "buttons.add-1.name", "&#3ecf8e+1");
            changed |= replaceExactDefault(gui, defaults, "buttons.add-1.lore", List.of("&#ffffffAdd 1."));
            changed |= replaceExactDefault(gui, defaults, "buttons.add-10.name", "&#3ecf8e+10");
            changed |= replaceExactDefault(gui, defaults, "buttons.add-10.lore", List.of("&#ffffffAdd 10."));
            changed |= replaceExactDefault(gui, defaults, "buttons.add-64.name", "&#3ecf8e+64");
            changed |= replaceExactDefault(gui, defaults, "buttons.add-64.lore", List.of("&#ffffffAdd 64."));
            changed |= replaceExactDefault(gui, defaults, "buttons.bulk.name", "&#03fc88Buy More");
            changed |= replaceExactDefault(gui, defaults, "buttons.bulk.lore", List.of("&#ffffffChoose stack amount preset."));
            changed |= replaceExactDefault(gui, defaults, "buttons.confirm.name", "&#3ecf8eConfirm Buy");
            changed |= replaceExactDefault(gui, defaults, "buttons.confirm.lore", List.of(
                    "&#ffffffBuy: &#03fc88{amount}",
                    "&#ffffffPrice: &#03fc88{price}"
            ));
        } else if (name.equals("buy-more")) {
            changed |= replaceExactDefault(gui, defaults, "option.name", "&#03fc88{stacks} stack{plural}");
            changed |= replaceExactDefault(gui, defaults, "option.lore", List.of(
                    "&#ffffffAmount: &#03fc88{amount}",
                    "&#ffffffPrice: &#03fc88{price}",
                    "&#ffffffClick to buy."
            ));
        } else if (name.equals("sell-gui")) {
            changed |= replaceExactDefault(gui, defaults, "buttons.sell.name", "&#03fc88Sell Items");
            changed |= replaceExactDefault(gui, defaults, "buttons.sell.lore", List.of(
                    "&#ffffffPut items in the top rows.",
                    "&#ffffffClick to sell all sellable items.",
                    "&#a7b8b0Unsellable items are returned."
            ));
        }

        if (changed) {
            try {
                gui.save(file);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed migrating " + file.getName() + " GUI defaults: " + exception.getMessage());
            }
        }
        return changed;
    }

    private YamlConfiguration loadGuiDefaults(String name) {
        try (InputStream input = plugin.getResource("guis/" + name + ".yml")) {
            if (input == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed loading GUI defaults for " + name + ": " + exception.getMessage());
            return null;
        }
    }

    private boolean replaceExactDefault(YamlConfiguration current, YamlConfiguration defaults, String path, Object oldValue) {
        if (!current.isSet(path) || !Objects.equals(current.get(path), oldValue) || !defaults.isSet(path)) {
            return false;
        }
        current.set(path, defaults.get(path));
        return true;
    }

    private void ensureNativeDialogDefaults() {
        FileConfiguration config = plugin.getConfig();
        boolean save = !config.contains(NativeDialogConfigDefaults.ENABLED_PATH)
                || !config.contains(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH)
                || config.getComments("native-dialogs").isEmpty()
                || config.getComments(NativeDialogConfigDefaults.ENABLED_PATH).isEmpty()
                || config.getComments(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH).isEmpty();
        NativeDialogConfigDefaults.addDefaults(config);
        if (save) {
            plugin.saveConfig();
        }
    }

    private void ensureConfigBlock() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            StringBuilder block = new StringBuilder();
            boolean changed = false;
            if (!content.contains("rotating-shop:")) {
                block.append(System.lineSeparator())
                        .append("# Rotating shop behavior. Visible layout lives in guis/rotating-shop.yml.").append(System.lineSeparator())
                        .append("rotating-shop:").append(System.lineSeparator())
                        .append("  # Disabled by default. When enabled, /rotatingshop opens the boosted items GUI.").append(System.lineSeparator())
                        .append("  enabled: false").append(System.lineSeparator())
                        .append("  # How often the random boosted items reset. 86400 = 1 day.").append(System.lineSeparator())
                        .append("  reset-interval-seconds: 86400").append(System.lineSeparator())
                        .append("  # Missing sections default to true. Use the editor to toggle section participation.").append(System.lineSeparator())
                        .append("  sections: {}").append(System.lineSeparator())
                        .append("  # Missing items default to true. Use the editor to toggle item participation per section.").append(System.lineSeparator())
                        .append("  items: {}").append(System.lineSeparator());
            }
            if (!content.contains("sell-boosters:")) {
                block.append(System.lineSeparator())
                        .append("# Sell booster behavior. Active boosters are stored in sell-boosters.yml.").append(System.lineSeparator())
                        .append("sell-boosters:").append(System.lineSeparator())
                        .append("  # When disabled, active sell boosters stay saved but do not affect prices.").append(System.lineSeparator())
                        .append("  enabled: true").append(System.lineSeparator())
                        .append("  # true = multiply all applicable boosters. false = use the highest applicable booster.").append(System.lineSeparator())
                        .append("  stack-boosters: false").append(System.lineSeparator())
                        .append("  # true = multiply rotating shop boosts with sell boosters. false = use the highest multiplier.").append(System.lineSeparator())
                        .append("  stack-with-rotating-shop: false").append(System.lineSeparator())
                        .append("  bossbar:").append(System.lineSeparator())
                        .append("    # Shows a bossbar to players while a sell booster applies to them.").append(System.lineSeparator())
                        .append("    enabled: true").append(System.lineSeparator())
                        .append("    title: \"&#03fc88{multiplier}x &#a7b8b0SELL BOOSTER &8- &#ffffff{time}\"").append(System.lineSeparator())
                        .append("    color: GREEN").append(System.lineSeparator())
                        .append("    style: SEGMENTED_10").append(System.lineSeparator())
                        .append("    update-ticks: 20").append(System.lineSeparator())
                        .append("  team-boosters:").append(System.lineSeparator())
                        .append("    # Requires FoTeams. FoShop only softdepends on FoTeams and never requires FoDropBooster.").append(System.lineSeparator())
                        .append("    enabled: true").append(System.lineSeparator());
            } else {
                if (!yaml.contains("sell-boosters.stack-boosters")) {
                    content = insertIntoTopLevelBlock(content, "sell-boosters:",
                            "  # true = multiply all applicable boosters. false = use the highest applicable booster." + System.lineSeparator()
                                    + "  stack-boosters: false" + System.lineSeparator());
                    changed = true;
                }
                if (!yaml.contains("sell-boosters.stack-with-rotating-shop")) {
                    content = insertIntoTopLevelBlock(content, "sell-boosters:",
                            "  # true = multiply rotating shop boosts with sell boosters. false = use the highest multiplier." + System.lineSeparator()
                                    + "  stack-with-rotating-shop: false" + System.lineSeparator());
                    changed = true;
                }
                if (!yaml.contains("sell-boosters.bossbar.enabled")) {
                    content = insertIntoTopLevelBlock(content, "sell-boosters:",
                            "  bossbar:" + System.lineSeparator()
                                    + "    # Shows a bossbar to players while a sell booster applies to them." + System.lineSeparator()
                                    + "    enabled: true" + System.lineSeparator()
                                    + "    title: \"&#03fc88{multiplier}x &#a7b8b0SELL BOOSTER &8- &#ffffff{time}\"" + System.lineSeparator()
                                    + "    color: GREEN" + System.lineSeparator()
                                    + "    style: SEGMENTED_10" + System.lineSeparator()
                                    + "    update-ticks: 20" + System.lineSeparator());
                    changed = true;
                }
                if (!yaml.contains("sell-boosters.team-boosters.enabled")) {
                    content = insertIntoTopLevelBlock(content, "sell-boosters:",
                            "  team-boosters:" + System.lineSeparator()
                                    + "    # Requires FoTeams. FoShop only softdepends on FoTeams and never requires FoDropBooster." + System.lineSeparator()
                                    + "    enabled: true" + System.lineSeparator());
                    changed = true;
                }
            }
            if (!content.contains("global-sell-prices:")) {
                block.append(System.lineSeparator())
                        .append("# Global sell prices make every vanilla item sellable from global-sell-prices.yml.").append(System.lineSeparator())
                        .append("global-sell-prices:").append(System.lineSeparator())
                        .append("  # Disabled by default. Shop section items always override global material prices.").append(System.lineSeparator())
                        .append("  enabled: false").append(System.lineSeparator())
                        .append("  worth-lore:").append(System.lineSeparator())
                        .append("    # Shows packet-only worth lore in player inventories and physical containers. Requires PacketEvents.").append(System.lineSeparator())
                        .append("    enabled: false").append(System.lineSeparator());
            } else if (!yaml.isConfigurationSection("global-sell-prices.worth-lore")) {
                content = insertIntoTopLevelBlock(content, "global-sell-prices:",
                        "  worth-lore:" + System.lineSeparator()
                                + "    # Shows packet-only worth lore in player inventories and physical containers. Requires PacketEvents." + System.lineSeparator()
                                + "    enabled: false" + System.lineSeparator());
                changed = true;
            } else {
                if (!yaml.contains("global-sell-prices.worth-lore.enabled")) {
                    content = insertIntoIndentedBlock(content, "  worth-lore:",
                            "    # Shows packet-only worth lore in player inventories and physical containers. Requires PacketEvents." + System.lineSeparator()
                                    + "    enabled: false" + System.lineSeparator());
                    changed = true;
                }
            }
            if (!block.isEmpty()) {
                content += block;
                changed = true;
            }
            if (changed) {
                Files.writeString(file.toPath(), content, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed backfilling config defaults: " + exception.getMessage());
        }
    }

    private String insertIntoTopLevelBlock(String content, String topLevelKey, String insertedBlock) {
        int blockStart = content.indexOf(topLevelKey);
        if (blockStart < 0) {
            return content + System.lineSeparator() + insertedBlock;
        }

        int lineStart = content.indexOf('\n', blockStart);
        if (lineStart < 0) {
            return content + System.lineSeparator() + insertedBlock;
        }

        int insertAt = content.length();
        int cursor = lineStart + 1;
        while (cursor < content.length()) {
            int lineEnd = content.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(cursor, lineEnd);
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                insertAt = cursor;
                break;
            }
            cursor = lineEnd + 1;
        }

        return content.substring(0, insertAt) + insertedBlock + content.substring(insertAt);
    }

    private String insertIntoIndentedBlock(String content, String blockKey, String insertedBlock) {
        int blockStart = content.indexOf(blockKey);
        if (blockStart < 0) {
            return content + System.lineSeparator() + insertedBlock;
        }

        int baseIndent = blockKey.indexOf(blockKey.trim());
        int lineStart = content.indexOf('\n', blockStart);
        if (lineStart < 0) {
            return content + System.lineSeparator() + insertedBlock;
        }

        int insertAt = content.length();
        int cursor = lineStart + 1;
        while (cursor < content.length()) {
            int lineEnd = content.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(cursor, lineEnd);
            if (!line.isBlank()) {
                int indent = line.indexOf(line.trim());
                if (indent <= baseIndent) {
                    insertAt = cursor;
                    break;
                }
            }
            cursor = lineEnd + 1;
        }
        return content.substring(0, insertAt) + insertedBlock + content.substring(insertAt);
    }

    private String configString(String path, String legacyPath, String fallback) {
        FileConfiguration config = plugin.getConfig();
        if (config.contains(path)) {
            return config.getString(path, fallback);
        }
        return config.getString(legacyPath, fallback);
    }

    private boolean configBool(String path, String legacyPath, boolean fallback) {
        FileConfiguration config = plugin.getConfig();
        if (config.contains(path)) {
            return config.getBoolean(path, fallback);
        }
        return config.getBoolean(legacyPath, fallback);
    }

    private int configInt(String path, String legacyPath, int fallback) {
        FileConfiguration config = plugin.getConfig();
        if (config.contains(path)) {
            return config.getInt(path, fallback);
        }
        return config.getInt(legacyPath, fallback);
    }

    private double configDouble(String path, String legacyPath, double fallback) {
        FileConfiguration config = plugin.getConfig();
        if (config.contains(path)) {
            return config.getDouble(path, fallback);
        }
        return config.getDouble(legacyPath, fallback);
    }

    private List<String> configStringList(String path, String legacyPath) {
        FileConfiguration config = plugin.getConfig();
        List<String> values = config.getStringList(path);
        if (!values.isEmpty() || config.contains(path)) {
            return values;
        }
        return config.getStringList(legacyPath);
    }

    private Set<String> normalizeGamemodes(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                out.add(value.trim().toUpperCase());
            }
        }
        return Set.copyOf(out);
    }

    public Map<String, String> tokenMap() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("{theme}", theme);
        tokens.put("{muted}", muted);
        tokens.put("{white}", white);
        tokens.put("{good}", good);
        tokens.put("{bad}", bad);
        tokens.put("{prefix}", prefix);
        tokens.put("%theme%", theme);
        tokens.put("%muted%", muted);
        tokens.put("%white%", white);
        tokens.put("%good%", good);
        tokens.put("%bad%", bad);
        tokens.put("%prefix%", prefix);
        return tokens;
    }

    private String applyStaticTokens(String template) {
        return Text.format(template, tokenMap());
    }

    public String rawMessage(String path, String fallback) {
        if (plugin.getMessages() == null) {
            return Text.format(fallback, tokenMap());
        }
        return plugin.getMessages().render(path, fallback);
    }

    public String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    public boolean isFileLoggingEnabled() {
        return fileLoggingEnabled;
    }

    public boolean isNativeDialogsEnabled() {
        return nativeDialogsEnabled;
    }

    public boolean isNativeDialogsWarnOnFallback() {
        return nativeDialogsWarnOnFallback;
    }

    public boolean isGlobalSellPricesEnabled() {
        return globalSellPricesEnabled;
    }

    public boolean isWorthLoreEnabled() {
        return worthLoreEnabled;
    }

    public boolean isWorthLoreConfiguredEnabled() {
        return plugin.getConfig().getBoolean("global-sell-prices.worth-lore.enabled", false);
    }

    public String getWorthLoreFormat() {
        return worthLoreFormat;
    }

    public String mainTitleSmallCaps() {
        return smallCapsTitle(mainTitle);
    }

    public int getMainRows() {
        return mainRows;
    }

    public int getMainSize() {
        return mainRows * 9;
    }

    public String sellTitleSmallCaps() {
        return smallCapsTitle(sellTitle);
    }

    public int getSellSize() {
        return sellRows * 9;
    }

    public int getSellReceiptType() {
        return sellReceiptType;
    }

    public boolean isSellTitlesEnabled() {
        return sellTitlesEnabled;
    }

    public String getSellTitleMessage() {
        return sellTitleMessage;
    }

    public String getSellSubtitleMessage() {
        return sellSubtitleMessage;
    }

    public boolean isSellActionBarEnabled() {
        return sellActionBarEnabled;
    }

    public String getSellActionBarMessage() {
        return sellActionBarMessage;
    }

    public boolean isSellTransactionLogEnabled() {
        return sellTransactionLogEnabled;
    }

    public String getSellTransactionLogDateFormat() {
        return sellTransactionLogDateFormat;
    }

    public boolean isSellAbbreviateNumbers() {
        return sellAbbreviateNumbers;
    }

    public String getSellReceiptText() {
        return rawMessage("sellgui-receipt-text", plugin.getConfig().getString("gui.sell.receipts.text", "&#a7b8b0(RECEIPT)"));
    }

    public String getSellReceiptTitle() {
        return rawMessage("sellgui-receipt-title", plugin.getConfig().getString("gui.sell.receipts.title", "&#ffffffReceipt for sell\n\n"));
    }

    public String getSellReceiptItemLayout() {
        return rawMessage("sellgui-receipt-item-layout", plugin.getConfig().getString("gui.sell.receipts.item-layout", "&#3ecf8e{amount} x {item} &#fffffffor &#03fc88{price}"));
    }

    public boolean isSellGamemodeBlocked(String gamemode) {
        return gamemode != null && sellBlockedGamemodes.contains(gamemode.toUpperCase());
    }

    public String formatSellMoney(double amount, String fallback) {
        if (!sellRoundedPricing && !sellRemoveTrailingZeros && !sellAbbreviateNumbers) {
            return fallback;
        }

        String formatted;
        if (sellAbbreviateNumbers) {
            formatted = abbreviate(amount);
        } else if (sellRoundedPricing) {
            formatted = new DecimalFormat("#,##0.00").format(amount);
        } else {
            formatted = Double.toString(amount);
        }

        return sellRemoveTrailingZeros ? removeTrailingZeros(formatted) : formatted;
    }

    private String abbreviate(double amount) {
        return NumberFormatters.compact(amount);
    }

    private String removeTrailingZeros(String value) {
        if (value == null || !value.contains(".")) {
            return value;
        }
        String out = value;
        while (out.endsWith("0")) {
            out = out.substring(0, out.length() - 1);
        }
        if (out.endsWith(".")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    public String sectionTitleSmallCaps(String raw) {
        // Keep the legacy method name for source compatibility, but configured
        // inventory titles are presentation data and must not be rewritten.
        return GuiTitles.format(Text.colorize(DialogIcons.fallbackText(raw)));
    }

    public String getTheme() {
        return theme;
    }

    public String getMuted() {
        return muted;
    }

    public String getWhite() {
        return white;
    }

    public String getGood() {
        return good;
    }

    public String getBad() {
        return bad;
    }

    public ConfigurationSection guiSection(String file, String path) {
        YamlConfiguration yaml = guiFiles.get(file);
        return yaml == null ? null : yaml.getConfigurationSection(path);
    }

    public Object guiObject(String file, String path) {
        YamlConfiguration yaml = guiFiles.get(file);
        return yaml == null ? null : yaml.get(path);
    }

    public String guiString(String file, String path, String fallback) {
        YamlConfiguration yaml = guiFiles.get(file);
        return yaml == null ? fallback : yaml.getString(path, fallback);
    }

    public int guiInt(String file, String path, int fallback) {
        YamlConfiguration yaml = guiFiles.get(file);
        return yaml == null ? fallback : yaml.getInt(path, fallback);
    }

    public boolean guiBool(String file, String path, boolean fallback) {
        YamlConfiguration yaml = guiFiles.get(file);
        return yaml == null ? fallback : yaml.getBoolean(path, fallback);
    }

    public List<String> guiStringList(String file, String path, List<String> fallback) {
        YamlConfiguration yaml = guiFiles.get(file);
        if (yaml == null) {
            return fallback;
        }
        List<String> values = yaml.getStringList(path);
        return values.isEmpty() && !yaml.contains(path) ? fallback : values;
    }

    public List<Integer> guiIntegerList(String file, String path, List<Integer> fallback) {
        YamlConfiguration yaml = guiFiles.get(file);
        if (yaml == null) {
            return fallback;
        }
        List<Integer> values = yaml.getIntegerList(path);
        return values.isEmpty() && !yaml.contains(path) ? fallback : values;
    }

    private String smallCapsTitle(String raw) {
        return GuiTitles.format(Text.colorize(DialogIcons.fallbackText(raw)));
    }
}
