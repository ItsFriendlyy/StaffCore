package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;

public class PlaceholderHook {

    public static void tryRegister(StaffCore plugin) {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                return;
            }
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            registerInternal(plugin);
        } catch (Throwable ignored) {
        }
    }

    private static void registerInternal(StaffCore plugin) {
        new StaffCoreExpansion(plugin).register();
        plugin.getLogger().info("Successfully hooked into PlaceholderAPI! TAB placeholders registered.");
    }
}