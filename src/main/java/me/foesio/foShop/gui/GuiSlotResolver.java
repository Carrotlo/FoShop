package me.foesio.foShop.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves configured controls to visible, unique slots shared by rendering and clicks. */
final class GuiSlotResolver {

    private GuiSlotResolver() {
    }

    static int resolve(int configured, int fallback, int inventorySize, Set<Integer> occupied) {
        int requested = configured < 0 ? fallback : configured;
        if (isFree(requested, inventorySize, occupied)) {
            occupied.add(requested);
            return requested;
        }
        if (isFree(fallback, inventorySize, occupied)) {
            occupied.add(fallback);
            return fallback;
        }
        for (int slot = 0; slot < inventorySize; slot++) {
            if (occupied.add(slot)) {
                return slot;
            }
        }
        return -1;
    }

    static int[] uniqueSlots(List<Integer> configured, int[] defaults, int maximum, int inventorySize) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        for (Integer slot : configured) {
            if (slot != null && slot >= 0 && slot < inventorySize) {
                slots.add(slot);
            }
            if (slots.size() >= maximum) {
                break;
            }
        }
        for (int slot : defaults) {
            if (slot >= 0 && slot < inventorySize) {
                slots.add(slot);
            }
            if (slots.size() >= maximum) {
                break;
            }
        }
        List<Integer> ordered = new ArrayList<>(slots);
        return ordered.stream().limit(maximum).mapToInt(Integer::intValue).toArray();
    }

    private static boolean isFree(int slot, int inventorySize, Set<Integer> occupied) {
        return slot >= 0 && slot < inventorySize && !occupied.contains(slot);
    }
}
