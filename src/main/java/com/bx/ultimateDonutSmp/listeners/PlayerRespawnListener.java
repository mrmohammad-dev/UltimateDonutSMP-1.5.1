package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.SpawnManager;
import com.bx.ultimateDonutSmp.models.PlayerData;
import com.bx.ultimateDonutSmp.utils.ItemUtils;
import com.bx.ultimateDonutSmp.utils.NightVisionUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerRespawnListener implements Listener {

    private final UltimateDonutSmp plugin;

    // One token per active death. Concurrent map access is required by Folia regions.
    private final Map<UUID, UUID> pendingRespawns = new ConcurrentHashMap<>();

    public PlayerRespawnListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getSpigotScheduler().isFolia()) {
            return;
        }

        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        String deathWorldName = player.getWorld() != null ? player.getWorld().getName() : null;
        UUID respawnToken = UUID.randomUUID();
        pendingRespawns.put(uuid, respawnToken);

        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline()) {
                pendingRespawns.remove(uuid, respawnToken);
                return;
            }
            startFoliaRespawnCheck(player, deathWorldName, respawnToken, 0);
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRespawns.remove(event.getPlayer().getUniqueId());
    }

    private void startFoliaRespawnCheck(Player player, String deathWorldName, UUID respawnToken, int attempts) {
        UUID uuid = player.getUniqueId();
        if (!respawnToken.equals(pendingRespawns.get(uuid))) {
            // This poll belongs to an older death or the event path already handled this death.
            return;
        }
        if (!player.isOnline() || attempts > 120) {
            pendingRespawns.remove(uuid, respawnToken);
            return;
        }

        if (player.getHealth() > 0.0D && !player.isDead()) {
            if (!claimPendingRespawn(pendingRespawns, uuid, respawnToken)) {
                return;
            }
            // The player is already at the location selected by vanilla. Do not teleport them
            // to an older bed or run the respawn side effects a second time.
            handleFoliaRespawnAfterPoll(player, deathWorldName);
            return;
        }

        plugin.getSpigotScheduler().runEntityLater(player, () ->
                startFoliaRespawnCheck(player, deathWorldName, respawnToken, attempts + 1), 1L);
    }

    static boolean claimPendingRespawn(Map<UUID, UUID> pending, UUID playerId) {
        return pending != null && playerId != null && pending.remove(playerId) != null;
    }

    static boolean claimPendingRespawn(Map<UUID, UUID> pending, UUID playerId, UUID expectedToken) {
        return pending != null && playerId != null && expectedToken != null
                && pending.remove(playerId, expectedToken);
    }

    private void handleFoliaRespawnAfterPoll(Player player, String deathWorldName) {
        boolean isStaffMode = plugin.getStaffModeManager().isInStaffMode(player.getUniqueId());
        NightVisionUtils.restoreIfEnabled(plugin, player);
        if (!isStaffMode) {
            scheduleChainmailKit(plugin, player, 0L);
            startRespawnRtp(player, deathWorldName);
        }
        plugin.getStaffModeManager().handleRespawn(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String deathWorldName = player.getWorld() == null ? null : player.getWorld().getName();

        if (plugin.getSpigotScheduler().isFolia()
                && !claimPendingRespawn(pendingRespawns, player.getUniqueId())) {
            return;
        }

        boolean duelRespawnHandled = plugin.getDuelManager() != null
                && plugin.getDuelManager().consumeRespawn(player, event);
        boolean ffaRespawnHandled = !duelRespawnHandled
                && plugin.getFfaManager() != null
                && plugin.getFfaManager().consumeRespawn(player, event);

        if (duelRespawnHandled || ffaRespawnHandled) {
            plugin.getStaffModeManager().handleRespawn(player);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline()) {
                    NightVisionUtils.restoreIfEnabled(plugin, player);
                }
            }, 2L);
            return;
        }

        Location pendingPearlLocation = plugin.getEnderPearlManager() != null
                ? plugin.getEnderPearlManager().consumePendingTeleport(player.getUniqueId())
                : null;
        // A vanilla Respawn Anchor takes precedence over a delayed pearl destination.
        // Consume and discard the stale pending pearl so it cannot affect a later death.
        Location pearlLocation = shouldUsePendingPearlTeleport(
                event.isAnchorSpawn(), pendingPearlLocation != null
        ) ? pendingPearlLocation : null;
        if (pearlLocation != null) {
            Location finalPearl = pearlLocation.clone();
            event.setRespawnLocation(finalPearl);
            boolean isStaffMode = plugin.getStaffModeManager().isInStaffMode(player.getUniqueId());

            plugin.getSpigotScheduler().runGlobalLater(() -> {
                if (!player.isOnline()) return;
                plugin.getSpigotScheduler().teleport(player, finalPearl).thenAccept(success ->
                        plugin.getSpigotScheduler().runEntity(player, () -> {
                            if (!player.isOnline()) return;
                            NightVisionUtils.restoreIfEnabled(plugin, player);
                            if (!isStaffMode) {
                                scheduleChainmailKit(plugin, player, 0L);
                            }
                        }));
            }, 1L);
            return;
        }

        handleRespawnLogic(player, deathWorldName, event);
    }

    static boolean shouldUsePendingPearlTeleport(boolean anchorSpawn, boolean hasPendingTeleport) {
        return hasPendingTeleport && !anchorSpawn;
    }

    private void handleRespawnLogic(Player player, String deathWorldName, PlayerRespawnEvent event) {
        if (event != null && event.isAnchorSpawn()) {
            // Leave the PlayerRespawnEvent location untouched. Paper/Folia consumes one
            // anchor charge after all listeners only when that location remains unchanged.
            // Do not schedule a second teleport: vanilla already selected the safe anchor spot.
            boolean isStaffMode = plugin.getStaffModeManager().isInStaffMode(player.getUniqueId());
            plugin.getStaffModeManager().handleRespawn(player);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                NightVisionUtils.restoreIfEnabled(plugin, player);
                if (!isStaffMode) {
                    scheduleChainmailKit(plugin, player, 0L);
                }
            }, 1L);
            return;
        }

        boolean respectBed = plugin.getConfigManager().getConfig()
                .getBoolean("SETTINGS.RESPAWN-ON-BED", true);

        Location bedLocation = player.getBedSpawnLocation();
        boolean hasValidBed = bedLocation != null && bedLocation.getWorld() != null;

        boolean isBedOrAnchor = false;
        if (event != null) {
            isBedOrAnchor = isRespawningAtBedOrAnchor(event, player);
        }

        boolean isAnchorSpawn = event != null && event.isAnchorSpawn();
        // The bed preference must not disable a Respawn Anchor explicitly selected by vanilla.
        boolean useBedOrAnchor = isAnchorSpawn || (respectBed && (hasValidBed || isBedOrAnchor));

        if (useBedOrAnchor) {
            Location eventLocation = event == null ? null : event.getRespawnLocation();
            boolean eventSelectedBedOrAnchor = event != null
                    && (event.isBedSpawn() || event.isAnchorSpawn());
            boolean hasUsableEventLocation = eventLocation != null && eventLocation.getWorld() != null;
            Location finalBed;
            if (eventSelectedBedOrAnchor && hasUsableEventLocation) {
                finalBed = eventLocation.clone();
            } else if (hasValidBed) {
                finalBed = bedLocation.clone();
            } else if (hasUsableEventLocation) {
                finalBed = eventLocation.clone();
            } else {
                finalBed = player.getLocation().clone();
            }

            if (event != null) {
                event.setRespawnLocation(finalBed);
            }

            boolean isStaffMode = plugin.getStaffModeManager().isInStaffMode(player.getUniqueId());

            plugin.getSpigotScheduler().runGlobalLater(() -> {
                if (!player.isOnline()) return;

                plugin.getSpigotScheduler().teleport(player, finalBed).thenAccept(success ->
                        plugin.getSpigotScheduler().runEntity(player, () -> {
                            if (!player.isOnline()) return;
                            NightVisionUtils.restoreIfEnabled(plugin, player);
                            if (!isStaffMode) {
                                scheduleChainmailKit(plugin, player, 0L);
                            }
                        }));
            }, 1L);

            plugin.getStaffModeManager().handleRespawn(player);
            return;
        }

        Location respawnLocation = plugin.getSpawnManager()
                .resolveCommandDestination(SpawnManager.AreaType.SPAWN);
        if (respawnLocation == null) {
            respawnLocation = plugin.getSpawnManager().getSpawnLocation();
        }
        if (respawnLocation == null && event != null) {
            respawnLocation = plugin.getSpawnManager().makeSafeDestination(event.getRespawnLocation());
        }
        if (respawnLocation == null) {
            respawnLocation = player.getLocation();
        }

        Location finalSpawn = respawnLocation.clone();
        if (event != null) {
            event.setRespawnLocation(finalSpawn);
        }

        boolean isStaffMode = plugin.getStaffModeManager().isInStaffMode(player.getUniqueId());

        plugin.getSpigotScheduler().runGlobalLater(() -> {
            if (!player.isOnline()) return;

            plugin.getSpigotScheduler().teleport(player, finalSpawn).thenAccept(success ->
                    plugin.getSpigotScheduler().runEntity(player, () -> {
                        if (!player.isOnline()) return;
                        NightVisionUtils.restoreIfEnabled(plugin, player);
                        if (!isStaffMode) {
                            scheduleChainmailKit(plugin, player, 0L);
                        }
                        startRespawnRtp(player, deathWorldName);
                    }));
        }, 1L);

        plugin.getStaffModeManager().handleRespawn(player);
    }

    private void startRespawnRtp(Player player, String deathWorldName) {
        if (plugin.getRespawnRtpManager() == null) {
            return;
        }
        plugin.getRespawnRtpManager().handleRespawn(player, deathWorldName);
    }

    private boolean isRespawningAtBedOrAnchor(PlayerRespawnEvent event, Player player) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return true;
        }
        Location respawnLoc = event.getRespawnLocation();
        Location bedLoc = player.getBedSpawnLocation();
        if (respawnLoc == null || bedLoc == null
                || respawnLoc.getWorld() == null || bedLoc.getWorld() == null) {
            return false;
        }
        return respawnLoc.getWorld().equals(bedLoc.getWorld())
                && respawnLoc.distanceSquared(bedLoc) < 9.0D;
    }

    public static void scheduleChainmailKit(UltimateDonutSmp plugin, Player player, long delayTicks) {
        if (plugin == null || player == null) {
            return;
        }

        boolean chainmailEnabled = plugin.getConfigManager().getConfig()
                .getBoolean("SETTINGS.CHAINMAIL-ON-RESPAWN", true);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        boolean playerEnabled = data == null || data.isChainmailOnRespawnEnabled();
        if (!chainmailEnabled || !playerEnabled) {
            return;
        }

        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                giveChainmailKit(plugin, player);
            }
        }, Math.max(1L, delayTicks));
    }

    private static void giveChainmailKit(UltimateDonutSmp plugin, Player player) {
        List<?> itemList = plugin.getConfigManager().getConfig()
                .getList("SETTINGS.CHAINMAIL-RESPAWN-ITEMS");
        Set<Material> grantedMaterials = new HashSet<>();
        if (itemList != null) {
            for (Object obj : itemList) {
                Material mat = Material.STONE;
                int amount = 1;
                String name = null;

                if (obj instanceof ConfigurationSection section) {
                    mat = ItemUtils.parseMaterial(section.getString("MATERIAL", "STONE"));
                    amount = section.getInt("AMOUNT", 1);
                    name = section.getString("NAME");
                } else if (obj instanceof java.util.Map<?, ?> map) {
                    Object matObj = map.get("MATERIAL");
                    if (matObj != null) {
                        mat = ItemUtils.parseMaterial(matObj.toString());
                    }
                    Object amtObj = map.get("AMOUNT");
                    if (amtObj != null) {
                        try {
                            amount = Integer.parseInt(amtObj.toString());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    Object nameObj = map.get("NAME");
                    if (nameObj != null) {
                        name = nameObj.toString();
                    }
                } else {
                    continue;
                }

                ItemStack item;
                if (name != null) {
                    item = ItemUtils.createItem(mat, name);
                    item.setAmount(amount);
                } else {
                    item = new ItemStack(mat, amount);
                }

                grantedMaterials.add(mat);
                giveRespawnItem(player, item);
            }
        } else {
            ensureDefaultItem(player, grantedMaterials, Material.CHAINMAIL_HELMET);
            ensureDefaultItem(player, grantedMaterials, Material.CHAINMAIL_CHESTPLATE);
            ensureDefaultItem(player, grantedMaterials, Material.CHAINMAIL_LEGGINGS);
            ensureDefaultItem(player, grantedMaterials, Material.CHAINMAIL_BOOTS);
            ensureDefaultItem(player, grantedMaterials, Material.STONE_SWORD);
            ensureDefaultItem(player, grantedMaterials, Material.STONE_PICKAXE);
            ensureDefaultItem(player, grantedMaterials, Material.STONE_AXE);
            ensureDefaultItem(player, grantedMaterials, Material.STONE_SHOVEL);
        }

        player.updateInventory();
    }

    private static void ensureDefaultItem(Player player, Set<Material> grantedMaterials, Material material) {
        if (grantedMaterials.contains(material)) {
            return;
        }
        giveRespawnItem(player, new ItemStack(material));
    }

    private static void giveRespawnItem(Player player, ItemStack item) {
        if (equipArmorIfApplicable(player, item)) {
            return;
        }
        placeInPreferredSlot(player, item);
    }

    private static boolean equipArmorIfApplicable(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        String matName = item.getType().name();

        if (matName.endsWith("_HELMET")) {
            if (isEmpty(inventory.getHelmet())) {
                inventory.setHelmet(item);
            } else {
                inventory.addItem(item);
            }
            return true;
        }
        if (matName.endsWith("_CHESTPLATE")) {
            if (isEmpty(inventory.getChestplate())) {
                inventory.setChestplate(item);
            } else {
                inventory.addItem(item);
            }
            return true;
        }
        if (matName.endsWith("_LEGGINGS")) {
            if (isEmpty(inventory.getLeggings())) {
                inventory.setLeggings(item);
            } else {
                inventory.addItem(item);
            }
            return true;
        }
        if (matName.endsWith("_BOOTS")) {
            if (isEmpty(inventory.getBoots())) {
                inventory.setBoots(item);
            } else {
                inventory.addItem(item);
            }
            return true;
        }
        return false;
    }

    private static void placeInPreferredSlot(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        int preferredSlot = getPreferredHotbarSlot(item.getType());

        if (preferredSlot >= 0 && isEmpty(inventory.getItem(preferredSlot))) {
            inventory.setItem(preferredSlot, item);
            return;
        }
        inventory.addItem(item);
    }

    private static int getPreferredHotbarSlot(Material material) {
        return switch (material) {
            case STONE_SWORD -> 0;
            case STONE_PICKAXE -> 1;
            case STONE_AXE -> 2;
            case STONE_SHOVEL -> 3;
            default -> -1;
        };
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}