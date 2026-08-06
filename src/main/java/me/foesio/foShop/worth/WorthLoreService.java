package me.foesio.foShop.worth;

import me.foesio.foShop.FoShop;
import org.bukkit.plugin.Plugin;

public interface WorthLoreService {

    void reload();

    void shutdown();

    static WorthLoreService create(FoShop plugin) {
        Plugin packetEvents = plugin.getServer().getPluginManager().getPlugin("packetevents");
        if (packetEvents == null) {
            packetEvents = plugin.getServer().getPluginManager().getPlugin("PacketEvents");
        }
        if (packetEvents == null || !packetEvents.isEnabled()) {
            return DisabledWorthLoreService.INSTANCE;
        }

        try {
            WorthLoreService service = new PacketEventsWorthLoreService(plugin);
            plugin.getLogger().info("PacketEvents worth lore hook enabled.");
            return service;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Installed PacketEvents hook could not start: " + exception.getMessage());
            return DisabledWorthLoreService.INSTANCE;
        }
    }

    enum DisabledWorthLoreService implements WorthLoreService {
        INSTANCE;

        @Override
        public void reload() {
        }

        @Override
        public void shutdown() {
        }
    }
}
