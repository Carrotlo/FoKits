package me.foesio.foKits.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class KitDefinition {
    private String key;
    private boolean enabled = true;
    private String displayName = "Starter";
    private Material iconMaterial = Material.CHEST;
    private Integer iconCustomModelData;
    private ItemStack iconItem;
    private int orderIndex;
    private String requiredPermission = "";
    private ClaimMode claimMode = ClaimMode.COOLDOWN;
    private long cooldownMillis = 86_400_000L;
    private final Set<String> denyWorlds = new HashSet<>();
    private boolean broadcastOnClaim;
    private final List<String> commandsOnClaim = new ArrayList<>();
    private ItemStack claimedDisplayItem;
    private ItemStack[] inventoryItems = new ItemStack[36];
    private ItemStack[] armorItems = new ItemStack[4];
    private ItemStack offhandItem;

    public KitDefinition(String key) {
        this.key = sanitizeKey(key);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = sanitizeKey(key);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public void setIconMaterial(Material iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public Integer getIconCustomModelData() {
        return iconCustomModelData;
    }

    public void setIconCustomModelData(Integer iconCustomModelData) {
        this.iconCustomModelData = iconCustomModelData;
    }

    public ItemStack getIconItem() {
        return iconItem;
    }

    public void setIconItem(ItemStack iconItem) {
        this.iconItem = iconItem;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = Math.max(0, orderIndex);
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission == null ? "" : requiredPermission.trim();
    }

    public ClaimMode getClaimMode() {
        return claimMode;
    }

    public void setClaimMode(ClaimMode claimMode) {
        this.claimMode = claimMode;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = Math.max(0L, cooldownMillis);
    }

    public Set<String> getDenyWorlds() {
        return denyWorlds;
    }

    public boolean isWorldDenied(String worldName) {
        return denyWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public void setDenyWorlds(List<String> worlds) {
        denyWorlds.clear();
        if (worlds == null) {
            return;
        }
        for (String world : worlds) {
            if (world == null || world.isBlank()) {
                continue;
            }
            denyWorlds.add(world.trim().toLowerCase(Locale.ROOT));
        }
    }

    public boolean isBroadcastOnClaim() {
        return broadcastOnClaim;
    }

    public void setBroadcastOnClaim(boolean broadcastOnClaim) {
        this.broadcastOnClaim = broadcastOnClaim;
    }

    public List<String> getCommandsOnClaim() {
        return commandsOnClaim;
    }

    public void setCommandsOnClaim(List<String> commands) {
        commandsOnClaim.clear();
        if (commands != null) {
            commandsOnClaim.addAll(commands);
        }
    }

    public ItemStack getClaimedDisplayItem() {
        return claimedDisplayItem;
    }

    public void setClaimedDisplayItem(ItemStack claimedDisplayItem) {
        this.claimedDisplayItem = claimedDisplayItem;
    }

    public ItemStack[] getInventoryItems() {
        return inventoryItems;
    }

    public void setInventoryItems(ItemStack[] inventoryItems) {
        this.inventoryItems = inventoryItems == null ? new ItemStack[36] : inventoryItems;
    }

    public ItemStack[] getArmorItems() {
        return armorItems;
    }

    public void setArmorItems(ItemStack[] armorItems) {
        this.armorItems = armorItems == null ? new ItemStack[4] : armorItems;
    }

    public ItemStack getOffhandItem() {
        return offhandItem;
    }

    public void setOffhandItem(ItemStack offhandItem) {
        this.offhandItem = offhandItem;
    }

    public String getDisplayOrKey() {
        return (displayName == null || displayName.isBlank()) ? key : displayName;
    }

    public String sanitizedFileName() {
        return sanitizeKey(key);
    }

    public KitDefinition copy() {
        KitDefinition copy = new KitDefinition(key);
        copy.setEnabled(enabled);
        copy.setDisplayName(displayName);
        copy.setIconMaterial(iconMaterial);
        copy.setIconCustomModelData(iconCustomModelData);
        copy.setIconItem(iconItem == null ? null : iconItem.clone());
        copy.setOrderIndex(orderIndex);
        copy.setRequiredPermission(requiredPermission);
        copy.setClaimMode(claimMode);
        copy.setCooldownMillis(cooldownMillis);
        copy.setDenyWorlds(new ArrayList<>(denyWorlds));
        copy.setBroadcastOnClaim(broadcastOnClaim);
        copy.setCommandsOnClaim(new ArrayList<>(commandsOnClaim));
        copy.setClaimedDisplayItem(claimedDisplayItem == null ? null : claimedDisplayItem.clone());
        copy.setInventoryItems(cloneItems(inventoryItems, 36));
        copy.setArmorItems(cloneItems(armorItems, 4));
        copy.setOffhandItem(offhandItem == null ? null : offhandItem.clone());
        return copy;
    }

    private static ItemStack[] cloneItems(ItemStack[] source, int size) {
        ItemStack[] out = new ItemStack[size];
        if (source == null) {
            return out;
        }
        for (int i = 0; i < Math.min(source.length, size); i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }

    public static String sanitizeKey(String input) {
        if (input == null) {
            return "kit";
        }
        String base = input.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (base.isBlank()) {
            return "kit";
        }
        return base;
    }
}
