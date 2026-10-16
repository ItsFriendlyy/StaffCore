package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.Punishment;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

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
        
        // OP players automatically bypass any ban checks on login
        OfflinePlayer opCheck = Bukkit.getOfflinePlayer(uuid);
        if (opCheck != null && opCheck.isOp()) {
            return;
        }

        InetAddress address = event.getAddress();
        String ip = address != null ? address.getHostAddress() : "";

        // Check IP Ban
        Punishment ipBan = plugin.getPunishmentManager().getActiveIpBan(ip);
        if (ipBan != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getPunishmentManager().getBanKickMessage(ipBan));
            return;
        }

        // Check UUID Ban
        Punishment uuidBan = plugin.getPunishmentManager().getActiveBan(uuid);
        if (uuidBan != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.getPunishmentManager().getBanKickMessage(uuidBan));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // OPs bypass mutes
        if (!player.isOp() && !player.hasPermission("staffcore.bypass.mute")) {
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
        }

        // Staff chat toggle
        if (plugin.getStaffChatManager().isInStaffChat(uuid)) {
            event.setCancelled(true);
            plugin.getStaffChatManager().sendStaffMessage(player, event.getMessage());
        }
    }
}