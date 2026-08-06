package me.foesio.foShop.worth;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.HashedComponentPatchMap;
import com.github.retrooper.packetevents.protocol.component.PatchableComponentMap;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.HashedStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.Text;
import me.foesio.foShop.util.WorthPriceFormatter;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class PacketEventsWorthLoreService extends PacketListenerAbstract implements WorthLoreService, Listener {

    private static final int MAX_PRICE_CACHE_SIZE = 2_048;
    private static final int MAX_WORTH_CONTAINER_DEPTH = 3;
    private static final int MAX_WORTH_CONTAINER_STEPS = 512;
    private static final String DEFAULT_FORMAT = "&#ffffffWorth: &#03fc88${worth}";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final FoShop plugin;
    private final NamespacedKey overlayLineKey;
    private final NamespacedKey overlayIndexKey;
    private final Map<UUID, ViewContext> openViews = new ConcurrentHashMap<>();
    private final Map<UUID, ClientOverlayState> overlayStates = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDragRefreshes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingCursorRefreshes = ConcurrentHashMap.newKeySet();
    private final Map<ItemStack, Double> unitWorthCache = new ConcurrentHashMap<>();
    private final AtomicBoolean packetFailureLogged = new AtomicBoolean();

    private volatile boolean enabled;
    private volatile String format = DEFAULT_FORMAT;
    private volatile long cacheRevision = Long.MIN_VALUE;

    PacketEventsWorthLoreService(FoShop plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        this.overlayLineKey = new NamespacedKey(plugin, "worth_lore_overlay");
        this.overlayIndexKey = new NamespacedKey(plugin, "worth_lore_index");

        if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isInitialized()) {
            throw new IllegalStateException("PacketEvents API is not initialized");
        }
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void reload() {
        boolean previous = enabled;
        enabled = plugin.getFoConfig().isWorthLoreEnabled();
        format = plugin.getFoConfig().getWorthLoreFormat();
        invalidateCache();
        packetFailureLogged.set(false);
        if (previous || enabled) {
            refreshOnlineInventories();
        }
    }

    @Override
    public void shutdown() {
        enabled = false;
        unitWorthCache.clear();
        try {
            if (PacketEvents.getAPI() != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(this);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // PacketEvents may already be terminating during server shutdown.
        }
        HandlerList.unregisterAll(this);
        refreshOnlineInventories();
        openViews.clear();
        overlayStates.clear();
        pendingDragRefreshes.clear();
        pendingCursorRefreshes.clear();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!enabled || event.isCancelled() || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        try {
            if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                decorateWindowItems(event, player);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                decorateSetSlot(event, player);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
                decoratePlayerInventory(event, player);
            } else if (event.getPacketType() == PacketType.Play.Server.SET_CURSOR_ITEM) {
                decorateCursor(event, player);
            }
        } catch (RuntimeException | LinkageError exception) {
            logPacketFailure(exception);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        try {
            if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                cleanWindowClick(event, player);
            } else if (event.getPacketType() == PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) {
                cleanCreativeAction(event, player);
            }
        } catch (RuntimeException | LinkageError exception) {
            logPacketFailure(exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        overlayStates.remove(player.getUniqueId());
        openViews.put(player.getUniqueId(), createViewContext(top));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysicalInventoryOpen(InventoryOpenEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (isPhysicalInventory(top)) {
            cleanLegacyInventory(top);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        openViews.remove(event.getPlayer().getUniqueId());
        overlayStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openViews.remove(event.getPlayer().getUniqueId());
        overlayStates.remove(event.getPlayer().getUniqueId());
        pendingDragRefreshes.remove(event.getPlayer().getUniqueId());
        pendingCursorRefreshes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cleanLegacyInventory(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled || !(event.getWhoClicked() instanceof Player player) || plugin.getCore() == null) {
            return;
        }
        ItemStack cursor = stripOverlay(event.getCursor());
        ItemStack clicked = stripOverlay(event.getCurrentItem());
        if (createWorthLine(cursor) == null && createWorthLine(clicked) == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!pendingCursorRefreshes.add(playerId)) {
            return;
        }
        plugin.getCore().scheduler().runLaterForPlayer(player, () -> {
            pendingCursorRefreshes.remove(playerId);
            if (!enabled || !plugin.isEnabled() || !player.isOnline()) {
                return;
            }
            try {
                sendAuthoritativeCursor(player);
            } catch (RuntimeException | LinkageError exception) {
                logPacketFailure(exception);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!enabled || !(event.getWhoClicked() instanceof Player player) || plugin.getCore() == null) {
            return;
        }
        ItemStack cursor = stripOverlay(event.getOldCursor());
        if (createWorthLine(cursor) == null || !pendingDragRefreshes.add(player.getUniqueId())) {
            return;
        }

        // QUICK_CRAFT redistributes client-only lore without recalculating its component state.
        plugin.getCore().scheduler().runLaterForPlayer(player, () -> {
            pendingDragRefreshes.remove(player.getUniqueId());
            if (!enabled || !plugin.isEnabled() || !player.isOnline()) {
                return;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            openViews.put(player.getUniqueId(), createViewContext(top));
            overlayStates.remove(player.getUniqueId());
            player.updateInventory();
        }, 1L);
    }

    private void cleanWindowClick(PacketReceiveEvent event, Player player) {
        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        ClientOverlayState state = overlayStates.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        Optional<Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> fullSlots = wrapper.getSlots();
        if (fullSlots.isPresent()) {
            cleanFullWindowClick(event, wrapper, state, fullSlots.get());
            return;
        }

        Map<Integer, Optional<HashedStack>> hashedSlots = wrapper.getHashedSlots();
        if (hashedSlots == null) {
            return;
        }
        Set<ItemType> overlayTypes = state.interactionTypes(
                wrapper.getWindowId(), wrapper.getSlot(), hashedSlots.keySet());
        if (overlayTypes.isEmpty()) {
            return;
        }

        Map<Integer, Optional<HashedStack>> cleanedSlots = new HashMap<>(hashedSlots.size());
        boolean changed = false;
        for (Map.Entry<Integer, Optional<HashedStack>> entry : hashedSlots.entrySet()) {
            OverlayDescriptor overlay = hashedOverlay(entry.getValue(), overlayTypes);
            Optional<HashedStack> cleaned = stripHashedLore(entry.getValue(), overlayTypes);
            cleanedSlots.put(entry.getKey(), cleaned);
            state.setSlot(wrapper.getWindowId(), entry.getKey(), overlay);
            changed |= !cleaned.equals(entry.getValue());
        }

        Optional<HashedStack> carried = wrapper.getCarriedHashedStack();
        OverlayDescriptor carriedOverlay = hashedOverlay(carried, overlayTypes);
        Optional<HashedStack> cleanedCarried = stripHashedLore(carried, overlayTypes);
        state.setCursor(carriedOverlay);
        changed |= !cleanedCarried.equals(carried);
        if (!changed) {
            return;
        }

        wrapper.setHashedSlots(cleanedSlots);
        wrapper.setCarriedHashedStack(cleanedCarried);
        event.markForReEncode(true);
    }

    private void sendAuthoritativeCursor(Player player) {
        ItemStack originalCursor = player.getOpenInventory().getCursor();
        ItemStack bukkitCursor = stripOverlay(originalCursor);
        if (bukkitCursor == null) {
            bukkitCursor = new ItemStack(Material.AIR);
        } else if (bukkitCursor != originalCursor) {
            player.getOpenInventory().setCursor(bukkitCursor);
        }
        com.github.retrooper.packetevents.protocol.item.ItemStack packetCursor =
                SpigotConversionUtil.fromBukkitItemStack(bukkitCursor);
        if (PacketEvents.getAPI().getPlayerManager().getClientVersion(player)
                .isNewerThanOrEquals(ClientVersion.V_1_21_2)) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    player, new WrapperPlayServerSetCursorItem(packetCursor));
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                player, new WrapperPlayServerSetSlot(-1, -1, -1, packetCursor));
    }

    private void cleanFullWindowClick(
            PacketReceiveEvent event,
            WrapperPlayClientClickWindow wrapper,
            ClientOverlayState state,
            Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> slots
    ) {
        Set<ItemType> overlayTypes = state.interactionTypes(
                wrapper.getWindowId(), wrapper.getSlot(), slots.keySet());
        if (overlayTypes.isEmpty()) {
            return;
        }

        Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> cleanedSlots = new HashMap<>(slots.size());
        boolean changed = false;
        for (Map.Entry<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> entry : slots.entrySet()) {
            OverlayDescriptor overlay = fullStackOverlay(entry.getValue(), overlayTypes);
            com.github.retrooper.packetevents.protocol.item.ItemStack cleaned = stripFullStackOverlay(
                    entry.getValue(), overlayTypes);
            cleanedSlots.put(entry.getKey(), cleaned);
            state.setSlot(wrapper.getWindowId(), entry.getKey(), overlay);
            changed |= cleaned != entry.getValue();
        }

        com.github.retrooper.packetevents.protocol.item.ItemStack carried = wrapper.getCarriedItemStack();
        OverlayDescriptor carriedOverlay = fullStackOverlay(carried, overlayTypes);
        com.github.retrooper.packetevents.protocol.item.ItemStack cleanedCarried = stripFullStackOverlay(
                carried, overlayTypes);
        state.setCursor(carriedOverlay);
        changed |= cleanedCarried != carried;
        if (!changed) {
            return;
        }

        wrapper.setSlots(cleanedSlots);
        wrapper.setCarriedItemStack(cleanedCarried);
        event.markForReEncode(true);
    }

    private void cleanCreativeAction(PacketReceiveEvent event, Player player) {
        WrapperPlayClientCreativeInventoryAction wrapper = new WrapperPlayClientCreativeInventoryAction(event);
        com.github.retrooper.packetevents.protocol.item.ItemStack packetItem = wrapper.getItemStack();
        if (packetItem == null || packetItem.isEmpty()) {
            return;
        }

        ClientOverlayState state = overlayStates.get(player.getUniqueId());
        Set<ItemType> overlayTypes = state == null
                ? Set.of()
                : state.interactionTypes(0, wrapper.getSlot(), Set.of(wrapper.getSlot()));
        OverlayDescriptor overlay = fullStackOverlay(packetItem, overlayTypes);
        com.github.retrooper.packetevents.protocol.item.ItemStack clean = stripFullStackOverlay(packetItem, overlayTypes);
        if (state != null) {
            state.setSlot(0, wrapper.getSlot(), overlay);
        }
        if (clean != packetItem) {
            wrapper.setItemStack(clean);
            event.markForReEncode(true);
        }
    }

    private OverlayDescriptor hashedOverlay(Optional<HashedStack> optional, Set<ItemType> overlayTypes) {
        if (optional == null || optional.isEmpty()) {
            return null;
        }
        HashedStack stack = optional.get();
        if (!overlayTypes.contains(stack.getItem())
                || !stack.getComponents().getAddedComponents().containsKey(ComponentTypes.LORE)) {
            return null;
        }
        return new OverlayDescriptor(stack.getItem());
    }

    private OverlayDescriptor fullStackOverlay(
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem,
            Set<ItemType> overlayTypes
    ) {
        if (packetItem == null || packetItem.isEmpty() || !overlayTypes.contains(packetItem.getType())
                || !packetItem.hasComponentPatches()
                || !packetItem.getComponents().getPatches().containsKey(ComponentTypes.LORE)) {
            return null;
        }
        return new OverlayDescriptor(packetItem.getType());
    }

    private Optional<HashedStack> stripHashedLore(Optional<HashedStack> optional, Set<ItemType> overlayTypes) {
        if (optional == null || optional.isEmpty()) {
            return optional == null ? Optional.empty() : optional;
        }
        HashedStack stack = optional.get();
        if (!overlayTypes.contains(stack.getItem())) {
            return optional;
        }

        HashedComponentPatchMap components = stack.getComponents();
        Map<ComponentType<?>, Integer> added = components.getAddedComponents();
        if (!added.containsKey(ComponentTypes.LORE)) {
            return optional;
        }

        Map<ComponentType<?>, Integer> cleanedAdded = new HashMap<>(added);
        cleanedAdded.remove(ComponentTypes.LORE);
        HashedComponentPatchMap cleanedComponents = new HashedComponentPatchMap(
                cleanedAdded, new HashSet<>(components.getRemovedComponents()));
        return Optional.of(new HashedStack(stack.getItem(), stack.getCount(), cleanedComponents));
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack stripFullStackOverlay(
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem,
            Set<ItemType> overlayTypes
    ) {
        if (packetItem == null || packetItem.isEmpty()) {
            return packetItem;
        }
        com.github.retrooper.packetevents.protocol.item.ItemStack clean = packetItem;
        if (overlayTypes.contains(packetItem.getType()) && packetItem.hasComponentPatches()
                && packetItem.getComponents().getPatches().containsKey(ComponentTypes.LORE)) {
            clean = packetItem.copy();
            PatchableComponentMap components = clean.getComponents().copy();
            components.getPatches().remove(ComponentTypes.LORE);
            clean.setComponents(components);
        }

        ItemStack bukkit = SpigotConversionUtil.toBukkitItemStack(clean);
        ItemStack legacyClean = stripOverlay(bukkit);
        if (legacyClean.equals(bukkit)) {
            return clean;
        }
        return SpigotConversionUtil.fromBukkitItemStack(legacyClean);
    }

    private void decorateWindowItems(PacketSendEvent event, Player player) {
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = wrapper.getItems();
        if (items == null) {
            return;
        }

        ViewContext context = openViews.get(player.getUniqueId());
        ClientOverlayState state = overlayState(player);
        state.setActiveWindow(wrapper.getWindowId(), contextTopSize(context));
        state.clearWindow(wrapper.getWindowId());
        List<com.github.retrooper.packetevents.protocol.item.ItemStack> decorated = new ArrayList<>(items);
        boolean changed = false;
        for (int slot = 0; slot < decorated.size(); slot++) {
            if (!shouldDecorate(wrapper.getWindowId(), slot, context)) {
                state.setSlot(wrapper.getWindowId(), slot, null);
                continue;
            }
            com.github.retrooper.packetevents.protocol.item.ItemStack original = decorated.get(slot);
            PacketDecoration decoration = decoratePacketItem(original);
            state.setSlot(wrapper.getWindowId(), slot, decoration.overlay());
            if (decoration.item() != original) {
                decorated.set(slot, decoration.item());
                changed = true;
            }
        }

        if (wrapper.getCarriedItem().isPresent()) {
            com.github.retrooper.packetevents.protocol.item.ItemStack original = wrapper.getCarriedItem().get();
            PacketDecoration decoration = decoratePacketItem(original);
            state.setCursor(decoration.overlay());
            if (decoration.item() != original) {
                wrapper.setCarriedItem(decoration.item());
                changed = true;
            }
        } else {
            state.setCursor(null);
        }

        if (changed) {
            wrapper.setItems(decorated);
            event.markForReEncode(true);
        }
    }

    private void decorateSetSlot(PacketSendEvent event, Player player) {
        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        ClientOverlayState state = overlayState(player);
        ViewContext context = openViews.get(player.getUniqueId());
        if (wrapper.getWindowId() > 0) {
            state.setActiveWindow(wrapper.getWindowId(), contextTopSize(context));
        }
        if (!shouldDecorate(wrapper.getWindowId(), wrapper.getSlot(), context)) {
            state.setSlot(wrapper.getWindowId(), wrapper.getSlot(), null);
            return;
        }
        com.github.retrooper.packetevents.protocol.item.ItemStack original = wrapper.getItem();
        PacketDecoration decoration = decoratePacketItem(original);
        if (wrapper.getWindowId() == -1 && wrapper.getSlot() == -1) {
            state.setCursor(decoration.overlay());
        } else {
            state.setSlot(normalizeWindowId(wrapper.getWindowId()), wrapper.getSlot(), decoration.overlay());
        }
        if (decoration.item() != original) {
            wrapper.setItem(decoration.item());
            event.markForReEncode(true);
        }
    }

    private void decoratePlayerInventory(PacketSendEvent event, Player player) {
        WrapperPlayServerSetPlayerInventory wrapper = new WrapperPlayServerSetPlayerInventory(event);
        com.github.retrooper.packetevents.protocol.item.ItemStack original = wrapper.getStack();
        PacketDecoration decoration = decoratePacketItem(original);
        overlayState(player).setPlayerInventorySlot(wrapper.getSlot(), decoration.overlay());
        if (decoration.item() != original) {
            wrapper.setStack(decoration.item());
            event.markForReEncode(true);
        }
    }

    private void decorateCursor(PacketSendEvent event, Player player) {
        WrapperPlayServerSetCursorItem wrapper = new WrapperPlayServerSetCursorItem(event);
        com.github.retrooper.packetevents.protocol.item.ItemStack original = wrapper.getStack();
        PacketDecoration decoration = decoratePacketItem(original);
        overlayState(player).setCursor(decoration.overlay());
        if (decoration.item() != original) {
            wrapper.setStack(decoration.item());
            event.markForReEncode(true);
        }
    }

    private PacketDecoration decoratePacketItem(
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem
    ) {
        if (packetItem == null || packetItem.isEmpty()) {
            return new PacketDecoration(packetItem, null);
        }
        ItemStack original = SpigotConversionUtil.toBukkitItemStack(packetItem);
        ItemStack clean = stripOverlay(original);
        com.github.retrooper.packetevents.protocol.item.ItemStack cleanPacket = clean.equals(original)
                ? packetItem
                : SpigotConversionUtil.fromBukkitItemStack(clean);
        String line = createWorthLine(clean);
        if (line == null) {
            return new PacketDecoration(cleanPacket, null);
        }

        com.github.retrooper.packetevents.protocol.item.ItemStack decorated = cleanPacket.copy();
        decorated.setComponent(ComponentTypes.LORE, new ItemLore(List.of(
                LEGACY_SERIALIZER.deserialize(line).decoration(TextDecoration.ITALIC, false))));
        return new PacketDecoration(decorated, new OverlayDescriptor(packetItem.getType()));
    }

    private String createWorthLine(ItemStack original) {
        if (original == null || original.getType() == Material.AIR) {
            return null;
        }

        refreshCacheRevision();
        ItemMeta meta = original.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
            return null;
        }
        ItemStack key = original.clone();
        key.setAmount(1);
        double unitWorth = cachedUnitWorth(key);
        if (unitWorth <= 0D || !Double.isFinite(unitWorth)) {
            return null;
        }

        double stackWorth = unitWorth * original.getAmount();
        String number = WorthPriceFormatter.formatNumber(stackWorth);
        return Text.colorize(format
                .replace("{worth}", number)
                .replace("{unit-worth}", WorthPriceFormatter.formatNumber(unitWorth))
                .replace("{amount}", Integer.toString(original.getAmount())));
    }

    private ItemStack stripOverlay(ItemStack original) {
        if (original == null || original.getType() == Material.AIR) {
            return original;
        }
        ItemMeta sourceMeta = original.getItemMeta();
        if (sourceMeta == null) {
            return original;
        }
        PersistentDataContainer sourceData = sourceMeta.getPersistentDataContainer();
        String markedLine = sourceData.get(overlayLineKey, PersistentDataType.STRING);
        Integer markedIndex = sourceData.get(overlayIndexKey, PersistentDataType.INTEGER);
        if (markedLine == null && markedIndex == null) {
            return original;
        }

        ItemStack clean = original.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta == null) {
            return original;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        if (markedLine != null && markedIndex != null && markedIndex >= 0 && markedIndex < lore.size()
                && markedLine.equals(lore.get(markedIndex))) {
            lore.remove((int) markedIndex);
        } else if (markedLine != null) {
            lore.remove(markedLine);
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        meta.getPersistentDataContainer().remove(overlayLineKey);
        meta.getPersistentDataContainer().remove(overlayIndexKey);
        clean.setItemMeta(meta);
        return clean;
    }

    private boolean shouldDecorate(int windowId, int slot, ViewContext context) {
        if (windowId <= 0 || slot < 0) {
            return true;
        }
        if (context == null) {
            return false;
        }
        return slot >= context.topSize()
                || context.decorateTop()
                || context.virtualTopSlots().contains(slot);
    }

    private int normalizeWindowId(int windowId) {
        return windowId < 0 ? 0 : windowId;
    }

    private int contextTopSize(ViewContext context) {
        return context == null ? 0 : context.topSize();
    }

    private ViewContext createViewContext(Inventory top) {
        boolean physical = isPhysicalInventory(top);
        Set<Integer> virtualTopSlots = physical || plugin.getGuiService() == null
                ? Set.of()
                : plugin.getGuiService().sellGuiInputSlots(top);
        return new ViewContext(top.getSize(), physical, virtualTopSlots);
    }

    private ClientOverlayState overlayState(Player player) {
        return overlayStates.computeIfAbsent(player.getUniqueId(), ignored -> new ClientOverlayState());
    }

    private boolean isPhysicalInventory(Inventory inventory) {
        if (inventory instanceof DoubleChestInventory) {
            return true;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof BlockState || holder instanceof Entity;
    }

    private void cleanLegacyInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack original = inventory.getItem(slot);
            ItemStack clean = stripOverlay(original);
            if (clean != original) {
                inventory.setItem(slot, clean);
            }
        }
    }

    private void refreshCacheRevision() {
        long revision = plugin.getShopManager().priceRevision();
        if (plugin.getGlobalSellPriceService() != null) {
            revision = revision * 31L + plugin.getGlobalSellPriceService().priceRevision();
        }
        if (revision == cacheRevision) {
            return;
        }
        synchronized (unitWorthCache) {
            if (revision != cacheRevision) {
                unitWorthCache.clear();
                cacheRevision = revision;
            }
        }
    }

    private double cachedUnitWorth(ItemStack key) {
        Double cached = unitWorthCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (unitWorthCache.size() >= MAX_PRICE_CACHE_SIZE) {
            return calculateUnitWorth(key);
        }
        return unitWorthCache.computeIfAbsent(key, this::calculateUnitWorth);
    }

    private double calculateUnitWorth(ItemStack stack) {
        return calculateStackWorth(stack, 0, new WorthWalkState());
    }

    private double calculateStackWorth(ItemStack stack, int depth, WorthWalkState state) {
        if (stack == null || stack.getType() == Material.AIR || !state.consume()) {
            return 0D;
        }

        int amount = Math.max(1, stack.getAmount());
        if (!isContainerItem(stack)) {
            double unitPrice = positiveBaseSellPrice(stack);
            return unitPrice <= 0D ? 0D : unitPrice * amount;
        }

        ItemStack one = stack.clone();
        one.setAmount(1);
        boolean hasContents = hasContainerContents(one);
        double total = hasContents ? 0D : positiveBaseSellPrice(stripContainerContents(one));
        if (depth >= MAX_WORTH_CONTAINER_DEPTH) {
            return total * amount;
        }

        double contentsWorth = hasContents ? containerContentsWorth(one, depth, state) : 0D;
        return (total + contentsWorth) * amount;
    }

    private boolean hasContainerContents(ItemStack containerItem) {
        ItemMeta meta = containerItem.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof Container container) {
            for (ItemStack content : container.getInventory().getContents()) {
                if (content != null && content.getType() != Material.AIR) {
                    return true;
                }
            }
            return false;
        }
        if (meta instanceof BundleMeta bundleMeta) {
            return !bundleMeta.getItems().isEmpty();
        }
        return false;
    }

    private double containerContentsWorth(ItemStack containerItem, int depth, WorthWalkState state) {
        ItemMeta meta = containerItem.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof Container container) {
            double total = 0D;
            for (ItemStack content : container.getInventory().getContents()) {
                total += calculateStackWorth(content, depth + 1, state);
            }
            return total;
        }
        if (meta instanceof BundleMeta bundleMeta) {
            double total = 0D;
            for (ItemStack content : bundleMeta.getItems()) {
                total += calculateStackWorth(content, depth + 1, state);
            }
            return total;
        }
        return 0D;
    }

    private double positiveBaseSellPrice(ItemStack stack) {
        double price = plugin.getShopManager().getBaseSellPrice(stack);
        return price > 0D && Double.isFinite(price) ? price : 0D;
    }

    private boolean isContainerItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta instanceof BlockStateMeta || meta instanceof BundleMeta;
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

    private void invalidateCache() {
        synchronized (unitWorthCache) {
            unitWorthCache.clear();
            cacheRevision = Long.MIN_VALUE;
        }
    }

    private void refreshOnlineInventories() {
        overlayStates.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            cleanLegacyInventory(player.getInventory());
            if (isPhysicalInventory(top)) {
                cleanLegacyInventory(top);
            }
            ItemStack cursor = player.getOpenInventory().getCursor();
            ItemStack cleanCursor = stripOverlay(cursor);
            if (cleanCursor != cursor) {
                player.getOpenInventory().setCursor(cleanCursor);
            }
            openViews.put(player.getUniqueId(), createViewContext(top));
            player.updateInventory();
        }
    }

    private static final class WorthWalkState {
        private int steps;

        private boolean consume() {
            if (steps >= MAX_WORTH_CONTAINER_STEPS) {
                return false;
            }
            steps++;
            return true;
        }
    }

    private void logPacketFailure(Throwable throwable) {
        if (packetFailureLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("PacketEvents worth lore rewrite failed and was skipped: " + throwable.getMessage());
        }
    }

    private record PacketDecoration(
            com.github.retrooper.packetevents.protocol.item.ItemStack item,
            OverlayDescriptor overlay
    ) {
    }

    private record OverlayDescriptor(ItemType type) {
    }

    private record SlotKey(int windowId, int slot) {
    }

    private static final class ClientOverlayState {

        private final Map<SlotKey, OverlayDescriptor> slots = new ConcurrentHashMap<>();
        private volatile OverlayDescriptor cursor;
        private volatile int activeWindowId;
        private volatile int activeTopSize;

        void setSlot(int windowId, int slot, OverlayDescriptor overlay) {
            SlotKey key = new SlotKey(windowId, slot);
            if (overlay == null) {
                slots.remove(key);
            } else {
                slots.put(key, overlay);
            }
        }

        void clearWindow(int windowId) {
            slots.keySet().removeIf(key -> key.windowId() == windowId);
        }

        void setActiveWindow(int windowId, int topSize) {
            activeWindowId = windowId;
            activeTopSize = topSize;
        }

        void setPlayerInventorySlot(int playerSlot, OverlayDescriptor overlay) {
            int playerMenuSlot = toPlayerMenuSlot(playerSlot);
            if (playerMenuSlot >= 0) {
                setSlot(0, playerMenuSlot, overlay);
            }

            int currentWindow = activeWindowId;
            if (currentWindow <= 0) {
                return;
            }
            int containerSlot = toContainerSlot(playerSlot, activeTopSize);
            if (containerSlot >= 0) {
                setSlot(currentWindow, containerSlot, overlay);
            }
        }

        void setCursor(OverlayDescriptor overlay) {
            cursor = overlay;
        }

        Set<ItemType> interactionTypes(int windowId, int clickedSlot, Set<Integer> changedSlots) {
            Set<ItemType> types = new HashSet<>();
            addType(types, cursor);
            addType(types, slots.get(new SlotKey(windowId, clickedSlot)));
            for (int slot : changedSlots) {
                addType(types, slots.get(new SlotKey(windowId, slot)));
            }
            return types;
        }

        private void addType(Set<ItemType> types, OverlayDescriptor overlay) {
            if (overlay != null) {
                types.add(overlay.type());
            }
        }

        private int toPlayerMenuSlot(int playerSlot) {
            if (playerSlot >= 0 && playerSlot <= 8) {
                return 36 + playerSlot;
            }
            if (playerSlot >= 9 && playerSlot <= 35) {
                return playerSlot;
            }
            if (playerSlot >= 36 && playerSlot <= 39) {
                return 44 - playerSlot;
            }
            return playerSlot == 40 ? 45 : -1;
        }

        private int toContainerSlot(int playerSlot, int topSize) {
            if (playerSlot >= 0 && playerSlot <= 8) {
                return topSize + 27 + playerSlot;
            }
            if (playerSlot >= 9 && playerSlot <= 35) {
                return topSize + playerSlot - 9;
            }
            return -1;
        }
    }

    private record ViewContext(int topSize, boolean decorateTop, Set<Integer> virtualTopSlots) {
    }
}
