package com.github.staffcore.hooks;

import com.github.staffcore.StaffCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class StaffCoreExpansion extends PlaceholderExpansion implements Relational {

    private final StaffCore plugin;

    public StaffCoreExpansion(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "staffcore";
    }

    @Override
    public String getAuthor() {
        return "StaffCore";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params == null) return null;
        String lower = params.toLowerCase();

        // 1. Online count placeholders for TAB & Scoreboard
        // %staffcore_online% -> Visible player count (real players minus vanished players)
        if (lower.equals("online") || lower.equals("online_players") || lower.equals("visible_online")) {
            int total = Bukkit.getOnlinePlayers().size();
            int vanished = plugin.getVanishManager().getVanishedCount();
            return String.valueOf(Math.max(0, total - vanished));
        }

        // %staffcore_online_total% -> Absolute online player count
        if (lower.equals("online_total") || lower.equals("total_online")) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }

        // %staffcore_vanished_count% -> Number of vanished players currently on the server
        if (lower.equals("vanished_count") || lower.equals("vanish_count")) {
            return String.valueOf(plugin.getVanishManager().getVanishedCount());
        }

        if (offlinePlayer == null) return null;

        boolean isVanished = plugin.getVanishManager().isVanished(offlinePlayer.getUniqueId());
        boolean isOnline = offlinePlayer.isOnline();

        // 2. Vanish boolean checks
        if (lower.equals("vanished") || lower.equals("is_vanished")) {
            return isVanished ? "yes" : "no";
        }

        // 3. Player status (when vanished, it displays "Offline" / "Not in the server")
        if (lower.equals("status") || lower.equals("player_status")) {
            if (!isOnline || isVanished) {
                return "Offline";
            }
            return "Online";
        }

        if (lower.equals("server_presence") || lower.equals("in_server")) {
            if (!isOnline || isVanished) {
                return "Not in the server";
            }
            return "In the server";
        }

        // %staffcore_tab_vanished% -> [V] badge for TAB tag if vanished
        if (lower.equals("tab_vanished") || lower.equals("vanish_tag")) {
            return isVanished ? "&8[&bV&8] " : "";
        }

        // %staffcore_phase% -> Phase state
        if (lower.equals("phase") || lower.equals("is_phased")) {
            return (plugin.getPhaseManager() != null && plugin.getPhaseManager().isPhased(offlinePlayer.getUniqueId())) ? "Enabled" : "Disabled";
        }

        return null;
    }

    /**
     * Relational placeholders for TAB: %rel_staffcore_<placeholder>%
     * Allows staff members to see vanished players while hiding them from normal players in TAB!
     */
    @Override
    public String onPlaceholderRequest(Player one, Player two, String params) {
        if (one == null || two == null || params == null) return null;
        String lower = params.toLowerCase();

        boolean twoVanished = plugin.getVanishManager().isVanished(two.getUniqueId());
        boolean oneCanSee = one.hasPermission("staffcore.vanish.see") || one.hasPermission("staffcore.staff");

        // %rel_staffcore_status%
        if (lower.equals("status") || lower.equals("player_status")) {
            if (twoVanished) {
                return oneCanSee ? "&bVanished" : "&cOffline";
            }
            return two.isOnline() ? "&aOnline" : "&cOffline";
        }

        // %rel_staffcore_in_server%
        if (lower.equals("in_server") || lower.equals("server_presence")) {
            if (twoVanished) {
                return oneCanSee ? "&bIn the server (Vanished)" : "&cNot in the server";
            }
            return two.isOnline() ? "&aIn the server" : "&cNot in the server";
        }

        // %rel_staffcore_vanished%
        if (lower.equals("vanished") || lower.equals("is_vanished")) {
            if (twoVanished) {
                return oneCanSee ? "yes" : "no";
            }
            return "no";
        }

        // %rel_staffcore_tab_prefix%
        if (lower.equals("tab_prefix") || lower.equals("tab_vanished") || lower.equals("vanish_tag")) {
            if (twoVanished && oneCanSee) {
                return "&8[&bV&8] ";
            }
            return "";
        }

        return null;
    }
}