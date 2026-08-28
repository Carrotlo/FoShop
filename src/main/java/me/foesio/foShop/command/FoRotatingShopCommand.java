package me.foesio.foShop.command;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FoRotatingShopCommand implements CommandExecutor {

    private final FoShop plugin;

    public FoRotatingShopCommand(FoShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }

        if (!player.hasPermission("foshop.rotatingshop")) {
            plugin.getMessages().send(player, "no-permission");
            plugin.getAdminSounds().updateError(player);
            return true;
        }

        plugin.getGuiService().openRotatingShop(player);
        return true;
    }
}
