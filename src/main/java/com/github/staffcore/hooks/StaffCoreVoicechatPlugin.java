package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffCoreVoicechatPlugin implements VoicechatPlugin {

    private final StaffCore plugin;
    private final Map<UUID, Long> lastActionBarMap = new ConcurrentHashMap<>();

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
                notifyMutedActionbar(senderUuid);
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
                notifyMutedActionbar(senderUuid);
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
                notifyMutedActionbar(senderUuid);
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

    private void notifyMutedActionbar(UUID uuid) {
        if (uuid == null) return;

        long now = System.currentTimeMillis();
        Long last = lastActionBarMap.get(uuid);
        // Throttle to display every 1.5 seconds while speaking so actionbar stays up without spam
        if (last != null && (now - last) < 1500L) {
            return;
        }
        lastActionBarMap.put(uuid, now);

        if (lastActionBarMap.size() > 500) {
            lastActionBarMap.entrySet().removeIf(entry -> (now - entry.getValue()) > 60000L);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String defaultMsg = ChatColor.WHITE + "" + ChatColor.BOLD + "You can't use voice chat because you are muted";
                String configMsg = plugin.getConfig().getString("messages.voicechat-muted", defaultMsg);
                String formatted = ChatColor.translateAlternateColorCodes('&', configMsg);
                sendActionBar(player, formatted);
            }
        });
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            // Modern Paper & Spigot
            Method m = player.getClass().getMethod("sendActionBar", String.class);
            m.invoke(player, message);
            return;
        } catch (Throwable ignored) {}

        try {
            // Spigot Bungee ChatMessageType API
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBarType = chatMessageTypeClass.getField("ACTION_BAR").get(null);
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Method fromLegacy = textComponentClass.getMethod("fromLegacyText", String.class);
            Object components = fromLegacy.invoke(null, message);

            Method spigotMethod = player.getClass().getMethod("spigot");
            Object spigot = spigotMethod.invoke(player);
            Method sendMethod = spigot.getClass().getMethod("sendMessage", chatMessageTypeClass, Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;"));
            sendMethod.invoke(spigot, actionBarType, components);
            return;
        } catch (Throwable ignored) {}

        try {
            // Legacy 1.8.8 NMS fallback
            String nms = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Object handle = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Object connection = handle.getClass().getField("playerConnection").get(handle);

            Class<?> chatSerializer = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent$ChatSerializer");
            Method a = chatSerializer.getMethod("a", String.class);
            Object chatComponent = a.invoke(null, "{\"text\":\"" + message.replace("\"", "\\\"") + "\"}");

            Class<?> packetClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutChat");
            Object packet = packetClass.getConstructor(Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent"), byte.class)
                    .newInstance(chatComponent, (byte) 2);

            Method sendPacket = connection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + nms + ".Packet"));
            sendPacket.invoke(connection, packet);
        } catch (Throwable ignored) {}
    }
}