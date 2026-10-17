package me.foesio.foShop.gui;

import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiContract;
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

class PublicEntryVisibilityTest {

    @Test
    void rotatingEntriesUseOnlyEnabledRegions() throws Exception {
        YamlConfiguration gui = resource("rotating-shop");
        gui.set("entries.center.enabled", false);
        var definition = PublicGuiSchema.parse("shop.rotating_shop", "guis/rotating-shop.yml", gui,
                new PublicGuiContract(Set.of(), Map.of(),
                        Set.of("item", "boost", "price", "base_price", "buy", "time"),
                        PublicGuiContract.InteractionMode.CORE));
        assertEquals(definition.entries().get("boosts").slots(),
                GuiService.visibleEntrySlots(definition, "center", "boosts"));
        var frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(definition.menuId(), definition)),
                definition.menuId(), PublicGuiRenderInput.empty());
        assertEquals("filler", frame.visuals().get(13).path());

        gui.set("entries.boosts.enabled", false);
        var neither = PublicGuiSchema.parse("shop.rotating_shop", "guis/rotating-shop.yml", gui,
                new PublicGuiContract(Set.of(), Map.of(),
                        Set.of("item", "boost", "price", "base_price", "buy", "time"),
                        PublicGuiContract.InteractionMode.CORE));
        assertEquals(List.of(), GuiService.visibleEntrySlots(neither, "center", "boosts"));
    }

    @Test
    void disabledBulkOptionsCannotKeepBulkChoiceAvailable() throws Exception {
        YamlConfiguration gui = resource("buy-more");
        gui.set("entries.options.enabled", false);
        var definition = PublicGuiSchema.parse("shop.buy_more", "guis/buy-more.yml", gui,
                new PublicGuiContract(Set.of("shop.more-back", "shop.more-buy"), Map.of(),
                        Set.of("item", "amount", "price", "stacks", "plural", "stack_unit"),
                        PublicGuiContract.InteractionMode.CORE));
        assertEquals(List.of(), GuiService.visibleBulkStackOptions(List.of(1, 2, 3),
                definition.entries().get("options")));
    }

    private static YamlConfiguration resource(String name) throws Exception {
        try (var stream = PublicEntryVisibilityTest.class.getClassLoader()
                .getResourceAsStream("guis/" + name + ".yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
