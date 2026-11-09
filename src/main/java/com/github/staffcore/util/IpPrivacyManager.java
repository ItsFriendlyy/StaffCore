package com.github.staffcore.util;

import com.github.staffcore.StaffCore;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class IpPrivacyManager {

    private static String serverSalt = null;

    /**
     * Initializes a unique, persistent server salt from config.yml
     * so hashes remain consistent across restarts, but are impossible to decrypt.
     */
    public static void init(StaffCore plugin) {
        if (serverSalt == null) {
            serverSalt = plugin.getConfig().getString("privacy.salt");
            if (serverSalt == null || serverSalt.trim().isEmpty()) {
                serverSalt = UUID.randomUUID().toString().replace("-", "");
                plugin.getConfig().set("privacy.salt", serverSalt);
                plugin.saveConfig();
            }
        }
    }

    /**
     * Converts a raw IP address into a one-way cryptographic SHA-256 hash.
     * The actual IP address is never stored or logged anywhere.
     */
    public static String hashIp(String rawIp) {
        if (rawIp == null || rawIp.trim().isEmpty()) return "UNKNOWN";
        try {
            String salt = (serverSalt != null) ? serverSalt : "StaffCoreSecureSaltKey";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((rawIp.trim() + ":" + salt).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(rawIp.hashCode());
        }
    }

    /**
     * Returns the hashed IP directly from an online player.
     */
    public static String getHashedIp(Player player) {
        if (player == null || player.getAddress() == null || player.getAddress().getAddress() == null) {
            return "UNKNOWN";
        }
        return hashIp(player.getAddress().getAddress().getHostAddress());
    }
}