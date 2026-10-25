package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.Punishment;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
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

    /**
     * Teleports jailed players back to jail when they join the server.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
        if (activeJail != null) {
            Location jailLoc = plugin.getPunishmentManager().getAssignedJailLocation(player.getUniqueId());
            if (jailLoc != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(jailLoc);
                    }
                }, 2L);
            }
        }
    }

    /**
     * Ensures players who die while jailed respawn directly back in jail.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
        if (activeJail != null) {
            Location jailLoc = plugin.getPunishmentManager().getAssignedJailLocation(player.getUniqueId());
            if (jailLoc != null) {
                event.setRespawnLocation(jailLoc);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(jailLoc);
                    }
                }, 1L);
            }
        }
    }

    /**
     * Prevents jailed players from throwing Ender Pearls, Potions, Tridents, Wind Charges, etc.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
            if (activeJail != null) {
                event.setCancelled(true);
                player.updateInventory();
                player.sendMessage(plugin.getMessageManager().color("&cYou cannot throw items or projectiles while in jail!"));
            }
        }
    }

    /**
     * Prevents jailed players from using Ender Pearls, Chorus Fruit, Buckets, Boats, or interacting in jail.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
        if (activeJail != null) {
            event.setCancelled(true);
            if (event.getItem() != null) {
                String matName = event.getItem().getType().name();
                if (matName.contains("PEARL") || matName.contains("CHORUS") || matName.contains("POTION") ||
                    matName.contains("WIND_CHARGE") || matName.contains("FIREWORK") || matName.contains("BOW")) {
                    player.updateInventory();
                    player.sendMessage(plugin.getMessageManager().color("&cYou cannot use items while in jail!"));
                }
            }
        }
    }

    /**
     * Prevents jailed players from eating Chorus Fruit or drinking potions to escape.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPunishmentManager().getActiveJail(player.getUniqueId()) != null) {
            String matName = event.getItem().getType().name();
            if (matName.contains("CHORUS") || matName.contains("POTION") || matName.contains("MILK")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().color("&cYou cannot consume that while in jail!"));
            }
        }
    }

    /**
     * Prevents jailed players from escaping via Ender Pearl or Chorus Fruit teleports.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPunishmentManager().getActiveJail(player.getUniqueId()) != null) {
            String cause = event.getCause().name();
            if (cause.equals("ENDER_PEARL") || cause.equals("CHORUS_FRUIT") ||
                cause.equals("NETHER_PORTAL") || cause.equals("END_PORTAL") || cause.equals("END_GATEWAY")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().color("&cYou cannot teleport while in jail!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getPunishmentManager().getActiveJail(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().color("&cYou cannot break blocks while in jail!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getPunishmentManager().getActiveJail(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().color("&cYou cannot place blocks while in jail!"));
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
     * Blocks all commands for jailed players and intercepts vanilla IP-ban commands.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
        if (activeJail != null && !player.hasPermission("staffcore.bypass.jail") && !player.isOp()) {
            event.setCancelled(true);
            String remaining = activeJail.getDuration() > 0 ? TimeUtil.formatRemaining(activeJail.getExpireTime()) : "Permanent";
            player.sendMessage(plugin.getMessageManager().color(
                    "&cYou cannot execute commands while in jail! &7(Reason: &f" + activeJail.getReason() + " &7| Remaining: &e" + remaining + "&7)"));
            return;
        }

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