package me.foesio.foShop.validation;

import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopItemType;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.util.ItemKeys;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ShopValidationService {

    public SectionValidationResult parseAndValidate(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return validateSectionYaml(stripExt(file.getName()), yaml);
    }

    public SectionValidationResult validateSectionYaml(String fallbackId, YamlConfiguration yaml) {
        List<String> issues = new ArrayList<>();

        String id = normalizeId(yaml.getString("id", fallbackId));
        if (id.isBlank()) {
            issues.add("Section id is missing or invalid.");
            return new SectionValidationResult(null, 0, issues);
        }

        String title = yaml.getString("title", "&8" + id);
        int size = yaml.getInt("size", 54);
        if (!isValidSize(size)) {
            issues.add("Section " + id + " has invalid size " + size + " (must be 9..54 and divisible by 9).");
            return new SectionValidationResult(null, 0, issues);
        }

        String iconRaw = yaml.getString("icon", "CHEST");
        Material icon = parseMaterial(iconRaw).orElse(null);
        if (icon == null || icon == Material.AIR) {
            issues.add("Section " + id + " has invalid icon material: " + iconRaw + ".");
            return new SectionValidationResult(null, 0, issues);
        }
        ItemStack iconItem = sanitizeIconItem(yaml.getItemStack("icon-item"));
        if (iconItem != null) {
            icon = iconItem.getType();
        }

        List<String> description = readStringList(yaml, "description");
        boolean enabled = yaml.getBoolean("enabled", true);

        int sectionSlot = yaml.getInt("slot", 10);
        if (sectionSlot < 0 || sectionSlot > 53) {
            issues.add("Section " + id + " has invalid menu slot " + sectionSlot + " (must be 0..53).");
            return new SectionValidationResult(null, 0, issues);
        }

        ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection == null) {
            ShopSection section = new ShopSection(id, title, size, icon, iconItem, description, enabled, sectionSlot, List.of());
            return new SectionValidationResult(section, 0, issues);
        }

        Set<String> usedIds = new HashSet<>();
        Set<String> usedSlots = new HashSet<>();
        List<ShopItem> items = new ArrayList<>();

        int skippedItems = 0;
        for (String itemIdRaw : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemIdRaw);
            if (itemSection == null) {
                issues.add("Section " + id + " item " + itemIdRaw + " is not a valid object.");
                skippedItems++;
                continue;
            }

            String itemId = normalizeId(itemIdRaw);
            if (itemId.isBlank()) {
                issues.add("Section " + id + " has an item with empty/invalid id: " + itemIdRaw + ".");
                skippedItems++;
                continue;
            }

            if (!usedIds.add(itemId)) {
                issues.add("Section " + id + " contains duplicate item id: " + itemId + ".");
                skippedItems++;
                continue;
            }

            ShopItemType type = ShopItemType.fromString(itemSection.getString("type", "item"));
            ItemStack itemStack = itemSection.getItemStack("item-stack");
            String materialRaw = itemSection.getString("material", defaultMaterial(type).name());
            Material material = itemStack == null ? parseMaterial(materialRaw).orElse(null) : itemStack.getType();
            if (material == null || material == Material.AIR) {
                issues.add("Section " + id + " item " + itemId + " has invalid material: " + materialRaw + ".");
                skippedItems++;
                continue;
            }

            int page = itemSection.getInt("page", 0);
            if (page < 0) {
                issues.add("Section " + id + " item " + itemId + " has invalid page " + page + ".");
                skippedItems++;
                continue;
            }

            int slot = itemSection.getInt("slot", -1);
            if (slot < 0 || slot >= size) {
                issues.add("Section " + id + " item " + itemId + " has invalid slot " + slot + " (0-" + (size - 1) + ").");
                skippedItems++;
                continue;
            }

            if (isReservedSlot(size, slot)) {
                issues.add("Section " + id + " item " + itemId + " uses reserved slot " + slot + ".");
                skippedItems++;
                continue;
            }

            String slotKey = page + ":" + slot;
            if (!usedSlots.add(slotKey)) {
                issues.add("Section " + id + " has duplicate item slot " + slot + " on page " + (page + 1) + " (item " + itemId + ").");
                skippedItems++;
                continue;
            }

            Integer stackSize = itemSection.contains("stack-size") ? itemSection.getInt("stack-size") : null;
            int maxStack = maxStackSize(material);
            if (stackSize != null && (stackSize < 1 || stackSize > maxStack)) {
                issues.add("Section " + id + " item " + itemId + " has invalid stack-size " + stackSize + " (1-" + maxStack + ").");
                skippedItems++;
                continue;
            }

            int effectiveStackSize = stackSize == null ? maxStack : stackSize;
            int amount = itemSection.getInt("amount", 1);
            if (amount < 1 || amount > effectiveStackSize) {
                issues.add("Section " + id + " item " + itemId + " has invalid amount " + amount + " (1-" + effectiveStackSize + ").");
                skippedItems++;
                continue;
            }

            double buy = itemSection.getDouble("buy-price", -1D);
            double sell = itemSection.getDouble("sell-price", -1D);

            if (!isValidPrice(buy)) {
                issues.add("Section " + id + " item " + itemId + " has invalid buy-price " + buy + ".");
                skippedItems++;
                continue;
            }

            if (!isValidPrice(sell)) {
                issues.add("Section " + id + " item " + itemId + " has invalid sell-price " + sell + ".");
                skippedItems++;
                continue;
            }

            List<String> lore = itemSection.getStringList("lore");
            String displayName = itemSection.getString("display-name");
            Integer customModelData = itemSection.contains("custom-model-data") ? itemSection.getInt("custom-model-data") : null;
            Map<String, Integer> enchants = new HashMap<>();
            ConfigurationSection enchantsSection = itemSection.getConfigurationSection("enchants");
            boolean invalidEnchant = false;
            if (enchantsSection != null) {
                for (String key : enchantsSection.getKeys(false)) {
                    String normalized = ItemKeys.normalizeEnchantmentKey(key);
                    if (normalized == null || ItemKeys.enchantment(normalized) == null) {
                        issues.add("Section " + id + " item " + itemId + " has invalid enchant: " + key + ".");
                        invalidEnchant = true;
                        continue;
                    }
                    enchants.put(normalized, Math.max(1, enchantsSection.getInt(key, 1)));
                }
            }
            if (invalidEnchant) {
                skippedItems++;
                continue;
            }
            String permission = firstNonBlank(itemSection.getString("permission"), itemSection.getString("permission-node"));
            String requiredPermission = itemSection.getString("required-permission", "");
            List<String> commands = readStringList(itemSection, "commands");
            String command = itemSection.getString("command");
            if (command != null && !command.isBlank()) {
                commands = new ArrayList<>(commands);
                commands.add(command);
            }
            String enchantment = firstNonBlank(itemSection.getString("enchantment"), itemSection.getString("enchant"));
            int enchantmentLevel = Math.max(1, itemSection.getInt("enchantment-level", itemSection.getInt("enchantmentLevel", itemSection.getInt("level", 1))));
            Integer stock = readOptionalInt(itemSection, "stock");
            Integer buyLimit = readOptionalInt(itemSection, "buy-limit");
            Integer limitResetSeconds = readOptionalInt(itemSection, "limit-reset-seconds");
            String rawNbt = itemSection.getString("nbt");

            if (type == ShopItemType.PERMISSION && (permission == null || permission.isBlank())) {
                issues.add("Section " + id + " item " + itemId + " is a permission item without permission-node.");
            }
            if (type == ShopItemType.COMMAND && commands.isEmpty()) {
                issues.add("Section " + id + " item " + itemId + " is a command item without commands.");
            }
            if (type == ShopItemType.ENCHANTMENT && (enchantment == null || enchantment.isBlank())) {
                issues.add("Section " + id + " item " + itemId + " is an enchantment item without enchantment.");
            }
            if (type == ShopItemType.ENCHANTMENT && ItemKeys.enchantment(enchantment) == null) {
                issues.add("Section " + id + " item " + itemId + " has invalid enchantment: " + enchantment + ".");
                skippedItems++;
                continue;
            }
            if (type == ShopItemType.ENCHANTMENT) {
                enchantment = ItemKeys.normalizeEnchantmentKey(enchantment);
            }

            items.add(new ShopItem(itemId, type, material, page, slot, amount, buy, sell, lore, displayName, customModelData, enchants, stackSize,
                    itemStack, permission, requiredPermission, commands, enchantment, enchantmentLevel, stock, buyLimit, limitResetSeconds, rawNbt));
        }

        ShopSection section = new ShopSection(id, title, size, icon, iconItem, description, enabled, sectionSlot, items);
        return new SectionValidationResult(section, skippedItems, issues);
    }

    public List<String> validateSectionModel(ShopSection section) {
        List<String> issues = new ArrayList<>();

        String id = normalizeId(section.id());
        if (id.isBlank()) {
            issues.add("Section id is missing or invalid.");
            return issues;
        }

        if (!isValidSize(section.size())) {
            issues.add("Section " + section.id() + " has invalid size " + section.size() + ".");
            return issues;
        }

        if (section.icon() == null || section.icon() == Material.AIR) {
            issues.add("Section " + section.id() + " has invalid icon material.");
            return issues;
        }
        ItemStack iconItem = sanitizeIconItem(section.iconItem());
        if (iconItem != null && (iconItem.getType() == Material.AIR)) {
            issues.add("Section " + section.id() + " has invalid icon item.");
            return issues;
        }

        if (section.slot() < 0 || section.slot() > 53) {
            issues.add("Section " + section.id() + " has invalid menu slot " + section.slot() + ".");
            return issues;
        }

        Set<String> ids = new HashSet<>();
        Set<String> slots = new HashSet<>();
        for (ShopItem item : section.items()) {
            String itemId = normalizeId(item.id());
            if (itemId.isBlank()) {
                issues.add("Section " + section.id() + " contains item with empty/invalid id.");
                continue;
            }

            if (!ids.add(itemId)) {
                issues.add("Section " + section.id() + " contains duplicate item id " + itemId + ".");
                continue;
            }

            if (item.material() == null || item.material() == Material.AIR) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid material.");
                continue;
            }

            if (item.page() < 0) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid page " + item.page() + ".");
                continue;
            }

            if (item.slot() < 0 || item.slot() >= section.size()) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid slot " + item.slot() + ".");
                continue;
            }

            if (isReservedSlot(section.size(), item.slot())) {
                issues.add("Section " + section.id() + " item " + item.id() + " uses reserved slot " + item.slot() + ".");
                continue;
            }

            String slotKey = item.page() + ":" + item.slot();
            if (!slots.add(slotKey)) {
                issues.add("Section " + section.id() + " has duplicate item slot " + item.slot() + " on page " + (item.page() + 1) + ".");
                continue;
            }

            if (item.stackSize() != null && (item.stackSize() < 1 || item.stackSize() > maxStackSize(item.material()))) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid stack-size " + item.stackSize() + ".");
            }

            if (item.amount() < 1 || item.amount() > item.effectiveStackSize()) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid amount " + item.amount() + ".");
            }

            if (!isValidPrice(item.buyPrice())) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid buy-price " + item.buyPrice() + ".");
            }

            if (!isValidPrice(item.sellPrice())) {
                issues.add("Section " + section.id() + " item " + item.id() + " has invalid sell-price " + item.sellPrice() + ".");
            }

            if (item.type() == ShopItemType.PERMISSION && (item.permission() == null || item.permission().isBlank())) {
                issues.add("Section " + section.id() + " item " + item.id() + " is missing permission-node.");
            }

            if (item.type() == ShopItemType.COMMAND && item.commands().isEmpty()) {
                issues.add("Section " + section.id() + " item " + item.id() + " is missing commands.");
            }

            if (item.type() == ShopItemType.ENCHANTMENT) {
                if (item.enchantment() == null || item.enchantment().isBlank()) {
                    issues.add("Section " + section.id() + " item " + item.id() + " is missing enchantment.");
                } else if (ItemKeys.enchantment(item.enchantment()) == null) {
                    issues.add("Section " + section.id() + " item " + item.id() + " has invalid enchantment " + item.enchantment() + ".");
                }
            }
            for (Map.Entry<String, Integer> enchant : item.enchants().entrySet()) {
                if (ItemKeys.enchantment(enchant.getKey()) == null) {
                    issues.add("Section " + section.id() + " item " + item.id() + " has invalid enchant " + enchant.getKey() + ".");
                }
                if (enchant.getValue() == null || enchant.getValue() < 1) {
                    issues.add("Section " + section.id() + " item " + item.id() + " has invalid enchant level for " + enchant.getKey() + ".");
                }
            }
        }

        return issues;
    }

    public String firstBlockingIssue(List<String> issues) {
        if (issues == null) {
            return null;
        }
        return issues.stream()
                .filter(issue -> !isDeferredConfigurationWarning(issue))
                .findFirst()
                .orElse(null);
    }

    private boolean isDeferredConfigurationWarning(String issue) {
        if (issue == null) {
            return false;
        }
        return issue.endsWith("is a permission item without permission-node.")
                || issue.endsWith("is a command item without commands.")
                || issue.endsWith("is an enchantment item without enchantment.")
                || issue.endsWith("is missing permission-node.")
                || issue.endsWith("is missing commands.")
                || issue.endsWith("is missing enchantment.");
    }

    public boolean isValidPrice(double price) {
        if (!Double.isFinite(price)) {
            return false;
        }
        return price == -1D || price >= 0D;
    }

    private boolean isReservedSlot(int size, int slot) {
        return slot == size - 9 || slot == size - 5 || slot == size - 1;
    }

    private int maxStackSize(Material material) {
        return Math.max(1, Math.min(64, material.getMaxStackSize()));
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

    private boolean isValidSize(int size) {
        return size >= 9 && size <= 54 && size % 9 == 0;
    }

    private String normalizeId(String id) {
        if (id == null) {
            return "";
        }

        String out = id.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        out = out.replaceAll("[^a-z0-9_\\-]", "");
        if (out.length() > 40) {
            out = out.substring(0, 40);
        }
        return out;
    }

    private Optional<Material> parseMaterial(String raw) {
        return ItemKeys.material(raw);
    }

    private ItemStack sanitizeIconItem(ItemStack input) {
        if (input == null || input.getType() == Material.AIR) {
            return null;
        }

        ItemStack out = new ItemStack(input.getType(), 1);
        var sourceMeta = input.getItemMeta();
        var targetMeta = out.getItemMeta();
        if (sourceMeta != null && targetMeta != null) {
            if (sourceMeta.hasDisplayName()) {
                targetMeta.setDisplayName(sourceMeta.getDisplayName());
            }
            if (sourceMeta.hasCustomModelData()) {
                targetMeta.setCustomModelData(sourceMeta.getCustomModelData());
            }
            for (var enchantment : sourceMeta.getEnchants().entrySet()) {
                targetMeta.addEnchant(enchantment.getKey(), enchantment.getValue(), true);
            }
            if (sourceMeta.hasLore()) {
                targetMeta.setLore(sourceMeta.getLore());
            }
            out.setItemMeta(targetMeta);
        }
        return out;
    }

    private List<String> readStringList(ConfigurationSection section, String path) {
        List<String> list = section.getStringList(path);
        if (!list.isEmpty()) {
            return list;
        }
        String single = section.getString(path);
        if (single != null && !single.isBlank()) {
            return List.of(single);
        }
        return List.of();
    }

    private Integer readOptionalInt(ConfigurationSection section, String path) {
        return section.contains(path) ? section.getInt(path) : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stripExt(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        if (lower.endsWith(".yaml")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    public record SectionValidationResult(ShopSection section, int skippedItems, List<String> issues) {
        public boolean validSection() {
            return section != null;
        }
    }
}
