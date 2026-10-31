package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;

public class PhaseListener implements Listener {

    private final StaffCore plugin;

    public PhaseListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSuffocationDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                if (plugin.getPhaseManager().isPhased(player.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPhaseManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPhaseManager().isPhased(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Update the ghost air bubble as the player moves into new blocks
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            plugin.getPhaseManager().updateGhostBlocks(player, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPhaseManager().isPhased(player.getUniqueId())) {
            plugin.getPhaseManager().updateGhostBlocks(player, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        // Prevent accidentally turning flight off while phasing
        if (plugin.getPhaseManager().isPhased(player.getUniqueId()) && !event.isFlying()) {
            event.setCancelled(true);
            player.setFlying(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPhaseManager().isPhased(player.getUniqueId())) return;

        // Left-clicking a solid block while holding items instantly passes through to the other side
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            if (!clicked.getType().isSolid()) return;

            Vector dir = player.getLocation().getDirection().normalize();
            Location target = findPassableLocationThrough(clicked.getLocation(), dir, 10);
            if (target != null) {
                target.setYaw(player.getLocation().getYaw());
                target.setPitch(player.getLocation().getPitch());
                player.teleport(target);
                playPhaseSound(player);
                event.setCancelled(true);
            }
        }
    }

    private Location findPassableLocationThrough(Location start, Vector dir, int maxDist) {
        Location check = start.clone();
        boolean enteredSolid = false;

        for (int i = 1; i <= maxDist; i++) {
            check.add(dir.clone().multiply(1.0));
            Block feet = check.getBlock();
            Block eye = check.clone().add(0, 1, 0).getBlock();

            if (feet.getType().isSolid() || eye.getType().isSolid()) {
                enteredSolid = true;
            } else if (enteredSolid) {
                return check.clone().add(0.5, 0.0, 0.5);
            }
        }
        return null;
    }

    private void playPhaseSound(Player player) {
        try {
            Sound s;
            try {
                s = Sound.valueOf("ENTITY_ENDERMAN_TELEPORT");
            } catch (Exception e) {
                s = Sound.valueOf("ENDERMAN_TELEPORT");
            }
            player.playSound(player.getLocation(), s, 0.3f, 1.8f);
        } catch (Throwable ignored) {}
    }
}