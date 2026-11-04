package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.utils.PermissionUtils;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class FlyCommand implements CommandExecutor {

    private static final String PERMISSION = "ultimatedonutsmp.staff.fly";

    private final UltimateDonutSmp plugin;

    public FlyCommand(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " [player]"));
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " <player>"));
                return true;
            }

            boolean isStaff = PermissionUtils.has(player, PERMISSION);
            String playerFlyPerm = plugin.getConfigManager().getConfig()
                    .getString("FLY-SYSTEM.PLAYER-FLY-PERMISSION", "ultimatedonutsmp.player.fly");
            boolean isPlayerFly = PermissionUtils.has(player, playerFlyPerm);

            if (!isStaff && !isPlayerFly) {
                player.sendMessage(ColorUtils.toComponent(
                        plugin.getConfigManager().getMessageOrDefault("STAFF.NO_PERMISSION_OTHERS", "&cYou do not have permission.")
                ));
                return true;
            }

            if (!isStaff) {
                // Check combat
                if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getConfigManager().getMessageOrDefault("FLY.PLAYER_IN_COMBAT", "&cYou cannot fly while in combat.")
                    ));
                    return true;
                }

                // Check location (spawn or cuboids)
                boolean inSpawn = plugin.getAFKManager().isInSpawnCuboid(player);
                boolean inCuboid = false;
                for (String name : plugin.getCuboidManager().getCuboidNames()) {
                    if (plugin.getCuboidManager().isInCuboid(player, name)) {
                        inCuboid = true;
                        break;
                    }
                }

                if (!inSpawn && !inCuboid) {
                    player.sendMessage(ColorUtils.toComponent(
                            plugin.getConfigManager().getMessageOrDefault("FLY.PLAYER_RESTRICTED", "&cYou can only use flight in spawn or cuboids.")
                    ));
                    return true;
                }
            }

            target = player;
        } else {
            // Target player specified - staff only
            if (sender instanceof Player player && !PermissionUtils.has(player, PERMISSION)) {
                player.sendMessage(ColorUtils.toComponent(
                        plugin.getConfigManager().getMessageOrDefault("STAFF.NO_PERMISSION_OTHERS", "&cYou do not have permission.")
                ));
                return true;
            }

            target = findOnlinePlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.toComponent("&cPlayer not online."));
                return true;
            }
        }

        Player selectedTarget = target;
        boolean targetIsSender = sender instanceof Player senderPlayer
                && senderPlayer.getUniqueId().equals(selectedTarget.getUniqueId());

        // On Folia, all Player state reads and mutations must run on the target's EntityScheduler.
        plugin.getSpigotScheduler().runEntity(selectedTarget, () -> {
            if (!selectedTarget.isOnline()) {
                return;
            }

            boolean currentlyEnabled = isFlightEnabled(selectedTarget);
            if (!currentlyEnabled && !isWorldAllowed(selectedTarget, selectedTarget.getWorld())) {
                selectedTarget.sendMessage(ColorUtils.toComponent(getWorldNotAllowedMessage(), selectedTarget));
                notifySender(sender, selectedTarget, false, true, targetIsSender);
                return;
            }

            boolean enabled = toggleFlight(selectedTarget);
            String path = enabled ? "FLY.ENABLED" : "FLY.DISABLED";
            String fallback = enabled ? "&aflight mode activated" : "&cflight mode deactivated";
            String targetMessage = plugin.getConfigManager().getMessageOrDefault(path, fallback);
            selectedTarget.sendMessage(ColorUtils.toComponent(targetMessage, selectedTarget));
            notifySender(sender, selectedTarget, enabled, false, targetIsSender);
        });
        return true;
    }

    private boolean isWorldAllowed(Player target, World world) {
        List<String> allowedWorlds = plugin.getConfigManager().getConfig()
                .getStringList("FLY-SYSTEM.ALLOWED-WORLDS");
        if (allowedWorlds.isEmpty()) {
            return true;
        }

        boolean staffBypass = plugin.getConfigManager().getConfig()
                .getBoolean("FLY-SYSTEM.STAFF-BYPASS-WORLD-WHITELIST", true);
        if (staffBypass && PermissionUtils.has(target, PERMISSION)) {
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

    private void notifySender(
            org.bukkit.command.CommandSender sender,
            Player target,
            boolean enabled,
            boolean denied,
            boolean targetIsSender
    ) {
        if (targetIsSender) {
            return;
        }

        String message = denied
                ? "&cFlight is not allowed for &e" + target.getName() + " &cin this world."
                : "&7Flight for &e" + target.getName() + " &7was "
                    + (enabled ? "&aEnabled" : "&cDisabled") + "&7.";

        if (sender instanceof Player senderPlayer) {
            plugin.getSpigotScheduler().runEntity(senderPlayer, () -> {
                if (senderPlayer.isOnline()) {
                    senderPlayer.sendMessage(ColorUtils.toComponent(message));
                }
            });
        } else {
            sender.sendMessage(ColorUtils.toComponent(message));
        }
    }

    private boolean toggleFlight(Player target) {
        boolean currentlyEnabled = isFlightEnabled(target);
        boolean nextEnabled = !currentlyEnabled;
        GameMode gameMode = target.getGameMode();
        boolean nativeFlightMode = gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;

        if (nextEnabled) {
            target.setAllowFlight(true);
            target.setFlying(true);
            return true;
        }

        target.setFlying(false);
        if (!nativeFlightMode) {
            target.setAllowFlight(false);
        }
        return false;
    }

    private boolean isFlightEnabled(Player target) {
        if (target == null) {
            return false;
        }

        GameMode gameMode = target.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            return target.isFlying();
        }
        return target.getAllowFlight() || target.isFlying();
    }

    private Player findOnlinePlayer(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }

        String expected = input.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).equals(expected)) {
                return player;
            }
        }
        return null;
    }
}
