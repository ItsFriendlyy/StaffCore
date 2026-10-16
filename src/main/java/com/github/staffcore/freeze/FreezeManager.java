package com.github.staffcore.freeze;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    private final StaffCore plugin;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public FreezeManager(StaffCore plugin) {
        this.plugin = plugin;
        startFreezeDisplayTask();
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public void setFrozen(Player target, boolean freeze, Player staff) {
        UUID uuid = target.getUniqueId();
        if (freeze) {
            frozenPlayers.add(uuid);
            sendFreezeNotice(target, staff != null ? staff.getName() : "Console");
            if (staff != null) {
                staff.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("freeze.frozen-staff-notify", "&aYou froze &e%target%&a.")
                                .replace("%target%", target.getName())));
            }
        } else {
            frozenPlayers.remove(uuid);
            target.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("freeze.unfrozen-target", "&aYou have been unfrozen by a staff member.")));
            if (staff != null) {
                staff.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("freeze.unfrozen-staff-notify", "&aYou unfroze &e%target%&a.")
                                .replace("%target%", target.getName())));
            }
        }
    }

    public void toggleFreeze(Player target, Player staff) {
        setFrozen(target, !isFrozen(target.getUniqueId()), staff);
    }

    private void sendFreezeNotice(Player target, String staffName) {
        String title = plugin.getMessageManager().color(plugin.getConfig().getString("freeze.title", "&c&lFROZEN"));
        String subtitle = plugin.getMessageManager().color(plugin.getConfig().getString("freeze.subtitle", "&eYou are frozen and being inspected by a staff member"));
        sendTitleUniversal(target, title, subtitle, 5, 45, 10);

        List<String> chatLines = plugin.getMessageManager().getMessageList("freeze.chat-message");
        for (String line : chatLines) {
            target.sendMessage(line.replace("%staff%", staffName).replace("%target%", target.getName()));
        }
    }

    private void startFreezeDisplayTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (frozenPlayers.isEmpty()) return;
                String title = plugin.getMessageManager().color(plugin.getConfig().getString("freeze.title", "&c&lFROZEN"));
                String subtitle = plugin.getMessageManager().color(plugin.getConfig().getString("freeze.subtitle", "&eYou are frozen and being inspected by a staff member"));

                for (UUID uuid : frozenPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        sendTitleUniversal(p, title, subtitle, 0, 45, 10);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 30L);
    }

    private void sendTitleUniversal(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Method m = player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            m.invoke(player, title, subtitle, fadeIn, stay, fadeOut);
        } catch (Exception e) {
            try {
                Method m2 = player.getClass().getMethod("sendTitle", String.class, String.class);
                m2.invoke(player, title, subtitle);
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        frozenPlayers.clear();
    }
}