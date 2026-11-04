package com.bx.ultimateDonutSmp.managers;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import java.util.List;

public class RTPZoneHologramManager {

    private static final String HOLOGRAM_NAME = "ultimate_donutsmp_rtp_zone";

    private final UltimateDonutSmp plugin;


    public RTPZoneHologramManager(UltimateDonutSmp plugin) {

        this.plugin = plugin;
    }

    public void showCountdown(int countdown) {

        createOrUpdate(createCountdownLines(countdown));
    }

    public void showTeleporting() {

        createOrUpdate(createTeleportingLines());
    }

    private void createOrUpdate(List<String> lines) {

        if (!isFancyHologramsAvailable()) {
            return;
        }

        if (plugin.getRtpZoneManager() == null || !plugin.getRtpZoneManager().isEnabled()) {

            remove();

            return;
        }

        Location location = getHologramLocation();

        if (location == null || location.getWorld() == null) {
            return;
        }

        Location safeLocation = location.clone();

        plugin.getSpigotScheduler().runRegion(safeLocation, () -> {

            try {
                // از همان ارتفاع وسط Cuboid استفاده کن
                Location hologramLocation = safeLocation.clone();

                HologramManager manager =
                        FancyHologramsPlugin.get().getHologramManager();

                Hologram hologram =
                        manager.getHologram(HOLOGRAM_NAME).orElse(null);

                if (hologram == null) {

                    TextHologramData data =
                            new TextHologramData(HOLOGRAM_NAME, hologramLocation);

                    configure(data);
                    data.setText(lines);

                    Hologram newHologram = manager.create(data);
                    manager.addHologram(newHologram);
                    return;
                }

                if (!(hologram.getData() instanceof TextHologramData data)) {

                    manager.getHologram(HOLOGRAM_NAME)
                            .ifPresent(manager::removeHologram);

                    TextHologramData newData =
                            new TextHologramData(HOLOGRAM_NAME, hologramLocation);

                    configure(newData);
                    newData.setText(lines);

                    Hologram newHologram = manager.create(newData);
                    manager.addHologram(newHologram);
                    return;
                }

                data.setLocation(hologramLocation);
                data.setText(lines);
                hologram.forceUpdate();

            } catch (Exception exception) {
                plugin.getLogger().warning(
                        "Could not create/update RTP Zone hologram: "
                                + exception.getMessage()
                );
            }
        });
    }

    private void configure(TextHologramData data) {

        data.setBillboard(Display.Billboard.CENTER);

        data.setTextAlignment(TextDisplay.TextAlignment.CENTER);

        data.setScale(new Vector3f(2f, 2f, 2f));

        data.setBackground(Color.fromARGB(0, 0, 0, 0));

        data.setVisibilityDistance(100);

        data.setSeeThrough(true);

        data.setTextShadow(true);

        data.setPersistent(false);
    }


    private List<String> createCountdownLines(int countdown) {

        return List.of(
                "<#bd0203><bold>RTP ZONE</bold></#bd0203>",
                "<#bd0203><bold>" + Math.max(0, countdown) + "s</bold></#bd0203>",
                "<gray>Walk in to teleport in a group randomly.</gray>",
                "",
                "<#bd0203><bold>⚠ Warning ⚠</bold></#bd0203>",
                "<gray>No Keep Inventory</gray>",
                "",
                "<yellow>↓ <bold>WALK</bold> in ↓<yellow>"
        );
    }

    private List<String> createTeleportingLines() {

        return List.of(
                "<#bd0203><bold>RTP ZONE</bold></#bd0203>",
                "<#bd0203><bold>Teleporting...</bold></#bd0203>",
                "<gray>Walk in to teleport in a group randomly.</gray>",
                "",
                "<#bd0203><bold>⚠ Warning ⚠</bold></#bd0203>",
                "<gray>No Keep Inventory</gray>",
                "",
                "<yellow>↓ <bold>WALK</bold> in ↓<yellow>"
        );
    }


    private Location getHologramLocation() {
        if (plugin.getRtpZoneManager() == null) {
            return null;
        }

        String cuboidName = plugin.getRtpZoneManager().getCuboidName();

        if (cuboidName == null || cuboidName.isBlank()) {
            return null;
        }

        CuboidManager.Cuboid cuboid =
                plugin.getCuboidManager().getCuboid(cuboidName);

        if (cuboid == null) {
            return null;
        }

        World world = Bukkit.getWorld(cuboid.world());

        if (world == null) {
            return null;
        }

        double centerX = (cuboid.x1() + cuboid.x2()) / 2.0;
        double centerY = (cuboid.y1() + cuboid.y2()) / 2.0;
        double centerZ = (cuboid.z1() + cuboid.z2()) / 2.0;

        return new Location(
                world,
                centerX,
                centerY,
                centerZ
        );
    }
    public void remove() {

        if (!isFancyHologramsAvailable()) {
            return;
        }

        Location location = getHologramLocation();

        if (location == null || location.getWorld() == null) {

            return;
        }

        Location safeLocation = location.clone();

        plugin.getSpigotScheduler().runRegion(safeLocation, () -> {

            try {

                HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

                manager.getHologram(HOLOGRAM_NAME).ifPresent(manager::removeHologram);

            } catch (Exception exception) {

                plugin.getLogger().warning("Could not remove RTP Zone hologram: " + exception.getMessage());
            }
        });
    }

    public boolean exists() {

        if (!isFancyHologramsAvailable()) {
            return false;
        }

        try {

            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

            return manager.getHologram(HOLOGRAM_NAME).isPresent();

        } catch (Exception exception) {

            return false;
        }
    }

    private boolean isFancyHologramsAvailable() {

        return plugin.getServer().getPluginManager().isPluginEnabled("FancyHolograms");
    }
}