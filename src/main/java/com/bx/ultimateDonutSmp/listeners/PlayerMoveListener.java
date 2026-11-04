package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.PermissionUtils;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

public class PlayerMoveListener implements Listener {

    private final UltimateDonutSmp plugin;

    public PlayerMoveListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()
                || event.getFrom().getYaw() != event.getTo().getYaw()
                || event.getFrom().getPitch() != event.getTo().getPitch()) {
            plugin.getAFKManager().recordMovement(player.getUniqueId());
        }

        boolean changedWorld = !event.getFrom().getWorld().equals(event.getTo().getWorld());
        boolean changedBlockPosition = changedWorld
                || event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();

        // Enforce the world allow-list even when a cross-world teleport keeps the same coordinates.
        if (player.getAllowFlight()
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                && !isWorldAllowed(player, event.getTo().getWorld())) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.sendMessage(ColorUtils.toComponent(getWorldNotAllowedMessage(), player));
        }

        // Only care about block-level movement for the remaining flight checks and cuboids.
        if (!changedBlockPosition) {
            return;
        }

        // Check area/combat flight restrictions for player-fly.
        if (player.getAllowFlight()
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {

            if (plugin.getConfigManager().getConfig().getBoolean("FLY-SYSTEM.AUTO-DISABLE-OUTSIDE", true)) {
                if (!PermissionUtils.has(player, "ultimatedonutsmp.staff.fly")) {
                    String playerFlyPerm = plugin.getConfigManager().getConfig()
                            .getString("FLY-SYSTEM.PLAYER-FLY-PERMISSION", "ultimatedonutsmp.player.fly");

                    if (PermissionUtils.has(player, playerFlyPerm)) {
                        boolean inSpawn = plugin.getAFKManager().isInSpawnCuboid(player);
                        boolean inCuboid = false;
                        for (String name : plugin.getCuboidManager().getCuboidNames()) {
                            if (plugin.getCuboidManager().isInCuboid(player, name)) {
                                inCuboid = true;
                                break;
                            }
                        }

                        boolean inCombat = plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId());

                        if ((!inSpawn && !inCuboid) || inCombat) {
                            player.setFlying(false);
                            player.setAllowFlight(false);
                            String msg = plugin.getConfigManager().getMessageOrDefault(
                                    "FLY.PLAYER_DISABLED",
                                    "&c✗ &7Flight deactivated because you left the allowed area or entered combat."
                            );
                            player.sendMessage(ColorUtils.toComponent(msg));
                        }
                    } else {
                        // Player doesn't have the player flight permission, disable flight
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                }
            }
        }

        // Reset AFK timer
        plugin.getAFKManager().recordMovement(player.getUniqueId());
        plugin.getShardManager().recordMovement(
                player.getUniqueId(),
                event.getFrom(),
                event.getTo(),
                event instanceof PlayerTeleportEvent
        );

        // Check pending teleport
        if (plugin.getTeleportManager().hasPending(player.getUniqueId())) {
            plugin.getTeleportManager().checkMovement(player);
        }
    }

    private boolean isWorldAllowed(Player player, World world) {
        List<String> allowedWorlds = plugin.getConfigManager().getConfig()
                .getStringList("FLY-SYSTEM.ALLOWED-WORLDS");
        if (allowedWorlds.isEmpty()) {
            return true;
        }

        boolean staffBypass = plugin.getConfigManager().getConfig()
                .getBoolean("FLY-SYSTEM.STAFF-BYPASS-WORLD-WHITELIST", true);
        if (staffBypass && PermissionUtils.has(player, "ultimatedonutsmp.staff.fly")) {
            return true;
        }
        if (world == null) {
            return false;
        }

        String worldName = world.getName();
        return allowedWorlds.stream()
                .filter(name -> name != null && !name.isBlank())
                .anyMatch(name -> name.trim().equalsIgnoreCase(worldName));
    }

    private String getWorldNotAllowedMessage() {
        return plugin.getConfigManager().getConfig().getString(
                "FLY-SYSTEM.WORLD-NOT-ALLOWED-MESSAGE",
                "&cYou cannot use /fly in this world."
        );
    }
}
