package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PhaseListener implements Listener {

    private final StaffCore plugin;

    public PhaseListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Safely restore original game mode if player disconnects while phased
        plugin.getPhaseManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        // If an admin manually changes game mode via /gmc or /gms while phased, stop phase mode cleanly
        if (plugin.getPhaseManager().isPhased(player.getUniqueId()) && event.getNewGameMode() != GameMode.SPECTATOR) {
            plugin.getPhaseManager().remove(player.getUniqueId());
        }
    }
}