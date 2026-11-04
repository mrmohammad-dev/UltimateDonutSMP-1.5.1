package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager;
import com.bx.ultimateDonutSmp.menus.LeaderboardMenu;
import com.bx.ultimateDonutSmp.menus.LeaderboardTypeMenu;
import com.bx.ultimateDonutSmp.utils.CommandLabelUtils;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class LeaderboardCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION =
            "ultimatedonutsmp.admin.leaderboard";

    private final UltimateDonutSmp plugin;

    public LeaderboardCommand(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        /*
         * ============================================================
         * /lb create <type>
         * ============================================================
         */

        if (args.length >= 1
                && args[0].equalsIgnoreCase("create")) {

            if (!player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(
                        ColorUtils.toComponent(
                                "&cYou do not have permission to create leaderboards."
                        )
                );
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(
                        ColorUtils.toComponent(
                                "&cUsage: &f/lb create <type>"
                        )
                );
                return true;
            }

            LeaderboardManager.LeaderboardType type =
                    plugin.getLeaderboardManager()
                            .parseType(args[1])
                            .orElse(null);

            if (type == null) {

                String available =
                        plugin.getLeaderboardManager()
                                .getTypes()
                                .stream()
                                .map(LeaderboardManager.LeaderboardType::getConfigKey)
                                .collect(Collectors.joining(", "));

                player.sendMessage(
                        ColorUtils.toComponent(
                                "&cInvalid leaderboard type."
                                        + " &7Available: &f"
                                        + available
                        )
                );

                return true;
            }

            plugin.getLeaderboardHologramManager()
                    .create(
                            player.getLocation().clone(),
                            type
                    );

            player.sendMessage(
                    ColorUtils.toComponent(
                            "&aLeaderboard hologram created for &f"
                                    + type.getConfigKey()
                                    + "&a."
                    )
            );

            return true;
        }

        /*
         * ============================================================
         * /lb remove <type>
         * ============================================================
         */

        if (args.length >= 1
                && args[0].equalsIgnoreCase("remove")) {

            if (!player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(
                        ColorUtils.toComponent(
                                "&cYou do not have permission to remove leaderboards."
                        )
                );
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(
                        ColorUtils.toComponent(
                                "&cUsage: &f/lb remove <type>"
                        )
                );
                return true;
            }

            LeaderboardManager.LeaderboardType type =
                    plugin.getLeaderboardManager()
                            .parseType(args[1])
                            .orElse(null);

            if (type == null) {
                player.sendMessage(
                        ColorUtils.toComponent(
                                "&cInvalid leaderboard type."
                        )
                );
                return true;
            }

            plugin.getLeaderboardHologramManager()
                    .remove(type);

            player.sendMessage(
                    ColorUtils.toComponent(
                            "&aLeaderboard hologram removed for &f"
                                    + type.getConfigKey()
                                    + "&a."
                    )
            );

            return true;
        }

        /*
         * ============================================================
         * /lb
         * ============================================================
         */

        if (!plugin.getConfigManager()
                .isCommandEnabled("LEADERBOARDS")) {

            player.sendMessage(
                    ColorUtils.toComponent(
                            "&cLeaderboards are currently disabled."
                    )
            );

            return true;
        }

        /*
         * /baltop
         */
        if (args.length == 0
                && CommandLabelUtils.normalizeLabel(label, cmd).equals("baltop")) {

            new LeaderboardTypeMenu(
                    plugin,
                    LeaderboardManager.LeaderboardType.MONEY
            ).open(player);

            return true;
        }

        /*
         * /lb
         */
        if (args.length == 0) {
            new LeaderboardMenu(plugin).open(player);
            return true;
        }

        /*
         * /lb <type>
         */
        LeaderboardManager.LeaderboardType type =
                plugin.getLeaderboardManager()
                        .parseType(args[0])
                        .orElse(null);

        if (type == null) {

            String available =
                    plugin.getLeaderboardManager()
                            .getTypes()
                            .stream()
                            .map(LeaderboardManager.LeaderboardType::getConfigKey)
                            .collect(Collectors.joining(", "));

            player.sendMessage(
                    ColorUtils.toComponent(
                            "&cInvalid leaderboard type."
                                    + " &7Available: &f"
                                    + available
                    )
            );

            return true;
        }

        new LeaderboardTypeMenu(
                plugin,
                type
        ).open(player);

        return true;
    }
}