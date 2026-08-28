package me.foesio.foShop.command;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FoWorthCommand implements CommandExecutor {

    private final FoShop plugin;

    public FoWorthCommand(FoShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foshop.worth")) {
            plugin.getMessages().send(sender, "no-permission");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }

        plugin.getGuiService().openWorth(player);
        return true;
    }
}
