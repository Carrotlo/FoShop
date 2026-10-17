package me.foesio.foShop.gui;

import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorthVisibilityTest {

    @Test
    void disabledRegionsDoNotConsumeWorthEntries() throws Exception {
        YamlConfiguration gui;
        try (var resource = getClass().getClassLoader().getResourceAsStream("guis/worth.yml")) {
            gui = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        var contract = new PublicGuiContract(Set.of("shop.worth-previous", "shop.worth-sort",
                "shop.worth-search", "shop.worth-clear-search", "shop.worth-next"), Map.of(),
                Set.of("item", "worth", "page", "pages", "sort", "next_sort", "query"),
                PublicGuiContract.InteractionMode.CORE);

        gui.set("entries.center.enabled", false);
        var pricesOnly = PublicGuiSchema.parse("shop.worth", "guis/worth.yml", gui, contract);
        assertEquals(pricesOnly.entries().get("prices").slots(), GuiService.visibleWorthSlots(pricesOnly));
        var frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(pricesOnly.menuId(), pricesOnly)),
                pricesOnly.menuId(), PublicGuiRenderInput.empty());
        assertEquals("filler", frame.visuals().get(22).path());
        assertEquals("entries.prices.empty-item", frame.visuals().get(10).path());

        gui.set("entries.prices.enabled", false);
        var neither = PublicGuiSchema.parse("shop.worth", "guis/worth.yml", gui, contract);
        assertEquals(List.of(), GuiService.visibleWorthSlots(neither));
        assertEquals(1, GuiService.worthPageCount(20, 0));
    }
}
