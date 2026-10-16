package me.foesio.foKits.gui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicKitMenuLayoutTest {
    @Test
    void paginatesEveryKitAndClampsRequestedPage() {
        assertEquals(3, PublicKitMenuLayout.pageCount(12, 5));
        assertEquals(2, PublicKitMenuLayout.clampPage(99, 3));
        assertEquals(10, PublicKitMenuLayout.pageStart(2, 5));
        assertEquals(1, PublicKitMenuLayout.pageCount(0, 5));
    }

    @Test
    void resolvesControlCollisionToSafeFallback() {
        assertEquals(9, PublicKitMenuLayout.resolveControlSlot(11, 9, 27, Set.of(11, 12, 13, 14, 15)));
        assertEquals(0, PublicKitMenuLayout.resolveControlSlot(11, 9, 27,
                Set.of(9, 10, 11, 12, 13, 14, 15, 16, 17)));
    }

    @Test
    void removesStaticAndDuplicateContentSlots() {
        assertEquals(List.of(11, 13), PublicKitMenuLayout.contentSlots(
                List.of(11, 12, 11, 13, 99, -1), 27, Set.of(12)));
    }

}
