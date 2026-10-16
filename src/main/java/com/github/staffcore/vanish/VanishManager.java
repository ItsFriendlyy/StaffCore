package com.github.staffcore.vanish;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    private final StaffCore plugin;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public VanishManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public int getVanishedCount() {
        return vanishedPlayers.size();
    }

    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()));
    }

    public void setVanished(Player player, boolean vanish) {
        UUID uuid = player.getUniqueId();
        if (vanish) {
            vanishedPlayers.add(uuid);
            hidePlayerFromNonStaff(player);
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.enabled", "&aYou are now vanished!")));

            String staffAlert = plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.staff-notify-vanish", "&8[&bVanish&8] &e%player% &7is now vanished.")
                            .replace("%player%", player.getName()));
            notifyStaff(staffAlert, player);
        } else {
            vanishedPlayers.remove(uuid);
            showPlayerToAll(player);
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.disabled", "&cYou are no longer vanished!")));

            String staffAlert = plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.staff-notify-unvanish", "&8[&bVanish&8] &e%player% &7is now visible.")
                            .replace("%player%", player.getName()));
            notifyStaff(staffAlert, player);
        }
    }

    public void hidePlayerFromNonStaff(Player vanishedPlayer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(vanishedPlayer)) continue;
            if (!other.hasPermission("staffcore.vanish.see")) {
                hide(other, vanishedPlayer);
            }
        }
    }

    public void showPlayerToAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            show(other, player);
        }
    }

    public void onPlayerJoin(Player joiningPlayer) {
        if (!joiningPlayer.hasPermission("staffcore.vanish.see")) {
            for (UUID uuid : vanishedPlayers) {
                Player vanished = Bukkit.getPlayer(uuid);
                if (vanished != null && vanished.isOnline()) {
                    hide(joiningPlayer, vanished);
                }
            }
        }
    }

    public void onPlayerQuit(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
    }

    // Cross-version reflection for hidePlayer (1.8 vs 1.12+)
    private void hide(Player viewer, Player target) {
        try {
            Method m = viewer.getClass().getMethod("hidePlayer", org.bukkit.plugin.Plugin.class, Player.class);
            m.invoke(viewer, plugin, target);
        } catch (Exception e) {
            viewer.hidePlayer(target);
        }
    }

    private void show(Player viewer, Player target) {
        try {
            Method m = viewer.getClass().getMethod("showPlayer", org.bukkit.plugin.Plugin.class, Player.class);
            m.invoke(viewer, plugin, target);
        } catch (Exception e) {
            viewer.showPlayer(target);
        }
    }

    private void notifyStaff(String message, Player exempt) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("staffcore.vanish.notify") && !staff.equals(exempt)) {
                staff.sendMessage(message);
            }
        }
    }
}