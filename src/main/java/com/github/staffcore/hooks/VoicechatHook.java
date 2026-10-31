package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Bukkit;

public class VoicechatHook {

    public static void tryRegister(StaffCore plugin) {
        try {
            if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
                return;
            }
            Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            registerInternal(plugin);
        } catch (Throwable ignored) {
        }
    }

    private static void registerInternal(StaffCore plugin) {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            service.registerPlugin(new StaffCoreVoicechatPlugin(plugin));
            plugin.getLogger().info("Successfully hooked into Simple Voice Chat! Vanished players will be silent to non-staff.");
        }
    }
}