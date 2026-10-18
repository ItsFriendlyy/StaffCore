package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.Punishment;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

public class PunishmentListener implements Listener {

    private final StaffCore plugin;

    public PunishmentListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        InetAddress address = event.getAddress();
        String ip = address != null ? address.getHostAddress() : "";

        // Cache player's last known IP address for offline /ipban lookups
        if (address != null) {
            plugin.getPunishmentManager().cachePlayerIp(uuid, event.getName(), ip);
        }

        // 1. Check IP Ban
        Punishment ipBan = plugin.getPunishmentManager().getActiveIpBan(ip);
        if (ipBan != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getPunishmentManager().getBanKickMessage(ipBan));
            return;
        }

        // 2. Check UUID Ban
        Punishment uuidBan = plugin.getPunishmentManager().getActiveBan(uuid);
        if (uuidBan != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getPunishmentManager().getBanKickMessage(uuidBan));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. Check if player is muted
        Punishment mute = plugin.getPunishmentManager().getActiveMute(uuid);
        if (mute != null) {
            event.setCancelled(true);
            String path = mute.getDuration() > 0 ? "mute.temporary-chat" : "mute.permanent-chat";
            List<String> lines = plugin.getMessageManager().getMessageList(path);
            String remaining = mute.getDuration() > 0 ? TimeUtil.formatRemaining(mute.getExpireTime()) : "Permanent";

            for (String line : lines) {
                player.sendMessage(line
                        .replace("%reason%", mute.getReason())
                        .replace("%staff%", mute.getStaffName())
                        .replace("%remaining%", remaining));
            }
            return;
        }

        // 2. Check if player is in Staff Chat mode
        if (plugin.getStaffChatManager().isInStaffChat(uuid)) {
            event.setCancelled(true);
            plugin.getStaffChatManager().sendStaffMessage(player, event.getMessage());
        }
    }

    /**
     * Intercepts vanilla Minecraft IP-ban commands from players and redirects them to StaffCore
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String[] parts = msg.split(" ", 2);
        String command = parts[0].toLowerCase();

        if (command.equals("ban-ip") || command.equals("banip") || command.equals("minecraft:ban-ip") || command.equals("minecraft:banip")) {
            event.setCancelled(true);
            String args = parts.length > 1 ? " " + parts[1] : "";
            event.getPlayer().performCommand("ipban" + args);
        } else if (command.equals("pardon-ip") || command.equals("pardonip") || command.equals("unban-ip") || command.equals("minecraft:pardon-ip")) {
            event.setCancelled(true);
            String args = parts.length > 1 ? " " + parts[1] : "";
            event.getPlayer().performCommand("unbanip" + args);
        }
    }

    /**
     * Intercepts vanilla Minecraft IP-ban commands from Console and redirects them to StaffCore
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        String msg = event.getCommand().trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String[] parts = msg.split(" ", 2);
        String command = parts[0].toLowerCase();

        if (command.equals("ban-ip") || command.equals("banip") || command.equals("minecraft:ban-ip") || command.equals("minecraft:banip")) {
            event.setCancelled(true);
            String args = parts.length > 1 ? " " + parts[1] : "";
            Bukkit.dispatchCommand(event.getSender(), "ipban" + args);
        } else if (command.equals("pardon-ip") || command.equals("pardonip") || command.equals("unban-ip") || command.equals("minecraft:pardon-ip")) {
            event.setCancelled(true);
            String args = parts.length > 1 ? " " + parts[1] : "";
            Bukkit.dispatchCommand(event.getSender(), "unbanip" + args);
        }
    }
}