package com.bx.ultimateDonutSmp.staff;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Collects real items acquired while StaffMode is active so restoring the pre-StaffMode snapshot
 * does not silently discard them. Staff tools are excluded by the supplied predicate.
 */
public final class StaffModeInventoryTransfer {

    private StaffModeInventoryTransfer() {
    }

    public static List<ItemStack> collect(ItemStack[] storage,
                                          ItemStack[] armor,
                                          ItemStack offhand,
                                          ItemStack cursor,
                                          Predicate<ItemStack> isStaffModeItem) {
        List<ItemStack> collected = new ArrayList<>();
        append(collected, storage, isStaffModeItem);
        append(collected, armor, isStaffModeItem);
        append(collected, new ItemStack[]{offhand, cursor}, isStaffModeItem);
        return List.copyOf(collected);
    }

    private static void append(List<ItemStack> destination,
                               ItemStack[] source,
                               Predicate<ItemStack> isStaffModeItem) {
        if (source == null) {
            return;
        }
        for (ItemStack item : source) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (isStaffModeItem != null && isStaffModeItem.test(item)) {
                continue;
            }
            destination.add(item.clone());
        }
    }
}
