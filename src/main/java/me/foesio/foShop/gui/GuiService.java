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
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.GuiDiagnostic;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiClick;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiInventoryHolder;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiRenderer;
import me.foesio.core.gui.publicgui.PublicGuiService;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.message.FoStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import me.foesio.foShop.validation.ShopValidationService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Objects;
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
    private static final int[] BUY_MORE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PLAYER_STORAGE_SLOT_COUNT = 36;
    private static final int THREE_ROW_BACK_SLOT = 22;
    private static final int FOUR_ROW_BACK_SLOT = 31;
    private static final int FIVE_ROW_BACK_SLOT = 40;
    private static final int SIX_ROW_BACK_SLOT = 49;
    private static final int MAX_SELL_CONTAINER_DEPTH = 3;
    private static final int MAX_SELL_PROCESSING_STEPS = 2048;
    private static final int MAX_SELL_RECEIPT_LINES = 80;
    private static final String ROTATING_SECTIONS_SELECTOR_TITLE = "Rotating Sections";
    private static final String ROTATING_ITEMS_SELECTOR_TITLE_PREFIX = "Rotating Items: ";
    private static final GuiButtonConfig GUI_BUTTONS = GuiButtonConfig.defaults();

    static String editorTitle(String label) {
        // Convert code-owned titles before coloring: converting a legacy color
        // code such as &8 after coloring would corrupt its letter-based codes.
        return GuiTitles.format(Text.colorize("&8" + GuiTitles.smallCaps(label)));
    }

    static Component receiptSeparator(String configured) {
        // The sprite renderer normalizes standalone whitespace on unsupported
        // clients; the receipt separator must keep the owner's exact spacing.
        return LegacyComponentSerializer.legacySection().deserialize(configured);
    }
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
    private final PublicGuiService publicGuis;
    private final PublicGuiService worthGuis;
    private final SellTransactionLogger transactionLogger;
    private final EditorSettingSaver configSettingSaver;
    private final ChatPromptManager chatPrompts;
    private final Map<UUID, Integer> rotatingItemSectionPages = new HashMap<>();
    private final Map<UUID, String> rotatingItemSectionIds = new HashMap<>();
    private final Map<UUID, Integer> sectionItemBrowserPages = new HashMap<>();
    private final Set<UUID> sellingInProgress = new HashSet<>();
    private final Set<UUID> suppressConfirmDeleteClose = new HashSet<>();
    private final Set<UUID> suppressConfirmRemoveBoosterClose = new HashSet<>();
    private final Set<UUID> nativeDialogFallbackWarnings = new HashSet<>();
    private final Map<UUID, ScreenState> activeScreens = new HashMap<>();
    private final Set<UUID> pendingBackNavigations = new HashSet<>();
    private final Map<UUID, SectionContext> publicSections = new HashMap<>();
    private final Map<UUID, BuyContext> publicBuys = new HashMap<>();
    private final Map<UUID, WorthContext> publicWorth = new HashMap<>();
    private ShopManager.ReloadResult lastShopReloadResult;
    private List<String> worthGuiIssues = List.of();
    private List<Integer> bulkStackOptions = List.of();
    private Map<String, String> publicItemNames = Map.of();

    private record SectionContext(String sectionId, int page) {}
    private record EditorBrowserContext(boolean sections, String sectionId) {}
    private record BuyContext(String sectionId, String itemId, int amount, long shopRevision, long guiRevision) {}
    private record WorthContext(int page, int pages, String query, WorthSort sort) {}

    public GuiService(FoShop plugin) {
        this.plugin = plugin;
        this.transactionLogger = new SellTransactionLogger(plugin);
        this.configSettingSaver = new EditorSettingSaver(plugin, () -> plugin.reloadAll());
        this.chatPrompts = new ChatPromptManager(plugin, plugin.getCore().scheduler());
        this.publicGuis = configurePublicGuis();
        this.worthGuis = configureWorthGuis();
        this.publicGuis.start(this::stageShopLayout);
        this.worthGuis.start();
    }

    private PublicGuiService.PreparedDependency stageShopLayout(Map<String, PublicGuiDefinition> proposed) {
        Set<Integer> mainSlots = Set.copyOf(proposed.get("shop.main").entries().get("sections").slots());
        Set<Integer> productSlots = Set.copyOf(proposed.get("shop.shop_section").entries().get("products").slots());
        ShopValidationService candidate = new ShopValidationService();
        candidate.configurePublicLayout(mainSlots, productSlots);
        ShopManager.PreparedReload prepared = plugin.getShopManager().prepareReload(candidate);
        boolean hasCachedGui = publicGuis.snapshot() != null;
        List<GuiDiagnostic> diagnostics = new ArrayList<>(prepared.result().issues().stream()
                .filter(issue -> hasCachedGui || issue.contains("public GUI layout:") || issue.contains("shop file invalid:")
                        || issue.equals("No shop files found."))
                .map(issue -> new GuiDiagnostic("shops", "layout", issue))
                .toList());
        List<Integer> candidateOptions = configuredBulkStackOptions(plugin.getConfig());
        if (invalidBulkStackOptions(plugin.getConfig(), candidateOptions)) {
            if (hasCachedGui) {
                diagnostics.add(new GuiDiagnostic("config.yml", "public-gui.bulk-stack-options",
                        "Use a list of unique whole stack counts from 1 to 9; previous choices remain active."));
            } else {
                candidateOptions = bundledBulkStackOptions();
                plugin.getLogger().warning("Invalid public-gui.bulk-stack-options; using bundled choices in memory on startup.");
            }
        }
        PublicItemNames candidateNames = readPublicItemNames(plugin.getConfig());
        if (!candidateNames.errors().isEmpty()) {
            if (hasCachedGui) {
                candidateNames.errors().forEach(error -> diagnostics.add(
                        new GuiDiagnostic("config.yml", "public-values.item-names", error)));
            } else {
                candidateNames.errors().forEach(error -> plugin.getLogger().warning(
                        "Invalid public item name override: " + error + "; using generated name in memory."));
            }
        }
        List<Integer> acceptedOptions = List.copyOf(candidateOptions);
        return new PublicGuiService.PreparedDependency(diagnostics, () -> {
            plugin.getValidationService().configurePublicLayout(mainSlots, productSlots);
            plugin.getShopManager().publish(prepared);
            lastShopReloadResult = prepared.result();
            bulkStackOptions = acceptedOptions;
            publicItemNames = candidateNames.values();
        });
    }

    private record PublicItemNames(Map<String, String> values, List<String> errors) {}

    private PublicItemNames readPublicItemNames(FileConfiguration config) {
        Map<String, String> values = new HashMap<>();
        List<String> errors = new ArrayList<>();
        String rootPath = "public-values.item-names";
        if (!config.isSet(rootPath)) {
            return new PublicItemNames(Map.of(), List.of());
        }
        ConfigurationSection root = config.getConfigurationSection(rootPath);
        if (root == null) {
            return new PublicItemNames(Map.of(), List.of(rootPath + " must be a section"));
        }
        for (String kind : List.of("materials", "potions", "enchantments")) {
            String kindPath = rootPath + "." + kind;
            if (!root.isSet(kind)) continue;
            ConfigurationSection names = root.getConfigurationSection(kind);
            if (names == null) {
                errors.add(kindPath + " must be a section");
                continue;
            }
            for (String id : names.getKeys(false)) {
                Object value = names.get(id);
                if (value instanceof String name) {
                    values.put(kind + "." + id, name);
                } else {
                    errors.add(kindPath + "." + id + " must be text");
                }
            }
        }
        return new PublicItemNames(Map.copyOf(values), List.copyOf(errors));
    }

    private List<Integer> bundledBulkStackOptions() {
        try (var resource = plugin.getResource("config.yml")) {
            if (resource == null) throw new IllegalStateException("Bundled config.yml is missing");
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            List<Integer> options = configuredBulkStackOptions(bundled);
            if (options.isEmpty()) throw new IllegalStateException("Bundled bulk-stack-options are invalid");
            return options;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled bulk-stack-options", exception);
        }
    }

    public ShopManager.ReloadResult lastShopReloadResult() {
        return lastShopReloadResult;
    }

    public GuiLoadReport reloadPublicGuis() {
        GuiLoadReport main = publicGuis.reload();
        GuiLoadReport worth = worthGuis.reload();
        worthGuiIssues = worth.successful() ? List.of()
                : worth.diagnostics().stream().map(GuiDiagnostic::toString).toList();
        if (!worth.successful()) {
            plugin.getLogger().warning("Worth GUI reload failed; other public menus retain their own GUI snapshot.");
        }
        return main;
    }

    public List<String> worthGuiIssues() {
        return worthGuiIssues;
    }

    private PublicGuiService configurePublicGuis() {
        PublicGuiService guis = new PublicGuiService(plugin);
        var root = plugin.getDataFolder().toPath().resolve("guis");
        guis.menu("shop.main", root.resolve("main.yml"), coreMenu(Set.of("shop.open-section"),
                Set.of("section", "section_id")));
        guis.menu("shop.shop_section", root.resolve("shop-section.yml"), coreMenu(Set.of(
                "shop.section-back", "shop.section-previous", "shop.section-next", "shop.open-product"),
                Set.of("section_title", "buy", "sell", "stock", "buy_limit", "page", "pages")));
        guis.menu("shop.buy_item", root.resolve("buy-item.yml"), coreMenu(Set.of(
                "shop.amount-remove-64", "shop.amount-remove-10", "shop.amount-remove-1",
                "shop.amount-add-1", "shop.amount-add-10", "shop.amount-add-64",
                "shop.amount-cancel", "shop.amount-bulk", "shop.amount-confirm"),
                Set.of("item", "amount", "price")));
        guis.menu("shop.buy_more", root.resolve("buy-more.yml"), coreMenu(Set.of(
                "shop.more-back", "shop.more-buy"), Set.of("item", "amount", "price", "stacks", "plural", "stack_unit")));
        guis.menu("shop.rotating_shop", root.resolve("rotating-shop.yml"), coreMenu(Set.of(),
                Set.of("item", "boost", "price", "base_price", "buy", "time")));
        guis.menu("shop.sell_gui", root.resolve("sell-gui.yml"), new PublicGuiContract(
                Set.of("shop.sell"), Map.of("sell-slots", new PublicGuiContract.GroupRule(
                PublicGuiContract.GroupRole.PHYSICAL_INPUT, 1, 45)), Set.of(),
                PublicGuiContract.InteractionMode.PLUGIN_OWNED));
        guis.requireEntry("shop.main", "sections", 0)
                .requireEntry("shop.shop_section", "products", 0)
                .requireEntry("shop.buy_item", "preview", 1)
                .requireEntry("shop.buy_more", "options", 0)
                .requireEntry("shop.rotating_shop", "center", 1)
                .requireEntry("shop.rotating_shop", "boosts", 0);
        for (String state : List.of("none", "buy", "sell", "stock", "limit", "buy-sell",
                "buy-stock", "buy-limit", "sell-stock", "sell-limit", "stock-limit",
                "buy-sell-stock", "buy-sell-limit", "buy-stock-limit", "sell-stock-limit",
                "buy-sell-stock-limit")) {
            guis.requireEntryLoreState("shop.shop_section", "products", state);
        }
        for (String region : List.of("center", "boosts")) {
            for (String state : List.of("shop-buy", "shop-sell-only", "global-material",
                    "global-enchantment", "global-potion")) {
                guis.requireEntryLoreState("shop.rotating_shop", region, state);
            }
        }
        guis.pluginRoute("shop.sell_gui", this);
        for (String action : Set.of("shop.open-section", "shop.section-back", "shop.section-previous",
                "shop.section-next", "shop.open-product", "shop.amount-remove-64", "shop.amount-remove-10",
                "shop.amount-remove-1", "shop.amount-add-1", "shop.amount-add-10", "shop.amount-add-64",
                "shop.amount-cancel", "shop.amount-bulk", "shop.amount-confirm", "shop.more-back",
                "shop.more-buy")) {
            guis.action(action, this::handlePublicGuiClick);
        }
        guis.entryPoint("command:/foshop", "shop.main")
                .entryPoint("command:/shop", "shop.main")
                .entryPoint("section selection", "shop.shop_section")
                .entryPoint("product selection", "shop.buy_item")
                .entryPoint("bulk buy", "shop.buy_more")
                .entryPoint("command:/fosellgui", "shop.sell_gui")
                .entryPoint("command:/sellgui", "shop.sell_gui")
                .entryPoint("command:/sell", "shop.sell_gui")
                .entryPoint("command:/sell gui", "shop.sell_gui")
                .entryPoint("command:/sell menu", "shop.sell_gui")
                .entryPoint("command:/sell open", "shop.sell_gui")
                .entryPoint("command:/rotatingshop", "shop.rotating_shop")
                .entryPoint("command:/rshop", "shop.rotating_shop");
        return guis;
    }

    private PublicGuiService configureWorthGuis() {
        PublicGuiService guis = new PublicGuiService(plugin);
        var root = plugin.getDataFolder().toPath().resolve("guis");
        guis.menu("shop.worth", root.resolve("worth.yml"), coreMenu(Set.of(
                "shop.worth-previous", "shop.worth-sort", "shop.worth-search",
                "shop.worth-clear-search", "shop.worth-next"),
                Set.of("item", "worth", "page", "pages", "sort", "next_sort", "query")));
        guis.requireEntry("shop.worth", "center", 1)
                .requireEntry("shop.worth", "prices", 0);
        for (String region : List.of("center", "prices")) {
            for (String state : List.of("material", "enchantment", "potion")) {
                guis.requireEntryLoreState("shop.worth", region, state);
            }
        }
        for (String action : Set.of("shop.worth-previous", "shop.worth-sort", "shop.worth-search",
                "shop.worth-clear-search", "shop.worth-next")) {
            guis.action(action, this::handlePublicGuiClick);
        }
        guis.entryPoint("command:/worth", "shop.worth")
                .entryPoint("command:/prices", "shop.worth");
        return guis;
    }

    private PublicGuiContract coreMenu(Set<String> actions, Set<String> placeholders) {
        return new PublicGuiContract(actions, Map.of(), placeholders, PublicGuiContract.InteractionMode.CORE);
    }

    private void handlePublicGuiClick(PublicGuiClick click) {
        Player player = click.player();
        String action = click.actionId();
        UUID id = player.getUniqueId();
        if (action.equals("shop.open-section")) {
            plugin.getShopManager().getSection(click.entryId()).filter(ShopSection::enabled).ifPresent(section -> {
                plugin.getSounds().play(player, "gui.select");
                openShopSection(player, section);
            });
            return;
        }
        if (action.startsWith("shop.section-") || action.equals("shop.open-product")) {
            SectionContext context = publicSections.get(id);
            ShopSection section = context == null ? null : plugin.getShopManager().getSection(context.sectionId()).orElse(null);
            if (section == null || !section.enabled()) {
                openMainShop(player);
                return;
            }
            switch (action) {
                case "shop.section-back" -> {
                    markBackNavigation(player);
                    openMainShop(player);
                }
                case "shop.section-previous" -> {
                    if (context.page() > 0) {
                        plugin.getSounds().play(player, "gui.page-previous");
                        openShopSection(player, section, context.page() - 1);
                    }
                }
                case "shop.section-next" -> {
                    if (context.page() < section.totalPages() - 1) {
                        plugin.getSounds().play(player, "gui.page-next");
                        openShopSection(player, section, context.page() + 1);
                    }
                }
                case "shop.open-product" -> {
                    ShopItem item = section.itemsBySlot(context.page()).get(click.rawSlot());
                    if (item == null || !item.id().equals(click.entryId()) || !hasProductAccess(player, item)
                            || item.type() == ShopItemType.DUMMY) {
                        return;
                    }
                    if (!item.canBuy()) {
                        plugin.sendConfigured(player, "buy-disabled");
                        plugin.getSounds().play(player, "shop.purchase-failure");
                    } else {
                        plugin.getSounds().play(player, "gui.select");
                        openBuyItemGui(player, section.id(), item.id(), item.amount());
                    }
                }
                default -> { }
            }
            return;
        }
        if (action.startsWith("shop.amount-") || action.startsWith("shop.more-")) {
            BuyContext context = publicBuys.get(id);
            ShopItem item = context == null ? null : findPublicItem(context.sectionId(), context.itemId());
            if (item == null) {
                plugin.sendConfigured(player, "gui-unavailable");
                openMainShop(player);
                return;
            }
            if (!hasProductAccess(player, item)) {
                denyProductAccess(player, context.sectionId());
                return;
            }
            if (publicGuis.snapshot() == null) {
                plugin.sendConfigured(player, "gui-unavailable");
                return;
            }
            if (context.shopRevision() != plugin.getShopManager().priceRevision()
                    || context.guiRevision() != publicGuis.snapshot().revision()) {
                if (action.startsWith("shop.more-")) {
                    openBuyMoreGui(player, context.sectionId(), context.itemId(), context.amount());
                } else {
                    openBuyItemGui(player, context.sectionId(), context.itemId(), context.amount());
                }
                return;
            }
            int delta = switch (action) {
                case "shop.amount-remove-64" -> -64;
                case "shop.amount-remove-10" -> -10;
                case "shop.amount-remove-1" -> -1;
                case "shop.amount-add-1" -> 1;
                case "shop.amount-add-10" -> 10;
                case "shop.amount-add-64" -> 64;
                default -> 0;
            };
            if (delta != 0) {
                delta = plugin.getConfig().getInt("public-gui.amount-changes."
                        + action.substring("shop.amount-".length()), delta);
                plugin.getSounds().play(player, "gui.cycle");
                openBuyItemGui(player, context.sectionId(), context.itemId(), context.amount() + delta);
                return;
            }
            switch (action) {
                case "shop.amount-confirm" -> {
                    if (buyItem(player, context.sectionId(), item, context.amount())) {
                        openBuyItemGui(player, context.sectionId(), context.itemId(), context.amount());
                    }
                }
                case "shop.amount-bulk" -> openBuyMoreGui(player, context.sectionId(), context.itemId(), context.amount());
                case "shop.amount-cancel" -> {
                    markBackNavigation(player);
                    plugin.getSounds().play(player, "gui.cancel");
                    plugin.getShopManager().getSection(context.sectionId()).ifPresent(section -> openShopSection(player, section));
                }
                case "shop.more-back" -> {
                    markBackNavigation(player);
                    openBuyItemGui(player, context.sectionId(), context.itemId(), context.amount());
                }
                case "shop.more-buy" -> {
                    int stacks;
                    try {
                        stacks = Integer.parseInt(click.entryId());
                    } catch (NumberFormatException exception) {
                        return;
                    }
                    if (visibleBulkStackOptions().contains(stacks)
                            && buyItem(player, context.sectionId(), item,
                            stacks * item.effectiveStackSize())) {
                        openBuyMoreGui(player, context.sectionId(), context.itemId(), context.amount());
                    }
                }
                default -> { }
            }
            return;
        }
        WorthContext worth = publicWorth.get(id);
        if (worth == null) {
            return;
        }
        if (!worthEnabled()) {
            closeWorthIfOpen(player);
            plugin.sendConfigured(player, "worth-disabled");
            return;
        }
        switch (action) {
            case "shop.worth-previous" -> {
                if (worth.page() > 0) {
                    pageSound(player, false, false);
                    openGlobalSellPriceList(player, false, worth.page() - 1, worth.query(), worth.sort());
                }
            }
            case "shop.worth-next" -> {
                if (worth.page() < worth.pages() - 1) {
                    pageSound(player, false, true);
                    openGlobalSellPriceList(player, false, worth.page() + 1, worth.query(), worth.sort());
                }
            }
            case "shop.worth-sort" -> {
                plugin.getSounds().play(player, "gui.sort");
                openGlobalSellPriceList(player, false, 0, worth.query(),
                        worth.sort() == WorthSort.NAME ? WorthSort.PRICE : WorthSort.NAME);
            }
            case "shop.worth-search" -> {
                searchSound(player, false, false);
                startPrompt(player, new PromptEdit(PromptType.GLOBAL_PRICE_SEARCH, "worth", null, null,
                        worth.page(), worth.sort().ordinal(), worth.query()),
                        publicValue("worth-search-prompt"));
            }
            case "shop.worth-clear-search" -> {
                if (!worth.query().isBlank()) {
                    searchSound(player, false, true);
                    openGlobalSellPriceList(player, false, 0, "", worth.sort());
                }
            }
            default -> { }
        }
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
        if (publicGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        var definition = publicGuis.snapshot().require("shop.main");
        List<Integer> available = definition.entries().get("sections").slots();
        Map<Integer, PublicGuiRenderInput.EntryValue> entries = new HashMap<>();
        for (ShopSection section : plugin.getShopManager().getSectionsOrdered()) {
            if (!section.enabled()) {
                continue;
            }
            int slot = section.slot();
            if (!available.contains(slot) || entries.containsKey(slot)) {
                logGuiWarning("main", "Section " + section.id() + " has an unavailable configured slot " + slot);
                continue;
            }
            ItemStack icon = section.iconItem() == null ? new ItemStack(section.icon()) : section.iconItem().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    meta.setDisplayName(Text.colorize(applyPlaceholderApi(player, meta.getDisplayName())));
                }
                List<String> description = formatSectionDescription(player, section);
                if (description != null) {
                    meta.setLore(description);
                } else if (meta.hasLore()) {
                    meta.setLore(colorizeLore(meta.getLore()));
                }
                icon.setItemMeta(meta);
            }
            entries.put(slot, new PublicGuiRenderInput.EntryValue(section.id(), icon,
                    Map.of("section", section.title(), "section_id", section.id())));
        }
        publicGuis.openEntryPoint(player, "command:/shop", new PublicGuiRenderInput(
                Map.of(), Map.of(), Map.of(), Map.of("sections", entries)));
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
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
        if (publicGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        ShopSection activeSection = section == null ? null
                : plugin.getShopManager().getSection(section.id()).orElse(null);
        if (activeSection == null || !activeSection.enabled()) {
            openMainShop(player);
            return;
        }
        int currentPage = Math.clamp(page, 0, activeSection.totalPages() - 1);
        var definition = publicGuis.snapshot().require("shop.shop_section");
        List<Integer> productSlots = definition.entries().get("products").slots();
        Map<Integer, PublicGuiRenderInput.EntryValue> entries = new HashMap<>();
        for (ShopItem item : activeSection.itemsBySlot(currentPage).values()) {
            if (!hasProductAccess(player, item)) {
                continue;
            }
            if (!productSlots.contains(item.slot())) {
                logGuiWarning("shop-section", "Product " + item.id() + " has slot " + item.slot()
                        + " outside the configured product region; skipped.");
                continue;
            }
            int amount = item.amount();
            int stock = plugin.getShopManager().getStock(activeSection.id(), item.id());
            int limit = plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), activeSection.id(), item);
            List<String> stateParts = new ArrayList<>();
            if (item.canBuy()) stateParts.add("buy");
            if (item.canSell()) stateParts.add("sell");
            if (stock >= 0) stateParts.add("stock");
            if (limit >= 0) stateParts.add("limit");
            String state = stateParts.isEmpty() ? "none" : String.join("-", stateParts);
            ItemStack source = createPurchaseStack(item, amount);
            ItemMeta meta = source.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    meta.setDisplayName(Text.colorize(applyTextPlaceholders(player, activeSection.id(), item,
                            amount, meta.getDisplayName())));
                }
                if (meta.hasLore()) {
                    meta.setLore(meta.getLore().stream().map(line -> Text.colorize(
                            applyTextPlaceholders(player, activeSection.id(), item, amount, line))).toList());
                }
                source.setItemMeta(meta);
            }
            entries.put(item.slot(), new PublicGuiRenderInput.EntryValue(item.id(), source,
                    Map.of("buy", plugin.getEconomyService().formatCompact(item.buyPrice()),
                            "sell", plugin.getEconomyService().formatCompact(item.sellPrice()),
                            "stock", String.valueOf(stock), "buy_limit", String.valueOf(limit)), state));
        }
        Map<String, String> placeholders = Map.of("section_title",
                activeSection.title().replace("%page%", String.valueOf(currentPage + 1)),
                "page", String.valueOf(currentPage + 1), "pages", String.valueOf(activeSection.totalPages()));
        Map<String, String> states = new HashMap<>();
        if (currentPage == 0) states.put("previous", "unavailable");
        if (currentPage >= activeSection.totalPages() - 1) states.put("next", "unavailable");
        publicSections.put(player.getUniqueId(), new SectionContext(activeSection.id(), currentPage));
        publicGuis.open(player, "shop.shop_section", new PublicGuiRenderInput(
                placeholders, states, Map.of(), Map.of("products", entries)));
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    public void openRotatingShop(Player player) {
        if (!plugin.getRotatingShopService().isEnabled()) {
            plugin.sendConfigured(player, "rotating-shop-disabled");
            return;
        }
        if (publicGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        var definition = publicGuis.snapshot().require("shop.rotating_shop");
        List<Integer> slots = visibleEntrySlots(definition, "center", "boosts");
        Map<Integer, PublicGuiRenderInput.EntryValue> center = new HashMap<>();
        Map<Integer, PublicGuiRenderInput.EntryValue> boosts = new HashMap<>();
        int index = 0;
        for (RotatingShopService.RotatingEntry entry : plugin.getRotatingShopService().activeEntries()) {
            if (index >= slots.size()) break;
            var globalMaterial = plugin.getRotatingShopService().findGlobalEntry(entry).orElse(null);
            var globalPotion = plugin.getRotatingShopService().findGlobalPotionEntry(entry).orElse(null);
            var globalEnchantment = plugin.getRotatingShopService().findGlobalEnchantmentEntry(entry).orElse(null);
            ShopItem item = plugin.getRotatingShopService().findItem(entry).orElse(null);
            ItemStack source;
            String state;
            String display;
            double sell;
            double buy = 0D;
            if (globalEnchantment != null) {
                source = globalEnchantmentBaseStack(globalEnchantment);
                display = enchantmentDisplayName(globalEnchantment);
                sell = globalEnchantment.price();
                state = "global-enchantment";
            } else if (globalPotion != null) {
                source = globalPotionBaseStack(globalPotion);
                display = publicPotionName(globalPotion.potionType().name());
                sell = globalPotion.price();
                state = "global-potion";
            } else if (globalMaterial != null) {
                source = new ItemStack(globalMaterial.material());
                display = publicMaterialName(globalMaterial.material());
                sell = globalMaterial.price();
                state = "global-material";
            } else if (item != null && hasProductAccess(player, item)) {
                source = createPurchaseStack(item, item.amount());
                display = purchaseDisplayName(player, entry.sectionId(), item, item.amount());
                sell = item.sellPrice();
                buy = item.buyPrice();
                state = item.canBuy() ? "shop-buy" : "shop-sell-only";
            } else {
                continue;
            }
            Map<String, String> values = Map.of("item", display, "boost", formatBoost(entry.multiplier()),
                    "base_price", plugin.getEconomyService().formatCompact(sell),
                    "price", plugin.getEconomyService().formatCompact(sell * entry.multiplier()),
                    "buy", plugin.getEconomyService().formatCompact(buy),
                    "time", DurationUtil.formatConfigured(
                            plugin.getRotatingShopService().timeUntilResetMillis() / 1000L,
                            plugin::renderConfigured));
            PublicGuiRenderInput.EntryValue value = new PublicGuiRenderInput.EntryValue(entry.itemId(), source,
                    values, state);
            (definition.entries().get("center").enabled() && index == 0 ? center : boosts)
                    .put(slots.get(index), value);
            index++;
        }
        publicGuis.openEntryPoint(player, "command:/rotatingshop", new PublicGuiRenderInput(
                Map.of(), Map.of(), Map.of(), Map.of("center", center, "boosts", boosts)));
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    public void openWorth(Player player) {
        if (!worthEnabled()) {
            plugin.sendConfigured(player, "worth-disabled");
            return;
        }
        openGlobalSellPriceList(player, false, 0, "", WorthSort.NAME);
    }

    private boolean worthEnabled() {
        return plugin.getGlobalSellPriceService() != null
                && plugin.getGlobalSellPriceService().isEnabled();
    }

    private void closeWorthIfOpen(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof PublicGuiInventoryHolder holder
                && "shop.worth".equals(holder.frame().definition().menuId())) {
            player.closeInventory();
        }
    }

    private void openGlobalSellPriceEditor(Player player) {
        GlobalSellPriceEditorHolder holder = new GlobalSellPriceEditorHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Global Prices"));
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
        if (!editor) {
            openPublicWorthList(player, page, query, sort);
            return;
        }
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        WorthSort activeSort = sort == null ? WorthSort.NAME : sort;
        List<GlobalPriceListEntry> entries = globalPriceEntries(search, activeSort, editor);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) GLOBAL_PRICE_SLOTS.length));
        int currentPage = Math.clamp(page, 0, totalPages - 1);
        GlobalSellPriceListHolder holder = new GlobalSellPriceListHolder(editor, currentPage, totalPages, search, activeSort);
        String title = editor ? editorTitle("Global Prices") : plugin.getFoConfig().sectionTitleSmallCaps(
                plugin.getFoConfig().guiString("worth", "title", "&8Worth"));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);

        if (editor) {
            fillBackground(inventory);
        } else {
            fillBackground(inventory, "worth");
        }

        int start = currentPage * GLOBAL_PRICE_SLOTS.length;
        for (int i = 0; i < GLOBAL_PRICE_SLOTS.length; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }

            GlobalPriceListEntry entry = entries.get(index);
            int slot = GLOBAL_PRICE_SLOTS[i];
            holder.slotToEntry.put(slot, entry);
            inventory.setItem(slot, globalPriceItem(player, entry, editor));
        }

        if (entries.isEmpty()) {
            if (editor) {
                inventory.setItem(22, createItem(Material.GRAY_DYE, "&#ff5d73No Matches", List.of("&#ffffffNo global sell prices matched your search.")));
            } else {
                GuiButton empty = readGuiButton("worth", "empty", 22, Material.GRAY_DYE,
                        "&#ff5d73No Matches", List.of("&#ffffffNo prices matched your search."), inventory.getSize());
                boolean contentSlot = false;
                for (int slot : GLOBAL_PRICE_SLOTS) {
                    if (slot == empty.slot()) {
                        contentSlot = true;
                        break;
                    }
                }
                if (!contentSlot) {
                    logGuiWarning("worth", "empty.slot must use a content slot; using 22.");
                    empty = new GuiButton(22, empty.material(), empty.amount(), empty.name(), empty.lore(), empty.customModelData());
                }
                inventory.setItem(empty.slot(), createButtonItem(player, empty));
            }
        }

        if (editor) {
            holder.previousSlot = 45;
            holder.sortSlot = 47;
            holder.backSlot = 49;
            holder.searchSlot = 51;
            holder.clearSearchSlot = 52;
            holder.nextSlot = 53;
            setPreviousPageButton(player, inventory, holder.previousSlot, currentPage, totalPages);
            inventory.setItem(holder.sortSlot, EditorItemFactory.cycle(player, plugin.getMessages(), "Sort", activeSort.name(), WORTH_SORT_OPTIONS));
            inventory.setItem(49, GUI_BUTTONS.back(player));
            inventory.setItem(holder.searchSlot, GUI_BUTTONS.search(player, search));
            setClearSearchButton(player, inventory, holder.clearSearchSlot, "worth", search);
            setNextPageButton(player, inventory, holder.nextSlot, currentPage, totalPages);
        } else {
            placeWorthControls(player, inventory, holder, currentPage, totalPages, search, activeSort);
        }

        openInventory(player, inventory);
    }

    private void openPublicWorthList(Player player, int page, String query, WorthSort sort) {
        if (!worthEnabled()) {
            closeWorthIfOpen(player);
            plugin.sendConfigured(player, "worth-disabled");
            return;
        }
        if (worthGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        WorthSort activeSort = sort == null ? WorthSort.NAME : sort;
        List<GlobalPriceListEntry> entries = globalPriceEntries(search, activeSort, false);
        var definition = worthGuis.snapshot().require("shop.worth");
        List<Integer> slots = visibleWorthSlots(definition);
        int pages = worthPageCount(entries.size(), slots.size());
        int currentPage = Math.clamp(page, 0, pages - 1);
        Map<Integer, PublicGuiRenderInput.EntryValue> center = new HashMap<>();
        Map<Integer, PublicGuiRenderInput.EntryValue> prices = new HashMap<>();
        for (int index = 0; index < slots.size(); index++) {
            int entryIndex = currentPage * slots.size() + index;
            if (entryIndex >= entries.size()) break;
            GlobalPriceListEntry entry = entries.get(entryIndex);
            String display = publicGlobalDisplayName(entry);
            PublicGuiRenderInput.EntryValue value = new PublicGuiRenderInput.EntryValue(entry.itemId(),
                    globalPriceBaseStack(entry), Map.of("item", display,
                    "worth", formatGlobalPrice(globalListPrice(entry))),
                    entry.isEnchantment() ? "enchantment" : entry.isPotion() ? "potion" : "material");
            (definition.entries().get("center").enabled() && index == 0 ? center : prices)
                    .put(slots.get(index), value);
        }
        Map<String, String> placeholders = Map.of("page", String.valueOf(currentPage + 1),
                "pages", String.valueOf(pages), "sort", worthSortLabel(activeSort),
                "next_sort", worthSortLabel(activeSort == WorthSort.NAME ? WorthSort.PRICE : WorthSort.NAME),
                "query", search.isBlank() ? publicValue("worth-search-empty") : search);
        Map<String, String> states = new HashMap<>();
        if (currentPage == 0) states.put("previous", "unavailable");
        if (currentPage >= pages - 1) states.put("next", "unavailable");
        if (search.isBlank()) states.put("clear-search", "unavailable");
        publicWorth.put(player.getUniqueId(), new WorthContext(currentPage, pages, search, activeSort));
        worthGuis.openEntryPoint(player, "command:/worth", new PublicGuiRenderInput(placeholders,
                states, Map.of(), Map.of("center", center, "prices", prices)));
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    static List<Integer> visibleWorthSlots(PublicGuiDefinition definition) {
        return visibleEntrySlots(definition, "center", "prices");
    }

    static List<Integer> visibleEntrySlots(PublicGuiDefinition definition, String first, String second) {
        List<Integer> slots = new ArrayList<>();
        if (definition.entries().get(first).enabled()) {
            slots.addAll(definition.entries().get(first).slots());
        }
        if (definition.entries().get(second).enabled()) {
            slots.addAll(definition.entries().get(second).slots());
        }
        return slots;
    }

    static int worthPageCount(int entryCount, int visibleSlotCount) {
        return visibleSlotCount == 0 ? 1
                : Math.max(1, (entryCount + visibleSlotCount - 1) / visibleSlotCount);
    }

    public void openSellGui(Player player) {
        if (denyBlockedSellGamemode(player)) {
            return;
        }
        if (publicGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        PublicGuiFrame frame = publicGuis.plan("shop.sell_gui", PublicGuiRenderInput.empty());
        SellGuiHolder holder = new SellGuiHolder();
        holder.frame = frame;
        holder.sellSlot = frame.definition().buttons().get("sell").slot();
        frame.definition().decorations().forEach((id, decoration) -> {
            holder.decorationSlots.addAll(decoration.slots());
            List<String> playerCommands = plugin.getConfig().getStringList(
                    "public-gui.sell-decoration-commands." + id + ".player");
            List<String> consoleCommands = plugin.getConfig().getStringList(
                    "public-gui.sell-decoration-commands." + id + ".console");
            for (int slot : decoration.slots()) {
                holder.playerCommands.put(slot, playerCommands);
                holder.consoleCommands.put(slot, consoleCommands);
            }
        });
        Inventory inventory = Bukkit.createInventory(holder, frame.definition().size(),
                DialogIcons.fallbackText(frame.title()));
        holder.setInventory(inventory);
        PublicGuiRenderer.renderInto(player, inventory, frame);
        player.openInventory(inventory);
        screenOpened(player, holder);
    }

    public Set<Integer> sellGuiInputSlots(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof SellGuiHolder holder)
                || holder.frame == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(holder.frame.definition().requireGroup("sell-slots")));
    }

    private void applySellGuiDecorations(Inventory inventory, SellGuiHolder holder) {
        ConfigurationSection decorations = plugin.getFoConfig().guiSection("sell-gui", "decorations");
        Object rawDecorations = plugin.getFoConfig().guiObject("sell-gui", "decorations");
        if (!plugin.getFoConfig().isPublicGuiMigrationReady()) {
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
                                mapInt(map, "custom-model-data", mapInt(map, "customModelData", mapInt(map, "item.custom-model-data", mapInt(map, "item.customModelData", -1)))),
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
                    section.contains("custom-model-data") ? section.getInt("custom-model-data")
                            : section.contains("customModelData") ? section.getInt("customModelData")
                            : section.contains("item.custom-model-data") ? section.getInt("item.custom-model-data")
                            : section.getInt("item.customModelData", -1),
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
        if (slot == holder.sellSlot) {
            logGuiWarning("sell-gui", "Decoration slot " + slot + " collides with buttons.sell; decoration skipped.");
            return;
        }
        if (holder.decorationSlots.contains(slot)) {
            logGuiWarning("sell-gui", "Multiple decorations use slot " + slot + "; later decoration skipped.");
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
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("FoShop Editor"));
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
        Inventory inventory = Bukkit.createInventory(holder, 36, editorTitle("FoShop Settings"));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(10, createItem(Material.CHEST, "&#03fc88Main Shop Layout", List.of(
                "&#ffffffRows: &#03fc88" + (publicGuis.snapshot() == null ? "?" : publicGuis.snapshot().require("shop.main").rows()),
                "&#a7b8b0Edit guis/main.yml to change the layout.")));
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
        Inventory inventory = Bukkit.createInventory(holder, 36, editorTitle("Sell Boosters"));
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
        Inventory inventory = Bukkit.createInventory(holder, 54, editorTitle("Active Sell Boosters"));
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
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Rotating Shop"));
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
                .title(editorTitle(ROTATING_SECTIONS_SELECTOR_TITLE))
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openRotatingSectionEditorList(player, sectionPage);
            return;
        }

        RotatingSectionDetailHolder holder = new RotatingSectionDetailHolder(sectionId, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Rotating: " + section.id()));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openRotatingSectionEditorList(player, sectionPage);
            return;
        }

        rotatingItemSectionPages.put(player.getUniqueId(), sectionPage);
        rotatingItemSectionIds.put(player.getUniqueId(), sectionId);
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
                .title(editorTitle(ROTATING_ITEMS_SELECTOR_TITLE_PREFIX + section.id()))
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
                .title(editorTitle("Section Editor"))
                .context(new EditorBrowserContext(true, null))
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openSectionEditorList(player, sectionPage);
            return;
        }

        SectionDetailHolder holder = new SectionDetailHolder(sectionId, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Section: " + section.id()));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(10, button(player, Material.CHEST, "Products", List.of("Open product browser."), "browse products"));
        inventory.setItem(11, createSectionIconCopyItem(section));
        inventory.setItem(12, createItem(Material.HOPPER, "&#03fc88Product Layout", List.of(
                "&#ffffffRows: &#03fc88" + (publicGuis.snapshot() == null ? "?" : publicGuis.snapshot().require("shop.shop_section").rows()),
                "&#a7b8b0Edit guis/shop-section.yml to change the layout.")));
        inventory.setItem(13, button(player, Material.WRITABLE_BOOK, "Edit Description", List.of("Lines: " + section.description().size(), "Use | between lines."), "edit the description"));
        inventory.setItem(14, button(player, Material.COMPASS, "Main Menu Slot", List.of("Current: " + section.slot(), "Choose a slot in guis/main.yml entries.sections.slots."), "edit the menu slot"));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
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
                .title(editorTitle("Items: " + sectionId))
                .context(new EditorBrowserContext(false, sectionId))
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
                        "&#ffffffSell: &#03fc88" + formatPrice(item.sellPrice()),
                        "&#ffffffAccess: &#03fc88" + requiredPermissionLabel(item)
                )));
    }

    private void openItemEditor(Player player, String sectionId, String itemId, int page, int sectionPage) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            openSectionEditorList(player, sectionPage);
            return;
        }

        ShopItem item = section.items().stream().filter(current -> current.id().equalsIgnoreCase(itemId)).findFirst().orElse(null);
        if (item == null) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            openItemListEditor(player, sectionId, page, sectionPage);
            return;
        }

        ItemEditorHolder holder = new ItemEditorHolder(sectionId, item.id(), page, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 45, editorTitle("Edit: " + item.id()));
        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(13, createItem(item.material(), "&#03fc88" + item.id(), List.of(
                "&#ffffffCurrent product preview.",
                "&#ffffffType: &#03fc88" + item.type().name().toLowerCase(Locale.ROOT),
                "&#ffffffPage: &#03fc88" + (item.page() + 1),
                "&#ffffffAmount: &#03fc88" + item.amount(),
                "&#ffffffStack cap: &#03fc88" + item.effectiveStackSize(),
                "&#ffffffSlot: &#03fc88" + item.slot(),
                "&#ffffffAccess: &#03fc88" + requiredPermissionLabel(item)
        )));

        inventory.setItem(10, button(player, Material.NAME_TAG, "Edit Product ID", List.of("Current: " + item.id(), "Chat input, type cancel to abort."), "edit the product ID"));
        inventory.setItem(11, button(player, item.material(), "Edit Material", List.of("Current: " + item.material().name(), "Hold cursor item + click to copy material/name/lore/model/enchants."), "edit the material"));
        inventory.setItem(12, button(player, Material.CHEST, "Edit Amount", List.of("Current: " + item.amount(), "Type a number 1-" + item.effectiveStackSize()), "edit the amount"));

        inventory.setItem(14, button(player, Material.ITEM_FRAME, "Edit GUI Slot", List.of("Current: " + item.slot(), "Choose a slot in guis/shop-section.yml entries.products.slots."), "edit the GUI slot"));
        inventory.setItem(15, button(player, Material.EMERALD, "Edit Buy Price", List.of("Current: " + formatPrice(item.buyPrice()), "Type number or -1/disable."), "edit the buy price"));
        inventory.setItem(16, button(player, Material.GOLD_INGOT, "Edit Sell Price", List.of("Current: " + formatPrice(item.sellPrice()), "Type number or -1/disable."), "edit the sell price"));

        inventory.setItem(19, EditorItemFactory.cycle(player, plugin.getMessages(), "Edit Type", editorItemTypeValue(item.type()), ITEM_TYPE_OPTIONS));
        inventory.setItem(20, button(player, Material.MAP, "Edit Page", List.of("Current: " + (item.page() + 1), "Type a page number, starting at 1."), "edit the page"));
        inventory.setItem(21, button(player, Material.REPEATING_COMMAND_BLOCK, "Edit Action Data", List.of(
                "Controls commands, enchantments, or permissions granted by the product.",
                "Separate from Required Permission.",
                "Commands use | between lines."
        ), "edit action data"));
        inventory.setItem(22, button(player, Material.HOPPER, "Edit Stack Cap", List.of("Current: " + item.effectiveStackSize(), "Type a number 1-" + Math.min(64, item.material().getMaxStackSize())), "edit the stack cap"));
        inventory.setItem(23, button(player, Material.BARREL, "Edit Stock", List.of("Current: " + (item.stock() == null ? "unlimited" : item.stock()), "Type amount or -1 to disable."), "edit the stock"));
        inventory.setItem(24, button(player, Material.CLOCK, "Edit Buy Limit", List.of("Current: " + (item.buyLimit() == null ? "unlimited" : item.buyLimit()), "Type amount or -1 to disable."), "edit the buy limit"));
        inventory.setItem(28, button(player, Material.TRIPWIRE_HOOK, "Required Permission", List.of(
                "Current: " + requiredPermissionLabel(item),
                "Only players with this permission can see and buy this product.",
                "Type a permission node, or clear/disable/none/-1 to make it public."
        ), "edit the required permission"));
        inventory.setItem(25, button(player, Material.LAVA_BUCKET, FoStyle.BAD, "Remove Product", List.of("Deletes this product from section."), "open confirmation"));
        inventory.setItem(FIVE_ROW_BACK_SLOT, GUI_BUTTONS.back(player));

        openInventory(player, inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof FoHolder && !(holder instanceof SellGuiHolder)
                && event.getClickedInventory() != null && event.getClickedInventory().equals(top)
                && isBackButton(event.getCurrentItem())) {
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
            markBackNavigation(player);
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

        if (!hasProductAccess(player, shopItem)) {
            return;
        }

        if (shopItem.type() == ShopItemType.DUMMY) {
            return;
        }

        if (!shopItem.canBuy()) {
            plugin.sendConfigured(player, "buy-disabled");
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
            case 10 -> { }
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            openItemListEditor(player, holder.sectionId, holder.page, holder.sectionPage);
            return;
        }

        String current = editorItemTypeValue(item.type());
        String next = CycleOptions.nextValue(current, ITEM_TYPE_OPTIONS);
        if (updateItemField(holder.sectionId, holder.itemId, "type", next, player)) {
            plugin.sendConfigured(player, "editor-saved");
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
                plugin.sendConfigured(player, "rotating-shop-reset");
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
        int slot = event.getSlot();
        if (slot == holder.previousSlot) {
            if (holder.page > 0) {
                pageSound(player, holder.editor, false);
                openGlobalSellPriceList(player, holder.editor, holder.page - 1, holder.query, holder.sort);
            }
            return;
        }
        if (slot == holder.sortSlot) {
            String next = CycleOptions.nextValue(holder.sort.name(), WORTH_SORT_OPTIONS);
            if (holder.editor) {
                plugin.getEditorSounds().cycle(player);
            } else {
                plugin.getSounds().play(player, "gui.sort");
            }
            openGlobalSellPriceList(player, holder.editor, 0, holder.query, sortFromValue(next));
            return;
        }
        if (slot == holder.backSlot) {
            if (holder.editor) {
                openGlobalSellPriceEditor(player);
            }
            return;
        }
        if (slot == holder.searchSlot) {
            searchSound(player, holder.editor, false);
            startPrompt(player,
                    new PromptEdit(PromptType.GLOBAL_PRICE_SEARCH, holder.editor ? "editor" : "worth",
                            null, null, holder.page, holder.sort.ordinal(), holder.query),
                    "&#a7b8b0Expected: material search text. Type &#ff5d73cancel &#a7b8b0to abort.");
            return;
        }
        if (slot == holder.clearSearchSlot) {
            if (!holder.query.isBlank()) {
                searchSound(player, holder.editor, true);
                openGlobalSellPriceList(player, holder.editor, 0, "", holder.sort);
            }
            return;
        }
        if (slot == holder.nextSlot) {
            if (holder.page < holder.totalPages - 1) {
                pageSound(player, holder.editor, true);
                openGlobalSellPriceList(player, holder.editor, holder.page + 1, holder.query, holder.sort);
            }
            return;
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
                plugin.sendConfigured(player, "editor-saved");
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
                plugin.sendConfigured(player, "editor-saved");
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
        boolean sectionsSelector = editorTitle(ROTATING_SECTIONS_SELECTOR_TITLE).equals(title);
        boolean itemsSelector = title.startsWith(editorTitle(ROTATING_ITEMS_SELECTOR_TITLE_PREFIX));
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
                    String sectionId = rotatingItemSectionId(player);
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
                    String sectionId = rotatingItemSectionId(player);
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
                    String sectionId = rotatingItemSectionId(player);
                    int sectionPage = rotatingItemSectionPages.getOrDefault(player.getUniqueId(), 0);
                    saveConfigValue(player, "rotating-shop.items." + sectionId + "." + click.key(), next,
                            () -> openRotatingItemEditorList(player, sectionId, click.page(), sectionPage, click.filter()));
                }
            }
            case NONE -> {
            }
        }
    }

    private String rotatingItemSectionId(Player player) {
        return rotatingItemSectionIds.getOrDefault(player.getUniqueId(), "");
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", result.errorMessage()));
            plugin.getEditorSounds().error(player);
            if (reopen != null) {
                reopen.run();
            }
            return false;
        }
        plugin.sendConfigured(player, "editor-saved");
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
        if (!(request.context() instanceof EditorBrowserContext context)) {
            return;
        }
        boolean sectionBrowser = context.sections();
        boolean itemBrowser = !sectionBrowser && context.sectionId() != null;
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

        String sectionId = context.sectionId();
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
                    plugin.sendConfigured(player, "editor-saved");
                    plugin.getEditorSounds().add(player);
                    openSectionDetailEditor(player, holder.sectionId, holder.sectionPage);
                }
            }
            case 12 -> { }
            case 13 -> startPrompt(player,
                    new PromptEdit(PromptType.SECTION_DESCRIPTION, null, holder.sectionId, null, holder.sectionPage, 0),
                    "&#a7b8b0Expected: section description lines separated by &#03fc88|&#a7b8b0. Type &#ff5d73cancel &#a7b8b0to abort, or &#ff5d73clear &#a7b8b0to remove.");
            case 14 -> startPrompt(player,
                    new PromptEdit(PromptType.SECTION_SLOT, null, holder.sectionId, null, holder.sectionPage, 0),
                    "&#a7b8b0Expected: a slot in guis/main.yml entries.sections.slots. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 15 -> {
                if (toggleSectionEnabled(holder.sectionId, player)) {
                    plugin.sendConfigured(player, "editor-saved");
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
                        plugin.sendConfigured(player, "editor-saved");
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
                    "&#a7b8b0Action data grants commands, enchantments, or permissions. It is separate from Required Permission. Type &#ff5d73cancel &#a7b8b0to abort.");
            case FIVE_ROW_BACK_SLOT -> openItemListEditor(player, holder.sectionId, holder.page, holder.sectionPage);
            case 23 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_STOCK, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: stock amount, or -1/disable. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 24 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_BUY_LIMIT, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Expected: buy limit amount, or -1/disable. Type &#ff5d73cancel &#a7b8b0to abort.");
            case 28 -> startPrompt(player,
                    new PromptEdit(PromptType.ITEM_REQUIRED_PERMISSION, null, holder.sectionId, holder.itemId, holder.page, holder.sectionPage),
                    "&#a7b8b0Type a permission node, or clear/disable/none/-1 to make this product public. Type &#ff5d73cancel &#a7b8b0to abort.");
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
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Confirm Delete"));
        holder.setInventory(inventory);

        fillBackground(inventory);
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, "&#3ecf8eConfirm", List.of("&#ffffffDelete product: &#03fc88" + itemId)));
        inventory.setItem(15, createItem(Material.RED_CONCRETE, "&#ff5d73Cancel", List.of("&#ffffffReturn to product editor.")));

        openInventory(player, inventory);
    }

    private void openConfirmDeleteSection(Player player, String sectionId, int sectionPage) {
        ConfirmDeleteSectionHolder holder = new ConfirmDeleteSectionHolder(sectionId, sectionPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Confirm Delete"));
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
                plugin.sendConfigured(player, "editor-saved");
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
            plugin.sendConfigured(player, "editor-cancelled");
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
                plugin.sendConfigured(player, "editor-saved");
                plugin.getEditorSounds().delete(player);
            } else {
                plugin.getEditorSounds().error(player);
            }
            openSectionEditorList(player, holder.sectionPage);
            return;
        }

        if (event.getSlot() == 15) {
            suppressConfirmDeleteClose.add(player.getUniqueId());
            plugin.sendConfigured(player, "editor-cancelled");
            markBackNavigation(player);
            openSectionDetailEditor(player, holder.sectionId, holder.sectionPage);
        }
    }

    private void openConfirmRemoveSellBooster(Player player, String boosterId, int page) {
        ConfirmRemoveSellBoosterHolder holder = new ConfirmRemoveSellBoosterHolder(boosterId, page);
        Inventory inventory = Bukkit.createInventory(holder, 27, editorTitle("Confirm Remove"));
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
                plugin.sendConfigured(player, "editor-saved");
                plugin.getSounds().play(player, "shop.booster-removed");
            } else {
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Booster not found."));
                plugin.getEditorSounds().error(player);
            }
            openActiveSellBoosters(player, holder.page);
            return;
        }

        if (event.getSlot() == 15) {
            suppressConfirmRemoveBoosterClose.add(player.getUniqueId());
            plugin.sendConfigured(player, "editor-cancelled");
            markBackNavigation(player);
            openActiveSellBoosters(player, holder.page);
        }
    }

    private void openBuyItemGui(Player player, String sectionId, String itemId, int selectedAmount) {
        ShopItem item = findPublicItem(sectionId, itemId);
        if (item == null || publicGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        if (!item.canBuy()) {
            plugin.sendConfigured(player, "buy-disabled");
            return;
        }
        if (!hasProductAccess(player, item)) {
            denyProductAccess(player, sectionId);
            return;
        }
        int amount = Math.clamp(selectedAmount, 1, item.effectiveStackSize());
        int slot = publicGuis.snapshot().require("shop.buy_item").entries().get("preview").slots().getFirst();
        ItemStack source = createPurchaseStack(item, amount);
        String itemName = DialogIcons.fallbackText(purchaseDisplayName(player, sectionId, item, amount));
        Map<String, String> values = Map.of("item", itemName, "amount", String.valueOf(amount),
                "price", plugin.getEconomyService().formatCompact(item.buyPrice() * amount));
        publicBuys.put(player.getUniqueId(), new BuyContext(sectionId, itemId, amount,
                plugin.getShopManager().priceRevision(), publicGuis.snapshot().revision()));
        publicGuis.open(player, "shop.buy_item", new PublicGuiRenderInput(values,
                supportsBulkBuy(item) ? Map.of() : Map.of("bulk", "unavailable"), Map.of(),
                Map.of("preview", Map.of(slot, new PublicGuiRenderInput.EntryValue(item.id(), source, values)))));
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    private void openBuyMoreGui(Player player, String sectionId, String itemId, int returnAmount) {
        ShopItem item = findPublicItem(sectionId, itemId);
        if (item == null || publicGuis.snapshot() == null) {
            plugin.sendConfigured(player, "gui-unavailable");
            return;
        }
        if (!item.canBuy()) {
            plugin.sendConfigured(player, "buy-disabled");
            return;
        }
        if (!hasProductAccess(player, item)) {
            denyProductAccess(player, sectionId);
            return;
        }
        if (!supportsBulkBuy(item)) {
            openBuyItemGui(player, sectionId, itemId, returnAmount);
            return;
        }
        List<Integer> slots = publicGuis.snapshot().require("shop.buy_more").entries().get("options").slots();
        List<Integer> options = visibleBulkStackOptions();
        Map<Integer, PublicGuiRenderInput.EntryValue> entries = new HashMap<>();
        String itemName = DialogIcons.fallbackText(purchaseDisplayName(player, sectionId, item, returnAmount));
        for (int index = 0; index < options.size(); index++) {
            int stacks = options.get(index);
            int amount = stacks * item.effectiveStackSize();
            entries.put(slots.get(index), new PublicGuiRenderInput.EntryValue(String.valueOf(stacks), null,
                    Map.of("item", itemName, "stacks", String.valueOf(stacks),
                            "plural", stacks == 1 ? "" : publicValue("stack-plural-suffix"), "amount", String.valueOf(amount),
                            "stack_unit", publicValue(stacks == 1 ? "stack-singular" : "stack-plural"),
                            "price", plugin.getEconomyService().formatCompact(item.buyPrice() * amount))));
        }
        publicBuys.put(player.getUniqueId(), new BuyContext(sectionId, itemId, returnAmount,
                plugin.getShopManager().priceRevision(), publicGuis.snapshot().revision()));
        publicGuis.open(player, "shop.buy_more", new PublicGuiRenderInput(Map.of("item", itemName),
                Map.of(), Map.of(), Map.of("options", entries)));
        screenOpened(player, player.getOpenInventory().getTopInventory().getHolder());
    }

    private void placeAmountSelectionButtons(Player player, Inventory inventory, BuyItemHolder holder, ShopItem item, int amount,
                                             Set<Integer> occupied) {
        placeAmountButton(player, inventory, holder, "remove-64", 10, -64, Material.RED_STAINED_GLASS_PANE, "&#ff5d73-64", List.of("&#ffffffRemove 64."), item, amount, occupied);
        placeAmountButton(player, inventory, holder, "remove-10", 11, -10, Material.RED_STAINED_GLASS_PANE, "&#ff5d73-10", List.of("&#ffffffRemove 10."), item, amount, occupied);
        placeAmountButton(player, inventory, holder, "remove-1", 12, -1, Material.RED_STAINED_GLASS_PANE, "&#ff5d73-1", List.of("&#ffffffRemove 1."), item, amount, occupied);
        placeAmountButton(player, inventory, holder, "add-1", 14, 1, Material.LIME_STAINED_GLASS_PANE, "&#3ecf8e+1", List.of("&#ffffffAdd 1."), item, amount, occupied);
        placeAmountButton(player, inventory, holder, "add-10", 15, 10, Material.LIME_STAINED_GLASS_PANE, "&#3ecf8e+10", List.of("&#ffffffAdd 10."), item, amount, occupied);
        placeAmountButton(player, inventory, holder, "add-64", 16, 64, Material.LIME_STAINED_GLASS_PANE, "&#3ecf8e+64", List.of("&#ffffffAdd 64."), item, amount, occupied);

        GuiButton cancel = resolveGuiButton("buy-item", "buttons.cancel", THREE_ROW_BACK_SLOT, Material.IRON_DOOR,
                "&#ff5d73Back", List.of("&#ffffffReturn to the shop section."), inventory.getSize(), occupied);
        holder.cancelSlot = cancel.slot();
        inventory.setItem(holder.cancelSlot, createButtonItem(player, cancel));

        if (supportsBulkBuy(item)) {
            GuiButton bulk = resolveGuiButton("buy-item", "buttons.bulk", 21, Material.HOPPER,
                    "&#03fc88Buy More", List.of("&#ffffffChoose stack amount preset."), inventory.getSize(), occupied);
            holder.bulkSlot = bulk.slot();
            inventory.setItem(bulk.slot(), createButtonItem(player, bulk, formatAmountLine(bulk.name(), item, amount),
                    bulk.lore().stream().map(line -> formatAmountLine(line, item, amount)).toList()));
        } else {
            holder.bulkSlot = -1;
        }

        GuiButton confirm = resolveGuiButton("buy-item", "buttons.confirm", 23, Material.LIME_SHULKER_BOX,
                "&#3ecf8eConfirm Buy", List.of(
                        "&#ffffffBuy: &#03fc88{amount}",
                        "&#ffffffPrice: &#03fc88{price}"
                ), inventory.getSize(), occupied);
        holder.confirmSlot = confirm.slot();
        inventory.setItem(confirm.slot(), createButtonItem(player, confirm, formatAmountLine(confirm.name(), item, amount),
                confirm.lore().stream().map(line -> formatAmountLine(line, item, amount)).toList()));
    }

    private void placeAmountButton(Player player, Inventory inventory, BuyItemHolder holder, String key, int defaultSlot, int defaultDelta,
                                   Material defaultMaterial, String defaultName, List<String> defaultLore, ShopItem item, int amount,
                                   Set<Integer> occupied) {
        ConfigurationSection section = plugin.getFoConfig().guiSection("buy-item", "buttons." + key);
        int delta = section == null ? defaultDelta : section.getInt("change", defaultDelta);
        GuiButton button = resolveGuiButton("buy-item", "buttons." + key, defaultSlot, defaultMaterial, defaultName,
                defaultLore, inventory.getSize(), occupied);
        holder.slotToDelta.put(button.slot(), delta);
        inventory.setItem(button.slot(), createButtonItem(player, button, formatAmountLine(button.name(), item, amount),
                button.lore().stream().map(line -> formatAmountLine(line, item, amount)).toList()));
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

    private GuiButton resolveGuiButton(String file, String path, int defaultSlot, Material defaultMaterial,
                                       String defaultName, List<String> defaultLore, int inventorySize,
                                       Set<Integer> occupied) {
        GuiButton configured = readGuiButton(file, path, defaultSlot, defaultMaterial, defaultName, defaultLore, inventorySize);
        int resolvedSlot = GuiSlotResolver.resolve(configured.slot(), defaultSlot, inventorySize, occupied);
        if (resolvedSlot != configured.slot()) {
            logGuiWarning(file, path + ".slot " + configured.slot() + " collides with visible content; using " + resolvedSlot + ".");
        }
        return new GuiButton(resolvedSlot, configured.material(), configured.amount(), configured.name(), configured.lore(), configured.customModelData());
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

    private ItemStack pageButton(Player player, GuiButton button, int page, int totalPages) {
        return configuredButton(player, button, Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(totalPages)
        ));
    }

    private void placeWorthControls(Player player, Inventory inventory, GlobalSellPriceListHolder holder,
                                    int currentPage, int totalPages, String search, WorthSort sort) {
        Set<Integer> occupied = new HashSet<>();
        for (int contentSlot : GLOBAL_PRICE_SLOTS) {
            occupied.add(contentSlot);
        }

        GuiButton previous = resolveGuiButton("worth", "buttons.previous", 45, Material.ARROW,
                "&#03fc88Previous Page", List.of("&#ffffffPage {page}/{pages}"), inventory.getSize(), occupied);
        GuiButton sortButton = resolveGuiButton("worth", "buttons.sort", 47, Material.COMPARATOR,
                "&#03fc88Sort: {sort}", List.of("&#ffffffNext: {next_sort}"), inventory.getSize(), occupied);
        GuiButton searchButton = resolveGuiButton("worth", "buttons.search", 51, Material.OAK_SIGN,
                "&#03fc88Search", List.of("&#ffffffCurrent: {query}"), inventory.getSize(), occupied);
        GuiButton clearSearch = resolveGuiButton("worth", "buttons.clear-search", 52, Material.BARRIER,
                "&#ff5d73Clear Search", List.of(), inventory.getSize(), occupied);
        GuiButton next = resolveGuiButton("worth", "buttons.next", 53, Material.ARROW,
                "&#03fc88Next Page", List.of("&#ffffffPage {page}/{pages}"), inventory.getSize(), occupied);

        holder.previousSlot = previous.slot();
        holder.sortSlot = sortButton.slot();
        holder.searchSlot = searchButton.slot();
        holder.clearSearchSlot = clearSearch.slot();
        holder.nextSlot = next.slot();

        if (currentPage > 0) {
            inventory.setItem(previous.slot(), pageButton(player, previous, currentPage, totalPages));
        }
        String currentSort = worthSortLabel(sort);
        String nextSort = worthSortLabel(sort == WorthSort.NAME ? WorthSort.PRICE : WorthSort.NAME);
        inventory.setItem(sortButton.slot(), configuredButton(player, sortButton, Map.of(
                "sort", currentSort,
                "next_sort", nextSort
        )));
        String visibleQuery = search == null || search.isBlank()
                ? publicValue("worth-search-empty")
                : search;
        inventory.setItem(searchButton.slot(), configuredButton(player, searchButton, Map.of("query", visibleQuery)));
        if (search != null && !search.isBlank()) {
            inventory.setItem(clearSearch.slot(), createButtonItem(player, clearSearch));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(next.slot(), pageButton(player, next, currentPage + 2, totalPages));
        }
    }

    private String worthSortLabel(WorthSort sort) {
        return publicValue(sort == WorthSort.PRICE ? "worth-sort-price" : "worth-sort-name");
    }

    private String publicValue(String key) {
        String path = "public-values." + key;
        return plugin.getConfig().isSet(path) ? Objects.toString(plugin.getConfig().getString(path), "") : "";
    }

    private String publicMaterialName(Material material) {
        return publicItemName("materials", material.name(), prettify(material.name()));
    }

    private String publicPotionName(String potionType) {
        return publicItemName("potions", potionType, prettify(potionType) + publicValue("potion-suffix"));
    }

    private String publicItemName(String kind, String id, String fallback) {
        return publicItemNames.getOrDefault(kind + "." + id, fallback);
    }

    private int publicIntValue(String key) {
        String path = "public-values." + key;
        return plugin.getConfig().isSet(path) ? plugin.getConfig().getInt(path) : 0;
    }

    private DialogButton publicDialogButton(String key) {
        return new DialogButton(publicValue(key + "-label"), publicValue(key + "-tooltip"),
                publicIntValue(key + "-width"), publicValue(key + "-icon"));
    }

    private ItemStack configuredButton(Player player, GuiButton button, Map<String, String> placeholders) {
        String name = replaceGuiPlaceholders(button.name(), placeholders);
        List<String> lore = button.lore().stream().map(line -> replaceGuiPlaceholders(line, placeholders)).toList();
        return createButtonItem(player, button, name, lore);
    }

    private String replaceGuiPlaceholders(String input, Map<String, String> placeholders) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    private int[] configuredSlots(String file, String path, int[] defaults, int maxSlots, int inventorySize) {
        List<Integer> configured = plugin.getFoConfig().guiIntegerList(file, path, List.of());
        int[] resolved = GuiSlotResolver.uniqueSlots(configured, defaults, maxSlots, inventorySize);
        if (!configured.isEmpty() && resolved.length < Math.min(maxSlots, defaults.length)) {
            logGuiWarning(file, path + " does not contain enough unique valid slots; safe defaults filled the missing positions.");
        }
        return resolved;
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
        if (!section.contains(path)) {
            return fallback;
        }
        List<String> values = section.getStringList(path);
        return values;
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
                .replace("{price}", plugin.getEconomyService().formatCompact(item.buyPrice() * amount));
    }

    private String formatBuyMoreLine(String line, ShopItem item, int amount, int stacks) {
        return formatAmountLine(line, item, amount)
                .replace("{stacks}", String.valueOf(stacks))
                .replace("{plural}", stacks == 1 ? "" : publicValue("stack-plural-suffix"));
    }

    private boolean supportsBulkBuy(ShopItem item) {
        return item.type() != ShopItemType.PERMISSION
                && !visibleBulkStackOptions().isEmpty();
    }

    private List<Integer> visibleBulkStackOptions() {
        if (publicGuis.snapshot() == null) return List.of();
        return visibleBulkStackOptions(bulkStackOptions,
                publicGuis.snapshot().require("shop.buy_more").entries().get("options"));
    }

    static List<Integer> visibleBulkStackOptions(List<Integer> options, PublicGuiDefinition.Entry region) {
        return region.enabled() ? options.subList(0, Math.min(options.size(), region.slots().size())) : List.of();
    }

    static List<Integer> configuredBulkStackOptions(FileConfiguration config) {
        String path = "public-gui.bulk-stack-options";
        if (!config.isSet(path) || !(config.get(path) instanceof List<?> raw)) {
            return List.of();
        }
        Set<Integer> options = new LinkedHashSet<>();
        for (Object value : raw) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() != number.intValue() || number.intValue() < 1
                    || number.intValue() > 9 || !options.add(number.intValue())) {
                return List.of();
            }
        }
        return List.copyOf(options);
    }

    static boolean invalidBulkStackOptions(FileConfiguration config, List<Integer> options) {
        String path = "public-gui.bulk-stack-options";
        if (!config.isSet(path)) return false; // Owner removed this optional choice list.
        Object raw = config.get(path);
        return !(raw instanceof List<?> list) || !list.isEmpty() && options.isEmpty();
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
            plugin.getSounds().play(player, "gui.cycle");
            openBuyItemGui(player, holder.sectionId, holder.itemId, holder.selectedAmount + delta);
            return;
        }

        if (event.getSlot() == holder.confirmSlot) {
            if (buyItem(player, holder.sectionId, item, holder.selectedAmount)) {
                openBuyItemGui(player, holder.sectionId, holder.itemId, holder.selectedAmount);
            }
            return;
        }

        if (event.getSlot() == holder.bulkSlot) {
            openBuyMoreGui(player, holder.sectionId, holder.itemId, holder.selectedAmount);
            return;
        }

        if (event.getSlot() == holder.cancelSlot) {
            markBackNavigation(player);
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
            if (buyItem(player, holder.sectionId, item, amount)) {
                openBuyMoreGui(player, holder.sectionId, holder.itemId, holder.returnAmount);
            }
        }
    }

    private boolean buyItem(Player player, String sectionId, ShopItem shopItem, int amount) {
        ShopSection currentSection = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (!isCurrentPublicItem(currentSection, shopItem)) {
            plugin.sendConfigured(player, "gui-unavailable");
            openMainShop(player);
            return false;
        }
        amount = shopItem.type() == ShopItemType.PERMISSION ? 1 : Math.max(1, amount);

        if (!hasProductAccess(player, shopItem)) {
            denyProductAccess(player, sectionId);
            return false;
        }

        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendConfigured(player, "no-economy");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        if (!shopItem.canBuy() || !isFinitePositiveOrZero(shopItem.buyPrice())) {
            plugin.sendConfigured(player, "buy-disabled");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        if (!hasConfiguredPurchaseData(shopItem)) {
            plugin.sendConfigured(player, "buy-disabled");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        if (shopItem.type() == ShopItemType.PERMISSION && !canBuyPermissionItem(player, shopItem)) {
            return true;
        }

        int stock = plugin.getShopManager().getStock(sectionId, shopItem.id());
        if (stock >= 0 && amount > stock) {
            plugin.sendConfigured(player, "stock-insufficient", Map.of("{stock}", String.valueOf(stock)));
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        int remainingLimit = plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), sectionId, shopItem);
        if (remainingLimit >= 0 && amount > remainingLimit) {
            plugin.sendConfigured(player, "buy-limit-reached", Map.of("{limit}", String.valueOf(remainingLimit)));
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        double totalCost = shopItem.buyPrice() * amount;
        if (totalCost < 0D || !Double.isFinite(totalCost)) {
            plugin.sendConfigured(player, "transaction-failed");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }
        if (plugin.getEconomyService().balance(player) < totalCost) {
            plugin.sendConfigured(player, "not-enough-money", Map.of("{amount}", plugin.getEconomyService().formatCompact(totalCost)));
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        if (requiresInventorySpace(shopItem) && !canFitExact(player.getInventory(), shopItem, amount)) {
            plugin.sendConfigured(player, "inventory-full");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        if (!plugin.getEconomyService().withdraw(player, totalCost)) {
            plugin.sendConfigured(player, "transaction-failed");
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        int purchasedAmount = executePurchaseDelivery(player, sectionId, shopItem, amount);
        if (purchasedAmount < amount) {
            double refund = (amount - purchasedAmount) * shopItem.buyPrice();
            plugin.getEconomyService().deposit(player, refund);
            plugin.sendConfigured(player, "inventory-full");
        }

        if (purchasedAmount <= 0) {
            plugin.getSounds().play(player, "shop.purchase-failure");
            return true;
        }

        plugin.getShopManager().recordPurchase(player.getUniqueId(), sectionId, shopItem, purchasedAmount);
        plugin.sendConfigured(player, "buy-success", Map.of(
                "{amount}", String.valueOf(purchasedAmount),
                "{item}", purchaseDisplayName(player, sectionId, shopItem, purchasedAmount),
                "{price}", plugin.getEconomyService().formatCompact(shopItem.buyPrice() * purchasedAmount)
        ));
        plugin.getSounds().play(player, "shop.purchase");
        return true;
    }

    private String purchaseDisplayName(Player player, String sectionId, ShopItem item, int amount) {
        ItemStack source = createPurchaseStack(item, 1);
        ItemMeta meta = source.getItemMeta();
        String name = meta != null && meta.hasDisplayName()
                ? meta.getDisplayName() : publicMaterialName(item.material());
        return applyTextPlaceholders(player, sectionId, item, amount, name);
    }

    private boolean hasProductAccess(Player player, ShopItem item) {
        return item == null || item.requiredPermission().isBlank()
                || plugin.getPermissionService().has(player, item.requiredPermission());
    }

    private void denyProductAccess(Player player, String sectionId) {
        plugin.sendConfigured(player, "product-permission-required");
        plugin.getSounds().play(player, "shop.purchase-failure");
        plugin.getShopManager().getSection(sectionId).ifPresent(section -> openShopSection(player, section));
    }

    private boolean canBuyPermissionItem(Player player, ShopItem item) {
        if (item.permission() == null || item.permission().isBlank()) {
            return false;
        }
        if (!plugin.getPermissionService().isEnabled()) {
            plugin.sendConfigured(player, "permission-provider-missing");
            return false;
        }
        if (plugin.getPermissionService().has(player, item.permission())) {
            plugin.sendConfigured(player, "permission-already-owned");
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
        Set<Integer> inputSlots = sellGuiInputSlots(top);

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
            if (slot == holder.sellSlot) {
                event.setCancelled(true);
                if (!holder.frame.bindings().containsKey(slot)) {
                    return;
                }
                if (denyBlockedSellGamemode(player)) {
                    return;
                }
                if (!plugin.getEconomyService().isAvailable()) {
                    plugin.sendConfigured(player, "no-economy");
                    return;
                }
                if (!sellingInProgress.add(player.getUniqueId())) {
                    plugin.sendConfigured(player, "sellgui-already-processing");
                    return;
                }
                sellFromGui(player, top);
                return;
            }

            if (!inputSlots.contains(slot)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack incoming = event.getCurrentItem();
            if (incoming == null || incoming.getType() == Material.AIR) {
                return;
            }
            ItemStack remaining = incoming.clone();
            for (int slot : inputSlots) {
                ItemStack existing = top.getItem(slot);
                if (existing == null || existing.getType() == Material.AIR || !existing.isSimilar(remaining)) {
                    continue;
                }
                int transfer = Math.min(remaining.getAmount(), existing.getMaxStackSize() - existing.getAmount());
                if (transfer > 0) {
                    existing.setAmount(existing.getAmount() + transfer);
                    top.setItem(slot, existing);
                    remaining.setAmount(remaining.getAmount() - transfer);
                }
                if (remaining.getAmount() == 0) break;
            }
            if (remaining.getAmount() > 0) {
                for (int slot : inputSlots) {
                    ItemStack existing = top.getItem(slot);
                    if (existing != null && existing.getType() != Material.AIR) continue;
                    int transfer = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                    ItemStack moved = remaining.clone();
                    moved.setAmount(transfer);
                    top.setItem(slot, moved);
                    remaining.setAmount(remaining.getAmount() - transfer);
                    if (remaining.getAmount() == 0) break;
                }
            }
            event.setCurrentItem(remaining.getAmount() == 0 ? null : remaining);
        }
    }

    private boolean isProtectedSellSlot(int rawSlot, Inventory top, SellGuiHolder holder) {
        return rawSlot >= 0 && rawSlot < top.getSize()
                && !sellGuiInputSlots(top).contains(rawSlot);
    }

    private boolean canAcceptSellInputItem(Inventory top, SellGuiHolder holder, ItemStack incoming) {
        if (incoming == null || incoming.getType() == Material.AIR) {
            return false;
        }
        for (int slot : sellGuiInputSlots(top)) {
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
        Set<Integer> inputs = sellGuiInputSlots(top);
        for (int slot = 0; slot < top.getSize(); slot++) {
            if (!inputs.contains(slot) && isSimilarNonAir(cursor, top.getItem(slot))) {
                return true;
            }
        }
        for (int slot : holder.decorationSlots) {
            if (slot >= 0 && slot < top.getSize() && isSimilarNonAir(cursor, top.getItem(slot))) {
                return true;
            }
        }
        if (holder.sellSlot >= 0 && holder.sellSlot < top.getSize() && isSimilarNonAir(cursor, top.getItem(holder.sellSlot))) {
            return true;
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
            plugin.sendConfigured(player, "sellall-empty");
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
            plugin.sendConfigured(player, "sellhand-empty");
            plugin.getSounds().play(player, "sell.failure");
            return;
        }

        SellResult result = processSellItem(player, hand.clone(), new SellProcessingContext());
        if (result.soldUnits <= 0) {
            sellingInProgress.remove(player.getUniqueId());
            plugin.sendConfigured(player, "sellgui-none");
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
        if (denyBlockedSellGamemode(player)) {
            return false;
        }

        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendConfigured(player, "no-economy");
            plugin.getSounds().play(player, "sell.failure");
            return false;
        }

        if (!sellingInProgress.add(player.getUniqueId())) {
            plugin.sendConfigured(player, "sellgui-already-processing");
            return false;
        }

        return true;
    }

    private boolean denyBlockedSellGamemode(Player player) {
        String gamemode = player.getGameMode().name();
        if (!plugin.getFoConfig().isSellGamemodeBlocked(gamemode)) {
            return false;
        }
        String displayName = plugin.getFoConfig().publicGamemodeName(gamemode);
        plugin.sendConfigured(player, "sellgui-gamemode-not-allowed", Map.of("{gamemode}", displayName));
        plugin.getSounds().play(player, "sell.failure");
        return true;
    }

    private void sellFromGui(Player player, Inventory inventory) {
        List<ItemStack> snapshot = new ArrayList<>();

        for (int slot : sellGuiInputSlots(inventory)) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            snapshot.add(item.clone());
            inventory.setItem(slot, null);
        }

        if (snapshot.isEmpty()) {
            sellingInProgress.remove(player.getUniqueId());
            plugin.sendConfigured(player, "sellgui-empty");
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
                        plugin.sendConfigured(player, "sellgui-inventory-full");
                    }
                    plugin.sendConfigured(player, "no-economy");
                    plugin.getSounds().play(player, "sell.failure");
                    return;
                }
                if (overflowed) {
                    plugin.sendConfigured(player, "sellgui-inventory-full");
                }
                sendSellSuccess(player, totalResult);
                plugin.getSounds().play(player, "sell.success");
                transactionLogger.write(player, totalResult.soldUnits, formatSellMoney(totalResult.earned), buildSoldList(totalResult));
            } else {
                if (overflowed) {
                    plugin.sendConfigured(player, "sellgui-inventory-full");
                }
                plugin.sendConfigured(player, "sellgui-none");
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

        if (plugin.getFoConfig().getSellReceiptType() == 1 && !result.soldLines.isEmpty()) {
            sendReceiptMessage(player, placeholders, result);
        } else {
            plugin.sendConfigured(player, "sellgui-success", placeholders);
        }

        if (plugin.getFoConfig().isSellTitlesEnabled()
                && (!plugin.getFoConfig().getSellTitleMessage().isBlank()
                || !plugin.getFoConfig().getSellSubtitleMessage().isBlank())) {
            player.sendTitle(
                    Text.format(plugin.getFoConfig().getSellTitleMessage(), placeholders),
                    Text.format(plugin.getFoConfig().getSellSubtitleMessage(), placeholders),
                    10,
                    50,
                    20
            );
        }

        if (plugin.getFoConfig().isSellActionBarEnabled()
                && !plugin.getFoConfig().getSellActionBarMessage().isBlank()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(Text.format(plugin.getFoConfig().getSellActionBarMessage(), placeholders)));
        }
    }

    private void sendReceiptMessage(Player player, Map<String, String> placeholders, SellResult result) {
        String receiptText = plugin.configuredMessageTemplate("sellgui-receipt-text");
        if (receiptText.isBlank()) {
            plugin.sendConfigured(player, "sellgui-success", placeholders);
            return;
        }
        Component message = plugin.renderConfiguredComponent(player, "sellgui-success", placeholders);
        Component separator = receiptSeparator(plugin.renderConfiguredSeparator("sellgui-receipt-separator"));
        Component receipt = plugin.getMessages().renderTemplateComponent(player, receiptText, Map.of())
                .hoverEvent(DialogIcons.render(player, buildReceiptHover(placeholders, result)));
        player.sendMessage(message.append(separator).append(receipt));
    }

    private String buildReceiptHover(Map<String, String> placeholders, SellResult result) {
        String title = Text.format(plugin.getFoConfig().getSellReceiptTitle(), placeholders);
        String layout = plugin.getFoConfig().getSellReceiptItemLayout();
        String lineSeparator = plugin.renderConfiguredSeparator("sellgui-receipt-line-separator");
        StringBuilder builder = new StringBuilder(title);
        for (SoldLine line : result.soldLines) {
            builder.append(Text.colorize(formatSoldLine(layout, line))).append(lineSeparator);
        }
        return builder.toString();
    }

    private String buildSoldList(SellResult result) {
        List<String> lines = new ArrayList<>();
        for (SoldLine line : result.soldLines) {
            lines.add(plugin.renderConfigured("sellgui-list-item-layout", Map.of(
                    "{amount}", String.valueOf(line.amount()), "{item}", soldLineItemName(line))));
        }
        return String.join(plugin.renderConfiguredSeparator("sellgui-list-separator"), lines);
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
        return publicMaterialName(stack.getType());
    }

    private String formatSellMoney(double amount) {
        return plugin.getFoConfig().formatSellMoney(amount, plugin.getEconomyService().formatCompact(amount));
    }

    private String formatSoldLine(String layout, SoldLine line) {
        return layout
                .replace("{amount}", String.valueOf(line.amount()))
                .replace("%amount%", String.valueOf(line.amount()))
                .replace("{item}", soldLineItemName(line))
                .replace("%item%", soldLineItemName(line))
                .replace("{price}", formatSellMoney(line.price()))
                .replace("%price%", formatSellMoney(line.price()))
                .replace("{earning}", formatSellMoney(line.price()))
                .replace("%earning%", formatSellMoney(line.price()));
    }

    private String soldLineItemName(SoldLine line) {
        return line.overflow() ? plugin.renderConfigured("sellgui-receipt-other-items") : line.item();
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

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < top.getSize() && !sellGuiInputSlots(top).contains(rawSlot)) {
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
        boolean suppressed = plugin.getCore() != null
                && plugin.getCore().inventoryCloseSuppressor().consumeSuppressedClose(player);
        if (suppressed && !(top.getHolder() instanceof SellGuiHolder)) {
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
                plugin.sendConfigured(player, "editor-cancelled");
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
                plugin.sendConfigured(player, "editor-cancelled");
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
                plugin.sendConfigured(player, "editor-cancelled");
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
        if (denyBlockedSellGamemode(player)) {
            returnUnsoldItems(player, top);
            return;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            returnUnsoldItems(player, top);
            plugin.sendConfigured(player, "no-economy");
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
        publicSections.remove(uuid);
        publicBuys.remove(uuid);
        publicWorth.remove(uuid);
        chatPrompts.clear(event.getPlayer());
        rotatingItemSectionPages.remove(uuid);
        rotatingItemSectionIds.remove(uuid);
        sectionItemBrowserPages.remove(uuid);
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
            plugin.sendConfigured(player, outcome.feedbackMessage);
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
        plugin.sendConfigured(player, "editor-cancelled");
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Value must be a number."));
                    return PromptOutcome.failure();
                }
                if (!Double.isFinite(value) || value < 0D) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Value must be 0 or higher."));
                    return PromptOutcome.failure();
                }
                return savePromptConfigValue(player, edit, value);
            }
            case CONFIG_SOUND -> {
                if (!SoundTypes.isValid(input)) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Invalid Bukkit sound."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Main shop rows must be a number."));
                    return PromptOutcome.failure();
                }
                if (rows < 1 || rows > 6) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Main shop rows must be 1-6."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Size must be a number."));
                    return PromptOutcome.failure();
                }

                if (size < 9 || size > 54 || size % 9 != 0) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Size must be 9-54 and divisible by 9."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Main menu slot must be a number."));
                    return PromptOutcome.failure();
                }
                if (!plugin.getValidationService().publicMainSlots().contains(slot)) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Slot is outside guis/main.yml entries.sections.slots."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product id is empty."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Invalid material."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Amount must be a number."));
                    return PromptOutcome.failure();
                }

                ShopItem item = findItem(edit.sectionId, edit.itemId);
                if (item == null) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
                    return PromptOutcome.failure();
                }

                int maxStack = item.effectiveStackSize();
                if (amount < 1 || amount > maxStack) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Amount must be 1-" + maxStack + "."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Slot must be a number."));
                    return PromptOutcome.failure();
                }

                ShopSection section = plugin.getShopManager().getSection(edit.sectionId).orElse(null);
                if (section == null) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
                    return PromptOutcome.failure();
                }

                if (!plugin.getValidationService().publicProductSlots().contains(slot)) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Slot is outside guis/shop-section.yml entries.products.slots."));
                    return PromptOutcome.failure();
                }

                if (isSlotTakenByOther(section, edit.itemId, slot)) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Another product already uses that slot."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Page must be a number."));
                    return PromptOutcome.failure();
                }
                if (page < 1) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Page must be 1 or higher."));
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "page", page - 1, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_REQUIRED_PERMISSION -> {
                String requiredPermission = input.isBlank() || isDisableInput(input) ? "" : input.trim();
                if (!requiredPermission.isBlank() && requiredPermission.chars().anyMatch(Character::isWhitespace)) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Permission node cannot contain spaces."));
                    return PromptOutcome.failure();
                }
                if (updateItemField(edit.sectionId, edit.itemId, "required-permission", requiredPermission, player)) {
                    return PromptOutcome.success(edit.itemId);
                }
                return PromptOutcome.failure();
            }
            case ITEM_ACTION -> {
                ShopItem item = findItem(edit.sectionId, edit.itemId);
                if (item == null) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
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
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Stack cap must be a number."));
                    return PromptOutcome.failure();
                }

                ShopItem item = findItem(edit.sectionId, edit.itemId);
                if (item == null) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
                    return PromptOutcome.failure();
                }

                int maxStack = Math.max(1, Math.min(64, item.material().getMaxStackSize()));
                if (stackSize < 1 || stackSize > maxStack) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Stack cap must be 1-" + maxStack + "."));
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
                        plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Failed to save enchantment price."));
                        return PromptOutcome.failure();
                    }
                    plugin.getRotatingShopService().reload();
                    return PromptOutcome.success(edit.itemId);
                }

                Optional<GlobalSellPriceService.GlobalPotionEntry> potionEntry = plugin.getGlobalSellPriceService().potionEntry(edit.itemId);
                if (potionEntry.isPresent()) {
                    if (!plugin.getGlobalSellPriceService().setPotionPrice(potionEntry.get().potionType(), Math.max(0D, value))) {
                        plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Failed to save potion price."));
                        return PromptOutcome.failure();
                    }
                    plugin.getRotatingShopService().reload();
                    return PromptOutcome.success(edit.itemId);
                }

                Material material = Material.matchMaterial(edit.itemId == null ? "" : edit.itemId);
                if (material == null || material == Material.AIR) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Material not found."));
                    return PromptOutcome.failure();
                }
                if (!plugin.getGlobalSellPriceService().setPrice(material, Math.max(0D, value))) {
                    plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Failed to save global price."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", result.errorMessage()));
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
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Invalid gamemode: " + value));
                return null;
            }
            values.add(value);
        }
        return values;
    }

    private Long parseDurationSeconds(String input, Player player) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.isBlank()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Duration is empty."));
            return null;
        }

        OptionalLong parsed = DurationUtil.parseSeconds(normalized);
        if (parsed.isEmpty()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Duration must be 60s+, 1h, or 1d."));
            return null;
        }

        long seconds = parsed.getAsLong();
        if (seconds < 60L) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Duration must be at least 60 seconds."));
            return null;
        }
        return seconds;
    }

    private PromptOutcome startSellBoosterFromPrompt(PromptEdit edit, String input, Player player) {
        SellBoosterScope scope = SellBoosterScope.fromInput(edit.configPath).orElse(null);
        if (scope == null) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Unknown booster scope."));
            return PromptOutcome.failure();
        }

        SellBoosterInputParser.StartInput promptInput = SellBoosterInputParser.parsePromptStart(scope, input).orElse(null);
        if (promptInput == null) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Expected " + (scope == SellBoosterScope.GLOBAL ? "multiplier duration." : "target multiplier duration.")));
            return PromptOutcome.failure();
        }

        String ownerId = "";
        String ownerName = "Global";
        if (scope == SellBoosterScope.PLAYER) {
            SellBoosterInputParser.Target target = SellBoosterInputParser.resolvePlayerTarget(promptInput.target()).orElse(null);
            if (target == null) {
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Player not found."));
                return PromptOutcome.failure();
            }
            ownerId = target.ownerId();
            ownerName = target.ownerName();
        } else if (scope == SellBoosterScope.TEAM) {
            if (!plugin.getSellBoosterService().isFoTeamsAvailable()) {
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "FoTeams is not installed or enabled."));
                return PromptOutcome.failure();
            }
            var team = plugin.getSellBoosterService().teamByInput(promptInput.target());
            if (team.isEmpty()) {
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Team not found."));
                return PromptOutcome.failure();
            }
            ownerId = team.get().id();
            ownerName = team.get().name();
        }

        Double multiplier = SellBoosterInputParser.parseMultiplier(promptInput.multiplier());
        if (multiplier == null || multiplier <= 1D) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Multiplier must be above 1x."));
            return PromptOutcome.failure();
        }

        OptionalLong duration = DurationUtil.parseSeconds(promptInput.duration());
        if (duration.isEmpty() || duration.getAsLong() < 60L) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Duration must be 60s+, 1h, or 1d."));
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
            case ITEM_ID, ITEM_MATERIAL, ITEM_AMOUNT, ITEM_SLOT, ITEM_BUY, ITEM_SELL, ITEM_TYPE, ITEM_PAGE, ITEM_REQUIRED_PERMISSION, ITEM_ACTION, ITEM_STACK_SIZE, ITEM_STOCK, ITEM_BUY_LIMIT ->
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
            case GLOBAL_PRICE_SEARCH -> openGlobalSellPriceList(player, "editor".equals(edit.configPath),
                    edit.page, edit.previousQuery == null ? "" : edit.previousQuery,
                    sortFromOrdinal(edit.sectionPage));
            case ITEM_SEARCH -> openItemListEditor(player, edit.sectionId, edit.page, edit.sectionPage);
            case NEW_SECTION_ID -> openSectionEditorList(player, edit.page);
            case SECTION_SIZE, SECTION_SLOT, SECTION_DESCRIPTION -> openSectionDetailEditor(player, edit.sectionId, edit.page);
            case ITEM_ID, ITEM_MATERIAL, ITEM_AMOUNT, ITEM_SLOT, ITEM_BUY, ITEM_SELL, ITEM_TYPE, ITEM_PAGE, ITEM_REQUIRED_PERMISSION, ITEM_ACTION, ITEM_STACK_SIZE, ITEM_STOCK, ITEM_BUY_LIMIT ->
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Failed to save main GUI rows: " + writeResult.error()));
            return false;
        }

        EditorSaveResult saveResult = configSettingSaver.save("gui.main.rows", rows);
        if (!saveResult.successful()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", saveResult.errorMessage()));
            return false;
        }
        return true;
    }

    private boolean updateSectionField(String sectionId, String path, Object value, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
            return false;
        }

        yaml.set(itemPath + "." + field, value);
        return persistSectionYaml(sectionId, yaml, player, "Failed to save product.");
    }

    private boolean updateItemAction(String sectionId, String itemId, ShopItemType type, String input, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
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
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "This item type has no action data."));
                return false;
            }
        }

        return persistSectionYaml(sectionId, yaml, player, "Failed to save product action.");
    }

    private boolean updateItemMaterial(String sectionId, String itemId, Material material, Player player) {
        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Hold an item on your cursor first."));
            return false;
        }
        ItemStack source = cursorItem.get();

        File sectionFile = new File(plugin.getShopManager().getShopFolder(), sectionId + ".yml");
        if (!sectionFile.exists()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found in section file."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String oldPath = "items." + oldId;
        String newPath = "items." + newId;

        if (!yaml.contains(oldPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            return false;
        }

        if (!oldId.equalsIgnoreCase(newId) && yaml.contains(newPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Another product already has that id."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
            return false;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
        String itemPath = "items." + itemId;
        if (!yaml.contains(itemPath)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Product not found."));
            return false;
        }

        yaml.set(itemPath, null);
        return persistSectionYaml(sectionId, yaml, player, "Failed to remove product.");
    }

    private boolean removeSection(String sectionId, Player player) {
        ShopManager.DeleteResult result = plugin.getShopManager().deleteSection(sectionId);
        if (!result.success()) {
            String reason = result.error() == null || result.error().isBlank() ? "Failed to remove section." : result.error();
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", reason));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section not found."));
            return;
        }

        Optional<ItemStack> cursorClone = CursorItemEditor.cloneItem(cursorItem);
        if (cursorClone.isEmpty()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Hold an item on your cursor first."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "No free slot available in this section GUI."));
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
        yaml.set(path + ".required-permission", "");
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
            plugin.sendConfigured(player, "editor-saved");
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
        for (int slot : plugin.getValidationService().publicProductSlots().stream().sorted().toList()) {
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

    private ShopItem findPublicItem(String sectionId, String itemId) {
        ShopSection section = plugin.getShopManager().getSection(sectionId).orElse(null);
        if (section == null || !section.enabled()) {
            return null;
        }
        return section.items().stream().filter(item -> item.id().equalsIgnoreCase(itemId)).findFirst().orElse(null);
    }

    static boolean isCurrentPublicItem(ShopSection section, ShopItem item) {
        return section != null && section.enabled() && item != null
                && section.items().stream().anyMatch(current -> current == item);
    }

    private Double parsePriceInput(String input, Player player) {
        if (input.equalsIgnoreCase("disable") || input.equalsIgnoreCase("disabled") || input.equals("-1")) {
            return -1D;
        }

        OptionalDouble parsed = LargeNumberParser.parseDouble(input);
        if (parsed.isEmpty()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Price must be a number, 50k/1.5m/1Qa, or -1."));
            return null;
        }

        double value = parsed.getAsDouble();
        if (value < 0D || !Double.isFinite(value)) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Use -1 to disable, or 0+ for a price."));
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
                plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", label + " must be 0+ or -1."));
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", label + " must be a number."));
            return null;
        }
    }

    private boolean isDisableInput(String input) {
        return input.equalsIgnoreCase("disable") || input.equalsIgnoreCase("disabled") || input.equalsIgnoreCase("clear")
                || input.equalsIgnoreCase("none") || input.equals("-1");
    }

    private boolean createSection(String input, Player player) {
        String sectionId = normalizeItemId(input);
        if (sectionId.isBlank()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section id is empty."));
            return false;
        }

        if (plugin.getShopManager().getSection(sectionId).isPresent()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section id already exists."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Hold an item on your cursor first."));
            return false;
        }
        ItemStack source = cursorItem.get();

        File sectionFile = plugin.getShopManager().getSectionFile(sectionId);
        if (!sectionFile.exists()) {
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", "Section file not found."));
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
            plugin.sendConfigured(player, "editor-invalid", Map.of("{reason}", reason));
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
        for (int slot : sellGuiInputSlots(inventory)) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            plugin.getCore().inventoryDeposits().deposit(player, item, player.getLocation(), OverflowPolicy.DROP_OVERFLOW, false);
            inventory.setItem(slot, null);
        }
    }

    private boolean hasSellGuiItems(Inventory inventory) {
        for (int slot : sellGuiInputSlots(inventory)) {
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
        if (edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                && !worthDialogReady()) {
            player.closeInventory();
            startChatPrompt(player, edit, message);
            return;
        }
        if (!canUseNativeDialogs(player)) {
            player.closeInventory();
            startChatPrompt(player, edit, message);
            return;
        }
        TextDialogRequest request = buildDialogInputRequest(edit, message);

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

    private boolean worthDialogReady() {
        return !publicValue("worth-search-title").isBlank()
                && !publicValue("worth-search-label").isBlank()
                && !publicValue("worth-search-submit-label").isBlank()
                && !publicValue("worth-search-cancel-label").isBlank()
                && publicIntValue("worth-search-submit-width") > 0
                && publicIntValue("worth-search-cancel-width") > 0
                && publicIntValue("worth-search-body-width") > 0
                && publicIntValue("worth-search-input-width") > 0
                && publicIntValue("worth-search-max-length") > 0;
    }

    private void startChatPrompt(Player player, PromptEdit edit, String message) {
        String promptLine = plugin.renderConfigured("editor-prompt", Map.of("{prompt}", message));
        chatPrompts.openRaw(player, List.of(promptLine), input -> handlePromptInput(player, edit, input), () -> handlePromptCancel(player, edit));
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof SellGuiHolder) {
                returnUnsoldItems(player, top);
                player.closeInventory();
            }
        }
        publicGuis.close();
        worthGuis.close();
        chatPrompts.close();
        publicSections.clear();
        publicBuys.clear();
        publicWorth.clear();
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
        plugin.sendConfigured(player, "native-dialogs-fallback");
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
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicValue("worth-search-title") : "Search",
                    body,
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicValue("worth-search-label") : "Search",
                    "",
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicValue("worth-search-placeholder") : "Search...",
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicDialogButton("worth-search-submit") : DialogButton.search(),
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicDialogButton("worth-search-cancel") : DialogButton.cancel(),
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicIntValue("worth-search-body-width") : 320,
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicIntValue("worth-search-input-width") : 300,
                    edit.type == PromptType.GLOBAL_PRICE_SEARCH && "worth".equals(edit.configPath)
                            ? publicIntValue("worth-search-max-length") : 64,
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
            case ITEM_BUY, ITEM_SELL -> "50k or 1Qa";
            case GLOBAL_PRICE -> "1.0";
            case ITEM_TYPE -> "item";
            case ITEM_PAGE -> "1";
            case ITEM_REQUIRED_PERMISSION -> "foshop.vip";
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
            case ITEM_REQUIRED_PERMISSION -> item == null ? "" : item.requiredPermission();
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
            case ITEM_REQUIRED_PERMISSION -> "Required Permission";
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
        return price < 0D ? "-1" : plugin.getEconomyService().formatCompact(price);
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

    private String requiredPermissionLabel(ShopItem item) {
        return item.requiredPermission().isBlank() ? "Everyone" : item.requiredPermission();
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
        return plugin.getEconomyService().formatCompact(price);
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
            case NAME -> Comparator.comparing(entry -> editor ? entry.sortName() : publicGlobalDisplayName(entry),
                    String.CASE_INSENSITIVE_ORDER);
            case PRICE -> Comparator
                    .comparingDouble((GlobalPriceListEntry entry) -> effectivePrices.get(entry))
                    .reversed()
                    .thenComparing(entry -> editor ? entry.sortName() : publicGlobalDisplayName(entry),
                            String.CASE_INSENSITIVE_ORDER);
        };

        return entries.stream()
                .filter(entry -> editor || effectivePrices.get(entry) > 0D)
                .filter(entry -> query.isBlank()
                        || entry.searchName().contains(query)
                        || prettify(entry.sortName()).toLowerCase(Locale.ROOT).contains(query)
                        || (!editor && publicGlobalDisplayName(entry).toLowerCase(Locale.ROOT).contains(query)))
                .sorted(comparator)
                .toList();
    }

    private String publicGlobalDisplayName(GlobalPriceListEntry entry) {
        if (entry.isEnchantment()) {
            return enchantmentDisplayName(entry.enchantmentEntry());
        }
        return entry.isPotion() ? publicPotionName(entry.potionType().name())
                : publicMaterialName(entry.material());
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

    private ItemStack globalPriceItem(Player player, GlobalPriceListEntry entry, boolean editor) {
        if (!editor) {
            return publicWorthPriceItem(player, entry);
        }
        if (entry.isEnchantment()) {
            return globalEnchantmentPriceItem(entry.enchantmentEntry(), true);
        }
        return entry.isPotion() ? globalPotionPriceItem(entry.potionEntry(), true) : globalPriceItem(entry.materialEntry(), true);
    }

    private ItemStack publicWorthPriceItem(Player player, GlobalPriceListEntry entry) {
        ItemStack stack = globalPriceBaseStack(entry);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String type;
        String itemName;
        String itemId;
        if (entry.isEnchantment()) {
            type = "enchantment";
            itemName = enchantmentDisplayName(entry.enchantmentEntry());
            itemId = GlobalSellPriceService.enchantmentEntryId(entry.enchantmentEntry().enchantmentKey(), entry.enchantmentEntry().level());
        } else if (entry.isPotion()) {
            type = "potion";
            itemName = publicPotionName(entry.potionEntry().potionType().name());
            itemId = entry.potionEntry().potionType().name();
        } else {
            type = "material";
            itemName = publicMaterialName(entry.materialEntry().material());
            itemId = entry.materialEntry().material().name();
        }
        Map<String, String> placeholders = Map.of(
                "item", itemName,
                "item_id", itemId,
                "worth", formatGlobalPrice(effectiveGlobalPrice(stack))
        );
        String nameTemplate = plugin.getFoConfig().guiString("worth", "entries." + type + ".name", "&f{item}");
        List<String> loreTemplate = plugin.getFoConfig().guiStringList("worth", "entries." + type + ".lore",
                List.of("&#ffffffWorth: &#03fc88{worth}"));
        meta.setDisplayName(Text.colorize(applyPlaceholderApi(player, replaceGuiPlaceholders(nameTemplate, placeholders))));
        meta.setLore(loreTemplate.stream()
                .map(line -> replaceGuiPlaceholders(line, placeholders))
                .map(line -> applyPlaceholderApi(player, line))
                .map(Text::colorize)
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (entry.isEnchantment()) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
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
        meta.setDisplayName(Text.colorize("&f" + prettify(entry.potionType().name()) + publicValue("potion-suffix")));
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
        String id = GlobalSellPriceService.enchantmentEntryId(entry.enchantmentKey(), entry.level());
        return publicItemName("enchantments", id,
                prettify(entry.enchantmentKey()) + " " + romanNumeral(entry.level()));
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
            List<String> originalLore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();
            for (String line : originalLore) {
                lore.add(Text.colorize(applyTextPlaceholders(player, sectionId, item, amount, line)));
            }
            if (item.canBuy()) {
                appendShopItemGuiLore(lore, player, sectionId, item, amount, "item-lore.buy", List.of("&#3ecf8eBuy: {buy}"));
            }
            if (item.canSell()) {
                appendShopItemGuiLore(lore, player, sectionId, item, amount, "item-lore.sell", List.of("&#ff5d73Sell: {sell}"));
            }
            int stock = plugin.getShopManager().getStock(sectionId, item.id());
            if (stock >= 0) {
                appendShopItemGuiLore(lore, player, sectionId, item, amount, "item-lore.stock", List.of("&#a7b8b0Stock: &#03fc88{stock}"));
            }
            int limit = plugin.getShopManager().getRemainingBuyLimit(player.getUniqueId(), sectionId, item);
            if (limit >= 0) {
                appendShopItemGuiLore(lore, player, sectionId, item, amount, "item-lore.buy-limit", List.of("&#a7b8b0Limit left: &#03fc88{buy_limit}"));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void appendShopItemGuiLore(List<String> target, Player player, String sectionId, ShopItem item,
                                       int amount, String path, List<String> fallback) {
        for (String line : plugin.getFoConfig().guiStringList("shop-section", path, fallback)) {
            target.add(Text.colorize(applyTextPlaceholders(player, sectionId, item, amount, line)));
        }
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

    private ItemStack createGlobalRotatingDisplayItem(Player player, RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalSellPriceEntry globalEntry) {
        ItemStack stack = new ItemStack(globalEntry.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String name = plugin.getFoConfig().guiString("rotating-shop", "global-material.name", "&#03fc88{item}");
        List<String> loreTemplate = plugin.getFoConfig().guiStringList("rotating-shop", "global-material.lore", List.of(
                "&#ff5d73Sell: {base_price}",
                "",
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        meta.setDisplayName(Text.colorize(applyPlaceholderApi(player, formatGlobalRotatingLine(name, entry, globalEntry))));
        List<String> lore = loreTemplate.stream()
                .map(line -> applyPlaceholderApi(player, formatGlobalRotatingLine(line, entry, globalEntry)))
                .map(Text::colorize)
                .toList();
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createGlobalEnchantmentRotatingDisplayItem(Player player, RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalEnchantmentEntry globalEntry) {
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

        String name = plugin.getFoConfig().guiString("rotating-shop", "global-enchantment.name", "&#03fc88{item}");
        List<String> loreTemplate = plugin.getFoConfig().guiStringList("rotating-shop", "global-enchantment.lore", List.of(
                "&#ff5d73Sell: {base_price}",
                "",
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        meta.setDisplayName(Text.colorize(applyPlaceholderApi(player, formatGlobalEnchantmentRotatingLine(name, entry, globalEntry))));
        List<String> lore = loreTemplate.stream()
                .map(line -> applyPlaceholderApi(player, formatGlobalEnchantmentRotatingLine(line, entry, globalEntry)))
                .map(Text::colorize)
                .toList();
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createGlobalPotionRotatingDisplayItem(Player player, RotatingShopService.RotatingEntry entry, GlobalSellPriceService.GlobalPotionEntry globalEntry) {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(globalEntry.potionType());
        }

        String name = plugin.getFoConfig().guiString("rotating-shop", "global-potion.name", "&#03fc88{item}");
        List<String> loreTemplate = plugin.getFoConfig().guiStringList("rotating-shop", "global-potion.lore", List.of(
                "&#ff5d73Sell: {base_price}",
                "",
                "&#ffffffBoost: &#03fc88{boost}x",
                "&#ffffffBoosted sell: &#03fc88{price}",
                "&#a7b8b0Resets in {time}."
        ));
        meta.setDisplayName(Text.colorize(applyPlaceholderApi(player, formatGlobalPotionRotatingLine(name, entry, globalEntry))));
        List<String> lore = loreTemplate.stream()
                .map(line -> applyPlaceholderApi(player, formatGlobalPotionRotatingLine(line, entry, globalEntry)))
                .map(Text::colorize)
                .toList();
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private String formatRotatingLine(String line, RotatingShopService.RotatingEntry entry, ShopItem item) {
        String boost = formatBoost(entry.multiplier());
        String baseSell = plugin.getEconomyService().formatCompact(item.sellPrice());
        String boostedSell = plugin.getEconomyService().formatCompact(item.sellPrice() * entry.multiplier());
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
        String baseSell = plugin.getEconomyService().formatCompact(globalEntry.price());
        String boostedSell = plugin.getEconomyService().formatCompact(globalEntry.price() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", "global")
                .replace("{item}", publicMaterialName(globalEntry.material()))
                .replace("{item_id}", globalEntry.material().name())
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
        String baseSell = plugin.getEconomyService().formatCompact(globalEntry.price());
        String boostedSell = plugin.getEconomyService().formatCompact(globalEntry.price() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", "global")
                .replace("{item}", enchantmentDisplayName(globalEntry))
                .replace("{item_id}", GlobalSellPriceService.enchantmentEntryId(globalEntry.enchantmentKey(), globalEntry.level()))
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
        String baseSell = plugin.getEconomyService().formatCompact(globalEntry.price());
        String boostedSell = plugin.getEconomyService().formatCompact(globalEntry.price() * entry.multiplier());
        String resetTime = DurationUtil.format(plugin.getRotatingShopService().timeUntilResetMillis() / 1000L);
        return line
                .replace("{section}", "global")
                .replace("{item}", publicPotionName(globalEntry.potionType().name()))
                .replace("{item_id}", globalEntry.potionType().name())
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
                "buy_price", plugin.getEconomyService().formatCompact(item.buyPrice()),
                "buy", plugin.getEconomyService().formatCompact(item.buyPrice()),
                "sell_price", plugin.getEconomyService().formatCompact(item.sellPrice()),
                "sell", plugin.getEconomyService().formatCompact(item.sellPrice()),
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
            plugin.sendConfigured(player, "permission-provider-missing");
            return 0;
        }
        if (plugin.getPermissionService().has(player, item.permission())) {
            plugin.sendConfigured(player, "permission-already-owned");
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
                if (line.overflow()) addOverflowSoldLine(line.amount(), line.price());
                else addSoldLine(line.item(), line.amount(), line.price());
            }
        }

        private void addSoldLine(String item, int amount, double price) {
            for (int i = 0; i < soldLines.size(); i++) {
                SoldLine existing = soldLines.get(i);
                if (!existing.overflow() && existing.item().equals(item)) {
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
                if (existing.overflow()) {
                    soldLines.set(i, new SoldLine("", existing.amount() + amount, existing.price() + price, true));
                    return;
                }
            }
            soldLines.add(new SoldLine("", amount, price, true));
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

    private record SoldLine(String item, int amount, double price, boolean overflow) {
        private SoldLine(String item, int amount, double price) {
            this(item, amount, price, false);
        }
    }

    private record PromptEdit(PromptType type, String configPath, String sectionId, String itemId,
                              int page, int sectionPage, String previousQuery) {
        private PromptEdit(PromptType type, String configPath, String sectionId, String itemId,
                           int page, int sectionPage) {
            this(type, configPath, sectionId, itemId, page, sectionPage, null);
        }
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
        ITEM_REQUIRED_PERMISSION,
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
        private PublicGuiFrame frame;
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
        private int previousSlot = -1;
        private int sortSlot = -1;
        private int backSlot = -1;
        private int searchSlot = -1;
        private int clearSearchSlot = -1;
        private int nextSlot = -1;

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
