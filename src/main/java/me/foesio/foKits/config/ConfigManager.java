package me.foesio.foKits.config;

import me.foesio.core.dialog.NativeDialogConfigDefaults;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps non-GUI config versioning separate from the installed public GUI migration. */
public class ConfigManager {
    public static final int LATEST_CONFIG_VERSION = 13;

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.saveDefaultConfig();
        migrateAndSave();
    }

    public void reload(boolean migrationReady) {
        plugin.reloadConfig();
        if (migrationReady && currentVersion() >= 12) {
            migrateAndSave();
        }
    }

    public int currentVersion() {
        return plugin.getConfig().getInt("config-version", 0);
    }

    private void migrateAndSave() {
        FileConfiguration config = plugin.getConfig();
        int before = config.getInt("config-version", 0);
        if (before >= LATEST_CONFIG_VERSION) {
            return;
        }
        NativeDialogConfigDefaults.addDefaults(config);
        config.set("config-version", LATEST_CONFIG_VERSION);
        plugin.saveConfig();
        if (before > 0) {
            plugin.getLogger().info("Migrated config from version " + before
                    + " to " + LATEST_CONFIG_VERSION + ".");
        }
    }
}
