package me.foesio.foKits.config;

import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiStartupFallback;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One-time conversion from released config/GUI presentation into generated Core menus. */
public final class PublicKitGuiMigration {
    private static final String MAIN = "guis/player-kits.yml";
    private static final String PREVIEW = "guis/kit-preview.yml";
    private static final String MARKER = ".public-gui-schema-version";
    static final String OLD_GUI_DEFAULTS = """
            title: '&8ᴋɪᴛ ѕᴇʟᴇᴄᴛᴏʀ'
            kit-entry: {name: '{material_sprite}{theme}{kit}'}
            filler: {material: GRAY_STAINED_GLASS_PANE, name: ' ', lore: []}
            empty-kit-slot: {material: LIGHT_GRAY_STAINED_GLASS_PANE, name: ' ', lore: []}
            states:
              available:
                lore: ['{white}Left click to claim this kit.', '{white}Right click to preview its contents.', '', ':lime_dye: {muted}Status: {good}Ready']
              cooldown:
                lore: ['{white}Left click to try claiming.', '{white}Right click to preview its contents.', '', ':clock: {muted}Next claim in: {theme}{time_remaining}']
              no-permission:
                lore: ['{white}Left click to try claiming.', '{white}Right click to preview its contents.', '', ':red_dye: {muted}Status: {bad}Missing permission']
              world-blocked:
                lore: ['{white}Left click to try claiming.', '{white}Right click to preview its contents.', '', ':barrier: {muted}Status: {bad}Unavailable in {world}']
              claimed-once:
                lore: ['{white}Left click to try claiming.', '{white}Right click to preview its contents.', '', ':red_dye: {muted}Status: {bad}Already claimed']
              disabled:
                lore: [':barrier: {white}This kit is currently disabled.']
            items:
              no-kits: {slot: 13, material: BARRIER, name: ':barrier: &cNo Kits Available', lore: ['&fThere are no kits to display.']}
            buttons:
              previous-page: {slot: 9, material: ARROW, name: ':arrow: &e&lPREVIOUS PAGE', lore: ['&8ʙᴜᴛᴛᴏɴ', ' ', '&eⓘ Information ↓', '&7&l | &fPage {page} of {pages}.', ' ', '&a→ Click to go to page {target_page} ←']}
              next-page: {slot: 17, material: ARROW, name: ':arrow: &e&lNEXT PAGE', lore: ['&8ʙᴜᴛᴛᴏɴ', ' ', '&eⓘ Information ↓', '&7&l | &fPage {page} of {pages}.', ' ', '&a→ Click to go to page {target_page} ←']}
            preview:
              title: '&8ᴋɪᴛ ᴘʀᴇᴠɪᴇᴡ &8- {kit}'
              filler: {material: GRAY_STAINED_GLASS_PANE, name: ' ', lore: []}
              buttons:
                back: {slot: 8, material: IRON_DOOR, name: ':iron_door: &e&lBACK', lore: ['&8ʙᴜᴛᴛᴏɴ', ' ', '&eⓘ Information ↓', '&7&l | &fReturn to the previous menu.', ' ', '&a→ Click to go back ←']}
            """;

    private PublicKitGuiMigration() {
    }

    public static boolean install(JavaPlugin plugin, boolean fresh, int originalConfigVersion) {
        Path root = plugin.getDataFolder().toPath();
        Path marker = root.resolve(MARKER);
        try {
            if (Files.exists(marker)) {
                if (!Files.isRegularFile(marker)) {
                    throw new IOException("public GUI schema marker is not a regular file: " + marker);
                }
                if (Files.readString(marker).trim().equals("1")) {
                    return true;
                }
                throw new IOException("unsupported public GUI schema marker: " + marker);
            }
            if (fresh) {
                Map<Path, byte[]> originals = new LinkedHashMap<>();
                Map<Path, byte[]> defaults = new LinkedHashMap<>();
                for (String resource : List.of(MAIN, PREVIEW, "messages.yml", "sounds.yml")) {
                    Path installed = root.resolve(resource);
                    originals.put(installed, null);
                    defaults.put(installed, bundledBytes(plugin, resource));
                }
                commit(root, marker, 0, originals, defaults);
                return true;
            }
            if (originalConfigVersion != 7 && originalConfigVersion != 9) {
                throw new IOException("config version " + originalConfigVersion
                        + " has no verified public GUI migration baseline (supported: 7, 9)");
            }
            return migrateOlder(plugin, root, marker, originalConfigVersion);
        } catch (Exception error) {
            plugin.getLogger().severe("FoKits public GUI conversion is pending; installed files were kept: "
                    + error.getMessage());
            return false;
        }
    }

    private static boolean migrateOlder(JavaPlugin plugin, Path root, Path marker, int originalVersion)
            throws Exception {
        Path configFile = root.resolve("config.yml");
        Path mainFile = root.resolve(MAIN);
        Path previewFile = root.resolve(PREVIEW);
        Path messagesFile = root.resolve("messages.yml");
        Path soundsFile = root.resolve("sounds.yml");
        byte[] configBytes = Files.readAllBytes(configFile);
        byte[] mainBytes = Files.isRegularFile(mainFile) ? Files.readAllBytes(mainFile) : null;
        byte[] previewBytes = Files.isRegularFile(previewFile) ? Files.readAllBytes(previewFile) : null;
        YamlConfiguration oldConfig = parse(configBytes);
        YamlConfiguration oldMain = mainBytes == null ? null : parse(mainBytes);
        if (previewBytes != null) {
            throw new IOException("existing kit-preview.yml has no known older schema; preserving it for review");
        }
        if (oldMain != null && oldMain.isConfigurationSection("entries.kits")) {
            plugin.getLogger().warning("Core-format player-kits.yml has no schema marker; preserving it as-is."
                    + " Add the marker only after checking this installation's schema history.");
            return false;
        }
        if (oldMain == null && !oldConfig.isConfigurationSection("player-gui")) {
            throw new IOException("no trustworthy older GUI source; a removed owner file was not recreated");
        }
        YamlConfiguration main = bundled(plugin, MAIN);
        YamlConfiguration preview = bundled(plugin, PREVIEW);
        YamlConfiguration mainDefaults = bundled(plugin, MAIN);
        YamlConfiguration previewDefaults = bundled(plugin, PREVIEW);
        YamlConfiguration oldDefaults;
        YamlConfiguration oldConfigDefaults;
        if (originalVersion == 9) {
            oldDefaults = bundled(plugin, "migrations/player-kits-v9.yml");
            oldConfigDefaults = bundled(plugin, "migrations/config-v9.yml");
        } else {
            oldDefaults = new YamlConfiguration();
            oldDefaults.loadFromString(OLD_GUI_DEFAULTS);
            oldConfigDefaults = v7Defaults();
        }
        overlayConfig(plugin, oldConfig, oldConfigDefaults, main, oldMain == null);
        if (oldMain != null) {
            overlayOldGui(plugin, oldMain, oldDefaults, main, preview, originalVersion < 9);
        }
        moveStaticItems(plugin, oldConfig, main);
        validateWithNewestDefaults(plugin, MAIN, main, mainDefaults, mainContract());
        validateWithNewestDefaults(plugin, PREVIEW, preview, previewDefaults, previewContract());
        // Keep only the serialized source items needed by the admin layout editor.
        ConfigurationSection staticItems = oldConfig.getConfigurationSection("player-gui.layout.static-items");
        Map<Integer, Object> retainedItems = new LinkedHashMap<>();
        if (staticItems != null) {
            for (String key : staticItems.getKeys(false)) {
                if (key.matches("\\d+") && fitsInt(key)
                        && main.getIntegerList("entries.static-items.slots").contains(Integer.parseInt(key))) {
                    retainedItems.put(Integer.parseInt(key), staticItems.get(key));
                }
            }
        }
        oldConfig.set("player-gui", null);
        retainedItems.forEach((slot, item) ->
                oldConfig.set("player-gui.layout.static-items." + slot, item));
        Map<Path, byte[]> original = new LinkedHashMap<>();
        original.put(configFile, configBytes);
        original.put(mainFile, mainBytes);
        original.put(previewFile, previewBytes);
        Map<Path, byte[]> replacement = new LinkedHashMap<>();
        replacement.put(configFile, oldConfig.saveToString().getBytes(StandardCharsets.UTF_8));
        replacement.put(mainFile, main.saveToString().getBytes(StandardCharsets.UTF_8));
        replacement.put(previewFile, preview.saveToString().getBytes(StandardCharsets.UTF_8));
        if (originalVersion == 7 && !Files.exists(messagesFile)) {
            YamlConfiguration messages = bundled(plugin, "messages.yml");
            migrateV7Messages(plugin, oldConfig, bundled(plugin, "migrations/config-v7.yml"), messages);
            original.put(messagesFile, null);
            replacement.put(messagesFile, messages.saveToString().getBytes(StandardCharsets.UTF_8));
        }
        if (originalVersion == 7 && !Files.exists(soundsFile)) {
            original.put(soundsFile, null);
            replacement.put(soundsFile, bundled(plugin, "sounds.yml").saveToString().getBytes(StandardCharsets.UTF_8));
        }
        commit(root, marker, originalVersion, original, replacement);
        return true;
    }

    static void migrateV7Messages(JavaPlugin plugin, YamlConfiguration installed,
                                  YamlConfiguration oldDefaults, YamlConfiguration latest) {
        copyLegacyMessage(plugin, installed, oldDefaults, latest, "prefix", "tokens.prefix");
        for (String color : List.of("theme", "muted", "white", "good", "bad")) {
            copyLegacyMessage(plugin, installed, oldDefaults, latest,
                    "colors." + color, "tokens." + color);
        }
        ConfigurationSection priorMessages = oldDefaults.getConfigurationSection("messages");
        if (priorMessages == null) {
            throw new IllegalStateException("Missing released v7 message baseline");
        }
        for (String key : priorMessages.getKeys(false)) {
            if (latest.contains(key, true)) {
                copyLegacyMessage(plugin, installed, oldDefaults, latest, "messages." + key, key);
            }
        }
    }

    private static void copyLegacyMessage(JavaPlugin plugin, YamlConfiguration installed,
                                          YamlConfiguration oldDefaults, YamlConfiguration latest,
                                          String source, String target) {
        if (!installed.contains(source, true)) {
            latest.set(target, null);
            return;
        }
        Object value = installed.get(source);
        if (!(value instanceof String text)) {
            if (plugin != null) {
                plugin.getLogger().warning("Invalid legacy message " + source + "=" + value
                        + "; using newest bundled default " + latest.get(target));
            }
            return;
        }
        if (!Objects.equals(text, oldDefaults.getString(source))) {
            latest.set(target, text);
        }
    }

    static void overlayConfig(JavaPlugin plugin, YamlConfiguration old, YamlConfiguration main) {
        overlayConfig(plugin, old, main, true);
    }

    static void overlayConfig(JavaPlugin plugin, YamlConfiguration old, YamlConfiguration main,
                              boolean configOwnsPresentation) {
        overlayConfig(plugin, old, v7Defaults(), main, configOwnsPresentation);
    }

    static void overlayConfig(JavaPlugin plugin, YamlConfiguration old, YamlConfiguration priorDefaults,
                              YamlConfiguration main, boolean configOwnsPresentation) {
        String root = "player-gui.";
        copyChanged(plugin, old, root + "title", priorDefaults.get(root + "title"), main, "title");
        List<Integer> oldSlots = old.getIntegerList(root + "layout.kit-slots");
        boolean legacyFull = oldSlots.equals(java.util.stream.IntStream.range(0, 54).boxed().toList());
        copyChanged(plugin, old, root + "rows", priorDefaults.get(root + "rows"), main, "rows");
        copyChanged(plugin, old, root + "fill-background", priorDefaults.get(root + "fill-background"),
                main, "filler.enabled");
        copyChanged(plugin, old, root + "filler-material", priorDefaults.get(root + "filler-material"),
                main, "filler.material");
        if (old.contains(root + "layout.kit-slots", true) && !legacyFull
                && !Objects.equals(oldSlots, List.of(11, 12, 13, 14, 15))) {
            main.set("entries.kits.slots", old.get(root + "layout.kit-slots"));
        }
        for (String state : List.of("available", "cooldown", "no-permission",
                "world-blocked", "claimed-once", "disabled")) {
            String sourceState = state.equals("world-blocked") ? "no-permission" : state;
            String path = root + "lore." + sourceState;
            String target = "entries.kits.source-item.lore-states." + state;
            if (configOwnsPresentation) {
                main.set(target + ".mode", "replace");
            }
            if (old.contains(path, true)) {
                copyChanged(plugin, old, path, priorDefaults.get(path), main, target + ".lines");
            } else if (configOwnsPresentation && old.isConfigurationSection(root)) {
                main.set(target + ".lines", List.of());
            }
        }
    }

    static void overlayOldGui(JavaPlugin plugin, YamlConfiguration old, YamlConfiguration defaults,
                                      YamlConfiguration main, YamlConfiguration preview) {
        overlayOldGui(plugin, old, defaults, main, preview, true);
    }

    static void overlayOldGui(JavaPlugin plugin, YamlConfiguration old, YamlConfiguration defaults,
                              YamlConfiguration main, YamlConfiguration preview,
                              boolean legacyUnspritedFallback) {
        copyChanged(plugin, old, "title", defaults.get("title"), main, "title");
        if (old.contains("kit-entry.name", true)) {
            copyChanged(plugin, old, "kit-entry.name", defaults.get("kit-entry.name"), main,
                    "entries.kits.source-item.default-name");
        } else if (defaults.contains("kit-entry.name", true)) {
            main.set("entries.kits.source-item.default-name", null);
        }
        for (String field : List.of("material", "name", "lore", "amount", "custom-model-data", "glow", "item-flags", "skull-skin-url")) {
            copyChanged(plugin, old, "filler." + field, defaults.get("filler." + field), main, "filler." + field);
            copyChanged(plugin, old, "empty-kit-slot." + field, defaults.get("empty-kit-slot." + field),
                    main, "entries.kits.empty-item." + field);
            copyChanged(plugin, old, "preview.filler." + field, defaults.get("preview.filler." + field),
                    preview, "filler." + field);
        }
        for (String state : List.of("available", "cooldown", "no-permission", "world-blocked", "claimed-once", "disabled")) {
            String source = "states." + state + ".lore";
            String dest = "entries.kits.source-item.lore-states." + state;
            if (old.contains(source, true)) {
                main.set(dest + ".mode", "append");
                Object value = old.get(source);
                if (!Objects.deepEquals(value, defaults.get(source))
                        && (!legacyUnspritedFallback || !Objects.deepEquals(value,
                        legacyUnspritedDefault(state)))) {
                    main.set(dest + ".lines", value);
                }
            } else if (defaults.contains(source, true)) {
                main.set(dest + ".mode", "preserve");
                main.set(dest + ".lines", null);
            }
        }
        for (String button : List.of("previous-page", "next-page")) {
            String source = "buttons." + button;
            String dest = "buttons." + button;
            if (!old.isConfigurationSection(source)) {
                if (!defaults.isConfigurationSection(source)) {
                    continue;
                }
                main.set(dest + ".enabled", false);
                continue;
            }
            copyItem(plugin, old, defaults, source, main, dest);
            List<String> lore = main.getStringList(dest + ".lore");
            if (lore.stream().anyMatch(line -> line.contains("{target_page}"))) {
                String replacement = button.equals("previous-page") ? "{previous_page}" : "{next_page}";
                main.set(dest + ".lore", lore.stream()
                        .map(line -> line.replace("{target_page}", replacement)).toList());
            }
            copyChanged(plugin, old, source + ".slot", defaults.get(source + ".slot"), main, dest + ".slot");
        }
        copyChanged(plugin, old, "preview.title", defaults.get("preview.title"), preview, "title");
        if (!old.isConfigurationSection("preview.buttons.back")) {
            if (defaults.isConfigurationSection("preview.buttons.back")) {
                preview.set("buttons.back.enabled", false);
            }
        } else {
            copyItem(plugin, old, defaults, "preview.buttons.back", preview, "buttons.back");
            copyChanged(plugin, old, "preview.buttons.back.slot", defaults.get("preview.buttons.back.slot"),
                    preview, "buttons.back.slot");
        }
    }

    private static Object legacyUnspritedDefault(String state) {
        return v7Defaults().get("player-gui.lore." + state);
    }

    private static YamlConfiguration v7Defaults() {
        try (InputStream stream = PublicKitGuiMigration.class.getClassLoader()
                .getResourceAsStream("migrations/config-v7.yml")) {
            if (stream == null) {
                throw new IllegalStateException("Missing released v7 config baseline");
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return defaults;
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid released v7 config baseline", failure);
        }
    }

    private static void copyItem(JavaPlugin plugin, YamlConfiguration old, YamlConfiguration defaults,
                                 String source, YamlConfiguration target, String destination) {
        for (String field : List.of("material", "name", "lore", "amount", "custom-model-data", "glow", "item-flags", "skull-skin-url")) {
            if (field.equals("lore") && old.isConfigurationSection(source)
                    && defaults.contains(source + ".lore", true)
                    && !old.contains(source + ".lore", true)) {
                target.set(destination + ".lore", null);
                continue;
            }
            copyChanged(plugin, old, source + "." + field, defaults.get(source + "." + field),
                    target, destination + "." + field);
        }
    }

    private static void copyChanged(JavaPlugin plugin, YamlConfiguration old, String source, Object oldDefault,
                                    YamlConfiguration target, String destination) {
        if (!old.contains(source, true)) {
            return;
        }
        Object value = old.get(source);
        if (value instanceof String text && text.isEmpty()
                && source.endsWith("custom-model-data")) {
            return; // legacy blank meant absent model data
        }
        if (Objects.deepEquals(value, oldDefault)) {
            return;
        }
        target.set(destination, value);
    }

    private static void moveStaticItems(JavaPlugin plugin, YamlConfiguration oldConfig, YamlConfiguration main) {
        ConfigurationSection staticItems = oldConfig.getConfigurationSection("player-gui.layout.static-items");
        if (staticItems == null) {
            return;
        }
        List<Integer> slots = new ArrayList<>();
        Set<Integer> occupied = new java.util.HashSet<>(main.getIntegerList("entries.kits.slots"));
        if (main.getBoolean("buttons.previous-page.enabled", true)) {
            occupied.add(main.getInt("buttons.previous-page.slot"));
        }
        if (main.getBoolean("buttons.next-page.enabled", true)) {
            occupied.add(main.getInt("buttons.next-page.slot"));
        }
        for (String key : staticItems.getKeys(false)) {
            if (!key.matches("\\d+")) {
                plugin.getLogger().warning("Invalid legacy static item slot " + key + "; newest default has no item there.");
                continue;
            }
            if (!fitsInt(key)) {
                plugin.getLogger().warning("Invalid legacy static item slot " + key + "; newest default has no item there.");
                continue;
            }
            int slot = Integer.parseInt(key);
            if (slot < 0 || slot >= main.getInt("rows") * 9
                    || staticItems.getItemStack(key) == null) {
                plugin.getLogger().warning("Invalid legacy static item at slot " + key
                        + "; newest default has no item there.");
                continue;
            }
            if (occupied.contains(slot)) {
                throw new IllegalStateException("Legacy static item at slot " + slot
                        + " conflicts with the new kit/button/empty region; owner files were kept for review");
            }
            slots.add(slot);
        }
        slots.sort(Comparator.naturalOrder());
        if (!slots.isEmpty()) {
            main.set("entries.static-items.slots", slots);
            main.createSection("entries.static-items.source-item");
        }
    }

    private static boolean fitsInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static void validateWithNewestDefaults(JavaPlugin plugin, String resource,
                                                    YamlConfiguration candidate, YamlConfiguration bundled,
                                                    PublicGuiContract contract) throws Exception {
        PublicGuiStartupFallback.parse(candidate, bundled,
                yaml -> {
                    PublicGuiDefinition definition = PublicGuiSchema.parse(resource, resource, yaml, contract);
                    if (resource.equals(MAIN)) {
                        PublicGuiDefinition.Entry kits = definition.entries().get("kits");
                        for (String state : List.of("available", "cooldown", "no-permission",
                                "world-blocked", "claimed-once", "disabled")) {
                            if (kits == null || kits.sourceItem() == null
                                    || !kits.sourceItem().loreStates().containsKey(state)) {
                                throw new GuiDefinitionException(
                                        "entries.kits.source-item.lore-states." + state,
                                        "required kit lore state is missing");
                            }
                        }
                    }
                    return definition;
                },
                replacement -> plugin.getLogger().warning("Replacing invalid old public GUI field "
                        + resource + ":" + replacement.path() + "=" + replacement.invalidValue()
                        + " (" + replacement.reason() + ") with newest bundled default "
                        + replacement.defaultValue()));
    }

    static PublicGuiContract mainContract() {
        return new PublicGuiContract(Set.of("kits.page.previous", "kits.page.next", "kits.claim", "kits.preview"),
                Map.of(), Set.of("kit", "kit_key", "material", "material_sprite", "time_remaining",
                        "permission", "world", "page", "pages", "previous_page", "next_page"),
                PublicGuiContract.InteractionMode.CORE);
    }

    static PublicGuiContract previewContract() {
        return new PublicGuiContract(Set.of("kits.preview.back"), Map.of(), Set.of("kit"),
                PublicGuiContract.InteractionMode.CORE);
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String resource) throws Exception {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                throw new IOException("missing bundled GUI " + resource);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static byte[] bundledBytes(JavaPlugin plugin, String resource) throws IOException {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                throw new IOException("missing bundled public resource " + resource);
            }
            return stream.readAllBytes();
        }
    }

    static YamlConfiguration parse(byte[] bytes) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        return yaml;
    }

    static void commit(Path root, Path marker, int originalVersion,
                               Map<Path, byte[]> originals, Map<Path, byte[]> replacements) throws IOException {
        Map<Path, Path> staged = new LinkedHashMap<>();
        byte[] originalMarker = Files.isRegularFile(marker) ? Files.readAllBytes(marker) : null;
        Path stagedMarker = null;
        try {
            for (Map.Entry<Path, byte[]> entry : replacements.entrySet()) {
                Path target = entry.getKey();
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), ".fokits-gui-", ".staged");
                Files.write(temp, entry.getValue());
                staged.put(target, temp);
            }
            for (Map.Entry<Path, byte[]> entry : originals.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                Path target = entry.getKey();
                Path backup = target.resolveSibling(target.getFileName()
                        + ".gui-from-config-v" + originalVersion + "-backup");
                if (!Files.exists(backup)) {
                    Files.write(backup, entry.getValue());
                }
            }
            for (Map.Entry<Path, Path> entry : staged.entrySet()) {
                replace(entry.getValue(), entry.getKey());
            }
            stagedMarker = Files.createTempFile(root, ".fokits-gui-marker-", ".staged");
            Files.writeString(stagedMarker, "1\n", StandardCharsets.UTF_8);
            replace(stagedMarker, marker);
            stagedMarker = null;
        } catch (IOException | RuntimeException failure) {
            for (Map.Entry<Path, byte[]> entry : originals.entrySet()) {
                Path target = entry.getKey();
                byte[] prior = entry.getValue();
                if (prior == null) {
                    if (Files.isRegularFile(target)) {
                        Files.deleteIfExists(target);
                    }
                } else {
                    Path rollback = Files.createTempFile(target.getParent(), ".fokits-rollback-", ".staged");
                    Files.write(rollback, prior);
                    replace(rollback, target);
                }
            }
            if (originalMarker == null) {
                Files.deleteIfExists(marker);
            } else {
                Path rollback = Files.createTempFile(root, ".fokits-marker-rollback-", ".staged");
                Files.write(rollback, originalMarker);
                replace(rollback, marker);
            }
            throw failure;
        } finally {
            for (Path temp : staged.values()) {
                Files.deleteIfExists(temp);
            }
            if (stagedMarker != null) {
                Files.deleteIfExists(stagedMarker);
            }
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
