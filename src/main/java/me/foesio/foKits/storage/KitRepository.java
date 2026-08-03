package me.foesio.foKits.storage;

import me.foesio.foKits.model.ClaimMode;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class KitRepository {
    private final JavaPlugin plugin;
    private final File kitsFolder;
    private final Map<String, KitDefinition> cache = new LinkedHashMap<>();

    public KitRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.kitsFolder = new File(plugin.getDataFolder(), "kits");
    }

    public void loadAll() {
        if (!kitsFolder.exists() && !kitsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create kits folder.");
        }

        ensureDefaultIfEmpty();

        cache.clear();
        File[] files = kitsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            try {
                KitDefinition kit = readKit(yaml, file.getName().replace(".yml", ""));
                cache.put(kit.getKey(), kit);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Failed loading kit file " + file.getName(), exception);
            }
        }
    }

    public Collection<KitDefinition> getAll() {
        List<KitDefinition> kits = new ArrayList<>();
        for (KitDefinition kit : cache.values()) {
            kits.add(kit.copy());
        }
        kits.sort(Comparator.comparingInt(KitDefinition::getOrderIndex).thenComparing(KitDefinition::getKey));
        return kits;
    }

    public Optional<KitDefinition> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        KitDefinition kit = cache.get(KitDefinition.sanitizeKey(key));
        return kit == null ? Optional.empty() : Optional.of(kit.copy());
    }

    public boolean exists(String key) {
        return cache.containsKey(KitDefinition.sanitizeKey(key));
    }

    public KitDefinition createNew(String key) {
        String finalKey = KitDefinition.sanitizeKey(key);
        if (cache.containsKey(finalKey)) {
            throw new IllegalArgumentException("Kit already exists: " + finalKey);
        }

        KitDefinition kit = new KitDefinition(finalKey);
        kit.setDisplayName(toTitleCase(finalKey));
        kit.setIconMaterial(Material.CHEST);
        kit.setOrderIndex(cache.size());
        kit.setRequiredPermission("fokits.claim." + finalKey);
        kit.setClaimMode(ClaimMode.COOLDOWN);
        kit.setCooldownMillis(TimeUtil.parseDurationMillis("24h"));
        save(kit);
        return kit.copy();
    }

    public void save(KitDefinition input) {
        KitDefinition kit = input.copy();
        String key = KitDefinition.sanitizeKey(kit.getKey());
        kit.setKey(key);

        File file = new File(kitsFolder, key + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("key", key);
        yaml.set("enabled", kit.isEnabled());
        yaml.set("display-name", kit.getDisplayName());
        yaml.set("icon.item", kit.getIconItem());
        yaml.set("icon.claimed-item", kit.getClaimedDisplayItem());
        yaml.set("order-index", kit.getOrderIndex());
        yaml.set("required-permission", kit.getRequiredPermission());
        yaml.set("claim-mode", kit.getClaimMode().name().toLowerCase(Locale.ROOT));
        yaml.set("cooldown", formatDurationForFile(kit.getCooldownMillis()));
        yaml.set("deny-worlds", new ArrayList<>(kit.getDenyWorlds()));
        yaml.set("broadcast-on-claim", kit.isBroadcastOnClaim());
        yaml.set("commands-on-claim", kit.getCommandsOnClaim());

        writeIndexedItems(yaml.createSection("items.inventory"), kit.getInventoryItems(), 36);
        writeIndexedItems(yaml.createSection("items.armor"), kit.getArmorItems(), 4);
        yaml.set("items.offhand", kit.getOffhandItem());

        try {
            yaml.save(file);
            cache.put(key, kit);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving kit " + key, exception);
        }
    }

    public boolean delete(String key) {
        String sanitized = KitDefinition.sanitizeKey(key);
        cache.remove(sanitized);
        File file = new File(kitsFolder, sanitized + ".yml");
        return !file.exists() || file.delete();
    }

    public Optional<KitDefinition> rename(String oldKey, String newKey) {
        String oldSanitized = KitDefinition.sanitizeKey(oldKey);
        String newSanitized = KitDefinition.sanitizeKey(newKey);
        if (!cache.containsKey(oldSanitized) || cache.containsKey(newSanitized)) {
            return Optional.empty();
        }

        KitDefinition kit = cache.remove(oldSanitized);
        File oldFile = new File(kitsFolder, oldSanitized + ".yml");
        if (oldFile.exists() && !oldFile.delete()) {
            plugin.getLogger().warning("Could not delete old kit file for rename: " + oldFile.getName());
        }

        kit.setKey(newSanitized);
        save(kit);
        return Optional.of(kit.copy());
    }

    private KitDefinition readKit(YamlConfiguration yaml, String fallbackKey) {
        String key = KitDefinition.sanitizeKey(yaml.getString("key", fallbackKey));
        KitDefinition kit = new KitDefinition(key);
        kit.setEnabled(yaml.getBoolean("enabled", true));
        kit.setDisplayName(yaml.getString("display-name", toTitleCase(key)));
        kit.setIconMaterial(readMaterial(yaml.getString("icon.material", "CHEST")));
        if (yaml.contains("icon.custom-model-data")) {
            kit.setIconCustomModelData(yaml.getInt("icon.custom-model-data"));
        }
        ItemStack iconItem = yaml.getItemStack("icon.item");
        if (iconItem == null || iconItem.getType().isAir()) {
            iconItem = new ItemStack(kit.getIconMaterial() == null ? Material.CHEST : kit.getIconMaterial());
            if (kit.getIconCustomModelData() != null) {
                org.bukkit.inventory.meta.ItemMeta meta = iconItem.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(kit.getIconCustomModelData());
                    iconItem.setItemMeta(meta);
                }
            }
        }
        kit.setIconItem(iconItem);
        kit.setClaimedDisplayItem(yaml.getItemStack("icon.claimed-item"));
        kit.setOrderIndex(Math.max(0, yaml.getInt("order-index", 0)));
        kit.setRequiredPermission(yaml.getString("required-permission", ""));

        String claimModeString = yaml.getString("claim-mode", "cooldown");
        if ("one-time".equalsIgnoreCase(claimModeString) || "one_time".equalsIgnoreCase(claimModeString)) {
            kit.setClaimMode(ClaimMode.ONE_TIME);
        } else {
            kit.setClaimMode(ClaimMode.COOLDOWN);
        }

        String cooldownString = yaml.getString("cooldown", "24h");
        long cooldownMillis = TimeUtil.parseDurationMillis(cooldownString);
        if (cooldownMillis <= 0 && kit.getClaimMode() == ClaimMode.COOLDOWN) {
            cooldownMillis = TimeUtil.parseDurationMillis("24h");
        }
        kit.setCooldownMillis(cooldownMillis);

        kit.setDenyWorlds(yaml.getStringList("deny-worlds"));
        kit.setBroadcastOnClaim(yaml.getBoolean("broadcast-on-claim", false));
        kit.setCommandsOnClaim(yaml.getStringList("commands-on-claim"));

        kit.setInventoryItems(readIndexedItems(yaml.getConfigurationSection("items.inventory"), 36));
        kit.setArmorItems(readIndexedItems(yaml.getConfigurationSection("items.armor"), 4));
        kit.setOffhandItem(yaml.getItemStack("items.offhand"));

        return kit;
    }

    private void ensureDefaultIfEmpty() {
        File[] files = kitsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null && files.length > 0) {
            return;
        }

        KitDefinition kit = new KitDefinition("starter");
        kit.setDisplayName("&aStarter Kit");
        kit.setIconMaterial(Material.CHEST);
        kit.setClaimMode(ClaimMode.COOLDOWN);
        kit.setCooldownMillis(TimeUtil.parseDurationMillis("24h"));

        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(Material.IRON_SWORD);
        inventory[1] = new ItemStack(Material.COOKED_BEEF, 16);
        inventory[2] = new ItemStack(Material.OAK_LOG, 32);
        kit.setInventoryItems(inventory);

        save(kit);
    }

    private Material readMaterial(String input) {
        if (input == null) {
            return Material.CHEST;
        }
        Material material = Material.matchMaterial(input);
        return material == null ? Material.CHEST : material;
    }

    private ItemStack[] readIndexedItems(ConfigurationSection section, int length) {
        ItemStack[] out = new ItemStack[length];
        if (section == null) {
            return out;
        }

        for (String key : section.getKeys(false)) {
            if (!key.matches("\\d+")) {
                continue;
            }
            int index = Integer.parseInt(key);
            if (index < 0 || index >= length) {
                continue;
            }
            out[index] = section.getItemStack(key);
        }
        return out;
    }

    private void writeIndexedItems(ConfigurationSection section, ItemStack[] input, int length) {
        for (int i = 0; i < length; i++) {
            ItemStack item = (input != null && i < input.length) ? input[i] : null;
            section.set(String.valueOf(i), item);
        }
    }

    private String toTitleCase(String key) {
        String[] split = key.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : split) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? key : builder.toString();
    }

    private String formatDurationForFile(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        String formatted = TimeUtil.formatDuration(millis);
        return formatted.isBlank() ? "0s" : formatted.replace(" ", "");
    }

    public File getKitsFolder() {
        return kitsFolder;
    }

    public Map<String, KitDefinition> getRawCacheView() {
        return Collections.unmodifiableMap(cache);
    }
}
