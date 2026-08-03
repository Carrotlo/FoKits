package me.foesio.foKits.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginSettings {
    private final JavaPlugin plugin;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean overrideFullInventories() {
        return plugin.getConfig().getBoolean("inventory.override-full-inventories", false);
    }

    public String playerGuiTitle() {
        return plugin.getConfig().getString("player-gui.title", "&8ᴋɪᴛ ѕᴇʟᴇᴄᴛᴏʀ");
    }

    public int playerGuiRows() {
        int rows = plugin.getConfig().getInt("player-gui.rows", 3);
        return Math.max(1, Math.min(6, rows));
    }

    public boolean fillBackground() {
        return plugin.getConfig().getBoolean("player-gui.fill-background", true);
    }

    public Material fillerMaterial() {
        String raw = plugin.getConfig().getString("player-gui.filler-material", "GRAY_STAINED_GLASS_PANE");
        Material parsed = Material.matchMaterial(raw == null ? "" : raw);
        return parsed == null ? Material.GRAY_STAINED_GLASS_PANE : parsed;
    }

    public List<String> loreForState(String state) {
        return plugin.getConfig().getStringList("player-gui.lore." + state);
    }

    public List<Integer> playerGuiKitSlots() {
        List<Integer> slots = new ArrayList<>();
        for (Integer slot : plugin.getConfig().getIntegerList("player-gui.layout.kit-slots")) {
            if (slot == null) {
                continue;
            }
            if (slot >= 0 && slot < playerGuiRows() * 9) {
                slots.add(slot);
            }
        }
        Collections.sort(slots);
        return slots;
    }

    public Map<Integer, ItemStack> playerGuiStaticItems() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("player-gui.layout.static-items");
        if (section == null) {
            return Map.of();
        }

        Map<Integer, ItemStack> out = new HashMap<>();
        int maxSlots = playerGuiRows() * 9;
        for (String key : section.getKeys(false)) {
            if (!key.matches("\\d+")) {
                continue;
            }
            int slot = Integer.parseInt(key);
            if (slot < 0 || slot >= maxSlots) {
                continue;
            }
            ItemStack item = section.getItemStack(key);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            out.put(slot, item);
        }
        return out;
    }

    public FileConfiguration rawConfig() {
        return plugin.getConfig();
    }
}
