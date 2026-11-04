package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.FreezeManager;
import com.bx.ultimateDonutSmp.managers.StaffModeManager;
import com.bx.ultimateDonutSmp.staff.StaffToolType;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffModeListener implements Listener {

    private static final long INTERACT_COOLDOWN_MS = 200L;

    private final UltimateDonutSmp plugin;
    private final Map<UUID, Long> lastInteractTimes = new ConcurrentHashMap<>();

    public StaffModeListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || !plugin.getStaffModeManager().shouldLockTools()) {
            return;
        }

        if (!touchesStaffTool(player, event)) {
            return;
        }

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || !plugin.getStaffModeManager().shouldLockTools()) {
            return;
        }

        if (!touchesStaffTool(event)) {
            return;
        }

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || !plugin.getStaffModeManager().shouldLockTools()) {
            return;
        }
        if (!plugin.getStaffModeManager().isStaffTool(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
        plugin.getStaffModeManager().sendToolLockedMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || !plugin.getStaffModeManager().shouldLockTools()) {
            return;
        }
        if (!plugin.getStaffModeManager().isStaffTool(event.getMainHandItem())
                && !plugin.getStaffModeManager().isStaffTool(event.getOffHandItem())) {
            return;
        }

        event.setCancelled(true);
        plugin.getStaffModeManager().sendToolLockedMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getBlock().getType() != Material.FARMLAND
                || event.getTo() != Material.DIRT) {
            return;
        }
        if (plugin.getStaffModeManager().isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || !plugin.getStaffModeManager().shouldLockTools()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        StaffToolType toolType = plugin.getStaffModeManager().resolveTool(event.getItem());
        if (toolType == null) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        Action action = event.getAction();
        boolean isLeftClickFreeze = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
                && toolType == StaffToolType.FREEZE;
        boolean isRightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);

        if (!isLeftClickFreeze && !isRightClick) {
            return;
        }

        if (isOnInteractCooldown(player)) {
            return;
        }

        if (isLeftClickFreeze) {
            plugin.getStaffModeManager().openFrozenPlayers(player);
            return;
        }

        switch (toolType) {
            case VANISH -> {
                if (!plugin.getStaffModeManager().canUseVanish(player)) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getStaffModeManager().getMessage("NO-PERMISSION", "&cYou do not have permission.")
                    ));
                    return;
                }
                plugin.getStaffModeManager().toggleVanish(player);
            }
            case STAFF_LIST -> {
                if (!plugin.getStaffModeManager().canOpenStaffList(player)) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getStaffModeManager().getMessage("NO-PERMISSION", "&cYou do not have permission.")
                    ));
                    return;
                }
                plugin.getStaffModeManager().openStaffList(player);
            }
            case BETTER_VIEW -> {
                if (!plugin.getStaffModeManager().canUseBetterView(player)) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getStaffModeManager().getMessage("NO-PERMISSION", "&cYou do not have permission.")
                    ));
                    return;
                }
                plugin.getStaffModeManager().toggleBetterView(player);
            }
            case RANDOM_TELEPORT -> {
                if (!plugin.getStaffModeManager().canUseRandomTeleport(player)) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getStaffModeManager().getMessage("NO-PERMISSION", "&cYou do not have permission.")
                    ));
                    return;
                }
                if (plugin.getStaffModeManager().teleportToRandomPlayer(player) == null) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getStaffModeManager().getRandomTeleportMessage("NO_PLAYERS", "&cNo other players available for random teleport")
                    ));
                }
            }
            case CUSTOM -> plugin.getStaffModeManager().useCustomItem(player, event.getItem(), null);
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastInteractTimes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        StaffToolType toolType = plugin.getStaffModeManager().resolveTool(player.getInventory().getItemInMainHand());
        if (toolType == null) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        if (toolType == StaffToolType.CUSTOM) {
            if (isOnInteractCooldown(player)) {
                return;
            }
            plugin.getStaffModeManager().useCustomItem(
                    player, player.getInventory().getItemInMainHand(), target);
            return;
        }

        if (toolType != StaffToolType.FREEZE) {
            return;
        }

        handleFreezeInteraction(player, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damager = resolveDamager(event.getDamager());
        if (damager == null || !plugin.getStaffModeManager().isInStaffMode(damager.getUniqueId())) {
            return;
        }

        StaffToolType toolType = plugin.getStaffModeManager().resolveTool(damager.getInventory().getItemInMainHand());
        if (toolType == null) {
            return;
        }

        event.setCancelled(true);
    }

    private void handleFreezeInteraction(Player staff, Player target) {
        FreezeManager freezeManager = plugin.getFreezeManager();
        if (!freezeManager.isEnabled()) {
            staff.sendMessage(ColorUtils.toComponent(
                    freezeManager.getMessage("FEATURE-DISABLED", "&cThe freeze system is disabled.")
            ));
            return;
        }
        if (!freezeManager.canUse(staff)) {
            staff.sendMessage(ColorUtils.toComponent(
                    freezeManager.getMessage("NO-PERMISSION", "&cYou do not have permission.")
            ));
            return;
        }
        if (freezeManager.isSelfTarget(staff, target)) {
            staff.sendMessage(ColorUtils.toComponent(
                    freezeManager.getMessage("SELF-TARGET", "&cYou cannot freeze yourself.")
            ));
            return;
        }

        FreezeManager.FreezeToggleResult result;
        if (freezeManager.hasActiveFreeze(target.getUniqueId())) {
            result = freezeManager.unfreeze(staff, target.getUniqueId());
        } else {
            if (!freezeManager.canFreeze(staff, target)) {
                staff.sendMessage(ColorUtils.toComponent(
                        freezeManager.getMessage("TARGET-EXEMPT", "&cYou cannot freeze that player.")
                ));
                return;
            }
            result = freezeManager.freeze(staff, target);
        }

        if (result != null) {
            staff.sendMessage(ColorUtils.toComponent(freezeManager.buildToggleMessage(result)));
        }
    }

    private boolean touchesStaffTool(Player player, InventoryClickEvent event) {
        StaffModeManager manager = plugin.getStaffModeManager();
        if (manager.isStaffTool(event.getCurrentItem()) || manager.isStaffTool(event.getCursor())) {
            return true;
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return manager.isStaffTool(player.getInventory().getItemInOffHand());
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0 || hotbarButton > 8) {
            return false;
        }
        return manager.isStaffTool(player.getInventory().getItem(hotbarButton));
    }

    private boolean touchesStaffTool(InventoryDragEvent event) {
        StaffModeManager manager = plugin.getStaffModeManager();
        if (manager.isStaffTool(event.getOldCursor())) {
            return true;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (manager.isStaffTool(event.getView().getItem(rawSlot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnInteractCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastInteractTimes.get(player.getUniqueId());
        if (last != null && (now - last) < INTERACT_COOLDOWN_MS) {
            return true;
        }
        lastInteractTimes.put(player.getUniqueId(), now);
        return false;
    }

    private Player resolveDamager(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        return null;
    }
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) {
            return;
        }

        String[] parts = raw.substring(1).split(" ", 2);
        String label = parts[0];

        if (plugin.getStaffModeManager().isCommandBlocked(player, label)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getStaffModeManager().getMessage(
                            "COMMAND-BLOCKED",
                            "&cYou cannot use this command in staff mode."),
                    player));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        StaffModeManager manager = plugin.getStaffModeManager();
        if (!manager.isInStaffMode(player.getUniqueId())) {
            return;
        }

        // اگه پرمیشن bypass داره، اجازه بده
        if (manager.canUseCreativeItems(player)) {
            return;
        }

        // اگه در حال جابه‌جا کردن یه staff tool هست، اجازه بده
        ItemStack newCursor = event.getCursor();
        if (newCursor != null && !newCursor.getType().isAir() && manager.isStaffTool(newCursor)) {
            return;
        }

        // اگه نتیجه‌ی کلیک اینه که یه آیتم غیر از staff tool در cursor قرار می‌گیره → بلاک
        if (newCursor != null && !newCursor.getType().isAir() && !manager.isStaffTool(newCursor)) {
            event.setCancelled(true);
            manager.sendToolLockedMessage(player);
            return;
        }

        // Middle-click (pick-block) روی یه اسلات که آیتم غیر staff داره → بلاک
        if (event.getClick() == ClickType.MIDDLE) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir() && !manager.isStaffTool(current)) {
                event.setCancelled(true);
                manager.sendToolLockedMessage(player);
            }
        }
    }
}
