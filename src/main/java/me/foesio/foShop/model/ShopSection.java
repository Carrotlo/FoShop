package me.foesio.foShop.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopSection {

    private final String id;
    private final String title;
    private final int size;
    private final Material icon;
    private final ItemStack iconItem;
    private final List<String> description;
    private final boolean enabled;
    private final int slot;
    private final Map<Integer, ShopItem> itemsBySlot;
    private final Map<Integer, Map<Integer, ShopItem>> itemsByPageAndSlot;
    private final int totalPages;

    public ShopSection(String id, String title, int size, Material icon, int slot, Collection<ShopItem> items) {
        this(id, title, size, icon, null, List.of(), true, slot, items);
    }

    public ShopSection(String id, String title, int size, Material icon, ItemStack iconItem, int slot, Collection<ShopItem> items) {
        this(id, title, size, icon, iconItem, List.of(), true, slot, items);
    }

    public ShopSection(String id, String title, int size, Material icon, List<String> description, int slot, Collection<ShopItem> items) {
        this(id, title, size, icon, null, description, true, slot, items);
    }

    public ShopSection(String id, String title, int size, Material icon, ItemStack iconItem, List<String> description, int slot, Collection<ShopItem> items) {
        this(id, title, size, icon, iconItem, description, true, slot, items);
    }

    public ShopSection(String id, String title, int size, Material icon, ItemStack iconItem, List<String> description, boolean enabled, int slot, Collection<ShopItem> items) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.icon = icon;
        this.iconItem = iconItem == null ? null : iconItem.clone();
        this.description = description == null ? List.of() : List.copyOf(description);
        this.enabled = enabled;
        this.slot = slot;
        this.itemsBySlot = new HashMap<>();
        this.itemsByPageAndSlot = new HashMap<>();
        int maxPage = 0;
        for (ShopItem item : items) {
            if (item.page() == 0) {
                this.itemsBySlot.put(item.slot(), item);
            }
            this.itemsByPageAndSlot.computeIfAbsent(item.page(), ignored -> new HashMap<>()).put(item.slot(), item);
            maxPage = Math.max(maxPage, item.page());
        }
        this.totalPages = maxPage + 1;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public Material icon() {
        return icon;
    }

    public ItemStack iconItem() {
        return iconItem == null ? null : iconItem.clone();
    }

    public List<String> description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public int slot() {
        return slot;
    }

    public Map<Integer, ShopItem> itemsBySlot() {
        return Collections.unmodifiableMap(itemsBySlot);
    }

    public Map<Integer, ShopItem> itemsBySlot(int page) {
        return Collections.unmodifiableMap(itemsByPageAndSlot.getOrDefault(page, Map.of()));
    }

    public Collection<ShopItem> items() {
        return allItems();
    }

    public Collection<ShopItem> allItems() {
        return itemsByPageAndSlot.values().stream()
                .flatMap(pageItems -> pageItems.values().stream())
                .toList();
    }

    public int totalPages() {
        return Math.max(1, totalPages);
    }
}
