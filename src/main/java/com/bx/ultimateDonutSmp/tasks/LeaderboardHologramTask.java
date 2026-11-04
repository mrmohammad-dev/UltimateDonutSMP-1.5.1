package com.bx.ultimateDonutSmp.tasks;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager;

public class LeaderboardHologramTask implements Runnable {

    private final UltimateDonutSmp plugin;

    public LeaderboardHologramTask(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {

        try {

            if (plugin.getLeaderboardManager() == null
                    || plugin.getLeaderboardHologramManager() == null) {
                return;
            }

            /*
             * Start a fresh async refresh for every leaderboard.
             */
            plugin.getLeaderboardManager().invalidateAll();

            /*
             * Give the async database refresh a moment to finish
             * before updating the holograms.
             */
            long timeout = System.currentTimeMillis() + 10_000L;

            while (plugin.getLeaderboardManager().isRefreshingAny()
                    && System.currentTimeMillis() < timeout) {

                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            boolean anyMissing = false;

            for (LeaderboardManager.LeaderboardType type
                    : LeaderboardManager.LeaderboardType.values()) {

                if (!plugin.getLeaderboardHologramManager().exists(type)) {
                    anyMissing = true;
                    break;
                }
            }

            if (anyMissing) {
                plugin.getLeaderboardHologramManager().loadAll();
            }

            /*
             * Update the actual FancyHolograms.
             */
            plugin.getLeaderboardHologramManager().updateAll();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to update leaderboard holograms: "
                            + exception.getMessage()
            );
        }
    }

    /*
     * ============================================================
     * START
     * ============================================================
     */

    public static void start(UltimateDonutSmp plugin) {

        /*
         * Initial delay:
         * 10 seconds
         *
         * Repeat:
         * every 60 seconds
         */
        plugin.getSpigotScheduler()
                .runAsyncTimer(
                        new LeaderboardHologramTask(plugin),
                        10L * 20L,
                        60L * 20L
                );
    }
}