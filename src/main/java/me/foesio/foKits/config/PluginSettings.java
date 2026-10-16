package me.foesio.foKits.config;

import me.foesio.core.config.ResourceFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin editor settings. The Core service owns the live public presentation. */
public class PluginSettings {
    private static final String GUI_RESOURCE = "guis/player-kits.yml";
    private final JavaPlugin plugin;
    private FileConfiguration gui = new YamlConfiguration();

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadGui();
    }

    public void reloadGui() {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(ResourceFiles.dataFile(plugin, GUI_RESOURCE));
            gui = loaded;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not read admin GUI settings from " + GUI_RESOURCE
                    + ": " + exception.getMessage());
        }
    }

    public boolean overrideFullInventories() {
        return plugin.getConfig().getBoolean("inventory.override-full-inventories", false);
    }

    public String playerGuiTitle() {
        return gui.getString("title", "");
    }

    public int playerGuiRows() {
        return Math.max(1, Math.min(6, gui.getInt("rows", 3)));
    }

    public boolean fillBackground() {
        return gui.getBoolean("filler.enabled", false);
    }

    public Material fillerMaterial() {
        String raw = gui.getString("filler.material", "");
        Material parsed = Material.matchMaterial(raw == null ? "" : raw);
        return parsed == null ? Material.AIR : parsed;
    }

    public List<Integer> playerGuiKitSlots() {
        return List.copyOf(gui.getIntegerList("entries.kits.slots"));
    }

    public Map<Integer, ItemStack> playerGuiStaticItems() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("player-gui.layout.static-items");
        if (section == null) {
            return Map.of();
        }
        List<Integer> slots = gui.getIntegerList("entries.static-items.slots");
        Map<Integer, ItemStack> mapped = new LinkedHashMap<>();
        for (int slot : slots) {
            ItemStack item = section.getItemStack(Integer.toString(slot));
            if (item != null && !item.getType().isAir()) {
                mapped.put(slot, item);
            }
        }
        return mapped;
    }

    public FileConfiguration rawConfig() {
        return plugin.getConfig();
    }
}
