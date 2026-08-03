package me.foesio.foKits.storage;

import me.foesio.foKits.model.KitDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class UserDataRepository {
    private final JavaPlugin plugin;
    private final File userDataFolder;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();

    public UserDataRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.userDataFolder = new File(plugin.getDataFolder(), "userdata");
    }

    public void ensureFolder() {
        if (!userDataFolder.exists() && !userDataFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create userdata folder.");
        }
    }

    public long getLastClaim(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        return yaml.getLong(path(kitKey, "last-claim-epoch-ms"), 0L);
    }

    public boolean hasClaimedOnce(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        return yaml.getBoolean(path(kitKey, "claimed-once"), false);
    }

    public void setClaim(UUID uuid, KitDefinition kit, long timestampMillis) {
        YamlConfiguration yaml = load(uuid);
        yaml.set(path(kit.getKey(), "last-claim-epoch-ms"), timestampMillis);
        yaml.set(path(kit.getKey(), "claimed-once"), true);
        save(uuid, yaml);
    }

    public int resetCooldowns(UUID uuid) {
        YamlConfiguration yaml = load(uuid);
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section == null) {
            return 0;
        }

        int resetCount = 0;
        for (String kitKey : section.getKeys(false)) {
            String cooldownPath = path(kitKey, "last-claim-epoch-ms");
            if (!yaml.contains(cooldownPath)) {
                continue;
            }
            yaml.set(cooldownPath, null);
            resetCount++;
        }

        if (resetCount > 0) {
            save(uuid, yaml);
        }
        return resetCount;
    }

    public boolean resetCooldown(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        String cooldownPath = path(kitKey, "last-claim-epoch-ms");
        boolean hadCooldown = yaml.contains(cooldownPath);
        if (hadCooldown) {
            yaml.set(cooldownPath, null);
            save(uuid, yaml);
        }
        return hadCooldown;
    }

    public File getUserDataFolder() {
        return userDataFolder;
    }

    private YamlConfiguration load(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            File file = fileOf(id);
            if (!file.exists()) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    private void save(UUID uuid, YamlConfiguration yaml) {
        File file = fileOf(uuid);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving userdata for " + uuid, exception);
        }
    }

    private File fileOf(UUID uuid) {
        return new File(userDataFolder, uuid + ".yml");
    }

    private String path(String kitKey, String node) {
        return "kits." + KitDefinition.sanitizeKey(kitKey) + "." + node;
    }
}
