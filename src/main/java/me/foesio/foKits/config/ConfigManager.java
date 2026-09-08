package me.foesio.foKits.config;

import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.gui.GuiResourceLoader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ConfigManager {
    public static final int LATEST_CONFIG_VERSION = 9;
    private static final java.util.List<Integer> DEFAULT_PLAYER_GUI_KIT_SLOTS = java.util.List.of(11, 12, 13, 14, 15);
    private static final java.util.List<Integer> LEGACY_FULL_PLAYER_GUI_KIT_SLOTS =
            java.util.stream.IntStream.range(0, 54).boxed().toList();

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.saveDefaultConfig();
        migrateAndSave();
    }

    public void reload() {
        plugin.reloadConfig();
        migrateAndSave();
    }

    public int currentVersion() {
        return plugin.getConfig().getInt("config-version", 0);
    }

    private void migrateAndSave() {
        FileConfiguration config = plugin.getConfig();
        int before = config.getInt("config-version", 0);
        boolean hadRows = config.contains("player-gui.rows");
        boolean hadKitSlots = config.contains("player-gui.layout.kit-slots");

        config.options().copyDefaults(true);
        NativeDialogConfigDefaults.addDefaults(config);

        if (before < 3) {
            if (!config.contains("player-gui.layout.kit-slots")) {
                config.set("player-gui.layout.kit-slots", DEFAULT_PLAYER_GUI_KIT_SLOTS);
            }
            if (!config.contains("player-gui.layout.static-items")) {
                config.set("player-gui.layout.static-items", new java.util.LinkedHashMap<>());
            }
        }

        if (before > 0 && before < 8) {
            boolean usingLegacyDefaultSlots = config.getIntegerList("player-gui.layout.kit-slots")
                    .equals(LEGACY_FULL_PLAYER_GUI_KIT_SLOTS);
            if (!hadRows || (usingLegacyDefaultSlots && config.getInt("player-gui.rows", 6) == 6)) {
                config.set("player-gui.rows", 3);
            }
            if (!hadKitSlots || usingLegacyDefaultSlots) {
                config.set("player-gui.layout.kit-slots", DEFAULT_PLAYER_GUI_KIT_SLOTS);
            }
        }

        if (before < 9 && !migratePlayerGuiResource(config)) {
            plugin.getLogger().warning("FoKits public GUI migration was not completed; keeping the old config version for retry.");
            plugin.saveConfig();
            return;
        }

        if (before < LATEST_CONFIG_VERSION) {
            config.set("config-version", LATEST_CONFIG_VERSION);
        }
        plugin.saveConfig();

        if (before > 0 && before < LATEST_CONFIG_VERSION) {
            plugin.getLogger().info("Migrated config from version " + before + " to " + LATEST_CONFIG_VERSION + ".");
        }
    }

    /** Copies legacy player-GUI presentation only into untouched bundled defaults. */
    private boolean migratePlayerGuiResource(FileConfiguration legacy) {
        GuiResourceLoader.loadAndBackfill(plugin, "guis/player-kits.yml");
        File targetFile = ResourceFiles.dataFile(plugin, "guis/player-kits.yml");
        YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = plugin.getResource("guis/player-kits.yml")) {
            if (input == null) {
                return false;
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not read bundled FoKits GUI defaults: " + exception.getMessage());
            return false;
        }

        copyIfUntouched(legacy, "player-gui.title", target, "title", defaults);
        copyIfUntouched(legacy, "player-gui.filler-material", target, "filler.material", defaults);
        for (String state : java.util.List.of("available", "cooldown", "no-permission", "claimed-once", "disabled")) {
            copyIfUntouched(legacy, "player-gui.lore." + state, target, "states." + state + ".lore", defaults);
        }
        try {
            target.save(targetFile);
            return true;
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not save migrated FoKits GUI resource: " + exception.getMessage());
            return false;
        }
    }

    private void copyIfUntouched(FileConfiguration legacy, String oldPath, YamlConfiguration target,
            String newPath, YamlConfiguration defaults) {
        if (!legacy.isSet(oldPath) || !Objects.deepEquals(target.get(newPath), defaults.get(newPath))) {
            return;
        }
        target.set(newPath, legacy.get(oldPath));
    }
}
