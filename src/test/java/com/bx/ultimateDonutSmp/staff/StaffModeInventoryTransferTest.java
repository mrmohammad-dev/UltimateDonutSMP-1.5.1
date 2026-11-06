package com.bx.ultimateDonutSmp.staff;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class StaffModeInventoryTransferTest {

    @Test
    void collectsAcquiredItemsFromStorageArmorOffhandAndCursor() {
        ItemStack diamond = new ItemStack(Material.DIAMOND, 32);
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack offhand = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemStack cursor = new ItemStack(Material.EMERALD, 12);

        List<ItemStack> result = StaffModeInventoryTransfer.collect(
                new ItemStack[]{diamond, null, new ItemStack(Material.AIR)},
                new ItemStack[]{helmet},
                offhand,
                cursor,
                item -> false
        );

        assertEquals(List.of(Material.DIAMOND, Material.DIAMOND_HELMET,
                Material.TOTEM_OF_UNDYING, Material.EMERALD),
                result.stream().map(ItemStack::getType).toList());
        assertEquals(List.of(32, 1, 1, 12), result.stream().map(ItemStack::getAmount).toList());
    }

    @Test
    void excludesStaffToolsAndClonesPreservedItems() {
        ItemStack diamond = new ItemStack(Material.DIAMOND, 16);
        ItemStack staffTool = new ItemStack(Material.COMPASS);
        List<ItemStack> result = StaffModeInventoryTransfer.collect(
                new ItemStack[]{diamond, staffTool}, null, null, null,
                item -> item.getType() == Material.COMPASS
        );

        assertEquals(1, result.size());
        assertEquals(Material.DIAMOND, result.get(0).getType());
        assertEquals(16, result.get(0).getAmount());
        assertNotSame(diamond, result.get(0));

        diamond.setAmount(1);
        assertEquals(16, result.get(0).getAmount(), "captured item must not alias the live inventory stack");
    }

    @Test
    void returnsEmptyWhenNoRealItemsWereAcquired() {
        assertEquals(List.of(), StaffModeInventoryTransfer.collect(
                new ItemStack[]{null, new ItemStack(Material.AIR)},
                null, null, null, item -> false
        ));
    }
}
