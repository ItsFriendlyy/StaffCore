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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPhaseManager().isPhased(player.getUniqueId())) return;

        // Left-clicking a solid block while in /phase instantly passes through it
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            if (isPassable(clicked)) return;

            Vector dir = player.getLocation().getDirection().normalize();
            Location target = findPassableLocationThrough(clicked.getLocation(), dir, 8);
            if (target != null) {
                target.setYaw(player.getLocation().getYaw());
                target.setPitch(player.getLocation().getPitch());
                player.teleport(target);
                playPhaseSound(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPhaseManager().isPhased(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Only process position changes
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Vector moveVector = to.toVector().subtract(from.toVector());
        if (moveVector.lengthSquared() < 0.0001) return;

        Vector dir = moveVector.normalize();
        Location eye = player.getEyeLocation();
        Location feet = player.getLocation();

        // 1. Vertical upward pass (flying up into ceiling)
        if (dir.getY() > 0.3) {
            Block aboveEye = eye.clone().add(0, 0.8, 0).getBlock();
            if (!isPassable(aboveEye)) {
                Location safeUp = findPassableVertical(aboveEye.getLocation(), true, 8);
                if (safeUp != null) {
                    safeUp.setYaw(player.getLocation().getYaw());
                    safeUp.setPitch(player.getLocation().getPitch());
                    player.teleport(safeUp);
                    playPhaseSound(player);
                    return;
                }
            }
        }

        // 2. Vertical downward pass (descending down into floor)
        if (dir.getY() < -0.3) {
            Block belowFeet = feet.clone().add(0, -0.6, 0).getBlock();
            if (!isPassable(belowFeet)) {
                Location safeDown = findPassableVertical(belowFeet.getLocation(), false, 8);
                if (safeDown != null) {
                    safeDown.setYaw(player.getLocation().getYaw());
                    safeDown.setPitch(player.getLocation().getPitch());
                    player.teleport(safeDown);
                    playPhaseSound(player);
                    return;
                }
            }
        }

        // 3. Horizontal block collision detection (walking/flying into wall)
        Block frontFeet = feet.clone().add(dir.clone().multiply(0.7)).getBlock();
        Block frontEye = eye.clone().add(dir.clone().multiply(0.7)).getBlock();

        if (!isPassable(frontFeet) || !isPassable(frontEye)) {
            Location safeAhead = findPassableLocationThrough(feet, dir, 8);
            if (safeAhead != null) {
                safeAhead.setYaw(player.getLocation().getYaw());
                safeAhead.setPitch(player.getLocation().getPitch());
                player.teleport(safeAhead);
                playPhaseSound(player);
            } else {
                // If wall is very thick, push 1.2 blocks forward so player keeps advancing
                Location stepForward = feet.clone().add(dir.clone().multiply(1.2));
                stepForward.setYaw(player.getLocation().getYaw());
                stepForward.setPitch(player.getLocation().getPitch());
                player.teleport(stepForward);
            }
        }
    }

    private boolean isPassable(Block block) {
        if (block == null) return true;
        Material mat = block.getType();
        if (mat == Material.AIR) return true;
        String name = mat.name();
        if (name.endsWith("_AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR")) return true;
        try {
            return !mat.isSolid();
        } catch (Throwable t) {
            return mat == Material.AIR;
        }
    }

    private Location findPassableLocationThrough(Location start, Vector dir, int maxDist) {
        Location check = start.clone();
        boolean enteredSolid = false;

        for (int i = 1; i <= maxDist; i++) {
            check.add(dir.clone().multiply(1.0));
            Block feet = check.getBlock();
            Block eye = check.clone().add(0, 1, 0).getBlock();

            if (!isPassable(feet) || !isPassable(eye)) {
                enteredSolid = true;
            } else if (enteredSolid) {
                return check.clone().add(0.5, 0.0, 0.5);
            }
        }
        return null;
    }

    private Location findPassableVertical(Location start, boolean up, int maxDist) {
        Location check = start.clone();
        boolean enteredSolid = false;
        int step = up ? 1 : -1;

        for (int i = 1; i <= maxDist; i++) {
            check.add(0, step, 0);
            Block feet = check.getBlock();
            Block eye = check.clone().add(0, 1, 0).getBlock();

            if (!isPassable(feet) || !isPassable(eye)) {
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