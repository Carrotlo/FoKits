package me.foesio.foKits.config;

import me.foesio.core.gui.GuiItemConfig;
import me.foesio.core.gui.GuiResourceLoader;
import org.bukkit.entity.Player;
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
    private static final String GUI_RESOURCE = "guis/player-kits.yml";
    private final JavaPlugin plugin;
    private FileConfiguration gui;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadGui();
    }

    public void reloadGui() {
        gui = GuiResourceLoader.loadAndBackfill(plugin, GUI_RESOURCE);
    }

    public boolean overrideFullInventories() {
        return plugin.getConfig().getBoolean("inventory.override-full-inventories", false);
    }

    public String playerGuiTitle() {
        return gui.getString("title", plugin.getConfig().getString("player-gui.title", "&8ᴋɪᴛ ѕᴇʟᴇᴄᴛᴏʀ"));
    }

    public int playerGuiRows() {
        int rows = plugin.getConfig().getInt("player-gui.rows", 3);
        return Math.max(1, Math.min(6, rows));
    }

    public boolean fillBackground() {
        return plugin.getConfig().getBoolean("player-gui.fill-background", true);
    }

    public Material fillerMaterial() {
        String raw = gui.getString("filler.material",
                plugin.getConfig().getString("player-gui.filler-material", "GRAY_STAINED_GLASS_PANE"));
        Material parsed = Material.matchMaterial(raw == null ? "" : raw);
        return parsed == null ? Material.GRAY_STAINED_GLASS_PANE : parsed;
    }

    public ItemStack playerGuiFiller(Player viewer) {
        return GuiItemConfig.from(gui.getConfigurationSection("filler"),
                GuiItemConfig.of(fillerMaterial(), " ", List.of())).create(viewer);
    }

    public ItemStack emptyKitSlot(Player viewer) {
        return GuiItemConfig.from(gui.getConfigurationSection("empty-kit-slot"),
                GuiItemConfig.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of())).create(viewer);
    }

    public ItemStack previewFiller(Player viewer) {
        return GuiItemConfig.from(gui.getConfigurationSection("preview.filler"),
                GuiItemConfig.of(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())).create(viewer);
    }

    public ItemStack previewBack(Player viewer) {
        return GuiItemConfig.from(gui.getConfigurationSection("preview.buttons.back"),
                GuiItemConfig.of(Material.IRON_DOOR, "&e&lBACK", List.of("&8ʙᴜᴛᴛᴏɴ", " ", "&eⓘ Information ↓",
                        "&7&l | &fReturn to the previous menu.", " ", "&a→ Click to go back ←"))).create(viewer);
    }

    public String previewTitle(String kitName) {
        return gui.getString("preview.title", "&8ᴋɪᴛ ᴘʀᴇᴠɪᴇᴡ &8- {kit}")
                .replace("{kit}", kitName == null ? "" : kitName);
    }

    public List<String> loreForState(String state) {
        String path = "states." + state + ".lore";
        return gui.isSet(path) ? gui.getStringList(path) : plugin.getConfig().getStringList("player-gui.lore." + state);
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
