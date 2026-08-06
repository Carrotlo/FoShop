package me.foesio.foShop.util;

import me.foesio.core.text.FoText;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class Text {

    private Text() {
    }

    public static String colorize(String text) {
        return FoText.color(text);
    }

    public static String format(String template, Map<String, String> placeholders) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return colorize(output);
    }

    public static void send(CommandSender sender, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        sender.sendMessage(colorize(message));
    }

}
