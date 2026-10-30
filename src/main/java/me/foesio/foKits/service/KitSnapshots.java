package me.foesio.foKits.service;

import me.foesio.foKits.api.KitSnapshot;
import me.foesio.foKits.model.ClaimMode;
import me.foesio.foKits.model.KitDefinition;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class KitSnapshots {
    private KitSnapshots() {
    }

    static KitSnapshot of(KitDefinition kit) {
        ItemStack icon = kit.getIconItem();
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(kit.getIconMaterial() == null ? Material.CHEST : kit.getIconMaterial());
        }
        return new KitSnapshot(kit.getKey(), kit.getDisplayOrKey(), kit.isEnabled(),
                kit.getRequiredPermission(), kit.getClaimMode() == ClaimMode.ONE_TIME,
                kit.getCooldownMillis(), kit.getDenyWorlds(), icon);
    }
}
