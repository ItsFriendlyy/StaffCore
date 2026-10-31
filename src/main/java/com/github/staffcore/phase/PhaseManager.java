package com.github.staffcore.phase;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseManager {

    private final StaffCore plugin;
    private final Set<UUID> phasedPlayers = ConcurrentHashMap.newKeySet();
    // Player UUID -> Set of BlockPos currently ghosted as AIR for that player
    private final Map<UUID, Set<BlockPos>> activeGhostBlocks = new ConcurrentHashMap<>();

    public PhaseManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPhased(UUID uuid) {
        return phasedPlayers.contains(uuid);
    }

    public boolean togglePhase(Player player) {
        UUID uuid = player.getUniqueId();
        if (isPhased(uuid)) {
            setPhased(player, false);
            return false;
        } else {
            setPhased(player, true);
            return true;
        }
    }

    public void setPhased(Player player, boolean phased) {
        UUID uuid = player.getUniqueId();
        if (phased) {
            phasedPlayers.add(uuid);

            // Enable flight so player can navigate in 3D through walls/floors while in Survival/Creative
            player.setAllowFlight(true);
            player.setFlying(true);

            // Create initial ghost air bubble around the player
            updateGhostBlocks(player, player.getLocation());

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.enabled", "&8[&cStaffCore&8] &aPhase mode &2enabled&a! Smooth block pass-through active in &e" + player.getGameMode().name() + "&a mode.")));
        } else {
            phasedPlayers.remove(uuid);

            // Restore all ghost blocks for this player back to solid blocks
            clearGhostBlocks(player);

            // If player ended up inside a solid block, safely step them into adjacent air
            ejectFromSolidBlock(player);

            // Restore flight settings if in Survival and /fly wasn't enabled
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                if (!UtilityCommands.isFlyEnabled(uuid)) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.disabled", "&8[&cStaffCore&8] &cPhase mode &4disabled&c.")));
        }
    }

    /**
     * Updates the dynamic ghost air bubble around the player as they move through blocks.
     */
    public void updateGhostBlocks(Player player, Location loc) {
        if (!isPhased(player.getUniqueId()) || loc == null) return;

        World world = loc.getWorld();
        if (world == null) return;

        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();

        Set<BlockPos> newBox = new HashSet<>();

        // 3x4x3 box around player body (from feet to head + 1 above)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int bx = px + dx;
                    int by = py + dy;
                    int bz = pz + dz;

                    if (by < 0 || by > world.getMaxHeight()) continue;

                    Block b = world.getBlockAt(bx, by, bz);
                    Material mat = b.getType();

                    if (mat != Material.AIR && !mat.name().endsWith("_AIR")) {
                        try {
                            if (mat.isSolid()) {
                                newBox.add(new BlockPos(world.getName(), bx, by, bz));
                            }
                        } catch (Throwable t) {
                            newBox.add(new BlockPos(world.getName(), bx, by, bz));
                        }
                    }
                }
            }
        }

        Set<BlockPos> current = activeGhostBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());

        // 1. Restore blocks that left the player's bubble
        for (BlockPos oldPos : current) {
            if (!newBox.contains(oldPos)) {
                Block b = oldPos.getBlock();
                if (b != null) {
                    restoreBlock(player, b);
                }
            }
        }

        // 2. Ghost new solid blocks as AIR for the player's client (removes client collision)
        for (BlockPos newPos : newBox) {
            if (!current.contains(newPos)) {
                sendAir(player, newPos.toLocation());
            }
        }

        current.clear();
        current.addAll(newBox);
    }

    public void clearGhostBlocks(Player player) {
        Set<BlockPos> current = activeGhostBlocks.remove(player.getUniqueId());
        if (current != null && !current.isEmpty()) {
            for (BlockPos pos : current) {
                Block b = pos.getBlock();
                if (b != null) {
                    restoreBlock(player, b);
                }
            }
        }
    }

    private void ejectFromSolidBlock(Player player) {
        Location loc = player.getLocation();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();

        if (feet.getType().isSolid() || head.getType().isSolid()) {
            for (int radius = 1; radius <= 4; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -1; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            Location check = loc.clone().add(dx, dy, dz);
                            Block b1 = check.getBlock();
                            Block b2 = check.clone().add(0, 1, 0).getBlock();
                            if (!b1.getType().isSolid() && !b2.getType().isSolid()) {
                                check.setYaw(player.getLocation().getYaw());
                                check.setPitch(player.getLocation().getPitch());
                                player.teleport(check.add(0.5, 0.0, 0.5));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void sendAir(Player player, Location loc) {
        if (player == null || !player.isOnline() || loc == null) return;
        try {
            player.sendBlockChange(loc, Material.AIR, (byte) 0);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void restoreBlock(Player player, Block block) {
        if (player == null || !player.isOnline() || block == null) return;
        try {
            // 1.13+ getBlockData() via dynamic reflection
            Method getBlockData = Block.class.getMethod("getBlockData");
            Object blockData = getBlockData.invoke(block);
            Method sendBlockData = Player.class.getMethod("sendBlockChange", Location.class, Class.forName("org.bukkit.block.data.BlockData"));
            sendBlockData.invoke(player, block.getLocation(), blockData);
        } catch (Throwable t) {
            // 1.8.8 - 1.12.2 fallback
            try {
                player.sendBlockChange(block.getLocation(), block.getType(), block.getData());
            } catch (Throwable ignored) {}
        }
    }

    public void remove(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            clearGhostBlocks(player);
        } else {
            activeGhostBlocks.remove(uuid);
        }
        phasedPlayers.remove(uuid);
    }

    public void clearAll() {
        for (UUID uuid : phasedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                clearGhostBlocks(p);
            }
        }
        phasedPlayers.clear();
        activeGhostBlocks.clear();
    }

    public static class BlockPos {
        private final String worldName;
        private final int x, y, z;

        public BlockPos(String worldName, int x, int y, int z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Block getBlock() {
            World w = Bukkit.getWorld(worldName);
            return w != null ? w.getBlockAt(x, y, z) : null;
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(worldName);
            return w != null ? new Location(w, x, y, z) : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos b = (BlockPos) o;
            return x == b.x && y == b.y && z == b.z && Objects.equals(worldName, b.worldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldName, x, y, z);
        }
    }
}