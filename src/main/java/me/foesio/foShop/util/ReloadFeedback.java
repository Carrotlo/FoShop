package me.foesio.foShop.util;

import me.foesio.foShop.FoShop;
import me.foesio.foShop.shop.ShopManager;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class ReloadFeedback {

    private ReloadFeedback() {
    }

    public static void send(FoShop plugin, CommandSender sender, ShopManager.ReloadResult result) {
        plugin.getMessages().send(sender, "reload-success", Map.of(
                "{count}", String.valueOf(result.loadedSections())
        ));
        plugin.getMessages().send(sender, "reload-detail", Map.of(
                "{sections}", String.valueOf(result.skippedSections()),
                "{items}", String.valueOf(result.skippedItems())
        ));
        if (!result.issues().isEmpty()) {
            plugin.getMessages().send(sender, "reload-warning", Map.of(
                    "{count}", String.valueOf(result.issues().size()),
                    "{reason}", result.issues().getFirst()
            ));
        }
    }
}
