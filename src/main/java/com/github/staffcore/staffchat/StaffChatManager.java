package com.github.staffcore.staffchat;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffChatManager {

    private final StaffCore plugin;
    private final Set<UUID> inStaffChat = ConcurrentHashMap.newKeySet();

    public StaffChatManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public boolean isInStaffChat(UUID uuid) {
        return inStaffChat.contains(uuid);
    }

    public void setStaffChat(Player player, boolean enabled) {
        if (enabled) {
            inStaffChat.add(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("staffchat.switched-staff", "&aEntered Staff Chat.")));
        } else {
            inStaffChat.remove(player.getUniqueId());
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("staffchat.switched-global", "&aReturned to Global Chat.")));
        }
    }

    public void sendStaffMessage(Player sender, String message) {
        String format = plugin.getConfig().getString("staffchat.format", "&8[&cStaffChat&8] &e%player%: &f%message%");
        String formatted = plugin.getMessageManager().color(format
                .replace("%player%", sender.getName())
                .replace("%message%", message));

        Bukkit.getConsoleSender().sendMessage(formatted);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("staffcore.staffchat")) {
                p.sendMessage(formatted);
            }
        }
    }
}
