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
import java.util.UUID;

public class CheckGui {

    public static final String GUI_TITLE_PREFIX = ChatColor.DARK_GRAY + "Check: " + ChatColor.RED;
    public static final String HISTORY_TITLE_PREFIX = ChatColor.DARK_GRAY + "History: " + ChatColor.RED;
    public static final int ITEMS_PER_PAGE = 45;

    public static void openGui(StaffCore plugin, Player staff, OfflinePlayer target) {
        openGui(plugin, staff, target, null, false);
    }

    public static void openGui(StaffCore plugin, Player staff, OfflinePlayer target, boolean revealIp) {
        openGui(plugin, staff, target, null, revealIp);
    }

    public static void openGui(StaffCore plugin, Player staff, OfflinePlayer target, String preferredName, boolean revealIp) {
        String targetName = (preferredName != null && !preferredName.isEmpty() && !preferredName.equalsIgnoreCase("Unknown"))
                ? preferredName : ((target != null && target.getName() != null) ? target.getName() : "Unknown");

        String title = GUI_TITLE_PREFIX + targetName;
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Fill background with gray stained glass pane (cross-version 1.8.8 - 1.21+)
        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Resolve player's IP address (online or last known)
        String targetIp = "";
        Player onlineTarget = (target != null) ? target.getPlayer() : null;
        if (onlineTarget != null && onlineTarget.isOnline() && onlineTarget.getAddress() != null && onlineTarget.getAddress().getAddress() != null) {
            targetIp = onlineTarget.getAddress().getAddress().getHostAddress();
        } else {
            String savedIp = (target != null && target.getUniqueId() != null) ? plugin.getPunishmentManager().getLastKnownIp(target.getUniqueId()) : null;
            if (savedIp == null || savedIp.isEmpty()) {
                savedIp = plugin.getPunishmentManager().getLastKnownIpByName(targetName);
            }
            if (savedIp != null) {
                targetIp = savedIp;
            }
        }

        // Slot 4: Player Skull (cross-version 1.8.8 - 1.21+)
        ItemStack skull = createSkullItem();
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            setSkullOwner(skullMeta, target, targetName);
            skullMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + targetName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Status: " + ((target != null && target.isOnline()) ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
            if (target != null && target.getUniqueId() != null) {
                lore.add(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + target.getUniqueId());
            }

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

        // Retrieve full comprehensive punishment history (UUID + Name + IP + Alts)
        List<Punishment> history = getPlayerPunishments(plugin, target, targetName);

        int totalBans = 0;
        int totalMutes = 0;
        int totalJails = 0;
        int totalWarns = 0;
        int totalKicks = 0;
        int activeWarns = (target != null && target.getUniqueId() != null) ? plugin.getPunishmentManager().getActiveWarnCount(target.getUniqueId()) : 0;
        int maxWarns = plugin.getConfig().getInt("warning.max-warnings", 3);

        for (Punishment p : history) {
            if (p.getType() == null) continue;
            switch (p.getType()) {
                case BAN:
                case TEMPBAN:
                case IP_BAN:
                case TEMP_IP_BAN:
                    totalBans++;
                    break;
                case MUTE:
                case TEMP_MUTE:
                    totalMutes++;
                    break;
                case JAIL:
                case TEMP_JAIL:
                    totalJails++;
                    break;
                case WARN:
                    totalWarns++;
                    break;
                case KICK:
                    totalKicks++;
                    break;
            }
        }

        List<String> historyLore = new ArrayList<>();
        historyLore.add(ChatColor.GRAY + "Times Banned: " + (totalBans > 0 ? ChatColor.RED : ChatColor.GREEN) + totalBans);
        historyLore.add(ChatColor.GRAY + "Times Muted: " + (totalMutes > 0 ? ChatColor.GOLD : ChatColor.GREEN) + totalMutes);
        historyLore.add(ChatColor.GRAY + "Times Jailed: " + (totalJails > 0 ? ChatColor.LIGHT_PURPLE : ChatColor.GREEN) + totalJails);
        historyLore.add(ChatColor.GRAY + "Active Warnings: " + (activeWarns > 0 ? ChatColor.YELLOW : ChatColor.GREEN) + activeWarns + (maxWarns > 0 ? "/" + maxWarns : ""));
        historyLore.add(ChatColor.GRAY + "Total Warnings: " + (totalWarns > 0 ? ChatColor.YELLOW : ChatColor.GREEN) + totalWarns);
        historyLore.add(ChatColor.GRAY + "Times Kicked: " + (totalKicks > 0 ? ChatColor.AQUA : ChatColor.GREEN) + totalKicks);

        if (!history.isEmpty()) {
            historyLore.add("");
            historyLore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Recent Punishments (Reasons):");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            int limit = Math.min(history.size(), 6);
            for (int i = 0; i < limit; i++) {
                Punishment p = history.get(i);
                String dateStr = (p.getTimestamp() > 0) ? sdf.format(new Date(p.getTimestamp())) : "Unknown Date";
                String reasonStr = (p.getReason() != null && !p.getReason().isEmpty()) ? p.getReason() : "No reason specified";
                String staffStr = (p.getStaffName() != null && !p.getStaffName().isEmpty()) ? p.getStaffName() : "Console";
                String typeStr = (p.getType() != null) ? p.getType().name() : "PUNISH";

                historyLore.add(ChatColor.DARK_GRAY + "• " + formatTypeColor(p.getType()) + typeStr
                        + ChatColor.GRAY + " - " + ChatColor.WHITE + reasonStr
                        + ChatColor.DARK_GRAY + " (" + staffStr + ", " + dateStr + ")");
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
        UUID targetUUID = (target != null) ? target.getUniqueId() : null;
        Punishment activeBan = (targetUUID != null) ? plugin.getPunishmentManager().getActiveBan(targetUUID) : null;
        Punishment activeMute = (targetUUID != null) ? plugin.getPunishmentManager().getActiveMute(targetUUID) : null;
        Punishment activeJail = (targetUUID != null) ? plugin.getPunishmentManager().getActiveJail(targetUUID) : null;
        boolean isFrozen = target != null && target.isOnline() && plugin.getFreezeManager().isFrozen(target.getUniqueId());

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
                Collections.singletonList(ChatColor.GRAY + "Click to close this window")));

        staff.openInventory(inv);
    }

    public static void openHistoryGui(StaffCore plugin, Player staff, OfflinePlayer target) {
        openHistoryGui(plugin, staff, target, null, 1);
    }

    public static void openHistoryGui(StaffCore plugin, Player staff, OfflinePlayer target, int page) {
        openHistoryGui(plugin, staff, target, null, page);
    }

    /**
     * Opens a paginated 54-slot GUI showing every punishment record across multiple rows and pages.
     */
    public static void openHistoryGui(StaffCore plugin, Player staff, OfflinePlayer target, String preferredName, int page) {
        String targetName = (preferredName != null && !preferredName.isEmpty() && !preferredName.equalsIgnoreCase("Unknown"))
                ? preferredName : ((target != null && target.getName() != null) ? target.getName() : "Unknown");

        List<Punishment> history = getPlayerPunishments(plugin, target, targetName);
        int totalItems = history.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String baseTitle = HISTORY_TITLE_PREFIX + targetName;
        String fullTitle = (totalPages > 1) ? baseTitle + ChatColor.DARK_GRAY + " (" + page + "/" + totalPages + ")" : baseTitle;
        if (fullTitle.length() > 32) {
            fullTitle = fullTitle.substring(0, 32);
        }

        Inventory inv = Bukkit.createInventory(null, 54, fullTitle);

        // Fill records for the current page (Slots 0 to 44)
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            Punishment p = history.get(i);
            inv.setItem(slot, formatPunishmentItem(p, sdf));
        }

        // Fill bottom control bar background (Slots 45 to 53)
        ItemStack filler = createGrayPane();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Slot 45: Previous Page Arrow
        if (page > 1) {
            inv.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ Previous Page (" + (page - 1) + ")",
                    Collections.singletonList(ChatColor.GRAY + "Click to view newer punishments.")));
        }

        // Slot 49: Back Button to return to /check overview
        inv.setItem(49, createItem(matchCrossVersionMaterial("OAK_DOOR", "WOOD_DOOR"), ChatColor.RED + "" + ChatColor.BOLD + "Back to Check",
                Arrays.asList(
                        ChatColor.GRAY + "Click to return to " + ChatColor.YELLOW + targetName + ChatColor.GRAY + "'s overview.",
                        ChatColor.DARK_GRAY + "Target: " + targetName
                )));

        // Slot 50: Page / Record Summary Indicator
        inv.setItem(50, createItem(Material.PAPER, ChatColor.GOLD + "" + ChatColor.BOLD + "Page " + page + " of " + totalPages,
                Arrays.asList(
                        ChatColor.GRAY + "Total Punishments: " + ChatColor.YELLOW + totalItems,
                        ChatColor.GRAY + "Showing Records: " + ChatColor.WHITE + (totalItems == 0 ? 0 : (startIndex + 1)) + " - " + endIndex
                )));

        // Slot 53: Next Page Arrow
        if (page < totalPages) {
            inv.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Next Page (" + (page + 1) + ") ▶",
                    Collections.singletonList(ChatColor.GRAY + "Click to view older punishments.")));
        }

        staff.openInventory(inv);
    }

    private static ItemStack formatPunishmentItem(Punishment p, SimpleDateFormat sdf) {
        if (p == null) {
            return createItem(Material.PAPER, ChatColor.RED + "Corrupted Record", Collections.singletonList(ChatColor.GRAY + "Could not read punishment data."));
        }

        PunishmentType type = p.getType();
        Material icon;
        if (type == null) {
            icon = Material.PAPER;
        } else {
            switch (type) {
                case BAN:
                case TEMPBAN:
                case IP_BAN:
                case TEMP_IP_BAN:
                    icon = Material.REDSTONE_BLOCK;
                    break;
                case MUTE:
                case TEMP_MUTE:
                    icon = Material.BOOK;
                    break;
                case JAIL:
                case TEMP_JAIL:
                    icon = matchCrossVersionMaterial("IRON_BARS", "IRON_FENCE");
                    break;
                case WARN:
                    icon = matchCrossVersionMaterial("GOLD_NUGGET", "GOLD_NUGGET");
                    break;
                case KICK:
                    icon = matchCrossVersionMaterial("LEATHER_BOOTS", "LEATHER_BOOTS");
                    break;
                default:
                    icon = Material.PAPER;
                    break;
            }
        }

        boolean currentlyActive = p.isActive() && !p.isExpired();
        String dateStr = (p.getTimestamp() > 0) ? sdf.format(new Date(p.getTimestamp())) : "Unknown Date";
        String typeName = (type != null) ? type.name() : "PUNISHMENT";
        String reason = (p.getReason() != null && !p.getReason().isEmpty()) ? p.getReason() : "No reason specified";
        String staff = (p.getStaffName() != null && !p.getStaffName().isEmpty()) ? p.getStaffName() : "Console";

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + formatTypeColor(type) + typeName);
        lore.add(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + reason);
        lore.add(ChatColor.GRAY + "Punished By: " + ChatColor.YELLOW + staff);
        lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + dateStr);

        if (type != PunishmentType.WARN && type != PunishmentType.KICK) {
            String durationStr = (p.getDuration() > 0) ? TimeUtil.formatDuration(p.getDuration()) : "Permanent";
            lore.add(ChatColor.GRAY + "Duration: " + ChatColor.YELLOW + durationStr);
        }

        if (type == PunishmentType.KICK) {
            lore.add(ChatColor.GRAY + "Status: " + ChatColor.AQUA + "EXECUTED");
        } else if (type == PunishmentType.WARN) {
            lore.add(ChatColor.GRAY + "Status: " + (p.isActive() ? ChatColor.YELLOW + "ACTIVE WARNING" : ChatColor.GRAY + "EXPIRED / CLEARED"));
        } else {
            lore.add(ChatColor.GRAY + "Status: " + (currentlyActive ? ChatColor.RED + "ACTIVE" : ChatColor.GREEN + "INACTIVE / EXPIRED"));
        }

        return createItem(icon, formatTypeColor(type) + "" + ChatColor.BOLD + typeName + ChatColor.GRAY + " (" + dateStr + ")", lore);
    }

    /**
     * Resolves all punishment records for the target player using UUID, Name, IP, and Alt Accounts.
     */
    public static List<Punishment> getPlayerPunishments(StaffCore plugin, OfflinePlayer target, String targetName) {
        List<Punishment> matched = new ArrayList<>();
        if (plugin == null || plugin.getPunishmentManager() == null) return matched;

        UUID targetUUID = (target != null) ? target.getUniqueId() : null;
        String cleanTargetName = (targetName != null && !targetName.isEmpty() && !targetName.equalsIgnoreCase("Unknown"))
                ? targetName : ((target != null && target.getName() != null) ? target.getName() : null);

        // Fetch target IP address
        String targetIp = null;
        if (target != null && target.isOnline() && target.getPlayer() != null && target.getPlayer().getAddress() != null) {
            targetIp = target.getPlayer().getAddress().getAddress().getHostAddress();
        }
        if ((targetIp == null || targetIp.isEmpty()) && targetUUID != null) {
            targetIp = plugin.getPunishmentManager().getLastKnownIp(targetUUID);
        }
        if ((targetIp == null || targetIp.isEmpty()) && cleanTargetName != null) {
            targetIp = plugin.getPunishmentManager().getLastKnownIpByName(cleanTargetName);
        }

        List<String> altAccounts = (targetIp != null && !targetIp.isEmpty())
                ? plugin.getPunishmentManager().getAccountsByIp(targetIp) : Collections.emptyList();

        for (Punishment p : plugin.getPunishmentManager().getAllPunishments()) {
            if (p == null) continue;

            // 1. UUID Match
            boolean uuidMatch = (targetUUID != null && p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID));

            // 2. Name Match (case-insensitive)
            boolean nameMatch = (cleanTargetName != null && p.getTargetName() != null && p.getTargetName().equalsIgnoreCase(cleanTargetName));

            // 3. IP Match
            boolean ipMatch = (targetIp != null && !targetIp.isEmpty() && p.getTargetIp() != null && p.getTargetIp().equalsIgnoreCase(targetIp));

            // 4. Punishment targetName is the IP itself
            boolean nameIsIpMatch = (targetIp != null && !targetIp.isEmpty() && p.getTargetName() != null && p.getTargetName().equalsIgnoreCase(targetIp));

            // 5. Alt account match
            boolean altMatch = false;
            if (p.getTargetName() != null && !altAccounts.isEmpty()) {
                for (String alt : altAccounts) {
                    if (p.getTargetName().equalsIgnoreCase(alt)) {
                        altMatch = true;
                        break;
                    }
                }
            }

            if (uuidMatch || nameMatch || ipMatch || nameIsIpMatch || altMatch) {
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
            case KICK:
                return ChatColor.AQUA;
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