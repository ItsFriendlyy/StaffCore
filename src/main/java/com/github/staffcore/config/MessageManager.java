package com.github.staffcore.config;

import com.github.staffcore.StaffCore;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class MessageManager {

    private final StaffCore plugin;

    public MessageManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public String color(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public List<String> color(List<String> messages) {
        List<String> list = new ArrayList<>();
        if (messages != null) {
            for (String s : messages) {
                list.add(color(s));
            }
        }
        return list;
    }

    public String getPrefix() {
        return color(plugin.getConfig().getString("prefix", "&8[&cStaffCore&8] &r"));
    }

    public String getMessage(String path) {
        String msg = plugin.getConfig().getString(path, "&cMessage not found: " + path);
        return color(msg);
    }

    public String getPrefixedMessage(String path) {
        return getPrefix() + getMessage(path);
    }

    public List<String> getMessageList(String path) {
        return color(plugin.getConfig().getStringList(path));
    }
}
