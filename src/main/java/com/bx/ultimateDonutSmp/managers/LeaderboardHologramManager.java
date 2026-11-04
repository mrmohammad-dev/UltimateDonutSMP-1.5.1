package com.bx.ultimateDonutSmp.managers;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.models.PlayerData;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

public class LeaderboardHologramManager {

    private static final String HOLOGRAM_PREFIX = "ultimate_leaderboard_";

    private final UltimateDonutSmp plugin;

    public LeaderboardHologramManager(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    public void create(Location location, LeaderboardManager.LeaderboardType type) {

        if (location == null || location.getWorld() == null || type == null) {

            return;
        }

        Location safeLocation = location.clone();

        String hologramName = getHologramName(type);

        plugin.getSpigotScheduler().runRegion(safeLocation, () -> {

            try {

                HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

                /*
                 * Remove old hologram.
                 */
                removeExisting(manager, hologramName);

                /*
                 * Save location.
                 */
                saveLocation(type, safeLocation);

                /*
                 * Create hologram data.
                 */
                TextHologramData data = new TextHologramData(hologramName, safeLocation);

                /*
                 * Display settings.
                 */
                data.setBillboard(Display.Billboard.CENTER);

                data.setTextAlignment(TextDisplay.TextAlignment.CENTER);

                data.setScale(new Vector3f(1.5f, 1.5f, 1.5f));

                data.setBackground(Color.fromARGB(0, 0, 0, 0));

                data.setVisibilityDistance(100);

                data.setSeeThrough(true);

                data.setTextShadow(true);

                data.setPersistent(false);

                Hologram hologram = manager.create(data);

                manager.addHologram(hologram);

                update(hologram, type);

            } catch (Exception exception) {

                plugin.getLogger().warning("Could not create leaderboard hologram: " + exception.getMessage());
            }
        });
    }

    public void create(Player player, LeaderboardManager.LeaderboardType type) {

        if (player == null || !player.isOnline() || type == null) {

            return;
        }

        create(player.getLocation().clone(), type);
    }

    public void update(Hologram hologram, LeaderboardManager.LeaderboardType type) {

        if (hologram == null || type == null) {

            return;
        }

        if (!(hologram.getData() instanceof TextHologramData data)) {

            return;
        }

        List<LeaderboardManager.LeaderboardEntry> entries = plugin.getLeaderboardManager().getEntries(type, 0, 10);

        StringBuilder text = new StringBuilder();

        text.append("&e&lTOP ").append(plugin.getLeaderboardManager().getDisplayName(type)).append("\n\n");

        if (entries.isEmpty()) {

            text.append("&7No data available.");

        } else {

            for (LeaderboardManager.LeaderboardEntry entry : entries) {

                PlayerData playerData = entry.playerData();

                if (playerData == null) {
                    continue;
                }

                String username = playerData.getUsername();

                if (username == null || username.isBlank()) {

                    username = "Unknown";
                }

                String value = plugin.getLeaderboardManager().formatValue(type, playerData, true, true);

                text.append("&e#").append(entry.position()).append(" &f").append(username).append(" &7- &a").append(value).append("\n");
            }
        }

        List<String> lines = List.of(text.toString().split("\\n", -1));

        data.setText(lines);

        hologram.forceUpdate();
    }

    public void updateAll() {

        HologramManager manager;

        try {

            manager = FancyHologramsPlugin.get().getHologramManager();

        } catch (Exception exception) {

            plugin.getLogger().warning("Could not access FancyHolograms: " + exception.getMessage());

            return;
        }

        for (LeaderboardManager.LeaderboardType type : LeaderboardManager.LeaderboardType.values()) {

            String name = getHologramName(type);

            Optional<Hologram> optional = manager.getHologram(name);

            if (optional.isEmpty()) {
                continue;
            }

            Hologram hologram = optional.get();

            Location location = hologram.getData().getLocation();

            if (location == null || location.getWorld() == null) {

                continue;
            }

            Location safeLocation = location.clone();

            plugin.getSpigotScheduler().runRegion(safeLocation, () -> update(hologram, type));
        }
    }

    public void remove(LeaderboardManager.LeaderboardType type) {

        if (type == null) {
            return;
        }

        String hologramName = getHologramName(type);

        try {

            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

            removeExisting(manager, hologramName);

        } catch (Exception exception) {

            plugin.getLogger().warning("Could not remove leaderboard hologram: " + exception.getMessage());
        }

        removeLocation(type);
    }

    public void removeAll() {

        for (LeaderboardManager.LeaderboardType type : LeaderboardManager.LeaderboardType.values()) {

            remove(type);
        }
    }

    public String getHologramName(LeaderboardManager.LeaderboardType type) {

        return HOLOGRAM_PREFIX + type.getConfigKey();
    }


    public boolean exists(LeaderboardManager.LeaderboardType type) {

        if (type == null) {
            return false;
        }

        try {

            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

            return manager.getHologram(getHologramName(type)).isPresent();

        } catch (Exception exception) {

            return false;
        }
    }

    private void removeExisting(HologramManager manager, String name) {

        if (manager == null || name == null) {

            return;
        }

        try {

            manager.getHologram(name).ifPresent(manager::removeHologram);

        } catch (Exception exception) {

            plugin.getLogger().warning("Could not remove existing leaderboard hologram: " + exception.getMessage());
        }
    }

    private void saveLocation(LeaderboardManager.LeaderboardType type, Location location) {

        if (type == null || location == null || location.getWorld() == null) {

            return;
        }

        String path = "LEADERBOARD-HOLOGRAMS." + type.getConfigKey();

        plugin.getConfigManager().getConfig().set(path + ".ENABLED", true);

        plugin.getConfigManager().getConfig().set(path + ".WORLD", location.getWorld().getName());

        plugin.getConfigManager().getConfig().set(path + ".X", location.getX());

        plugin.getConfigManager().getConfig().set(path + ".Y", location.getY());

        plugin.getConfigManager().getConfig().set(path + ".Z", location.getZ());

        plugin.getConfigManager().saveConfig();
    }

    private void removeLocation(LeaderboardManager.LeaderboardType type) {

        if (type == null) {
            return;
        }

        plugin.getConfigManager().getConfig().set("LEADERBOARD-HOLOGRAMS." + type.getConfigKey(), null);

        plugin.getConfigManager().saveConfig();
    }


    public void loadAll() {

        for (LeaderboardManager.LeaderboardType type : LeaderboardManager.LeaderboardType.values()) {

            String path = "LEADERBOARD-HOLOGRAMS." + type.getConfigKey();

            boolean enabled = plugin.getConfigManager().getConfig().getBoolean(path + ".ENABLED", false);

            if (!enabled) {
                continue;
            }

            String worldName = plugin.getConfigManager().getConfig().getString(path + ".WORLD");

            if (worldName == null || worldName.isBlank()) {

                continue;
            }

            World world = Bukkit.getWorld(worldName);

            if (world == null) {

                plugin.getLogger().warning("World '" + worldName + "' for leaderboard " + type.getConfigKey() + " was not found.");

                continue;
            }

            double x = plugin.getConfigManager().getConfig().getDouble(path + ".X");

            double y = plugin.getConfigManager().getConfig().getDouble(path + ".Y");

            double z = plugin.getConfigManager().getConfig().getDouble(path + ".Z");

            Location location = new Location(world, x, y, z);

            create(location, type);
        }
    }
}