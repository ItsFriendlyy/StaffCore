package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.PunishmentType;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PunishCommands implements CommandExecutor {

    private final StaffCore plugin;

    public PunishCommands(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "ban":
                return handleBan(sender, args, false, false);
            case "tempban":
                return handleBan(sender, args, true, false);
            case "ipban":
                return handleBan(sender, args, false, true);
            case "tempipban":
                return handleBan(sender, args, true, true);
            case "unban":
                return handleUnban(sender, args);
            case "unbanip":
                return handleUnbanIp(sender, args);
            case "mute":
                return handleMute(sender, args, false);
            case "tempmute":
                return handleMute(sender, args, true);
            case "unmute":
                return handleUnmute(sender, args);
            case "warn":
                return handleWarn(sender, args);
            default:
                return false;
        }
    }

    private boolean handleBan(CommandSender sender, String[] args, boolean temporary, boolean isIp) {
        int minArgs = temporary ? 2 : 1;
        if (args.length < minArgs) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /" + (temporary ? "temp" : "") + (isIp ? "ipban" : "ban") + " <player" + (isIp ? "|ip" : "") + "> " + (temporary ? "<duration> " : "") + "[reason] [-s]"));
            return true;
        }

        String targetInput = args[0];
        long duration = -1;
        int reasonStartIndex = 1;

        if (temporary) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration <= 0) {
                sender.sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.invalid-duration"));
                return true;
            }
            reasonStartIndex = 2;
        }

        ParsedReason parsed = parseSilentAndReason(args, reasonStartIndex);

        String targetName = targetInput;
        String targetIp = "";
        UUID targetUUID = null;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            targetUUID = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();
            if (onlineTarget.getAddress() != null) {
                targetIp = onlineTarget.getAddress().getAddress().getHostAddress();
            }

            // OP and bypass permission check
            if (onlineTarget.isOp() || onlineTarget.hasPermission("staffcore.bypass.ban")) {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.cannot-punish-exempt", "&cCannot punish exempt player!")
                                .replace("%target%", targetName)));
                return true;
            }
        } else {
            if (isIp && targetInput.contains(".")) {
                targetIp = targetInput;
            } else {
                @SuppressWarnings("deprecation")
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetInput);
                if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
                    if (offlineTarget.isOp()) {
                        sender.sendMessage(plugin.getMessageManager().color(
                                plugin.getConfig().getString("messages.cannot-punish-exempt", "&cCannot punish exempt player!")
                                        .replace("%target%", targetInput)));
                        return true;
                    }
                    targetUUID = offlineTarget.getUniqueId();
                    targetName = offlineTarget.getName() != null ? offlineTarget.getName() : targetInput;
                } else if (!isIp) {
                    sender.sendMessage(plugin.getMessageManager().color(
                            plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found!")
                                    .replace("%target%", targetInput)));
                    return true;
                }
            }
        }

        PunishmentType type;
        if (isIp) {
            type = temporary ? PunishmentType.TEMP_IP_BAN : PunishmentType.IP_BAN;
        } else {
            type = temporary ? PunishmentType.TEMPBAN : PunishmentType.BAN;
        }

        plugin.getPunishmentManager().createPunishment(
                targetUUID, targetName, targetIp, sender.getName(), type, parsed.reason, duration, parsed.silent);
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args, boolean temporary) {
        int minArgs = temporary ? 2 : 1;
        if (args.length < minArgs) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /" + (temporary ? "tempmute" : "mute") + " <player> " + (temporary ? "<duration> " : "") + "[reason] [-s]"));
            return true;
        }

        String targetInput = args[0];
        long duration = -1;
        int reasonStartIndex = 1;

        if (temporary) {
            duration = TimeUtil.parseDuration(args[1]);
            if (duration <= 0) {
                sender.sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.invalid-duration"));
                return true;
            }
            reasonStartIndex = 2;
        }

        ParsedReason parsed = parseSilentAndReason(args, reasonStartIndex);

        String targetName = targetInput;
        UUID targetUUID = null;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            targetUUID = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();

            if (onlineTarget.isOp() || onlineTarget.hasPermission("staffcore.bypass.mute")) {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.cannot-punish-exempt", "&cCannot punish exempt player!")
                                .replace("%target%", targetName)));
                return true;
            }
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetInput);
            if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
                if (offlineTarget.isOp()) {
                    sender.sendMessage(plugin.getMessageManager().color(
                            plugin.getConfig().getString("messages.cannot-punish-exempt", "&cCannot punish exempt player!")
                                    .replace("%target%", targetInput)));
                    return true;
                }
                targetUUID = offlineTarget.getUniqueId();
                targetName = offlineTarget.getName() != null ? offlineTarget.getName() : targetInput;
            } else {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found!")
                                .replace("%target%", targetInput)));
                return true;
            }
        }

        PunishmentType type = temporary ? PunishmentType.TEMP_MUTE : PunishmentType.MUTE;
        plugin.getPunishmentManager().createPunishment(
                targetUUID, targetName, "", sender.getName(), type, parsed.reason, duration, parsed.silent);
        return true;
    }

    private boolean handleWarn(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /warn <player> [reason] [-s]"));
            return true;
        }

        String targetInput = args[0];
        ParsedReason parsed = parseSilentAndReason(args, 1);

        String targetName = targetInput;
        UUID targetUUID = null;

        Player onlineTarget = Bukkit.getPlayer(targetInput);
        if (onlineTarget != null) {
            targetUUID = onlineTarget.getUniqueId();
            targetName = onlineTarget.getName();

            if (onlineTarget.isOp() || onlineTarget.hasPermission("staffcore.bypass.warn")) {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.cannot-punish-exempt", "&cCannot punish exempt player!")
                                .replace("%target%", targetName)));
                return true;
            }
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetInput);
            if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
                if (offlineTarget.isOp()) {
                    sender.sendMessage(plugin.getMessageManager().color(
                            plugin.getConfig().getString("messages.cannot-punish-exempt", "&cCannot punish exempt player!")
                                    .replace("%target%", targetInput)));
                    return true;
                }
                targetUUID = offlineTarget.getUniqueId();
                targetName = offlineTarget.getName() != null ? offlineTarget.getName() : targetInput;
            } else {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found!")
                                .replace("%target%", targetInput)));
                return true;
            }
        }

        plugin.getPunishmentManager().createPunishment(
                targetUUID, targetName, "", sender.getName(), PunishmentType.WARN, parsed.reason, -1, parsed.silent);
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /unban <player>"));
            return true;
        }

        String targetInput = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetInput);

        boolean success = plugin.getPunishmentManager().unban(target.getUniqueId());
        if (success) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.unban-success", "&aSuccessfully unbanned &e%target%&a.")
                            .replace("%target%", targetInput)));
        } else {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.not-banned", "&cPlayer &e%target% &cis not banned!")
                            .replace("%target%", targetInput)));
        }
        return true;
    }

    private boolean handleUnbanIp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /unbanip <ip>"));
            return true;
        }

        String ip = args[0];
        boolean success = plugin.getPunishmentManager().unbanIp(ip);
        if (success) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.unbanip-success", "&aSuccessfully unbanned IP &e%ip%&a.")
                            .replace("%ip%", ip)));
        } else {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.ip-not-banned", "&cIP &e%ip% &cis not banned!")
                            .replace("%ip%", ip)));
        }
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /unmute <player>"));
            return true;
        }

        String targetInput = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetInput);

        boolean success = plugin.getPunishmentManager().unmute(target.getUniqueId());
        if (success) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.unmute-success", "&aSuccessfully unmuted &e%target%&a.")
                            .replace("%target%", targetInput)));
        } else {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.not-muted", "&cPlayer &e%target% &cis not muted!")
                            .replace("%target%", targetInput)));
        }
        return true;
    }

    private ParsedReason parseSilentAndReason(String[] args, int startIndex) {
        boolean silent = false;
        List<String> words = new ArrayList<String>();

        for (int i = startIndex; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-s")) {
                silent = true;
            } else {
                words.add(args[i]);
            }
        }

        String reason = words.isEmpty() ? "No reason specified" : joinStrings(words, " ");
        return new ParsedReason(silent, reason);
    }

    private String joinStrings(List<String> list, String delim) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append(delim);
        }
        return sb.toString();
    }

    private static class ParsedReason {
        final boolean silent;
        final String reason;

        ParsedReason(boolean silent, String reason) {
            this.silent = silent;
            this.reason = reason;
        }
    }
}