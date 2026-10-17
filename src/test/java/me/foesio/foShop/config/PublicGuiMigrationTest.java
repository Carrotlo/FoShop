package me.foesio.foShop.config;

import me.foesio.core.gui.publicgui.ClickKind;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiText;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PublicGuiMigrationTest {

    @Test
    void allGeneratedMenusPassCoreSchema() throws Exception {
        for (String name : List.of("main", "shop-section", "buy-item", "buy-more",
                "sell-gui", "rotating-shop", "worth")) {
            PublicGuiMigration.validate(name, yaml(resource("guis/" + name + ".yml")));
        }
    }

    @Test
    void releasedGuiShapesMigrateToCoreSchema() throws Exception {
        for (int version : List.of(0, 1)) {
            for (String name : List.of("main", "shop-section", "buy-item", "buy-more",
                    "sell-gui", "rotating-shop")) {
                byte[] previous = resource("legacy-guis/v" + version + "/" + name + ".yml");
                byte[] migrated = PublicGuiMigration.migrateGui(name, previous, previous,
                        resource("guis/" + name + ".yml"), new YamlConfiguration(), version, null);
                PublicGuiMigration.validate(name, yaml(migrated));
            }
        }
    }

    @Test
    void missingVersionMarkerDoesNotRewriteCurrentOwnerGui() throws Exception {
        for (String name : List.of("main", "shop-section", "buy-item", "buy-more",
                "sell-gui", "rotating-shop", "worth")) {
            YamlConfiguration current = yaml(resource("guis/" + name + ".yml"));
            current.set("title", "&8Eier ✓ " + name);
            current.set("filler.lore", null);
            byte[] ownerBytes = ("# Keep this owner comment\n" + current.saveToString())
                    .getBytes(StandardCharsets.UTF_8);
            byte[] oldDefault = name.equals("worth") ? new byte[0]
                    : resource("legacy-guis/v1/" + name + ".yml");
            byte[] migrated = PublicGuiMigration.migrateExistingGui(name, ownerBytes, oldDefault,
                    resource("guis/" + name + ".yml"), new YamlConfiguration(), 1, null);
            assertArrayEquals(ownerBytes, migrated, name);
        }
    }

    @Test
    void legacyFillerEnabledDoesNotHideOldShapeButBrokenCurrentFileStaysUntouched() throws Exception {
        YamlConfiguration legacy = yaml(resource("legacy-guis/v1/main.yml"));
        legacy.set("filler.enabled", false);
        legacy.set("title", "&8Eier ✓");
        YamlConfiguration upgraded = yaml(PublicGuiMigration.migrateExistingGui("main", bytes(legacy),
                resource("legacy-guis/v1/main.yml"), resource("guis/main.yml"),
                new YamlConfiguration(), 1, null));
        assertEquals("&8Eier ✓", upgraded.getString("title"));
        assertFalse(upgraded.getBoolean("filler.enabled"));
        assertTrue(upgraded.isConfigurationSection("entries.sections"));
        PublicGuiMigration.validate("main", upgraded);

        YamlConfiguration current = yaml(resource("guis/main.yml"));
        current.set("entries", null);
        byte[] ownerBytes = bytes(current);
        assertArrayEquals(ownerBytes, PublicGuiMigration.migrateExistingGui("main", ownerBytes,
                resource("legacy-guis/v1/main.yml"), resource("guis/main.yml"),
                new YamlConfiguration(), 1, null));

        YamlConfiguration currentSell = yaml(resource("guis/sell-gui.yml"));
        currentSell.set("sell-slots", null);
        byte[] sellBytes = bytes(currentSell);
        assertArrayEquals(sellBytes, PublicGuiMigration.migrateExistingGui("sell-gui", sellBytes,
                resource("legacy-guis/v1/sell-gui.yml"), resource("guis/sell-gui.yml"),
                new YamlConfiguration(), 1, null));

        YamlConfiguration legacyRotating = yaml(resource("legacy-guis/v1/rotating-shop.yml"));
        legacyRotating.set("filler.enabled", false);
        YamlConfiguration rotating = yaml(PublicGuiMigration.migrateExistingGui("rotating-shop",
                bytes(legacyRotating), resource("legacy-guis/v1/rotating-shop.yml"),
                resource("guis/rotating-shop.yml"), new YamlConfiguration(), 1, null));
        assertFalse(rotating.getBoolean("filler.enabled"));
        assertTrue(rotating.isConfigurationSection("entries.center"));
        PublicGuiMigration.validate("rotating-shop", rotating);
    }

    @Test
    void invalidCurrentGuiFieldIsLeftForInMemoryStartupFallback() throws Exception {
        YamlConfiguration current = yaml(resource("guis/buy-more.yml"));
        current.set("title", "&8Eier ✓");
        current.set("buttons.back.material", "NOT_A_MATERIAL");
        byte[] ownerBytes = bytes(current);
        byte[] migrated = PublicGuiMigration.migrateExistingGui("buy-more", ownerBytes,
                resource("legacy-guis/v1/buy-more.yml"), resource("guis/buy-more.yml"),
                new YamlConfiguration(), 1, null);
        assertArrayEquals(ownerBytes, migrated);
        assertThrows(me.foesio.core.gui.publicgui.GuiDefinitionException.class,
                () -> PublicGuiMigration.validate("buy-more", yaml(migrated)));
    }

    @Test
    void alreadyCurrentInstallationIsRecognizedWithoutWorthFile(@TempDir Path root) throws Exception {
        for (String name : List.of("main", "shop-section", "buy-item", "buy-more",
                "sell-gui", "rotating-shop")) {
            Files.write(root.resolve(name + ".yml"), resource("guis/" + name + ".yml"));
        }
        assertTrue(PublicGuiMigration.isCurrentInstallation(root));
        Files.writeString(root.resolve("worth.yml"), "title: old\nentries:\n  material: {}\n");
        assertFalse(PublicGuiMigration.isCurrentInstallation(root));
        Files.write(root.resolve("worth.yml"), resource("guis/worth.yml"));
        assertTrue(PublicGuiMigration.isCurrentInstallation(root));
        Files.write(root.resolve("main.yml"), resource("legacy-guis/v1/main.yml"));
        assertFalse(PublicGuiMigration.isCurrentInstallation(root));
        YamlConfiguration v0WithFillerEnabled = yaml(resource("legacy-guis/v0/main.yml"));
        v0WithFillerEnabled.set("filler.enabled", true);
        Files.write(root.resolve("main.yml"), bytes(v0WithFillerEnabled));
        assertFalse(PublicGuiMigration.isCurrentInstallation(root));
    }

    @Test
    void currentGuisAloneDoNotProveMessagesAndConfigWereMigrated(@TempDir Path root) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("public-values.stack-singular", "stack");
        config.setDefaults(defaults);
        Files.write(root.resolve("messages.yml"), resource("legacy-guis/v1/messages.yml"));
        assertFalse(PublicGuiMigration.hasCurrentPublicResources(root, config));

        Files.write(root.resolve("messages.yml"), resource("messages.yml"));
        assertFalse(PublicGuiMigration.hasCurrentPublicResources(root, config));

        Files.write(root.resolve("messages.yml"), resource("legacy-guis/v1/messages.yml"));
        config.set("public-values.stack-singular", "stack");
        assertFalse(PublicGuiMigration.hasCurrentPublicResources(root, config));

        Files.write(root.resolve("messages.yml"), resource("messages.yml"));
        assertTrue(PublicGuiMigration.hasCurrentPublicResources(root, config));
    }

    @Test
    void malformedVersionMarkerCannotSelectAnOldMigrationBaseline() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(0, PublicGuiMigration.sourceVersion(config));
        config.set(PublicGuiMigration.VERSION_PATH, "1");
        assertThrows(IllegalArgumentException.class, () -> PublicGuiMigration.sourceVersion(config));
        config.set(PublicGuiMigration.VERSION_PATH, 1.5);
        assertThrows(IllegalArgumentException.class, () -> PublicGuiMigration.sourceVersion(config));
        config.set(PublicGuiMigration.VERSION_PATH, -1);
        assertThrows(IllegalArgumentException.class, () -> PublicGuiMigration.sourceVersion(config));
        config.set(PublicGuiMigration.VERSION_PATH, 1);
        assertEquals(1, PublicGuiMigration.sourceVersion(config));
        config.set(PublicGuiMigration.VERSION_PATH, 2);
        assertEquals(2, PublicGuiMigration.sourceVersion(config));
        config.set(PublicGuiMigration.VERSION_PATH, 3);
        assertThrows(IllegalArgumentException.class, () -> PublicGuiMigration.sourceVersion(config));
    }

    @Test
    void newWorthDefaultsLeaveNativeNamesUnsetExceptForEnchantedBooks() throws Exception {
        YamlConfiguration generated = yaml(resource("guis/worth.yml"));
        for (String region : List.of("center", "prices")) {
            String source = "entries." + region + ".source-item";
            assertFalse(generated.isSet(source + ".default-name"));
            assertFalse(generated.isSet(source + ".lore-states.material.default-name"));
            assertFalse(generated.isSet(source + ".lore-states.potion.default-name"));
            assertEquals("&#ffffff{item}",
                    generated.getString(source + ".lore-states.enchantment.default-name"));
        }
        byte[] unknown = "title: old\nentries:\n  enchantment: {}\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> PublicGuiMigration.migrateExistingGui("worth", unknown,
                new byte[0], resource("guis/worth.yml"), new YamlConfiguration(), 1, null));
    }

    @Test
    void oldCustomizedButtonAndInvalidMaterialUseFieldLevelDefault() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/buy-item.yml"));
        old.set("buttons.confirm.name", "Ångström {amount} ✓");
        old.set("buttons.confirm.lore", List.of());
        old.set("buttons.confirm.slot", 20);
        old.set("buttons.confirm.material", "NOT_A_MATERIAL");
        byte[] migrated = PublicGuiMigration.migrateGui("buy-item", bytes(old),
                resource("legacy-guis/v1/buy-item.yml"), resource("guis/buy-item.yml"),
                new YamlConfiguration(), 1, null);
        YamlConfiguration result = yaml(migrated);
        assertEquals("Ångström {amount} ✓", result.getString("buttons.confirm.name"));
        assertEquals(List.of(), result.getStringList("buttons.confirm.lore"));
        assertEquals(20, result.getInt("buttons.confirm.slot"));
        assertEquals("LIME_SHULKER_BOX", result.getString("buttons.confirm.material"));
        assertEquals("shop.amount-confirm", result.getString("buttons.confirm.action"));
    }

    @Test
    void invalidLegacySlotListMemberUsesNewestListAndKeepsCustomSibling() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/buy-item.yml"));
        old.set("selected-item.slot", 999);
        old.set("buttons.confirm.name", "Bekreft kjøp ✓");
        YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("buy-item", bytes(old),
                resource("legacy-guis/v1/buy-item.yml"), resource("guis/buy-item.yml"),
                new YamlConfiguration(), 1, null));
        assertEquals(List.of(13), result.getIntegerList("entries.preview.slots"));
        assertEquals("Bekreft kjøp ✓", result.getString("buttons.confirm.name"));
        PublicGuiMigration.validate("buy-item", result);
    }

    @Test
    void legacyButtonOverlappingDefaultPreviewRepairsButtonSlotOnly() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/buy-item.yml"));
        old.set("buttons.add-1.slot", 13);
        old.set("buttons.add-1.name", "Eier ✓");
        YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("buy-item", bytes(old),
                resource("legacy-guis/v1/buy-item.yml"), resource("guis/buy-item.yml"),
                new YamlConfiguration(), 1, null));
        assertEquals(14, result.getInt("buttons.add-1.slot"));
        assertEquals("Eier ✓", result.getString("buttons.add-1.name"));
        assertEquals(List.of(13), result.getIntegerList("entries.preview.slots"));
        PublicGuiMigration.validate("buy-item", result);
    }

    @Test
    void invalidOptionalLegacyModelUsesAbsentBundledDefaultAndIgnoredFieldsStayAbsent() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/buy-item.yml"));
        old.set("buttons.confirm.custom-model-data", -2);
        old.set("buttons.confirm.item-flags", List.of("HIDE_ATTRIBUTES"));
        old.set("buttons.confirm.name", "Bekreft kjøp ✓");
        YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("buy-item", bytes(old),
                resource("legacy-guis/v1/buy-item.yml"), resource("guis/buy-item.yml"),
                new YamlConfiguration(), 1, null));
        assertFalse(result.isSet("buttons.confirm.custom-model-data"));
        assertFalse(result.isSet("buttons.confirm.item-flags"));
        assertEquals("Bekreft kjøp ✓", result.getString("buttons.confirm.name"));
        PublicGuiMigration.validate("buy-item", result);
    }

    @Test
    void removedOptionalPreviewLoreSurvivesUpgrade() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/buy-item.yml"));
        old.set("selected-item.lore", null);
        byte[] migrated = PublicGuiMigration.migrateGui("buy-item", bytes(old),
                resource("legacy-guis/v1/buy-item.yml"), resource("guis/buy-item.yml"),
                new YamlConfiguration(), 1, null);
        YamlConfiguration result = yaml(migrated);
        assertEquals("preserve", result.getString("entries.preview.source-item.lore.mode"));
        assertFalse(result.isSet("entries.preview.source-item.lore.lines"));
    }

    @Test
    void invalidLegacyLorePartUsesNewestDefaultWithoutLosingCustomSiblings() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/shop-section.yml"));
        YamlConfiguration latest = yaml(resource("guis/shop-section.yml"));
        old.set("item-lore.buy", 7);
        old.set("item-lore.sell", List.of("Å salg ✓ {sell}"));
        latest.set("entries.products.source-item.lore-states.buy-sell.lines",
                List.of("Nytt kjøp {buy}", "New sell {sell}"));

        YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("shop-section", bytes(old),
                resource("legacy-guis/v1/shop-section.yml"), bytes(latest),
                new YamlConfiguration(), 1, null));

        assertEquals(List.of("Nytt kjøp {buy}", "Å salg ✓ {sell}"),
                result.getStringList("entries.products.source-item.lore-states.buy-sell.lines"));
        assertEquals(List.of("&#a7b8b0Stock: &#03fc88{stock}"),
                result.getStringList("entries.products.source-item.lore-states.stock.lines"));
    }

    @Test
    void removedLegacyFillerStaysDisabledAfterUpgrade() throws Exception {
        for (boolean removeSection : List.of(false, true)) {
            YamlConfiguration old = yaml(resource("legacy-guis/v1/main.yml"));
            old.set(removeSection ? "filler" : "filler.material", null);
            YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("main", bytes(old),
                    resource("legacy-guis/v1/main.yml"), resource("guis/main.yml"),
                    new YamlConfiguration(), 1, null));
            assertFalse(result.getBoolean("filler.enabled"));
            PublicGuiMigration.validate("main", result);
        }
    }

    @Test
    void priorConfigTitleMovesWhenGuiHadOldDefault() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("gui.main.title", "&8Magasin ✓");
        config.set("gui.main.rows", 5);
        byte[] migrated = PublicGuiMigration.migrateGui("main", resource("legacy-guis/v0/main.yml"),
                resource("legacy-guis/v0/main.yml"), resource("guis/main.yml"), config, 0, null);
        YamlConfiguration result = yaml(migrated);
        assertEquals("&8Magasin ✓", result.getString("title"));
        assertEquals(5, result.getInt("rows"));
        assertEquals(45, result.getIntegerList("entries.sections.slots").size());
        assertEquals("shop.open-section", result.getString("entries.sections.actions.left"));
    }

    @Test
    void messageUpgradePreservesCustomTextAndIntentionalRemoval() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/messages.yml"));
        old.set("buy-success", "Kjøpt ✓ {item}");
        old.set("sellgui-none", null);
        byte[] migrated = PublicGuiMigration.migrateMessages(bytes(old),
                resource("legacy-guis/v1/messages.yml"), resource("messages.yml"), null);
        YamlConfiguration result = yaml(migrated);
        assertEquals("Kjøpt ✓ {item}", result.getString("buy-success"));
        assertFalse(result.getString("buy-success").contains(":emerald:"));
        assertEquals(":emerald: #03fc88FoShop &8» #a7b8b0", result.getString("tokens.prefix"));
        assertFalse(result.isSet("sellgui-none"));
        assertTrue(result.isSet("gui-unavailable"));
        assertEquals("Other Items", result.getString("sellgui-receipt-other-items"));
        assertEquals(" ", result.getString("sellgui-receipt-separator"));
        assertEquals("\n", result.getString("sellgui-receipt-line-separator"));
        assertEquals("{amount}x {item}", result.getString("sellgui-list-item-layout"));
        assertEquals(", ", result.getString("sellgui-list-separator"));
        assertEquals("{first} {second}", result.getString("duration.pair"));
    }

    @Test
    void soundUpgradeFallsBackOnlyInvalidPitch() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/sounds.yml"));
        old.set("sell.success.pitch", 8.0);
        old.set("sell.success.volume", 0.42);
        old.set("sell.failure.enabled", false);
        byte[] migrated = PublicGuiMigration.migrateSounds(bytes(old),
                resource("legacy-guis/v1/sounds.yml"), resource("sounds.yml"),
                new YamlConfiguration(), null);
        YamlConfiguration result = yaml(migrated);
        assertEquals(1.0, result.getDouble("sell.success.pitch"));
        assertEquals(0.42, result.getDouble("sell.success.volume"));
        assertFalse(result.getBoolean("sell.failure.enabled"));
    }

    @Test
    void listFormSellDecorationKeepsVisualAndReservesItsSlot() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/sell-gui.yml"));
        old.set("decorations", List.of(Map.of("slot", 10, "material", "DIAMOND",
                "name", "Owner decoration", "commandsOnClick", List.of("say hello"))));
        byte[] migrated = PublicGuiMigration.migrateGui("sell-gui", bytes(old),
                resource("legacy-guis/v1/sell-gui.yml"), resource("guis/sell-gui.yml"),
                new YamlConfiguration(), 1, null);
        YamlConfiguration result = yaml(migrated);
        assertEquals("DIAMOND", result.getString("decorations.legacy-0.material"));
        assertEquals("Owner decoration", result.getString("decorations.legacy-0.name"));
        assertFalse(result.getIntegerList("sell-slots").contains(10));
        PublicGuiMigration.validate("sell-gui", result);
    }

    @Test
    void smallerSellGuiKeepsItsRowsAndBottomButton() throws Exception {
        YamlConfiguration old = yaml(resource("legacy-guis/v1/sell-gui.yml"));
        old.set("rows", 4);
        old.set("buttons.sell.slot", 49);
        YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("sell-gui", bytes(old),
                resource("legacy-guis/v1/sell-gui.yml"), resource("guis/sell-gui.yml"),
                new YamlConfiguration(), 1, null));
        assertEquals(4, result.getInt("rows"));
        assertEquals(31, result.getInt("buttons.sell.slot"));
        assertEquals(27, result.getIntegerList("sell-slots").size());
        PublicGuiMigration.validate("sell-gui", result);
    }

    @Test
    void coreFrameUsesMovedControlEmptyLoreAndReorderedOwnerText() throws Exception {
        YamlConfiguration yaml = yaml(resource("guis/buy-more.yml"));
        yaml.set("buttons.back.slot", 21);
        yaml.set("buttons.back.lore", List.of());
        yaml.set("entries.options.item.name", "Å {stack_unit}: {stacks}");
        var definition = PublicGuiMigration.parseDefinition("buy-more", yaml);
        var snapshot = new GuiSnapshot<>(1, Map.of(definition.menuId(), definition));
        var frame = PublicGuiPlanner.plan(snapshot, definition.menuId(), new PublicGuiRenderInput(
                Map.of("item", "Granite"), Map.of(), Map.of(), Map.of("options", Map.of(10,
                new PublicGuiRenderInput.EntryValue("1", null,
                        Map.of("stacks", "1", "stack_unit", "STACK", "amount", "64", "price", "10"))))));
        assertEquals("shop.more-back", frame.bindings().get(21).actionFor(ClickKind.LEFT));
        assertEquals(List.of(), frame.visuals().get(21).fixed().lore());
        assertEquals("shop.more-buy", frame.bindings().get(10).actionFor(ClickKind.SHIFT_LEFT));
        assertEquals("1", frame.bindings().get(10).entryId());
        assertEquals("Å STACK: 1", PublicGuiText.scalar(frame.visuals().get(10).fixed().name(),
                frame.visuals().get(10).placeholders(), "entry.name"));
    }

    @Test
    void disabledSellButtonHasNoCoreActionBinding() throws Exception {
        YamlConfiguration yaml = yaml(resource("guis/sell-gui.yml"));
        int sellSlot = yaml.getInt("buttons.sell.slot");
        yaml.set("buttons.sell.enabled", false);
        var withoutDecoration = PublicGuiMigration.parseDefinition("sell-gui", yaml);
        var emptyFrame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
                Map.of(withoutDecoration.menuId(), withoutDecoration)),
                withoutDecoration.menuId(), PublicGuiRenderInput.empty());
        assertFalse(emptyFrame.bindings().containsKey(sellSlot));
        assertEquals("filler", emptyFrame.visuals().get(sellSlot).path());

        yaml.set("decorations.owner.slots", List.of(sellSlot));
        yaml.set("decorations.owner.material", "BOOK");
        yaml.set("decorations.owner.name", "Eier ✓");
        var definition = PublicGuiMigration.parseDefinition("sell-gui", yaml);
        var snapshot = new GuiSnapshot<>(1, Map.of(definition.menuId(), definition));
        var frame = PublicGuiPlanner.plan(snapshot, definition.menuId(), PublicGuiRenderInput.empty());
        assertFalse(frame.bindings().containsKey(sellSlot));
        assertEquals("decorations.owner", frame.visuals().get(sellSlot).path());
        assertEquals("Eier ✓", frame.visuals().get(sellSlot).fixed().name());
    }

    @Test
    void proposedGuiLayoutRejectsOrphanedShopSlotBeforeCommit(@TempDir Path root) throws Exception {
        Path guiDir = root.resolve("guis");
        Path shopDir = root.resolve("shops");
        Files.createDirectories(shopDir);
        YamlConfiguration shop = yaml(resource("shops/blocks.yml"));
        shop.set("items.stone.slot", 53);
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir,
                Map.of(guiDir.resolve("main.yml"), resource("guis/main.yml"),
                        guiDir.resolve("shop-section.yml"), resource("guis/shop-section.yml")), guiDir));
    }

    @Test
    void migrationPreflightRejectsMalformedSectionPlacementAndVisibility(@TempDir Path root) throws Exception {
        Path guiDir = root.resolve("guis");
        Path shopDir = root.resolve("shops");
        Files.createDirectories(shopDir);
        YamlConfiguration shop = yaml(resource("shops/blocks.yml"));
        Map<Path, byte[]> proposed = Map.of(guiDir.resolve("main.yml"), resource("guis/main.yml"),
                guiDir.resolve("shop-section.yml"), resource("guis/shop-section.yml"));

        shop.set("slot", "10");
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));

        shop.set("slot", 10);
        shop.set("enabled", "false");
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));

        shop.set("enabled", false);
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertDoesNotThrow(() -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
    }

    @Test
    void migrationPreflightRejectsProductPageThatWouldSilentlyBecomePageZero(@TempDir Path root) throws Exception {
        Path guiDir = root.resolve("guis");
        Path shopDir = root.resolve("shops");
        Files.createDirectories(shopDir);
        YamlConfiguration shop = yaml(resource("shops/blocks.yml"));
        Map<Path, byte[]> proposed = Map.of(guiDir.resolve("main.yml"), resource("guis/main.yml"),
                guiDir.resolve("shop-section.yml"), resource("guis/shop-section.yml"));

        for (Object invalid : List.of("1", 1.5, Integer.MAX_VALUE)) {
            shop.set("items.stone.page", invalid);
            Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
            assertThrows(IOException.class,
                    () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        }

        shop.set("items.stone.page", 1);
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertDoesNotThrow(() -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));

        shop.set("items", List.of("stone"));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
    }

    @Test
    void migrationPreflightRejectsMalformedSectionPresentation(@TempDir Path root) throws Exception {
        Path guiDir = root.resolve("guis");
        Path shopDir = root.resolve("shops");
        Files.createDirectories(shopDir);
        YamlConfiguration shop = yaml(resource("shops/blocks.yml"));
        Map<Path, byte[]> proposed = Map.of(guiDir.resolve("main.yml"), resource("guis/main.yml"),
                guiDir.resolve("shop-section.yml"), resource("guis/shop-section.yml"));

        shop.set("title", List.of("wrong type"));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        shop.set("title", "&8Blocks");

        shop.set("description", List.of("valid", 3));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        shop.set("description", List.of("valid"));

        shop.set("size", "27");
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        shop.set("size", 27);

        shop.set("icon", List.of("CHEST"));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        shop.set("icon", "STONE");

        shop.set("items.stone.display-name", List.of("wrong type"));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        shop.set("items.stone.display-name", "Stone");

        shop.set("items.stone.lore", List.of("valid", 3));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
        shop.set("items.stone.lore", List.of("valid"));

        shop.set("items.stone.material", List.of("STONE"));
        Files.writeString(shopDir.resolve("blocks.yml"), shop.saveToString());
        assertThrows(IOException.class, () -> PublicGuiMigration.validateShopLayout(shopDir, proposed, guiDir));
    }

    @Test
    void sectionUpgradeUsesLargestOldSectionAndKeepsMovedControl(@TempDir Path root) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("small.yml"), "id: small\nsize: 27\n");
        Files.writeString(root.resolve("large.yml"), "id: large\nsize: 54\n");
        assertEquals(6, PublicGuiMigration.largestSectionRows(root));
        byte[] sized = PublicGuiMigration.sizedSectionDefault(resource("guis/shop-section.yml"), 6);
        YamlConfiguration old = yaml(resource("legacy-guis/v1/shop-section.yml"));
        old.set("buttons.back.slot", 48);
        YamlConfiguration result = yaml(PublicGuiMigration.migrateGui("shop-section", bytes(old),
                resource("legacy-guis/v1/shop-section.yml"), sized,
                new YamlConfiguration(), 1, null));
        assertEquals(6, result.getInt("rows"));
        assertEquals(48, result.getInt("buttons.back.slot"));
        assertFalse(result.getIntegerList("entries.products.slots").contains(48));
        assertTrue(result.getIntegerList("entries.products.slots").contains(49));
        PublicGuiMigration.validate("shop-section", result);
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream input = PublicGuiMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return input.readAllBytes();
        }
    }

    private static YamlConfiguration yaml(byte[] bytes) throws Exception {
        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        return result;
    }

    private static byte[] bytes(YamlConfiguration yaml) {
        return yaml.saveToString().getBytes(StandardCharsets.UTF_8);
    }
}
