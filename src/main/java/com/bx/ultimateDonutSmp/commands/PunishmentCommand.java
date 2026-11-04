package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.utils.CommandLabelUtils;
import com.bx.ultimateDonutSmp.utils.PermissionUtils;
import com.bx.ultimateDonutSmp.utils.PunishmentExemptPolicy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.PunishmentManager;
import com.bx.ultimateDonutSmp.models.PunishmentRecord;
import com.bx.ultimateDonutSmp.models.PunishmentScope;
import com.bx.ultimateDonutSmp.models.PunishmentType;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PunishmentCommand implements CommandExecutor {

    private static final String CREATE_PERMISSION = "ultimatedonutsmp.staff.punishments.create";
    private static final String BAN_PERMISSION = "ultimatedonutsmp.staff.punishments.ban";
    private static final String UNBAN_PERMISSION = "ultimatedonutsmp.staff.punishments.unban";
    private static final String MUTE_PERMISSION = "ultimatedonutsmp.staff.punishments.mute";
    private static final String VCMUTE_PERMISSION = "ultimatedonutsmp.staff.vcmute";
    private static final String VCUNMUTE_PERMISSION = "ultimatedonutsmp.staff.vcunmute";
    private static final String UNMUTE_PERMISSION = "ultimatedonutsmp.staff.punishments.unmute";
    private static final String BLACKLIST_PERMISSION = "ultimatedonutsmp.staff.punishments.blacklist";
    private static final String UNBLACKLIST_PERMISSION = "ultimatedonutsmp.staff.punishments.unblacklist";
    private static final Pattern DURATION_FULL = Pattern.compile("(\\d+)(mo|[smhdwy])", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> USAGE_MESSAGES = Map.ofEntries(
            Map.entry("ban", "&cusage: /ban <player> [reason] [duration] &7(duration: 30s, 15m, 2h, 5d, 2w, 1mo, 1y)"),
            Map.entry("tempban", "&cusage: /tempban <player> <time> [reason] &7(time: 30s, 15m, 2h, 5d, or 5d 15m 30s)"),
            Map.entry("mute", "&cusage: /mute <player> [reason] [duration] &7(duration: 30s, 15m, 2h, 5d, 2w)"),
            Map.entry("tempmute", "&cusage: /tempmute <player> <time> [reason] &7(time: 30s, 15m, 2h, 5d, or 5d 15m 30s)"),
            Map.entry("vcmute", "&cusage: /vcmute <player> [reason] [duration] &7(duration: 30s, 15m, 2h, 5d, 2w)"),
            Map.entry("warn", "&cusage: /warn <player> [reason]"),
            Map.entry("kick", "&cusage: /kick <player> [reason]"),
            Map.entry("blacklist", "&cusage: /blacklist <player> [reason]"),
            Map.entry("unban", "&cusage: /unban <player> [reason]"),
            Map.entry("pardon", "&cusage: /pardon <player> [reason]"),
            Map.entry("unmute", "&cusage: /unmute <player> [reason]"),
            Map.entry("vcunmute", "&cusage: /vcunmute <player> [reason]"),
            Map.entry("unblacklist", "&cusage: /unblacklist <player> [reason]")
    );

    private final UltimateDonutSmp plugin;
    private final PunishmentMessages messages;

    public PunishmentCommand(UltimateDonutSmp plugin) {
        this.plugin = plugin;
        this.messages = new PunishmentMessages(plugin, true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = CommandLabelUtils.normalizeLabel(label, command);

        return switch (action) {
            case "ban" -> handleCreate(sender, PunishmentType.BAN, args, false, false, true, action);
            case "tempban" -> handleCreate(sender, PunishmentType.BAN, args, true, false, false, action);
            case "mute" -> handleCreate(sender, PunishmentType.MUTE, args, false, false, true, action);
            case "tempmute" -> handleCreate(sender, PunishmentType.MUTE, args, true, false, false, action);
            case "vcmute" -> handleCreate(sender, PunishmentType.VOICE_MUTE, args, false, false, true, action);
            case "warn" -> handleCreate(sender, PunishmentType.WARN, args, false, false, false, action);
            case "kick" -> handleCreate(sender, PunishmentType.KICK, args, false, true, false, action);
            case "blacklist" -> handleCreate(sender, PunishmentType.BLACKLIST, args, false, false, false, action);
            case "unban", "pardon" -> handleRemove(sender, PunishmentType.BAN, args, action);
            case "unmute" -> handleRemove(sender, PunishmentType.MUTE, args, action);
            case "vcunmute" -> handleRemove(sender, PunishmentType.VOICE_MUTE, args, action);
            case "unblacklist" -> handleRemove(sender, PunishmentType.BLACKLIST, args, action);
            default -> false;
        };
    }

    static String permissionForAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }

        return switch (action.toLowerCase(Locale.ROOT)) {
            case "ban", "tempban" -> BAN_PERMISSION;
            case "unban", "pardon" -> UNBAN_PERMISSION;
            case "mute", "tempmute" -> MUTE_PERMISSION;
            case "vcmute" -> VCMUTE_PERMISSION;
            case "vcunmute" -> VCUNMUTE_PERMISSION;
            case "unmute" -> UNMUTE_PERMISSION;
            case "blacklist" -> BLACKLIST_PERMISSION;
            case "unblacklist" -> UNBLACKLIST_PERMISSION;
            case "warn", "kick" -> CREATE_PERMISSION;
            default -> null;
        };
    }

    static boolean hasPermissionForAction(Permissible permissible, String action) {
        String permission = permissionForAction(action);
        return permission != null && PermissionUtils.has(permissible, permission);
    }

    private boolean handleCreate(CommandSender sender,
                                 PunishmentType type,
                                 String[] args,
                                 boolean temporary,
                                 boolean onlineOnly,
                                 boolean allowInlineDuration,
                                 String usageLabel) {
        if (!hasPermission(sender, usageLabel)) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.NO-CREATE-PERMISSION",
                    "&cYou do not have permission to create punishments."
            ));
            return true;
        }

        int minimumArgs = temporary ? 2 : 1;
        if (args.length < minimumArgs) {
            sendUsage(sender, usageLabel);
            return true;
        }

        ResolvedTarget target = resolveTarget(args[0]);
        if (target == null || target.uuid() == null) {
            send(sender, plugin.getConfigManager().getMessageOrDefault("PUNISHMENTS.NOT-FOUND", "&cPlayer not found."));
            return true;
        }

        Player onlineTarget = Bukkit.getPlayer(target.uuid());
        if (onlineOnly && onlineTarget == null) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.TARGET-OFFLINE",
                    "&cThat player is not online."
            ));
            return true;
        }

        if (!PunishmentExemptPolicy.canPunish(sender, onlineTarget)) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.TARGET-EXEMPT",
                    "&cYou cannot punish that player."
            ));
            return true;
        }

        Long expiresAt = null;
        String reason;

        if (temporary) {
            DurationParseResult duration = parseDuration(args, 1);
            if (duration.millis() <= 0L) {
                send(sender, plugin.getConfigManager().getMessageOrDefault(
                        "PUNISHMENTS.INVALID-DURATION",
                        "&cInvalid time. Use values like 30s, 15m, 2h, 5d, or combine: 5d 15m 30s."
                ));
                return true;
            }
            expiresAt = System.currentTimeMillis() + duration.millis();
            reason = joinReason(args, duration.nextArgIndex());
        } else if (allowInlineDuration && args.length > 1) {
            InlineDurationScan scan = scanInlineDuration(args, 1);
            if (scan != null) {
                expiresAt = System.currentTimeMillis() + scan.millis();
                reason = joinReasonExcluding(args, 1, scan.excludeStart(), scan.excludeEnd());
            } else {
                reason = joinReason(args, 1);
            }
        } else {
            reason = joinReason(args, 1);
        }

        Actor actor = resolveActor(sender);
        PunishmentRecord record = plugin.getPunishmentManager().createRecord(new PunishmentManager.PunishmentCreateRequest(
                target.uuid(),
                target.name(),
                type,
                reason,
                actor.uuid(),
                actor.name(),
                System.currentTimeMillis(),
                expiresAt,
                "local",
                PunishmentScope.SERVER
        ));

        if (record == null) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.CREATE-FAILED",
                    "&cFailed to create punishment record."
            ));
            return true;
        }

        messages.applyRuntimeEffect(type, onlineTarget, record);
        plugin.getDiscordWebhookManager().sendPunishment(record);
        send(sender, plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.CREATED",
                "&aCreated &f{type} &apunishment for &b{player}&a. ID: &f#{id}",
                "{type}", plugin.getPunishmentManager().getDisplayType(record),
                "{player}", target.name(),
                "{id}", String.valueOf(record.getId())
        ));
        return true;
    }

    private boolean handleRemove(CommandSender sender, PunishmentType type, String[] args, String label) {
        if (!hasPermission(sender, label)) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.NO-REMOVE-PERMISSION",
                    "&cYou do not have permission to remove punishments."
            ));
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        ResolvedTarget target = resolveTarget(args[0]);
        UUID targetUuid = target != null ? target.uuid() : null;
        String targetName = target != null ? target.name() : args[0];

        if (targetUuid == null && (targetName == null || targetName.isBlank())) {
            send(sender, plugin.getConfigManager().getMessageOrDefault("PUNISHMENTS.NOT-FOUND", "&cPlayer not found."));
            return true;
        }

        String reason = joinReason(args, 1);
        if (reason.equals("no reason specified")) {
            reason = "removed by staff";
        }

        Actor actor = resolveActor(sender);
        boolean removed = plugin.getPunishmentManager().markActiveRecordsRemoved(
                targetUuid,
                targetName,
                type,
                new PunishmentManager.PunishmentRemovalRequest(
                        actor.uuid(),
                        actor.name(),
                        System.currentTimeMillis(),
                        reason
                )
        );

        if (!removed) {
            send(sender, plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.NO-ACTIVE",
                    "&cNo active {type} punishment found for {player}.",
                    "{type}", type.name(),
                    "{player}", targetName
            ));
            return true;
        }

        if (type == PunishmentType.VOICE_MUTE) {
            messages.refreshVoiceMute(targetUuid, targetName);
        }

        send(sender, plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.REMOVED",
                "&aRemoved active &f{type} &apunishment(s) for &b{player}&a.",
                "{type}", type.name(),
                "{player}", targetName
        ));
        return true;
    }

    private ResolvedTarget resolveTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online == null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().equalsIgnoreCase(input)) {
                    online = player;
                    break;
                }
            }
        }

        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName());
        }

        UUID knownUuid = plugin.getPunishmentManager().resolveTargetUuid(input, true).orElse(null);
        if (knownUuid != null) {
            return new ResolvedTarget(knownUuid, plugin.getPunishmentManager().resolveTargetName(knownUuid, input));
        }
        return null;
    }

    private Actor resolveActor(CommandSender sender) {
        if (sender instanceof Player player) {
            return new Actor(player.getUniqueId(), player.getName());
        }
        return new Actor(null, "console");
    }

    private boolean hasPermission(CommandSender sender, String action) {
        return !(sender instanceof Player) || hasPermissionForAction(sender, action);
    }

    private void sendUsage(CommandSender sender, String label) {
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        String fallback = USAGE_MESSAGES.getOrDefault(normalizedLabel, "&cUsage: /" + normalizedLabel + " <player> [reason]");
        send(sender, plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.USAGE-" + normalizedLabel.toUpperCase(Locale.ROOT),
                fallback
        ));
    }

    private long parseDurationMillis(String input) {
        if (input == null || input.isBlank()) {
            return -1L;
        }

        String trimmed = input.trim();
        Matcher matcher = DURATION_FULL.matcher(trimmed);
        if (!matcher.matches()) {
            return -1L;
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1L;
        }

        long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            case "mo" -> 2_592_000_000L;
            case "y" -> 31_536_000_000L;
            default -> -1L;
        };
        if (multiplier <= 0L) {
            return -1L;
        }

        return amount * multiplier;
    }

    private DurationParseResult parseDuration(String[] args, int startIndex) {
        long totalMillis = 0L;
        int index = startIndex;
        while (index < args.length) {
            long tokenMillis = parseDurationMillis(args[index]);
            if (tokenMillis <= 0L) {
                break;
            }
            totalMillis += tokenMillis;
            index++;
        }
        return new DurationParseResult(totalMillis, index);
    }

    private InlineDurationScan scanInlineDuration(String[] args, int startIndex) {

        if (args.length > startIndex) {
            long lastToken = parseDurationMillis(args[args.length - 1]);
            if (lastToken > 0L) {
                long total = 0L;
                int start = args.length;
                while (start > startIndex) {
                    long t = parseDurationMillis(args[start - 1]);
                    if (t <= 0L) {
                        break;
                    }
                    total += t;
                    start--;
                }
                if (total > 0L) {
                    return new InlineDurationScan(total, start, args.length);
                }
            }
        }

        if (startIndex < args.length) {
            long firstToken = parseDurationMillis(args[startIndex]);
            if (firstToken > 0L) {
                long total = 0L;
                int end = startIndex;
                while (end < args.length) {
                    long t = parseDurationMillis(args[end]);
                    if (t <= 0L) {
                        break;
                    }
                    total += t;
                    end++;
                }
                if (total > 0L) {
                    return new InlineDurationScan(total, startIndex, end);
                }
            }
        }

        return null;
    }

    private String joinReason(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return "no reason specified";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.isEmpty() ? "no reason specified" : builder.toString();
    }
    private String joinReasonExcluding(String[] args, int startIndex, int excludeStart, int excludeEnd) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (index >= excludeStart && index < excludeEnd) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.isEmpty() ? "no reason specified" : builder.toString();
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.toComponent(message));
    }

    private record ResolvedTarget(UUID uuid, String name) {
    }

    private record Actor(UUID uuid, String name) {
    }

    private record DurationParseResult(long millis, int nextArgIndex) {
    }

    private record InlineDurationScan(long millis, int excludeStart, int excludeEnd) {
    }

}
