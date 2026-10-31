package com.github.staffcore.vanish;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    private final StaffCore plugin;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final File dataFile;
    private FileConfiguration dataConfig;
    private BukkitTask actionBarTask;

    public VanishManager(StaffCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "vanished.yml");
        loadData();
        startActionBarTask();
    }

    public void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException ignored) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        vanishedPlayers.clear();
        List<String> list = dataConfig.getStringList("vanished");
        if (list != null) {
            for (String s : list) {
                try {
                    vanishedPlayers.add(UUID.fromString(s));
                } catch (Exception ignored) {}
            }
        }
    }

    public void saveData() {
        List<String> list = new ArrayList<String>();
        for (UUID uuid : vanishedPlayers) {
            list.add(uuid.toString());
        }
        dataConfig.set("vanished", list);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save vanished.yml: " + e.getMessage());
        }
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public int getVanishedCount() {
        int count = 0;
        for (UUID uuid : vanishedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public Set<UUID> getVanishedPlayers() {
        return vanishedPlayers;
    }

    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()));
    }

    public void setVanished(Player player, boolean vanish) {
        UUID uuid = player.getUniqueId();
        if (vanish) {
            vanishedPlayers.add(uuid);
            saveData();

            // Set dimmed [v] tag in tab list
            player.setPlayerListName(ChatColor.GRAY + "[v] " + ChatColor.ITALIC + player.getName());

            // Hide from regular players, keep visible for staff
            updateVisibility(player);

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.enabled", "&aYou are now vanished!")));

            // Suggest /phase command in chat when vanishing
            String phaseTip = plugin.getConfig().getString("vanish.phase-suggestion",
                    "&7Tip: You can use &e/phase &7to phase through blocks without spectator mode!");
            if (phaseTip != null && !phaseTip.trim().isEmpty()) {
                player.sendMessage(plugin.getMessageManager().color(phaseTip));
            }

            String staffAlert = plugin.getMessageManager().color(
                    plugin.getConfig().getString("vanish.staff-notify-vanish", "&8[&bVanish&8] &e%player% &7is now vanished.")
                            .replace("%player%", player.getName()));
            notifyStaff(staffAlert, player);
        } else {
            vanishedPlayers.remove(uuid);
            saveData();

            // Disable phase mode automatically upon unvanishing if active
            if (plugin.getPhaseManager() != null && plugin.getPhaseManager().isPhased(uuid)) {
                plugin.getPhaseManager().setPhased(player, false);
            }

            // Restore original name in tab list
            player.setPlayerListName(player.getName());

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
            if (other.isOp() || other.hasPermission("staffcore.vanish.see") || other.hasPermission("staffcore.staff")) {
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

    public void onPlayerJoin(Player player) {
        // If this joining player was saved as vanished, re-apply vanish automatically
        if (isVanished(player.getUniqueId())) {
            player.setPlayerListName(ChatColor.GRAY + "[v] " + ChatColor.ITALIC + player.getName());
            updateVisibility(player);
            player.sendMessage(plugin.getMessageManager().color("&aYou logged in while vanished. (Visible only to staff)"));

            String alert = plugin.getMessageManager().color("&8[&bVanish&8] &e" + player.getName() + " &7joined while vanished.");
            notifyStaff(alert, player);
        }

        // Hide other currently vanished players from this new player (unless they have permission)
        for (UUID uuid : vanishedPlayers) {
            Player vanished = Bukkit.getPlayer(uuid);
            if (vanished != null && vanished.isOnline() && !vanished.equals(player)) {
                if (player.isOp() || player.hasPermission("staffcore.vanish.see") || player.hasPermission("staffcore.staff")) {
                    show(player, vanished);
                } else {
                    hide(player, vanished);
                }
            }
        }
    }

    public void onPlayerQuit(Player player) {
        // We do NOT remove from vanishedPlayers so they stay vanished when relogging!
        if (plugin.getPhaseManager() != null) {
            plugin.getPhaseManager().remove(player.getUniqueId());
        }
    }

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

    private void sendActionBar(Player player, String message) {
        try {
            Method m = player.getClass().getMethod("sendActionBar", String.class);
            m.invoke(player, message);
        } catch (Exception e1) {
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
            if ((staff.isOp() || staff.hasPermission("staffcore.vanish.notify")) && !staff.equals(exempt)) {
                staff.sendMessage(message);
            }
        }
    }

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        saveData();
    }
}