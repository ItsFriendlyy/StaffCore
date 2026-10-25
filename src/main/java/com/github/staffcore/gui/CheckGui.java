package com.github.staffcore.gui;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.Punishment;
import com.github.staffcore.punishment.PunishmentType;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CheckGui {

    public static final String GUI_TITLE_PREFIX = ChatColor.DARK_GRAY + "Check: " + ChatColor.RED;
    public static final String HISTORY_TITLE_PREFIX = ChatColor.DARK_GRAY + "History: " + ChatColor.RED;

    public static void openGui(StaffCore plugin, Player staff, OfflinePlayer target) {
        openGui(plugin, staff, target, false);
    }

    public static void openGui(StaffCore plugin, Player staff, OfflinePlayer target, boolean revealIp) {
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE_PREFIX + targetName);

        // Fill background with gray stained glass pane (cross-version 1.8.8 - 1.20+)
        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Resolve player's IP address (online or last known)
        String targetIp = "";
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline() && onlineTarget.getAddress() != null && onlineTarget.getAddress().getAddress() != null) {
            targetIp = onlineTarget.getAddress().getAddress().getHostAddress();
        } else {
            String savedIp = plugin.getPunishmentManager().getLastKnownIp(target.getUniqueId());
            if (savedIp == null || savedIp.isEmpty()) {
                savedIp = plugin.getPunishmentManager().getLastKnownIpByName(targetName);
            }
            if (savedIp != null) {
                targetIp = savedIp;
            }
        }

        // Slot 4: Player Skull (cross-version 1.8.8 - 1.20+)
        ItemStack skull = createSkullItem();
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            setSkullOwner(skullMeta, target, targetName);
            skullMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + targetName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Status: " + (target.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
            lore.add(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + target.getUniqueId());

            if (staff.hasPermission("staffcore.check.ip") || staff.isOp()) {
                if (revealIp) {
                    String ipDisplay = (targetIp != null && !targetIp.isEmpty()) ? targetIp : "Unknown";
                    lore.add(ChatColor.GRAY + "IP: " + ChatColor.YELLOW + ipDisplay + ChatColor.DARK_GRAY + " (Right-Click to hide)");
                } else {
                    lore.add(ChatColor.GRAY + "IP: " + ChatColor.RED + "" + ChatColor.BOLD + "[HIDDEN] " + ChatColor.YELLOW + "(Right-Click to reveal)");
                }

                // Show all accounts that have logged in from this IP location
                List<String> altAccounts = new ArrayList<>(plugin.getPunishmentManager().getAccountsByIp(targetIp));
                boolean containsSelf = false;
                for (String acc : altAccounts) {
                    if (acc.equalsIgnoreCase(targetName)) {
                        containsSelf = true;
                        break;
                    }
                }
                if (!containsSelf && !targetName.equals("Unknown")) {
                    altAccounts.add(0, targetName);
                }

                lore.add("");
                lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Accounts on this IP (" + altAccounts.size() + "):");
                for (String acc : altAccounts) {
                    lore.add(ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + acc);
                }
            }

            skullMeta.setLore(lore);
            skull.setItemMeta(skullMeta);
        }
        inv.setItem(4, skull);

        // Fetch statistics safely from online Player instance
        long ticksPlayed = 0;
        int kills = 0;
        int deaths = 0;

        if (onlineTarget != null && onlineTarget.isOnline()) {
            try {
                Statistic playStat;
                try {
                    playStat = Statistic.valueOf("PLAY_ONE_MINUTE");
                } catch (IllegalArgumentException e) {
                    playStat = Statistic.valueOf("PLAY_ONE_TICK");
                }
                ticksPlayed = onlineTarget.getStatistic(playStat);
                kills = onlineTarget.getStatistic(Statistic.PLAYER_KILLS);
                deaths = onlineTarget.getStatistic(Statistic.DEATHS);
            } catch (Throwable ignored) {}
        }

        long secondsPlayed = ticksPlayed / 20;
        long hours = secondsPlayed / 3600;
        long minutes = (secondsPlayed % 3600) / 60;
        long seconds = secondsPlayed % 60;
        String playtimeStr = onlineTarget != null ? String.format("%dh %dm %ds", hours, minutes, seconds) : "Offline (Requires login)";

        // Slot 10: Clock - Playtime
        Material clockMat = matchCrossVersionMaterial("CLOCK", "WATCH");
        inv.setItem(10, createItem(clockMat, ChatColor.YELLOW + "" + ChatColor.BOLD + "Playtime",
                Arrays.asList(
                        ChatColor.GRAY + "Total Time on Server:",
                        ChatColor.WHITE + playtimeStr,
                        onlineTarget != null ? ChatColor.DARK_GRAY + "(" + ticksPlayed + " ticks)" : ChatColor.DARK_GRAY + "(Player is offline)"
                )));

        double kd = deaths == 0 ? kills : (double) Math.round(((double) kills / deaths) * 100.0) / 100.0;

        // Slot 12: Diamond Sword - Combat Stats
        inv.setItem(12, createItem(Material.DIAMOND_SWORD, ChatColor.RED + "" + ChatColor.BOLD + "Combat Stats",
                Arrays.asList(
                        ChatColor.GRAY + "Player Kills: " + ChatColor.GREEN + kills,
                        ChatColor.GRAY + "Deaths: " + ChatColor.RED + deaths,
                        ChatColor.GRAY + "K/D Ratio: " + ChatColor.GOLD + kd
                )));

        // Punishment History counts & reasons
        int totalBans = plugin.getPunishmentManager().getBanCount(target.getUniqueId());
        int totalMutes = plugin.getPunishmentManager().getMuteCount(target.getUniqueId());
        int totalJails = plugin.getPunishmentManager().getJailCount(target.getUniqueId());
        int totalWarns = plugin.getPunishmentManager().getWarnCount(target.getUniqueId());
        int activeWarns = plugin.getPunishmentManager().getActiveWarnCount(target.getUniqueId());
        int maxWarns = plugin.getConfig().getInt("warning.max-warnings", 3);

        List<Punishment> history = getPlayerPunishments(plugin, target, targetName);
        List<String> historyLore = new ArrayList<>();
        historyLore.add(ChatColor.GRAY + "Times Banned: " + (totalBans > 0 ? ChatColor.RED : ChatColor.GREEN) + totalBans);
        historyLore.add(ChatColor.GRAY + "Times Muted: " + (totalMutes > 0 ? ChatColor.GOLD : ChatColor.GREEN) + totalMutes);
        historyLore.add(ChatColor.GRAY + "Times Jailed: " + (totalJails > 0 ? ChatColor.LIGHT_PURPLE : ChatColor.GREEN) + totalJails);
        historyLore.add(ChatColor.GRAY + "Active Warnings: " + (activeWarns > 0 ? ChatColor.YELLOW : ChatColor.GREEN) + activeWarns + (maxWarns > 0 ? "/" + maxWarns : ""));
        historyLore.add(ChatColor.GRAY + "Total Warnings: " + (totalWarns > 0 ? ChatColor.YELLOW : ChatColor.GREEN) + totalWarns);

        if (!history.isEmpty()) {
            historyLore.add("");
            historyLore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Recent Punishments (Reasons):");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            int limit = Math.min(history.size(), 6);
            for (int i = 0; i < limit; i++) {
                Punishment p = history.get(i);
                String dateStr = sdf.format(new Date(p.getTimestamp()));
                historyLore.add(ChatColor.DARK_GRAY + "• " + formatTypeColor(p.getType()) + p.getType().name()
                        + ChatColor.GRAY + " - " + ChatColor.WHITE + p.getReason()
                        + ChatColor.DARK_GRAY + " (" + p.getStaffName() + ", " + dateStr + ")");
            }
            if (history.size() > limit) {
                historyLore.add(ChatColor.DARK_GRAY + "  ...and " + (history.size() - limit) + " more.");
            }
            historyLore.add("");
            historyLore.add(ChatColor.YELLOW + "Click to view full punishment history!");
        } else {
            historyLore.add("");
            historyLore.add(ChatColor.GREEN + "No punishments on record.");
        }

        // Slot 14: Book & Quill - Punishment History
        Material bookMat = matchCrossVersionMaterial("WRITABLE_BOOK", "BOOK_AND_QUILL");
        inv.setItem(14, createItem(bookMat, ChatColor.AQUA + "" + ChatColor.BOLD + "Punishment History", historyLore));

        // Active Status
        Punishment activeBan = plugin.getPunishmentManager().getActiveBan(target.getUniqueId());
        Punishment activeMute = plugin.getPunishmentManager().getActiveMute(target.getUniqueId());
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(target.getUniqueId());
        boolean isFrozen = target.isOnline() && plugin.getFreezeManager().isFrozen(target.getUniqueId());

        Material statusMat = (activeBan != null || activeMute != null || activeJail != null || isFrozen) ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK;
        List<String> statusLore = new ArrayList<>();
        statusLore.add(ChatColor.GRAY + "Currently Banned: " + (activeBan != null ? ChatColor.RED + "YES (" + activeBan.getType().name() + ")" : ChatColor.GREEN + "NO"));
        if (activeBan != null) {
            statusLore.add(ChatColor.DARK_RED + " » Reason: " + ChatColor.WHITE + activeBan.getReason());
            statusLore.add(ChatColor.DARK_RED + " » By: " + ChatColor.YELLOW + activeBan.getStaffName());
            statusLore.add(ChatColor.DARK_RED + " » Remaining: " + ChatColor.YELLOW + TimeUtil.formatRemaining(activeBan.getExpireTime()));
        }
        statusLore.add(ChatColor.GRAY + "Currently Muted: " + (activeMute != null ? ChatColor.RED + "YES" : ChatColor.GREEN + "NO"));
        if (activeMute != null) {
            statusLore.add(ChatColor.GOLD + " » Reason: " + ChatColor.WHITE + activeMute.getReason());
            statusLore.add(ChatColor.GOLD + " » By: " + ChatColor.YELLOW + activeMute.getStaffName());
            statusLore.add(ChatColor.GOLD + " » Remaining: " + ChatColor.YELLOW + TimeUtil.formatRemaining(activeMute.getExpireTime()));
        }
        statusLore.add(ChatColor.GRAY + "Currently Jailed: " + (activeJail != null ? ChatColor.RED + "YES" : ChatColor.GREEN + "NO"));
        if (activeJail != null) {
            statusLore.add(ChatColor.LIGHT_PURPLE + " » Reason: " + ChatColor.WHITE + activeJail.getReason());
            statusLore.add(ChatColor.LIGHT_PURPLE + " » By: " + ChatColor.YELLOW + activeJail.getStaffName());
            statusLore.add(ChatColor.LIGHT_PURPLE + " » Remaining: " + ChatColor.YELLOW + TimeUtil.formatRemaining(activeJail.getExpireTime()));
        }
        statusLore.add(ChatColor.GRAY + "Currently Frozen: " + (isFrozen ? ChatColor.RED + "YES" : ChatColor.GREEN + "NO"));

        // Slot 16: Status
        inv.setItem(16, createItem(statusMat, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Active Status", statusLore));

        // Slot 22: Close Button
        inv.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close",
                Arrays.asList(ChatColor.GRAY + "Click to close this window")));

        staff.openInventory(inv);
    }

    /**
     * Opens a dedicated 54-slot GUI showing every punishment record and its reason for the target player.
     */
    public static void openHistoryGui(StaffCore plugin, Player staff, OfflinePlayer target) {
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        Inventory inv = Bukkit.createInventory(null, 54, HISTORY_TITLE_PREFIX + targetName);

        ItemStack filler = createGrayPane();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        List<Punishment> history = getPlayerPunishments(plugin, target, targetName);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        int slot = 0;
        for (Punishment p : history) {
            if (slot >= 45) break;

            Material icon = Material.PAPER;
            if (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMPBAN ||
                p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN) {
                icon = Material.REDSTONE_BLOCK;
            } else if (p.getType() == PunishmentType.MUTE || p.getType() == PunishmentType.TEMP_MUTE) {
                icon = Material.BOOK;
            } else if (p.getType() == PunishmentType.JAIL || p.getType() == PunishmentType.TEMP_JAIL) {
                icon = matchCrossVersionMaterial("IRON_BARS", "IRON_FENCE");
            }

            boolean currentlyActive = p.isActive() && !p.isExpired();
            String dateStr = sdf.format(new Date(p.getTimestamp()));
            String durationStr = p.getDuration() > 0 ? TimeUtil.formatDuration(p.getDuration()) : "Permanent";

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + formatTypeColor(p.getType()) + p.getType().name());
            lore.add(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + p.getReason());
            lore.add(ChatColor.GRAY + "Punished By: " + ChatColor.YELLOW + p.getStaffName());
            lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + dateStr);
            if (p.getType() != PunishmentType.WARN) {
                lore.add(ChatColor.GRAY + "Duration: " + ChatColor.YELLOW + durationStr);
            }
            lore.add(ChatColor.GRAY + "Status: " + (currentlyActive ? ChatColor.RED + "ACTIVE" : ChatColor.GREEN + "INACTIVE / EXPIRED"));

            inv.setItem(slot++, createItem(icon,
                    formatTypeColor(p.getType()) + "" + ChatColor.BOLD + p.getType().name() + ChatColor.GRAY + " (" + dateStr + ")",
                    lore));
        }

        // Slot 49: Back button to return to /check GUI
        inv.setItem(49, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Back to Check",
                Collections.singletonList(ChatColor.GRAY + "Click to return to " + targetName + "'s overview")));

        staff.openInventory(inv);
    }

    private static List<Punishment> getPlayerPunishments(StaffCore plugin, OfflinePlayer target, String targetName) {
        List<Punishment> matched = new ArrayList<>();
        for (Punishment p : plugin.getPunishmentManager().getAllPunishments()) {
            boolean uuidMatch = (target.getUniqueId() != null && p.getTargetUUID() != null && p.getTargetUUID().equals(target.getUniqueId()));
            boolean nameMatch = (targetName != null && p.getTargetName() != null && p.getTargetName().equalsIgnoreCase(targetName));
            if (uuidMatch || nameMatch) {
                matched.add(p);
            }
        }
        // Sort newest first
        matched.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return matched;
    }

    private static ChatColor formatTypeColor(PunishmentType type) {
        if (type == null) return ChatColor.WHITE;
        switch (type) {
            case BAN:
            case TEMPBAN:
            case IP_BAN:
            case TEMP_IP_BAN:
                return ChatColor.RED;
            case MUTE:
            case TEMP_MUTE:
                return ChatColor.GOLD;
            case JAIL:
            case TEMP_JAIL:
                return ChatColor.LIGHT_PURPLE;
            case WARN:
                return ChatColor.YELLOW;
            default:
                return ChatColor.WHITE;
        }
    }

    private static ItemStack createGrayPane() {
        ItemStack pane;
        try {
            Material mat = Material.valueOf("GRAY_STAINED_GLASS_PANE");
            pane = new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            pane = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
        }
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static ItemStack createSkullItem() {
        try {
            Material mat = Material.valueOf("PLAYER_HEAD");
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (short) 3);
        }
    }

    private static void setSkullOwner(SkullMeta meta, OfflinePlayer target, String targetName) {
        try {
            Method m = meta.getClass().getMethod("setOwningPlayer", OfflinePlayer.class);
            m.invoke(meta, target);
        } catch (Throwable t) {
            try {
                meta.setOwner(targetName);
            } catch (Throwable ignored) {}
        }
    }

    private static Material matchCrossVersionMaterial(String modernName, String legacyName) {
        try {
            return Material.valueOf(modernName);
        } catch (IllegalArgumentException e) {
            return Material.valueOf(legacyName);
        }
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}