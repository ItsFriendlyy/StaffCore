package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;

public class VoicechatHook {

    public static void tryRegister(StaffCore plugin) {
        try {
            // 1. Check if Simple Voice Chat plugin is present and enabled
            if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
                return;
            }
            if (!Bukkit.getPluginManager().isPluginEnabled("voicechat")) {
                return;
            }

            // 2. Check if the Voicechat API classes exist on the server classpath
            Class<?> serviceClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");

            // 3. Delegate to isolated inner class so the JVM only loads Voicechat types if verified
            VoicechatRegistrar.register(plugin, serviceClass);
        } catch (Throwable ignored) {
            // Simple Voice Chat is not installed or API is missing - fail silently without errors
        }
    }

    /**
     * Isolated static class: The JVM will only load this class and its Voicechat imports
     * if tryRegister() verifies that voicechat is present and enabled.
     */
    private static class VoicechatRegistrar {
        private static void register(StaffCore plugin, Class<?> serviceClass) {
            try {
                @SuppressWarnings("unchecked")
                de.maxhenkel.voicechat.api.BukkitVoicechatService service =
                        (de.maxhenkel.voicechat.api.BukkitVoicechatService) Bukkit.getServicesManager().load(serviceClass);

                if (service != null) {
                    service.registerPlugin(new StaffCoreVoicechatPlugin(plugin));
                    plugin.getLogger().info("Successfully hooked into Simple Voice Chat! Muted players and vanished staff integrated.");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not register Simple Voice Chat hook: " + t.getMessage());
            }
        }
    }
}