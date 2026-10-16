package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

public class FreezeListener implements Listener {

    private final StaffCore plugin;

    public FreezeListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                Location locked = from.clone();
                locked.setYaw(to.getYaw());
                locked.setPitch(to.getPitch());
                event.setTo(locked);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getFreezeManager().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.action-blocked-frozen"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getFreezeManager().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.action-blocked-frozen"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getFreezeManager().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getFreezeManager().isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.command-blocked-frozen"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (plugin.getFreezeManager().isFrozen(uuid)) {
            String alert = plugin.getMessageManager().color(
                    plugin.getConfig().getString("freeze.disconnect-alert", "&4&l[STAFF ALERT] &cPlayer &e%target% &cdisconnected while frozen!")
                            .replace("%target%", player.getName()));

            Bukkit.getConsoleSender().sendMessage(alert);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("staffcore.freeze.alerts")) {
                    staff.sendMessage(alert);
                }
            }
        }
    }
}