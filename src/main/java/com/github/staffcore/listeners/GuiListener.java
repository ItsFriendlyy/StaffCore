package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import com.github.staffcore.gui.CheckGui;
import com.github.staffcore.gui.TeleportGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiListener implements Listener {

    private final StaffCore plugin;

    public GuiListener() {
        this(JavaPlugin.getPlugin(StaffCore.class));
    }

    public GuiListener(StaffCore plugin) {
        this.plugin = (plugin != null) ? plugin : JavaPlugin.getPlugin(StaffCore.class);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title == null) return;

        StaffCore core = (this.plugin != null) ? this.plugin : JavaPlugin.getPlugin(StaffCore.class);

        // 1. Main /check GUI
        if (title.startsWith(CheckGui.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player staff = (Player) event.getWhoClicked();
            String targetName = ChatColor.stripColor(title.substring(CheckGui.GUI_TITLE_PREFIX.length())).trim();
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            @SuppressWarnings("deprecation")
            OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);

            // Slot 4: Player Skull - Right-click to reveal/hide IP address
            if (event.getRawSlot() == 4 && event.isRightClick()) {
                if (!staff.hasPermission("staffcore.check.ip") && !staff.isOp()) return;

                boolean currentlyHidden = true;
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.hasItemMeta()) {
                    ItemMeta meta = clicked.getItemMeta();
                    if (meta != null && meta.hasLore()) {
                        List<String> lore = meta.getLore();
                        if (lore != null) {
                            for (String line : lore) {
                                if (ChatColor.stripColor(line).contains("Right-Click to hide")) {
                                    currentlyHidden = false;
                                    break;
                                }
                            }
                        }
                    }
                }
                CheckGui.openGui(core, staff, target, currentlyHidden);
                return;
            }

            // Slot 14: Punishment History - Click to open full punishment reasons list
            if (event.getRawSlot() == 14) {
                CheckGui.openHistoryGui(core, staff, target);
                return;
            }

            // Slot 22: Barrier close slot
            if (event.getRawSlot() == 22) {
                staff.closeInventory();
            }
            return;
        }

        // 2. Detailed Punishment History GUI
        if (title.startsWith(CheckGui.HISTORY_TITLE_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player staff = (Player) event.getWhoClicked();
            if (event.getRawSlot() == 49) {
                String targetName = ChatColor.stripColor(title.substring(CheckGui.HISTORY_TITLE_PREFIX.length())).trim();
                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                @SuppressWarnings("deprecation")
                OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);
                CheckGui.openGui(core, staff, target, false);
            }
            return;
        }

        // 3. Interactive Homes GUI (/home)
        if (title.startsWith(TeleportGui.HOME_GUI_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            int rawSlot = event.getRawSlot();

            // Slot 49: Close
            if (rawSlot == 49) {
                player.closeInventory();
                return;
            }

            // Slot 51: Mode Switcher (Bedrock / Sub-Menu Mode toggle)
            if (rawSlot == 51) {
                TeleportGui.toggleBedrockMode(player.getUniqueId());
                boolean nowBedrock = TeleportGui.isBedrockMode(player.getUniqueId());
                if (nowBedrock) {
                    player.sendMessage(core.getMessageManager().color("&6[StaffCore] &aSwitched to &eSub-Menu Mode &7(Touch & Bedrock friendly)!"));
                } else {
                    player.sendMessage(core.getMessageManager().color("&6[StaffCore] &aSwitched to &bDirect Java Mode &7(Left-click instant teleport)!"));
                }
                TeleportGui.openHomeGui(core, player);
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
                return;
            }

            String homeDisplayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim().toLowerCase();
            Map<String, Location> homes = UtilityCommands.playerHomes.get(player.getUniqueId());
            if (homes == null || !homes.containsKey(homeDisplayName)) {
                return;
            }

            ClickType click = event.getClick();

            // Java Shortcut: Shift + Right-Click -> Delete Home
            if (click == ClickType.SHIFT_RIGHT) {
                homes.remove(homeDisplayName);
                Map<String, String> icons = UtilityCommands.playerHomeIcons.get(player.getUniqueId());
                if (icons != null) icons.remove(homeDisplayName);
                UtilityCommands.saveStates(core);
                player.sendMessage(core.getMessageManager().color(
                        core.getConfig().getString("messages.delhome-success", "&aSuccessfully deleted home &e%home%&a.")
                                .replace("%home%", homeDisplayName)));
                TeleportGui.openHomeGui(core, player);
                return;
            }

            // Java Shortcut: Shift + Left-Click -> Rename Home via chat prompt
            if (click == ClickType.SHIFT_LEFT) {
                player.closeInventory();
                TeleportGui.renamingHomeMap.put(player.getUniqueId(), homeDisplayName);
                player.sendMessage(core.getMessageManager().color(
                        "&e&l[HOMES] &aPlease type the new name for home &6" + homeDisplayName + " &ain chat &7(or type &ccancel&7):"));
                return;
            }

            // Java Shortcut: Right-Click -> Open Icon Picker directly
            if (event.isRightClick()) {
                TeleportGui.openHomeIconPicker(core, player, homeDisplayName);
                return;
            }

            // Standard Left-Click / Bedrock single tap
            if (TeleportGui.isBedrockMode(player.getUniqueId())) {
                // Open touch-friendly action sub-menu for Bedrock players
                TeleportGui.openHomeActionsMenu(core, player, homeDisplayName);
                return;
            }

            // Direct Java Mode Left-Click: Instant Teleport
            Location loc = homes.get(homeDisplayName);
            if (loc != null && loc.getWorld() != null) {
                player.closeInventory();
                player.teleport(loc);
                player.sendMessage(core.getMessageManager().color(
                        core.getConfig().getString("messages.home-teleported", "&aTeleported to home &e%home%&a.")
                                .replace("%home%", homeDisplayName)));
            } else {
                player.sendMessage(core.getMessageManager().color(
                        core.getConfig().getString("messages.home-not-found", "&cHome &e%home% &cwas not found!")
                                .replace("%home%", homeDisplayName)));
            }
            return;
        }

        // 4. Bedrock / Touch Home Actions Sub-Menu ("Manage: <home_name>")
        if (title.startsWith(TeleportGui.HOME_ACTIONS_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            String homeName = ChatColor.stripColor(title.substring(TeleportGui.HOME_ACTIONS_PREFIX.length())).trim().toLowerCase();
            Map<String, Location> homes = UtilityCommands.playerHomes.get(player.getUniqueId());
            int rawSlot = event.getRawSlot();

            // Slot 22: Back button
            if (rawSlot == 22) {
                TeleportGui.openHomeGui(core, player);
                return;
            }

            if (homes == null || !homes.containsKey(homeName)) {
                player.sendMessage(core.getMessageManager().color("&cHome &e" + homeName + " &cwas not found!"));
                TeleportGui.openHomeGui(core, player);
                return;
            }

            // Slot 10: 🟢 Teleport Button
            if (rawSlot == 10) {
                Location loc = homes.get(homeName);
                if (loc != null && loc.getWorld() != null) {
                    player.closeInventory();
                    player.teleport(loc);
                    player.sendMessage(core.getMessageManager().color(
                            core.getConfig().getString("messages.home-teleported", "&aTeleported to home &e%home%&a.")
                                    .replace("%home%", homeName)));
                }
                return;
            }

            // Slot 12: 🎨 Change Icon Button
            if (rawSlot == 12) {
                TeleportGui.openHomeIconPicker(core, player, homeName);
                return;
            }

            // Slot 14: 🏷️ Rename Button
            if (rawSlot == 14) {
                player.closeInventory();
                TeleportGui.renamingHomeMap.put(player.getUniqueId(), homeName);
                player.sendMessage(core.getMessageManager().color(
                        "&e&l[HOMES] &aPlease type the new name for home &6" + homeName + " &ain chat &7(or type &ccancel&7):"));
                return;
            }

            // Slot 16: 🔴 Delete Button
            if (rawSlot == 16) {
                homes.remove(homeName);
                Map<String, String> icons = UtilityCommands.playerHomeIcons.get(player.getUniqueId());
                if (icons != null) icons.remove(homeName);
                UtilityCommands.saveStates(core);
                player.sendMessage(core.getMessageManager().color(
                        core.getConfig().getString("messages.delhome-success", "&aSuccessfully deleted home &e%home%&a.")
                                .replace("%home%", homeName)));
                TeleportGui.openHomeGui(core, player);
                return;
            }
            return;
        }

        // 5. Home Icon Picker GUI
        if (title.startsWith(TeleportGui.HOME_ICON_PICKER_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            String homeName = ChatColor.stripColor(title.substring(TeleportGui.HOME_ICON_PICKER_PREFIX.length())).trim().toLowerCase();
            int rawSlot = event.getRawSlot();

            if (rawSlot == 22) {
                if (TeleportGui.isBedrockMode(player.getUniqueId())) {
                    TeleportGui.openHomeActionsMenu(core, player, homeName);
                } else {
                    TeleportGui.openHomeGui(core, player);
                }
                return;
            }

            if (rawSlot >= 0 && rawSlot < TeleportGui.ICON_CHOICES.size()) {
                TeleportGui.IconChoice choice = TeleportGui.ICON_CHOICES.get(rawSlot);
                UtilityCommands.setHomeIcon(player.getUniqueId(), homeName, choice.id);
                UtilityCommands.saveStates(core);
                player.sendMessage(core.getMessageManager().color(
                        "&aSelected icon " + choice.displayName + " &afor home &e" + homeName + "&a!"));

                if (TeleportGui.isBedrockMode(player.getUniqueId())) {
                    TeleportGui.openHomeActionsMenu(core, player, homeName);
                } else {
                    TeleportGui.openHomeGui(core, player);
                }
            }
            return;
        }

        // 6. Interactive Warps Explorer GUI (/warp)
        if (title.startsWith(TeleportGui.WARP_GUI_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            int rawSlot = event.getRawSlot();

            int currentPage = 1;
            try {
                String pagePart = title.substring(TeleportGui.WARP_GUI_PREFIX.length()).split("/")[0].trim();
                currentPage = Integer.parseInt(pagePart);
            } catch (Throwable ignored) {}

            // Slot 45: Previous page
            if (rawSlot == 45 && currentPage > 1) {
                TeleportGui.openWarpGui(core, player, currentPage - 1);
                return;
            }

            // Slot 49: Close
            if (rawSlot == 49) {
                player.closeInventory();
                return;
            }

            // Slot 51: Info Book / Staff rename hint
            if (rawSlot == 51 && (player.hasPermission("staffcore.command.setwarp") || player.isOp())) {
                player.closeInventory();
                player.sendMessage(core.getMessageManager().color(
                        "&6&l[WARPS] &aTo rename a warp, type: &f/setwarp <new_name> &aand &f/delwarp <old_name>&a, or Right-Click any warp to customize its icon."));
                return;
            }

            // Slot 53: Next page
            if (rawSlot == 53) {
                TeleportGui.openWarpGui(core, player, currentPage + 1);
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
                return;
            }

            String warpName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).trim().toLowerCase();
            Location loc = UtilityCommands.warps.get(warpName);
            if (loc == null) return;

            ClickType click = event.getClick();

            // Staff Shift + Right-Click: Delete warp
            if (click == ClickType.SHIFT_RIGHT && (player.hasPermission("staffcore.command.setwarp") || player.isOp())) {
                UtilityCommands.warps.remove(warpName);
                UtilityCommands.warpIcons.remove(warpName);
                UtilityCommands.saveStates(core);
                player.sendMessage(core.getMessageManager().color(
                        core.getConfig().getString("messages.delwarp-success", "&aSuccessfully deleted warp &e%warp%&a.")
                                .replace("%warp%", warpName)));
                TeleportGui.openWarpGui(core, player, currentPage);
                return;
            }

            // Staff Right-Click: Edit warp icon
            if (event.isRightClick() && (player.hasPermission("staffcore.command.setwarp") || player.isOp())) {
                TeleportGui.openWarpIconPicker(core, player, warpName);
                return;
            }

            // Left-Click: Teleport to Warp
            if (loc.getWorld() != null) {
                player.closeInventory();
                player.teleport(loc);
                player.sendMessage(core.getMessageManager().color(
                        core.getConfig().getString("messages.warp-teleported", "&aTeleported to warp &e%warp%&a.")
                                .replace("%warp%", warpName)));
            }
            return;
        }

        // 7. Warp Icon Picker GUI
        if (title.startsWith(TeleportGui.WARP_ICON_PICKER_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            String warpName = ChatColor.stripColor(title.substring(TeleportGui.WARP_ICON_PICKER_PREFIX.length())).trim().toLowerCase();
            int rawSlot = event.getRawSlot();

            if (rawSlot == 22) {
                TeleportGui.openWarpGui(core, player, 1);
                return;
            }

            if (rawSlot >= 0 && rawSlot < TeleportGui.ICON_CHOICES.size()) {
                TeleportGui.IconChoice choice = TeleportGui.ICON_CHOICES.get(rawSlot);
                UtilityCommands.setWarpIcon(warpName, choice.id);
                UtilityCommands.saveStates(core);
                player.sendMessage(core.getMessageManager().color(
                        "&aSelected icon " + choice.displayName + " &afor warp &6" + warpName + "&a!"));
                TeleportGui.openWarpGui(core, player, 1);
            }
        }
    }

    /**
     * Handles chat input for home renaming when clicking the Name Tag / Shift-Click in /home.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (TeleportGui.renamingHomeMap.containsKey(uuid)) {
            event.setCancelled(true);
            String oldName = TeleportGui.renamingHomeMap.remove(uuid);
            String newName = event.getMessage().trim().toLowerCase();

            if (newName.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.getMessageManager().color("&cHome rename cancelled."));
                Bukkit.getScheduler().runTask(plugin, () -> TeleportGui.openHomeGui(plugin, player));
                return;
            }

            if (newName.isEmpty() || newName.contains(" ") || newName.length() > 16) {
                player.sendMessage(plugin.getMessageManager().color("&cInvalid home name! Must be 1-16 characters without spaces."));
                Bukkit.getScheduler().runTask(plugin, () -> TeleportGui.openHomeGui(plugin, player));
                return;
            }

            Map<String, Location> homes = UtilityCommands.playerHomes.get(uuid);
            if (homes != null && homes.containsKey(oldName)) {
                Location loc = homes.remove(oldName);
                homes.put(newName, loc);

                // Transfer customized icon
                String icon = UtilityCommands.getHomeIcon(uuid, oldName);
                UtilityCommands.setHomeIcon(uuid, newName, icon);
                Map<String, String> icons = UtilityCommands.playerHomeIcons.get(uuid);
                if (icons != null) icons.remove(oldName);

                UtilityCommands.saveStates(plugin);
                player.sendMessage(plugin.getMessageManager().color(
                        "&aSuccessfully renamed home &e" + oldName + " &ato &6" + newName + "&a!"));
            }

            Bukkit.getScheduler().runTask(plugin, () -> TeleportGui.openHomeGui(plugin, player));
        }
    }
}