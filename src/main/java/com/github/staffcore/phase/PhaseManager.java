package com.github.staffcore.phase;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseManager {

    private final StaffCore plugin;
    // UUID -> previous GameMode before entering Phase
    private final Map<UUID, GameMode> previousGameModes = new ConcurrentHashMap<>();

    public PhaseManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPhased(UUID uuid) {
        return previousGameModes.containsKey(uuid);
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
            GameMode current = player.getGameMode();
            if (current == GameMode.SPECTATOR) {
                current = GameMode.CREATIVE;
            }
            previousGameModes.put(uuid, current);

            // Put into spectator for 100% buttery smooth client-side noclip
            player.setGameMode(GameMode.SPECTATOR);

            // Automatically vanish so normal players never see a floating spectator head
            if (!plugin.getVanishManager().isVanished(uuid)) {
                plugin.getVanishManager().setVanished(player, true);
            }

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.enabled", "&8[&cStaffCore&8] &aPhase mode &2enabled&a! Smooth noclip active.")));
        } else {
            GameMode prev = previousGameModes.remove(uuid);
            if (prev == null || prev == GameMode.SPECTATOR) {
                prev = GameMode.SURVIVAL;
            }
            player.setGameMode(prev);

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.disabled", "&8[&cStaffCore&8] &cPhase mode &4disabled&c. Returned to &e" + prev.name() + "&c.")));
        }
    }

    public GameMode getPreviousGameMode(UUID uuid) {
        return previousGameModes.get(uuid);
    }

    public void remove(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        GameMode prev = previousGameModes.remove(uuid);
        if (player != null && player.isOnline() && prev != null) {
            player.setGameMode(prev);
        }
    }

    public void clearAll() {
        for (Map.Entry<UUID, GameMode> entry : previousGameModes.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.setGameMode(entry.getValue());
            }
        }
        previousGameModes.clear();
    }
}