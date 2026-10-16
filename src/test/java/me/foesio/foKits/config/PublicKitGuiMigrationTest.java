package me.foesio.foKits.config;

import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiStartupFallback;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicKitGuiMigrationTest {
    @TempDir Path temporary;

    @Test
    void releasedTwoPointZeroDefaultsBecomeNewestGeneratedGui() throws Exception {
        YamlConfiguration oldConfig = bundled("migrations/config-v9.yml");
        YamlConfiguration oldGui = bundled("migrations/player-kits-v9.yml");
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration preview = bundled("guis/kit-preview.yml");

        PublicKitGuiMigration.overlayConfig(null, oldConfig, oldConfig, main, false);
        PublicKitGuiMigration.overlayOldGui(null, oldGui, oldGui, main, preview, false);

        assertEquals(3, main.getInt("rows"));
        assertTrue(main.getBoolean("buttons.previous-page.enabled"));
        assertTrue(main.getBoolean("buttons.next-page.enabled"));
        assertEquals("{material_sprite}{theme}{kit}", main.getString("entries.kits.source-item.default-name"));
        assertEquals("append", main.getString("entries.kits.source-item.lore-states.world-blocked.mode"));
        assertEquals("&8ᴋɪᴛ ᴘʀᴇᴠɪᴇᴡ", preview.getString("title"));
        assertEquals("LIGHT_GRAY_STAINED_GLASS_PANE",
                preview.getString("entries.contents.empty-item.material"));
        assertTrue(preview.getBoolean("buttons.back.enabled"));
        PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        PublicGuiSchema.parse("kits.preview", "guis/kit-preview.yml", preview,
                PublicKitGuiMigration.previewContract());
    }

    @Test
    void releasedTwoPointZeroCustomGuiValuesSurviveConversion() throws Exception {
        YamlConfiguration oldConfig = bundled("migrations/config-v9.yml");
        YamlConfiguration oldConfigDefaults = bundled("migrations/config-v9.yml");
        YamlConfiguration oldGui = bundled("migrations/player-kits-v9.yml");
        YamlConfiguration oldGuiDefaults = bundled("migrations/player-kits-v9.yml");
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration preview = bundled("guis/kit-preview.yml");
        oldConfig.set("player-gui.rows", 4);
        oldGui.set("filler.material", "BLUE_STAINED_GLASS_PANE");
        oldGui.set("states.available.lore", List.of("§bPrêt {kit}", ""));
        oldGui.set("preview.title", "§dAperçu");
        oldGui.set("preview.buttons.back.lore", List.of());

        PublicKitGuiMigration.overlayConfig(null, oldConfig, oldConfigDefaults, main, false);
        PublicKitGuiMigration.overlayOldGui(null, oldGui, oldGuiDefaults, main, preview, false);

        assertEquals(4, main.getInt("rows"));
        assertEquals("BLUE_STAINED_GLASS_PANE", main.getString("filler.material"));
        assertEquals(List.of("§bPrêt {kit}", ""),
                main.getStringList("entries.kits.source-item.lore-states.available.lines"));
        assertEquals("§dAperçu", preview.getString("title"));
        assertEquals(List.of(), preview.getStringList("buttons.back.lore"));
        PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        PublicGuiSchema.parse("kits.preview", "guis/kit-preview.yml", preview,
                PublicKitGuiMigration.previewContract());
    }

    @Test
    void previewDefaultsUseLightGrayEmptyEntriesAndFixedSmallCapsTitle() throws Exception {
        YamlConfiguration preview = bundled("guis/kit-preview.yml");
        var definition = PublicGuiSchema.parse("kits.preview", "guis/kit-preview.yml", preview,
                PublicKitGuiMigration.previewContract());
        var snapshot = new GuiSnapshot<>(1, Map.of("kits.preview", definition));
        var frame = PublicGuiPlanner.plan(snapshot, "kits.preview",
                new PublicGuiRenderInput(Map.of("kit", "Starter Kit"), Map.of(), Map.of(), Map.of()));

        assertEquals("&8ᴋɪᴛ ᴘʀᴇᴠɪᴇᴡ", frame.title());
        assertEquals("entries.contents.empty-item", frame.visuals().get(18).path());
        assertEquals(Material.LIGHT_GRAY_STAINED_GLASS_PANE, frame.visuals().get(18).fixed().material());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, frame.visuals().get(0).fixed().material());
        assertFalse(frame.bindings().containsKey(18));
    }

    @Test
    void formerNoticeSlotUsesConfiguredFillerWithNoClickAction() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        main.set("filler.material", "BLUE_STAINED_GLASS_PANE");
        var definition = PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        var snapshot = new GuiSnapshot<>(1, Map.of("kits.main", definition));

        var frame = PublicGuiPlanner.plan(snapshot, "kits.main", PublicGuiRenderInput.empty());
        assertEquals(3, definition.rows());
        assertFalse(definition.entries().containsKey("no-kits"));
        assertEquals("filler", frame.visuals().get(22).path());
        assertEquals(Material.BLUE_STAINED_GLASS_PANE, frame.visuals().get(22).fixed().material());
        assertFalse(frame.bindings().containsKey(22));
    }

    @Test
    void versionSevenConfigCarriesCustomLoreAndKeepsNewDefaultForOldDefault() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration prior = yaml("""
                player-gui:
                  title: '&8ᴋɪᴛ ѕᴇʟᴇᴄᴛᴏʀ'
                  rows: 6
                  layout:
                    kit-slots: [%s]
                  lore:
                    available: ['§bKlaar {kit}', '']
                    cooldown: ['{white}Left click to try claiming.', '{white}Right click to preview its contents.', '', '{muted}Next claim in: {theme}{time_remaining}']
                """.formatted(java.util.stream.IntStream.range(0, 54)
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(", "))));

        PublicKitGuiMigration.overlayConfig(null, prior, main);

        assertEquals(3, main.getInt("rows"));
        assertEquals(List.of("§bKlaar {kit}", ""),
                main.getStringList("entries.kits.source-item.lore-states.available.lines"));
        assertTrue(main.getStringList("entries.kits.source-item.lore-states.cooldown.lines")
                .getLast().contains(":clock:"));
        PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
    }

    @Test
    void releasedV7LoreReplacesIconLoreAndWorldBlockUsesTranslatedPermissionLore() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration prior = yaml("""
                player-gui:
                  lore:
                    available: ['§aPrêt ✓']
                    no-permission: ['§cGeen toegang ✓']
                """);

        PublicKitGuiMigration.overlayConfig(null, prior, main);
        var parsed = PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        var states = parsed.entries().get("kits").sourceItem().loreStates();
        assertEquals(me.foesio.core.gui.publicgui.PublicGuiDefinition.EditMode.REPLACE,
                states.get("available").mode());
        assertEquals(List.of("§cGeen toegang ✓"), states.get("no-permission").lines());
        assertEquals(List.of("§cGeen toegang ✓"), states.get("world-blocked").lines());
        assertEquals(me.foesio.core.gui.publicgui.PublicGuiDefinition.EditMode.REPLACE,
                states.get("world-blocked").mode());
    }

    @Test
    void releasedV7RemovedLoreStillClearsOriginalIconLore() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration prior = yaml("player-gui: {lore: {available: []}}\n");

        PublicKitGuiMigration.overlayConfig(null, prior, main);
        var parsed = PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        var states = parsed.entries().get("kits").sourceItem().loreStates();
        assertEquals(me.foesio.core.gui.publicgui.PublicGuiDefinition.EditMode.REPLACE,
                states.get("available").mode());
        assertEquals(List.of(), states.get("available").lines());
        assertEquals(List.of(), states.get("world-blocked").lines());
    }

    @Test
    void releasedV7CustomThreeRowSelectorKeepsItsHeight() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration prior = yaml("player-gui: {rows: 3}\n");

        PublicKitGuiMigration.overlayConfig(null, prior, main);

        assertEquals(3, main.getInt("rows"));
        PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
    }

    @Test
    void preCoreGuiDefaultsDoNotDiscardCustomConfigPresentation() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration prior = yaml("""
                player-gui:
                  title: 'Stale config title'
                  filler-material: GOLD_BLOCK
                  lore: {available: ['Stale config lore']}
                """);

        PublicKitGuiMigration.overlayConfig(null, prior, main, false);
        PublicKitGuiMigration.overlayOldGui(null,
                yaml(PublicKitGuiMigration.OLD_GUI_DEFAULTS),
                yaml(PublicKitGuiMigration.OLD_GUI_DEFAULTS), main,
                bundled("guis/kit-preview.yml"));

        assertEquals("Stale config title", main.getString("title"));
        assertEquals("GOLD_BLOCK", main.getString("filler.material"));
        assertEquals(List.of("Stale config lore"),
                main.getStringList("entries.kits.source-item.lore-states.available.lines"));
        assertEquals("append", main.getString("entries.kits.source-item.lore-states.available.mode"));
    }

    @Test
    void olderGuiPreservesRemovedLoreAndMapsMovedButtonPlaceholder() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration preview = bundled("guis/kit-preview.yml");
        YamlConfiguration old = yaml("""
                states:
                  available:
                    lore: []
                buttons:
                  previous-page:
                    slot: 10
                    material: ARROW
                    name: 'Vorige'
                    lore: ['Pagina {target_page}']
                preview:
                  buttons:
                    back:
                      slot: 7
                """);
        YamlConfiguration priorDefaults = yaml("""
                kit-entry:
                  name: '{material_sprite}{theme}{kit}'
                states:
                  available:
                    lore: ['Ready']
                  cooldown:
                    lore: ['Wait']
                buttons:
                  previous-page:
                    slot: 9
                    material: ARROW
                    name: 'Previous'
                    lore: ['Page {target_page}']
                  next-page:
                    slot: 17
                    material: ARROW
                    name: 'Next'
                    lore: ['Page {target_page}']
                preview:
                  buttons:
                    back:
                      slot: 8
                """);

        PublicKitGuiMigration.overlayOldGui(null, old, priorDefaults, main, preview);

        assertEquals(List.of(), main.getStringList("entries.kits.source-item.lore-states.available.lines"));
        assertEquals(10, main.getInt("buttons.previous-page.slot"));
        assertEquals(List.of("Pagina {previous_page}"), main.getStringList("buttons.previous-page.lore"));
        assertFalse(main.getBoolean("buttons.next-page.enabled"));
        assertFalse(main.contains("entries.no-kits", true));
        assertFalse(main.contains("entries.kits.source-item.default-name", true));
        assertEquals("preserve", main.getString("entries.kits.source-item.lore-states.cooldown.mode"));
        assertEquals(7, preview.getInt("buttons.back.slot"));
        PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        PublicGuiSchema.parse("kits.preview", "guis/kit-preview.yml", preview,
                PublicKitGuiMigration.previewContract());
    }

    @Test
    void unchangedPreCoreGuiDefaultsBecomeNewestGeneratedStyle() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration preview = bundled("guis/kit-preview.yml");
        YamlConfiguration newestMain = bundled("guis/player-kits.yml");
        YamlConfiguration newestPreview = bundled("guis/kit-preview.yml");
        YamlConfiguration old = yaml(PublicKitGuiMigration.OLD_GUI_DEFAULTS);

        PublicKitGuiMigration.overlayOldGui(null, old, old, main, preview);

        assertEquals(newestMain.getString("entries.kits.source-item.default-name"),
                main.getString("entries.kits.source-item.default-name"));
        assertEquals(newestMain.getStringList("entries.kits.source-item.lore-states.available.lines"),
                main.getStringList("entries.kits.source-item.lore-states.available.lines"));
        assertEquals(newestMain.getStringList("buttons.previous-page.lore"),
                main.getStringList("buttons.previous-page.lore"));
        assertEquals(newestPreview.getStringList("buttons.back.lore"),
                preview.getStringList("buttons.back.lore"));
    }

    @Test
    void invalidOldFieldUsesOnlyMatchingNewestDefault() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        YamlConfiguration defaults = bundled("guis/player-kits.yml");
        main.set("title", "§bEigen titel");
        main.set("filler.material", "NOT_A_MATERIAL");

        var replacements = new java.util.ArrayList<PublicGuiStartupFallback.Replacement>();
        PublicGuiStartupFallback.parse(main, defaults,
                yaml -> PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", yaml,
                        PublicKitGuiMigration.mainContract()), replacements::add);

        assertEquals("§bEigen titel", main.getString("title"));
        assertEquals(defaults.getString("filler.material"), main.getString("filler.material"));
        assertEquals(1, replacements.size());
        assertEquals("filler.material", replacements.getFirst().path());
    }

    @Test
    void staticSourceEntryHasAValidCoreShape() throws Exception {
        YamlConfiguration main = bundled("guis/player-kits.yml");
        main.set("entries.static-items.slots", List.of(20));
        main.createSection("entries.static-items.source-item");
        var parsed = PublicGuiSchema.parse("kits.main", "guis/player-kits.yml", main,
                PublicKitGuiMigration.mainContract());
        assertEquals(List.of(20), parsed.entries().get("static-items").slots());
    }

    @Test
    void versionSevenMessagesCarryOnlyOwnerChangesAndRemovals() throws Exception {
        YamlConfiguration oldDefaults = bundled("migrations/config-v7.yml");
        YamlConfiguration installed = yaml(oldDefaults.saveToString());
        installed.set("messages.claim-success", "§aKit klaar: {kit}");
        installed.set("messages.claim-cooldown", null);
        installed.set("messages.claim-disabled", List.of("invalid list type"));
        YamlConfiguration latest = bundled("messages.yml");
        String newestUnchanged = latest.getString("no-permission");
        String newestPrefix = latest.getString("tokens.prefix");
        String newestInvalid = latest.getString("claim-disabled");

        PublicKitGuiMigration.migrateV7Messages(null, installed, oldDefaults, latest);

        assertEquals("§aKit klaar: {kit}", latest.getString("claim-success"));
        assertFalse(latest.contains("claim-cooldown", true));
        assertEquals(newestUnchanged, latest.getString("no-permission"));
        assertEquals(newestPrefix, latest.getString("tokens.prefix"));
        assertEquals(newestInvalid, latest.getString("claim-disabled"));
    }

    @Test
    void backupHasOriginalBytesAndIsNeverReplaced() throws Exception {
        Path config = temporary.resolve("config.yml");
        Path gui = temporary.resolve("guis/player-kits.yml");
        Path marker = temporary.resolve(".public-gui-schema-version");
        Files.createDirectories(gui.getParent());
        byte[] originalConfig = "config-version: 7\n# eigenaar\n".getBytes(StandardCharsets.UTF_8);
        byte[] originalGui = "title: Oud\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, originalConfig);
        Files.write(gui, originalGui);
        Map<Path, byte[]> original = new LinkedHashMap<>();
        original.put(config, originalConfig);
        original.put(gui, originalGui);
        PublicKitGuiMigration.commit(temporary, marker, 7, original,
                Map.of(config, "config-version: 7\n".getBytes(StandardCharsets.UTF_8),
                        gui, "title: Nieuw\n".getBytes(StandardCharsets.UTF_8)));

        Path backup = gui.resolveSibling("player-kits.yml.gui-from-config-v7-backup");
        assertArrayEquals(originalGui, Files.readAllBytes(backup));
        assertEquals("title: Nieuw\n", Files.readString(gui));
        assertEquals("1\n", Files.readString(marker));
        Files.writeString(backup, "retained backup");
        PublicKitGuiMigration.commit(temporary, marker, 7, original,
                Map.of(config, "config-version: 7\n".getBytes(StandardCharsets.UTF_8),
                        gui, "title: Again\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("retained backup", Files.readString(backup));
    }

    @Test
    void freshResourceSetHasNoSyntheticLegacyBackup() throws Exception {
        Path marker = temporary.resolve(".public-gui-schema-version");
        Path main = temporary.resolve("guis/player-kits.yml");
        Path preview = temporary.resolve("guis/kit-preview.yml");
        Path messages = temporary.resolve("messages.yml");
        Path sounds = temporary.resolve("sounds.yml");
        Map<Path, byte[]> absent = new LinkedHashMap<>();
        Map<Path, byte[]> defaults = new LinkedHashMap<>();
        for (Path path : List.of(main, preview, messages, sounds)) {
            absent.put(path, null);
            defaults.put(path, path.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        }

        PublicKitGuiMigration.commit(temporary, marker, 0, absent, defaults);

        for (Path path : defaults.keySet()) {
            assertArrayEquals(defaults.get(path), Files.readAllBytes(path));
            assertFalse(Files.exists(path.resolveSibling(path.getFileName()
                    + ".gui-from-config-v0-backup")));
        }
        assertEquals("1\n", Files.readString(marker));
    }

    @Test
    void partialReplacementRestoresOriginalActiveBytes() throws Exception {
        Path config = temporary.resolve("config.yml");
        Path obstructed = temporary.resolve("guis/player-kits.yml");
        Path marker = temporary.resolve(".public-gui-schema-version");
        byte[] original = "config-version: 7\n# owner\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, original);
        Files.createDirectories(obstructed);
        Files.writeString(obstructed.resolve("keep.txt"), "owner data");
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        originals.put(config, original);
        originals.put(obstructed, null);
        Map<Path, byte[]> proposed = new LinkedHashMap<>();
        proposed.put(config, "config-version: 13\n".getBytes(StandardCharsets.UTF_8));
        proposed.put(obstructed, "title: New\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> PublicKitGuiMigration.commit(
                temporary, marker, 7, originals, proposed));

        assertArrayEquals(original, Files.readAllBytes(config));
        assertEquals("owner data", Files.readString(obstructed.resolve("keep.txt")));
        assertFalse(Files.exists(marker));
        assertArrayEquals(original, Files.readAllBytes(config.resolveSibling(
                "config.yml.gui-from-config-v7-backup")));
    }

    @Test
    void unparseableOldYamlIsRejectedBeforeAnyReplacement() throws Exception {
        Path installed = temporary.resolve("player-kits.yml");
        byte[] broken = "title: [unterminated\n".getBytes(StandardCharsets.UTF_8);
        Files.write(installed, broken);
        assertThrows(Exception.class, () -> PublicKitGuiMigration.parse(Files.readAllBytes(installed)));
        assertArrayEquals(broken, Files.readAllBytes(installed));
    }

    private static YamlConfiguration yaml(String source) throws Exception {
        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(source);
        return result;
    }

    private static YamlConfiguration bundled(String path) throws Exception {
        try (InputStream input = PublicKitGuiMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            YamlConfiguration result = new YamlConfiguration();
            result.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return result;
        }
    }
}
