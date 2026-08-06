package me.foesio.foShop.logging;

import me.foesio.foShop.FoShop;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class SellTransactionLogger {

    private final FoShop plugin;

    public SellTransactionLogger(FoShop plugin) {
        this.plugin = plugin;
    }

    public void write(Player player, int soldUnits, String earned, String receipt) {
        if (!plugin.getFoConfig().isSellTransactionLogEnabled() || plugin.getCore() == null) {
            return;
        }

        String line = timestamp()
                + " | " + player.getName()
                + " | " + player.getUniqueId()
                + " | items=" + soldUnits
                + " | earned=" + earned
                + " | receipt=" + receipt
                + System.lineSeparator();

        plugin.getCore().scheduler().runAsync(() -> append(line));
    }

    private String timestamp() {
        try {
            return new SimpleDateFormat(plugin.getFoConfig().getSellTransactionLogDateFormat()).format(new Date());
        } catch (IllegalArgumentException exception) {
            return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
        }
    }

    private void append(String line) {
        try {
            File folder = new File(plugin.getDataFolder(), "transactions");
            Files.createDirectories(folder.toPath());
            Files.writeString(new File(folder, "sellgui.log").toPath(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed writing SellGUI transaction log: " + exception.getMessage());
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Failed writing SellGUI transaction log: " + exception.getMessage());
            }
        }
    }
}
