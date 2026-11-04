package com.bx.ultimateDonutSmp.staff;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VirtualChestHolder implements InventoryHolder {

    private final Location chestLocation;
    private Inventory inventory;

    public VirtualChestHolder(Location chestLocation) {
        this.chestLocation = chestLocation;
    }

    /**
     * یک inventory مجازی با عنوان legacy (String) می‌سازه.
     * از API قدیمی Bukkit استفاده می‌کنه چون Adventure API در این پروژه در دسترس نیست.
     */
    @SuppressWarnings("deprecation")
    public void createInventory(int size, String legacyTitle) {
        this.inventory = Bukkit.createInventory(
                this,
                size,
                legacyTitle == null ? "Chest" : legacyTitle
        );
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Location getChestLocation() {
        return chestLocation;
    }
}