package me.foesio.foKits.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PublicKitMenuLayout {
    private PublicKitMenuLayout() {
    }

    static int pageCount(int entryCount, int pageCapacity) {
        if (entryCount <= 0 || pageCapacity <= 0) {
            return 1;
        }
        return Math.max(1, (entryCount + pageCapacity - 1) / pageCapacity);
    }

    static int clampPage(int requestedPage, int pageCount) {
        return Math.max(0, Math.min(requestedPage, Math.max(1, pageCount) - 1));
    }

    static int pageStart(int page, int pageCapacity) {
        return Math.max(0, page) * Math.max(0, pageCapacity);
    }

    static int resolveControlSlot(int configuredSlot, int fallbackSlot, int inventorySize,
                                  Set<Integer> reservedSlots) {
        if (isAvailable(configuredSlot, inventorySize, reservedSlots)) {
            return configuredSlot;
        }
        if (isAvailable(fallbackSlot, inventorySize, reservedSlots)) {
            return fallbackSlot;
        }
        for (int slot = 0; slot < inventorySize; slot++) {
            if (isAvailable(slot, inventorySize, reservedSlots)) {
                return slot;
            }
        }
        return -1;
    }

    static List<Integer> contentSlots(List<Integer> configuredSlots, int inventorySize,
                                      Set<Integer> blockedSlots) {
        Set<Integer> blocked = blockedSlots == null ? Set.of() : blockedSlots;
        LinkedHashSet<Integer> resolved = new LinkedHashSet<>();
        if (configuredSlots != null) {
            for (Integer slot : configuredSlots) {
                if (slot != null && slot >= 0 && slot < inventorySize && !blocked.contains(slot)) {
                    resolved.add(slot);
                }
            }
        }
        return List.copyOf(resolved);
    }

    private static boolean isAvailable(int slot, int inventorySize, Set<Integer> reservedSlots) {
        return slot >= 0 && slot < inventorySize
                && (reservedSlots == null || !reservedSlots.contains(slot));
    }
}
