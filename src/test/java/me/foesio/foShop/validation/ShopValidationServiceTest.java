package me.foesio.foShop.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopValidationServiceTest {

    @Test
    void malformedShopFileDoesNotBecomeAnEmptySection(@TempDir Path root) throws Exception {
        Path file = root.resolve("broken.yml");
        Files.writeString(file, "items: [broken\n");

        var result = new ShopValidationService().parseAndValidate(file.toFile());

        assertFalse(result.validSection());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.startsWith("shop file invalid:")));
    }

    @Test
    void invalidPublicSectionPlacementAndVisibilityCannotUseSilentDefaults() throws Exception {
        YamlConfiguration section = new YamlConfiguration();
        section.loadFromString("id: test\ntitle: Test\nsize: 27\nicon: CHEST\nslot: '10'\n");
        var validator = new ShopValidationService();
        assertFalse(validator.validateSectionYaml("test", section).validSection());

        section.set("slot", 10.5);
        assertFalse(validator.validateSectionYaml("test", section).validSection());

        section.set("slot", 10);
        section.set("enabled", "false");
        assertFalse(validator.validateSectionYaml("test", section).validSection());

    }

    @Test
    void malformedPublicSectionTextAndSizeCannotBecomeDefaults() throws Exception {
        YamlConfiguration section = new YamlConfiguration();
        section.loadFromString("id: test\ntitle: Test\nsize: 27\nicon: CHEST\nslot: 10\n");
        var validator = new ShopValidationService();

        section.set("title", List.of("wrong type"));
        assertFalse(validator.validateSectionYaml("test", section).validSection());
        section.set("title", "Test");

        section.set("description", List.of("valid", 3));
        assertFalse(validator.validateSectionYaml("test", section).validSection());
        section.set("description", List.of("valid", "also valid"));

        section.set("size", "27");
        assertFalse(validator.validateSectionYaml("test", section).validSection());
        section.set("size", 27);

        section.set("icon", List.of("CHEST"));
        assertFalse(validator.validateSectionYaml("test", section).validSection());
    }
}
