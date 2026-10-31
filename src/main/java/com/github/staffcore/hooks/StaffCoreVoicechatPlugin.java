package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class StaffCoreVoicechatPlugin implements VoicechatPlugin {

    private final StaffCore plugin;

    public StaffCoreVoicechatPlugin(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginId() {
        return "staffcore";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // 1. Proximity / locational audio packets
        registration.registerEvent(LocationalSoundPacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            VoicechatConnection receiver = event.getReceiverConnection();
            if (sender == null || receiver == null) return;
            if (sender.getPlayer() == null || receiver.getPlayer() == null) return;

            UUID senderUuid = sender.getPlayer().getUuid();
            if (plugin.getVanishManager().isVanished(senderUuid)) {
                UUID receiverUuid = receiver.getPlayer().getUuid();
                Player receiverPlayer = Bukkit.getPlayer(receiverUuid);
                // Mute vanished player for any listener who lacks staff/see permissions
                if (receiverPlayer != null && !receiverPlayer.hasPermission("staffcore.vanish.see") && !receiverPlayer.hasPermission("staffcore.staff")) {
                    event.cancel();
                }
            }
        });

        // 2. Entity-bound audio packets (whisper/riding)
        registration.registerEvent(EntitySoundPacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            VoicechatConnection receiver = event.getReceiverConnection();
            if (sender == null || receiver == null) return;
            if (sender.getPlayer() == null || receiver.getPlayer() == null) return;

            UUID senderUuid = sender.getPlayer().getUuid();
            if (plugin.getVanishManager().isVanished(senderUuid)) {
                UUID receiverUuid = receiver.getPlayer().getUuid();
                Player receiverPlayer = Bukkit.getPlayer(receiverUuid);
                // Mute vanished player for any listener who lacks staff/see permissions
                if (receiverPlayer != null && !receiverPlayer.hasPermission("staffcore.vanish.see") && !receiverPlayer.hasPermission("staffcore.staff")) {
                    event.cancel();
                }
            }
        });
    }
}