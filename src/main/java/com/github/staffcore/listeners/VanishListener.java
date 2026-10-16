package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.lang.reflect.Method;
import java.util.Iterator;

public class VanishListener implements Listener {

    private final StaffCore plugin;

    public VanishListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getVanishManager().onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getVanishManager().onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        int vanishedCount = plugin.getVanishManager().getVanishedCount();
        if (vanishedCount > 0) {
            try {
                Method setNumPlayersMethod = event.getClass().getMethod("setNumPlayers", int.class);
                int targetCount = Math.max(0, event.getNumPlayers() - vanishedCount);
                setNumPlayersMethod.invoke(event, targetCount);
            } catch (Exception ignored) {}

            try {
                Iterator<Player> iterator = event.iterator();
                while (iterator.hasNext()) {
                    Player p = iterator.next();
                    if (plugin.getVanishManager().isVanished(p.getUniqueId())) {
                        iterator.remove();
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}