package me.foesio.foShop.config;

import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import me.foesio.core.sound.SoundTypes;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/** One-time conversion from the released FoShop GUI files to Core's generated schema. */
final class PublicGuiMigration {
    static final String VERSION_PATH = "gui-config-version";
    static final int CURRENT_VERSION = 2;
    private static final List<String> FILES = List.of(
            "main", "shop-section", "buy-item", "buy-more", "sell-gui", "rotating-shop", "worth");
    private static final Set<String> PREVIOUS_FILES = Set.of(
            "main", "shop-section", "buy-item", "buy-more", "sell-gui", "rotating-shop");
    private static final List<String> VISUAL_FIELDS = List.of(
            "material", "amount", "name", "lore", "custom-model-data");

    private PublicGuiMigration() {}

    static boolean migrate(JavaPlugin plugin, boolean freshInstall) {
        FileConfiguration config = plugin.getConfig();
        int sourceVersion;
        try {
            sourceVersion = sourceVersion(config);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "FoShop public GUI migration cannot identify the installed schema version; owner files were left unchanged.",
                    exception);
            return false;
        }
        if (sourceVersion == CURRENT_VERSION) return true;
        Path data = plugin.getDataFolder().toPath();
        Path guiDir = data.resolve("guis");
        Path configFile = data.resolve("config.yml");
        byte[] originalConfig = null;
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        Map<Path, byte[]> proposed = new LinkedHashMap<>();
        Map<Path, Path> stages = new LinkedHashMap<>();
        List<Path> committed = new ArrayList<>();
        boolean configSaveAttempted = false;
        Object priorVersion = config.get(VERSION_PATH);
        Map<String, Object> priorConfigValues = new HashMap<>();
        try {
            Files.createDirectories(guiDir);
            originalConfig = Files.isRegularFile(configFile) ? Files.readAllBytes(configFile) : null;
            if (!freshInstall && !config.isSet(VERSION_PATH) && isCurrentInstallation(guiDir)) {
                if (!hasCurrentPublicResources(data, config)) {
                    throw new IOException("Core GUI files exist without gui-config-version, but the public "
                            + "config/messages version is ambiguous; owner files were left unchanged");
                }
                Path worth = guiDir.resolve("worth.yml");
                if (!Files.isRegularFile(worth)) {
                    throw new IOException("Current public GUI files have no gui-config-version or worth.yml; "
                            + "whether Worth was deliberately removed is ambiguous, so owner files were left unchanged");
                }
                // The marker was removed from a demonstrably current installation. Do not
                // run old-schema mappings over current GUIs, messages, sounds, or config.
                config.set(VERSION_PATH, CURRENT_VERSION);
                config.setComments(VERSION_PATH, List.of("DO NOT CHANGE"));
                configSaveAttempted = true;
                config.save(configFile.toFile());
                return true;
            }
            for (String name : FILES) {
                Path target = guiDir.resolve(name + ".yml");
                byte[] current = Files.isRegularFile(target) ? Files.readAllBytes(target) : null;
                if (current == null && !freshInstall && PREVIOUS_FILES.contains(name)) {
                    throw new IOException(target + " is missing on an existing installation; it will not be recreated");
                }
                byte[] latest = resource(plugin, "guis/" + name + ".yml");
                if (name.equals("shop-section") && !freshInstall) {
                    latest = sizedSectionDefault(latest, largestSectionRows(data.resolve("shops")));
                }
                byte[] next;
                if (current == null) {
                    next = latest; // A genuinely new menu has no predecessor owner source.
                } else if (freshInstall) {
                    throw new IOException("Fresh installation unexpectedly contains " + target);
                } else if (name.equals("worth")) {
                    if (!isCurrentGuiShape(name, yaml(current))) {
                        throw new IOException(target + " predates FoShop's first bundled Worth GUI; "
                                + "its unknown owner format was left unchanged");
                    }
                    next = current;
                } else {
                    String source = "legacy-guis/v" + Math.min(sourceVersion, 1) + "/" + name + ".yml";
                    byte[] oldDefault = resource(plugin, source);
                    next = migrateExistingGui(name, current, oldDefault, latest, config, sourceVersion, plugin);
                }
                originals.put(target, current);
                proposed.put(target, next);
            }
            if (!freshInstall) {
                Path messages = data.resolve("messages.yml");
                if (Files.isRegularFile(messages)) {
                    byte[] current = Files.readAllBytes(messages);
                    byte[] previous = resource(plugin, "legacy-guis/v" + Math.min(sourceVersion, 1)
                            + "/messages.yml");
                    byte[] newest = resource(plugin, "messages.yml");
                    originals.put(messages, current);
                    proposed.put(messages, migrateMessages(current, previous, newest, plugin));
                }
                Path sounds = data.resolve("sounds.yml");
                byte[] currentSounds = Files.isRegularFile(sounds) ? Files.readAllBytes(sounds) : null;
                if (currentSounds != null || sourceVersion == 0) {
                    byte[] newest = resource(plugin, "sounds.yml");
                    byte[] previous = resource(plugin, "legacy-guis/v1/sounds.yml");
                    originals.put(sounds, currentSounds);
                    proposed.put(sounds, migrateSounds(currentSounds, previous, newest, config, plugin));
                }
            }
            // Validate the entire proposed set before changing the active files.
            for (Map.Entry<Path, byte[]> entry : proposed.entrySet()) {
                if (!entry.getKey().getParent().equals(guiDir)) continue;
                String name = entry.getKey().getFileName().toString().replaceFirst("\\.yml$", "");
                validate(name, yaml(entry.getValue()));
            }
            validateShopLayout(data.resolve("shops"), proposed, guiDir);
            Files.createDirectories(guiDir);
            for (Map.Entry<Path, byte[]> entry : proposed.entrySet()) {
                if (Arrays.equals(originals.get(entry.getKey()), entry.getValue())) continue;
                Path stage = Files.createTempFile(guiDir, ".gui-v2-", ".staged");
                Files.write(stage, entry.getValue());
                stages.put(entry.getKey(), stage);
            }
            // Preserve original bytes once for the whole upgrade; never replace an older backup.
            if (!freshInstall) {
                for (Map.Entry<Path, byte[]> entry : originals.entrySet()) {
                    if (entry.getValue() == null || !stages.containsKey(entry.getKey())) continue;
                    Path backup = entry.getKey().resolveSibling(entry.getKey().getFileName()
                            + ".gui-v" + sourceVersion + "-backup");
                    if (!Files.exists(backup)) Files.write(backup, entry.getValue());
                }
                if (originalConfig != null) {
                    Path backup = configFile.resolveSibling("config.yml.gui-v" + sourceVersion + "-backup");
                    if (!Files.exists(backup)) Files.write(backup, originalConfig);
                }
            }
            for (Map.Entry<Path, Path> entry : stages.entrySet()) {
                replace(entry.getValue(), entry.getKey());
                committed.add(entry.getKey());
            }
            if (!freshInstall) applyLegacyExtras(plugin, config, originals, sourceVersion, priorConfigValues);
            // Save this version only after the complete GUI set was installed.
            config.set(VERSION_PATH, CURRENT_VERSION);
            config.setComments(VERSION_PATH, List.of("DO NOT CHANGE"));
            configSaveAttempted = true;
            config.save(data.resolve("config.yml").toFile());
            return true;
        } catch (Exception failure) {
            config.set(VERSION_PATH, priorVersion);
            priorConfigValues.forEach(config::set);
            for (Path path : committed) {
                try {
                    byte[] original = originals.get(path);
                    if (original == null) Files.deleteIfExists(path);
                    else {
                        Path restore = Files.createTempFile(guiDir, ".gui-restore-", ".staged");
                        Files.write(restore, original);
                        replace(restore, path);
                    }
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            try {
                if (configSaveAttempted && originalConfig != null) Files.write(configFile, originalConfig);
            } catch (IOException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            plugin.getLogger().log(Level.SEVERE,
                    "FoShop public GUI migration failed; active files restored and gui-config-version remains pending.", failure);
            return false;
        } finally {
            for (Path stage : stages.values()) {
                try { Files.deleteIfExists(stage); } catch (IOException ignored) { }
            }
        }
    }

    static int sourceVersion(FileConfiguration config) {
        if (!config.contains(VERSION_PATH, true)) return 0;
        Object raw = config.get(VERSION_PATH);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > CURRENT_VERSION) {
            throw new IllegalArgumentException(VERSION_PATH + " must be a whole number from 0 to "
                    + CURRENT_VERSION + "; found " + raw);
        }
        return number.intValue();
    }

    static boolean isCurrentInstallation(Path guiDir) throws IOException, InvalidConfigurationException {
        for (String name : PREVIOUS_FILES) {
            Path file = guiDir.resolve(name + ".yml");
            if (!Files.isRegularFile(file) || !hasCurrentGuiAnchor(name, yaml(Files.readAllBytes(file)))) {
                return false;
            }
        }
        Path worth = guiDir.resolve("worth.yml");
        return !Files.isRegularFile(worth) || hasCurrentGuiAnchor("worth", yaml(Files.readAllBytes(worth)));
    }

    private static boolean hasCurrentGuiAnchor(String name, YamlConfiguration gui) {
        return switch (name) {
            case "main" -> gui.isConfigurationSection("entries.sections");
            case "shop-section" -> gui.isConfigurationSection("entries.products");
            case "buy-item" -> gui.isConfigurationSection("entries.preview");
            case "buy-more" -> gui.isConfigurationSection("entries.options");
            case "rotating-shop" -> gui.isConfigurationSection("entries.center");
            case "sell-gui" -> gui.isSet("sell-slots");
            case "worth" -> gui.isConfigurationSection("entries.center")
                    && gui.isConfigurationSection("entries.prices");
            default -> throw new IllegalArgumentException("Unknown menu: " + name);
        };
    }

    static boolean hasCurrentPublicResources(Path data, FileConfiguration config)
            throws IOException, InvalidConfigurationException {
        if (!config.contains("public-values", true) || !config.isConfigurationSection("public-values")) {
            return false;
        }
        Path messages = data.resolve("messages.yml");
        if (!Files.isRegularFile(messages)) return false;
        YamlConfiguration installed = yaml(Files.readAllBytes(messages));
        return installed.isSet("gui-unavailable") || installed.isSet("sellgui-receipt-other-items")
                || installed.isConfigurationSection("duration");
    }

    static byte[] migrateExistingGui(String name, byte[] original, byte[] oldDefaultBytes, byte[] latestBytes,
                                     FileConfiguration pluginConfig, int sourceVersion, JavaPlugin plugin)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration installed = yaml(original);
        if (isCurrentGuiShape(name, installed)) {
            // A missing version marker is not permission to rewrite current-schema owner files.
            // Invalid current fields are handled in memory by Core on cold startup.
            return original;
        }
        return migrateGui(name, original, oldDefaultBytes, latestBytes, pluginConfig, sourceVersion, plugin);
    }

    private static boolean isCurrentGuiShape(String name, YamlConfiguration gui) {
        if (hasCurrentGuiAnchor(name, gui)) return true;
        if (name.equals("sell-gui")) {
            // A current owner can remove a required group. Leave that invalid
            // current file untouched for Core's startup/reload validation.
            return gui.isSet("filler.enabled");
        }
        String entry = switch (name) {
            case "main" -> "sections";
            case "shop-section" -> "products";
            case "buy-item" -> "preview";
            case "buy-more" -> "options";
            case "rotating-shop", "worth" -> "center";
            default -> throw new IllegalArgumentException("Unknown menu: " + name);
        };
        if (gui.isConfigurationSection("entries." + entry)) return true;
        // Owners could add filler.enabled to a released GUI file. Its old
        // presentation sections still identify the file as needing conversion.
        String legacySection = switch (name) {
            case "main" -> "section";
            case "shop-section" -> "item-lore";
            case "buy-item" -> "selected-item";
            case "buy-more" -> "option";
            case "rotating-shop" -> "boost-lore";
            default -> null;
        };
        if (legacySection != null && gui.isSet(legacySection)) return false;
        // Preserve a current file whose required entry was deleted or broken;
        // migration must not rewrite a current-schema owner edit.
        return gui.isSet("filler.enabled");
    }

    static byte[] migrateGui(String name, byte[] original, byte[] oldDefaultBytes, byte[] latestBytes,
                             FileConfiguration pluginConfig, int sourceVersion, JavaPlugin plugin)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration old = yaml(original);
        YamlConfiguration previous = yaml(oldDefaultBytes);
        YamlConfiguration newest = yaml(latestBytes);
        YamlConfiguration result = yaml(latestBytes);
        overlay(old, previous, result, newest, "title", "title", false);
        overlay(old, previous, result, newest, "rows", "rows", false);
        overlay(old, previous, result, newest, "filler.enabled", "filler.enabled", false);
        for (String field : VISUAL_FIELDS) {
            overlay(old, previous, result, newest, "filler." + field, "filler." + field, true);
        }
        if (previous.isSet("filler.material") && !old.isSet("filler.material")) {
            result.set("filler.enabled", false);
        }
        switch (name) {
            case "main" -> {
                overlay(old, previous, result, newest, "section.fallback-name",
                        "entries.sections.source-item.default-name", true);
                if (sourceVersion == 0) {
                    legacyConfig(pluginConfig, old, previous, result, newest,
                            "gui.main.title", "title", "&8Shop");
                    legacyConfig(pluginConfig, old, previous, result, newest,
                            "gui.main.rows", "rows", 3);
                }
                int rows = result.getInt("rows", 3);
                if (rows >= 1 && rows <= 6) {
                    List<Integer> sectionSlots = new ArrayList<>();
                    for (int slot = 0; slot < rows * 9; slot++) sectionSlots.add(slot);
                    result.set("entries.sections.slots", sectionSlots);
                }
            }
            case "shop-section" -> {
                Set<String> invalidLore = new HashSet<>();
                for (String state : result.getConfigurationSection(
                        "entries.products.source-item.lore-states").getKeys(false)) {
                    if (state.equals("none")) continue;
                    List<String> lines = new ArrayList<>();
                    String targetPath = "entries.products.source-item.lore-states." + state + ".lines";
                    List<String> defaults = newest.getStringList(targetPath);
                    for (String part : state.split("-")) {
                        String legacy = part.equals("limit") ? "buy-limit" : part;
                        String sourcePath = "item-lore." + legacy;
                        int position = List.of(state.split("-")).indexOf(part);
                        if (old.isSet(sourcePath)) {
                            Object value = old.get(sourcePath);
                            if (!(value instanceof List<?> list) || list.stream().anyMatch(line -> !(line instanceof String))) {
                                if (invalidLore.add(sourcePath) && plugin != null) {
                                    plugin.getLogger().warning("GUI migration " + sourcePath + "=" + value
                                            + " (invalid lore list) -> newest bundled default");
                                }
                                if (position < defaults.size()) lines.add(defaults.get(position));
                            } else if (Objects.equals(value, previous.get(sourcePath))) {
                                if (position < defaults.size()) lines.add(defaults.get(position));
                            } else {
                                lines.addAll(old.getStringList(sourcePath));
                            }
                        } else if (previous.isSet(sourcePath)) {
                            // An owner-removed optional legacy list remains absent.
                        } else {
                            if (position < defaults.size()) lines.add(defaults.get(position));
                        }
                    }
                    if (!lines.isEmpty()) {
                        result.set(targetPath, lines);
                    } else {
                        result.set("entries.products.source-item.lore-states." + state + ".mode", "preserve");
                        result.set(targetPath, null);
                    }
                }
            }
            case "buy-item" -> {
                if (old.isSet("selected-item.slot") && !Objects.equals(old.get("selected-item.slot"),
                        previous.get("selected-item.slot"))) {
                    result.set("entries.preview.slots", List.of(old.get("selected-item.slot")));
                }
                overlay(old, previous, result, newest, "selected-item.lore",
                        "entries.preview.source-item.lore.lines", true);
                if (!old.isSet("selected-item.lore") && previous.isSet("selected-item.lore")) {
                    result.set("entries.preview.source-item.lore.mode", "preserve");
                }
            }
            case "buy-more" -> {
                for (String field : VISUAL_FIELDS) {
                    overlay(old, previous, result, newest, "option." + field,
                            "entries.options.item." + field, true);
                }
            }
            case "sell-gui" -> {
                if (sourceVersion == 0) {
                    legacyConfig(pluginConfig, old, previous, result, newest,
                            "gui.sell.title", "title", "&8Sell Gui");
                    legacyConfig(pluginConfig, old, previous, result, newest,
                            "gui.sell.rows", "rows", 6);
                }
                int rows = result.getInt("rows", 6);
                if (rows >= 3 && rows <= 6 && rows != 6) {
                    List<Integer> input = new ArrayList<>();
                    for (int slot = 0; slot < (rows - 1) * 9; slot++) input.add(slot);
                    result.set("sell-slots", input);
                    result.set("buttons.sell.slot", rows * 9 - 5);
                }
                ConfigurationSection oldDecorations = legacyDecorations(old.get("decorations"));
                if ((oldDecorations == null || oldDecorations.getKeys(false).isEmpty()) && sourceVersion == 0) {
                    oldDecorations = legacyDecorations(pluginConfig.get("gui.sell.decorations"));
                }
                if (oldDecorations != null) {
                    List<Integer> input = result.getIntegerList("sell-slots");
                    for (String id : oldDecorations.getKeys(false)) {
                        ConfigurationSection source = oldDecorations.getConfigurationSection(id);
                        if (source == null) continue;
                        String target = "decorations." + id;
                        int slot = source.getInt("slot", -1);
                        result.set(target + ".slots", List.of(slot));
                        result.set(target + ".material", source.getString("material",
                                source.getString("item.material", "GRAY_STAINED_GLASS_PANE")));
                        result.set(target + ".amount", source.getInt("amount", source.getInt("item.quantity",
                                source.getInt("item.amount", 1))));
                        result.set(target + ".name", source.getString("name", source.getString("item.name", " ")));
                        result.set(target + ".lore", source.contains("lore") ? source.get("lore")
                                : source.contains("item.lore") ? source.get("item.lore") : List.of());
                        int model = source.getInt("custom-model-data", source.getInt("customModelData", -1));
                        if (model >= 0) result.set(target + ".custom-model-data", model);
                        input.remove(Integer.valueOf(slot));
                    }
                    result.set("sell-slots", input);
                }
            }
            case "rotating-shop" -> {
                if (old.isSet("slots") && !Objects.equals(old.get("slots"), previous.get("slots"))) {
                    List<?> slots = old.getList("slots");
                    if (slots == null || slots.isEmpty()) {
                        result.set("entries.center.slots", old.get("slots"));
                    } else {
                        result.set("entries.center.slots", List.of(slots.getFirst()));
                        result.set("entries.boosts.slots", slots.subList(1, slots.size()));
                    }
                }
                for (String field : VISUAL_FIELDS) {
                    overlay(old, previous, result, newest, "empty." + field,
                            "entries.center.empty-item." + field, true);
                }
                if (old.isSet("boost-lore") && !Objects.equals(old.get("boost-lore"), previous.get("boost-lore"))) {
                    Object boost = old.get("boost-lore");
                    for (String region : List.of("center", "boosts")) {
                        for (String state : List.of("shop-buy", "shop-sell-only")) {
                            String path = "entries." + region + ".source-item.lore-states." + state + ".lines";
                            if (boost instanceof List<?> lines) {
                                List<Object> combined = new ArrayList<>(newest.getList(path));
                                combined.subList(state.equals("shop-buy") ? 3 : 2, combined.size()).clear();
                                combined.addAll(lines);
                                result.set(path, combined);
                            } else {
                                result.set(path, boost);
                            }
                        }
                    }
                } else if (!old.isSet("boost-lore") && previous.isSet("boost-lore")) {
                    for (String region : List.of("center", "boosts")) {
                        for (String state : List.of("shop-buy", "shop-sell-only")) {
                            String path = "entries." + region + ".source-item.lore-states." + state + ".lines";
                            List<String> lines = newest.getStringList(path);
                            result.set(path, lines.subList(0, state.equals("shop-buy") ? 3 : 2));
                        }
                    }
                }
                for (String type : List.of("material", "enchantment", "potion")) {
                    for (String region : List.of("center", "boosts")) {
                        String target = "entries." + region + ".source-item.lore-states.global-" + type;
                        overlay(old, previous, result, newest, "global-" + type + ".name",
                                target + ".default-name", true);
                        overlay(old, previous, result, newest, "global-" + type + ".lore",
                                target + ".lines", true);
                        if (!old.isSet("global-" + type + ".lore")
                                && previous.isSet("global-" + type + ".lore")) {
                            result.set(target + ".mode", "preserve");
                        }
                    }
                }
            }
            default -> throw new IOException("Unknown menu: " + name);
        }
        var buttons = newest.getConfigurationSection("buttons");
        if (buttons != null) {
            for (String button : buttons.getKeys(false)) {
                for (String field : VISUAL_FIELDS) {
                    overlay(old, previous, result, newest, "buttons." + button + "." + field,
                            "buttons." + button + "." + field, true);
                }
                String sourcePath = "buttons." + button + ".slot";
                if (old.isSet(sourcePath) && !Objects.equals(old.get(sourcePath), previous.get(sourcePath))) {
                    Object slot = old.get(sourcePath);
                    if (!(slot instanceof Number number && number.intValue() == -1)) result.set(sourcePath, slot);
                }
            }
        }
        if (name.equals("sell-gui")) {
            int rows = result.getInt("rows", 6);
            int sellSlot = result.getInt("buttons.sell.slot", -1);
            if (rows >= 3 && rows <= 6 && (sellSlot < 0 || sellSlot >= rows * 9)) {
                result.set("buttons.sell.slot", rows * 9 - 5);
            }
        }
        if (name.equals("shop-section")) {
            int size = result.getInt("rows", 3) * 9;
            Set<Integer> controls = new HashSet<>(List.of(result.getInt("buttons.previous.slot"),
                    result.getInt("buttons.back.slot"), result.getInt("buttons.next.slot")));
            List<Integer> productSlots = new ArrayList<>();
            for (int slot = 0; slot < size; slot++) {
                if (!controls.contains(slot)) productSlots.add(slot);
            }
            result.set("entries.products.slots", productSlots);
        }
        return repairInvalid(name, result, newest, plugin).getBytes(StandardCharsets.UTF_8);
    }

    static int largestSectionRows(Path shopDir) throws IOException {
        int largest = 0;
        if (!Files.isDirectory(shopDir)) return 3;
        try (var files = Files.list(shopDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).toList()) {
                YamlConfiguration section = new YamlConfiguration();
                try { section.load(file.toFile()); }
                catch (InvalidConfigurationException exception) {
                    throw new IOException("Invalid shop section " + file.getFileName(), exception);
                }
                int size = section.getInt("size", 27);
                if (size >= 9 && size <= 54 && size % 9 == 0) largest = Math.max(largest, size / 9);
            }
        }
        return largest == 0 ? 3 : largest;
    }

    static byte[] sizedSectionDefault(byte[] bundled, int rows) throws InvalidConfigurationException {
        YamlConfiguration section = yaml(bundled);
        int size = rows * 9;
        section.set("rows", rows);
        section.set("buttons.previous.slot", size - 9);
        section.set("buttons.back.slot", size - 5);
        section.set("buttons.next.slot", size - 1);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (slot != size - 9 && slot != size - 5 && slot != size - 1) slots.add(slot);
        }
        section.set("entries.products.slots", slots);
        return section.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] migrateMessages(byte[] original, byte[] oldDefaultBytes, byte[] latestBytes,
                                  JavaPlugin plugin) throws InvalidConfigurationException {
        YamlConfiguration old = yaml(original);
        YamlConfiguration previous = yaml(oldDefaultBytes);
        YamlConfiguration newest = yaml(latestBytes);
        YamlConfiguration result = yaml(latestBytes);
        for (String path : newest.getKeys(true)) {
            if (newest.isConfigurationSection(path)) continue;
            if (old.isSet(path)) {
                Object current = old.get(path);
                if (!previous.isSet(path) || !Objects.equals(current, previous.get(path))) {
                    boolean token = path.startsWith("tokens.");
                    boolean valid = current instanceof String || (!token && current instanceof List<?> list
                            && list.stream().allMatch(String.class::isInstance));
                    if (valid) result.set(path, current);
                    else if (plugin != null) plugin.getLogger().warning("Message migration " + path
                            + "=" + current + " (wrong type) -> newest bundled default " + newest.get(path));
                }
            } else if (previous.isSet(path)) {
                result.set(path, null);
            }
        }
        return result.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] migrateSounds(byte[] original, byte[] oldDefaultBytes, byte[] latestBytes,
                                FileConfiguration pluginConfig, JavaPlugin plugin)
            throws InvalidConfigurationException {
        YamlConfiguration previous = yaml(oldDefaultBytes);
        YamlConfiguration newest = yaml(latestBytes);
        YamlConfiguration result = yaml(latestBytes);
        if (original != null) {
            YamlConfiguration old = yaml(original);
            for (String path : newest.getKeys(true)) {
                if (newest.isConfigurationSection(path)) continue;
                if (old.isSet(path)) {
                    Object current = old.get(path);
                    if ((!previous.isSet(path) || !Objects.equals(current, previous.get(path)))
                            && validSoundValue(path, current)) result.set(path, current);
                    else if ((!previous.isSet(path) || !Objects.equals(current, previous.get(path)))
                            && plugin != null) plugin.getLogger().warning("Sound migration " + path + "="
                            + current + " (invalid) -> newest bundled default " + newest.get(path));
                } else if (previous.isSet(path)) {
                    result.set(path, null);
                }
            }
        } else {
            Map<String, String> events = Map.of("open", "sell.open", "success", "sell.success",
                    "failed", "sell.failure");
            Map<String, String> oldDefaults = Map.of("open", "BLOCK_CHEST_OPEN",
                    "success", "ENTITY_VILLAGER_CELEBRATE", "failed", "ENTITY_VILLAGER_HURT");
            String root = pluginConfig.isSet("sellgui.sounds") ? "sellgui.sounds" : "gui.sell.sounds";
            for (Map.Entry<String, String> event : events.entrySet()) {
                String source = root + ".events." + event.getKey();
                String target = event.getValue();
                if (!pluginConfig.isSet(source)) continue;
                String sound = pluginConfig.getString(source);
                if (sound != null && !sound.equals(oldDefaults.get(event.getKey()))
                        && validSoundValue(target + ".sound", sound)) result.set(target + ".sound", sound);
                for (String field : List.of("enabled", "volume", "pitch")) {
                    String sourceField = root + "." + field;
                    Object value = pluginConfig.get(sourceField);
                    Object oldDefault = field.equals("enabled") ? true : 1;
                    boolean unchanged = value instanceof Number number && oldDefault instanceof Number expected
                            ? number.doubleValue() == expected.doubleValue() : Objects.equals(value, oldDefault);
                    if (pluginConfig.isSet(sourceField) && !unchanged
                            && validSoundValue(target + "." + field, value)) {
                        result.set(target + "." + field, value);
                    }
                }
            }
        }
        return result.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean validSoundValue(String path, Object value) {
        String field = path.substring(path.lastIndexOf('.') + 1);
        return switch (field) {
            case "enabled" -> value instanceof Boolean;
            case "sound", "key" -> value instanceof String text && !text.isBlank()
                    && (Bukkit.getServer() == null || SoundTypes.isValid(text));
            case "category" -> {
                if (!(value instanceof String text)) yield false;
                try { SoundCategory.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT)); yield true; }
                catch (IllegalArgumentException exception) { yield false; }
            }
            case "volume" -> value instanceof Number number && Double.isFinite(number.doubleValue())
                    && number.doubleValue() >= 0;
            case "pitch" -> value instanceof Number number && Double.isFinite(number.doubleValue())
                    && number.doubleValue() >= 0.5 && number.doubleValue() <= 2;
            default -> false;
        };
    }

    private static void applyLegacyExtras(JavaPlugin plugin, FileConfiguration config, Map<Path, byte[]> originals,
                                          int sourceVersion, Map<String, Object> prior) throws IOException {
        if (sourceVersion == 0) migrateLegacySellFeedback(plugin, config, prior);
        byte[] oldBuy = originalNamed(originals, "buy-item.yml");
        if (oldBuy != null) {
            YamlConfiguration buy;
            try { buy = yaml(oldBuy); }
            catch (InvalidConfigurationException exception) { throw new IOException("Invalid old buy-item.yml", exception); }
            Map<String, Integer> defaults = Map.of("remove-64", -64, "remove-10", -10,
                    "remove-1", -1, "add-1", 1, "add-10", 10, "add-64", 64);
            defaults.forEach((id, defaultValue) -> {
                String source = "buttons." + id + ".change";
                String target = "public-gui.amount-changes." + id;
                if (buy.isInt(source) && buy.getInt(source) != defaultValue && !config.isSet(target)) {
                    prior.put(target, null);
                    config.set(target, buy.getInt(source));
                }
            });
        }
        byte[] oldSell = originalNamed(originals, "sell-gui.yml");
        if (oldSell != null) {
            YamlConfiguration sell;
            try { sell = yaml(oldSell); }
            catch (InvalidConfigurationException exception) { throw new IOException("Invalid old sell-gui.yml", exception); }
            ConfigurationSection decorations = legacyDecorations(sell.get("decorations"));
            if ((decorations == null || decorations.getKeys(false).isEmpty()) && sourceVersion == 0) {
                decorations = legacyDecorations(config.get("gui.sell.decorations"));
            }
            if (decorations != null) {
                for (String id : decorations.getKeys(false)) {
                    ConfigurationSection entry = decorations.getConfigurationSection(id);
                    if (entry == null) continue;
                    for (String kind : List.of("player", "console")) {
                        List<String> candidates = kind.equals("player")
                                ? List.of("player-commands", "commands-on-click", "commandsOnClick")
                                : List.of("console-commands", "commands-on-click-console", "commandsOnClickConsole");
                        for (String key : candidates) {
                            if (!entry.isSet(key)) continue;
                            if (!entry.isList(key)) throw new IOException("sell decoration " + id + "." + key
                                    + " must be a list of commands");
                            String target = "public-gui.sell-decoration-commands." + id + "." + kind;
                            if (!config.isSet(target)) {
                                prior.put(target, null);
                                config.set(target, entry.getStringList(key));
                            }
                            break;
                        }
                    }
                }
            }
        }
        try {
            YamlConfiguration bundledConfig = yaml(resource(plugin, "config.yml"));
            String bulkOptions = "public-gui.bulk-stack-options";
            if (!config.isSet(bulkOptions)) {
                prior.put(bulkOptions, null);
                config.set(bulkOptions, bundledConfig.getIntegerList(bulkOptions));
            }
            for (String key : List.of("stack-singular", "stack-plural", "stack-plural-suffix", "worth-sort-name",
                    "worth-sort-price", "worth-search-empty", "worth-search-prompt",
                    "worth-search-title", "worth-search-label", "worth-search-placeholder",
                    "worth-search-submit-label", "worth-search-submit-tooltip", "worth-search-submit-icon",
                    "worth-search-submit-width", "worth-search-cancel-label", "worth-search-cancel-tooltip",
                    "worth-search-cancel-icon", "worth-search-cancel-width", "worth-search-body-width",
                    "worth-search-input-width", "worth-search-max-length", "potion-suffix",
                    "gamemode-names.SURVIVAL", "gamemode-names.CREATIVE",
                    "gamemode-names.ADVENTURE", "gamemode-names.SPECTATOR")) {
                String path = "public-values." + key;
                if (!config.isSet(path)) {
                    prior.put(path, null);
                    config.set(path, bundledConfig.get(path));
                }
            }
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Invalid bundled config.yml public values", exception);
        }
    }

    private static void migrateLegacySellFeedback(JavaPlugin plugin, FileConfiguration config,
                                                   Map<String, Object> prior) throws IOException {
        YamlConfiguration latest;
        try { latest = yaml(resource(plugin, "config.yml")); }
        catch (InvalidConfigurationException exception) { throw new IOException("Invalid bundled config.yml", exception); }
        Map<String, Object> oldDefaults = Map.of(
                "titles.enabled", true,
                "titles.title", "{good}+{earning}",
                "titles.subtitle", "{muted}You sold {theme}{amount}{muted} items.",
                "action-bar.enabled", true,
                "action-bar.message", "{good}Sold {theme}{amount}{good} item(s) for {theme}{earning}{good}.");
        for (Map.Entry<String, Object> entry : oldDefaults.entrySet()) {
            String source = "gui.sell." + entry.getKey();
            String target = "sellgui." + entry.getKey();
            if (config.isSet(target) || !config.isSet(source)) continue;
            Object owner = config.get(source);
            Object value = owner;
            boolean valid = entry.getValue() instanceof Boolean ? owner instanceof Boolean : owner instanceof String;
            if (!valid || Objects.equals(owner, entry.getValue())) {
                value = latest.get(target);
                if (!valid) plugin.getLogger().warning("Config migration " + source + "=" + owner
                        + " (wrong type) -> newest bundled default " + value);
            }
            prior.put(target, null);
            config.set(target, value);
        }
    }

    private static byte[] originalNamed(Map<Path, byte[]> originals, String name) {
        return originals.entrySet().stream().filter(entry -> entry.getKey().getFileName().toString().equals(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static ConfigurationSection legacyDecorations(Object raw) throws IOException {
        if (raw instanceof ConfigurationSection section) return section;
        if (!(raw instanceof List<?> list)) return null;
        YamlConfiguration container = new YamlConfiguration();
        ConfigurationSection normalized = container.createSection("decorations");
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> map)) {
                throw new IOException("Legacy sell decoration " + index + " must be an object");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : map.entrySet()) {
                if (!(field.getKey() instanceof String key)) {
                    throw new IOException("Legacy sell decoration " + index + " has a non-text field");
                }
                values.put(key, field.getValue());
            }
            normalized.createSection("legacy-" + index, values);
        }
        return normalized;
    }

    private static void legacyConfig(FileConfiguration config, YamlConfiguration old,
                                     YamlConfiguration previous, YamlConfiguration result,
                                     YamlConfiguration newest, String sourcePath, String targetPath,
                                     Object oldBundledDefault) {
        if (!config.isSet(sourcePath) || Objects.equals(config.get(sourcePath), oldBundledDefault)) return;
        if (old.isSet(targetPath) && previous.isSet(targetPath)
                && !Objects.equals(old.get(targetPath), previous.get(targetPath))) return;
        result.set(targetPath, config.get(sourcePath));
    }

    private static void overlay(YamlConfiguration old, YamlConfiguration previous,
                                YamlConfiguration result, YamlConfiguration newest,
                                String sourcePath, String targetPath, boolean optional) {
        if (old.isSet(sourcePath)) {
            Object value = old.get(sourcePath);
            if (!previous.isSet(sourcePath) || !Objects.equals(value, previous.get(sourcePath))) {
                if (!(value instanceof Number number && number.intValue() == -1
                        && sourcePath.endsWith("custom-model-data"))) result.set(targetPath, value);
            }
        } else if (optional && previous.isSet(sourcePath) && newest.isSet(targetPath)) {
            result.set(targetPath, null);
        }
    }

    private static String repairInvalid(String name, YamlConfiguration candidate,
                                        YamlConfiguration newest, JavaPlugin plugin) throws IOException {
        for (int attempt = 0; attempt < 80; attempt++) {
            try {
                validate(name, candidate);
                return candidate.saveToString();
            } catch (GuiDefinitionException exception) {
                String path = exception.key();
                // Core identifies a bad list member as path[index]. The list is the
                // smallest field that can be replaced with a bundled default.
                String fallbackPath = path == null ? null : path.replaceFirst("\\[\\d+\\]$", "");
                // Core reports the later claimant for an overlap. If that field
                // still has its bundled value, the earlier claimant is the
                // migrated customization that must be repaired instead.
                if (fallbackPath != null && newest.isSet(fallbackPath)
                        && Objects.equals(candidate.get(fallbackPath), newest.get(fallbackPath))
                        && exception.getMessage().startsWith("slot ")) {
                    int separator = exception.getMessage().indexOf(" overlaps ");
                    if (separator > 0) {
                        String previousPath = exception.getMessage().substring(separator + " overlaps ".length())
                                .replaceFirst("\\[\\d+\\]$", "");
                        if (newest.isSet(previousPath)
                                && !Objects.equals(candidate.get(previousPath), newest.get(previousPath))) {
                            fallbackPath = previousPath;
                        }
                    }
                }
                boolean bundledField = fallbackPath != null && newest.isSet(fallbackPath);
                boolean removableOptional = fallbackPath != null && !bundledField
                        && candidate.isSet(fallbackPath) && optionalVisualField(fallbackPath);
                if (fallbackPath == null || fallbackPath.isBlank()
                        || (!bundledField && !removableOptional)
                        || (bundledField && Objects.equals(candidate.get(fallbackPath), newest.get(fallbackPath)))) {
                    throw new IOException(name + ": cannot isolate invalid migrated field " + path
                            + ": " + exception.getMessage(), exception);
                }
                if (plugin != null) plugin.getLogger().warning("GUI migration " + name + ": " + path + "="
                        + candidate.get(fallbackPath) + " (" + exception.getMessage()
                        + ") -> newest bundled default at " + fallbackPath + "="
                        + (bundledField ? newest.get(fallbackPath) : "<absent>"));
                candidate.set(fallbackPath, bundledField ? newest.get(fallbackPath) : null);
            }
        }
        throw new IOException(name + ": too many invalid migrated GUI fields");
    }

    private static boolean optionalVisualField(String path) {
        if (!(path.startsWith("buttons.") || path.startsWith("filler.")
                || path.startsWith("entries.") || path.startsWith("decorations."))) return false;
        String field = path.substring(path.lastIndexOf('.') + 1);
        return Set.of("amount", "name", "lore", "custom-model-data", "glow", "item-flags",
                "skull-skin-url", "default-name").contains(field);
    }

    static void validate(String name, YamlConfiguration gui) throws GuiDefinitionException {
        parseDefinition(name, gui);
    }

    static PublicGuiDefinition parseDefinition(String name, YamlConfiguration gui) throws GuiDefinitionException {
        return PublicGuiSchema.parse("shop." + name.replace('-', '_'), "guis/" + name + ".yml",
                gui, contract(name));
    }

    static void validateShopLayout(Path shopDir, Map<Path, byte[]> proposed, Path guiDir)
            throws IOException, InvalidConfigurationException, GuiDefinitionException {
        if (!Files.isDirectory(shopDir)) return;
        PublicGuiDefinition main = parseDefinition("main", yaml(proposed.get(guiDir.resolve("main.yml"))));
        PublicGuiDefinition section = parseDefinition("shop-section", yaml(proposed.get(guiDir.resolve("shop-section.yml"))));
        Set<Integer> mainSlots = Set.copyOf(main.entries().get("sections").slots());
        Set<Integer> productSlots = Set.copyOf(section.entries().get("products").slots());
        Set<Integer> occupiedMainSlots = new HashSet<>();
        try (var files = Files.list(shopDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).toList()) {
                YamlConfiguration shop = new YamlConfiguration();
                shop.load(file.toFile());
                for (String field : List.of("id", "title", "icon")) {
                    Object value = shop.get(field);
                    if (value != null && !(value instanceof String)) {
                        throw new IOException(file.getFileName() + ": " + field + " must be text");
                    }
                }
                Object descriptionValue = shop.get("description");
                if (descriptionValue != null && !(descriptionValue instanceof String)
                        && (!(descriptionValue instanceof List<?> lines)
                        || lines.stream().anyMatch(line -> !(line instanceof String)))) {
                    throw new IOException(file.getFileName() + ": description must be text or a list of text lines");
                }
                Object sizeValue = shop.get("size");
                if (sizeValue != null && (!(sizeValue instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.doubleValue() < 9 || number.doubleValue() > 54
                        || number.doubleValue() % 9 != 0)) {
                    throw new IOException(file.getFileName() + ": size must be a whole number of slots from 9 to 54");
                }
                Object enabledValue = shop.get("enabled");
                if (enabledValue != null && !(enabledValue instanceof Boolean)) {
                    throw new IOException(file.getFileName() + ": enabled must be true or false");
                }
                Object slotValue = shop.get("slot");
                if (slotValue != null && (!(slotValue instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.doubleValue() < 0 || number.doubleValue() > 53)) {
                    throw new IOException(file.getFileName() + ": main menu slot must be a whole number from 0 to 53");
                }
                int mainSlot = slotValue == null ? 10 : ((Number) slotValue).intValue();
                if (!mainSlots.contains(mainSlot)) {
                    throw new IOException(file.getFileName() + ": main menu slot " + mainSlot
                            + " is outside guis/main.yml entries.sections.slots");
                }
                if ((enabledValue == null || (Boolean) enabledValue) && !occupiedMainSlots.add(mainSlot)) {
                    throw new IOException(file.getFileName() + ": duplicate public main menu slot "
                            + mainSlot);
                }
                ConfigurationSection items = shop.getConfigurationSection("items");
                if (items == null) {
                    if (shop.isSet("items")) {
                        throw new IOException(file.getFileName() + ": items must be an object");
                    }
                    continue;
                }
                for (String id : items.getKeys(false)) {
                    ConfigurationSection item = items.getConfigurationSection(id);
                    if (item == null) {
                        throw new IOException(file.getFileName() + ": product " + id + " must be an object");
                    }
                    for (String field : List.of("material", "display-name")) {
                        Object value = item.get(field);
                        if (value != null && !(value instanceof String)) {
                            throw new IOException(file.getFileName() + ": product " + id + " " + field
                                    + " must be text");
                        }
                    }
                    Object loreValue = item.get("lore");
                    if (loreValue != null && (!(loreValue instanceof List<?> lines)
                            || lines.stream().anyMatch(line -> !(line instanceof String)))) {
                        throw new IOException(file.getFileName() + ": product " + id
                                + " lore must be a list of text lines");
                    }
                    Object pageValue = items.get(id + ".page");
                    if (pageValue != null && (!(pageValue instanceof Number number)
                            || !Double.isFinite(number.doubleValue())
                            || number.doubleValue() != Math.rint(number.doubleValue())
                            || number.doubleValue() < 0 || number.doubleValue() >= Integer.MAX_VALUE)) {
                        throw new IOException(file.getFileName() + ": product " + id
                                + " page must be a nonnegative whole number below " + Integer.MAX_VALUE);
                    }
                    int productSlot = items.getInt(id + ".slot", -1);
                    if (!productSlots.contains(productSlot)) {
                        throw new IOException(file.getFileName() + ": product " + id + " slot "
                                + productSlot + " is outside guis/shop-section.yml entries.products.slots");
                    }
                }
            }
        }
    }

    private static PublicGuiContract contract(String name) {
        PublicGuiContract.InteractionMode mode = name.equals("sell-gui")
                ? PublicGuiContract.InteractionMode.PLUGIN_OWNED : PublicGuiContract.InteractionMode.CORE;
        Map<String, PublicGuiContract.GroupRule> groups = name.equals("sell-gui")
                ? Map.of("sell-slots", new PublicGuiContract.GroupRule(
                    PublicGuiContract.GroupRole.PHYSICAL_INPUT, 1, 45)) : Map.of();
        Set<String> actions = switch (name) {
            case "main" -> Set.of("shop.open-section");
            case "shop-section" -> Set.of("shop.section-back", "shop.section-previous", "shop.section-next", "shop.open-product");
            case "buy-item" -> Set.of("shop.amount-remove-64", "shop.amount-remove-10", "shop.amount-remove-1",
                    "shop.amount-add-1", "shop.amount-add-10", "shop.amount-add-64",
                    "shop.amount-cancel", "shop.amount-bulk", "shop.amount-confirm");
            case "buy-more" -> Set.of("shop.more-back", "shop.more-buy");
            case "sell-gui" -> Set.of("shop.sell");
            case "worth" -> Set.of("shop.worth-previous", "shop.worth-sort", "shop.worth-search",
                    "shop.worth-clear-search", "shop.worth-next");
            default -> Set.of();
        };
        Set<String> placeholders = switch (name) {
            case "main" -> Set.of("section", "section_id");
            case "shop-section" -> Set.of("section_title", "buy", "sell", "stock", "buy_limit", "page", "pages");
            case "buy-item" -> Set.of("item", "amount", "price");
            case "buy-more" -> Set.of("item", "amount", "price", "stacks", "plural", "stack_unit");
            case "rotating-shop" -> Set.of("item", "boost", "price", "base_price", "buy", "time");
            case "worth" -> Set.of("item", "worth", "page", "pages", "sort", "next_sort", "query");
            default -> Set.of();
        };
        return new PublicGuiContract(actions, groups, placeholders, mode);
    }

    private static YamlConfiguration yaml(byte[] bytes) throws InvalidConfigurationException {
        YamlConfiguration value = new YamlConfiguration();
        value.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        return value;
    }

    private static byte[] resource(JavaPlugin plugin, String path) throws IOException {
        try (InputStream input = plugin.getResource(path)) {
            if (input == null) throw new IOException("Bundled GUI resource missing: " + path);
            return input.readAllBytes();
        }
    }

    private static void replace(Path stage, Path target) throws IOException {
        try {
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
