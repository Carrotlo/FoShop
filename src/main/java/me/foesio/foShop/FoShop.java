package me.foesio.foShop;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.economy.VaultEconomyBridge;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundMigrations;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foShop.command.FoSellGuiCommand;
import me.foesio.foShop.command.FoSellCommand;
import me.foesio.foShop.command.FoRotatingShopCommand;
import me.foesio.foShop.command.FoShopAdminCommand;
import me.foesio.foShop.command.FoShopCommand;
import me.foesio.foShop.command.FoWorthCommand;
import me.foesio.foShop.api.DefaultFoShopSellBoostApi;
import me.foesio.foShop.api.FoShopSellBoostApi;
import me.foesio.foShop.booster.SellBoosterBossbarService;
import me.foesio.foShop.booster.SellBoosterService;
import me.foesio.foShop.config.FoConfig;
import me.foesio.foShop.converter.ShopGUIPlusConverter;
import me.foesio.foShop.data.UserDataStore;
import me.foesio.foShop.economy.PermissionService;
import me.foesio.foShop.gui.GuiService;
import me.foesio.foShop.hook.FoTeamsHook;
import me.foesio.foShop.io.SafeYamlWriter;
import me.foesio.foShop.rotating.RotatingShopService;
import me.foesio.foShop.shop.GlobalSellPriceService;
import me.foesio.foShop.shop.ShopManager;
import me.foesio.foShop.validation.ShopValidationService;
import me.foesio.foShop.worth.WorthLoreService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public final class FoShop extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 32519;

    private FoConfig foConfig;
    private ShopManager shopManager;
    private VaultEconomyBridge economyService;
    private PermissionService permissionService;
    private GuiService guiService;
    private FoCoreContext core;
    private FoSoundService sounds;
    private FoGuiSounds guiSounds;
    private FoEditorSounds editorSounds;
    private FoAdminSounds adminSounds;
    private FoMessageService coreMessages;
    private YamlConfiguration ownerMessageValues;
    private UpdateNoticeService updateNotices;
    private ShopGUIPlusConverter converter;
    private ShopValidationService validationService;
    private SafeYamlWriter safeYamlWriter;
    private FoFileLogger fileLogger;
    private UserDataStore userDataStore;
    private RotatingShopService rotatingShopService;
    private GlobalSellPriceService globalSellPriceService;
    private FoTeamsHook foTeamsHook;
    private SellBoosterService sellBoosterService;
    private SellBoosterBossbarService sellBoosterBossbarService;
    private FoShopSellBoostApi sellBoostApi;
    private WorthLoreService worthLoreService;

    @Override
    public void onEnable() {
        boolean freshInstall = isFreshInstall(getDataFolder());
        saveDefaultConfig();
        if (freshInstall) {
            saveResource("messages.yml", false);
            saveResource("sounds.yml", false);
        }
        this.foConfig = new FoConfig(this, freshInstall);
        this.foConfig.reload();
        this.fileLogger = FoFileLogger.create(this);
        this.fileLogger.configure(foConfig.isFileLoggingEnabled(), true);
        fileLogger.info("Plugin enable started.");
        refreshCoreContext();
        this.coreMessages = FoMessageService.loadPreservingRemovals(this, FoMessageMigrations.none());
        try {
            this.ownerMessageValues = readOwnerMessages();
        } catch (IllegalStateException exception) {
            getLogger().warning(exception.getMessage());
            this.ownerMessageValues = new YamlConfiguration();
        }
        this.updateNotices = core.createUpdateNotices(coreMessages, "foshop").start();
        this.userDataStore = new UserDataStore(this);
        this.userDataStore.open();

        this.validationService = new ShopValidationService();
        this.safeYamlWriter = new SafeYamlWriter();
        this.shopManager = new ShopManager(this, validationService, safeYamlWriter, userDataStore);
        this.shopManager.prepareResources(freshInstall);
        this.globalSellPriceService = new GlobalSellPriceService(this);
        this.rotatingShopService = new RotatingShopService(this);
        this.foTeamsHook = new FoTeamsHook(this);
        this.sellBoosterService = new SellBoosterService(this, foTeamsHook);
        this.sellBoosterBossbarService = new SellBoosterBossbarService(this, sellBoosterService);
        this.economyService = core.createVaultEconomy();
        this.permissionService = new PermissionService(this);
        this.converter = new ShopGUIPlusConverter(this);

        this.guiService = new GuiService(this);
        this.worthLoreService = WorthLoreService.create(this);

        reloadAll();
        sellBoosterService.start();
        sellBoosterBossbarService.start();
        sellBoostApi = new DefaultFoShopSellBoostApi(sellBoosterService);
        getServer().getServicesManager().register(FoShopSellBoostApi.class, sellBoostApi, this, ServicePriority.Normal);

        if (!economyService.isAvailable()) {
            getLogger().warning("Vault economy not found. Buy/sell actions will be disabled until Vault + economy plugin are installed.");
            fileLogger.warn("Vault economy not found. Buy/sell disabled.");
        }

        registerCommands();

        fileLogger.info("Plugin enabled.");
    }

    static boolean isFreshInstall(File dataFolder) {
        if (!dataFolder.exists()) return true;
        if (!dataFolder.isDirectory()) return false;
        String[] entries = dataFolder.list();
        return entries != null && entries.length == 0;
    }

    @Override
    public void onDisable() {
        if (sellBoostApi != null) {
            getServer().getServicesManager().unregister(FoShopSellBoostApi.class, sellBoostApi);
            sellBoostApi = null;
        }
        if (worthLoreService != null) {
            worthLoreService.shutdown();
            worthLoreService = null;
        }
        if (guiService != null) {
            guiService.close();
        }
        if (rotatingShopService != null) {
            rotatingShopService.shutdown();
        }
        if (sellBoosterBossbarService != null) {
            sellBoosterBossbarService.shutdown();
        }
        if (sellBoosterService != null) {
            sellBoosterService.shutdown();
        }
        if (userDataStore != null) {
            userDataStore.close();
        }
        if (fileLogger != null) {
            fileLogger.shutdown();
        }
        if (core != null) {
            core.close();
            core = null;
            coreMessages = null;
            updateNotices = null;
        }
    }

    public ShopManager.ReloadResult reloadAll() {
        if (fileLogger != null) {
            fileLogger.info("Reload started.");
        }
        AtomicReference<ShopManager.ReloadResult> shopReloadResult = new AtomicReference<>();
        FoReloadResult reload = FoReloadRegistry.create()
                .add("config/guis", foConfig::reload)
                .add("file logging", () -> {
                    if (fileLogger != null) {
                        fileLogger.configure(foConfig.isFileLoggingEnabled(), false);
                    }
                })
                .add("native dialog settings", () -> core.reloadNativeDialogSettings())
                .add("core messages", this::reloadCoreMessages)
                .add("sounds", () -> {
                    if (sounds != null && !sounds.reloadChecked()) {
                        throw new IllegalStateException("sounds.yml is invalid; prior sound cache remains active");
                    }
                })
                .add("public GUIs", () -> {
                    if (guiService != null && !guiService.reloadPublicGuis().successful()) {
                        throw new IllegalStateException("Public GUI files or dependent shop slots failed validation; prior GUI revision remains active");
                    }
                })
                .add("shops", () -> shopReloadResult.set(guiService.lastShopReloadResult()))
                .add("global sell prices", () -> {
                    if (globalSellPriceService != null) {
                        globalSellPriceService.reload();
                    }
                })
                .add("rotating shop", () -> {
                    if (rotatingShopService != null) {
                        rotatingShopService.reload();
                    }
                })
                .add("sell boosters", () -> {
                    if (sellBoosterService != null) {
                        sellBoosterService.reload();
                    }
                })
                .add("sell booster bossbar", () -> {
                    if (sellBoosterBossbarService != null) {
                        sellBoosterBossbarService.reload();
                    }
                })
                .add("worth lore", () -> {
                    if (worthLoreService != null) {
                        worthLoreService.reload();
                    }
                })
                .add("economy", economyService::reload)
                .add("permissions", permissionService::setup)
                .reload();

        if (!reload.successful()) {
            String issue = "Reload failed at " + reload.failedStep() + ": " + reload.errorMessage();
            getLogger().warning(issue);
            if (fileLogger != null) {
                fileLogger.error(issue, reload.error());
            }
            return new ShopManager.ReloadResult(0, 0, 0, java.util.List.of(issue));
        }

        ShopManager.ReloadResult result = shopReloadResult.get();
        if (result == null) {
            String issue = "Reload failed: shop reload step did not return a result.";
            getLogger().warning(issue);
            if (fileLogger != null) {
                fileLogger.warn(issue);
            }
            return new ShopManager.ReloadResult(0, 0, 0, java.util.List.of(issue));
        }

        if (guiService != null && !guiService.worthGuiIssues().isEmpty()) {
            java.util.List<String> issues = new java.util.ArrayList<>(result.issues());
            issues.addAll(guiService.worthGuiIssues());
            result = new ShopManager.ReloadResult(result.loadedSections(), result.skippedSections(),
                    result.skippedItems(), java.util.List.copyOf(issues));
        }

        String resultMessage = "Loaded " + result.loadedSections() + " shop section(s). Skipped sections: " + result.skippedSections() + ", skipped items: " + result.skippedItems() + ".";
        getLogger().info(resultMessage);
        if (fileLogger != null) {
            fileLogger.debug("Reload steps: " + String.join(", ", reload.completedSteps()));
            fileLogger.info("Reload result: " + resultMessage);
            fileLogger.info("Integrations: Vault economy=" + economyService.isAvailable() + ", Vault permissions=" + permissionService.isEnabled()
                    + ", PlaceholderAPI=" + (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null));
        }
        for (String issue : result.issues()) {
            getLogger().warning(issue);
            if (fileLogger != null) {
                fileLogger.warn(issue);
            }
        }
        return result;
    }

    private void reloadCoreMessages() {
        if (coreMessages == null) {
            return;
        }
        YamlConfiguration next = readOwnerMessages();
        coreMessages.reload();
        ownerMessageValues = next;
    }

    private YamlConfiguration readOwnerMessages() {
        YamlConfiguration next = new YamlConfiguration();
        try {
            next.load(new File(getDataFolder(), "messages.yml"));
            return next;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("messages.yml could not be loaded; previous messages remain active", exception);
        }
    }

    private void refreshCoreContext() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        sounds = FoSoundService.loadPreservingRemovals(this);
        guiSounds = FoGuiSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        adminSounds = FoAdminSounds.create(sounds);
        core.metrics(BSTATS_PLUGIN_ID);
        core.warnIfNativeDialogsUnavailable();
        if (fileLogger != null) {
            var availability = core.nativeDialogs().availability();
            if (availability.configEnabled() && !availability.canUseNativeDialogs()) {
                fileLogger.warn("Native dialogs unavailable: " + availability.reason());
            }
        }
    }

    private FoSoundMigrations soundMigrations() {
        Map<String, String> sellEvents = Map.of(
                "events.open", "sell.open",
                "events.success", "sell.success",
                "events.failed", "sell.failure"
        );
        return FoSoundMigrations.create()
                .add(soundService -> soundService.moveFromConfigSharedEventSettings("sellgui.sounds", sellEvents))
                .add(soundService -> soundService.moveFromConfigSharedEventSettings("gui.sell.sounds", sellEvents))
                .build();
    }

    private void registerCommands() {
        FoShopCommand shopCommand = new FoShopCommand(this);
        FoRotatingShopCommand rotatingShopCommand = new FoRotatingShopCommand(this);
        FoSellGuiCommand sellCommand = new FoSellGuiCommand(this);
        FoSellCommand sellRootCommand = new FoSellCommand(this, FoSellCommand.Mode.ROOT);
        FoSellCommand sellAllCommand = new FoSellCommand(this, FoSellCommand.Mode.ALL);
        FoSellCommand sellHandCommand = new FoSellCommand(this, FoSellCommand.Mode.HAND);
        FoWorthCommand worthCommand = new FoWorthCommand(this);

        PluginCommand foshop = Objects.requireNonNull(getCommand("foshop"), "Command foshop missing from plugin.yml");
        foshop.setExecutor(shopCommand);

        PluginCommand rotatingshop = Objects.requireNonNull(getCommand("rotatingshop"), "Command rotatingshop missing from plugin.yml");
        rotatingshop.setExecutor(rotatingShopCommand);

        PluginCommand fosellgui = Objects.requireNonNull(getCommand("fosellgui"), "Command fosellgui missing from plugin.yml");
        fosellgui.setExecutor(sellCommand);

        PluginCommand sell = Objects.requireNonNull(getCommand("sell"), "Command sell missing from plugin.yml");
        sell.setExecutor(sellRootCommand);
        sell.setTabCompleter(sellRootCommand);

        PluginCommand sellall = Objects.requireNonNull(getCommand("sellall"), "Command sellall missing from plugin.yml");
        sellall.setExecutor(sellAllCommand);

        PluginCommand sellhand = Objects.requireNonNull(getCommand("sellhand"), "Command sellhand missing from plugin.yml");
        sellhand.setExecutor(sellHandCommand);

        PluginCommand worth = Objects.requireNonNull(getCommand("worth"), "Command worth missing from plugin.yml");
        worth.setExecutor(worthCommand);

        FoShopAdminCommand.register(this, coreMessages);
    }

    public FoConfig getFoConfig() {
        return foConfig;
    }

    public FoMessageService getMessages() {
        return coreMessages;
    }

    /** Missing or deliberately empty owner messages stay silent. */
    public void sendConfigured(CommandSender sender, String path) {
        sendConfigured(sender, path, Map.of());
    }

    public void sendConfigured(CommandSender sender, String path, Map<String, String> placeholders) {
        if (coreMessages == null || ownerMessageValues == null || !ownerMessageValues.isSet(path)) {
            return;
        }
        Object value = ownerMessageValues.get(path);
        if (value instanceof String text && text.isEmpty()) {
            return;
        }
        coreMessages.send(sender, path, "", placeholders);
    }

    public String renderConfigured(String path) {
        return renderConfigured(path, Map.of());
    }

    public String renderConfigured(String path, Map<String, String> placeholders) {
        if (coreMessages == null || ownerMessageValues == null || !ownerMessageValues.isSet(path)) {
            return "";
        }
        return coreMessages.render(path, "", placeholders);
    }

    public Component renderConfiguredComponent(Player player, String path, Map<String, String> placeholders) {
        if (coreMessages == null || ownerMessageValues == null || !ownerMessageValues.isString(path)) {
            return Component.empty();
        }
        return coreMessages.renderComponent(player, path, "", placeholders);
    }

    public String configuredMessageTemplate(String path) {
        if (ownerMessageValues == null || !ownerMessageValues.isString(path)) {
            return "";
        }
        return ownerMessageValues.getString(path, "");
    }

    /** Render a configured separator without Core's item-sprite whitespace cleanup. */
    public String renderConfiguredSeparator(String path) {
        if (coreMessages == null || ownerMessageValues == null || !ownerMessageValues.isString(path)) {
            return "";
        }
        String rendered = coreMessages.renderTemplateForItem(ownerMessageValues.getString(path), Map.of());
        StringBuilder result = new StringBuilder(rendered.length());
        int start = 0;
        while (start < rendered.length()) {
            boolean whitespace = Character.isWhitespace(rendered.charAt(start));
            int end = start + 1;
            while (end < rendered.length() && Character.isWhitespace(rendered.charAt(end)) == whitespace) {
                end++;
            }
            String part = rendered.substring(start, end);
            result.append(whitespace ? part : DialogIcons.fallbackText(part));
            start = end;
        }
        return result.toString();
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public RotatingShopService getRotatingShopService() {
        return rotatingShopService;
    }

    public GlobalSellPriceService getGlobalSellPriceService() {
        return globalSellPriceService;
    }

    public SellBoosterService getSellBoosterService() {
        return sellBoosterService;
    }

    public FoShopSellBoostApi getSellBoostApi() {
        return sellBoostApi;
    }

    public FoTeamsHook getFoTeamsHook() {
        return foTeamsHook;
    }

    public VaultEconomyBridge getEconomyService() {
        return economyService;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public GuiService getGuiService() {
        return guiService;
    }

    public UpdateNoticeService getUpdateNotices() {
        return updateNotices;
    }

    public FoCoreContext getCore() {
        return core;
    }

    public FoGuiSounds getGuiSounds() {
        return guiSounds;
    }

    public FoSoundService getSounds() {
        return sounds;
    }

    public FoEditorSounds getEditorSounds() {
        return editorSounds;
    }

    public FoAdminSounds getAdminSounds() {
        return adminSounds;
    }

    public ShopGUIPlusConverter getConverter() {
        return converter;
    }

    public ShopValidationService getValidationService() {
        return validationService;
    }

    public SafeYamlWriter getSafeYamlWriter() {
        return safeYamlWriter;
    }

    public FoFileLogger getFileLogger() {
        return fileLogger;
    }

    public UserDataStore getUserDataStore() {
        return userDataStore;
    }
}
