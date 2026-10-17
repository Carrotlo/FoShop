package me.foesio.foShop.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuyMoreOptionsTest {

    @Test
    void bundledChoicesMatchReleasedBulkMenu() throws Exception {
        try (var resource = BuyMoreOptionsTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (resource == null) throw new AssertionError("Bundled config.yml missing");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            assertEquals(List.of(1, 2, 3, 4, 6, 8, 9), GuiService.configuredBulkStackOptions(config));
        }
    }

    @Test
    void ownerOrderAndRemovalDetermineVisibleChoices() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("public-gui.bulk-stack-options", List.of(9, 1, 3));
        assertEquals(List.of(9, 1, 3), GuiService.configuredBulkStackOptions(config));
        assertEquals(false, GuiService.invalidBulkStackOptions(config,
                GuiService.configuredBulkStackOptions(config)));

        config.set("public-gui.bulk-stack-options", List.of());
        assertEquals(List.of(), GuiService.configuredBulkStackOptions(config));

        config.set("public-gui.bulk-stack-options", null);
        assertEquals(List.of(), GuiService.configuredBulkStackOptions(config));
        assertEquals(false, GuiService.invalidBulkStackOptions(config,
                GuiService.configuredBulkStackOptions(config)));
    }

    @Test
    void malformedBulkChoicesFailClosed() {
        YamlConfiguration config = new YamlConfiguration();
        for (Object invalid : List.of(List.of(1, 10), List.of(1, 1), List.of("three"), "1,2")) {
            config.set("public-gui.bulk-stack-options", invalid);
            assertEquals(List.of(), GuiService.configuredBulkStackOptions(config));
            assertEquals(true, GuiService.invalidBulkStackOptions(config,
                    GuiService.configuredBulkStackOptions(config)));
        }
    }
}
