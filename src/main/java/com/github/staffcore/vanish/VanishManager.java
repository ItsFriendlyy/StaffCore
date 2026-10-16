package com.github.staffcore.vanish;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    private final StaffCore plugin;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask actionBarTask;

    public VanishManager(StaffCore plugin) {
        this.plugin = plugin;
        startActionBarTask();
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

            // 1. Add [v] in tab list and dim the name
            player.setPlayerListName(ChatColor.GRAY + "[v] " + ChatColor.ITALIC + player.getName());

            // 2. Add invisibility so staff members see a translucent ghost effect
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

            // 3. Hide from players without permission, keep visible for staff
            updateVisibility(player);

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.enabled", "&aYou are now vanished!")));

            String staffAlert = plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.staff-notify-vanish", "&8[&bVanish&8] &e%player% &7is now vanished.")
                            .replace("%player%", player.getName()));
            notifyStaff(staffAlert, player);
        } else {
            vanishedPlayers.remove(uuid);

            // Restore normal tab list name
            player.setPlayerListName(player.getName());

            // Remove ghost invisibility
            player.removePotionEffect(PotionEffectType.INVISIBILITY);

            // Show to all players
            showPlayerToAll(player);

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.disabled", "&cYou are no longer vanished!")));

            String staffAlert = plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.staff-notify-unvanish", "&8[&bVanish&8] &e%player% &7is now visible.")
                            .replace("%player%", player.getName()));
            notifyStaff(staffAlert, player);
        }
    }

    public void updateVisibility(Player vanishedPlayer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(vanishedPlayer)) continue;
            if (other.isOp() || other.hasPermission("staffcore.vanish.see")) {
                show(other, vanishedPlayer);
            } else {
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
        for (UUID uuid : vanishedPlayers) {
            Player vanished = Bukkit.getPlayer(uuid);
            if (vanished != null && vanished.isOnline()) {
                if (joiningPlayer.isOp() || joiningPlayer.hasPermission("staffcore.vanish.see")) {
                    show(joiningPlayer, vanished);
                } else {
                    hide(joiningPlayer, vanished);
                }
            }
        }
    }

    public void onPlayerQuit(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
    }

    // Displays continuous green message below the screen
    private void startActionBarTask() {
        actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (vanishedPlayers.isEmpty()) return;
                for (UUID uuid : vanishedPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        sendActionBar(p, ChatColor.GREEN + "" + ChatColor.BOLD + "YOU ARE CURRENTLY VANISHED");
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 20L);
    }

    // Universal action bar sender (1.8 to 1.20+)
    private void sendActionBar(Player player, String message) {
        try {
            // Modern Spigot / Paper 1.11+
            Method m = player.getClass().getMethod("sendActionBar", String.class);
            m.invoke(player, message);
        } catch (Exception e1) {
            // 1.8 NMS packet fallback
            try {
                String nms = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");
                Object craftPlayer = craftPlayerClass.cast(player);
                Object handle = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
                Object connection = handle.getClass().getField("playerConnection").get(handle);

                Class<?> chatSerializer = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent$ChatSerializer");
                Method a = chatSerializer.getMethod("a", String.class);
                Object chatComponent = a.invoke(null, "{\"text\":\"" + message + "\"}");

                Class<?> packetClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutChat");
                Object packet = packetClass.getConstructor(Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent"), byte.class)
                        .newInstance(chatComponent, (byte) 2);

                Method sendPacket = connection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + nms + ".Packet"));
                sendPacket.invoke(connection, packet);
            } catch (Exception ignored) {}
        }
    }

    // Universal hidePlayer (1.8 vs 1.12+)
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

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        for (UUID uuid : vanishedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setPlayerListName(p.getName());
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                showPlayerToAll(p);
            }
        }
        vanishedPlayers.clear();
    }
}