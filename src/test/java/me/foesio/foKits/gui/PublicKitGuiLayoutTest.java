package me.foesio.foKits.gui;

import me.foesio.core.message.FoMessageService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicKitGuiLayoutTest {
    @Test
    void chestKitUsesMinecartSpriteWithoutChangingOtherKitIcons() {
        assertEquals(Material.CHEST_MINECART, PublicKitGui.spriteMaterial(Material.CHEST));
        assertEquals(Material.DIAMOND, PublicKitGui.spriteMaterial(Material.DIAMOND));
        assertEquals(":chest_minecart: ", FoMessageService.materialPlaceholders(
                "Starter Kit", PublicKitGui.spriteMaterial(Material.CHEST)).get("material_sprite"));
    }

    @Test
    void previewContentFollowsConfiguredSlotOrder() {
        List<Integer> moved = List.of(50, 2, 3, 4, 6, 18);
        assertEquals(50, PublicKitGui.contentSlot(moved, 0));
        assertEquals(6, PublicKitGui.contentSlot(moved, 4));
        assertEquals(18, PublicKitGui.contentSlot(moved, 5));
        assertEquals(-1, PublicKitGui.contentSlot(moved, 6));
    }
}
