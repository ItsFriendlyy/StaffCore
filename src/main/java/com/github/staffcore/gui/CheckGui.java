package com.github.staffcore.gui;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.Punishment;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class CheckGui {

    public static final String GUI_TITLE_PREFIX = ChatColor.DARK_GRAY + "Check: " + ChatColor.RED;
    public static final String HISTORY_TITLE_PREFIX = ChatColor.DARK_GRAY + "History: " + ChatColor.DARK_RED;

    public static void openGui(StaffCore plugin, Player staff, OfflinePlayer target) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE_PREFIX + target.getName());

        ItemStack filler = getMaterialItem("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 7, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 4: Player Skull
        ItemStack skull = getMaterialItem("PLAYER_HEAD", "SKULL_ITEM", (short) 3, ChatColor.GOLD + "" + ChatColor.BOLD + target.getName());
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwner(target.getName());
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Status: " + (target.isOnline() ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"));
            lore.add(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + target.getUniqueId());
            if (target.isOnline() && staff.hasPermission("staffcore.check.ip")) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null && onlineTarget.getAddress() != null) {
                    lore.add(ChatColor.GRAY + "IP: " + ChatColor.YELLOW + onlineTarget.getAddress().getAddress().getHostAddress());
                }
            }
            skullMeta.setLore(lore);
            skull.setItemMeta(skullMeta);
        }
        inv.setItem(4, skull);

        // Fetch stats safely
        long ticksPlayed = 0;
        int kills = 0;
        int deaths = 0;

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            try {
                Statistic playStat = null;
                try { playStat = Statistic.valueOf("PLAY_ONE_TICK"); } catch (Exception ignored) {}
                if (playStat == null) {
                    try { playStat = Statistic.valueOf("PLAY_ONE_MINUTE"); } catch (Exception ignored) {}
                }
                if (playStat != null) {
                    ticksPlayed = onlineTarget.getStatistic(playStat);
                }
                kills = onlineTarget.getStatistic(Statistic.PLAYER_KILLS);
                deaths = onlineTarget.getStatistic(Statistic.DEATHS);
            } catch (Exception ignored) {}
        }

        long secondsPlayed = ticksPlayed / 20;
        long hours = secondsPlayed / 3600;
        long minutes = (secondsPlayed % 3600) / 60;
        long seconds = secondsPlayed % 60;
        String playtimeStr = onlineTarget != null ? String.format("%dh %dm %ds", hours, minutes, seconds) : "Offline (Requires login)";

        // Slot 10: Clock / Watch
        ItemStack clock = getMaterialItem("CLOCK", "WATCH", (short) 0, ChatColor.YELLOW + "" + ChatColor.BOLD + "Playtime");
        setItemLore(clock, Arrays.asList(
                ChatColor.GRAY + "Total Time on Server:",
                ChatColor.WHITE + playtimeStr,
                onlineTarget != null ? ChatColor.DARK_GRAY + "(" + ticksPlayed + " ticks)" : ChatColor.DARK_GRAY + "(Player is offline)"
        ));
        inv.setItem(10, clock);

        double kd = deaths == 0 ? kills : (double) Math.round(((double) kills / deaths) * 100.0) / 100.0;

        // Slot 12: Diamond Sword
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        setItemNameAndLore(sword, ChatColor.RED + "" + ChatColor.BOLD + "Combat Stats", Arrays.asList(
                ChatColor.GRAY + "Player Kills: " + ChatColor.GREEN + kills,
                ChatColor.GRAY + "Deaths: " + ChatColor.RED + deaths,
                ChatColor.GRAY + "K/D Ratio: " + ChatColor.GOLD + kd
        ));
        inv.setItem(12, sword);

        // Slot 14: Book & Quill with Reasons Preview
        int totalBans = plugin.getPunishmentManager().getBanCount(target.getUniqueId());
        int totalMutes = plugin.getPunishmentManager().getMuteCount(target.getUniqueId());
        int totalWarns = plugin.getPunishmentManager().getWarnCount(target.getUniqueId());
        List<Punishment> history = plugin.getPunishmentManager().getAllPunishments(target.getUniqueId());

        ItemStack book = getMaterialItem("WRITABLE_BOOK", "BOOK_AND_QUILL", (short) 0, ChatColor.AQUA + "" + ChatColor.BOLD + "Punishment History");
        List<String> bookLore = new ArrayList<String>();
        bookLore.add(ChatColor.GRAY + "Times Banned: " + (totalBans > 0 ? ChatColor.RED : ChatColor.GREEN) + totalBans);
        bookLore.add(ChatColor.GRAY + "Times Muted: " + (totalMutes > 0 ? ChatColor.GOLD : ChatColor.GREEN) + totalMutes);
        bookLore.add(ChatColor.GRAY + "Total Warnings: " + (totalWarns > 0 ? ChatColor.YELLOW : ChatColor.GREEN) + totalWarns);
        bookLore.add(ChatColor.DARK_GRAY + "--------------------------------");
        
        if (history.isEmpty()) {
            bookLore.add(ChatColor.GREEN + "No prior punishment records!");
        } else {
            bookLore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Recent Infraction Reasons:");
            int shown = 0;
            for (int i = history.size() - 1; i >= 0 && shown < 4; i--) {
                Punishment p = history.get(i);
                bookLore.add(ChatColor.RED + "• " + p.getType().name() + ": " + ChatColor.WHITE + p.getReason());
                shown++;
            }
        }
        bookLore.add(ChatColor.DARK_GRAY + "--------------------------------");
        bookLore.add(ChatColor.GREEN + "» Click to view detailed history log!");
        setItemLore(book, bookLore);
        inv.setItem(14, book);

        // Slot 16: Active Status with Active Reason
        Punishment activeBan = plugin.getPunishmentManager().getActiveBan(target.getUniqueId());
        Punishment activeMute = plugin.getPunishmentManager().getActiveMute(target.getUniqueId());
        boolean isFrozen = target.isOnline() && plugin.getFreezeManager().isFrozen(target.getUniqueId());

        Material statusMat = (activeBan != null || activeMute != null || isFrozen) ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK;
        ItemStack statusItem = new ItemStack(statusMat);

        List<String> statusLore = new ArrayList<String>();
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
        statusLore.add(ChatColor.GRAY + "Currently Frozen: " + (isFrozen ? ChatColor.RED + "YES" : ChatColor.GREEN + "NO"));
        setItemNameAndLore(statusItem, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Active Status", statusLore);
        inv.setItem(16, statusItem);

        // Slot 22: Close
        ItemStack barrier = new ItemStack(Material.BARRIER);
        setItemNameAndLore(barrier, ChatColor.RED + "" + ChatColor.BOLD + "Close", Arrays.asList(ChatColor.GRAY + "Click to close this window"));
        inv.setItem(22, barrier);

        staff.openInventory(inv);
    }

    public static void openHistoryGui(StaffCore plugin, Player staff, OfflinePlayer target) {
        Inventory inv = Bukkit.createInventory(null, 54, HISTORY_TITLE_PREFIX + target.getName());
        List<Punishment> history = plugin.getPunishmentManager().getAllPunishments(target.getUniqueId());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        if (history.isEmpty()) {
            ItemStack clean = new ItemStack(Material.PAPER);
            setItemNameAndLore(clean, ChatColor.GREEN + "" + ChatColor.BOLD + "Clean Record",
                    Arrays.asList(ChatColor.GRAY + "This player has no registered punishments."));
            inv.setItem(22, clean);
        } else {
            int slot = 0;
            for (int i = history.size() - 1; i >= 0 && slot < 45; i--) {
                Punishment p = history.get(i);
                Material mat = Material.PAPER;
                if (p.getType().name().contains("BAN")) mat = Material.REDSTONE_BLOCK;
                else if (p.getType().name().contains("MUTE")) mat = Material.GOLD_BLOCK;

                ItemStack item = new ItemStack(mat);
                List<String> lore = new ArrayList<String>();
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + p.getType().name());
                lore.add(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + p.getStaffName());
                lore.add(ChatColor.GRAY + "Reason: " + ChatColor.YELLOW + p.getReason());
                lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + sdf.format(new Date(p.getTimestamp())));
                lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + (p.getDuration() > 0 ? TimeUtil.formatDuration(p.getDuration()) : "Permanent"));
                lore.add(ChatColor.GRAY + "Status: " + (p.isActive() ? ChatColor.RED + "Active" : ChatColor.GREEN + "Expired/Revoked"));

                setItemNameAndLore(item, ChatColor.RED + "" + ChatColor.BOLD + p.getType().name() + " #" + (i + 1), lore);
                inv.setItem(slot++, item);
            }
        }

        // Slot 49: Back Button
        ItemStack back = new ItemStack(Material.ARROW);
        setItemNameAndLore(back, ChatColor.YELLOW + "" + ChatColor.BOLD + "« Back to Overview",
                Arrays.asList(ChatColor.GRAY + "Return to player overview"));
        inv.setItem(49, back);

        staff.openInventory(inv);
    }

    private static ItemStack getMaterialItem(String modernName, String legacyName, short legacyData, String displayName) {
        Material mat = null;
        try { mat = Material.valueOf(modernName); } catch (Exception ignored) {}
        if (mat == null) {
            try { mat = Material.valueOf(legacyName); } catch (Exception ignored) {}
        }
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat, 1, legacyData);
        if (displayName != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private static void setItemLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private static void setItemNameAndLore(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
}