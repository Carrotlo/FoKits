package me.foesio.foKits.config;

import me.foesio.core.dialog.NativeDialogConfigDefaults;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    public static final int LATEST_CONFIG_VERSION = 8;
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

        config.set("config-version", LATEST_CONFIG_VERSION);
        plugin.saveConfig();

        if (before > 0 && before < LATEST_CONFIG_VERSION) {
            plugin.getLogger().info("Migrated config from version " + before + " to " + LATEST_CONFIG_VERSION + ".");
        }
    }
}
