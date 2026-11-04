package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /broadcast &lt;message&gt;
 *
 * Cooldown is driven purely by permissions:
 *
 *   ultimatedonutsmp.broadcast             -> broadcast access, NO cooldown (any time)
 *   ultimatedonutsmp.broadcast.&lt;N&gt;         -> broadcast access with an N-minute cooldown
 *   ultimatedonutsmp.command.broadcast.media -> shows [MEDIA] prefix instead of [BROADCAST]
 *
 * Any positive integer works (5, 15, 30, 32, 54, 120, ...).
 * If a player holds several numbered permissions, the smallest number wins.
 * If they also hold the bare permission (no number), the cooldown is removed entirely.
 */
public class BroadcastCommand implements CommandExecutor, TabCompleter {

    /** Prefix WITH the trailing dot, used for the numbered variants. */
    private static final String PERM_NUMBERED_PREFIX = "ultimatedonutsmp.broadcast.";
    /** The bare permission, no trailing dot. Grants access with no cooldown. */
    private static final String PERM_BARE = "ultimatedonutsmp.broadcast";
    /** Media badge permission — swaps the [BROADCAST] prefix for [MEDIA] with a cyan colour. */
    private static final String PERM_MEDIA = "ultimatedonutsmp.command.broadcast.media";

    /** Returned by {@link #resolveCooldownMinutes(Player)} when the player has no permission. */
    private static final int NO_PERMISSION = -1;

    private final UltimateDonutSmp plugin;
    private final Map<UUID, Long> lastBroadcast = new ConcurrentHashMap<>();

    public BroadcastCommand(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault(
                            "BROADCAST.USAGE",
                            "&cUsage: /" + label + " <message>"
                    )
            ));
            return true;
        }

        String message = String.join(" ", args);

        // Console / command blocks bypass permission + cooldown checks.
        if (!(sender instanceof Player player)) {
            broadcast(message, "Console", false);
            return true;
        }

        int cooldownMinutes = resolveCooldownMinutes(player);
        if (cooldownMinutes == NO_PERMISSION) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault(
                            "BROADCAST.NO-PERMISSION",
                            "&cYou do not have permission to broadcast."
                    ),
                    player
            ));
            return true;
        }

        // cooldownMinutes == 0 -> bare permission, skip the cooldown check entirely.
        if (cooldownMinutes > 0) {
            long cooldownMs = cooldownMinutes * 60_000L;
            long now = System.currentTimeMillis();
            long last = lastBroadcast.getOrDefault(player.getUniqueId(), 0L);
            long remaining = cooldownMs - (now - last);

            if (remaining > 0) {
                long remainingSeconds = (remaining + 999L) / 1000L;
                // includeDays = true -> days, hours, minutes, seconds
                String formatted = plugin.getLanguageManager().formatDuration(remainingSeconds, true);
                player.sendMessage(ColorUtils.toComponent(
                        plugin.getConfigManager().getMessageOrDefault(
                                "BROADCAST.COOLDOWN",
                                "&cPlease wait &e%time% &cbefore broadcasting again.",
                                "%time%", formatted
                        ),
                        player
                ));
                return true;
            }
        }

        lastBroadcast.put(player.getUniqueId(), System.currentTimeMillis());

        boolean isMedia = player.hasPermission(PERM_MEDIA);
        broadcast(message, plugin.getHideManager().publicName(player), isMedia);
        return true;
    }

    private void broadcast(String message, String senderName, boolean isMedia) {
        String format;
        if (isMedia) {
            format = plugin.getConfigManager().getMessageOrDefault(
                    "BROADCAST.MEDIA-FORMAT",
                    "&#37BFF9&l[MEDIA] &r&f%message%"
            );
        } else {
            format = plugin.getConfigManager().getMessageOrDefault(
                    "BROADCAST.FORMAT",
                    "&6&l[BROADCAST] &r&f%message%"
            );
        }

        String rendered = format
                .replace("%message%", message)
                .replace("%sender%", senderName);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(ColorUtils.toComponent(rendered, online));
            try {
                online.playSound(online.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            } catch (Throwable ignored) {
                // Never let a missing sound take down the broadcast.
            }
        }

        Bukkit.getConsoleSender().sendMessage(ColorUtils.toComponent(rendered));
    }

    /**
     * Resolves the effective broadcast cooldown for a player.
     *
     * @return {@link #NO_PERMISSION} when the player cannot broadcast at all,
     *         {@code 0} when they hold the bare permission (no cooldown),
     *         otherwise the smallest numbered cooldown in minutes.
     */
    private int resolveCooldownMinutes(Player player) {
        boolean hasBare = false;
        int min = Integer.MAX_VALUE;

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String perm = info.getPermission().toLowerCase(Locale.ROOT);

            if (perm.equals(PERM_BARE)) {
                hasBare = true;
                continue;
            }

            if (!perm.startsWith(PERM_NUMBERED_PREFIX)) {
                continue;
            }

            String tail = perm.substring(PERM_NUMBERED_PREFIX.length());
            try {
                int value = Integer.parseInt(tail);
                if (value > 0 && value < min) {
                    min = value;
                }
            } catch (NumberFormatException ignored) {
                // Something like ultimatedonutsmp.broadcast.* or .reload -> ignore.
            }
        }

        // Catch the case where getEffectivePermissions didn't expand the bare wildcard.
        if (!hasBare && player.hasPermission(PERM_BARE)) {
            hasBare = true;
        }

        if (hasBare) {
            return 0;
        }
        return min == Integer.MAX_VALUE ? NO_PERMISSION : min;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return Collections.emptyList();
    }
}