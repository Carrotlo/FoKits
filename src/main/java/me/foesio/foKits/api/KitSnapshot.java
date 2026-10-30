package me.foesio.foKits.api;

import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Immutable kit metadata. The icon is cloned on input and output. */
public final class KitSnapshot {
    private final String key;
    private final String displayName;
    private final boolean enabled;
    private final String requiredPermission;
    private final boolean oneTime;
    private final long cooldownMillis;
    private final Set<String> deniedWorlds;
    private final ItemStack icon;

    public KitSnapshot(String key, String displayName, boolean enabled, String requiredPermission,
                       boolean oneTime, long cooldownMillis, Set<String> deniedWorlds, ItemStack icon) {
        this.key = key;
        this.displayName = displayName;
        this.enabled = enabled;
        this.requiredPermission = requiredPermission;
        this.oneTime = oneTime;
        this.cooldownMillis = cooldownMillis;
        this.deniedWorlds = Set.copyOf(deniedWorlds);
        this.icon = icon == null ? null : icon.clone();
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public String requiredPermission() { return requiredPermission; }
    public boolean oneTime() { return oneTime; }
    public long cooldownMillis() { return cooldownMillis; }
    public Set<String> deniedWorlds() { return deniedWorlds; }
    public ItemStack icon() { return icon == null ? null : icon.clone(); }
}
