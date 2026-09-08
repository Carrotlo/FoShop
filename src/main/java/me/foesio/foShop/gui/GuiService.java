package me.foesio.foShop.gui;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.CursorItemEditor;
import me.foesio.core.editor.ChatPromptManager;
import me.foesio.core.editor.CycleOption;
import me.foesio.core.editor.CycleOptions;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.editor.EditorSettingSaver;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.message.FoStyle;
import me.foesio.core.text.FoText;
import me.foesio.core.inventory.InventoryDepositResult;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.selector.StringSelectionEntries;
import me.foesio.core.selector.TriStateSelectionActionType;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionEntry;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.sound.SoundTypes;
import me.foesio.core.text.PromptNormalizer;
import me.foesio.foShop.FoShop;
import me.foesio.foShop.booster.ActiveSellBooster;
import me.foesio.foShop.booster.SellBoosterInputParser;
import me.foesio.foShop.booster.SellBoosterScope;
import me.foesio.foShop.logging.SellTransactionLogger;
import me.foesio.foShop.model.ShopItem;
import me.foesio.foShop.model.ShopItemType;
import me.foesio.foShop.model.ShopSection;
import me.foesio.foShop.rotating.RotatingShopService;
import me.foesio.foShop.shop.GlobalSellPriceService;
import me.foesio.foShop.shop.ShopManager;
import me.foesio.foShop.util.DurationUtil;
import me.foesio.foShop.util.ItemKeys;
import me.foesio.foShop.util.ReloadFeedback;
import me.foesio.foShop.util.Text;
import me.foesio.foShop.util.WorthPriceFormatter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

public class GuiService implements Listener {

    private static final int[] PAGE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] GLOBAL_PRICE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int[] BUY_MORE_STACK_OPTIONS = {1, 2, 3, 4, 6, 8, 9};
    private static final int[] BUY_MORE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PLAYER_STORAGE_SLOT_COUNT = 36;
    private static final int THREE_ROW_BACK_SLOT = 22;
    private static final int FOUR_ROW_BACK_SLOT = 31;
    private static final int SIX_ROW_BACK_SLOT = 49;
    private static final int MAX_SELL_CONTAINER_DEPTH = 3;
    private static final int MAX_SELL_PROCESSING_STEPS = 2048;
    private static final int MAX_SELL_RECEIPT_LINES = 80;
    private static final String OTHER_SOLD_ITEMS_LABEL = "Other Items";
    private static final String ROTATING_SECTIONS_SELECTOR_TITLE = "Rotating Sections";
    private static final String ROTATING_ITEMS_SELECTOR_TITLE_PREFIX = "Rotating Items: ";
    private static final GuiButtonConfig GUI_BUTTONS = GuiButtonConfig.defaults();
    private static final List<CycleOption> RECEIPT_TYPE_OPTIONS = List.of(
            new CycleOption("0", "Disabled"),
            new CycleOption("1", "Hover receipt")
    );
    private static final List<CycleOption> WORTH_SORT_OPTIONS = List.of(
            new CycleOption("NAME", "Name"),
            new CycleOption("PRICE", "Price")
    );
    private static final List<CycleOption> ITEM_TYPE_OPTIONS = List.of(
            new CycleOption("item", "Item"),
            new CycleOption("permission", "Permission"),
            new CycleOption("command", "Command"),
            new CycleOption("dummy", "Dummy")
    );

    private final FoShop plugin;
    private final SellTransactionLogger transactionLogger;
    private final EditorSettingSaver configSettingSaver;
    private final ChatPromptManager chatPrompts;
    private final Map<UUID, Integer> rotatingItemSectionPages = new HashMap<>();
    private final Map<UUID, Integer> sectionItemBrowserPages = new HashMap<>();
    private final Set<UUID> sellingInProgress = new HashSet<>();
    private final Set<UUID> suppressConfirmDeleteClose = new HashSet<>();
    private final Set<UUID> suppressConfirmRemoveBoosterClose = new HashSet<>();
    private final Set<UUID> nativeDialogFallbackWarnings = new HashSet<>();
    private final Map<UUID, ScreenState> activeScreens = new HashMap<>();
    private final Set<UUID> pendingBackNavigations = new HashSet<>();

    public GuiService(FoShop plugin) {
        this.plugin = plugin;
        this.transactionLogger = new SellTransactionLogger(plugin);
        this.configSettingSaver = new EditorSettingSaver(plugin, () -> plugin.reloadAll());
        this.chatPrompts = new ChatPromptManager(plugin, plugin.getCore().scheduler());
    }

    private void openInventory(Player player, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                inventory.setItem(slot, DialogIcons.forViewer(player, item));
            }
        }
        player.openInventory(inventory);
        screenOpened(player, inventory.getHolder());
    }

    private void screenOpened(Player player, InventoryHolder holder) {
        boolean editor = isEditorScreen(holder);
        ScreenState previous = activeScreens.put(player.getUniqueId(), new ScreenState(player.getOpenInventory().getTitle(), editor));
        if (pendingBackNavigations.remove(player.getUniqueId())) {
            if (previous == null || previous.editor()) {
                plugin.getEditorSounds().back(player);
            } else {
                plugin.getSounds().play(player, "gui.back");
            }
            return;
        }
        if (previous != null && previous.editor() == editor && previous.title().equals(player.getOpenInventory().getTitle())) {
            return;
        }
        if (editor) {
            plugin.getEditorSounds().open(player);
        } else if (holder instanceof SellGuiHolder) {
            plugin.getSounds().play(player, "sell.open");
        } else {
            plugin.getGuiSounds().open(player);
        }
    }

    private boolean isEditorScreen(InventoryHolder holder) {
        return holder instanceof AdminEditorHolder
                || holder instanceof SettingsEditorHolder
                || holder instanceof SellBoosterEditorHolder
                || holder instanceof ActiveSellBoostersHolder
                || holder instanceof ConfirmRemoveSellBoosterHolder
                || holder instanceof GlobalSellPriceEditorHolder
                || holder instanceof GlobalSellPriceListHolder list && list.editor
                || holder instanceof RotatingEditorHolder
                || holder instanceof RotatingSectionDetailHolder
                || holder instanceof SectionDetailHolder
                || holder instanceof ItemEditorHolder
                || holder instanceof ConfirmDeleteItemHolder
                || holder instanceof ConfirmDeleteSectionHolder
                || holder instanceof EntryBrowserHolder
                || holder instanceof TriStateSelectionHolder;
    }

    private void markBackNavigation(Player player) {
        pendingBackNavigations.add(player.getUniqueId());
    }

    private boolean isBackButton(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_DOOR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName()
                && net.md_5.bungee.api.ChatColor.stripColor(meta.getDisplayName()).trim().equalsIgnoreCase("← Back");
    }

    private void clearScreenTrackingIfClosed(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (!(holder instanceof FoHolder) && !(holder instanceof EntryBrowserHolder) && !(holder instanceof TriStateSelectionHolder)) {
                activeScreens.remove(player.getUniqueId());
                pendingBackNavigations.remove(player.getUniqueId());
            }
        });
    }

    public void openMainShop(Player player) {
        MainMenuHolder holder = new MainMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, plugin.getFoConfig().getMainSize(), plugin.getFoConfig().mainTitleSmallCaps());
        holder.setInventory(inventory);

        fillBackground(inventory, "main");

        int autoSlot = 10;
        for (ShopSection section : plugin.getShopManager().getSectionsOrdered()) {
            if (!section.enabled()) {
                continue;
            }
            int slot = section.slot();
            if (slot < 0 || slot >= inventory.getSize()) {
                logGuiWarning("main", "Section " + section.id() + " has invalid main menu slot " + slot + "; using automatic slot.");
                slot = nextFreeMainSlot(holder, autoSlot, inventory.getSize());
            } else if (holder.slotToSection.containsKey(slot)) {
                logGuiWarning("main", "Section " + section.id() + " main menu slot " + slot + " is already used; using automatic slot.");
                slot = nextFreeMainSlot(holder, autoSlot, inventory.getSize());
            }
            if (slot < 0 || slot >= inventory.getSize()) {
                logGuiWarning("main", "No free main menu slot for section " + section.id() + "; skipped.");
                continue;
            }
            autoSlot = Math.max(autoSlot, slot + 1);
            holder.slotToSection.put(slot, section.id());

            ItemStack icon = section.iconItem() == null ? new ItemStack(section.icon()) : section.iconItem().clone();
            ItemMeta iconMeta = icon.getItemMeta();
            if (iconMeta != null) {
                if (!iconMeta.hasDisplayName()) {
                    iconMeta.setDisplayName(Text.colorize("&#03fc88" + prettify(section.id())));
                } else {
                    iconMeta.setDisplayName(Text.colorize(iconMeta.getDisplayName()));
                }
                List<String> description = formatSectionDescription(player, section);
                if (description != null) {
                    iconMeta.setLore(description);
                } else if (iconMeta.hasLore()) {
                    iconMeta.setLore(colorizeLore(iconMeta.getLore()));
                }
                iconMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                icon.setItemMeta(iconMeta);
            }
            inventory.setItem(slot, icon);
        }

        openInventory(player, inventory);
    }

    private int nextFreeMainSlot(MainMenuHolder holder, int start, int size) {
        int safeStart = Math.clamp(start, 0, Math.max(0, size - 1));
        for (int slot = safeStart; slot < size; slot++) {
            if (!holder.slotToSection.containsKey(slot)) {
                return slot;
            }
        }
        for (int slot = 0; slot < safeStart; slot++) {
            if (!holder.slotToSection.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public void openShopSection(Player player, ShopSection section) {
        openShopSection(player, section, 0);
    }

    public void openShopSection(Player player, ShopSection section, int page) {
        int currentPage = Math.clamp(page, 0, section.totalPages() - 1);
        SectionHolder holder = new SectionHolder(section.id(), currentPage);
        String title = section.title().replace("%page%", String.valueOf(currentPage + 1));
        Inventory inventory = Bukkit.createInventory(holder, section.size(), plugin.getFoConfig().sectionTitleSmallCaps(title));
        holder.setInventory(inventory);

        fillBackground(inventory, "shop-section");

        for (ShopItem shopItem : section.itemsBySlot(currentPage).values()) {
            ItemStack itemStack = createShopDisplayItem(player, section.id(), shopItem, shopItem.amount());

            if (shopItem.slot() >= 0 && shopItem.slot() < inventory.getSize()) {
                inventory.setItem(shopItem.slot(), itemStack);
            }
        }

        holder.backSlot = readGuiSlot("shop-section", "buttons.back", inventory.getSize() - 5, inventory.getSize());
        inventory.setItem(holder.backSlot, GUI_BUTTONS.back(player));
        if (section.totalPages() > 1) {
            holder.previousSlot = readGuiSlot("shop-section", "buttons.previous", inventory.getSize() - 9, inventory.getSize());
            holder.nextSlot = readGuiSlot("shop-section", "buttons.next", inventory.getSize() - 1, inventory.getSize());
            setPreviousPageButton(player, inventory, holder.previousSlot, currentPage, section.totalPages());
            setNextPageButton(player, inventory, holder.nextSlot, currentPage, section.totalPages());
        }

        openInventory(player, inventory);
    }

    public void openRotatingShop(Player player) {
        if (!plugin.getRotatingShopService().isEnabled()) {
            plugin.getMessages().send(player, "rotating-shop-disabled");
            return;
        }

        RotatingShopHolder holder = new RotatingShopHolder();
        String title = plugin.getFoConfig().guiString("rotating-shop", "title", "&8Rotating Shop");
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps(title));
        holder.setInventory(inventory);

        fillBackground(inventory, "rotating-shop");

        List<RotatingShopService.RotatingEntry> entries = plugin.getRotatingShopService().activeEntries();
        if (entries.isEmpty()) {
            GuiButton empty = readGuiButton("rotating-shop", "empty", 13, Material.GRAY_DYE,
                    "&#ff5d73No Boosts", List.of("&#ffffffNo sellable rotating items available."), inventory.getSize());
            inventory.setItem(empty.slot(), createButtonItem(player, empty));
            openInventory(player, inventory);
            return;
        }

        int[] slots = configuredSlots("rotating-shop", "slots", new int[]{11, 12, 13, 14, 15}, entries.size(), inventory.getSize());
        for (int index = 0; index < entries.size() && index < slots.length; index++) {
            RotatingShopService.RotatingEntry entry = entries.get(index);
            GlobalSellPriceService.GlobalSellPriceEntry globalEntry = plugin.getRotatingShopService().findGlobalEntry(entry).orElse(null);
            GlobalSellPriceService.GlobalPotionEntry globalPotionEntry = plugin.getRotatingShopService().findGlobalPotionEntry(entry).orElse(null);
            GlobalSellPriceService.GlobalEnchantmentEntry globalEnchantmentEntry = plugin.getRotatingShopService().findGlobalEnchantmentEntry(entry).orElse(null);
            ShopItem item = plugin.getRotatingShopService().findItem(entry).orElse(null);
            if (item == null && globalEntry == null && globalPotionEntry == null && globalEnchantmentEntry == null) {
                continue;
            }
            int slot = slots[index];
            holder.slotToEntry.put(slot, entry);
            if (globalEnchantmentEntry != null) {
                inventory.setItem(slot, createGlobalEnchantmentRotatingDisplayItem(entry, globalEnchantmentEntry));
            } else if (globalPotionEntry != null) {
                inventory.setItem(slot, createGlobalPotionRotatingDisplayItem(entry, globalPotionEntry));
            } else if (globalEntry != null) {
                inventory.setItem(slot, createGlobalRotatingDisplayItem(entry, globalEntry));
            } else {
                inventory.setItem(slot, createRotatingDisplayItem(player, entry, item));
            }
        }

        openInventory(player, inventory);
    }

    public void openWorth(Player player) {
        if (plugin.getGlobalSellPriceService() == null || !plugin.getGlobalSellPriceService().isEnabled()) {
            plugin.getMessages().send(player, "worth-disabled");
            return;
        }
        openGlobalSellPriceList(player, false, 0, "", WorthSort.NAME);
    }

    private void openGlobalSellPriceEditor(Player player) {
        GlobalSellPriceEditorHolder holder = new GlobalSellPriceEditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Global Prices"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        boolean enabled = plugin.getFoConfig().isGlobalSellPricesEnabled();
        long priced = plugin.getGlobalSellPriceService().entries().stream().filter(entry -> entry.enabled() && entry.price() > 0D).count()
                + plugin.getGlobalSellPriceService().potionEntries().stream().filter(entry -> entry.enabled() && entry.price() > 0D).count();
        inventory.setItem(10, toggleItem(player, "Global Sell Prices", enabled, "Use global-sell-prices.yml as fallback sell prices."));
        inventory.setItem(12, button(player, Material.EMERALD, "Price Browser", List.of(
                "&#ffffffEntries: &#03fc88" + (plugin.getGlobalSellPriceService().entries().size() + plugin.getGlobalSellPriceService().potionEntries().size()),
                "&#ffffffEnabled prices: &#03fc88" + priced,
                "&#a7b8b0Click to browse, edit, blacklist, and toggle rotating."
        ), "browse prices"));
        inventory.setItem(11, toggleItem(player, "Worth Item Lore", plugin.getFoConfig().isWorthLoreConfiguredEnabled(),
                "Show packet-only stack worth in inventories and containers."));
        inventory.setItem(14, EditorItemFactory.filler());
        inventory.setItem(THREE_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    private void openGlobalSellPriceList(Player player, boolean editor, int page, String query, WorthSort sort) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        WorthSort activeSort = sort == null ? WorthSort.NAME : sort;
        List<GlobalPriceListEntry> entries = globalPriceEntries(search, activeSort, editor);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) GLOBAL_PRICE_SLOTS.length));
        int currentPage = Math.clamp(page, 0, totalPages - 1);
        GlobalSellPriceListHolder holder = new GlobalSellPriceListHolder(editor, currentPage, totalPages, search, activeSort);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.getFoConfig().sectionTitleSmallCaps(editor ? "&8Global Prices" : "&8Worth"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        int start = currentPage * GLOBAL_PRICE_SLOTS.length;
        for (int i = 0; i < GLOBAL_PRICE_SLOTS.length; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }

            GlobalPriceListEntry entry = entries.get(index);
            int slot = GLOBAL_PRICE_SLOTS[i];
            holder.slotToEntry.put(slot, entry);
            inventory.setItem(slot, globalPriceItem(entry, editor));
        }

        if (entries.isEmpty()) {
            inventory.setItem(22, createItem(Material.GRAY_DYE, "&#ff5d73No Matches", List.of("&#ffffffNo global sell prices matched your search.")));
        }

        setPreviousPageButton(player, inventory, 45, currentPage, totalPages);
        inventory.setItem(47, EditorItemFactory.cycle(player, plugin.getMessages(), "Sort", activeSort.name(), WORTH_SORT_OPTIONS));
        if (editor) {
            inventory.setItem(49, GUI_BUTTONS.back(player));
        }
        inventory.setItem(51, GUI_BUTTONS.search(player, search));
        setClearSearchButton(player, inventory, 52, "worth", search);
        setNextPageButton(player, inventory, 53, currentPage, totalPages);

        openInventory(player, inventory);
    }

    public void openSellGui(Player player) {
        if (plugin.getFoConfig().isSellGamemodeBlocked(player.getGameMode().name())) {
            plugin.getMessages().send(player, "sellgui-gamemode-not-allowed", Map.of("{gamemode}", player.getGameMode().name()));
            plugin.getSounds().play(player, "sell.failure");
            return;
        }

        SellGuiHolder holder = new SellGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, plugin.getFoConfig().getSellSize(), plugin.getFoConfig().sellTitleSmallCaps());
        holder.setInventory(inventory);

        int inputEnd = inventory.getSize() - 9;
        for (int slot = inputEnd; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, createFillerItem("sell-gui"));
        }

        GuiButton sellButton = readGuiButton("sell-gui", "buttons.sell", inventory.getSize() - 5, Material.EMERALD,
                "&#03fc88Sell Items",
                List.of(
                        "&#ffffffPut items in the top rows.",
                        "&#ffffffClick to sell all sellable items.",
                        "&#a7b8b0Unsellable items are returned."
                ),
                inventory.getSize());
        holder.sellSlot = sellButton.slot();
        inventory.setItem(sellButton.slot(), createButtonItem(player, sellButton));

        applySellGuiDecorations(inventory, holder);
        openInventory(player, inventory);
    }

    public Set<Integer> sellGuiInputSlots(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof SellGuiHolder holder)) {
            return Set.of();
        }
        int inputEnd = inventory.getSize() - 9;
        Set<Integer> slots = new HashSet<>(Math.max(0, inputEnd));
        for (int slot = 0; slot < inputEnd; slot++) {
            if (!holder.decorationSlots.contains(slot)) {
                slots.add(slot);
            }
        }
        return Set.copyOf(slots);
    }

    private void applySellGuiDecorations(Inventory inventory, SellGuiHolder holder) {
        ConfigurationSection decorations = plugin.getFoConfig().guiSection("sell-gui", "decorations");
        Object rawDecorations = plugin.getFoConfig().guiObject("sell-gui", "decorations");
        boolean emptyGuiDecorations = decorations != null && decorations.getKeys(false).isEmpty();
        if ((decorations == null && rawDecorations == null) || emptyGuiDecorations) {
            decorations = plugin.getConfig().getConfigurationSection("gui.sell.decorations");
            rawDecorations = plugin.getConfig().get("gui.sell.decorations");
        }
        if (decorations == null) {
            if (rawDecorations instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        applySellGuiDecoration(inventory, holder,
                                mapInt(map, "slot", -1),
                                mapString(map, "material", mapString(map, "item.material", "GRAY_STAINED_GLASS_PANE")),
                                mapInt(map, "amount", mapInt(map, "item.quantity", mapInt(map, "item.amount", 1))),
                                mapString(map, "name", mapString(map, "item.name", " ")),
                                firstNonEmptyList(mapStringList(map, "lore"), mapStringList(map, "item.lore")),
                                mapInt(map, "customModelData", mapInt(map, "item.customModelData", -1)),
                                firstNonEmptyList(mapStringList(map, "commandsOnClick"), mapStringList(map, "commands-on-click"), mapStringList(map, "player-commands")),
                                firstNonEmptyList(mapStringList(map, "commandsOnClickConsole"), mapStringList(map, "commands-on-click-console"), mapStringList(map, "console-commands"))
                        );
                    }
                }
            }
            return;
        }

        for (String key : decorations.getKeys(false)) {
            ConfigurationSection section = decorations.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            applySellGuiDecoration(inventory, holder,
                    section.getInt("slot", -1),
                    section.getString("material", section.getString("item.material", "GRAY_STAINED_GLASS_PANE")),
                    section.getInt("amount", section.getInt("item.quantity", section.getInt("item.amount", 1))),
                    section.getString("name", section.getString("item.name", " ")),
                    firstNonEmptyList(section.getStringList("lore"), section.getStringList("item.lore")),
                    section.contains("customModelData") ? section.getInt("customModelData") : section.getInt("item.customModelData", -1),
                    firstNonEmptyList(
                    section.getStringList("commandsOnClick"),
                    section.getStringList("commands-on-click"),
                    section.getStringList("player-commands")
                    ),
                    firstNonEmptyList(
                    section.getStringList("commandsOnClickConsole"),
                    section.getStringList("commands-on-click-console"),
                    section.getStringList("console-commands")
                    ));
        }
    }

    private void applySellGuiDecoration(Inventory inventory, SellGuiHolder holder, int slot, String materialName, int amount,
                                        String name, List<String> lore, int customModelData, List<String> playerCommands,
                                        List<String> consoleCommands) {
        if (slot < 0 || slot >= inventory.getSize()) {
            logGuiWarning("sell-gui", "Invalid decoration slot " + slot + "; skipped.");
            return;
        }

        Material material = matchMaterial(materialName, Material.GRAY_STAINED_GLASS_PANE, "sell-gui decoration slot " + slot);
        ItemStack decoration = createItem(material, name == null ? " " : name, lore);
        decoration.setAmount(Math.clamp(amount, 1, material.getMaxStackSize()));
        ItemMeta meta = decoration.getItemMeta();
        if (meta != null && customModelData >= 0) {
            meta.setCustomModelData(customModelData);
            decoration.setItemMeta(meta);
        }

        inventory.setItem(slot, decoration);
        holder.decorationSlots.add(slot);
        holder.playerCommands.put(slot, playerCommands);
        holder.consoleCommands.put(slot, consoleCommands);
    }

    public void openAdminEditor(Player player) {
        AdminEditorHolder holder = new AdminEditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8FoShop Editor"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(10, button(player, Material.REDSTONE, "Reload Plugin", List.of("Reload config, messages, GUI files, and shops."), "reload the plugin"));
        inventory.setItem(11, button(player, Material.EMERALD, "Global Sell Prices", List.of("Manage global-sell-prices.yml and /worth."), "manage global sell prices"));
        inventory.setItem(12, button(player, Material.COMPARATOR, "Settings", List.of("Edit config toggles, sounds, price display, and limits."), "open settings"));
        inventory.setItem(13, button(player, Material.CLOCK, "Rotating Shop", List.of("Edit boosted item rotation."), "open the rotating shop"));

        String stateText = plugin.getFoConfig().isFileLoggingEnabled() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled";
        inventory.setItem(14, button(player, Material.WRITABLE_BOOK, "File Logging", List.of("Current: " + stateText, "Debug file: logs/latest.log"), "toggle file logging"));

        inventory.setItem(15, button(player, Material.EXPERIENCE_BOTTLE, "Sell Boosters", List.of("Manage global, player, and team sell price boosters."), "manage sell boosters"));
        inventory.setItem(16, button(player, Material.CHEST, "Shop Section Manager", List.of("Manage sections and products."), "manage shop sections"));

        openInventory(player, inventory);
    }

    private void openSettingsEditor(Player player) {
        SettingsEditorHolder holder = new SettingsEditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 36, plugin.getFoConfig().sectionTitleSmallCaps("&8FoShop Settings"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(10, button(player, Material.CHEST, "Main Shop Rows", List.of("Current: " + plugin.getFoConfig().getMainRows(), "Click to enter 1-6 rows."), "edit the shop rows"));
        inventory.setItem(11, toggleItem(player, "Rounded Pricing", plugin.getConfig().getBoolean("sellgui.price-format.rounded-pricing", false), "Format sell values with two decimals."));
        inventory.setItem(12, toggleItem(player, "Trim Zeros", plugin.getConfig().getBoolean("sellgui.price-format.remove-trailing-zeros", false), "Remove trailing .00 from sell values."));
        inventory.setItem(13, toggleItem(player, "Abbreviate Prices", plugin.getFoConfig().isSellAbbreviateNumbers(), "Use k/m/b for large sell values."));

        inventory.setItem(14, toggleItem(player, "Sell Titles", plugin.getFoConfig().isSellTitlesEnabled(), "Show title after SellGUI sale."));
        inventory.setItem(15, toggleItem(player, "Sell Action Bar", plugin.getFoConfig().isSellActionBarEnabled(), "Show action bar after SellGUI sale."));
        inventory.setItem(16, toggleItem(player, "Transaction Log", plugin.getFoConfig().isSellTransactionLogEnabled(), "Write SellGUI sale history."));

        inventory.setItem(19, EditorItemFactory.cycle(player, plugin.getMessages(), "Receipt Mode", Integer.toString(plugin.getFoConfig().getSellReceiptType()), RECEIPT_TYPE_OPTIONS));
        inventory.setItem(20, button(player, Material.IRON_BARS, "Blocked Gamemodes", List.of("Current: " + String.join(", ", effectiveStringListSetting("sellgui.blocked-gamemodes")), "Click to edit list."), "edit blocked gamemodes"));

        inventory.setItem(FOUR_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    private void openSellBoosterEditor(Player player) {
        SellBoosterEditorHolder holder = new SellBoosterEditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 36, plugin.getFoConfig().sectionTitleSmallCaps("&8Sell Boosters"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(10, toggleItem(player, "Sell Boosters", plugin.getSellBoosterService().isEnabled(), "Apply active boosters to sell prices."));
        inventory.setItem(11, toggleItem(player, "Stack Boosters", plugin.getSellBoosterService().isStackBoosters(), "Multiply active boosters together. Disabled uses the highest booster."));
        inventory.setItem(12, toggleItem(player, "Team Boosters", plugin.getSellBoosterService().isTeamBoostersEnabled(), "Use FoTeams team boosters when FoTeams is installed."));
        inventory.setItem(13, toggleItem(player, "Stack Rotating", plugin.getSellBoosterService().isStackWithRotatingShop(), "Multiply rotating boosts with sell boosters. Disabled uses the highest multiplier."));
        inventory.setItem(14, button(player, Material.BOOK, "Active Boosters", List.of(
                "Current: " + plugin.getSellBoosterService().activeBoosters().size(),
                "Click to browse and remove active boosters."
        ), "browse active boosters"));
        inventory.setItem(15, button(player, Material.NETHER_STAR, "Start Global Booster", List.of("Prompt format: multiplier duration", "Example: 2 1h"), "start a global booster"));
        inventory.setItem(16, button(player, Material.PLAYER_HEAD, "Start Player Booster", List.of("Prompt format: player multiplier duration", "Example: Steve 2 1h"), "start a player booster"));
        inventory.setItem(20, button(player, Material.SHIELD, "Start Team Booster", List.of("Prompt format: team multiplier duration", "Requires FoTeams."), "start a team booster"));
        inventory.setItem(FOUR_ROW_BACK_SLOT, GUI_BUTTONS.back(player));
        inventory.setItem(19, toggleItem(player, "Bossbar", effectiveBooleanSetting("sell-boosters.bossbar.enabled"), "Show active sell boost bossbar to boosted players."));

        openInventory(player, inventory);
    }

    private void openActiveSellBoosters(Player player, int page) {
        List<ActiveSellBooster> boosters = plugin.getSellBoosterService().activeBoosters();
        int totalPages = Math.max(1, (int) Math.ceil(boosters.size() / (double) PAGE_SLOTS.length));
        int currentPage = Math.clamp(page, 0, totalPages - 1);
        ActiveSellBoostersHolder holder = new ActiveSellBoostersHolder(currentPage, totalPages);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.getFoConfig().sectionTitleSmallCaps("&8Active Sell Boosters"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        int start = currentPage * PAGE_SLOTS.length;
        int end = Math.min(boosters.size(), start + PAGE_SLOTS.length);
        for (int index = start; index < end; index++) {
            ActiveSellBooster booster = boosters.get(index);
            int slot = PAGE_SLOTS[index - start];
            inventory.setItem(slot, createItem(Material.EXPERIENCE_BOTTLE, "&#03fc88" + booster.scope().display() + " Booster", List.of(
                    "&#ffffffID: &#03fc88" + booster.id(),
                    "&#ffffffTarget: &#03fc88" + booster.displayOwner(),
                    "&#ffffffMultiplier: &#03fc88" + plugin.getSellBoosterService().formatMultiplier(booster.multiplier()) + "x",
                    "&#ffffffRemaining: &#03fc88" + plugin.getSellBoosterService().formatRemaining(booster),
                    "&#a7b8b0Click to remove."
            )));
            holder.slotToBooster.put(slot, booster.id());
        }

        if (boosters.isEmpty()) {
            inventory.setItem(22, createItem(Material.GRAY_DYE, "&#ff5d73No Active Boosters", List.of("&#ffffffStart one from the previous page.")));
        }

        inventory.setItem(SIX_ROW_BACK_SLOT, GUI_BUTTONS.back(player));
        setPreviousPageButton(player, inventory, 48, currentPage, totalPages);
        setNextPageButton(player, inventory, 50, currentPage, totalPages);

        openInventory(player, inventory);
    }

    private void openRotatingShopEditor(Player player) {
        RotatingEditorHolder holder = new RotatingEditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Rotating Shop"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        RotatingShopService service = plugin.getRotatingShopService();
        String status = service.isEnabled() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled";
        inventory.setItem(10, toggleItem(player, "Rotating Shop", service.isEnabled(), "Allow /rotatingshop boosted sell prices."));
        inventory.setItem(12, button(player, Material.CLOCK, "Reset Timer", List.of(
                "Current: " + DurationUtil.format(service.resetIntervalSeconds()),
                "Seconds: " + service.resetIntervalSeconds(),
                "Click to edit, e.g. 1d, 12h, 30m."
        ), "edit the reset timer"));
        inventory.setItem(13, button(player, Material.NETHER_STAR, "Reset Now", List.of(
                "Pick new boosted items now.",
                "Current: " + status,
                "Active boosts: " + service.activeEntries().size()
        ), "reset the rotation"));
        inventory.setItem(14, toggleItem(player, "Stack Sell Boosters", plugin.getSellBoosterService().isStackWithRotatingShop(), "Multiply rotating boosts with sell boosters. Disabled uses the highest multiplier."));
        inventory.setItem(11, button(player, Material.CHEST, "Section Pool", List.of(
                "Toggle which shop sections can be picked.",
                "Disabled shop sections are never picked."
        ), "configure section pool"));
        inventory.setItem(THREE_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    private void openRotatingSectionEditorList(Player player, int page) {
        openRotatingSectionEditorList(player, page, "");
    }

    private void openRotatingSectionEditorList(Player player, int page, String query) {
        Map<String, ShopSection> sectionById = new HashMap<>();
        List<String> sectionIds = plugin.getShopManager().getSectionsOrdered().stream()
                .sorted(Comparator.comparingInt(ShopSection::slot).thenComparing(ShopSection::id))
                .peek(section -> sectionById.put(section.id(), section))
                .map(ShopSection::id)
                .toList();
        List<TriStateSelectionEntry> entries = StringSelectionEntries.fromKeys(sectionIds, id -> {
            ShopSection section = sectionById.get(id);
            return section == null || section.title().isBlank() ? prettify(id) : section.title();
        }).stream()
                .map(entry -> {
                    ShopSection section = sectionById.get(entry.key());
                    if (section == null) {
                        return entry;
                    }
                    return TriStateSelectionEntry.of(entry.key(), entry.displayName(), List.of(
                            "&#ffffffRight-click: item pool",
                            "&#a7b8b0Shop section: " + (section.enabled() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled"),
                            "&#a7b8b0Rotating items: &#03fc88" + countRotatingParticipatingItems(section) + " / " + countRotatingSellableItems(section)
                    ));
                })
                .toList();
        Map<String, TriStateSelectionState> states = new HashMap<>();
        for (String sectionId : sectionIds) {
            states.put(sectionId, plugin.getRotatingShopService().sectionParticipates(sectionId)
                    ? TriStateSelectionState.ENABLED
                    : TriStateSelectionState.DISABLED);
        }

        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
                .title(ROTATING_SECTIONS_SELECTOR_TITLE)
                .entries(entries)
                .states(states)
                .cycleOrder(List.of(TriStateSelectionState.ENABLED, TriStateSelectionState.DISABLED))
                .enabledLabel("Enabled")
                .disabledLabel("Disabled")
                .clickHint("Left-click: toggle section pool.")
                .emptyTitle("No Sections")
                .emptyLore(List.of("&#ffffffNo matching shop sections."))
                .buttons(GUI_BUTTONS)
                .page(page)
                .filter(query)
                .showBack(true)
                .showSearch(true)
                .build();
        TriStateSelectionMenus.open(player, request);
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    private void openRotatingSectionDetailEditor(Player player, String sectionId, int sectionPage) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openRotatingSectionEditorList(player, sectionPage);
            return;
        }

        RotatingSectionDetailHolder holder = new RotatingSectionDetailHolder(sectionId, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Rotating: " + section.id()));
        holder.setInventory(inventory);

        fillBackground(inventory);

        long activeItems = countRotatingParticipatingItems(section);
        long totalItems = countRotatingSellableItems(section);
        inventory.setItem(10, toggleItem(player, "Section Pool", plugin.getRotatingShopService().sectionParticipates(section.id()),
                "Allow this section in rotating shop picks."));
        inventory.setItem(12, createItem(Material.CHEST, "&#03fc88Item Pool", List.of(
                "&#ffffffToggle individual sellable items.",
                "&#a7b8b0Active: &#03fc88" + activeItems + " / " + totalItems
        )));
        inventory.setItem(THREE_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    private void openRotatingItemEditorList(Player player, String sectionId, int page, int sectionPage) {
        openRotatingItemEditorList(player, sectionId, page, sectionPage, "");
    }

    private void openRotatingItemEditorList(Player player, String sectionId, int page, int sectionPage, String query) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openRotatingSectionEditorList(player, sectionPage);
            return;
        }

        rotatingItemSectionPages.put(player.getUniqueId(), sectionPage);
        List<ShopItem> items = rotatingSellableItems(section, "");
        List<String> itemIds = items.stream().map(ShopItem::id).toList();
        Map<String, ShopItem> itemById = new HashMap<>();
        for (ShopItem item : items) {
            itemById.put(item.id(), item);
        }
        List<TriStateSelectionEntry> entries = StringSelectionEntries.fromKeys(itemIds, id -> {
            ShopItem item = itemById.get(id);
            if (item == null) {
                return prettify(id);
            }
            return item.displayName() == null || item.displayName().isBlank()
                    ? prettify(item.material().name())
                    : item.displayName();
        }).stream()
                .map(entry -> {
                    ShopItem item = itemById.get(entry.key());
                    if (item == null) {
                        return entry;
                    }
                    return TriStateSelectionEntry.of(entry.key(), entry.displayName(), List.of(
                            "&#a7b8b0ID: &#03fc88" + item.id(),
                            "&#a7b8b0Sell: &#03fc88" + formatPrice(item.sellPrice()),
                            "&#a7b8b0Slot: &#03fc88" + item.slot()
                    ));
                })
                .toList();
        Map<String, TriStateSelectionState> states = new HashMap<>();
        for (ShopItem item : items) {
            states.put(item.id(), plugin.getRotatingShopService().itemParticipates(section.id(), item.id())
                    ? TriStateSelectionState.ENABLED
                    : TriStateSelectionState.DISABLED);
        }

        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
                .title(ROTATING_ITEMS_SELECTOR_TITLE_PREFIX + section.id())
                .entries(entries)
                .states(states)
                .cycleOrder(List.of(TriStateSelectionState.ENABLED, TriStateSelectionState.DISABLED))
                .enabledLabel("Enabled")
                .disabledLabel("Disabled")
                .clickHint("Click to toggle item pool.")
                .emptyTitle("No Sellable Items")
                .emptyLore(List.of("&#ffffffNo matching sellable items in this section."))
                .buttons(GUI_BUTTONS)
                .page(page)
                .filter(query)
                .showBack(true)
                .showSearch(true)
                .build();
        TriStateSelectionMenus.open(player, request);
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    private ItemStack toggleItem(Player player, String label, boolean enabled, String description) {
        return EditorItemFactory.button(player, enabled ? Material.LIME_DYE : Material.RED_DYE,
                enabled ? FoStyle.GOOD : FoStyle.BAD, label,
                List.of(description, "State: " + (enabled ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF")),
                "toggle");
    }

    private void openSectionEditorList(Player player, int page) {
        openSectionEditorList(player, page, "");
    }

    private void openSectionEditorList(Player player, int page, String query) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ShopSection> sections = plugin.getShopManager().getSectionsOrdered().stream()
                .filter(section -> search.isBlank()
                        || section.id().toLowerCase(Locale.ROOT).contains(search)
                        || section.title().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparingInt(ShopSection::slot).thenComparing(ShopSection::id))
                .toList();
        List<EntryBrowserRequest.Entry> entries = sections.stream()
                .map(this::sectionBrowserEntry)
                .toList();
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Section Editor")
                .entries(entries)
                .page(page)
                .filter(search)
                .buttons(GUI_BUTTONS)
                .showBack(true)
                .addButton(button(player, Material.ANVIL, "New Section", List.of(
                        "&#ffffffCreate a new shop section.",
                        "&#a7b8b0Click then type section id in chat."
                ), "create a section"))
                .build());
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    private EntryBrowserRequest.Entry sectionBrowserEntry(ShopSection section) {
        ItemStack sectionIcon = section.iconItem() == null ? new ItemStack(section.icon()) : section.iconItem().clone();
        ItemMeta sectionMeta = sectionIcon.getItemMeta();
        if (sectionMeta != null) {
            if (!sectionMeta.hasDisplayName()) {
                sectionMeta.setDisplayName(Text.colorize("&#03fc88" + section.id()));
            } else {
                sectionMeta.setDisplayName(Text.colorize(sectionMeta.getDisplayName()));
            }
            String status = section.enabled() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled";
            sectionMeta.setLore(List.of(
                    Text.colorize("&#ffffffOpen section editor"),
                    Text.colorize("&#a7b8b0Status: " + status),
                    Text.colorize("&#a7b8b0Items: &#03fc88" + section.items().size()),
                    Text.colorize("&#a7b8b0Description lines: &#03fc88" + section.description().size())
            ));
            sectionMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            sectionIcon.setItemMeta(sectionMeta);
        }
        return EntryBrowserRequest.Entry.of(section.id(), sectionIcon);
    }

    private void openSectionDetailEditor(Player player, String sectionId, int sectionPage) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openSectionEditorList(player, sectionPage);
            return;
        }

        SectionDetailHolder holder = new SectionDetailHolder(sectionId, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Section: " + section.id()));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(10, button(player, Material.CHEST, "Products", List.of("Open product browser."), "browse products"));
        inventory.setItem(11, createSectionIconCopyItem(section));
        inventory.setItem(12, button(player, Material.HOPPER, "Edit Size", List.of("Current: " + section.size(), "Click to enter 9-54 divisible by 9."), "edit the size"));
        inventory.setItem(13, button(player, Material.WRITABLE_BOOK, "Edit Description", List.of("Lines: " + section.description().size(), "Use | between lines."), "edit the description"));
        inventory.setItem(14, button(player, Material.COMPASS, "Main Menu Slot", List.of("Current: " + section.slot(), "Click to lock this section to a /foshop slot.", "Visible range now: 0-" + (plugin.getFoConfig().getMainSize() - 1)), "edit the menu slot"));
        inventory.setItem(15, toggleItem(player, "Section Enabled", section.enabled(), "Show this section in /shop."));
        inventory.setItem(16, button(player, Material.LAVA_BUCKET, FoStyle.BAD, "Remove Section", List.of("Deletes this section and all products in it."), "open confirmation"));
        inventory.setItem(THREE_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    private ItemStack createSectionIconCopyItem(ShopSection section) {
        ItemStack icon = section.iconItem() == null ? new ItemStack(section.icon()) : section.iconItem();
        icon.setAmount(1);

        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }

        if (meta.hasDisplayName()) {
            meta.setDisplayName(Text.colorize(meta.getDisplayName()));
        } else {
            meta.setDisplayName(Text.colorize("&#03fc88Copy Icon"));
        }

        List<String> lore = new ArrayList<>();
        if (meta.hasLore() && meta.getLore() != null) {
            lore.addAll(meta.getLore().stream().map(Text::colorize).toList());
            lore.add(Text.colorize(""));
        }
        lore.add(Text.colorize("&#ffffffHold item on cursor."));
        lore.add(Text.colorize("&#ffffffClick to copy as section icon."));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        return icon;
    }

    private void openItemListEditor(Player player, String sectionId, int page, int sectionPage) {
        openItemListEditor(player, sectionId, page, sectionPage, "");
    }

    private void openItemListEditor(Player player, String sectionId, int page, int sectionPage, String query) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openSectionEditorList(player, sectionPage);
            return;
        }

        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ShopItem> items = section.items().stream()
                .filter(item -> search.isBlank()
                        || item.id().toLowerCase(Locale.ROOT).contains(search)
                        || item.material().name().toLowerCase(Locale.ROOT).contains(search)
                        || item.type().name().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparingInt(ShopItem::slot).thenComparing(ShopItem::id))
                .toList();
        sectionItemBrowserPages.put(player.getUniqueId(), sectionPage);
        List<EntryBrowserRequest.Entry> entries = items.stream()
                .map(this::itemBrowserEntry)
                .toList();
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Items: " + sectionId)
                .entries(entries)
                .page(page)
                .filter(search)
                .buttons(GUI_BUTTONS)
                .showBack(true)
                .addButton(button(player, Material.ANVIL, "Add Product From Cursor", List.of(
                        "&#ffffffHold an item on your cursor.",
                        "&#ffffffClick to add it as a product.",
                        "&#a7b8b0Prices default to disabled (-1)."
                ), "add a product"))
                .build());
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    private EntryBrowserRequest.Entry itemBrowserEntry(ShopItem item) {
        return EntryBrowserRequest.Entry.of(item.id(), createItem(
                item.material(),
                "&#03fc88" + item.id(),
                List.of(
                        "&#ffffffMaterial: &#03fc88" + item.material().name(),
                        "&#ffffffAmount: &#03fc88" + item.amount(),
                        "&#ffffffStack cap: &#03fc88" + item.effectiveStackSize(),
                        "&#ffffffGUI Slot: &#03fc88" + item.slot(),
                        "&#ffffffBuy: &#03fc88" + formatPrice(item.buyPrice()),
                        "&#ffffffSell: &#03fc88" + formatPrice(item.sellPrice())
                )));
    }

    private void openItemEditor(Player player, String sectionId, String itemId, int page, int sectionPage) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openSectionEditorList(player, sectionPage);
            return;
        }

        ShopItem item = section.items().stream().filter(current -> current.id().equalsIgnoreCase(itemId)).findFirst().orElse(null);
        if (item == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            openItemListEditor(player, sectionId, page, sectionPage);
            return;
        }

        ItemEditorHolder holder = new ItemEditorHolder(sectionId, item.id(), page, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 36, plugin.getFoConfig().sectionTitleSmallCaps("&8Edit: " + item.id()));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(13, createItem(item.material(), "&#03fc88" + item.id(), List.of(
                "&#ffffffCurrent product preview.",
                "&#ffffffType: &#03fc88" + item.type().name().toLowerCase(Locale.ROOT),
                "&#ffffffPage: &#03fc88" + (item.page() + 1),
                "&#ffffffAmount: &#03fc88" + item.amount(),
                "&#ffffffStack cap: &#03fc88" + item.effectiveStackSize(),
                "&#ffffffSlot: &#03fc88" + item.slot()
        )));

        inventory.setItem(10, button(player, Material.NAME_TAG, "Edit Product ID", List.of("Current: " + item.id(), "Chat input, type cancel to abort."), "edit the product ID"));
        inventory.setItem(11, button(player, item.material(), "Edit Material", List.of("Current: " + item.material().name(), "Hold cursor item + click to copy material/name/lore/model/enchants."), "edit the material"));
        inventory.setItem(12, button(player, Material.CHEST, "Edit Amount", List.of("Current: " + item.amount(), "Type a number 1-" + item.effectiveStackSize()), "edit the amount"));

        inventory.setItem(14, button(player, Material.ITEM_FRAME, "Edit GUI Slot", List.of("Current: " + item.slot(), "Type a slot index (0-" + (section.size() - 1) + ")."), "edit the GUI slot"));
        inventory.setItem(15, button(player, Material.EMERALD, "Edit Buy Price", List.of("Current: " + formatPrice(item.buyPrice()), "Type number or -1/disable."), "edit the buy price"));
        inventory.setItem(16, button(player, Material.GOLD_INGOT, "Edit Sell Price", List.of("Current: " + formatPrice(item.sellPrice()), "Type number or -1/disable."), "edit the sell price"));

        inventory.setItem(19, EditorItemFactory.cycle(player, plugin.getMessages(), "Edit Type", editorItemTypeValue(item.type()), ITEM_TYPE_OPTIONS));
        inventory.setItem(20, button(player, Material.MAP, "Edit Page", List.of("Current: " + (item.page() + 1), "Type a page number, starting at 1."), "edit the page"));
        inventory.setItem(21, button(player, Material.REPEATING_COMMAND_BLOCK, "Edit Action Data", List.of("Permission node / commands.", "Commands use | between lines."), "edit action data"));
        inventory.setItem(22, button(player, Material.HOPPER, "Edit Stack Cap", List.of("Current: " + item.effectiveStackSize(), "Type a number 1-" + Math.min(64, item.material().getMaxStackSize())), "edit the stack cap"));
        inventory.setItem(23, button(player, Material.BARREL, "Edit Stock", List.of("Current: " + (item.stock() == null ? "unlimited" : item.stock()), "Type amount or -1 to disable."), "edit the stock"));
        inventory.setItem(24, button(player, Material.CLOCK, "Edit Buy Limit", List.of("Current: " + (item.buyLimit() == null ? "unlimited" : item.buyLimit()), "Type amount or -1 to disable."), "edit the buy limit"));
        inventory.setItem(25, button(player, Material.LAVA_BUCKET, FoStyle.BAD, "Remove Product", List.of("Deletes this product from section."), "open confirmation"));
        inventory.setItem(FOUR_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(top) && isBackButton(event.getCurrentItem())) {
            markBackNavigation(player);
        }

        if (holder instanceof FoHolder && !(holder instanceof SellGuiHolder) && shouldCancelReadOnlyViewClick(event, top)) {
            event.setCancelled(true);
            return;
        }

        if (holder instanceof TriStateSelectionHolder selectionHolder) {
            handleRotatingSelectionClick(event, player, top, selectionHolder);
            return;
        }

        if (holder instanceof EntryBrowserHolder entryBrowserHolder) {
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
            handleEntryBrowserClick(event, player, entryBrowserHolder);
            return;
        }

        if (holder instanceof MainMenuHolder menuHolder) {
            handleMainMenuClick(event, player, top, menuHolder);
            return;
        }

        if (holder instanceof SectionHolder sectionHolder) {
            handleSectionClick(event, player, top, sectionHolder);
            return;
        }

        if (holder instanceof RotatingShopHolder rotatingShopHolder) {
            handleRotatingShopClick(event, player, top, rotatingShopHolder);
            return;
        }

        if (holder instanceof SellGuiHolder sellGuiHolder) {
            handleSellClick(event, player, top, sellGuiHolder);
            return;
        }

        if (holder instanceof AdminEditorHolder) {
            handleAdminEditorClick(event, player);
            return;
        }

        if (holder instanceof SettingsEditorHolder) {
            handleSettingsEditorClick(event, player);
            return;
        }

        if (holder instanceof RotatingEditorHolder) {
            handleRotatingEditorClick(event, player);
            return;
        }

        if (holder instanceof SellBoosterEditorHolder) {
            handleSellBoosterEditorClick(event, player);
            return;
        }

        if (holder instanceof ActiveSellBoostersHolder activeSellBoostersHolder) {
            handleActiveSellBoostersClick(event, player, top, activeSellBoostersHolder);
            return;
        }

        if (holder instanceof GlobalSellPriceEditorHolder) {
            handleGlobalSellPriceEditorClick(event, player);
            return;
        }

        if (holder instanceof GlobalSellPriceListHolder globalSellPriceListHolder) {
            handleGlobalSellPriceListClick(event, player, top, globalSellPriceListHolder);
            return;
        }

        if (holder instanceof RotatingSectionDetailHolder rotatingSectionDetailHolder) {
            handleRotatingSectionDetailClick(event, player, top, rotatingSectionDetailHolder);
            return;
        }

        if (holder instanceof SectionDetailHolder sectionDetailHolder) {
            handleSectionDetailClick(event, player, top, sectionDetailHolder);
            return;
        }

        if (holder instanceof ItemEditorHolder itemEditorHolder) {
            handleItemEditorClick(event, player, top, itemEditorHolder);
            return;
        }

        if (holder instanceof ConfirmDeleteItemHolder confirmDeleteItemHolder) {
            handleConfirmDeleteItemClick(event, player, top, confirmDeleteItemHolder);
            return;
        }

        if (holder instanceof ConfirmDeleteSectionHolder confirmDeleteSectionHolder) {
            handleConfirmDeleteSectionClick(event, player, top, confirmDeleteSectionHolder);
            return;
        }

        if (holder instanceof ConfirmRemoveSellBoosterHolder confirmRemoveSellBoosterHolder) {
            handleConfirmRemoveSellBoosterClick(event, player, top, confirmRemoveSellBoosterHolder);
            return;
        }

        if (holder instanceof BuyItemHolder buyItemHolder) {
            handleBuyItemGuiClick(event, player, top, buyItemHolder);
            return;
        }

        if (holder instanceof BuyMoreHolder buyMoreHolder) {
            handleBuyMoreGuiClick(event, player, top, buyMoreHolder);
        }
    }

    private boolean shouldCancelReadOnlyViewClick(InventoryClickEvent event, Inventory top) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            return true;
        }
        return event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && (event.getClickedInventory() == null || !event.getClickedInventory().equals(top));
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player, Inventory top, MainMenuHolder holder) {
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(top)) {
            event.setCancelled(true);

            String sectionId = holder.slotToSection.get(event.getSlot());
            if (sectionId == null) {
                return;
            }
            plugin.getShopManager().getSection(sectionId)
                    .filter(ShopSection::enabled)
                    .ifPresent(section -> {
                        plugin.getSounds().play(player, "gui.select");
                        openShopSection(player, section);
                    });
        }
    }

    private void handleSectionClick(InventoryClickEvent event, Player player, Inventory top, SectionHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getSlot();
        ShopSection section = plugin.getShopManager().getSection(holder.sectionId()).orElse(null);
        if (section == null) {
            return;
        }

        if (slot == holder.backSlot) {
            openMainShop(player);
            return;
        }

        if (section.totalPages() > 1 && holder.page() > 0 && slot == holder.previousSlot) {
            plugin.getSounds().play(player, "gui.page-previous");
            openShopSection(player, section, holder.page() - 1);
            return;
        }

        if (section.totalPages() > 1 && holder.page() < section.totalPages() - 1 && slot == holder.nextSlot) {
            plugin.getSounds().play(player, "gui.page-next");
            openShopSection(player, section, holder.page() + 1);
            return;
        }

        ShopItem shopItem = section.itemsBySlot(holder.page()).get(slot);
        if (shopItem == null) {
            return;
        }

        if (shopItem.type() == ShopItemType.DUMMY) {
            return;
        }

        if (!shopItem.canBuy()) {
            plugin.getMessages().send(player, "buy-disabled");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
            plugin.getSounds().play(player, "gui.select");
            openBuyItemGui(player, holder.sectionId(), shopItem.id(), shopItem.amount());
        }
    }

    private void handleRotatingShopClick(InventoryClickEvent event, Player player, Inventory top, RotatingShopHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
    }

    private void handleAdminEditorClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);

        switch (event.getSlot()) {
            case 10 -> {
                ShopManager.ReloadResult result = plugin.reloadAll();
                ReloadFeedback.send(plugin, player, result);
                if (result.issues().isEmpty()) {
                    plugin.getAdminSounds().reload(player);
                } else {
                    plugin.getAdminSounds().reloadError(player);
                }
                openAdminEditor(player);
            }
            case 11 -> openGlobalSellPriceEditor(player);
            case 12 -> openSettingsEditor(player);
            case 13 -> openRotatingShopEditor(player);
            case 14 -> toggleBooleanSetting(player, "file-logging", () -> openAdminEditor(player));
            case 15 -> openSellBoosterEditor(player);
            case 16 -> openSectionEditorList(player, 0);
            default -> {
            }
        }
    }

    private void handleSettingsEditorClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        Runnable reopenSettings = () -> openSettingsEditor(player);
        switch (event.getSlot()) {
            case 10 -> startPrompt(player, new PromptEdit(PromptType.MAIN_ROWS, null, null, null, 0, 0),
                    "&#a7b8b0Expected: main /foshop rows from &#03fc881-6&#a7b8b0. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 11 -> toggleBooleanSetting(player, "sellgui.price-format.rounded-pricing", reopenSettings);
            case 12 -> toggleBooleanSetting(player, "sellgui.price-format.remove-trailing-zeros", reopenSettings);
            case 13 -> toggleBooleanSetting(player, "sellgui.price-format.abbreviate-numbers", reopenSettings);
            case 14 -> toggleBooleanSetting(player, "sellgui.titles.enabled", reopenSettings);
            case 15 -> toggleBooleanSetting(player, "sellgui.action-bar.enabled", reopenSettings);
            case 16 -> toggleBooleanSetting(player, "sellgui.transaction-log.enabled", reopenSettings);
            case 19 -> cycleReceiptType(player, reopenSettings);
            case 20 -> startPrompt(player, new PromptEdit(PromptType.CONFIG_GAMEMODE_LIST, "sellgui.blocked-gamemodes", null, null, 0, 0),
                    "&#a7b8b0Expected: gamemodes separated by |, comma, or space. Type &#ff5d73clear &#a7b8b0to empty, or &#ff5d73cancel &#a7b8b0to abort.");
            case FOUR_ROW_BACK_SLOT -> openAdminEditor(player);
            default -> {
            }
        }
    }

    private void toggleBooleanSetting(Player player, String path, Runnable reopen) {
        boolean next = !effectiveBooleanSetting(path);
        saveConfigValue(player, path, next, reopen);
    }

    private void cycleReceiptType(Player player, Runnable reopen) {
        String current = Integer.toString(plugin.getFoConfig().getSellReceiptType());
        String next = CycleOptions.nextValue(current, RECEIPT_TYPE_OPTIONS);
        saveConfigValue(player, "sellgui.receipts.type", Integer.parseInt(next), reopen);
    }

    private void cycleItemType(Player player, ItemEditorHolder holder) {
        ShopItem item = findItem(holder.sectionId, holder.itemId);
        if (item == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            openItemListEditor(player, holder.sectionId, holder.page, holder.sectionPage);
            return;
        }

        String current = editorItemTypeValue(item.type());
        String next = CycleOptions.nextValue(current, ITEM_TYPE_OPTIONS);
        if (updateItemField(holder.sectionId, holder.itemId, "type", next, player)) {
            plugin.getMessages().send(player, "editor-saved");
            plugin.getEditorSounds().cycle(player);
            openItemEditor(player, holder.sectionId, holder.itemId, holder.page, holder.sectionPage);
        }
    }

    private String editorItemTypeValue(ShopItemType type) {
        return type == ShopItemType.ENCHANTMENT ? "item" : type.name().toLowerCase(Locale.ROOT);
    }

    private boolean effectiveBooleanSetting(String path) {
        return switch (path) {
            case "global-sell-prices.enabled" -> plugin.getFoConfig().isGlobalSellPricesEnabled();
            case "global-sell-prices.worth-lore.enabled" -> plugin.getFoConfig().isWorthLoreConfiguredEnabled();
            case "file-logging" -> plugin.getFoConfig().isFileLoggingEnabled();
            case "sellgui.titles.enabled" -> plugin.getFoConfig().isSellTitlesEnabled();
            case "sellgui.action-bar.enabled" -> plugin.getFoConfig().isSellActionBarEnabled();
            case "sellgui.transaction-log.enabled" -> plugin.getFoConfig().isSellTransactionLogEnabled();
            case "sellgui.price-format.rounded-pricing" -> plugin.getConfig().getBoolean(path, plugin.getConfig().getBoolean("gui.sell.price-format.rounded-pricing", false));
            case "sellgui.price-format.remove-trailing-zeros" -> plugin.getConfig().getBoolean(path, plugin.getConfig().getBoolean("gui.sell.price-format.remove-trailing-zeros", false));
            case "sellgui.price-format.abbreviate-numbers" -> plugin.getFoConfig().isSellAbbreviateNumbers();
            case "sell-boosters.enabled" -> plugin.getSellBoosterService().isEnabled();
            case "sell-boosters.stack-boosters" -> plugin.getSellBoosterService().isStackBoosters();
            case "sell-boosters.stack-with-rotating-shop" -> plugin.getSellBoosterService().isStackWithRotatingShop();
            case "sell-boosters.team-boosters.enabled" -> plugin.getSellBoosterService().isTeamBoostersEnabled();
            case "sell-boosters.bossbar.enabled" -> plugin.getConfig().getBoolean(path, true);
            default -> plugin.getConfig().getBoolean(path, false);
        };
    }

    private double effectiveDoubleSetting(String path, double fallback) {
        return plugin.getConfig().getDouble(path, fallback);
    }

    private String effectiveStringSetting(String path) {
        return plugin.getConfig().getString(path, "");
    }

    private void handleRotatingEditorClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case 10 -> toggleBooleanSetting(player, "rotating-shop.enabled", () -> openRotatingShopEditor(player));
            case 12 -> startPrompt(player,
                    new PromptEdit(PromptType.ROTATING_RESET_SECONDS, "rotating-shop.reset-interval-seconds", null, null, 0, 0),
                    "&#a7b8b0Expected: duration like &#03fc881d&#a7b8b0, &#03fc8812h&#a7b8b0, &#03fc8830m&#a7b8b0, or seconds. Minimum 60s. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 13 -> {
                plugin.getRotatingShopService().resetNow();
                plugin.getMessages().send(player, "rotating-shop-reset");
                plugin.getSounds().play(player, "shop.rotating-reset");
                openRotatingShopEditor(player);
            }
            case 14 -> toggleBooleanSetting(player, "sell-boosters.stack-with-rotating-shop", () -> openRotatingShopEditor(player));
            case 11 -> openRotatingSectionEditorList(player, 0);
            case THREE_ROW_BACK_SLOT -> openAdminEditor(player);
            default -> {
            }
        }
    }

    private void handleSellBoosterEditorClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case 10 -> toggleBooleanSetting(player, "sell-boosters.enabled", () -> openSellBoosterEditor(player));
            case 11 -> toggleBooleanSetting(player, "sell-boosters.stack-boosters", () -> openSellBoosterEditor(player));
            case 12 -> toggleBooleanSetting(player, "sell-boosters.team-boosters.enabled", () -> openSellBoosterEditor(player));
            case 13 -> toggleBooleanSetting(player, "sell-boosters.stack-with-rotating-shop", () -> openSellBoosterEditor(player));
            case 14 -> openActiveSellBoosters(player, 0);
            case 15 -> startPrompt(player,
                    new PromptEdit(PromptType.SELL_BOOSTER_START, SellBoosterScope.GLOBAL.key(), null, null, 0, 0),
                    "&#a7b8b0Expected: multiplier and duration, like &#03fc882 1h&#a7b8b0. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 16 -> startPrompt(player,
                    new PromptEdit(PromptType.SELL_BOOSTER_START, SellBoosterScope.PLAYER.key(), null, null, 0, 0),
                    "&#a7b8b0Expected: player, multiplier, duration, like &#03fc88Steve 2 1h&#a7b8b0. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 20 -> startPrompt(player,
                    new PromptEdit(PromptType.SELL_BOOSTER_START, SellBoosterScope.TEAM.key(), null, null, 0, 0),
                    "&#a7b8b0Expected: FoTeams team, multiplier, duration, like &#03fc88Knights 2 1h&#a7b8b0. Type &#ff5d73cancel &#a7b8b0to abort.");
            case FOUR_ROW_BACK_SLOT -> openAdminEditor(player);
            case 19 -> toggleBooleanSetting(player, "sell-boosters.bossbar.enabled", () -> openSellBoosterEditor(player));
            default -> {
            }
        }
    }

    private void handleActiveSellBoostersClick(InventoryClickEvent event, Player player, Inventory top, ActiveSellBoostersHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case SIX_ROW_BACK_SLOT -> {
                openSellBoosterEditor(player);
                return;
            }
            case 48 -> {
                if (holder.page > 0) {
                    plugin.getEditorSounds().previousPage(player);
                    openActiveSellBoosters(player, holder.page - 1);
                }
                return;
            }
            case 50 -> {
                if (holder.page < holder.totalPages - 1) {
                    plugin.getEditorSounds().nextPage(player);
                    openActiveSellBoosters(player, holder.page + 1);
                }
                return;
            }
            default -> {
            }
        }

        String boosterId = holder.slotToBooster.get(event.getSlot());
        if (boosterId != null) {
            openConfirmRemoveSellBooster(player, boosterId, holder.page);
        }
    }

    private void handleGlobalSellPriceEditorClick(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case 10 -> toggleBooleanSetting(player, "global-sell-prices.enabled", () -> openGlobalSellPriceEditor(player));
            case 11 -> toggleBooleanSetting(player, "global-sell-prices.worth-lore.enabled", () -> openGlobalSellPriceEditor(player));
            case 12 -> openGlobalSellPriceList(player, true, 0, "", WorthSort.NAME);
            case THREE_ROW_BACK_SLOT -> openAdminEditor(player);
            default -> {
            }
        }
    }

    private void handleGlobalSellPriceListClick(InventoryClickEvent event, Player player, Inventory top, GlobalSellPriceListHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case 45 -> {
                if (holder.page > 0) {
                    pageSound(player, holder.editor, false);
                    openGlobalSellPriceList(player, holder.editor, holder.page - 1, holder.query, holder.sort);
                }
                return;
            }
            case 47 -> {
                String next = CycleOptions.nextValue(holder.sort.name(), WORTH_SORT_OPTIONS);
                if (holder.editor) {
                    plugin.getEditorSounds().cycle(player);
                } else {
                    plugin.getSounds().play(player, "gui.sort");
                }
                openGlobalSellPriceList(player, holder.editor, 0, holder.query, sortFromValue(next));
                return;
            }
            case SIX_ROW_BACK_SLOT -> {
                if (holder.editor) {
                    openGlobalSellPriceEditor(player);
                }
                return;
            }
            case 51 -> {
                searchSound(player, holder.editor, false);
                startPrompt(player,
                        new PromptEdit(PromptType.GLOBAL_PRICE_SEARCH, holder.editor ? "editor" : "worth", null, null, holder.page, holder.sort.ordinal()),
                        "&#a7b8b0Expected: material search text. Type &#ff5d73cancel &#a7b8b0to abort.");
                return;
            }
            case 52 -> {
                if (!holder.query.isBlank()) {
                    searchSound(player, holder.editor, true);
                    openGlobalSellPriceList(player, holder.editor, 0, "", holder.sort);
                }
                return;
            }
            case 53 -> {
                if (holder.page < holder.totalPages - 1) {
                    pageSound(player, holder.editor, true);
                    openGlobalSellPriceList(player, holder.editor, holder.page + 1, holder.query, holder.sort);
                }
                return;
            }
            default -> {
            }
        }

        GlobalPriceListEntry globalEntry = holder.slotToEntry.get(event.getSlot());
        if (globalEntry == null || !holder.editor) {
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT) {
            boolean current = globalEntry.enabled();
            boolean saved;
            if (globalEntry.isPotion()) {
                saved = plugin.getGlobalSellPriceService().setPotionEnabled(globalEntry.potionType(), !current);
            } else if (globalEntry.isEnchantment()) {
                saved = plugin.getGlobalSellPriceService().setEnchantmentEnabled(globalEntry.enchantmentKey(), globalEntry.enchantmentLevel(), !current);
            } else {
                saved = plugin.getGlobalSellPriceService().setEnabled(globalEntry.material(), !current);
            }
            if (saved) {
                plugin.getRotatingShopService().reload();
                plugin.getMessages().send(player, "editor-saved");
                plugin.getEditorSounds().toggle(player, !current);
            } else {
                plugin.getEditorSounds().error(player);
            }
            openGlobalSellPriceList(player, true, holder.page, holder.query, holder.sort);
            return;
        }

        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            boolean current = globalEntry.rotatingShop();
            boolean saved;
            if (globalEntry.isPotion()) {
                saved = plugin.getGlobalSellPriceService().setPotionRotatingShop(globalEntry.potionType(), !current);
            } else if (globalEntry.isEnchantment()) {
                saved = plugin.getGlobalSellPriceService().setEnchantmentRotatingShop(globalEntry.enchantmentKey(), globalEntry.enchantmentLevel(), !current);
            } else {
                saved = plugin.getGlobalSellPriceService().setRotatingShop(globalEntry.material(), !current);
            }
            if (saved) {
                plugin.getRotatingShopService().reload();
                plugin.getMessages().send(player, "editor-saved");
                plugin.getEditorSounds().toggle(player, !current);
            } else {
                plugin.getEditorSounds().error(player);
            }
            openGlobalSellPriceList(player, true, holder.page, holder.query, holder.sort);
            return;
        }

        startPrompt(player,
                new PromptEdit(PromptType.GLOBAL_PRICE, holder.query, null, globalEntry.itemId(), holder.page, holder.sort.ordinal()),
                "&#a7b8b0Expected: sell price number, or &#03fc880&#a7b8b0 to disable. Type &#ff5d73cancel &#a7b8b0to abort.");
    }

    private void handleRotatingSelectionClick(InventoryClickEvent event, Player player, Inventory top, TriStateSelectionHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        TriStateSelectionRequest request = holder.request();
        String title = request.title();
        boolean sectionsSelector = ROTATING_SECTIONS_SELECTOR_TITLE.equals(title);
        boolean itemsSelector = title.startsWith(ROTATING_ITEMS_SELECTOR_TITLE_PREFIX);
        if (!sectionsSelector && !itemsSelector) {
            return;
        }

        if (sectionsSelector && event.getClick().isRightClick()) {
            TriStateSelectionEntry entry = holder.entryAt(event.getSlot());
            if (entry != null) {
                openRotatingSectionDetailEditor(player, entry.key(), request.page());
                return;
            }
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(event.getSlot(), holder);
        switch (click.action()) {
            case PREVIOUS_PAGE, NEXT_PAGE, CLEAR_SEARCH -> {
                if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE) {
                    plugin.getEditorSounds().previousPage(player);
                } else if (click.action() == TriStateSelectionActionType.NEXT_PAGE) {
                    plugin.getEditorSounds().nextPage(player);
                } else {
                    plugin.getEditorSounds().clearSearch(player);
                }
                TriStateSelectionMenus.open(player, click.nextRequest());
                screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
            }
            case SEARCH -> {
                plugin.getEditorSounds().search(player);
                if (sectionsSelector) {
                    startPrompt(player,
                            new PromptEdit(PromptType.ROTATING_SECTION_SEARCH, null, null, null, request.page(), 0),
                            "&#a7b8b0Expected: section search text. Type &#ff5d73cancel &#a7b8b0to abort.");
                } else {
                    String sectionId = rotatingItemSectionId(request);
                    int sectionPage = rotatingItemSectionPages.getOrDefault(player.getUniqueId(), 0);
                    startPrompt(player,
                            new PromptEdit(PromptType.ROTATING_ITEM_SEARCH, null, sectionId, null, request.page(), sectionPage),
                            "&#a7b8b0Expected: item search text. Type &#ff5d73cancel &#a7b8b0to abort.");
                }
            }
            case BACK -> {
                if (sectionsSelector) {
                    openRotatingShopEditor(player);
                } else {
                    String sectionId = rotatingItemSectionId(request);
                    int sectionPage = rotatingItemSectionPages.getOrDefault(player.getUniqueId(), 0);
                    openRotatingSectionDetailEditor(player, sectionId, sectionPage);
                }
            }
            case TOGGLE -> {
                boolean next = click.newState() == TriStateSelectionState.ENABLED;
                if (sectionsSelector) {
                    saveConfigValue(player, "rotating-shop.sections." + click.key(), next,
                            () -> openRotatingSectionEditorList(player, click.page(), click.filter()));
                } else {
                    String sectionId = rotatingItemSectionId(request);
                    int sectionPage = rotatingItemSectionPages.getOrDefault(player.getUniqueId(), 0);
                    saveConfigValue(player, "rotating-shop.items." + sectionId + "." + click.key(), next,
                            () -> openRotatingItemEditorList(player, sectionId, click.page(), sectionPage, click.filter()));
                }
            }
            case NONE -> {
            }
        }
    }

    private String rotatingItemSectionId(TriStateSelectionRequest request) {
        String title = request.title();
        if (!title.startsWith(ROTATING_ITEMS_SELECTOR_TITLE_PREFIX)) {
            return "";
        }
        return title.substring(ROTATING_ITEMS_SELECTOR_TITLE_PREFIX.length());
    }

    private void handleRotatingSectionDetailClick(InventoryClickEvent event, Player player, Inventory top, RotatingSectionDetailHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case 10 -> {
                boolean next = !plugin.getRotatingShopService().sectionParticipates(holder.sectionId);
                saveConfigValue(player, "rotating-shop.sections." + holder.sectionId, next,
                        () -> openRotatingSectionDetailEditor(player, holder.sectionId, holder.sectionPage));
            }
            case 12 -> openRotatingItemEditorList(player, holder.sectionId, 0, holder.sectionPage);
            case THREE_ROW_BACK_SLOT -> openRotatingSectionEditorList(player, holder.sectionPage);
            default -> {
            }
        }
    }

    private List<String> effectiveStringListSetting(String path) {
        List<String> values = plugin.getConfig().getStringList(path);
        if (!values.isEmpty() || plugin.getConfig().contains(path)) {
            return values;
        }
        if ("sellgui.blocked-gamemodes".equals(path)) {
            return plugin.getConfig().getStringList("gui.sell.blocked-gamemodes");
        }
        return values;
    }

    private boolean saveConfigValue(Player player, String path, Object value, Runnable reopen) {
        EditorSaveResult result = configSettingSaver.save(path, value);
        if (!result.successful()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", result.errorMessage()));
            plugin.getEditorSounds().error(player);
            if (reopen != null) {
                reopen.run();
            }
            return false;
        }
        plugin.getMessages().send(player, "editor-saved");
        if (path.equals("sellgui.receipts.type") || path.startsWith("rotating-shop.sections.") || path.startsWith("rotating-shop.items.")) {
            plugin.getEditorSounds().cycle(player);
        } else if (value instanceof Boolean enabled) {
            plugin.getEditorSounds().toggle(player, enabled);
        } else {
            plugin.getEditorSounds().save(player);
        }
        if (reopen != null) {
            reopen.run();
        }
        return true;
    }

    private void pageSound(Player player, boolean editor, boolean next) {
        if (editor) {
            if (next) {
                plugin.getEditorSounds().nextPage(player);
            } else {
                plugin.getEditorSounds().previousPage(player);
            }
        } else {
            plugin.getSounds().play(player, next ? "gui.page-next" : "gui.page-previous");
        }
    }

    private void searchSound(Player player, boolean editor, boolean clear) {
        if (editor) {
            if (clear) {
                plugin.getEditorSounds().clearSearch(player);
            } else {
                plugin.getEditorSounds().search(player);
            }
        } else {
            plugin.getSounds().play(player, clear ? "gui.clear-search" : "gui.search");
        }
    }

    private void handleEntryBrowserClick(InventoryClickEvent event, Player player, EntryBrowserHolder holder) {
        EntryBrowserRequest request = holder.request();
        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getSlot(), holder);
        String title = request.title();
        boolean sectionBrowser = "Section Editor".equals(title);
        boolean itemBrowser = title.startsWith("Items: ");
        if (!sectionBrowser && !itemBrowser) {
            return;
        }

        String search = request.filter();
        if (sectionBrowser) {
            switch (click.action()) {
            case ENTRY -> openSectionDetailEditor(player, click.entryId(), request.page());
                case ADD -> {
                    startPrompt(player,
                        new PromptEdit(PromptType.NEW_SECTION_ID, null, null, null, request.page(), 0),
                        "&#a7b8b0Expected: new section id (letters, numbers, _ or -). Type &#ff5d73cancel &#a7b8b0to abort.");
                }
                case BACK -> openAdminEditor(player);
                case SEARCH -> {
                    plugin.getEditorSounds().search(player);
                    startPrompt(player,
                        new PromptEdit(PromptType.SECTION_SEARCH, null, null, null, request.page(), 0),
                        "&#a7b8b0Expected: section search text. Type &#ff5d73cancel &#a7b8b0to abort.");
                }
                case CLEAR_SEARCH -> {
                    plugin.getEditorSounds().clearSearch(player);
                    openSectionEditorList(player, 0, "");
                }
                case PREVIOUS_PAGE -> {
                    plugin.getEditorSounds().previousPage(player);
                    openSectionEditorList(player, request.page() - 1, search);
                }
                case NEXT_PAGE -> {
                    plugin.getEditorSounds().nextPage(player);
                    openSectionEditorList(player, request.page() + 1, search);
                }
                case NONE -> {
                }
            }
            return;
        }

        String sectionId = title.substring("Items: ".length());
        int sectionPage = sectionItemBrowserPages.getOrDefault(player.getUniqueId(), 0);
        switch (click.action()) {
            case ENTRY -> openItemEditor(player, sectionId, click.entryId(), request.page(), sectionPage);
            case ADD -> {
                addProductFromCursor(player, event.getCursor(), sectionId, request.page(), sectionPage);
            }
            case BACK -> openSectionDetailEditor(player, sectionId, sectionPage);
            case SEARCH -> {
                plugin.getEditorSounds().search(player);
                startPrompt(player,
                    new PromptEdit(PromptType.ITEM_SEARCH, null, sectionId, null, request.page(), sectionPage),
                    "&#a7b8b0Expected: product search text. Type &#ff5d73cancel &#a7b8b0to abort.");
            }
            case CLEAR_SEARCH -> {
                plugin.getEditorSounds().clearSearch(player);
                openItemListEditor(player, sectionId, 0, sectionPage, "");
            }
            case PREVIOUS_PAGE -> {
                plugin.getEditorSounds().previousPage(player);
                openItemListEditor(player, sectionId, request.page() - 1, sectionPage, search);
            }
            case NEXT_PAGE -> {
                plugin.getEditorSounds().nextPage(player);
                openItemListEditor(player, sectionId, request.page() + 1, sectionPage, search);
            }
            case NONE -> {
            }
        }
    }

    private void handleSectionDetailClick(InventoryClickEvent event, Player player, Inventory top, SectionDetailHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        switch (event.getSlot()) {
            case 10 -> openItemListEditor(player, holder.sectionId, 0, holder.sectionPage);
            case 11 -> {
                if (applySectionIconFromCursor(holder.sectionId, event.getCursor(), player)) {
                    plugin.getMessages().send(player, "editor-saved");
                    plugin.getEditorSounds().add(player);
                    openSectionDetailEditor(player, holder.sectionId, holder.sectionPage);
                }
            }
            case 12 -> startPrompt(player,
                    new PromptEdit(PromptType.SECTION_SIZE, null, holder.sectionId, null, holder.sectionPage, 0),
                    "&#a7b8b0Expected: number between 9 and 54 divisible by 9. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 13 -> startPrompt(player,
                    new PromptEdit(PromptType.SECTION_DESCRIPTION, null, holder.sectionId, null, holder.sectionPage, 0),
                    "&#a7b8b0Expected: section description lines separated by &#03fc88|&#a7b8b0. Type &#ff5d73cancel &#a7b8b0to abort, or &#ff5d73clear &#a7b8b0to remove.");
            case 14 -> startPrompt(player,
                    new PromptEdit(PromptType.SECTION_SLOT, null, holder.sectionId, null, holder.sectionPage, 0),
                    "&#a7b8b0Expected: /foshop slot from &#03fc880-53&#a7b8b0. Slots outside the current GUI rows stay saved but are not visible yet. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 15 -> {
                if (toggleSectionEnabled(holder.sectionId, player)) {
                    plugin.getMessages().send(player, "editor-saved");
                    boolean enabled = plugin.getShopManager().getSection(holder.sectionId)
                            .map(ShopSection::enabled)
                            .orElse(false);
                    plugin.getEditorSounds().toggle(player, enabled);
                    openSectionDetailEditor(player, holder.sectionId, holder.sectionPage);
                }
            }
            case 16 -> openConfirmDeleteSection(player, holder.sectionId, holder.sectionPage);
            case THREE_ROW_BACK_SLOT -> openSectionEditorList(player, holder.sectionPage);
            default -> {
            }
        }
    }

    private void handleItemEditorClick(InventoryClickEvent event, Player player, Inventory top, ItemEditorHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);

        switch (event.getSlot()) {
            case 10 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_ID, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: item id (letters, numbers, _ or -). Type &#ff5d73cancel &#a7b8b0to abort.");
            case 11 -> {
                if (hasCursorItem(event)) {
                    if (applyItemFromCursor(holder.sectionId, holder.itemId, event.getCursor(), player)) {
                        plugin.getMessages().send(player, "editor-saved");
                        plugin.getEditorSounds().add(player);
                        openItemEditor(player, holder.sectionId, holder.itemId, holder.page, holder.sectionPage);
                    }
                    return;
                }
                startPrompt(player,
                        new PromptEdit(PromptType.ITEM_MATERIAL, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                        "&#a7b8b0Expected: material name. Type &#ff5d73cancel &#a7b8b0to abort.");
            }
            case 12 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_AMOUNT, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: amount number. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 14 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_SLOT, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: gui slot index number. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 15 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_BUY, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: number or -1/disable. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 16 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_SELL, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: number or -1/disable. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 19 -> cycleItemType(player, holder);
            case 20 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_PAGE, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: page number starting at 1. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 21 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_ACTION, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Permission: node. Command: commands separated by |. Type &#ff5d73cancel &#a7b8b0to abort.");
            case FOUR_ROW_BACK_SLOT -> openItemListEditor(player, holder.sectionId, holder.page, holder.sectionPage);
            case 23 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_STOCK, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: stock amount, or -1/disable. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 24 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_BUY_LIMIT, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: buy limit amount, or -1/disable. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 22 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_STACK_SIZE, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: stack cap number. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 25 -> openConfirmDeleteItem(player, holder.sectionId, holder.itemId, holder.page, holder.sectionPage);
            default -> {
            }
        }
    }

    private void openConfirmDeleteItem(Player player, String sectionId, String itemId, int page, int sectionPage) {
        ConfirmDeleteItemHolder holder = new ConfirmDeleteItemHolder(sectionId, itemId, page, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Confirm Delete"));
        holder.setInventory(inventory);

        fillBackground(inventory);
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, "&#3ecf8eConfirm", List.of("&#ffffffDelete product: &#03fc88" + itemId)));
        inventory.setItem(15, createItem(Material.RED_CONCRETE, "&#ff5d73Cancel", List.of("&#ffffffReturn to product editor.")));

        openInventory(player, inventory);
    }

    private void openConfirmDeleteSection(Player player, String sectionId, int sectionPage) {
        ConfirmDeleteSectionHolder holder = new ConfirmDeleteSectionHolder(sectionId, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Confirm Delete"));
        holder.setInventory(inventory);

        fillBackground(inventory);
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, "&#3ecf8eConfirm", List.of("&#ffffffDelete section: &#03fc88" + sectionId)));
        inventory.setItem(15, createItem(Material.RED_CONCRETE, "&#ff5d73Cancel", List.of("&#ffffffReturn to section editor.")));

        openInventory(player, inventory);
    }

    private void handleConfirmDeleteItemClick(InventoryClickEvent event, Player player, Inventory top, ConfirmDeleteItemHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        if (event.getSlot() == 11) {
            if (removeItem(holder.sectionId, holder.itemId, player)) {
                suppressConfirmDeleteClose.add(player.getUniqueId());
                plugin.getMessages().send(player, "editor-saved");
                plugin.getEditorSounds().delete(player);
                openItemListEditor(player, holder.sectionId, holder.page, holder.sectionPage);
            } else {
                suppressConfirmDeleteClose.add(player.getUniqueId());
                plugin.getEditorSounds().error(player);
                openItemListEditor(player, holder.sectionId, holder.page, holder.sectionPage);
            }
            return;
        }

        if (event.getSlot() == 15) {
            suppressConfirmDeleteClose.add(player.getUniqueId());
            plugin.getMessages().send(player, "editor-cancelled");
            markBackNavigation(player);
            openItemEditor(player, holder.sectionId, holder.itemId, holder.page, holder.sectionPage);
        }
    }

    private void handleConfirmDeleteSectionClick(InventoryClickEvent event, Player player, Inventory top, ConfirmDeleteSectionHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        if (event.getSlot() == 11) {
            suppressConfirmDeleteClose.add(player.getUniqueId());
            if (removeSection(holder.sectionId, player)) {
                plugin.getMessages().send(player, "editor-saved");
                plugin.getEditorSounds().delete(player);
            } else {
                plugin.getEditorSounds().error(player);
            }
            openSectionEditorList(player, holder.sectionPage);
            return;
        }

        if (event.getSlot() == 15) {
            suppressConfirmDeleteClose.add(player.getUniqueId());
            plugin.getMessages().send(player, "editor-cancelled");
            markBackNavigation(player);
            openSectionDetailEditor(player, holder.sectionId, holder.sectionPage);
        }
    }

    private void openConfirmRemoveSellBooster(Player player, String boosterId, int page) {
        ConfirmRemoveSellBoosterHolder holder = new ConfirmRemoveSellBoosterHolder(boosterId, page);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps("&8Confirm Remove"));
        holder.setInventory(inventory);

        fillBackground(inventory);
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, "&#3ecf8eConfirm", List.of("&#ffffffRemove booster: &#03fc88" + boosterId)));
        inventory.setItem(15, createItem(Material.RED_CONCRETE, "&#ff5d73Cancel", List.of("&#ffffffReturn to active boosters.")));

        openInventory(player, inventory);
    }

    private void handleConfirmRemoveSellBoosterClick(InventoryClickEvent event, Player player, Inventory top, ConfirmRemoveSellBoosterHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        event.setCancelled(true);
        if (event.getSlot() == 11) {
            suppressConfirmRemoveBoosterClose.add(player.getUniqueId());
            if (plugin.getSellBoosterService().remove(holder.boosterId)) {
                plugin.getMessages().send(player, "editor-saved");
                plugin.getSounds().play(player, "shop.booster-removed");
            } else {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Booster not found."));
                plugin.getEditorSounds().error(player);
            }
            openActiveSellBoosters(player, holder.page);
            return;
        }

        if (event.getSlot() == 15) {
            suppressConfirmRemoveBoosterClose.add(player.getUniqueId());
            plugin.getMessages().send(player, "editor-cancelled");
            markBackNavigation(player);
            openActiveSellBoosters(player, holder.page);
        }
    }

    private void openBuyItemGui(Player player, String sectionId, String itemId, int selectedAmount) {
        ShopItem item = findItem(sectionId, itemId);
        if (item == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            return;
        }

        if (!item.canBuy()) {
            plugin.getMessages().send(player, "buy-disabled");
            return;
        }

        int amount = Math.clamp(selectedAmount, 1, item.effectiveStackSize());
        BuyItemHolder holder = new BuyItemHolder(sectionId, itemId, amount);
        String title = plugin.getFoConfig().guiString("buy-item", "title", "&8Buy: {item}").replace("{item}", item.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps(title));
        holder.setInventory(inventory);

        fillBackground(inventory, "buy-item");

        ItemStack center = createPurchaseStack(item, Math.clamp(amount, 1, item.effectiveStackSize()));
        ItemMeta meta = center.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    Text.colorize("&#ffffffSelected amount: &#03fc88" + amount),
                    Text.colorize("&#ffffffPrice: &#03fc88" + plugin.getEconomyService().format(item.buyPrice() * amount)),
                    Text.colorize("&#a7b8b0Use the shulker buttons below.")
            ));
            center.setItemMeta(meta);
        }
        inventory.setItem(13, center);

        placeAmountSelectionButtons(player, inventory, holder, item, amount);

        openInventory(player, inventory);
    }

    private void openBuyMoreGui(Player player, String sectionId, String itemId, int returnAmount) {
        ShopItem item = findItem(sectionId, itemId);
        if (item == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            return;
        }
        if (!item.canBuy()) {
            plugin.getMessages().send(player, "buy-disabled");
            return;
        }
        if (!supportsBulkBuy(item)) {
            openBuyItemGui(player, sectionId, itemId, returnAmount);
            return;
        }

        BuyMoreHolder holder = new BuyMoreHolder(sectionId, itemId, returnAmount);
        String title = plugin.getFoConfig().guiString("buy-more", "title", "&8Buy More: {item}").replace("{item}", item.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.getFoConfig().sectionTitleSmallCaps(title));
        holder.setInventory(inventory);

        fillBackground(inventory, "buy-more");

        int[] options = BUY_MORE_STACK_OPTIONS;
        int[] slots = BUY_MORE_SLOTS;
        Material optionMaterial = materialFromGui("buy-more", "option.material", Material.LIME_SHULKER_BOX);
        String optionName = plugin.getFoConfig().guiString("buy-more", "option.name", "&#03fc88{stacks} stack{plural}");
        List<String> optionLore = plugin.getFoConfig().guiStringList("buy-more", "option.lore", List.of(
                "&#ffffffAmount: &#03fc88{amount}",
                "&#ffffffPrice: &#03fc88{price}",
                "&#ffffffClick to buy."
        ));
        for (int i = 0; i < options.length && i < slots.length; i++) {
            int stacks = options[i];
            int totalAmount = stacks * item.effectiveStackSize();
            holder.slotToAmount.put(slots[i], totalAmount);
            inventory.setItem(slots[i], createItem(optionMaterial,
                    formatBuyMoreLine(optionName, item, totalAmount, stacks),
                    optionLore.stream().map(line -> formatBuyMoreLine(line, item, totalAmount, stacks)).toList()));
        }

        holder.backSlot = readGuiSlot("buy-more", "buttons.back", THREE_ROW_BACK_SLOT, inventory.getSize());
        inventory.setItem(holder.backSlot, GUI_BUTTONS.back(player));
        openInventory(player, inventory);
    }

    private void placeAmountSelectionButtons(Player player, Inventory inventory, BuyItemHolder holder, ShopItem item, int amount) {
        placeAmountButton(player, inventory, holder, "remove-64", 10, -64, Material.RED_STAINED_GLASS_PANE, "&#ff5d73-64", List.of("&#ffffffRemove 64."), item, amount);
        placeAmountButton(player, inventory, holder, "remove-10", 11, -10, Material.RED_STAINED_GLASS_PANE, "&#ff5d73-10", List.of("&#ffffffRemove 10."), item, amount);
        placeAmountButton(player, inventory, holder, "remove-1", 12, -1, Material.RED_STAINED_GLASS_PANE, "&#ff5d73-1", List.of("&#ffffffRemove 1."), item, amount);
        placeAmountButton(player, inventory, holder, "add-1", 14, 1, Material.LIME_STAINED_GLASS_PANE, "&#3ecf8e+1", List.of("&#ffffffAdd 1."), item, amount);
        placeAmountButton(player, inventory, holder, "add-10", 15, 10, Material.LIME_STAINED_GLASS_PANE, "&#3ecf8e+10", List.of("&#ffffffAdd 10."), item, amount);
        placeAmountButton(player, inventory, holder, "add-64", 16, 64, Material.LIME_STAINED_GLASS_PANE, "&#3ecf8e+64", List.of("&#ffffffAdd 64."), item, amount);

        holder.cancelSlot = readGuiSlot("buy-item", "buttons.cancel", THREE_ROW_BACK_SLOT, inventory.getSize());
        inventory.setItem(holder.cancelSlot, GUI_BUTTONS.back(player));

        if (supportsBulkBuy(item)) {
            GuiButton bulk = readGuiButton("buy-item", "buttons.bulk", 21, Material.HOPPER,
                    "&#03fc88Buy More", List.of("&#ffffffChoose stack amount preset."), inventory.getSize());
            holder.bulkSlot = bulk.slot();
            inventory.setItem(bulk.slot(), createButtonItem(player, bulk, formatAmountLine(bulk.name(), item, amount),
                    bulk.lore().stream().map(line -> formatAmountLine(line, item, amount)).toList()));
        } else {
            holder.bulkSlot = -1;
        }

        GuiButton confirm = readGuiButton("buy-item", "buttons.confirm", 23, Material.LIME_SHULKER_BOX,
                "&#3ecf8eConfirm Buy", List.of(
                        "&#ffffffBuy: &#03fc88{amount}",
                        "&#ffffffPrice: &#03fc88{price}"
                ), inventory.getSize());
        holder.confirmSlot = confirm.slot();
        inventory.setItem(confirm.slot(), createButtonItem(player, confirm, formatAmountLine(confirm.name(), item, amount),
                confirm.lore().stream().map(line -> formatAmountLine(line, item, amount)).toList()));
    }

    private void placeAmountButton(Player player, Inventory inventory, BuyItemHolder holder, String key, int defaultSlot, int defaultDelta,
                                   Material defaultMaterial, String defaultName, List<String> defaultLore, ShopItem item, int amount) {
        ConfigurationSection section = plugin.getFoConfig().guiSection("buy-item", "buttons." + key);
        int slot = sanitizeGuiSlot(section == null ? defaultSlot : section.getInt("slot", defaultSlot), defaultSlot, inventory.getSize(), "buy-item buttons." + key + ".slot");
        int delta = section == null ? defaultDelta : section.getInt("change", defaultDelta);
        Material material = section == null ? defaultMaterial : materialFromSection(section, "material", defaultMaterial);
        String name = section == null ? defaultName : section.getString("name", defaultName);
        List<String> lore = section == null ? defaultLore : sectionStringList(section, "lore", defaultLore);

        holder.slotToDelta.put(slot, delta);
        inventory.setItem(slot, EditorItemFactory.item(player, material, formatAmountLine(name, item, amount),
                lore.stream().map(line -> formatAmountLine(line, item, amount)).toList()));
    }

    private GuiButton readGuiButton(String file, String path, int defaultSlot, Material defaultMaterial, String defaultName, List<String> defaultLore, int inventorySize) {
        ConfigurationSection section = plugin.getFoConfig().guiSection(file, path);
        if (section == null) {
            return new GuiButton(defaultSlot, defaultMaterial, defaultName, defaultLore);
        }
        int configuredSlot = section.getInt("slot", defaultSlot);
        Material material = materialFromSection(section, "material", defaultMaterial);
        return new GuiButton(
                configuredSlot < 0 ? defaultSlot : sanitizeGuiSlot(configuredSlot, defaultSlot, inventorySize, file + " " + path + ".slot"),
                material,
                Math.clamp(section.getInt("amount", 1), 1, Math.max(1, material.getMaxStackSize())),
                section.getString("name", defaultName),
                sectionStringList(section, "lore", defaultLore),
                section.contains("custom-model-data") ? section.getInt("custom-model-data") : section.getInt("customModelData", -1)
        );
    }

    private int readGuiSlot(String file, String path, int defaultSlot, int inventorySize) {
        ConfigurationSection section = plugin.getFoConfig().guiSection(file, path);
        if (section == null) {
            return defaultSlot;
        }
        int configuredSlot = section.getInt("slot", defaultSlot);
        return configuredSlot < 0 ? defaultSlot : sanitizeGuiSlot(configuredSlot, defaultSlot, inventorySize, file + " " + path + ".slot");
    }

    private void setPreviousPageButton(Player player, Inventory inventory, int slot, int currentPage, int totalPages) {
        if (currentPage > 0) {
            inventory.setItem(slot, GUI_BUTTONS.previousPage(player, currentPage, Math.max(0, totalPages - 1)));
        }
    }

    private void setNextPageButton(Player player, Inventory inventory, int slot, int currentPage, int totalPages) {
        if (currentPage < totalPages - 1) {
            inventory.setItem(slot, GUI_BUTTONS.nextPage(player, currentPage, Math.max(0, totalPages - 1)));
        }
    }

    private void setClearSearchButton(Player player, Inventory inventory, int slot, String target, String search) {
        if (search != null && !search.isBlank()) {
            inventory.setItem(slot, GUI_BUTTONS.clearSearch(player, target));
        }
    }

    private int[] configuredSlots(String file, String path, int[] defaults, int maxSlots, int inventorySize) {
        List<Integer> configured = plugin.getFoConfig().guiIntegerList(file, path, List.of());
        if (configured.isEmpty()) {
            int length = Math.min(defaults.length, maxSlots);
            int[] out = new int[length];
            System.arraycopy(defaults, 0, out, 0, length);
            return out;
        }

        return configured.stream()
                .map(slot -> sanitizeGuiSlot(slot, -1, inventorySize, file + " " + path))
                .filter(slot -> slot >= 0)
                .limit(maxSlots)
                .mapToInt(Integer::intValue)
                .toArray();
    }

    private int sanitizeGuiSlot(int slot, int fallback) {
        return sanitizeGuiSlot(slot, fallback, 27, "gui slot");
    }

    private int sanitizeGuiSlot(int slot, int fallback, int inventorySize, String context) {
        if (slot >= 0 && slot < inventorySize) {
            return slot;
        }
        logGuiWarning("gui", "Invalid " + context + " value " + slot + "; using " + fallback + ".");
        return fallback;
    }

    private Material materialFromGui(String file, String path, Material fallback) {
        return matchMaterial(plugin.getFoConfig().guiString(file, path, fallback.name()), fallback, file + " " + path);
    }

    private Material materialFromSection(ConfigurationSection section, String path, Material fallback) {
        return matchMaterial(section.getString(path, fallback.name()), fallback, "GUI material " + path);
    }

    private List<String> sectionStringList(ConfigurationSection section, String path, List<String> fallback) {
        List<String> values = section.getStringList(path);
        return values.isEmpty() ? fallback : values;
    }

    @SafeVarargs
    private List<String> firstNonEmptyList(List<String>... values) {
        for (List<String> value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return List.of();
    }

    private String mapString(Map<?, ?> map, String path, String fallback) {
        Object value = mapValue(map, path);
        return value == null ? fallback : String.valueOf(value);
    }

    private int mapInt(Map<?, ?> map, String path, int fallback) {
        Object value = mapValue(map, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private List<String> mapStringList(Map<?, ?> map, String path) {
        Object value = mapValue(map, path);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String string && !string.isBlank()) {
            return List.of(string);
        }
        return List.of();
    }

    private Object mapValue(Map<?, ?> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(part);
        }
        return current;
    }

    private String formatAmountLine(String line, ShopItem item, int amount) {
        return line
                .replace("{amount}", String.valueOf(amount))
                .replace("{stack_size}", String.valueOf(item.effectiveStackSize()))
                .replace("{item}", item.id())
                .replace("{price}", plugin.getEconomyService().format(item.buyPrice() * amount));
    }

    private String formatBuyMoreLine(String line, ShopItem item, int amount, int stacks) {
        return formatAmountLine(line, item, amount)
                .replace("{stacks}", String.valueOf(stacks))
                .replace("{plural}", stacks == 1 ? "" : "s");
    }

    private boolean supportsBulkBuy(ShopItem item) {
        return item.type() != ShopItemType.PERMISSION;
    }

    private void handleBuyItemGuiClick(InventoryClickEvent event, Player player, Inventory top, BuyItemHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        event.setCancelled(true);

        ShopItem item = findItem(holder.sectionId, holder.itemId);
        if (item == null) {
            player.closeInventory();
            return;
        }

        Integer delta = holder.slotToDelta.get(event.getSlot());
        if (delta != null) {
            plugin.getSounds().playWithPitchVariation(player, "gui.cycle", 0.06F);
            openBuyItemGui(player, holder.sectionId, holder.itemId, holder.selectedAmount + delta);
            return;
        }

        if (event.getSlot() == holder.confirmSlot) {
            buyItem(player, holder.sectionId, item, holder.selectedAmount);
            openBuyItemGui(player, holder.sectionId, holder.itemId, holder.selectedAmount);
            return;
        }

        if (event.getSlot() == holder.bulkSlot) {
            openBuyMoreGui(player, holder.sectionId, holder.itemId, holder.selectedAmount);
            return;
        }

        if (event.getSlot() == holder.cancelSlot) {
            plugin.getSounds().play(player, "gui.cancel");
            plugin.getShopManager().getSection(holder.sectionId).ifPresent(section -> openShopSection(player, section));
        }
    }

    private void handleBuyMoreGuiClick(InventoryClickEvent event, Player player, Inventory top, BuyMoreHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        event.setCancelled(true);

        ShopItem item = findItem(holder.sectionId, holder.itemId);
        if (item == null) {
            player.closeInventory();
            return;
        }

        if (event.getSlot() == holder.backSlot) {
            markBackNavigation(player);
            openBuyItemGui(player, holder.sectionId, holder.itemId, holder.returnAmount);
            return;
        }

        Integer amount = holder.slotToAmount.get(event.getSlot());
        if (amount != null) {
            buyItem(player, holder.sectionId, item, amount);
            openBuyMoreGui(player, holder.sectionId, holder.itemId, holder.returnAmount);
        }
    }

    private void buyItem(Player player, String sectionId, ShopItem shopItem, int amount) {
        amount = shopItem.type() == ShopItemType.PERMISSION ? 1 : Math.max(1, amount);

        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "no-economy");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        if (!shopItem.canBuy() || !isFinitePositiveOrZero(shopItem.buyPrice())) {
            plugin.getMessages().send(player, "buy-disabled");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        if (!hasConfiguredPurchaseData(shopItem)) {
            plugin.getMessages().send(player, "buy-disabled");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        if (shopItem.type() == ShopItemType.PERMISSION && !canBuyPermissionItem(player, shopItem)) {
            return;
        }

        int stock = plugin.getShopManager().getStock(sectionId, shopItem.id());
        if (stock >= 0 && amount > stock) {
            plugin.getMessages().send(player, "stock-insufficient", Map.of("{stock}", String.valueOf(stock)));
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        int remainingLimit = plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), sectionId, shopItem);
        if (remainingLimit >= 0 && amount > remainingLimit) {
            plugin.getMessages().send(player, "buy-limit-reached", Map.of("{limit}", String.valueOf(remainingLimit)));
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        double totalCost = shopItem.buyPrice() * amount;
        if (totalCost < 0D || !Double.isFinite(totalCost)) {
            plugin.getMessages().send(player, "transaction-failed");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }
        if (plugin.getEconomyService().getBalance(player) < totalCost) {
            plugin.getMessages().send(player, "not-enough-money", Map.of("{amount}", plugin.getEconomyService().format(totalCost)));
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        if (requiresInventorySpace(shopItem) && !canFitExact(player.getInventory(), shopItem, amount)) {
            plugin.getMessages().send(player, "inventory-full");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        if (!plugin.getEconomyService().withdraw(player, totalCost)) {
            plugin.getMessages().send(player, "transaction-failed");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        int purchasedAmount = executePurchaseDelivery(player, sectionId, shopItem, amount);
        if (purchasedAmount < amount) {
            double refund = (amount - purchasedAmount) * shopItem.buyPrice();
            plugin.getEconomyService().deposit(player, refund);
            plugin.getMessages().send(player, "inventory-full");
        }

        if (purchasedAmount <= 0) {
            plugin.getSounds().play(player, "shop.purchase-failure");
            return;
        }

        plugin.getShopManager().recordPurchase(player.getUniqueId(), sectionId, shopItem, purchasedAmount);
        plugin.getMessages().send(player, "buy-success", Map.of(
                "{amount}", String.valueOf(purchasedAmount),
                "{item}", prettify(shopItem.material().name()),
                "{price}", plugin.getEconomyService().format(shopItem.buyPrice() * purchasedAmount)
        ));
        plugin.getSounds().play(player, "shop.purchase");
    }

    private boolean canBuyPermissionItem(Player player, ShopItem item) {
        if (item.permission() == null || item.permission().isBlank()) {
            return false;
        }
        if (!plugin.getPermissionService().isEnabled()) {
            plugin.getMessages().send(player, "permission-provider-missing");
            return false;
        }
        if (plugin.getPermissionService().has(player, item.permission())) {
            plugin.getMessages().send(player, "permission-already-owned");
            return false;
        }
        return true;
    }

    private boolean hasConfiguredPurchaseData(ShopItem item) {
        return switch (item.type()) {
            case ITEM, DUMMY -> true;
            case PERMISSION -> item.permission() != null && !item.permission().isBlank();
            case COMMAND -> item.commands() != null && !item.commands().isEmpty();
            case ENCHANTMENT -> item.enchantment() != null
                    && !item.enchantment().isBlank()
                    && parseEnchantment(item.enchantment()) != null;
        };
    }

    private void handleSellClick(InventoryClickEvent event, Player player, Inventory top, SellGuiHolder holder) {
        int inputEnd = top.getSize() - 9;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && canCollectProtectedSellItem(event.getCursor(), top, holder)) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            if (isProtectedSellSlot(event.getRawSlot(), top, holder)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getClickedInventory().equals(top)) {
            int slot = event.getRawSlot();
            if (holder.decorationSlots.contains(slot)) {
                event.setCancelled(true);
                runSellGuiDecorationCommands(player, holder, slot);
                return;
            }

            if (slot >= inputEnd) {
                event.setCancelled(true);

                if (slot == holder.sellSlot) {
                    if (!plugin.getEconomyService().isEnabled()) {
                        plugin.getMessages().send(player, "no-economy");
                        return;
                    }
                    if (!sellingInProgress.add(player.getUniqueId())) {
                        plugin.getMessages().send(player, "sellgui-already-processing");
                        return;
                    }
                    sellFromGui(player, top);
                }
            }
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && !canAcceptSellInputItem(top, holder, event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedSellSlot(int rawSlot, Inventory top, SellGuiHolder holder) {
        int inputEnd = top.getSize() - 9;
        return rawSlot >= 0 && rawSlot < top.getSize()
                && (rawSlot >= inputEnd || holder.decorationSlots.contains(rawSlot));
    }

    private boolean canAcceptSellInputItem(Inventory top, SellGuiHolder holder, ItemStack incoming) {
        if (incoming == null || incoming.getType() == Material.AIR) {
            return false;
        }
        int inputEnd = top.getSize() - 9;
        for (int slot = 0; slot < inputEnd; slot++) {
            if (holder.decorationSlots.contains(slot)) {
                continue;
            }
            ItemStack item = top.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return true;
            }
            if (item.isSimilar(incoming) && item.getAmount() < item.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean canCollectProtectedSellItem(ItemStack cursor, Inventory top, SellGuiHolder holder) {
        if (cursor == null || cursor.getType() == Material.AIR) {
            return false;
        }
        int inputEnd = top.getSize() - 9;
        for (int slot = inputEnd; slot < top.getSize(); slot++) {
            if (isSimilarNonAir(cursor, top.getItem(slot))) {
                return true;
            }
        }
        for (int slot : holder.decorationSlots) {
            if (slot >= 0 && slot < top.getSize() && isSimilarNonAir(cursor, top.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isSimilarNonAir(ItemStack first, ItemStack second) {
        return first != null
                && second != null
                && first.getType() != Material.AIR
                && second.getType() != Material.AIR
                && first.isSimilar(second);
    }

    private void runSellGuiDecorationCommands(Player player, SellGuiHolder holder, int slot) {
        for (String command : holder.playerCommands.getOrDefault(slot, List.of())) {
            player.performCommand(applyPlayerPlaceholders(player, command));
        }
        for (String command : holder.consoleCommands.getOrDefault(slot, List.of())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlayerPlaceholders(player, command));
        }
    }

    public void sellAll(Player player) {
        if (!startDirectSell(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        boolean foundItems = false;
        SellResult totalResult = new SellResult();
        List<ItemStack> returnedOverflow = new ArrayList<>();
        SellProcessingContext context = new SellProcessingContext();
        int storageSlots = Math.min(PLAYER_STORAGE_SLOT_COUNT, inventory.getSize());

        for (int slot = 0; slot < storageSlots; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            foundItems = true;
            SellResult result = processSellItem(player, item.clone(), context);
            if (result.soldUnits <= 0) {
                continue;
            }

            totalResult.merge(result);
            inventory.setItem(slot, null);
            if (!result.returnedItems.isEmpty()) {
                inventory.setItem(slot, result.returnedItems.getFirst());
                returnedOverflow.addAll(result.returnedItems.subList(1, result.returnedItems.size()));
            }
        }

        if (!foundItems) {
            sellingInProgress.remove(player.getUniqueId());
            plugin.getMessages().send(player, "sellall-empty");
            plugin.getSounds().play(player, "sell.failure");
            return;
        }

        finishSellFromGui(player, totalResult, returnedOverflow);
    }

    public void sellHand(Player player) {
        if (!startDirectSell(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            sellingInProgress.remove(player.getUniqueId());
            plugin.getMessages().send(player, "sellhand-empty");
            plugin.getSounds().play(player, "sell.failure");
            return;
        }

        SellResult result = processSellItem(player, hand.clone(), new SellProcessingContext());
        if (result.soldUnits <= 0) {
            sellingInProgress.remove(player.getUniqueId());
            plugin.getMessages().send(player, "sellgui-none");
            plugin.getSounds().play(player, "sell.failure");
            return;
        }

        inventory.setItemInMainHand(null);
        List<ItemStack> returned = new ArrayList<>(result.returnedItems);
        if (!returned.isEmpty()) {
            inventory.setItemInMainHand(returned.removeFirst());
        }
        finishSellFromGui(player, result, returned);
    }

    private boolean startDirectSell(Player player) {
        if (plugin.getFoConfig().isSellGamemodeBlocked(player.getGameMode().name())) {
            plugin.getMessages().send(player, "sellgui-gamemode-not-allowed", Map.of("{gamemode}", player.getGameMode().name()));
            plugin.getSounds().play(player, "sell.failure");
            return false;
        }

        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "no-economy");
            plugin.getSounds().play(player, "sell.failure");
            return false;
        }

        if (!sellingInProgress.add(player.getUniqueId())) {
            plugin.getMessages().send(player, "sellgui-already-processing");
            return false;
        }

        return true;
    }

    private void sellFromGui(Player player, Inventory inventory) {
        int inputEnd = inventory.getSize() - 9;
        SellGuiHolder holder = inventory.getHolder() instanceof SellGuiHolder sellGuiHolder ? sellGuiHolder : null;
        List<ItemStack> snapshot = new ArrayList<>();

        for (int slot = 0; slot < inputEnd; slot++) {
            if (holder != null && holder.decorationSlots.contains(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            snapshot.add(item.clone());
            inventory.setItem(slot, null);
        }

        if (snapshot.isEmpty()) {
            sellingInProgress.remove(player.getUniqueId());
            plugin.getMessages().send(player, "sellgui-empty");
            plugin.getSounds().play(player, "sell.failure");
            return;
        }

        SellResult totalResult = new SellResult();
        List<ItemStack> returned = new ArrayList<>();
        SellProcessingContext context = new SellProcessingContext();

        for (ItemStack item : snapshot) {
            SellResult result = processSellItem(player, item, context);
            totalResult.merge(result);
            returned.addAll(result.returnedItems);
        }

        finishSellFromGui(player, totalResult, returned);
    }

    private void finishSellFromGui(Player player, SellResult totalResult, List<ItemStack> returned) {
        try {
            if (!player.isOnline()) {
                dropSellItems(player, returned);
                dropSellItems(player, totalResult.soldItems);
                return;
            }

            boolean overflowed = depositSellItems(player, returned);

            if (totalResult.soldUnits > 0) {
                if (totalResult.earned <= 0D
                        || !Double.isFinite(totalResult.earned)
                        || !plugin.getEconomyService().deposit(player, totalResult.earned)) {
                    overflowed |= depositSellItems(player, totalResult.soldItems);
                    if (overflowed) {
                        plugin.getMessages().send(player, "sellgui-inventory-full");
                    }
                    plugin.getMessages().send(player, "no-economy");
                    plugin.getSounds().play(player, "sell.failure");
                    return;
                }
                if (overflowed) {
                    plugin.getMessages().send(player, "sellgui-inventory-full");
                }
                sendSellSuccess(player, totalResult);
                plugin.getSounds().play(player, "sell.success");
                transactionLogger.write(player, totalResult.soldUnits, formatSellMoney(totalResult.earned), buildSoldList(totalResult));
            } else {
                if (overflowed) {
                    plugin.getMessages().send(player, "sellgui-inventory-full");
                }
                plugin.getMessages().send(player, "sellgui-none");
                plugin.getSounds().play(player, "sell.failure");
            }
        } finally {
            sellingInProgress.remove(player.getUniqueId());
        }
    }

    private void dropSellItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private boolean depositSellItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        InventoryDepositResult result = plugin.getCore().inventoryDeposits().deposit(player, items, player.getLocation(), OverflowPolicy.DROP_OVERFLOW, false);
        return result.hasOverflow();
    }

    private void sendSellSuccess(Player player, SellResult result) {
        String earning = formatSellMoney(result.earned);
        String list = buildSoldList(result);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{amount}", String.valueOf(result.soldUnits));
        placeholders.put("%amount%", String.valueOf(result.soldUnits));
        placeholders.put("{earning}", earning);
        placeholders.put("%earning%", earning);
        placeholders.put("{price}", earning);
        placeholders.put("%price%", earning);
        placeholders.put("{list}", list);
        placeholders.put("%list%", list);
        placeholders.put("{items}", list);
        placeholders.put("%items%", list);

        String message = plugin.getMessages().render("sellgui-success", "", placeholders);
        if (plugin.getFoConfig().getSellReceiptType() == 1 && !result.soldLines.isEmpty()) {
            sendReceiptMessage(player, message, placeholders, result);
        } else {
            Text.send(player, message);
        }

        if (plugin.getFoConfig().isSellTitlesEnabled()) {
            player.sendTitle(
                    Text.format(plugin.getFoConfig().getSellTitleMessage(), placeholders),
                    Text.format(plugin.getFoConfig().getSellSubtitleMessage(), placeholders),
                    10,
                    50,
                    20
            );
        }

        if (plugin.getFoConfig().isSellActionBarEnabled()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(Text.format(plugin.getFoConfig().getSellActionBarMessage(), placeholders)));
        }
    }

    private void sendReceiptMessage(Player player, String message, Map<String, String> placeholders, SellResult result) {
        String receiptText = plugin.getFoConfig().getSellReceiptText();
        String hover = buildReceiptHover(placeholders, result);

        TextComponent root = new TextComponent(TextComponent.fromLegacyText(message + " "));
        TextComponent receipt = new TextComponent(TextComponent.fromLegacyText(Text.colorize(receiptText)));
        receipt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.hover.content.Text(TextComponent.fromLegacyText(hover))));
        root.addExtra(receipt);
        player.spigot().sendMessage(root);
    }

    private String buildReceiptHover(Map<String, String> placeholders, SellResult result) {
        String title = Text.format(plugin.getFoConfig().getSellReceiptTitle(), placeholders);
        String layout = plugin.getFoConfig().getSellReceiptItemLayout();
        StringBuilder builder = new StringBuilder(title);
        for (SoldLine line : result.soldLines) {
            builder.append(Text.colorize(formatSoldLine(layout, line))).append('\n');
        }
        return builder.toString();
    }

    private String buildSoldList(SellResult result) {
        List<String> lines = new ArrayList<>();
        for (SoldLine line : result.soldLines) {
            lines.add(line.amount() + "x " + line.item());
        }
        return String.join(", ", lines);
    }

    private SellResult processSellItem(Player player, ItemStack stack) {
        return processSellItem(player, stack, new SellProcessingContext());
    }

    private SellResult processSellItem(Player player, ItemStack stack, SellProcessingContext context) {
        return processSellItem(player, stack, context, 0);
    }

    private SellResult processSellItem(Player player, ItemStack stack, SellProcessingContext context, int depth) {
        SellResult result = new SellResult();

        if (stack == null || stack.getType() == Material.AIR) {
            return result;
        }
        if (context == null) {
            context = new SellProcessingContext();
        }
        if (!context.consumeStep()) {
            result.returnedItems.add(stack.clone());
            return result;
        }

        try {
            if (isContainerItem(stack)) {
                if (depth >= MAX_SELL_CONTAINER_DEPTH) {
                    result.returnedItems.add(stack.clone());
                    return result;
                }

                for (int i = 0; i < stack.getAmount(); i++) {
                    if (!context.hasCapacity()) {
                        ItemStack remaining = stack.clone();
                        remaining.setAmount(stack.getAmount() - i);
                        result.returnedItems.add(remaining);
                        break;
                    }
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    SellResult containerResult = processContainerContents(player, one, context, depth);
                    result.merge(containerResult);
                    result.returnedItems.addAll(containerResult.returnedItems);
                }
                return result;
            }

            double price = plugin.getShopManager().getSellPrice(player, stack);
            double totalPrice = price * stack.getAmount();
            if (price <= 0D || !Double.isFinite(price) || totalPrice <= 0D || !Double.isFinite(totalPrice)) {
                result.returnedItems.add(stack.clone());
                return result;
            }

            result.earned += totalPrice;
            result.soldUnits += stack.getAmount();
            result.soldItems.add(stack.clone());
            result.addSoldLine(displayItemName(stack), stack.getAmount(), totalPrice);
            return result;
        } catch (RuntimeException exception) {
            clearSellResult(result);
            result.returnedItems.add(stack.clone());
            return result;
        }
    }

    private SellResult processContainerContents(Player player, ItemStack containerItem, SellProcessingContext context, int depth) {
        SellResult result = new SellResult();
        ItemStack originalContainer = containerItem.clone();

        try {
            ItemMeta meta = containerItem.getItemMeta();
            if (meta == null) {
                result.merge(processContainerShell(player, containerItem));
                if (result.soldUnits <= 0) {
                    result.returnedItems.add(containerItem);
                }
                return result;
            }

            if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof Container container) {
                Inventory containerInventory = container.getInventory();
                List<ItemStack> unsold = new ArrayList<>();
                boolean hadContents = false;

                for (ItemStack content : containerInventory.getContents()) {
                    if (content == null || content.getType() == Material.AIR) {
                        continue;
                    }
                    hadContents = true;
                    SellResult contentResult = processSellItem(player, content.clone(), context, depth + 1);
                    result.merge(contentResult);
                    unsold.addAll(contentResult.returnedItems);
                }
                if (!hadContents) {
                    result.merge(processContainerShell(player, containerItem));
                    if (result.soldUnits <= 0) {
                        result.returnedItems.add(containerItem);
                    }
                    return result;
                }

                List<ItemStack> overflow = new ArrayList<>();
                containerInventory.clear();
                for (int i = 0; i < unsold.size(); i++) {
                    if (i >= containerInventory.getSize()) {
                        overflow.add(unsold.get(i));
                        continue;
                    }
                    containerInventory.setItem(i, unsold.get(i));
                }

                blockStateMeta.setBlockState(container);
                containerItem.setItemMeta(blockStateMeta);
                result.returnedItems.add(containerItem);
                result.returnedItems.addAll(overflow);
                return result;
            }

            if (meta instanceof BundleMeta bundleMeta) {
                List<ItemStack> unsold = new ArrayList<>();
                List<ItemStack> contents = bundleMeta.getItems();
                if (contents.isEmpty()) {
                    result.merge(processContainerShell(player, containerItem));
                    if (result.soldUnits <= 0) {
                        result.returnedItems.add(containerItem);
                    }
                    return result;
                }
                for (ItemStack content : contents) {
                    SellResult contentResult = processSellItem(player, content.clone(), context, depth + 1);
                    result.merge(contentResult);
                    unsold.addAll(contentResult.returnedItems);
                }
                bundleMeta.setItems(unsold);
                containerItem.setItemMeta(bundleMeta);
                result.returnedItems.add(containerItem);
                return result;
            }

            result.merge(processContainerShell(player, containerItem));
            if (result.soldUnits <= 0) {
                result.returnedItems.add(containerItem);
            }
            return result;
        } catch (RuntimeException exception) {
            clearSellResult(result);
            result.returnedItems.add(originalContainer);
            return result;
        }
    }

    private SellResult processContainerShell(Player player, ItemStack containerItem) {
        SellResult result = new SellResult();
        ItemStack shell = stripContainerContents(containerItem);
        double price = plugin.getShopManager().getSellPrice(player, shell);
        if (price <= 0D || !Double.isFinite(price)) {
            return result;
        }

        result.earned += price;
        result.soldUnits += 1;
        result.soldItems.add(shell);
        result.addSoldLine(displayItemName(shell), 1, price);
        return result;
    }

    private ItemStack stripContainerContents(ItemStack stack) {
        ItemStack shell = stack.clone();
        shell.setAmount(1);
        ItemMeta meta = shell.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof Container container) {
            container.getInventory().clear();
            blockStateMeta.setBlockState(container);
            shell.setItemMeta(blockStateMeta);
            return shell;
        }
        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.setItems(List.of());
            shell.setItemMeta(bundleMeta);
        }
        return shell;
    }

    private void clearSellResult(SellResult result) {
        result.returnedItems.clear();
        result.soldItems.clear();
        result.soldLines.clear();
        result.earned = 0D;
        result.soldUnits = 0;
    }

    private boolean isContainerItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        return meta instanceof BlockStateMeta || meta instanceof BundleMeta;
    }

    private String displayItemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return prettify(stack.getType().name());
    }

    private String formatSellMoney(double amount) {
        return plugin.getFoConfig().formatSellMoney(amount, plugin.getEconomyService().format(amount));
    }

    private String formatSoldLine(String layout, SoldLine line) {
        return layout
                .replace("{amount}", String.valueOf(line.amount()))
                .replace("%amount%", String.valueOf(line.amount()))
                .replace("{item}", line.item())
                .replace("%item%", line.item())
                .replace("{price}", formatSellMoney(line.price()))
                .replace("%price%", formatSellMoney(line.price()))
                .replace("{earning}", formatSellMoney(line.price()))
                .replace("%earning%", formatSellMoney(line.price()));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof EntryBrowserHolder) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= 0 && rawSlot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (top.getHolder() instanceof FoHolder && !(top.getHolder() instanceof SellGuiHolder)) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= 0 && rawSlot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (!(top.getHolder() instanceof SellGuiHolder holder)) {
            return;
        }

        int inputEnd = top.getSize() - 9;
        for (int rawSlot : event.getRawSlots()) {
            if ((rawSlot >= inputEnd && rawSlot < top.getSize()) || holder.decorationSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory top = event.getInventory();
        if (plugin.getCore() != null && plugin.getCore().inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }

        clearScreenTrackingIfClosed(player);

        if (top.getHolder() instanceof ConfirmDeleteItemHolder holder) {
            if (suppressConfirmDeleteClose.remove(player.getUniqueId())) {
                return;
            }
            runSyncIfEnabled(() -> {
                if (!player.isOnline()) {
                    return;
                }
                plugin.getMessages().send(player, "editor-cancelled");
                openItemEditor(player, holder.sectionId, holder.itemId, holder.page, holder.sectionPage);
            });
            return;
        }

        if (top.getHolder() instanceof ConfirmDeleteSectionHolder holder) {
            if (suppressConfirmDeleteClose.remove(player.getUniqueId())) {
                return;
            }
            runSyncIfEnabled(() -> {
                if (!player.isOnline()) {
                    return;
                }
                plugin.getMessages().send(player, "editor-cancelled");
                openSectionDetailEditor(player, holder.sectionId, holder.sectionPage);
            });
            return;
        }

        if (top.getHolder() instanceof ConfirmRemoveSellBoosterHolder holder) {
            if (suppressConfirmRemoveBoosterClose.remove(player.getUniqueId())) {
                return;
            }
            runSyncIfEnabled(() -> {
                if (!player.isOnline()) {
                    return;
                }
                plugin.getMessages().send(player, "editor-cancelled");
                openActiveSellBoosters(player, holder.page);
            });
            return;
        }

        if (!(top.getHolder() instanceof SellGuiHolder)) {
            return;
        }

        if (sellingInProgress.contains(player.getUniqueId())) {
            return;
        }
        if (!hasSellGuiItems(top)) {
            return;
        }
        if (!plugin.getEconomyService().isEnabled()) {
            returnUnsoldItems(player, top);
            plugin.getMessages().send(player, "no-economy");
            return;
        }
        if (!sellingInProgress.add(player.getUniqueId())) {
            return;
        }
        sellFromGui(player, top);
    }

    private void runSyncIfEnabled(Runnable task) {
        if (!plugin.isEnabled() || plugin.getCore() == null) {
            return;
        }
        plugin.getCore().scheduler().runGlobal(() -> {
            if (plugin.isEnabled()) {
                task.run();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chatPrompts.clear(event.getPlayer());
        rotatingItemSectionPages.remove(uuid);
        sellingInProgress.remove(uuid);
        suppressConfirmDeleteClose.remove(uuid);
        suppressConfirmRemoveBoosterClose.remove(uuid);
        nativeDialogFallbackWarnings.remove(uuid);
        if (plugin.getCore() != null) {
            plugin.getCore().inventoryCloseSuppressor().clear(event.getPlayer());
        }
    }

    private void handlePromptInput(Player player, PromptEdit edit, String input) {
        String trimmed = input == null ? "" : input.trim();
        if (PromptNormalizer.isCancel(trimmed)) {
            handlePromptCancel(player, edit);
            return;
        }

        PromptOutcome outcome = applyPrompt(edit, trimmed, player);
        if (outcome.success) {
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().info("Editor prompt saved by " + player.getName() + ": " + promptLogContext(edit));
            }
            plugin.getMessages().send(player, outcome.feedbackMessage);
            playPromptSuccessSound(player, edit.type);
            openAfterPromptSuccess(player, edit, outcome.updatedItemId);
        } else {
            plugin.getEditorSounds().error(player);
            openAfterPromptCancel(player, edit);
        }
    }

    private void playPromptSuccessSound(Player player, PromptType type) {
        switch (type) {
            case SECTION_SEARCH, ROTATING_SECTION_SEARCH, ROTATING_ITEM_SEARCH, GLOBAL_PRICE_SEARCH, ITEM_SEARCH -> {
                // The search control already played its feedback before opening the prompt.
            }
            case NEW_SECTION_ID -> plugin.getEditorSounds().add(player);
            case SELL_BOOSTER_START -> plugin.getSounds().play(player, "shop.booster-started");
            default -> plugin.getEditorSounds().save(player);
        }
    }

    private void handlePromptCancel(Player player, PromptEdit edit) {
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().debug("Editor prompt cancelled by " + player.getName() + ": " + promptLogContext(edit));
        }
        plugin.getMessages().send(player, "editor-cancelled");
        openAfterPromptCancel(player, edit);
    }

    private PromptOutcome applyPrompt(PromptEdit edit, String input, Player player) {
        switch (edit.type) {
            case CONFIG_STRING -> {
                return savePromptConfigValue(player, edit, input);
            }
            case CONFIG_DOUBLE -> {
                double value;
                try {
                    value = Double.parseDouble(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Value must be a number."));
                    return PromptOutcome.failure();
                }
                if (!Double.isFinite(value) || value < 0D) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Value must be 0 or higher."));
                    return PromptOutcome.failure();
                }
                return savePromptConfigValue(player, edit, value);
            }
            case CONFIG_SOUND -> {
                if (!SoundTypes.isValid(input)) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Invalid Bukkit sound."));
                    return PromptOutcome.failure();
                }
                return savePromptConfigValue(player, edit, input.trim());
            }
            case CONFIG_GAMEMODE_LIST -> {
                List<String> gamemodes = parseGamemodeList(input, player);
                if (gamemodes == null) {
                    return PromptOutcome.failure();
                }
                return savePromptConfigValue(player, edit, gamemodes);
            }
            case ROTATING_RESET_SECONDS -> {
                Long seconds = parseDurationSeconds(input, player);
                if (seconds == null) {
                    return PromptOutcome.failure();
                }
                return savePromptConfigValue(player, edit, seconds);
            }
            case SELL_BOOSTER_START -> {
                return startSellBoosterFromPrompt(edit, input, player);
            }
            case MAIN_ROWS -> {
                int rows;
                try {
                    rows = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Main shop rows must be a number."));
                    return PromptOutcome.failure();
                }
                if (rows < 1 || rows > 6) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Main shop rows must be 1-6."));
                    return PromptOutcome.failure();
                }
                return updateMainGuiRows(rows, player) ? PromptOutcome.success(edit.itemId) : PromptOutcome.failure();
            }
            case SECTION_SEARCH, ITEM_SEARCH, ROTATING_SECTION_SEARCH, ROTATING_ITEM_SEARCH, GLOBAL_PRICE_SEARCH -> {
                return PromptOutcome.search(input);
            }
            case NEW_SECTION_ID -> {
                if (createSection(input, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case SECTION_SIZE -> {
                int size;
                try {
                    size = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Size must be a number."));
                    return PromptOutcome.failure();
                }

                if (size < 9 || size > 54 || size % 9 != 0) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Size must be 9-54 and divisible by 9."));
                    return PromptOutcome.failure();
                }

                if (updateSectionField(edit.sectionId, "size", size, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case SECTION_SLOT -> {
                int slot;
                try {
                    slot = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Main menu slot must be a number."));
                    return PromptOutcome.failure();
                }
                if (slot < 0 || slot > 53) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Main menu slot must be 0-53."));
                    return PromptOutcome.failure();
                }
                if (updateSectionField(edit.sectionId, "slot", slot, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case SECTION_DESCRIPTION -> {
                if (updateSectionField(edit.sectionId, "description", parseDescriptionInput(input), player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_ID -> {
                String newId = normalizeItemId(input);
                if (newId.isBlank()) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product id is empty."));
                    return PromptOutcome.failure();
                }
                if (renameItem(edit.sectionId, edit.itemId, newId, player)) {
                    return PromptOutcome.success(newId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_MATERIAL -> {
                Material material = ItemKeys.material(input).orElse(null);
                if (material == null || material == Material.AIR) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Invalid material."));
                    return PromptOutcome.failure();
                }

                if (updateItemMaterial(edit.sectionId, edit.itemId, material, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_AMOUNT -> {
                int amount;
                try {
                    amount = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Amount must be a number."));
                    return PromptOutcome.failure();
                }

                ShopItem item = findItem(edit.sectionId, edit.itemId);
                if (item == null) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
                    return PromptOutcome.failure();
                }

                int maxStack = item.effectiveStackSize();
                if (amount < 1 || amount > maxStack) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Amount must be 1-" + maxStack + "."));
                    return PromptOutcome.failure();
                }

                if (updateItemField(edit.sectionId, edit.itemId, "amount", amount, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_SLOT -> {
                int slot;
                try {
                    slot = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Slot must be a number."));
                    return PromptOutcome.failure();
                }

                ShopSection section = plugin.getShopManager().getSection(edit.sectionId).orElse(null);
                if (section == null) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
                    return PromptOutcome.failure();
                }

                if (slot < 0 || slot >= section.size()) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Slot must be 0-" + (section.size() - 1) + "."));
                    return PromptOutcome.failure();
                }

                if (isReservedSectionSlot(section, slot)) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "That slot is reserved for the back button."));
                    return PromptOutcome.failure();
                }

                if (isSlotTakenByOther(section, edit.itemId, slot)) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Another product already uses that slot."));
                    return PromptOutcome.failure();
                }

                if (updateItemField(edit.sectionId, edit.itemId, "slot", slot, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_BUY -> {
                Double value = parsePriceInput(input, player);
                if (value == null) {
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "buy-price", value, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_SELL -> {
                Double value = parsePriceInput(input, player);
                if (value == null) {
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "sell-price", value, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_TYPE -> {
                ShopItemType type = ShopItemType.fromString(input);
                if (updateItemField(edit.sectionId, edit.itemId, "type", type.name().toLowerCase(Locale.ROOT), player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_PAGE -> {
                int page;
                try {
                    page = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Page must be a number."));
                    return PromptOutcome.failure();
                }
                if (page < 1) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Page must be 1 or higher."));
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "page", page - 1, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_ACTION -> {
                ShopItem item = findItem(edit.sectionId, edit.itemId);
                if (item == null) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
                    return PromptOutcome.failure();
                }
                if (updateItemAction(edit.sectionId, edit.itemId, item.type(), input, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_STACK_SIZE -> {
                int stackSize;
                try {
                    stackSize = Integer.parseInt(input);
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Stack cap must be a number."));
                    return PromptOutcome.failure();
                }

                ShopItem item = findItem(edit.sectionId, edit.itemId);
                if (item == null) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
                    return PromptOutcome.failure();
                }

                int maxStack = Math.max(1, Math.min(64, item.material().getMaxStackSize()));
                if (stackSize < 1 || stackSize > maxStack) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Stack cap must be 1-" + maxStack + "."));
                    return PromptOutcome.failure();
                }

                if (updateItemStackSize(edit.sectionId, edit.itemId, stackSize, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_STOCK -> {
                Integer value = parseOptionalNonNegativeInt(input, player, "Stock");
                if (value == null && !isDisableInput(input)) {
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "stock", value, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_BUY_LIMIT -> {
                Integer value = parseOptionalNonNegativeInt(input, player, "Buy limit");
                if (value == null && !isDisableInput(input)) {
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "buy-limit", value, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case GLOBAL_PRICE -> {
                Double value = parsePriceInput(input, player);
                if (value == null) {
                    return PromptOutcome.failure();
                }
                Optional<GlobalSellPriceService.GlobalEnchantmentEntry> enchantmentEntry = plugin.getGlobalSellPriceService().enchantmentEntry(edit.itemId);
                if (enchantmentEntry.isPresent()) {
                    if (!plugin.getGlobalSellPriceService().setEnchantmentPrice(enchantmentEntry.get().enchantmentKey(), enchantmentEntry.get().level(), Math.max(0D, value))) {
                        plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Failed to save enchantment price."));
                        return PromptOutcome.failure();
                    }
                    plugin.getRotatingShopService().reload();
                    return PromptOutcome.success(edit.itemId);
                }

                Optional<GlobalSellPriceService.GlobalPotionEntry> potionEntry = plugin.getGlobalSellPriceService().potionEntry(edit.itemId);
                if (potionEntry.isPresent()) {
                    if (!plugin.getGlobalSellPriceService().setPotionPrice(potionEntry.get().potionType(), Math.max(0D, value))) {
                        plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Failed to save potion price."));
                        return PromptOutcome.failure();
                    }
                    plugin.getRotatingShopService().reload();
                    return PromptOutcome.success(edit.itemId);
                }

                Material material = Material.matchMaterial(edit.itemId == null ? "" : edit.itemId);
                if (material == null || material == Material.AIR) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Material not found."));
                    return PromptOutcome.failure();
                }
                if (!plugin.getGlobalSellPriceService().setPrice(material, Math.max(0D, value))) {
                    plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Failed to save global price."));
                    return PromptOutcome.failure();
                }
                plugin.getRotatingShopService().reload();
                return PromptOutcome.success(edit.itemId);
            }
        }

        return PromptOutcome.failure();
    }

    private PromptOutcome savePromptConfigValue(Player player, PromptEdit edit, Object value) {
        EditorSaveResult result = configSettingSaver.save(edit.configPath, value);
        if (!result.successful()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", result.errorMessage()));
            return PromptOutcome.failure();
        }
        return PromptOutcome.success(edit.itemId);
    }

    private List<String> parseGamemodeList(String input, Player player) {
        if (input.equalsIgnoreCase("clear") || input.equalsIgnoreCase("none") || input.equals("-")) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String part : input.split("[|,\\s]+")) {
            String value = part.trim().toUpperCase(Locale.ROOT);
            if (value.isBlank()) {
                continue;
            }
            try {
                GameMode.valueOf(value);
            } catch (IllegalArgumentException exception) {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Invalid gamemode: " + value));
                return null;
            }
            values.add(value);
        }
        return values;
    }

    private Long parseDurationSeconds(String input, Player player) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.isBlank()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Duration is empty."));
            return null;
        }

        OptionalLong parsed = DurationUtil.parseSeconds(normalized);
        if (parsed.isEmpty()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Duration must be 60s+, 1h, or 1d."));
            return null;
        }

        long seconds = parsed.getAsLong();
        if (seconds < 60L) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Duration must be at least 60 seconds."));
            return null;
        }
        return seconds;
    }

    private PromptOutcome startSellBoosterFromPrompt(PromptEdit edit, String input, Player player) {
        SellBoosterScope scope = SellBoosterScope.fromInput(edit.configPath).orElse(null);
        if (scope == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Unknown booster scope."));
            return PromptOutcome.failure();
        }

        SellBoosterInputParser.StartInput promptInput = SellBoosterInputParser.parsePromptStart(scope, input).orElse(null);
        if (promptInput == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Expected " + (scope == SellBoosterScope.GLOBAL ? "multiplier duration." : "target multiplier duration.")));
            return PromptOutcome.failure();
        }

        String ownerId = "";
        String ownerName = "Global";
        if (scope == SellBoosterScope.PLAYER) {
            SellBoosterInputParser.Target target = SellBoosterInputParser.resolvePlayerTarget(promptInput.target()).orElse(null);
            if (target == null) {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Player not found."));
                return PromptOutcome.failure();
            }
            ownerId = target.ownerId();
            ownerName = target.ownerName();
        } else if (scope == SellBoosterScope.TEAM) {
            if (!plugin.getSellBoosterService().isFoTeamsAvailable()) {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "FoTeams is not installed or enabled."));
                return PromptOutcome.failure();
            }
            var team = plugin.getSellBoosterService().teamByInput(promptInput.target());
            if (team.isEmpty()) {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Team not found."));
                return PromptOutcome.failure();
            }
            ownerId = team.get().id();
            ownerName = team.get().name();
        }

        Double multiplier = SellBoosterInputParser.parseMultiplier(promptInput.multiplier());
        if (multiplier == null || multiplier <= 1D) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Multiplier must be above 1x."));
            return PromptOutcome.failure();
        }

        OptionalLong duration = DurationUtil.parseSeconds(promptInput.duration());
        if (duration.isEmpty() || duration.getAsLong() < 60L) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Duration must be 60s+, 1h, or 1d."));
            return PromptOutcome.failure();
        }

        plugin.getSellBoosterService().start(scope, ownerId, ownerName, multiplier, duration.getAsLong());
        return PromptOutcome.success(null);
    }

    private void openAfterPromptSuccess(Player player, PromptEdit edit, String updatedItemId) {
        switch (edit.type) {
            case CONFIG_STRING, CONFIG_DOUBLE, CONFIG_SOUND, CONFIG_GAMEMODE_LIST, MAIN_ROWS -> openSettingsEditor(player);
            case ROTATING_RESET_SECONDS -> openRotatingShopEditor(player);
            case SELL_BOOSTER_START -> openSellBoosterEditor(player);
            case SECTION_SEARCH -> openSectionEditorList(player, 0, updatedItemId);
            case ROTATING_SECTION_SEARCH -> openRotatingSectionEditorList(player, 0, updatedItemId);
            case ROTATING_ITEM_SEARCH -> openRotatingItemEditorList(player, edit.sectionId, 0, edit.sectionPage, updatedItemId);
            case GLOBAL_PRICE_SEARCH -> openGlobalSellPriceList(player, "editor".equals(edit.configPath), 0, updatedItemId, sortFromOrdinal(edit.sectionPage));
            case ITEM_SEARCH -> openItemListEditor(player, edit.sectionId, 0, edit.sectionPage, updatedItemId);
            case NEW_SECTION_ID -> openSectionEditorList(player, edit.page);
            case SECTION_SIZE, SECTION_SLOT, SECTION_DESCRIPTION -> openSectionDetailEditor(player, edit.sectionId, edit.page);
            case ITEM_ID, ITEM_MATERIAL, ITEM_AMOUNT, ITEM_SLOT, ITEM_BUY, ITEM_SELL, ITEM_TYPE, ITEM_PAGE, ITEM_ACTION, ITEM_STACK_SIZE, ITEM_STOCK, ITEM_BUY_LIMIT ->
                    openItemEditor(player, edit.sectionId, updatedItemId == null ? edit.itemId : updatedItemId, edit.page, edit.sectionPage);
            case GLOBAL_PRICE -> openGlobalSellPriceList(player, true, edit.page, edit.configPath, sortFromOrdinal(edit.sectionPage));
        }
    }

    private void openAfterPromptCancel(Player player, PromptEdit edit) {
        switch (edit.type) {
            case CONFIG_STRING, CONFIG_DOUBLE, CONFIG_SOUND, CONFIG_GAMEMODE_LIST, MAIN_ROWS -> openSettingsEditor(player);
            case ROTATING_RESET_SECONDS -> openRotatingShopEditor(player);
            case SELL_BOOSTER_START -> openSellBoosterEditor(player);
            case SECTION_SEARCH -> openSectionEditorList(player, edit.page);
            case ROTATING_SECTION_SEARCH -> openRotatingSectionEditorList(player, edit.page);
            case ROTATING_ITEM_SEARCH -> openRotatingItemEditorList(player, edit.sectionId, edit.page, edit.sectionPage);
            case GLOBAL_PRICE_SEARCH -> openGlobalSellPriceList(player, "editor".equals(edit.configPath), edit.page, "", sortFromOrdinal(edit.sectionPage));
            case ITEM_SEARCH -> openItemListEditor(player, edit.sectionId, edit.page, edit.sectionPage);
            case NEW_SECTION_ID -> openSectionEditorList(player, edit.page);
            case SECTION_SIZE, SECTION_SLOT, SECTION_DESCRIPTION -> openSectionDetailEditor(player, edit.sectionId, edit.page);
            case ITEM_ID, ITEM_MATERIAL, ITEM_AMOUNT, ITEM_SLOT, ITEM_BUY, ITEM_SELL, ITEM_TYPE, ITEM_PAGE, ITEM_ACTION, ITEM_STACK_SIZE, ITEM_STOCK, ITEM_BUY_LIMIT ->
                    openItemEditor(player, edit.sectionId, edit.itemId, edit.page, edit.sectionPage);
            case GLOBAL_PRICE -> openGlobalSellPriceList(player, true, edit.page, edit.configPath, sortFromOrdinal(edit.sectionPage));
        }
    }

    private boolean updateMainGuiRows(int rows, Player player) {
        File mainGuiFile = new File(plugin.getDataFolder(), "guis/main.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mainGuiFile);
        yaml.set("rows", rows);

        var writeResult = plugin.getSafeYamlWriter().write(mainGuiFile, yaml);
        if (!writeResult.success()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Failed to save main GUI rows: " + writeResult.error()));
            return false;
        }

        EditorSaveResult saveResult = configSettingSaver.save("gui.main.rows", rows);
        if (!saveResult.successful()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", saveResult.errorMessage()));
            return false;
        }
        return true;
    }

    private boolean updateSectionField(String sectionId, String path, Object value, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        yaml.set(path, value);

        return persistSectionYaml(sectionId, yaml, player, "Failed to save section.");
    }

    private List<String> parseDescriptionInput(String input) {
        if (input.equalsIgnoreCase("clear") || input.equalsIgnoreCase("none") || input.equals("-")) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (String part : input.split("\\|")) {
            String line = part.trim();
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private boolean updateItemField(String sectionId, String itemId, String field, Object value, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
            return false;
        }

        yaml.set(itemPath + "." + field, value);
        return persistSectionYaml(sectionId, yaml, player, "Failed to save product.");
    }

    private boolean updateItemAction(String sectionId, String itemId, ShopItemType type, String input, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
            return false;
        }

        switch (type) {
            case PERMISSION -> yaml.set(itemPath + ".permission-node", input.trim());
            case COMMAND -> yaml.set(itemPath + ".commands", parseDescriptionInput(input));
            case ENCHANTMENT -> {
                String[] parts = input.split("[:;, ]+", 2);
                yaml.set(itemPath + ".enchantment", parts[0].trim());
                if (parts.length > 1) {
                    try {
                        yaml.set(itemPath + ".enchantment-level", Math.max(1, Integer.parseInt(parts[1].trim())));
                    } catch (NumberFormatException ignored) {
                        yaml.set(itemPath + ".enchantment-level", 1);
                    }
                }
            }
            case ITEM, DUMMY -> {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "This item type has no action data."));
                return false;
            }
        }

        return persistSectionYaml(sectionId, yaml, player, "Failed to save product action.");
    }

    private boolean updateItemMaterial(String sectionId, String itemId, Material material, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
            return false;
        }

        yaml.set(itemPath + ".material", material.name());
        int maxStack = Math.max(1, Math.min(64, material.getMaxStackSize()));
        Integer configuredStackSize = yaml.contains(itemPath + ".stack-size") ? yaml.getInt(itemPath + ".stack-size") : null;
        if (configuredStackSize != null && configuredStackSize > maxStack) {
            yaml.set(itemPath + ".stack-size", maxStack);
            configuredStackSize = maxStack;
        }
        int effectiveStackSize = configuredStackSize == null ? maxStack : configuredStackSize;
        if (yaml.getInt(itemPath + ".amount", 1) > effectiveStackSize) {
            yaml.set(itemPath + ".amount", effectiveStackSize);
        }
        return persistSectionYaml(sectionId, yaml, player, "Failed to save product material.");
    }

    private boolean updateItemStackSize(String sectionId, String itemId, int stackSize, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
            return false;
        }

        yaml.set(itemPath + ".stack-size", stackSize);
        int currentAmount = yaml.getInt(itemPath + ".amount", 1);
        if (currentAmount > stackSize) {
            yaml.set(itemPath + ".amount", stackSize);
        }
        return persistSectionYaml(sectionId, yaml, player, "Failed to save product stack cap.");
    }

    private boolean applyItemFromCursor(String sectionId, String itemId, ItemStack cursor, Player player) {
        Optional<ItemStack> cursorItem = CursorItemEditor.cloneItem(cursor);
        if (cursorItem.isEmpty()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Hold an item on your cursor first."));
            return false;
        }
        ItemStack source = cursorItem.get();

        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
            return false;
        }

        if (ShopItemType.fromString(yaml.getString(itemPath + ".type", "item")) == ShopItemType.ENCHANTMENT) {
            yaml.set(itemPath + ".type", "item");
        }
        yaml.set(itemPath + ".enchantment", null);
        yaml.set(itemPath + ".enchantment-level", null);
        yaml.set(itemPath + ".material", source.getType().name());
        ItemStack template = source.clone();
        template.setAmount(1);
        yaml.set(itemPath + ".item-stack", template);
        int maxStack = Math.max(1, Math.min(64, source.getType().getMaxStackSize()));
        Integer configuredStackSize = yaml.contains(itemPath + ".stack-size") ? yaml.getInt(itemPath + ".stack-size") : null;
        if (configuredStackSize != null && configuredStackSize > maxStack) {
            yaml.set(itemPath + ".stack-size", maxStack);
            configuredStackSize = maxStack;
        }
        int effectiveStackSize = configuredStackSize == null ? maxStack : configuredStackSize;
        if (yaml.getInt(itemPath + ".amount", 1) > effectiveStackSize) {
            yaml.set(itemPath + ".amount", effectiveStackSize);
        }

        yaml.set(itemPath + ".display-name", null);
        yaml.set(itemPath + ".lore", null);
        yaml.set(itemPath + ".custom-model-data", null);
        yaml.set(itemPath + ".enchants", null);
        ItemMeta meta = source.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                yaml.set(itemPath + ".display-name", meta.getDisplayName());
            }
            if (meta.hasLore() && meta.getLore() != null) {
                yaml.set(itemPath + ".lore", meta.getLore());
            }
            if (meta.hasCustomModelData()) {
                yaml.set(itemPath + ".custom-model-data", meta.getCustomModelData());
            }
            if (!meta.getEnchants().isEmpty()) {
                Map<String, Integer> enchants = new HashMap<>();
                for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                    enchants.put(enchant.getKey().getKey().toString(), enchant.getValue());
                }
                yaml.set(itemPath + ".enchants", enchants);
            }
        }

        return persistSectionYaml(sectionId, yaml, player, "Failed to copy product item.");
    }

    private boolean renameItem(String sectionId, String oldId, String newId, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String oldPath = "items." + oldId;
        String newPath = "items." + newId;

        if (!yaml.contains(oldPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            return false;
        }

        if (!oldId.equalsIgnoreCase(newId) && yaml.contains(newPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Another product already has that id."));
            return false;
        }

        Object data = yaml.get(oldPath);
        yaml.set(oldPath, null);
        yaml.set(newPath, data);

        return persistSectionYaml(sectionId, yaml, player, "Failed to save product id.");
    }

    private boolean removeItem(String sectionId, String itemId, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            return false;
        }

        yaml.set(itemPath, null);
        return persistSectionYaml(sectionId, yaml, player, "Failed to remove product.");
    }

    private boolean removeSection(String sectionId, Player player) {
        ShopManager.DeleteResult result = plugin.getShopManager().deleteSection(sectionId);
        if (!result.success()) {
            String reason = result.error() == null || result.error().isBlank() ? "Failed to remove section." : result.error();
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", reason));
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Editor section delete failed for " + sectionId + ": " + reason);
            }
            return false;
        }

        plugin.reloadAll();
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().info("Deleted shop section: " + sectionId);
        }
        return true;
    }

    private void addProductFromCursor(Player player, ItemStack cursorItem, String sectionId, int page, int sectionPage) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            return;
        }

        Optional<ItemStack> cursorClone = CursorItemEditor.cloneItem(cursorItem);
        if (cursorClone.isEmpty()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Hold an item on your cursor first."));
            return;
        }
        ItemStack hand = cursorClone.get();

        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) {
            items = yaml.createSection("items");
        }

        String baseId = normalizeItemId(hand.getType().name().toLowerCase(Locale.ROOT));
        String itemId = makeUniqueItemId(items, baseId);

        int slot = firstFreeSlot(section, null);
        if (slot < 0) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "No free slot available in this section GUI."));
            return;
        }

        String path = "items." + itemId;
        yaml.set(path + ".material", hand.getType().name());
        ItemStack template = hand.clone();
        template.setAmount(1);
        yaml.set(path + ".item-stack", template);
        yaml.set(path + ".slot", slot);
        yaml.set(path + ".amount", Math.clamp(hand.getAmount(), 1, hand.getType().getMaxStackSize()));
        yaml.set(path + ".buy-price", -1D);
        yaml.set(path + ".sell-price", -1D);
        yaml.set(path + ".lore", List.of());
        if (hand.hasItemMeta()) {
            ItemMeta meta = hand.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    yaml.set(path + ".display-name", meta.getDisplayName());
                }
                if (meta.hasLore() && meta.getLore() != null) {
                    yaml.set(path + ".lore", meta.getLore());
                }
                if (meta.hasCustomModelData()) {
                    yaml.set(path + ".custom-model-data", meta.getCustomModelData());
                }
                if (!meta.getEnchants().isEmpty()) {
                    Map<String, Integer> enchants = new HashMap<>();
                    for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                        enchants.put(enchant.getKey().getKey().toString(), enchant.getValue());
                    }
                    yaml.set(path + ".enchants", enchants);
                }
            }
        }

        if (persistSectionYaml(sectionId, yaml, player, "Failed to add product.")) {
            plugin.getMessages().send(player, "editor-saved");
            plugin.getEditorSounds().add(player);
            openItemEditor(player, sectionId, itemId, page, sectionPage);
        }
    }

    private String makeUniqueItemId(ConfigurationSection items, String baseId) {
        if (!items.contains(baseId)) {
            return baseId;
        }

        int index = 2;
        while (items.contains(baseId + "_" + index)) {
            index++;
        }
        return baseId + "_" + index;
    }

    private int firstFreeSlot(ShopSection section, String exceptItemId) {
        for (int slot = 0; slot < section.size(); slot++) {
            if (isReservedSectionSlot(section, slot)) {
                continue;
            }
            if (!isSlotTakenByOther(section, exceptItemId, slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isSlotTakenByOther(ShopSection section, String exceptItemId, int slot) {
        for (ShopItem item : section.items()) {
            if (item.slot() != slot) {
                continue;
            }
            if (exceptItemId != null && item.id().equalsIgnoreCase(exceptItemId)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isReservedSectionSlot(ShopSection section, int slot) {
        return slot == section.size() - 9 || slot == section.size() - 5 || slot == section.size() - 1;
    }

    private ShopItem findItem(String sectionId, String itemId) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            return null;
        }
        return section.items().stream().filter(item -> item.id().equalsIgnoreCase(itemId)).findFirst().orElse(null);
    }

    private Double parsePriceInput(String input, Player player) {
        if (input.equalsIgnoreCase("disable") || input.equalsIgnoreCase("disabled") || input.equals("-1")) {
            return -1D;
        }

        OptionalDouble parsed = LargeNumberParser.parseDouble(input);
        if (parsed.isEmpty()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Price must be a number, 50k/1.5m, or -1."));
            return null;
        }

        double value = parsed.getAsDouble();
        if (value < 0D || !Double.isFinite(value)) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Use -1 to disable, or 0+ for a price."));
            return null;
        }

        return value;
    }

    private Integer parseOptionalNonNegativeInt(String input, Player player, String label) {
        if (isDisableInput(input)) {
            return null;
        }
        try {
            int value = Integer.parseInt(input);
            if (value < 0) {
                plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", label + " must be 0+ or -1."));
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", label + " must be a number."));
            return null;
        }
    }

    private boolean isDisableInput(String input) {
        return input.equalsIgnoreCase("disable") || input.equalsIgnoreCase("disabled") || input.equalsIgnoreCase("clear") || input.equals("-1");
    }

    private boolean createSection(String input, Player player) {
        String sectionId = normalizeItemId(input);
        if (sectionId.isBlank()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section id is empty."));
            return false;
        }

        if (plugin.getShopManager().getSection(sectionId).isPresent()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section id already exists."));
            return false;
        }

        int preferredSlot = 10;
        for (ShopSection existing : plugin.getShopManager().getSectionsOrdered()) {
            preferredSlot = Math.max(preferredSlot, existing.slot() + 1);
        }
        if (preferredSlot > 53) {
            preferredSlot = 53;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", sectionId);
        yaml.set("title", "&8" + sectionId);
        yaml.set("size", 54);
        yaml.set("icon", "CHEST");
        yaml.set("description", List.of("&#a7b8b0New shop section."));
        yaml.set("enabled", true);
        yaml.set("slot", preferredSlot);
        yaml.createSection("items");

        return persistSectionYaml(sectionId, yaml, player, "Failed to create section.");
    }

    private boolean toggleSectionEnabled(String sectionId, Player player) {
        File sectionFile = plugin.getShopManager().getSectionFile(sectionId);
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        yaml.set("enabled", !yaml.getBoolean("enabled", true));
        return persistSectionYaml(sectionId, yaml, player, "Failed to save section enabled state.");
    }

    private boolean hasCursorItem(InventoryClickEvent event) {
        return CursorItemEditor.cloneItem(event.getCursor()).isPresent();
    }

    private boolean applySectionIconFromCursor(String sectionId, ItemStack cursor, Player player) {
        Optional<ItemStack> cursorItem = CursorItemEditor.cloneItem(cursor);
        if (cursorItem.isEmpty()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Hold an item on your cursor first."));
            return false;
        }
        ItemStack source = cursorItem.get();

        File sectionFile = plugin.getShopManager().getSectionFile(sectionId);
        if (!sectionFile.exists()) {
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        yaml.set("icon", source.getType().name());
        yaml.set("icon-item", buildSectionIconItem(source));
        return persistSectionYaml(sectionId, yaml, player, "Failed to save section icon.");
    }

    private ItemStack buildSectionIconItem(ItemStack source) {
        ItemStack out = new ItemStack(source.getType(), 1);
        ItemMeta sourceMeta = source.getItemMeta();
        ItemMeta targetMeta = out.getItemMeta();
        if (sourceMeta != null && targetMeta != null) {
            if (sourceMeta.hasDisplayName()) {
                targetMeta.setDisplayName(sourceMeta.getDisplayName());
            }
            if (sourceMeta.hasCustomModelData()) {
                targetMeta.setCustomModelData(sourceMeta.getCustomModelData());
            }
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : sourceMeta.getEnchants().entrySet()) {
                targetMeta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            targetMeta.setLore(null);
            out.setItemMeta(targetMeta);
        }
        return out;
    }

    private boolean persistSectionYaml(String sectionId, YamlConfiguration yaml, Player player, String fallbackReason) {
        var result = plugin.getShopManager().saveSectionYaml(sectionId, yaml);
        if (!result.success()) {
            String reason = result.error() == null || result.error().isBlank() ? fallbackReason : result.error();
            plugin.getMessages().send(player, "editor-invalid", Map.of("{reason}", reason));
            plugin.getEditorSounds().error(player);
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Editor save failed for section " + sectionId + ": " + reason);
            }
            return false;
        }

        plugin.reloadAll();
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().info("Saved section YAML: " + sectionId);
        }
        return true;
    }

    private void returnUnsoldItems(Player player, Inventory inventory) {
        int inputEnd = inventory.getSize() - 9;
        SellGuiHolder holder = inventory.getHolder() instanceof SellGuiHolder sellGuiHolder ? sellGuiHolder : null;
        for (int slot = 0; slot < inputEnd; slot++) {
            if (holder != null && holder.decorationSlots.contains(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            plugin.getCore().inventoryDeposits().deposit(player, item, player.getLocation(), OverflowPolicy.DROP_OVERFLOW, false);
            inventory.setItem(slot, null);
        }
    }

    private boolean hasSellGuiItems(Inventory inventory) {
        int inputEnd = inventory.getSize() - 9;
        SellGuiHolder holder = inventory.getHolder() instanceof SellGuiHolder sellGuiHolder ? sellGuiHolder : null;
        for (int slot = 0; slot < inputEnd; slot++) {
            if (holder != null && holder.decorationSlots.contains(slot)) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private void startPrompt(Player player, PromptEdit edit, String message) {
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().debug("Editor prompt opened by " + player.getName() + ": " + promptLogContext(edit));
        }
        TextDialogRequest request = buildDialogInputRequest(edit, message);
        if (!canUseNativeDialogs(player)) {
            player.closeInventory();
            startChatPrompt(player, edit, message);
            return;
        }

        runSyncIfEnabled(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (openNativeTextInputFromInventory(player, request, input -> handlePromptInput(player, edit, input), () -> handlePromptCancel(player, edit))) {
                return;
            }
            warnNativeDialogFallback(player);
            startChatPrompt(player, edit, message);
        });
    }

    private void startChatPrompt(Player player, PromptEdit edit, String message) {
        String promptLine = plugin.getMessages().render("editor-prompt", "", Map.of("{prompt}", message));
        chatPrompts.openRaw(player, List.of(promptLine), input -> handlePromptInput(player, edit, input), () -> handlePromptCancel(player, edit));
    }

    public void close() {
        chatPrompts.close();
    }

    private boolean canUseNativeDialogs(Player player) {
        return plugin.getCore() != null && plugin.getCore().nativeDialogs().canUseNativeDialogs(player);
    }

    private boolean openNativeTextInputFromInventory(Player player, TextDialogRequest request, java.util.function.Consumer<String> onSubmit, Runnable onCancel) {
        if (plugin.getCore() == null) {
            return false;
        }
        DialogService fallback = new FallbackDialogService(
                plugin.getCore().nativeDialogs(),
                (noticePlayer, noticeRequest, fallbackClose) -> {
                },
                (confirmPlayer, confirmRequest, fallbackConfirm, fallbackCancel) -> {
                },
                (inputPlayer, inputRequest, fallbackSubmit, fallbackCancel) -> {
                }
        );
        return EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.getCore().inventoryCloseSuppressor(),
                plugin.getCore().createDialogService(fallback),
                player,
                request,
                onSubmit,
                onCancel
        );
    }

    private void warnNativeDialogFallback(Player player) {
        if (!plugin.getFoConfig().isNativeDialogsEnabled() || !plugin.getFoConfig().isNativeDialogsWarnOnFallback()) {
            return;
        }
        if (!player.hasPermission("foshop.admin") || !nativeDialogFallbackWarnings.add(player.getUniqueId())) {
            return;
        }
        plugin.getMessages().send(player, "native-dialogs-fallback");
    }

    private TextDialogRequest buildDialogInputRequest(PromptEdit edit, String prompt) {
        String current = promptCurrentValue(edit);
        String type = promptTypeLabel(edit);
        List<String> body = current.isBlank()
                ? List.of(prompt)
                : List.of(prompt, "&#a7b8b0Current: &#ffffff" + current);
        int maxLength = maxDialogLength(edit);

        if (isSearchPrompt(edit.type)) {
            return new TextDialogRequest(
                    "Search",
                    body,
                    "Search",
                    "",
                    "Search...",
                    DialogButton.search(),
                    DialogButton.cancel(),
                    320,
                    300,
                    64,
                    true,
                    true,
                    false
            );
        }

        return new TextDialogRequest(
                dialogTitle(edit),
                body,
                type,
                current,
                dialogPlaceholder(edit),
                DialogButton.save(),
                DialogButton.cancel(),
                340,
                320,
                maxLength,
                true,
                true,
                false
        );
    }

    private boolean isSearchPrompt(PromptType type) {
        return switch (type) {
            case SECTION_SEARCH, ITEM_SEARCH, ROTATING_SECTION_SEARCH, ROTATING_ITEM_SEARCH, GLOBAL_PRICE_SEARCH -> true;
            default -> false;
        };
    }

    private String dialogTitle(PromptEdit edit) {
        if (isNumberPrompt(edit.type)) {
            return "&#03fc88Number Input";
        }
        if (isCommandPrompt(edit)) {
            return "&#03fc88Command Input";
        }
        return "&#03fc88Text Input";
    }

    private boolean isNumberPrompt(PromptType type) {
        return switch (type) {
            case CONFIG_DOUBLE, MAIN_ROWS, SECTION_SIZE, SECTION_SLOT, ITEM_AMOUNT, ITEM_SLOT, ITEM_BUY, ITEM_SELL,
                    ITEM_PAGE, ITEM_STOCK, ITEM_BUY_LIMIT, ITEM_STACK_SIZE -> true;
            default -> false;
        };
    }

    private boolean isCommandPrompt(PromptEdit edit) {
        if (edit.type != PromptType.ITEM_ACTION) {
            return false;
        }
        ShopItem item = findItem(edit.sectionId, edit.itemId);
        return item != null && item.type() == ShopItemType.COMMAND;
    }

    private String dialogPlaceholder(PromptEdit edit) {
        return switch (edit.type) {
            case CONFIG_DOUBLE -> "1.0";
            case CONFIG_SOUND -> "BLOCK_CHEST_OPEN";
            case CONFIG_GAMEMODE_LIST -> "SURVIVAL | ADVENTURE";
            case ROTATING_RESET_SECONDS -> "1d";
            case SELL_BOOSTER_START -> edit.configPath == null || edit.configPath.equals(SellBoosterScope.GLOBAL.key()) ? "2 1h" : "Target 2 1h";
            case MAIN_ROWS -> "4";
            case SECTION_SEARCH, ROTATING_SECTION_SEARCH -> "ores";
            case ROTATING_ITEM_SEARCH, ITEM_SEARCH, GLOBAL_PRICE_SEARCH -> "diamond";
            case NEW_SECTION_ID -> "ores";
            case SECTION_SIZE -> "54";
            case SECTION_SLOT -> "10";
            case SECTION_DESCRIPTION -> "Line one | Line two";
            case ITEM_ID -> "diamond";
            case ITEM_MATERIAL -> "DIAMOND";
            case ITEM_AMOUNT -> "16";
            case ITEM_SLOT -> "13";
            case ITEM_BUY, ITEM_SELL -> "50k";
            case GLOBAL_PRICE -> "1.0";
            case ITEM_TYPE -> "item";
            case ITEM_PAGE -> "1";
            case ITEM_ACTION -> "say Hello | give {player} diamond 1";
            case ITEM_STOCK, ITEM_BUY_LIMIT -> "-1";
            case ITEM_STACK_SIZE -> "64";
            case CONFIG_STRING -> "Text...";
        };
    }

    private int maxDialogLength(PromptEdit edit) {
        return switch (edit.type) {
            case ITEM_ACTION -> 1024;
            case SECTION_DESCRIPTION, CONFIG_GAMEMODE_LIST, SELL_BOOSTER_START -> 512;
            case GLOBAL_PRICE_SEARCH -> 64;
            default -> 256;
        };
    }

    private String promptCurrentValue(PromptEdit edit) {
        ShopItem item = edit.itemId == null ? null : findItem(edit.sectionId, edit.itemId);
        ShopSection section = edit.sectionId == null ? null : plugin.getShopManager().getSection(edit.sectionId).orElse(null);

        return switch (edit.type) {
            case CONFIG_STRING -> plugin.getConfig().getString(edit.configPath, "");
            case CONFIG_DOUBLE -> String.valueOf(effectiveDoubleSetting(edit.configPath, 0D));
            case CONFIG_SOUND -> effectiveStringSetting(edit.configPath);
            case CONFIG_GAMEMODE_LIST -> String.join(" | ", effectiveStringListSetting(edit.configPath));
            case ROTATING_RESET_SECONDS -> String.valueOf(plugin.getRotatingShopService().resetIntervalSeconds());
            case SELL_BOOSTER_START -> "";
            case MAIN_ROWS -> String.valueOf(plugin.getFoConfig().getMainRows());
            case SECTION_SEARCH, ITEM_SEARCH, ROTATING_SECTION_SEARCH, ROTATING_ITEM_SEARCH, GLOBAL_PRICE_SEARCH -> "";
            case NEW_SECTION_ID -> "";
            case SECTION_SIZE -> section == null ? "" : String.valueOf(section.size());
            case SECTION_SLOT -> section == null ? "" : String.valueOf(section.slot());
            case SECTION_DESCRIPTION -> section == null ? "" : String.join(" | ", section.description());
            case ITEM_ID -> edit.itemId == null ? "" : edit.itemId;
            case ITEM_MATERIAL -> item == null ? "" : item.material().name();
            case ITEM_AMOUNT -> item == null ? "" : String.valueOf(item.amount());
            case ITEM_SLOT -> item == null ? "" : String.valueOf(item.slot());
            case ITEM_BUY -> item == null ? "" : pricePromptValue(item.buyPrice());
            case ITEM_SELL -> item == null ? "" : pricePromptValue(item.sellPrice());
            case ITEM_TYPE -> item == null ? "" : item.type().name().toLowerCase(Locale.ROOT);
            case ITEM_PAGE -> item == null ? "" : String.valueOf(item.page() + 1);
            case ITEM_ACTION -> item == null ? "" : actionPromptValue(item);
            case ITEM_STOCK -> item == null || item.stock() == null ? "-1" : String.valueOf(item.stock());
            case ITEM_BUY_LIMIT -> item == null || item.buyLimit() == null ? "-1" : String.valueOf(item.buyLimit());
            case ITEM_STACK_SIZE -> item == null ? "" : String.valueOf(item.effectiveStackSize());
            case GLOBAL_PRICE -> {
                Optional<GlobalSellPriceService.GlobalEnchantmentEntry> enchantmentEntry = plugin.getGlobalSellPriceService() == null
                        ? Optional.empty()
                        : plugin.getGlobalSellPriceService().enchantmentEntry(edit.itemId);
                if (enchantmentEntry.isPresent()) {
                    yield Double.toString(enchantmentEntry.get().price());
                }
                Optional<GlobalSellPriceService.GlobalPotionEntry> potionEntry = plugin.getGlobalSellPriceService() == null
                        ? Optional.empty()
                        : plugin.getGlobalSellPriceService().potionEntry(edit.itemId);
                if (potionEntry.isPresent()) {
                    yield Double.toString(potionEntry.get().price());
                }
                Material material = Material.matchMaterial(edit.itemId == null ? "" : edit.itemId);
                yield material == null || plugin.getGlobalSellPriceService() == null
                        ? ""
                        : Double.toString(plugin.getGlobalSellPriceService().price(material));
            }
        };
    }

    private String promptTypeLabel(PromptEdit edit) {
        return switch (edit.type) {
            case CONFIG_STRING -> "Text";
            case CONFIG_DOUBLE -> "Number";
            case CONFIG_SOUND -> "Sound";
            case CONFIG_GAMEMODE_LIST -> "Gamemodes";
            case ROTATING_RESET_SECONDS -> "Reset Timer";
            case SELL_BOOSTER_START -> "Sell Booster";
            case MAIN_ROWS -> "Main Shop Rows";
            case SECTION_SEARCH -> "Section Search";
            case ROTATING_SECTION_SEARCH -> "Rotating Search";
            case ROTATING_ITEM_SEARCH -> "Rotating Items";
            case GLOBAL_PRICE_SEARCH -> "Worth Search";
            case ITEM_SEARCH -> "Product Search";
            case NEW_SECTION_ID -> "Section ID";
            case SECTION_SIZE -> "Section Size";
            case SECTION_SLOT -> "Main Menu Slot";
            case SECTION_DESCRIPTION -> "Description";
            case ITEM_ID -> "Product ID";
            case ITEM_MATERIAL -> "Material";
            case ITEM_AMOUNT -> "Amount";
            case ITEM_SLOT -> "GUI Slot";
            case ITEM_BUY -> "Buy Price";
            case ITEM_SELL -> "Sell Price";
            case ITEM_TYPE -> "Product Type";
            case ITEM_PAGE -> "Page";
            case ITEM_ACTION -> "Action Data";
            case ITEM_STOCK -> "Stock";
            case ITEM_BUY_LIMIT -> "Buy Limit";
            case ITEM_STACK_SIZE -> "Stack Cap";
            case GLOBAL_PRICE -> "Global Price";
        };
    }

    private WorthSort sortFromOrdinal(int ordinal) {
        WorthSort[] values = WorthSort.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return WorthSort.NAME;
        }
        return values[ordinal];
    }

    private WorthSort sortFromValue(String value) {
        for (WorthSort sort : WorthSort.values()) {
            if (sort.name().equalsIgnoreCase(value)) {
                return sort;
            }
        }
        return WorthSort.NAME;
    }

    private String pricePromptValue(double price) {
        return price < 0D ? "-1" : Double.toString(price);
    }

    private String actionPromptValue(ShopItem item) {
        return switch (item.type()) {
            case PERMISSION -> item.permission() == null ? "" : item.permission();
            case COMMAND -> String.join(" | ", item.commands());
            case ENCHANTMENT -> {
                String enchantment = item.enchantment() == null ? "" : item.enchantment();
                yield enchantment.isBlank() ? "" : enchantment + ":" + Math.max(1, item.enchantmentLevel());
            }
            case ITEM, DUMMY -> "";
        };
    }

    private String promptLogContext(PromptEdit edit) {
        return "type=" + edit.type
                + ", section=" + (edit.sectionId == null ? "-" : edit.sectionId)
                + ", item=" + (edit.itemId == null ? "-" : edit.itemId);
    }

    private void fillBackground(Inventory inventory) {
        fillBackground(inventory, null);
    }

    private void fillBackground(Inventory inventory, String guiFile) {
        ItemStack filler = guiFile == null ? EditorItemFactory.filler() : createFillerItem(guiFile);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack createFillerItem(String guiFile) {
        Material material = materialFromGui(guiFile, "filler.material", Material.GRAY_STAINED_GLASS_PANE);
        int amount = Math.clamp(plugin.getFoConfig().guiInt(guiFile, "filler.amount", 1), 1, material.getMaxStackSize());
        ItemStack item = createItem(material, plugin.getFoConfig().guiString(guiFile, "filler.name", " "),
                plugin.getFoConfig().guiStringList(guiFile, "filler.lore", List.of()));
        item.setAmount(amount);
        int customModelData = plugin.getFoConfig().guiInt(guiFile, "filler.custom-model-data", -1);
        if (customModelData >= 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack createButtonItem(GuiButton button) {
        return createButtonItem(button, button.name(), button.lore());
    }

    private ItemStack createButtonItem(Player player, GuiButton button) {
        return createButtonItem(player, button, button.name(), button.lore());
    }

    private ItemStack createButtonItem(GuiButton button, String displayName, List<String> lore) {
        ItemStack item = createItem(button.material(), displayName, lore);
        item.setAmount(Math.clamp(button.amount(), 1, button.material().getMaxStackSize()));
        if (button.customModelData() >= 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(button.customModelData());
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack createButtonItem(Player player, GuiButton button, String displayName, List<String> lore) {
        ItemStack item = EditorItemFactory.item(player, button.material(), displayName, lore);
        item.setAmount(Math.clamp(button.amount(), 1, button.material().getMaxStackSize()));
        if (button.customModelData() >= 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(button.customModelData());
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private Material matchMaterial(String raw, Material fallback, String context) {
        Material material = MaterialTypes.match(raw == null ? fallback.name() : raw);
        if (material == null || material == Material.AIR) {
            logGuiWarning("gui", "Invalid material '" + raw + "' at " + context + "; using " + fallback.name() + ".");
            return fallback;
        }
        return material;
    }

    private void logGuiWarning(String file, String message) {
        plugin.getLogger().warning(message);
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().warn("[" + file + "] " + message);
        }
    }

    private ItemStack createItem(Material material, String displayName, Collection<String> lore) {
        ItemStack item = EditorItemFactory.templateItem(material, Text.colorize(displayName),
                lore == null ? List.of() : lore.stream().map(Text::colorize).toList());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Player player, Material material, String label,
                             Collection<String> information, String clickAction) {
        return button(player, material, FoStyle.THEME, label, information, clickAction);
    }

    private ItemStack button(Player player, Material material, String nameColor, String label,
                             Collection<String> information, String clickAction) {
        return EditorItemFactory.button(player, material, nameColor, FoText.plain(label),
                information == null ? List.of() : List.copyOf(information), clickAction);
    }

    private String prettify(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String word : value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private String normalizeItemId(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        normalized = normalized.replaceAll("[^a-z0-9_\\-]", "");
        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40);
        }
        return normalized;
    }

    private String formatPrice(double price) {
        if (price < 0D) {
            return "disabled";
        }
        return plugin.getEconomyService().format(price);
    }

    private String formatGlobalPrice(double price) {
        return WorthPriceFormatter.format(plugin.getEconomyService(), price);
    }

    private List<GlobalPriceListEntry> globalPriceEntries(String search, WorthSort sort, boolean editor) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<GlobalPriceListEntry> entries = new ArrayList<>();
        plugin.getGlobalSellPriceService().entries().forEach(entry -> entries.add(GlobalPriceListEntry.material(entry)));
        plugin.getGlobalSellPriceService().enchantmentEntries().forEach(entry -> entries.add(GlobalPriceListEntry.enchantment(entry)));
        plugin.getGlobalSellPriceService().potionEntries().forEach(entry -> entries.add(GlobalPriceListEntry.potion(entry)));

        Map<GlobalPriceListEntry, Double> effectivePrices = new HashMap<>(entries.size());
        entries.forEach(entry -> effectivePrices.put(entry, globalListPrice(entry)));
        Comparator<GlobalPriceListEntry> comparator = switch (sort == null ? WorthSort.NAME : sort) {
            case NAME -> Comparator.comparing(GlobalPriceListEntry::sortName);
            case PRICE -> Comparator
                    .comparingDouble((GlobalPriceListEntry entry) -> effectivePrices.get(entry))
                    .reversed()
                    .thenComparing(GlobalPriceListEntry::sortName);
        };

        return entries.stream()
                .filter(entry -> editor || effectivePrices.get(entry) > 0D)
                .filter(entry -> query.isBlank()
                        || entry.searchName().contains(query)
                        || prettify(entry.sortName()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(comparator)
                .toList();
    }

    private double globalListPrice(GlobalPriceListEntry entry) {
        return effectiveGlobalPrice(globalPriceBaseStack(entry));
    }

    private double effectiveGlobalPrice(ItemStack stack) {
        if (plugin.getShopManager().hasMatchingSellOffer(stack)) {
            return plugin.getShopManager().getBaseSellPrice(stack);
        }
        return plugin.getGlobalSellPriceService().price(stack);
    }

    private ItemStack globalPriceBaseStack(GlobalPriceListEntry entry) {
        if (entry.isEnchantment()) {
            return globalEnchantmentBaseStack(entry.enchantmentEntry());
        }
        if (entry.isPotion()) {
            return globalPotionBaseStack(entry.potionEntry());
        }
        return new ItemStack(entry.material());
    }

    private ItemStack globalEnchantmentBaseStack(GlobalSellPriceService.GlobalEnchantmentEntry entry) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        Enchantment enchantment = ItemKeys.enchantment(entry.enchantmentKey());
        if (enchantment != null && meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(enchantment, Math.max(1, entry.level()), true);
            stack.setItemMeta(storageMeta);
        }
        return stack;
    }

    private ItemStack globalPotionBaseStack(GlobalSellPriceService.GlobalPotionEntry entry) {
        ItemStack stack = new ItemStack(Material.POTION);
        if (stack.getItemMeta() instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(entry.potionType());
            stack.setItemMeta(potionMeta);
        }
        return stack;
    }

    private ItemStack globalPriceItem(GlobalPriceListEntry entry, boolean editor) {
        if (entry.isEnchantment()) {
            return globalEnchantmentPriceItem(entry.enchantmentEntry(), editor);
        }
        return entry.isPotion() ? globalPotionPriceItem(entry.potionEntry(), editor) : globalPriceItem(entry.materialEntry(), editor);
    }

    private ItemStack globalPriceItem(GlobalSellPriceService.GlobalSellPriceEntry entry, boolean editor) {
        ItemStack stack = new ItemStack(entry.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        boolean shopOverride = plugin.getShopManager().hasMatchingSellOffer(stack);
        double effectivePrice = effectiveGlobalPrice(stack);
        meta.setDisplayName(Text.colorize("&f" + prettify(entry.material().name())));
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&#ffffffWorth: &#03fc88" + formatGlobalPrice(effectivePrice)));
        if (editor) {
            lore.add(Text.colorize("&#ffffffSource: " + (shopOverride ? "&#3ecf8eShop override" : "&#a7b8b0Global file")));
            if (shopOverride) {
                lore.add(Text.colorize("&#a7b8b0Global file price: &#03fc88" + formatGlobalPrice(entry.price())));
            }
            lore.add(Text.colorize("&#ffffffGlobal fallback: " + (entry.enabled() ? "&#3ecf8eEnabled" : "&#ff5d73Blacklisted")));
            lore.add(Text.colorize("&#ffffffGlobal rotating: " + (entry.enabled() && entry.rotatingShop() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled")));
            lore.add(Text.colorize(""));
            lore.add(Text.colorize("&#a7b8b0Left-click: edit price."));
            lore.add(Text.colorize("&#a7b8b0Shift-left: toggle blacklist."));
            lore.add(Text.colorize("&#a7b8b0Right-click: toggle rotating."));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack globalEnchantmentPriceItem(GlobalSellPriceService.GlobalEnchantmentEntry entry, boolean editor) {
        ItemStack stack = globalEnchantmentBaseStack(entry);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        boolean shopOverride = plugin.getShopManager().hasMatchingSellOffer(stack);
        double effectivePrice = effectiveGlobalPrice(stack);
        meta.setDisplayName(Text.colorize("&f" + enchantmentDisplayName(entry)));
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&#ffffffWorth: &#03fc88" + formatGlobalPrice(effectivePrice)));
        if (editor) {
            lore.add(0, Text.colorize("&#ffffffEnchantment: &#03fc88" + entry.enchantmentKey()));
            lore.add(Text.colorize("&#ffffffLevel: &#03fc88" + entry.level()));
            lore.add(Text.colorize("&#ffffffSource: " + (shopOverride ? "&#3ecf8eShop override" : "&#a7b8b0Global file")));
            if (shopOverride) {
                lore.add(Text.colorize("&#a7b8b0Global file price: &#03fc88" + formatGlobalPrice(entry.price())));
            }
            lore.add(Text.colorize("&#ffffffGlobal fallback: " + (entry.enabled() ? "&#3ecf8eEnabled" : "&#ff5d73Blacklisted")));
            lore.add(Text.colorize("&#ffffffGlobal rotating: " + (entry.enabled() && entry.rotatingShop() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled")));
            lore.add(Text.colorize(""));
            lore.add(Text.colorize("&#a7b8b0Left-click: edit price."));
            lore.add(Text.colorize("&#a7b8b0Shift-left: toggle blacklist."));
            lore.add(Text.colorize("&#a7b8b0Right-click: toggle rotating."));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack globalPotionPriceItem(GlobalSellPriceService.GlobalPotionEntry entry, boolean editor) {
        ItemStack stack = globalPotionBaseStack(entry);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        boolean shopOverride = plugin.getShopManager().hasMatchingSellOffer(stack);
        double effectivePrice = effectiveGlobalPrice(stack);
        meta.setDisplayName(Text.colorize("&f" + prettify(entry.potionType().name()) + " Potion"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&#ffffffWorth: &#03fc88" + formatGlobalPrice(effectivePrice)));
        if (editor) {
            lore.add(0, Text.colorize("&#ffffffPotion: &#03fc88" + entry.potionType().name()));
            lore.add(Text.colorize("&#ffffffSource: " + (shopOverride ? "&#3ecf8eShop override" : "&#a7b8b0Global file")));
            if (shopOverride) {
                lore.add(Text.colorize("&#a7b8b0Global file price: &#03fc88" + formatGlobalPrice(entry.price())));
            }
            lore.add(Text.colorize("&#ffffffGlobal fallback: " + (entry.enabled() ? "&#3ecf8eEnabled" : "&#ff5d73Blacklisted")));
            lore.add(Text.colorize("&#ffffffGlobal rotating: " + (entry.enabled() && entry.rotatingShop() ? "&#3ecf8eEnabled" : "&#ff5d73Disabled")));
            lore.add(Text.colorize(""));
            lore.add(Text.colorize("&#a7b8b0Left-click: edit price."));
            lore.add(Text.colorize("&#a7b8b0Shift-left: toggle blacklist."));
            lore.add(Text.colorize("&#a7b8b0Right-click: toggle rotating."));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private String enchantmentDisplayName(GlobalSellPriceService.GlobalEnchantmentEntry entry) {
        return prettify(entry.enchantmentKey()) + " " + romanNumeral(entry.level());
    }

    private String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }

    private ItemStack createShopDisplayItem(Player player, String sectionId, ShopItem item, int amount) {
        ItemStack stack = createPurchaseStack(item, Math.clamp(amount, 1, item.effectiveStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (item.displayName() != null && !item.displayName().isBlank()) {
                meta.setDisplayName(Text.colorize(applyTextPlaceholders(player, sectionId, item, amount, item.displayName())));
            }
            List<String> lore = new ArrayList<>();
            for (String line : item.lore()) {
                lore.add(Text.colorize(applyTextPlaceholders(player, sectionId, item, amount, line)));
            }
            if (item.canBuy()) {
                lore.add(Text.colorize("&#3ecf8eBuy: " + plugin.getEconomyService().format(item.buyPrice())));
            }
            if (item.canSell()) {
                lore.add(Text.colorize("&#ff5d73Sell: " + plugin.getEconomyService().format(item.sellPrice())));
            }
            int stock = plugin.getShopManager().getStock(sectionId, item.id());
            if (stock >= 0) {
                lore.add(Text.colorize("&#a7b8b0Stock: &#03fc88" + stock));
            }
            int limit = plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), sectionId, item);
            if (limit >= 0) {
                lore.add(Text.colorize("&#a7b8b0Limit left: &#03fc88" + limit));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createRotatingDisplayItem(Player player, RotatingShopService.RotatingEntry entry, ShopItem item) {
        ItemStack stack = createShopDisplayItem(player, entry.sectionId(), item, item.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Text.colorize(""));
        }

        List<String> boostLore = plugin.getFoConfig().guiStringList("rotating-shop", "boost-lore", List.of(
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        for (String line : boostLore) {
            lore.add(Text.colorize(formatRotatingLine(line, entry, item)));
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createGlobalRotatingDisplayItem(RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalSellPriceEntry globalEntry) {
        ItemStack stack = new ItemStack(globalEntry.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName(Text.colorize("&#03fc88" + prettify(globalEntry.material().name())));
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&#ff5d73Sell: " + plugin.getEconomyService().format(globalEntry.price())));
        lore.add(Text.colorize(""));
        List<String> boostLore = plugin.getFoConfig().guiStringList("rotating-shop", "boost-lore", List.of(
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        for (String line : boostLore) {
            lore.add(Text.colorize(formatGlobalRotatingLine(line, entry, globalEntry)));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createGlobalEnchantmentRotatingDisplayItem(RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalEnchantmentEntry globalEntry) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        Enchantment enchantment = ItemKeys.enchantment(globalEntry.enchantmentKey());
        if (enchantment != null && meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(enchantment, Math.max(1, globalEntry.level()), true);
            meta = storageMeta;
        }

        meta.setDisplayName(Text.colorize("&#03fc88" + enchantmentDisplayName(globalEntry)));
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&#ff5d73Sell: " + plugin.getEconomyService().format(globalEntry.price())));
        lore.add(Text.colorize(""));
        List<String> boostLore = plugin.getFoConfig().guiStringList("rotating-shop", "boost-lore", List.of(
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        for (String line : boostLore) {
            lore.add(Text.colorize(formatGlobalEnchantmentRotatingLine(line, entry, globalEntry)));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createGlobalPotionRotatingDisplayItem(RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalPotionEntry globalEntry) {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(globalEntry.potionType());
        }

        meta.setDisplayName(Text.colorize("&#03fc88" + prettify(globalEntry.potionType().name()) + " Potion"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.colorize("&#ff5d73Sell: " + plugin.getEconomyService().format(globalEntry.price())));
        lore.add(Text.colorize(""));
        List<String> boostLore = plugin.getFoConfig().guiStringList("rotating-shop", "boost-lore", List.of(
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        for (String line : boostLore) {
            lore.add(Text.colorize(formatGlobalPotionRotatingLine(line, entry, globalEntry)));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private String formatRotatingLine(String line, RotatingShopService.RotatingEntry entry, ShopItem item) {
        String boost = formatBoost(entry.multiplier());
        String baseSell = plugin.getEconomyService().format(item.sellPrice());
        String boostedSell = plugin.getEconomyService().format(item.sellPrice() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", entry.sectionId())
                .replace("{item}", item.id())
                .replace("{boost}", boost)
                .replace("{multiplier}", boost)
                .replace("{base_price}", baseSell)
                .replace("{base_sell}", baseSell)
                .replace("{price}", boostedSell)
                .replace("{boosted_price}", boostedSell)
                .replace("{time}", resetTime)
                .replace("{reset}", resetTime);
    }

    private String formatGlobalRotatingLine(String line, RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalSellPriceEntry globalEntry) {
        String boost = formatBoost(entry.multiplier());
        String baseSell = plugin.getEconomyService().format(globalEntry.price());
        String boostedSell = plugin.getEconomyService().format(globalEntry.price() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", "global")
                .replace("{item}", globalEntry.material().name())
                .replace("{boost}", boost)
                .replace("{multiplier}", boost)
                .replace("{base_price}", baseSell)
                .replace("{base_sell}", baseSell)
                .replace("{price}", boostedSell)
                .replace("{boosted_price}", boostedSell)
                .replace("{time}", resetTime)
                .replace("{reset}", resetTime);
    }

    private String formatGlobalEnchantmentRotatingLine(String line, RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalEnchantmentEntry globalEntry) {
        String boost = formatBoost(entry.multiplier());
        String baseSell = plugin.getEconomyService().format(globalEntry.price());
        String boostedSell = plugin.getEconomyService().format(globalEntry.price() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", "global")
                .replace("{item}", GlobalSellPriceService.enchantmentEntryId(globalEntry.enchantmentKey(), globalEntry.level()))
                .replace("{boost}", boost)
                .replace("{multiplier}", boost)
                .replace("{base_price}", baseSell)
                .replace("{base_sell}", baseSell)
                .replace("{price}", boostedSell)
                .replace("{boosted_price}", boostedSell)
                .replace("{time}", resetTime)
                .replace("{reset}", resetTime);
    }

    private String formatGlobalPotionRotatingLine(String line, RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalPotionEntry globalEntry) {
        String boost = formatBoost(entry.multiplier());
        String baseSell = plugin.getEconomyService().format(globalEntry.price());
        String boostedSell = plugin.getEconomyService().format(globalEntry.price() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", "global")
                .replace("{item}", globalEntry.potionType().name())
                .replace("{boost}", boost)
                .replace("{multiplier}", boost)
                .replace("{base_price}", baseSell)
                .replace("{base_sell}", baseSell)
                .replace("{price}", boostedSell)
                .replace("{boosted_price}", boostedSell)
                .replace("{time}", resetTime)
                .replace("{reset}", resetTime);
    }

    private String formatBoost(double multiplier) {
        return String.format(Locale.US, "%.2f", multiplier);
    }

    private List<ShopItem> rotatingSellableItems(ShopSection section, String search) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return section.items().stream()
                .filter(item -> item.canSell() && item.sellPrice() > 0D)
                .filter(item -> query.isBlank()
                        || item.id().toLowerCase(Locale.ROOT).contains(query)
                        || item.material().name().toLowerCase(Locale.ROOT).contains(query)
                        || (item.displayName() != null && item.displayName().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(Comparator.comparingInt(ShopItem::page).thenComparingInt(ShopItem::slot).thenComparing(ShopItem::id))
                .toList();
    }

    private long countRotatingSellableItems(ShopSection section) {
        return section.items().stream()
                .filter(item -> item.canSell() && item.sellPrice() > 0D)
                .count();
    }

    private long countRotatingParticipatingItems(ShopSection section) {
        return section.items().stream()
                .filter(item -> item.canSell() && item.sellPrice() > 0D)
                .filter(item -> plugin.getRotatingShopService().itemParticipates(section.id(), item.id()))
                .count();
    }

    private String applyTextPlaceholders(Player player, String sectionId, ShopItem item, int amount, String input) {
        String output = CommandPlaceholders.apply(input, Map.of(
                "player", player.getName(),
                "section", sectionId,
                "item", item.id(),
                "amount", String.valueOf(amount),
                "buy_price", plugin.getEconomyService().format(item.buyPrice()),
                "buy", plugin.getEconomyService().format(item.buyPrice()),
                "sell_price", plugin.getEconomyService().format(item.sellPrice()),
                "sell", plugin.getEconomyService().format(item.sellPrice()),
                "stock", String.valueOf(plugin.getShopManager().getStock(sectionId, item.id())),
                "buy_limit", String.valueOf(plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), sectionId, item))
        ));
        return applyPlaceholderApi(player, output);
    }

    private List<String> formatSectionDescription(Player player, ShopSection section) {
        if (section.description().isEmpty()) {
            return null;
        }

        return section.description().stream()
                .map(line -> CommandPlaceholders.apply(line, Map.of(
                        "player", player.getName(),
                        "section", section.id(),
                        "title", section.title()
                )))
                .map(line -> applyPlaceholderApi(player, line))
                .map(Text::colorize)
                .toList();
    }

    private List<String> colorizeLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        return lore.stream().map(Text::colorize).toList();
    }

    private String applyPlaceholderApi(Player player, String input) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return input;
        }
        try {
            Class<?> placeholders = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object parsed = placeholders.getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, input);
            return parsed instanceof String string ? string : input;
        } catch (ReflectiveOperationException exception) {
            return input;
        }
    }

    private ItemStack createPurchaseStack(ShopItem item, int amount) {
        ItemStack stack;
        if (item.itemStack() != null) {
            stack = item.itemStack().clone();
            stack.setAmount(Math.clamp(amount, 1, item.effectiveStackSize()));
        } else if (item.type() == ShopItemType.ENCHANTMENT) {
            stack = new ItemStack(Material.ENCHANTED_BOOK, Math.clamp(amount, 1, item.effectiveStackSize()));
        } else {
            stack = new ItemStack(item.material(), Math.clamp(amount, 1, item.effectiveStackSize()));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            boolean metaChanged = applyShopItemMeta(meta, item);
            if (item.type() == ShopItemType.ENCHANTMENT && meta instanceof EnchantmentStorageMeta storageMeta) {
                Enchantment enchantment = parseEnchantment(item.enchantment());
                if (enchantment != null) {
                    storageMeta.addStoredEnchant(enchantment, Math.max(1, item.enchantmentLevel()), true);
                    meta = storageMeta;
                    metaChanged = true;
                }
            }
            if (metaChanged) {
                stack.setItemMeta(meta);
            }
        }
        applyRawNbt(stack, item.rawNbt());
        return stack;
    }

    private Enchantment parseEnchantment(String key) {
        return ItemKeys.enchantment(key);
    }

    private void applyRawNbt(ItemStack stack, String rawNbt) {
        if (rawNbt == null || rawNbt.isBlank()) {
            return;
        }
        try {
            Bukkit.getUnsafe().getClass()
                    .getMethod("modifyItemStack", ItemStack.class, String.class)
                    .invoke(Bukkit.getUnsafe(), stack, rawNbt);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Raw NBT is only applied on server builds exposing Bukkit's unsafe modifier.
        }
    }

    private boolean applyShopItemMeta(ItemMeta meta, ShopItem item) {
        boolean changed = false;
        if (item.displayName() != null && !item.displayName().isBlank()) {
            meta.setDisplayName(Text.colorize(item.displayName()));
            changed = true;
        }
        if (item.customModelData() != null) {
            meta.setCustomModelData(item.customModelData());
            changed = true;
        }
        if (item.lore() != null && !item.lore().isEmpty()) {
            meta.setLore(item.lore().stream().map(Text::colorize).toList());
            changed = true;
        }
        if (item.enchants() != null) {
            for (Map.Entry<String, Integer> entry : item.enchants().entrySet()) {
                var enchantment = ItemKeys.enchantment(entry.getKey());
                if (enchantment != null) {
                    meta.addEnchant(enchantment, entry.getValue(), true);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean requiresInventorySpace(ShopItem item) {
        return item.type() == ShopItemType.ITEM || item.type() == ShopItemType.ENCHANTMENT;
    }

    private int executePurchaseDelivery(Player player, String sectionId, ShopItem item, int amount) {
        return switch (item.type()) {
            case ITEM, ENCHANTMENT -> giveItems(player.getInventory(), item, amount);
            case PERMISSION -> grantPermissionPurchase(player, item, amount);
            case COMMAND -> executeCommandPurchase(player, sectionId, item, amount);
            case DUMMY -> 0;
        };
    }

    private int grantPermissionPurchase(Player player, ShopItem item, int amount) {
        if (item.permission() == null || item.permission().isBlank()) {
            return 0;
        }
        if (!plugin.getPermissionService().isEnabled()) {
            plugin.getMessages().send(player, "permission-provider-missing");
            return 0;
        }
        if (plugin.getPermissionService().has(player, item.permission())) {
            plugin.getMessages().send(player, "permission-already-owned");
            return 0;
        }
        return plugin.getPermissionService().grant(player, item.permission()) ? amount : 0;
    }

    private int executeCommandPurchase(Player player, String sectionId, ShopItem item, int amount) {
        for (String command : item.commands()) {
            String parsed = applyCommandPlaceholders(player, sectionId, item, amount, command);
            if (parsed.startsWith("[player]")) {
                player.performCommand(parsed.substring("[player]".length()).trim());
            } else {
                String consoleCommand = parsed.startsWith("[console]")
                        ? parsed.substring("[console]".length()).trim()
                        : parsed;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            }
        }
        return amount;
    }

    private String applyCommandPlaceholders(Player player, String sectionId, ShopItem item, int amount, String input) {
        return CommandPlaceholders.apply(input, Map.of(
                "player", player.getName(),
                "PLAYER", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "UUID", player.getUniqueId().toString(),
                "section", sectionId,
                "SECTION", sectionId,
                "item", item.id(),
                "ITEM", item.id(),
                "amount", String.valueOf(amount),
                "AMOUNT", String.valueOf(amount)
        ));
    }

    private String applyPlayerPlaceholders(Player player, String input) {
        return CommandPlaceholders.apply(input, Map.of(
                "player", player.getName(),
                "PLAYER", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "UUID", player.getUniqueId().toString()
        ));
    }

    private boolean canFitExact(PlayerInventory inventory, ShopItem item, int amount) {
        int remaining = amount;
        int stackCap = item.effectiveStackSize();
        ItemStack probe = createPurchaseStack(item, 1);
        ItemStack[] storage = inventory.getStorageContents();
        for (ItemStack content : storage) {
            if (content == null || content.getType() == Material.AIR) {
                remaining -= stackCap;
            } else if (content.isSimilar(probe) && content.getAmount() < stackCap) {
                remaining -= (stackCap - content.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private int giveItems(PlayerInventory inventory, ShopItem item, int amount) {
        int remaining = amount;
        int added = 0;
        int stackCap = item.effectiveStackSize();
        ItemStack probe = createPurchaseStack(item, 1);

        for (int slot = 0; slot < inventory.getStorageContents().length && remaining > 0; slot++) {
            ItemStack content = inventory.getItem(slot);
            if (content == null || content.getType() == Material.AIR || !content.isSimilar(probe) || content.getAmount() >= stackCap) {
                continue;
            }
            int add = Math.min(stackCap - content.getAmount(), remaining);
            content.setAmount(content.getAmount() + add);
            inventory.setItem(slot, content);
            remaining -= add;
            added += add;
        }

        for (int slot = 0; slot < inventory.getStorageContents().length && remaining > 0; slot++) {
            ItemStack content = inventory.getItem(slot);
            if (content != null && content.getType() != Material.AIR) {
                continue;
            }
            int add = Math.min(stackCap, remaining);
            inventory.setItem(slot, createPurchaseStack(item, add));
            remaining -= add;
            added += add;
        }

        return added;
    }

    private boolean isFinitePositiveOrZero(double value) {
        return Double.isFinite(value) && value >= 0D;
    }

    private static class SellResult {
        private double earned;
        private int soldUnits;
        private final List<ItemStack> returnedItems = new ArrayList<>();
        private final List<ItemStack> soldItems = new ArrayList<>();
        private final List<SoldLine> soldLines = new ArrayList<>();

        private void merge(SellResult other) {
            earned += other.earned;
            soldUnits += other.soldUnits;
            soldItems.addAll(other.soldItems);
            for (SoldLine line : other.soldLines) {
                addSoldLine(line.item(), line.amount(), line.price());
            }
        }

        private void addSoldLine(String item, int amount, double price) {
            for (int i = 0; i < soldLines.size(); i++) {
                SoldLine existing = soldLines.get(i);
                if (existing.item().equals(item)) {
                    soldLines.set(i, new SoldLine(item, existing.amount() + amount, existing.price() + price));
                    return;
                }
            }
            if (soldLines.size() >= MAX_SELL_RECEIPT_LINES - 1) {
                addOverflowSoldLine(amount, price);
                return;
            }
            soldLines.add(new SoldLine(item, amount, price));
        }

        private void addOverflowSoldLine(int amount, double price) {
            for (int i = 0; i < soldLines.size(); i++) {
                SoldLine existing = soldLines.get(i);
                if (existing.item().equals(OTHER_SOLD_ITEMS_LABEL)) {
                    soldLines.set(i, new SoldLine(OTHER_SOLD_ITEMS_LABEL, existing.amount() + amount, existing.price() + price));
                    return;
                }
            }
            soldLines.add(new SoldLine(OTHER_SOLD_ITEMS_LABEL, amount, price));
        }
    }

    private static class SellProcessingContext {
        private int processedSteps;

        private boolean consumeStep() {
            if (processedSteps >= MAX_SELL_PROCESSING_STEPS) {
                return false;
            }
            processedSteps++;
            return true;
        }

        private boolean hasCapacity() {
            return processedSteps < MAX_SELL_PROCESSING_STEPS;
        }
    }

    private record SoldLine(String item, int amount, double price) {
    }

    private record PromptEdit(PromptType type, String configPath, String sectionId, String itemId, int page, int sectionPage) {
    }

    private record PromptOutcome(boolean success, String updatedItemId, String feedbackMessage) {
        private static PromptOutcome success(String itemId) {
            return new PromptOutcome(true, itemId, "editor-saved");
        }

        private static PromptOutcome search(String query) {
            return new PromptOutcome(true, query, "editor-search-applied");
        }

        private static PromptOutcome failure() {
            return new PromptOutcome(false, null, "editor-invalid");
        }
    }

    private record GuiButton(int slot, Material material, int amount, String name, List<String> lore, int customModelData) {
        private GuiButton(int slot, Material material, String name, List<String> lore) {
            this(slot, material, 1, name, lore, -1);
        }
    }

    private record ScreenState(String title, boolean editor) {
    }

    private enum PromptType {
        CONFIG_STRING,
        CONFIG_DOUBLE,
        CONFIG_SOUND,
        CONFIG_GAMEMODE_LIST,
        ROTATING_RESET_SECONDS,
        SELL_BOOSTER_START,
        MAIN_ROWS,
        SECTION_SEARCH,
        ROTATING_SECTION_SEARCH,
        ROTATING_ITEM_SEARCH,
        GLOBAL_PRICE_SEARCH,
        ITEM_SEARCH,
        NEW_SECTION_ID,
        SECTION_SIZE,
        SECTION_SLOT,
        SECTION_DESCRIPTION,
        ITEM_ID,
        ITEM_MATERIAL,
        ITEM_AMOUNT,
        ITEM_SLOT,
        ITEM_BUY,
        ITEM_SELL,
        ITEM_TYPE,
        ITEM_PAGE,
        ITEM_ACTION,
        ITEM_STOCK,
        ITEM_BUY_LIMIT,
        ITEM_STACK_SIZE,
        GLOBAL_PRICE
    }

    private enum WorthSort {
        NAME("Name"),
        PRICE("Price");

        private final String display;

        WorthSort(String display) {
            this.display = display;
        }

        private String display() {
            return display;
        }

    }

    private record GlobalPriceListEntry(GlobalSellPriceService.GlobalSellPriceEntry materialEntry,
                                        GlobalSellPriceService.GlobalPotionEntry potionEntry,
                                        GlobalSellPriceService.GlobalEnchantmentEntry enchantmentEntry) {
        private static GlobalPriceListEntry material(GlobalSellPriceService.GlobalSellPriceEntry entry) {
            return new GlobalPriceListEntry(entry, null, null);
        }

        private static GlobalPriceListEntry potion(GlobalSellPriceService.GlobalPotionEntry entry) {
            return new GlobalPriceListEntry(null, entry, null);
        }

        private static GlobalPriceListEntry enchantment(GlobalSellPriceService.GlobalEnchantmentEntry entry) {
            return new GlobalPriceListEntry(null, null, entry);
        }

        private boolean isPotion() {
            return potionEntry != null;
        }

        private boolean isEnchantment() {
            return enchantmentEntry != null;
        }

        private Material material() {
            return materialEntry == null ? null : materialEntry.material();
        }

        private PotionType potionType() {
            return potionEntry == null ? null : potionEntry.potionType();
        }

        private String enchantmentKey() {
            return enchantmentEntry == null ? null : enchantmentEntry.enchantmentKey();
        }

        private int enchantmentLevel() {
            return enchantmentEntry == null ? 0 : enchantmentEntry.level();
        }

        private boolean enabled() {
            if (isEnchantment()) {
                return enchantmentEntry.enabled();
            }
            return isPotion() ? potionEntry.enabled() : materialEntry.enabled();
        }

        private boolean rotatingShop() {
            if (isEnchantment()) {
                return enchantmentEntry.rotatingShop();
            }
            return isPotion() ? potionEntry.rotatingShop() : materialEntry.rotatingShop();
        }

        private String itemId() {
            if (isEnchantment()) {
                return GlobalSellPriceService.enchantmentEntryId(enchantmentEntry.enchantmentKey(), enchantmentEntry.level());
            }
            return isPotion() ? GlobalSellPriceService.potionEntryId(potionEntry.potionType()) : materialEntry.material().name();
        }

        private String sortName() {
            if (isEnchantment()) {
                return String.format(Locale.ROOT, "ENCHANTMENT_%s_%03d", enchantmentEntry.enchantmentKey(), 100 - enchantmentEntry.level());
            }
            return isPotion() ? "POTION_" + potionEntry.potionType().name() : materialEntry.material().name();
        }

        private String searchName() {
            if (isEnchantment()) {
                String name = enchantmentEntry.enchantmentKey().toLowerCase(Locale.ROOT).replace('_', ' ');
                return ("enchanted book enchantment " + name + " "
                        + enchantmentEntry.enchantmentKey() + " "
                        + enchantmentEntry.level() + " "
                        + itemId()).toLowerCase(Locale.ROOT);
            }
            return isPotion()
                    ? ("potion " + potionEntry.potionType().name() + " " + itemId()).toLowerCase(Locale.ROOT)
                    : materialEntry.material().name().toLowerCase(Locale.ROOT);
        }
    }

    private abstract static class FoHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private static class MainMenuHolder extends FoHolder {
        private final Map<Integer, String> slotToSection = new HashMap<>();
    }

    private static class SellGuiHolder extends FoHolder {
        private final List<Integer> decorationSlots = new ArrayList<>();
        private final Map<Integer, List<String>> playerCommands = new HashMap<>();
        private final Map<Integer, List<String>> consoleCommands = new HashMap<>();
        private int sellSlot;
    }

    private static class RotatingShopHolder extends FoHolder {
        private final Map<Integer, RotatingShopService.RotatingEntry> slotToEntry = new HashMap<>();
    }

    private static class AdminEditorHolder extends FoHolder {
    }

    private static class SettingsEditorHolder extends FoHolder {
    }

    private static class RotatingEditorHolder extends FoHolder {
    }

    private static class SellBoosterEditorHolder extends FoHolder {
    }

    private static class ActiveSellBoostersHolder extends FoHolder {
        private final int page;
        private final int totalPages;
        private final Map<Integer, String> slotToBooster = new HashMap<>();

        private ActiveSellBoostersHolder(int page, int totalPages) {
            this.page = page;
            this.totalPages = totalPages;
        }
    }

    private static class ConfirmRemoveSellBoosterHolder extends FoHolder {
        private final String boosterId;
        private final int page;

        private ConfirmRemoveSellBoosterHolder(String boosterId, int page) {
            this.boosterId = boosterId;
            this.page = page;
        }
    }

    private static class GlobalSellPriceEditorHolder extends FoHolder {
    }

    private static class GlobalSellPriceListHolder extends FoHolder {
        private final boolean editor;
        private final int page;
        private final int totalPages;
        private final String query;
        private final WorthSort sort;
        private final Map<Integer, GlobalPriceListEntry> slotToEntry = new HashMap<>();

        private GlobalSellPriceListHolder(boolean editor, int page, int totalPages, String query, WorthSort sort) {
            this.editor = editor;
            this.page = page;
            this.totalPages = totalPages;
            this.query = query == null ? "" : query;
            this.sort = sort == null ? WorthSort.NAME : sort;
        }
    }

    private static class RotatingSectionDetailHolder extends FoHolder {
        private final String sectionId;
        private final int sectionPage;

        private RotatingSectionDetailHolder(String sectionId, int sectionPage) {
            this.sectionId = sectionId;
            this.sectionPage = sectionPage;
        }
    }

    private static class SectionDetailHolder extends FoHolder {
        private final String sectionId;
        private final int sectionPage;

        private SectionDetailHolder(String sectionId, int sectionPage) {
            this.sectionId = sectionId;
            this.sectionPage = sectionPage;
        }
    }

    private static class ItemEditorHolder extends FoHolder {
        private final String sectionId;
        private final String itemId;
        private final int page;
        private final int sectionPage;

        private ItemEditorHolder(String sectionId, String itemId, int page, int sectionPage) {
            this.sectionId = sectionId;
            this.itemId = itemId;
            this.page = page;
            this.sectionPage = sectionPage;
        }
    }

    private static class ConfirmDeleteItemHolder extends FoHolder {
        private final String sectionId;
        private final String itemId;
        private final int page;
        private final int sectionPage;

        private ConfirmDeleteItemHolder(String sectionId, String itemId, int page, int sectionPage) {
            this.sectionId = sectionId;
            this.itemId = itemId;
            this.page = page;
            this.sectionPage = sectionPage;
        }
    }

    private static class ConfirmDeleteSectionHolder extends FoHolder {
        private final String sectionId;
        private final int sectionPage;

        private ConfirmDeleteSectionHolder(String sectionId, int sectionPage) {
            this.sectionId = sectionId;
            this.sectionPage = sectionPage;
        }
    }

    private static class BuyItemHolder extends FoHolder {
        private final String sectionId;
        private final String itemId;
        private final int selectedAmount;
        private final Map<Integer, Integer> slotToDelta = new HashMap<>();
        private int cancelSlot = 21;
        private int bulkSlot = 22;
        private int confirmSlot = 23;

        private BuyItemHolder(String sectionId, String itemId, int selectedAmount) {
            this.sectionId = sectionId;
            this.itemId = itemId;
            this.selectedAmount = selectedAmount;
        }
    }

    private static class BuyMoreHolder extends FoHolder {
        private final String sectionId;
        private final String itemId;
        private final int returnAmount;
        private final Map<Integer, Integer> slotToAmount = new HashMap<>();
        private int backSlot = 18;

        private BuyMoreHolder(String sectionId, String itemId, int returnAmount) {
            this.sectionId = sectionId;
            this.itemId = itemId;
            this.returnAmount = returnAmount;
        }
    }

    private static class SectionHolder extends FoHolder {
        private final String sectionId;
        private final int page;
        private int backSlot = -1;
        private int previousSlot = -1;
        private int nextSlot = -1;

        private SectionHolder(String sectionId, int page) {
            this.sectionId = sectionId;
            this.page = page;
        }

        public String sectionId() {
            return sectionId;
        }

        public int page() {
            return page;
        }
    }
}
