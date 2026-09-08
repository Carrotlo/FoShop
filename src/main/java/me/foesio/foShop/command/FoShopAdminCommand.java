package me.foesio.foShop.command;

import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.message.FoMessageService;
import me.foesio.foShop.FoShop;
import me.foesio.foShop.booster.ActiveSellBooster;
import me.foesio.foShop.booster.SellBoosterInputParser;
import me.foesio.foShop.booster.SellBoosterScope;
import me.foesio.foShop.hook.FoTeamsHook;
import me.foesio.foShop.util.DurationUtil;
import me.foesio.foShop.converter.ShopGUIPlusConverter;
import me.foesio.foShop.util.ReloadFeedback;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class FoShopAdminCommand {

    private final FoShop plugin;

    private FoShopAdminCommand(FoShop plugin) {
        this.plugin = plugin;
    }

    public static void register(FoShop plugin, FoMessageService messages) {
        FoShopAdminCommand handlers = new FoShopAdminCommand(plugin);
        FoAdminCommand command = FoAdminCommand.builder(plugin, messages)
                .commandName("foshopadmin")
                .permission("foshop.admin")
                .adminSounds(plugin.getAdminSounds())
                .adminMessages(adminMessages())
                .versionCommand(false)
                .addSubcommand(handlers.versionSubcommand())
                .addSubcommand(handlers.reloadSubcommand())
                .addSubcommand(handlers.editorSubcommand())
                .addSubcommand(handlers.convertSubcommand())
                .addSubcommand(handlers.rotatingShopSubcommand())
                .addSubcommand(handlers.resetRotatingShopSubcommand())
                .addSubcommand(handlers.boosterSubcommand())
                .build();

        PluginCommand pluginCommand = plugin.getCommand("foshopadmin");
        if (pluginCommand == null) {
            messages.send(plugin.getServer().getConsoleSender(), "admin-command-missing",
                    "{prefix}{bad}Admin command is missing from plugin.yml: {theme}{command}",
                    Map.of("command", "foshopadmin"));
            return;
        }

        LoggedAdminCommand logged = new LoggedAdminCommand(plugin, command);
        pluginCommand.setExecutor(logged);
        pluginCommand.setTabCompleter(logged);
    }

    private static FoAdminMessages adminMessages() {
        return FoAdminMessages.builder()
                .generalNoPermission("no-permission", "{prefix}{bad}No permission.")
                .generalPlayerOnly("player-only", "{prefix}{bad}Players only.")
                .usage("admin-usage", "{prefix}{muted}Usage: {theme}/foshopadmin <version|reload|editor|booster|rotatingshop reset|convert <ShopGUIPlus|EconomyShopGUI> [dryrun]>")
                .commandMissing("admin-command-missing", "{prefix}{bad}Admin command is missing from plugin.yml: {theme}{command}")
                .commandFailed("admin-command-failed", "{prefix}{bad}Command failed. {muted}{error}")
                .build();
    }

    private FoAdminSubcommand versionSubcommand() {
        return FoAdminSubcommand.builder("version", context -> {
            handleVersion(context.sender());
            return true;
        }).usage("version").build();
    }

    private FoAdminSubcommand reloadSubcommand() {
        return FoAdminSubcommand.builder("reload", context -> {
            handleReload(context.sender());
            return true;
        }).usage("reload").build();
    }

    private FoAdminSubcommand editorSubcommand() {
        return FoAdminSubcommand.builder("editor", context -> {
            Player player = context.playerOrNull();
            plugin.getGuiService().openAdminEditor(player);
            return true;
        }).usage("editor").playerOnly().build();
    }

    private FoAdminSubcommand convertSubcommand() {
        return FoAdminSubcommand.builder("convert", context -> {
            handleConvert(context.sender(), context.args());
            return true;
        }).usage("convert <ShopGUIPlus|EconomyShopGUI> [dryrun]").tabCompleter(context -> convertTabs(context.args())).build();
    }

    private FoAdminSubcommand rotatingShopSubcommand() {
        return FoAdminSubcommand.builder("rotatingshop", context -> {
            handleRotatingShop(context.sender(), context.args());
            return true;
        }).usage("rotatingshop reset").tabCompleter(context -> rotatingShopTabs(context.args())).build();
    }

    private FoAdminSubcommand resetRotatingShopSubcommand() {
        return FoAdminSubcommand.builder("resetrotatingshop", context -> {
            plugin.getRotatingShopService().resetNow();
            plugin.getMessages().send(context.sender(), "rotating-shop-reset");
            play(context.sender(), "shop.rotating-reset");
            return true;
        }).usage("resetrotatingshop").build();
    }

    private FoAdminSubcommand boosterSubcommand() {
        return FoAdminSubcommand.builder("booster", context -> {
            handleSellBooster(context.sender(), context.args());
            return true;
        }).aliases("boosters", "sellbooster", "sellboosters").usage("booster <list|start|clear>").tabCompleter(context -> boosterTabs(context.args())).build();
    }

    private void handleVersion(CommandSender sender) {
        plugin.getUpdateNotices().checkAndSendVersion(sender);
    }

    private void handleReload(CommandSender sender) {
        var result = plugin.reloadAll();
        ReloadFeedback.send(plugin, sender, result);
        if (sender instanceof Player player) {
            if (result.issues().isEmpty()) {
                plugin.getAdminSounds().reload(player);
            } else {
                plugin.getAdminSounds().reloadError(player);
            }
        }
    }

    private void handleConvert(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "convert-usage");
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        ShopGUIPlusConverter.SourcePlugin sourcePlugin = ShopGUIPlusConverter.SourcePlugin.fromInput(args[1]).orElse(null);
        if (sourcePlugin == null) {
            plugin.getMessages().send(sender, "convert-unknown", Map.of("{plugin}", args[1]));
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        ShopGUIPlusConverter.ConversionMode mode = ShopGUIPlusConverter.ConversionMode.APPLY;
        if (args.length >= 3) {
            if (args[2].equalsIgnoreCase("dryrun") || args[2].equalsIgnoreCase("dry-run")) {
                mode = ShopGUIPlusConverter.ConversionMode.DRY_RUN;
            } else if (!args[2].equalsIgnoreCase("apply")) {
                plugin.getMessages().send(sender, "convert-mode-unknown");
                plugin.getAdminSounds().updateError(sender);
                return;
            }
        }

        ShopGUIPlusConverter.ConversionResult result = plugin.getConverter().convert(sourcePlugin, mode);
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().info("Converter result: success=" + result.success() + ", message=" + result.message());
        }
        if (result.success()) {
            if (mode == ShopGUIPlusConverter.ConversionMode.APPLY) {
                plugin.reloadAll();
                plugin.getMessages().send(sender, "convert-success", Map.of(
                        "{count}", String.valueOf(result.convertedCount()),
                        "{plugin}", sourcePlugin.displayName()
                ));
            } else {
                plugin.getMessages().send(sender, "convert-dryrun", Map.of(
                        "{count}", String.valueOf(result.convertibleCount()),
                        "{plugin}", sourcePlugin.displayName()
                ));
            }
            plugin.getMessages().send(sender, "convert-report", Map.of("{message}", result.message()));
        } else {
            plugin.getMessages().send(sender, "convert-fail", Map.of("{reason}", result.message()));
            plugin.getAdminSounds().updateError(sender);
        }
    }

    private void handleRotatingShop(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("reset")) {
            plugin.getMessages().send(sender, "admin-usage");
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        plugin.getRotatingShopService().resetNow();
        plugin.getMessages().send(sender, "rotating-shop-reset");
        play(sender, "shop.rotating-reset");
    }

    private void handleSellBooster(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "booster-usage");
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list", "active" -> listBoosters(sender);
            case "start", "add" -> startBooster(sender, args);
            case "clear", "remove", "delete" -> clearBooster(sender, args);
            default -> {
                plugin.getMessages().send(sender, "booster-usage");
                plugin.getAdminSounds().updateError(sender);
            }
        }
    }

    private void listBoosters(CommandSender sender) {
        List<ActiveSellBooster> boosters = plugin.getSellBoosterService().activeBoosters();
        if (boosters.isEmpty()) {
            plugin.getMessages().send(sender, "booster-list-empty");
            return;
        }

        plugin.getMessages().send(sender, "booster-list-header", Map.of("{count}", String.valueOf(boosters.size())));
        for (ActiveSellBooster booster : boosters) {
            plugin.getMessages().send(sender, "booster-list-entry", Map.of(
                    "{id}", booster.id(),
                    "{scope}", booster.scope().display(),
                    "{target}", booster.displayOwner(),
                    "{multiplier}", plugin.getSellBoosterService().formatMultiplier(booster.multiplier()),
                    "{time}", plugin.getSellBoosterService().formatRemaining(booster)
            ));
        }
    }

    private void startBooster(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.getMessages().send(sender, "booster-usage");
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        Optional<SellBoosterScope> scope = SellBoosterScope.fromInput(args[2]);
        if (scope.isEmpty()) {
            plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "Unknown scope."));
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        SellBoosterInputParser.StartInput input = SellBoosterInputParser.parseCommandStart(scope.get(), args).orElse(null);
        if (input == null) {
            plugin.getMessages().send(sender, "booster-usage");
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        SellBoosterInputParser.Target target = startTarget(sender, scope.get(), input.target());
        if (target == null) {
            return;
        }

        Double multiplier = SellBoosterInputParser.parseMultiplier(input.multiplier());
        if (multiplier == null || multiplier <= 1D) {
            plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "Multiplier must be above 1x."));
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        OptionalLong duration = DurationUtil.parseSeconds(input.duration());
        if (duration.isEmpty() || duration.getAsLong() < 60L) {
            plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "Duration must be 60s+, 1h, or 1d."));
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        ActiveSellBooster booster = plugin.getSellBoosterService().start(scope.get(), target.ownerId(), target.ownerName(), multiplier, duration.getAsLong());
        plugin.getMessages().send(sender, "booster-started", Map.of(
                "{id}", booster.id(),
                "{scope}", booster.scope().display(),
                "{target}", booster.displayOwner(),
                "{multiplier}", plugin.getSellBoosterService().formatMultiplier(booster.multiplier()),
                "{time}", DurationUtil.format(duration.getAsLong())
        ));
        play(sender, "shop.booster-started");
    }

    private SellBoosterInputParser.Target startTarget(CommandSender sender, SellBoosterScope scope, String input) {
        if (scope == SellBoosterScope.GLOBAL) {
            return new SellBoosterInputParser.Target("", "Global");
        }

        if (scope == SellBoosterScope.PLAYER) {
            Optional<SellBoosterInputParser.Target> target = SellBoosterInputParser.resolvePlayerTarget(input);
            if (target.isEmpty()) {
                plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "Player not found."));
                plugin.getAdminSounds().updateError(sender);
                return null;
            }
            return target.get();
        }

        if (!plugin.getSellBoosterService().isFoTeamsAvailable()) {
            plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "FoTeams is not installed or enabled."));
            plugin.getAdminSounds().updateError(sender);
            return null;
        }
        Optional<FoTeamsHook.TeamInfo> team = plugin.getSellBoosterService().teamByInput(input);
        if (team.isEmpty()) {
            plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "Team not found."));
            plugin.getAdminSounds().updateError(sender);
            return null;
        }
        return new SellBoosterInputParser.Target(team.get().id(), team.get().name());
    }

    private void clearBooster(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessages().send(sender, "booster-usage");
            plugin.getAdminSounds().updateError(sender);
            return;
        }

        String target = args[2];
        if (target.equalsIgnoreCase("all")) {
            int removed = plugin.getSellBoosterService().clearAll();
            plugin.getMessages().send(sender, "booster-cleared", Map.of("{count}", String.valueOf(removed)));
            if (removed > 0) {
                play(sender, "shop.booster-removed");
            }
            return;
        }

        Optional<SellBoosterScope> scope = SellBoosterScope.fromInput(target);
        if (scope.isPresent()) {
            String rawOwner = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim() : "";
            String owner = rawOwner.isBlank() ? "" : resolveClearOwner(scope.get(), rawOwner);
            int removed = plugin.getSellBoosterService().clearScope(scope.get(), owner);
            plugin.getMessages().send(sender, "booster-cleared", Map.of("{count}", String.valueOf(removed)));
            if (removed > 0) {
                play(sender, "shop.booster-removed");
            }
            return;
        }

        boolean removed = plugin.getSellBoosterService().remove(target);
        if (removed) {
            plugin.getMessages().send(sender, "booster-cleared", Map.of("{count}", "1"));
            play(sender, "shop.booster-removed");
        } else {
            plugin.getMessages().send(sender, "booster-invalid", Map.of("{reason}", "Booster id not found."));
            plugin.getAdminSounds().updateError(sender);
        }
    }

    private void play(CommandSender sender, String soundPath) {
        if (sender instanceof Player player) {
            plugin.getSounds().play(player, soundPath);
        }
    }

    private String resolveClearOwner(SellBoosterScope scope, String input) {
        if (scope == SellBoosterScope.PLAYER) {
            return SellBoosterInputParser.resolvePlayerTarget(input).map(SellBoosterInputParser.Target::ownerId).orElse(input);
        }
        if (scope == SellBoosterScope.TEAM) {
            return plugin.getSellBoosterService().teamByInput(input).map(FoTeamsHook.TeamInfo::id).orElse(input);
        }
        return "";
    }

    private List<String> rotatingShopTabs(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("rotatingshop")) {
            return filterPrefix(List.of("reset"), args[1]);
        }
        return List.of();
    }

    private List<String> convertTabs(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("convert")) {
            return filterPrefix(List.of("ShopGUIPlus", "EconomyShopGUI"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("convert")
                && ShopGUIPlusConverter.SourcePlugin.fromInput(args[1]).isPresent()) {
            return filterPrefix(List.of("dryrun", "apply"), args[2]);
        }

        return List.of();
    }

    private List<String> boosterTabs(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "start", "clear"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("start")) {
            return filterPrefix(List.of("global", "player", "team"), args[2]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("clear")) {
            List<String> options = new ArrayList<>(List.of("all", "global", "player", "team"));
            plugin.getSellBoosterService().activeBoosters().stream().map(ActiveSellBooster::id).forEach(options::add);
            return filterPrefix(options, args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("start") && args[2].equalsIgnoreCase("player")) {
            return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("start") && args[2].equalsIgnoreCase("team")) {
            return filterPrefix(plugin.getSellBoosterService().teamNames(), args[3]);
        }
        if ((args.length == 4 && args[1].equalsIgnoreCase("start") && args[2].equalsIgnoreCase("global"))
                || (args.length == 5 && args[1].equalsIgnoreCase("start") && !args[2].equalsIgnoreCase("global"))) {
            return filterPrefix(List.of("1.25", "1.5", "2", "2.5", "3"), args[args.length - 1]);
        }
        if ((args.length == 5 && args[1].equalsIgnoreCase("start") && args[2].equalsIgnoreCase("global"))
                || (args.length == 6 && args[1].equalsIgnoreCase("start") && !args[2].equalsIgnoreCase("global"))) {
            return filterPrefix(List.of("30m", "1h", "6h", "12h", "1d"), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> filterPrefix(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) {
                result.add(option);
            }
        }
        return result;
    }

    private static final class LoggedAdminCommand implements TabExecutor {
        private final FoShop plugin;
        private final FoAdminCommand delegate;

        private LoggedAdminCommand(FoShop plugin, FoAdminCommand delegate) {
            this.plugin = plugin;
            this.delegate = delegate;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length > 0 && sender.hasPermission("foshop.admin") && plugin.getFileLogger() != null) {
                plugin.getFileLogger().info("Admin command used by " + sender.getName() + ": " + String.join(" ", args));
            }
            return delegate.onCommand(sender, command, label, args);
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return delegate.onTabComplete(sender, command, alias, args);
        }
    }
}
