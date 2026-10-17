package me.foesio.foShop.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiSlotResolverTest {

    @Test
    void collisionFallsBackAndReservesTheSameEffectiveSlotForClicks() {
        Set<Integer> occupied = new HashSet<>(Set.of(10, 11));
        assertEquals(22, GuiSlotResolver.resolve(10, 22, 27, occupied));
        assertEquals(Set.of(10, 11, 22), occupied);
    }

    @Test
    void configuredListsAreUniqueAndFilledFromSafeDefaults() {
        assertArrayEquals(new int[]{11, 12, 13},
                GuiSlotResolver.uniqueSlots(List.of(11, 11, 99), new int[]{11, 12, 13}, 3, 27));
    }
}
