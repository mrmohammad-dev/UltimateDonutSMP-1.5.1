package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class MediaJoinListener implements Listener {

    private final UltimateDonutSmp plugin;

    public MediaJoinListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        var config = plugin.getConfig();
        if (!config.getBoolean("media-join.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (config.getBoolean("media-join.ignore-ops", true) && player.isOp()) {
            return;
        }

        for (String bypassPerm : config.getStringList("media-join.bypass-permissions")) {
            if (bypassPerm != null && !bypassPerm.isEmpty() && player.hasPermission(bypassPerm)) {
                return;
            }
        }
        ConfigurationSection group = resolveGroup(player);
        if (group == null) {
            return;
        }

        long delayTicks = Math.max(0L, config.getLong("media-join.delay-ticks", 5L));
        if (delayTicks <= 0L) {
            announce(player, group);
            return;
        }

        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                announce(player, group);
            }
        }, delayTicks);
    }

    /**
     * بالاترین گروهی که بازیکن داره رو برمی‌گردونه.
     * ترتیب چک: media-plus اول، media دوم. اگه هیچ‌کدوم رو نداشت null.
     */
    private ConfigurationSection resolveGroup(Player player) {
        var config = plugin.getConfig();

        ConfigurationSection plus = config.getConfigurationSection("media-join.groups.media-plus");
        if (plus != null) {
            String perm = plus.getString("permission", "ultimatedonutsmp.media.plus");
            if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                return plus;
            }
        }

        ConfigurationSection media = config.getConfigurationSection("media-join.groups.media");
        if (media != null) {
            String perm = media.getString("permission", "ultimatedonutsmp.media");
            if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                return media;
            }
        }

        return null;
    }

    private void announce(Player mediaPlayer, ConfigurationSection group) {
        // پیام‌ها: هر خط یک پیام جدا
        List<String> lines = group.getStringList("message");
        if (lines.isEmpty()) {
            String single = group.getString("message");
            if (single != null) {
                lines = List.of(single);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            for (String raw : lines) {
                String line = ChatColor.translateAlternateColorCodes(
                        '&',
                        raw.replace("%player%", mediaPlayer.getName())
                );
                online.sendMessage(line);
            }
        }

        // ساند
        String soundName = group.getString("sound.name", "ENTITY_PLAYER_LEVELUP");
        float volume = (float) group.getDouble("sound.volume", 1.0D);
        float pitch = (float) group.getDouble("sound.pitch", 1.2D);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            sound = Sound.ENTITY_PLAYER_LEVELUP;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), sound, volume, pitch);
        }
    }
}