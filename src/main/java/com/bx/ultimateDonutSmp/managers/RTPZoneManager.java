package com.bx.ultimateDonutSmp.managers;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.SoundUtils;
import com.bx.ultimateDonutSmp.utils.TitleUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RTPZoneManager {

    private static final int PRELOAD_SECONDS = 10;

    private static final long TELEPORTING_TICKS = 100L;


    private final UltimateDonutSmp plugin;

    private final Set<UUID> pendingTeleports = ConcurrentHashMap.newKeySet();

    private final Set<UUID> playersInZone = ConcurrentHashMap.newKeySet();

    private final Set<UUID> teleportingPlayers = ConcurrentHashMap.newKeySet();

    private final Set<UUID> completedTeleports = ConcurrentHashMap.newKeySet();

    private final Map<UUID, PreparedTeleport> preparedTeleports = new ConcurrentHashMap<>();

    private final Map<UUID, Long> preparedCycleIds = new ConcurrentHashMap<>();

    private boolean enabled;
    private String cuboidName;
    private int countdownSeconds;

    private int titleFadeOutTicks;
    private String titleTemplate;
    private String subtitleTemplate;

    private String cancelledMessage;
    private String failedMessage;
    private String successMessage;

    private String countdownSound;
    private String cancelledSound;

    private RTPManager.SearchSettings searchSettings;

    private int globalCountdown;
    private BukkitTask globalTask;

    private boolean teleporting;

    private BukkitTask teleportingTask;

    private long cycleId;

    private Location preparedBaseLocation;

    private CompletableFuture<Location> preparedBaseLocationFuture;

    private boolean preloadingStarted;

    private String teleportingTitle;
    private String teleportingSubtitle;

    private final RTPZoneHologramManager hologramManager;

    public RTPZoneManager(UltimateDonutSmp plugin) {

        this.plugin = plugin;

        this.hologramManager = new RTPZoneHologramManager(plugin);

        reloadSettings();
    }

    public void reloadSettings() {

        if (globalTask != null) {

            globalTask.cancel();
            globalTask = null;
        }

        if (teleportingTask != null) {

            teleportingTask.cancel();
            teleportingTask = null;
        }

        teleporting = false;

        teleportingPlayers.clear();

        preparedTeleports.clear();

        preparedCycleIds.clear();

        completedTeleports.clear();

        preparedBaseLocation = null;

        preparedBaseLocationFuture = null;

        preloadingStarted = false;

        cycleId++;


        hardClearActiveTitles();

        playersInZone.clear();

        pendingTeleports.clear();

        enabled = plugin.getFeatureManager().isEnabled(FeatureManager.Feature.RTP_ZONE) && plugin.getConfigManager().getConfig().getBoolean("RTP-ZONE.ENABLED", true);

        cuboidName = plugin.getConfigManager().getConfig().getString("RTP-ZONE.CUBOID", "");

        countdownSeconds = Math.max(1, plugin.getConfigManager().getConfig().getInt("RTP-ZONE.EVERY", 30));

        titleFadeOutTicks = normalizeTitleFadeOutTicks(plugin.getConfigManager().getConfig().getInt("RTP-ZONE.TITLE-FADE-OUT-TICKS", 10));

        titleTemplate = plugin.getConfigManager().getConfig().getString("RTP-ZONE.TITLE", "&c&lrtp zone");

        subtitleTemplate = plugin.getConfigManager().getConfig().getString("RTP-ZONE.SUB-TITLE", "&fteleporting in %countdown%");

        teleportingTitle = plugin.getConfigManager().getConfig().getString("RTP-ZONE.TELEPORTING-TITLE", "&e&lTeleporting....");

        teleportingSubtitle = plugin.getConfigManager().getConfig().getString("RTP-ZONE.TELEPORTING-SUB-TITLE", "&fplease wait...");

        cancelledMessage = plugin.getConfigManager().getConfig().getString("RTP-ZONE.CANCELLED-MESSAGE", "&crtp cancelled because you left the zone.");

        failedMessage = plugin.getConfigManager().getConfig().getString("RTP-ZONE.FAILED-MESSAGE", "&ccould not find a safe rtp zone location.");

        successMessage = plugin.getConfigManager().getConfig().getString("RTP-ZONE.SUCCESS-MESSAGE", "");

        countdownSound = plugin.getConfigManager().getSound("RTP-ZONE.COUNTDOWN");

        if (countdownSound == null || countdownSound.isBlank()) {

            countdownSound = plugin.getConfigManager().getSound("TELEPORT.COUNTDOWN");
        }

        cancelledSound = plugin.getConfigManager().getSound("RTP-ZONE.CANCELLED");

        if (cancelledSound == null || cancelledSound.isBlank()) {

            cancelledSound = plugin.getConfigManager().getSound("TELEPORT.CANCELLED");
        }

        searchSettings = plugin.getRtpManager().getZoneSearchSettings();

        globalCountdown = countdownSeconds;

        if (enabled && cuboidName != null && !cuboidName.isBlank()) {


            startGlobalTimer();

            startPreparingPhase();

        }
    }

    private void startGlobalTimer() {

        globalTask = plugin.getSpigotScheduler().runGlobalTimer(() -> {

            if (!enabled || cuboidName == null || cuboidName.isBlank()) {

                if (globalTask != null) {

                    globalTask.cancel();
                    globalTask = null;
                }

                return;
            }

            CuboidManager.Cuboid cuboid = plugin.getCuboidManager().getCuboid(cuboidName);

            if (cuboid == null) {
                hardClearActiveTitles();

                playersInZone.clear();

                teleportingPlayers.clear();

                preparedTeleports.clear();

                if (globalTask != null) {

                    globalTask.cancel();
                    globalTask = null;
                }

                return;
            }

            if (teleporting) {
                return;
            }

            if (!pendingTeleports.isEmpty()) {
                return;
            }

            globalCountdown--;



            if (hologramManager != null) {

                hologramManager.showCountdown(Math.max(0, globalCountdown));
            }

            if (globalCountdown == PRELOAD_SECONDS) {
                startPreloadingPhase(cuboid);
            }

            if (globalCountdown <= 0) {
                startTeleportingPhase(cuboid);

                return;
            }

            for (UUID uuid : playersInZone) {

                if (completedTeleports.contains(uuid)) {
                    continue;
                }

                Player player = plugin.getServer().getPlayer(uuid);

                if (player != null && player.isOnline()) {

                    showCountdown(player, globalCountdown);
                }
            }

        }, 20L, 20L);
    }

    private void startPreparingPhase() {

        cycleId++;

        final long currentCycle = cycleId;

        preparedBaseLocation = null;

        preparedBaseLocationFuture = null;

        preloadingStarted = false;

        preparedTeleports.clear();

        preparedCycleIds.clear();

        completedTeleports.clear();

        if (hologramManager != null) {

            hologramManager.showCountdown(globalCountdown);
        }

        findPreparedBaseLocation(currentCycle);
    }

    private void findPreparedBaseLocation(long currentCycle) {

        if (currentCycle != cycleId) {
            return;
        }

        if (!enabled) {
            return;
        }

        if (preparedBaseLocation != null) {
            return;
        }

        if (preparedBaseLocationFuture != null && !preparedBaseLocationFuture.isDone()) {

            return;
        }

        long startTime = System.currentTimeMillis();
        preparedBaseLocationFuture = plugin.getRtpManager().findSafeLocationAsync(searchSettings);

        preparedBaseLocationFuture.thenAccept(location -> {

            long elapsed = System.currentTimeMillis() - startTime;

            if (currentCycle != cycleId) {
                return;
            }

            if (location != null && location.getWorld() != null) {

                preparedBaseLocation = location;
                return;
            }
            scheduleBaseLocationRetry(currentCycle);

        }).exceptionally(exception -> {

            long elapsed = System.currentTimeMillis() - startTime;

            if (currentCycle != cycleId) {
                return null;
            }

            scheduleBaseLocationRetry(currentCycle);

            return null;
        });
    }

    private void scheduleBaseLocationRetry(long currentCycle) {

        if (currentCycle != cycleId) {
            return;
        }

        if (globalCountdown <= PRELOAD_SECONDS) {
            return;
        }

        plugin.getSpigotScheduler().runGlobalLater(() -> findPreparedBaseLocation(currentCycle), 20L);
    }

    private void startPreloadingPhase(CuboidManager.Cuboid cuboid) {

        if (preloadingStarted) {
            return;
        }

        preloadingStarted = true;

        final long currentCycle = cycleId;

        if (preparedBaseLocation == null || preparedBaseLocation.getWorld() == null) {
            long startTime = System.currentTimeMillis();

            plugin.getRtpManager().findSafeLocationAsync(searchSettings).thenAccept(location -> {

                long elapsed = System.currentTimeMillis() - startTime;

                if (currentCycle != cycleId) {
                    return;
                }

                if (location == null || location.getWorld() == null) {
                    return;
                }

                preparedBaseLocation = location;

                preparePlayerDestinations(cuboid, currentCycle);

            }).exceptionally(exception -> {

                return null;
            });

            return;
        }

        preparePlayerDestinations(cuboid, currentCycle);
    }

    private void preparePlayerDestinations(CuboidManager.Cuboid cuboid, long currentCycle) {

        if (currentCycle != cycleId) {
            return;
        }

        Location baseLocation = preparedBaseLocation;

        if (baseLocation == null || baseLocation.getWorld() == null) {
            return;
        }

        World world = baseLocation.getWorld();

        List<UUID> playersToPrepare = new ArrayList<>();

        for (UUID uuid : playersInZone) {

            if (completedTeleports.contains(uuid)) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {

                playersInZone.remove(uuid);
                continue;
            }

            if (!cuboid.contains(player.getLocation())) {

                handleLeave(player);
                continue;
            }

            playersToPrepare.add(uuid);
        }

        preparedTeleports.clear();

        preparedCycleIds.clear();

        if (playersToPrepare.isEmpty()) {
            return;
        }

        for (UUID uuid : playersToPrepare) {

            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                continue;
            }

            long playerStartTime = System.currentTimeMillis();

            double distance = ThreadLocalRandom.current().nextDouble(5.0, 21.0);

            double angle = ThreadLocalRandom.current().nextDouble(0.0, 2.0 * Math.PI);

            int dx = (int) Math.round(distance * Math.cos(angle));

            int dz = (int) Math.round(distance * Math.sin(angle));

            int newX = baseLocation.getBlockX() + dx;

            int newZ = baseLocation.getBlockZ() + dz;

            int chunkX = newX >> 4;

            int chunkZ = newZ >> 4;

            preparedCycleIds.put(uuid, currentCycle);

            long chunkStartTime = System.currentTimeMillis();

            plugin.getRtpManager().getChunkAtAsync(world, chunkX, chunkZ, true).thenAccept(chunk -> {

                long chunkElapsed = System.currentTimeMillis() - chunkStartTime;

                if (currentCycle != cycleId) {
                    return;
                }

                if (chunk == null) {
                    preparedCycleIds.remove(uuid);
                    return;
                }

                long regionStartTime = System.currentTimeMillis();

                plugin.getSpigotScheduler().runRegion(world, chunkX, chunkZ, () -> {

                    long regionElapsed = System.currentTimeMillis() - regionStartTime;

                    if (currentCycle != cycleId) {
                        return;
                    }

                    if (completedTeleports.contains(uuid)) {
                        return;
                    }

                    Player currentPlayer = plugin.getServer().getPlayer(uuid);

                    if (currentPlayer == null || !currentPlayer.isOnline()) {

                        preparedCycleIds.remove(uuid);

                        return;
                    }

                    CuboidManager.Cuboid currentCuboid = plugin.getCuboidManager().getCuboid(cuboidName);

                    if (currentCuboid == null || !currentCuboid.contains(currentPlayer.getLocation())) {

                        preparedCycleIds.remove(uuid);

                        preparedTeleports.remove(uuid);

                        return;
                    }

                    long groundStartTime = System.currentTimeMillis();

                    try {

                        int groundY = world.getHighestBlockYAt(newX, newZ);

                        long groundElapsed = System.currentTimeMillis() - groundStartTime;

                        Location destination = new Location(world, newX + 0.5, groundY + 1.0, newZ + 0.5);

                        preparedTeleports.put(uuid, new PreparedTeleport(uuid, destination, chunkX, chunkZ, currentCycle));

                        long totalElapsed = System.currentTimeMillis() - playerStartTime;

                    } catch (RuntimeException exception) {

                        long groundElapsed = System.currentTimeMillis() - groundStartTime;

                        preparedCycleIds.remove(uuid);

                        plugin.getLogger().warning("[RTP-ZONE] Failed to prepare RTP destination for " + currentPlayer.getName() + " at [" + newX + ", " + newZ + "]: " + exception.getMessage());
                    }
                });

            }).exceptionally(exception -> {

                long chunkElapsed = System.currentTimeMillis() - chunkStartTime;

                if (currentCycle == cycleId) {

                    preparedCycleIds.remove(uuid);
                }

                return null;
            });
        }
    }

    private void prepareSinglePlayerDestination(Player player, long currentCycle) {

        if (player == null || !player.isOnline()) {

            return;
        }

        if (currentCycle != cycleId) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (completedTeleports.contains(uuid)) {
            return;
        }

        PreparedTeleport existing = preparedTeleports.get(uuid);

        if (existing != null && existing.cycleId() == currentCycle) {

            return;
        }

        if (preparedBaseLocation == null || preparedBaseLocation.getWorld() == null) {
            return;
        }

        World world = preparedBaseLocation.getWorld();

        Location baseLocation = preparedBaseLocation;

        double distance = ThreadLocalRandom.current().nextDouble(5.0, 21.0);

        double angle = ThreadLocalRandom.current().nextDouble(0.0, 2.0 * Math.PI);

        int dx = (int) Math.round(distance * Math.cos(angle));

        int dz = (int) Math.round(distance * Math.sin(angle));

        int newX = baseLocation.getBlockX() + dx;

        int newZ = baseLocation.getBlockZ() + dz;

        int chunkX = newX >> 4;

        int chunkZ = newZ >> 4;

        preparedCycleIds.put(uuid, currentCycle);

        long chunkStartTime = System.currentTimeMillis();

        plugin.getRtpManager().getChunkAtAsync(world, chunkX, chunkZ, true).thenAccept(chunk -> {

            long chunkElapsed = System.currentTimeMillis() - chunkStartTime;

            if (currentCycle != cycleId) {
                return;
            }

            if (completedTeleports.contains(uuid)) {
                return;
            }

            if (chunk == null) {
                preparedCycleIds.remove(uuid);
                return;
            }

            plugin.getSpigotScheduler().runRegion(world, chunkX, chunkZ, () -> {

                if (currentCycle != cycleId) {
                    return;
                }

                if (completedTeleports.contains(uuid)) {
                    return;
                }

                Player currentPlayer = plugin.getServer().getPlayer(uuid);

                if (currentPlayer == null || !currentPlayer.isOnline()) {

                    preparedCycleIds.remove(uuid);

                    return;
                }

                CuboidManager.Cuboid cuboid = plugin.getCuboidManager().getCuboid(cuboidName);

                if (cuboid == null || !cuboid.contains(currentPlayer.getLocation())) {

                    preparedCycleIds.remove(uuid);

                    preparedTeleports.remove(uuid);

                    return;
                }

                try {

                    int groundY = world.getHighestBlockYAt(newX, newZ);

                    Location destination = new Location(world, newX + 0.5, groundY + 1.0, newZ + 0.5);

                    preparedTeleports.put(uuid, new PreparedTeleport(uuid, destination, chunkX, chunkZ, currentCycle));
                } catch (RuntimeException exception) {
                    preparedCycleIds.remove(uuid);

                }
            });

        }).exceptionally(exception -> {

            if (currentCycle == cycleId) {
                preparedCycleIds.remove(uuid);
            }

            return null;
        });
    }

    public void tick(Player player) {

        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (completedTeleports.contains(uuid)) {
            return;
        }

        if (!plugin.getFeatureManager().isEnabled(FeatureManager.Feature.RTP) || !plugin.getFeatureManager().isEnabled(FeatureManager.Feature.RTP_ZONE)) {

            handleLeave(player);
            return;
        }

        if (plugin.getDuelManager() != null) {

            if (plugin.getDuelManager().isInDuel(uuid) || plugin.getDuelManager().isTransitioning(uuid)) {

                handleLeave(player);
                return;
            }
        }

        if (teleporting) {

            if (teleportingPlayers.contains(uuid)) {
                return;
            }

            if (playersInZone.contains(uuid)) {
                return;
            }

            handleLeave(player);

            return;
        }

        if (!enabled || cuboidName == null || cuboidName.isBlank()) {

            handleLeave(player);
            return;
        }

        CuboidManager.Cuboid cuboid = plugin.getCuboidManager().getCuboid(cuboidName);

        if (cuboid == null) {

            handleLeave(player);
            return;
        }

        boolean inZone = cuboid.contains(player.getLocation());

        if (inZone && !playersInZone.contains(uuid)) {

            playersInZone.add(uuid);

            showCountdown(player, globalCountdown);

            if (preloadingStarted && globalCountdown > 0 && globalCountdown <= PRELOAD_SECONDS) {
                prepareSinglePlayerDestination(player, cycleId);
            }

        } else if (!inZone && playersInZone.contains(uuid)) {

            handleLeave(player);
        }
    }

    private void handleLeave(Player player) {

        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (completedTeleports.contains(uuid)) {
            return;
        }

        teleportingPlayers.remove(uuid);

        preparedTeleports.remove(uuid);

        preparedCycleIds.remove(uuid);

        if (playersInZone.remove(uuid)) {

            TitleUtils.clearTitle(player);

            SoundUtils.play(player, cancelledSound);

            if (cancelledMessage != null && !cancelledMessage.isBlank()) {

                player.sendMessage(ColorUtils.toComponent(cancelledMessage));
            }
        }
    }

    public void clearState(Player player) {

        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (completedTeleports.contains(uuid)) {
            return;
        }

        TitleUtils.clearTitle(player);

        playersInZone.remove(uuid);

        teleportingPlayers.remove(uuid);

        preparedTeleports.remove(uuid);

        preparedCycleIds.remove(uuid);
    }

    public void clearState(UUID uuid) {

        if (uuid == null) {
            return;
        }

        if (completedTeleports.contains(uuid)) {
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);

        if (player != null) {

            clearState(player);

        } else {

            playersInZone.remove(uuid);

            teleportingPlayers.remove(uuid);

            preparedTeleports.remove(uuid);

            preparedCycleIds.remove(uuid);
        }
    }

    private void startTeleportingPhase(CuboidManager.Cuboid cuboid) {

        if (teleporting) {
            return;
        }

        final long currentCycle = cycleId;

        teleporting = true;

        if (hologramManager != null) {

            hologramManager.showTeleporting();
        }

        teleportingPlayers.clear();

        List<Player> playersToTeleport = new ArrayList<>();

        for (UUID uuid : playersInZone) {

            if (completedTeleports.contains(uuid)) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {

                continue;
            }

            if (!cuboid.contains(player.getLocation())) {

                continue;
            }

            PreparedTeleport prepared = preparedTeleports.get(uuid);

            if (prepared == null || prepared.cycleId() != currentCycle) {
                continue;
            }

            teleportingPlayers.add(uuid);

            playersToTeleport.add(player);
        }
        for (Player player : playersToTeleport) {

            if (player != null && player.isOnline()) {

                showTeleporting(player);
            }
        }

        teleportPreparedGroup(playersToTeleport, currentCycle);

        long separatorStartTime = System.currentTimeMillis();

        teleportingTask = plugin.getSpigotScheduler().runGlobalLater(() -> {

            teleportingTask = null;

            long elapsed = System.currentTimeMillis() - separatorStartTime;

            teleporting = false;

            teleportingPlayers.clear();

            globalCountdown = countdownSeconds;

            startPreparingPhase();

        }, TELEPORTING_TICKS);
    }

    private void teleportPreparedGroup(Collection<Player> players, long preparedCycle) {

        if (players.isEmpty()) {
            return;
        }

        List<PreparedTeleport> preparedPlayers = new ArrayList<>();

        for (Player player : players) {

            if (player == null) {
                continue;
            }

            UUID uuid = player.getUniqueId();

            if (completedTeleports.contains(uuid)) {
                continue;
            }

            PreparedTeleport prepared = preparedTeleports.get(uuid);

            if (prepared == null) {
                continue;
            }

            preparedPlayers.add(prepared);

            pendingTeleports.add(uuid);
        }

        preparedTeleports.clear();

        preparedCycleIds.clear();

        preparedBaseLocation = null;

        preparedBaseLocationFuture = null;

        preloadingStarted = false;

        if (preparedPlayers.isEmpty()) {
            return;
        }

        for (PreparedTeleport prepared : preparedPlayers) {

            Player player = plugin.getServer().getPlayer(prepared.playerId());

            if (player == null || !player.isOnline()) {

                pendingTeleports.remove(prepared.playerId());

                continue;
            }

            World world = prepared.location().getWorld();

            if (world == null) {

                pendingTeleports.remove(prepared.playerId());

                sendFailedMessage(player);

                continue;
            }

            long regionQueueStart = System.currentTimeMillis();

            plugin.getSpigotScheduler().runRegion(world, prepared.chunkX(), prepared.chunkZ(), () -> {

                long regionQueueElapsed = System.currentTimeMillis() - regionQueueStart;

                if (!player.isOnline()) {

                    pendingTeleports.remove(prepared.playerId());

                    return;
                }

                long entityQueueStart = System.currentTimeMillis();

                plugin.getSpigotScheduler().runEntity(player, () -> {

                    long entityQueueElapsed = System.currentTimeMillis() - entityQueueStart;

                    long teleportStart = System.currentTimeMillis();

                    plugin.getSpigotScheduler().teleport(player, prepared.location()).thenAccept(success -> plugin.getSpigotScheduler().runEntity(player, () -> {

                        long teleportElapsed = System.currentTimeMillis() - teleportStart;

                        UUID teleportedUuid = player.getUniqueId();

                        pendingTeleports.remove(teleportedUuid);

                        if (!Boolean.TRUE.equals(success) || !player.isOnline()) {

                            return;
                        }

                        playersInZone.remove(teleportedUuid);

                        teleportingPlayers.remove(teleportedUuid);

                        preparedTeleports.remove(teleportedUuid);

                        preparedCycleIds.remove(teleportedUuid);

                        completedTeleports.add(teleportedUuid);

                        SoundUtils.play(player, plugin.getConfigManager().getSound("TELEPORT.SUCCESS"));

                        if (successMessage != null && !successMessage.isBlank()) {

                            player.sendMessage(ColorUtils.toComponent(successMessage));
                        }
                    }));
                });
            });
        }

    }

    private void teleportAllInZone(CuboidManager.Cuboid cuboid) {

        List<Player> playersToTeleport = new ArrayList<>();

        for (UUID uuid : playersInZone) {

            if (completedTeleports.contains(uuid)) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {

                playersInZone.remove(uuid);
                continue;
            }

            if (!cuboid.contains(player.getLocation())) {

                handleLeave(player);
                continue;
            }

            playersToTeleport.add(player);
        }

        if (!playersToTeleport.isEmpty()) {

            teleportPreparedGroup(playersToTeleport, cycleId);
        }
    }

    private void teleportGroup(Collection<Player> players) {

        if (players.isEmpty()) {
            return;
        }

        teleportPreparedGroup(players, cycleId);
    }


    private void sendFailedMessage(Player player) {

        if (player == null || !player.isOnline()) {

            return;
        }

        if (failedMessage != null && !failedMessage.isBlank()) {

            player.sendMessage(ColorUtils.toComponent(failedMessage));
        }
    }

    public boolean isInZone(Player player) {

        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();

        /*
         * Completed player رسماً دیگر داخل Zone نیست.
         */
        if (completedTeleports.contains(uuid)) {
            return false;
        }

        return playersInZone.contains(uuid);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRemainingSeconds() {
        return globalCountdown;
    }

    public String getFormattedCountdown() {

        int secs = getRemainingSeconds();

        return secs > 0 ? com.bx.ultimateDonutSmp.utils.NumberUtils.formatCountdown(secs) : "0s";
    }

    private void showCountdown(Player player, int remaining) {

        if (player == null || !player.isOnline()) {

            return;
        }

        String countdown = String.valueOf(remaining);

        String title = titleTemplate.replace("%countdown%", countdown);

        String subtitle = subtitleTemplate.replace("%countdown%", countdown);

        TitleUtils.sendTitle(player, ColorUtils.colorize(title, player), ColorUtils.colorize(subtitle, player), 0, 24, 3);

        SoundUtils.play(player, countdownSound);
    }

    private void showTeleporting(Player player) {

        if (player == null || !player.isOnline()) {

            return;
        }

        TitleUtils.sendTitle(player, ColorUtils.colorize(teleportingTitle, player), ColorUtils.colorize(teleportingSubtitle, player), 0, 100, 5);

        SoundUtils.play(player, countdownSound);
    }


    private void hardClearActiveTitles() {

        for (UUID uuid : playersInZone) {

            Player player = plugin.getServer().getPlayer(uuid);

            if (player != null) {

                TitleUtils.clearTitle(player);
            }
        }

        for (UUID uuid : teleportingPlayers) {

            Player player = plugin.getServer().getPlayer(uuid);

            if (player != null) {

                TitleUtils.clearTitle(player);
            }
        }
    }

    static int normalizeTitleFadeOutTicks(int configuredTicks) {

        return Math.max(1, configuredTicks);
    }

    private record PreparedTeleport(UUID playerId, Location location, int chunkX, int chunkZ, long cycleId) {
    }

    public String getCuboidName() {
        return cuboidName;
    }
}