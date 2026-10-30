package me.foesio.foKits.storage;

import me.foesio.foKits.model.KitDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserDataRepository {
    private final Logger logger;
    private final File userDataFolder;
    private final Map<UUID, YamlConfiguration> cache = new ConcurrentHashMap<>();

    public UserDataRepository(JavaPlugin plugin) {
        this(plugin.getDataFolder(), plugin.getLogger());
    }

    UserDataRepository(File dataFolder, Logger logger) {
        this.logger = logger;
        this.userDataFolder = new File(dataFolder, "userdata");
    }

    public void ensureFolder() {
        if (!userDataFolder.exists() && !userDataFolder.mkdirs()) {
            logger.warning("Could not create userdata folder.");
        }
    }

    public long getLastClaim(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        synchronized (yaml) {
            return yaml.getLong(path(kitKey, "last-claim-epoch-ms"), 0L);
        }
    }

    public boolean hasClaimedOnce(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        synchronized (yaml) {
            return yaml.getBoolean(path(kitKey, "claimed-once"), false);
        }
    }

    public void setClaim(UUID uuid, KitDefinition kit, long timestampMillis) {
        YamlConfiguration yaml = load(uuid);
        synchronized (yaml) {
            yaml.set(path(kit.getKey(), "last-claim-epoch-ms"), timestampMillis);
            yaml.set(path(kit.getKey(), "claimed-once"), true);
            save(uuid, yaml);
        }
    }

    public int resetCooldowns(UUID uuid) {
        return resetCooldownsChecked(uuid).resetCount();
    }

    public ResetBatch resetCooldownsChecked(UUID uuid) {
        YamlConfiguration yaml = load(uuid);
        synchronized (yaml) {
            ConfigurationSection section = yaml.getConfigurationSection("kits");
            if (section == null) return new ResetBatch(ResetStatus.UNCHANGED, 0);
            Map<String, Object> previous = new LinkedHashMap<>();
            for (String kitKey : new ArrayList<>(section.getKeys(false))) {
                String cooldownPath = path(kitKey, "last-claim-epoch-ms");
                if (yaml.contains(cooldownPath)) {
                    previous.put(cooldownPath, yaml.get(cooldownPath));
                    yaml.set(cooldownPath, null);
                }
            }
            if (previous.isEmpty()) return new ResetBatch(ResetStatus.UNCHANGED, 0);
            if (!save(uuid, yaml)) {
                previous.forEach(yaml::set);
                return new ResetBatch(ResetStatus.SAVE_FAILED, 0);
            }
            return new ResetBatch(ResetStatus.RESET, previous.size());
        }
    }

    public boolean resetCooldown(UUID uuid, String kitKey) {
        return resetCooldownChecked(uuid, kitKey) == ResetStatus.RESET;
    }

    public ResetStatus resetCooldownChecked(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        synchronized (yaml) {
            String cooldownPath = path(kitKey, "last-claim-epoch-ms");
            if (!yaml.contains(cooldownPath)) return ResetStatus.UNCHANGED;
            Object previous = yaml.get(cooldownPath);
            yaml.set(cooldownPath, null);
            if (!save(uuid, yaml)) {
                yaml.set(cooldownPath, previous);
                return ResetStatus.SAVE_FAILED;
            }
            return ResetStatus.RESET;
        }
    }

    public boolean resetOneTimeClaim(UUID uuid, String kitKey) {
        return resetOneTimeClaimChecked(uuid, kitKey) == ResetStatus.RESET;
    }

    public ResetStatus resetOneTimeClaimChecked(UUID uuid, String kitKey) {
        YamlConfiguration yaml = load(uuid);
        synchronized (yaml) {
            String claimedPath = path(kitKey, "claimed-once");
            if (!yaml.getBoolean(claimedPath, false)) return ResetStatus.UNCHANGED;
            Object previous = yaml.get(claimedPath);
            yaml.set(claimedPath, null);
            if (!save(uuid, yaml)) {
                yaml.set(claimedPath, previous);
                return ResetStatus.SAVE_FAILED;
            }
            return ResetStatus.RESET;
        }
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

    private boolean save(UUID uuid, YamlConfiguration yaml) {
        Path file = fileOf(uuid).toPath();
        Path staged = null;
        try {
            staged = Files.createTempFile(userDataFolder.toPath(), ".userdata-", ".staged");
            Files.writeString(staged, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed saving userdata for " + uuid, exception);
            return false;
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException cleanupFailure) {
                    logger.log(Level.WARNING, "Failed removing staged userdata for " + uuid, cleanupFailure);
                }
            }
        }
    }

    public enum ResetStatus { RESET, UNCHANGED, SAVE_FAILED }

    public record ResetBatch(ResetStatus status, int resetCount) { }

    private File fileOf(UUID uuid) {
        return new File(userDataFolder, uuid + ".yml");
    }

    private String path(String kitKey, String node) {
        return "kits." + KitDefinition.sanitizeKey(kitKey) + "." + node;
    }
}
