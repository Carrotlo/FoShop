package me.foesio.foShop.command;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class FoSellCommand implements CommandExecutor, TabCompleter {

    private final FoShop plugin;
    private final Mode mode;

    public FoSellCommand(FoShop plugin, Mode mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }

        if (mode == Mode.ROOT) {
            return handleRoot(sender, player, args);
        }

        if (!sender.hasPermission(mode.permission())) {
            plugin.getMessages().send(sender, "no-permission");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }

        switch (mode) {
            case ALL -> plugin.getGuiService().sellAll(player);
            case HAND -> plugin.getGuiService().sellHand(player);
            case ROOT -> {
            }
        }
        return true;
    }

    private boolean handleRoot(CommandSender sender, Player player, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission("foshop.sellgui.use")) {
                plugin.getMessages().send(sender, "no-permission");
                plugin.getAdminSounds().updateError(sender);
                return true;
            }
            plugin.getGuiService().openSellGui(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "all", "inventory", "inv", "sellall" -> {
                if (!sender.hasPermission("foshop.sellall")) {
                    plugin.getMessages().send(sender, "no-permission");
                    plugin.getAdminSounds().updateError(sender);
                    return true;
                }
                plugin.getGuiService().sellAll(player);
            }
            case "hand", "held", "mainhand", "sellhand" -> {
                if (!sender.hasPermission("foshop.sellhand")) {
                    plugin.getMessages().send(sender, "no-permission");
                    plugin.getAdminSounds().updateError(sender);
                    return true;
                }
                plugin.getGuiService().sellHand(player);
            }
            case "gui", "menu", "open" -> {
                if (!sender.hasPermission("foshop.sellgui.use")) {
                    plugin.getMessages().send(sender, "no-permission");
                    plugin.getAdminSounds().updateError(sender);
                    return true;
                }
                plugin.getGuiService().openSellGui(player);
            }
            default -> {
                plugin.getMessages().send(sender, "sell-usage");
                plugin.getAdminSounds().updateError(sender);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (mode != Mode.ROOT || args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("all", "hand", "gui").stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }

    public enum Mode {
        ROOT(null),
        ALL("foshop.sellall"),
        HAND("foshop.sellhand");

        private final String permission;

        Mode(String permission) {
            this.permission = permission;
        }

        public String permission() {
            return permission;
        }
    }
}
