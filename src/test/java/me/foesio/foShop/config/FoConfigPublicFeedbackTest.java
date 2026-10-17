package me.foesio.foShop.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoConfigPublicFeedbackTest {

    @Test
    void currentSchemaDoesNotResurrectRemovedTitleOrActionBar() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("sellgui.titles.title", "English title");
        defaults.set("sellgui.titles.enabled", true);
        defaults.set("sellgui.action-bar.enabled", true);
        YamlConfiguration owner = new YamlConfiguration();
        owner.setDefaults(defaults);
        owner.set("gui.sell.titles.title", "Old title");
        owner.set("sellgui.titles.subtitle", "Overført {amount}");

        assertEquals("", FoConfig.publicFeedbackText(owner, "sellgui.titles.title",
                "gui.sell.titles.title", true));
        assertFalse(FoConfig.publicFeedbackEnabled(owner, "sellgui.titles.enabled",
                "gui.sell.titles.enabled", true));
        assertFalse(FoConfig.publicFeedbackEnabled(owner, "sellgui.action-bar.enabled",
                "gui.sell.action-bar.enabled", true));
        assertEquals("Overført {amount}", FoConfig.publicFeedbackText(owner,
                "sellgui.titles.subtitle", "gui.sell.titles.subtitle", true));
        assertEquals("Old title", FoConfig.publicFeedbackText(owner,
                "sellgui.titles.title", "gui.sell.titles.title", false));
        assertTrue(owner.contains("sellgui.titles.title"));
        assertFalse(owner.isSet("sellgui.titles.title"));
    }

    @Test
    void removedGamemodeNameDoesNotBecomeJavaEnumText() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("public-values.gamemode-names.CREATIVE", "CREATIVE");
        YamlConfiguration owner = new YamlConfiguration();
        owner.setDefaults(defaults);
        owner.set("public-values.gamemode-names.CREATIVE", "Kreativ ✓");
        assertEquals("Kreativ ✓", FoConfig.configuredGamemodeName(owner, "CREATIVE"));
        owner.set("public-values.gamemode-names.CREATIVE", "");
        assertEquals("", FoConfig.configuredGamemodeName(owner, "CREATIVE"));
        owner.set("public-values.gamemode-names.CREATIVE", null);
        assertEquals("", FoConfig.configuredGamemodeName(owner, "CREATIVE"));
        assertTrue(owner.contains("public-values.gamemode-names.CREATIVE"));
        assertFalse(owner.isSet("public-values.gamemode-names.CREATIVE"));
    }
}
