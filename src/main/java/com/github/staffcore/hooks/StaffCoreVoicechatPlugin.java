package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
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
        // 1. Intercept microphone packets: mute talking at the source if muted in StaffCore
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender == null || sender.getPlayer() == null) return;

            UUID senderUuid = sender.getPlayer().getUuid();
            // If player is actively muted via /mute or /tempmute, drop their microphone packets
            if (plugin.getPunishmentManager().getActiveMute(senderUuid) != null) {
                event.cancel();
            }
        });

        // 2. Proximity / locational audio packets
        registration.registerEvent(LocationalSoundPacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            VoicechatConnection receiver = event.getReceiverConnection();
            if (sender == null || receiver == null) return;
            if (sender.getPlayer() == null || receiver.getPlayer() == null) return;

            UUID senderUuid = sender.getPlayer().getUuid();

            // Mute check: muted players cannot broadcast outgoing audio
            if (plugin.getPunishmentManager().getActiveMute(senderUuid) != null) {
                event.cancel();
                return;
            }

            // Vanish check: vanished staff members can only be heard by staff
            if (plugin.getVanishManager().isVanished(senderUuid)) {
                UUID receiverUuid = receiver.getPlayer().getUuid();
                Player receiverPlayer = Bukkit.getPlayer(receiverUuid);
                if (receiverPlayer != null && !receiverPlayer.hasPermission("staffcore.vanish.see") && !receiverPlayer.hasPermission("staffcore.staff")) {
                    event.cancel();
                }
            }
        });

        // 3. Entity-bound audio packets (whisper / riding)
        registration.registerEvent(EntitySoundPacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            VoicechatConnection receiver = event.getReceiverConnection();
            if (sender == null || receiver == null) return;
            if (sender.getPlayer() == null || receiver.getPlayer() == null) return;

            UUID senderUuid = sender.getPlayer().getUuid();

            // Mute check: muted players cannot broadcast outgoing audio
            if (plugin.getPunishmentManager().getActiveMute(senderUuid) != null) {
                event.cancel();
                return;
            }

            // Vanish check: vanished staff members can only be heard by staff
            if (plugin.getVanishManager().isVanished(senderUuid)) {
                UUID receiverUuid = receiver.getPlayer().getUuid();
                Player receiverPlayer = Bukkit.getPlayer(receiverUuid);
                if (receiverPlayer != null && !receiverPlayer.hasPermission("staffcore.vanish.see") && !receiverPlayer.hasPermission("staffcore.staff")) {
                    event.cancel();
                }
            }
        });
    }
}