package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.gui.CheckGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {

    private final StaffCore plugin;

    public GuiListener() {
        this.plugin = StaffCore.getPlugin(StaffCore.class);
    }

    public GuiListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Main Check GUI
        if (title.startsWith(CheckGui.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();

            if (slot == 22) {
                player.closeInventory();
            } else if (slot == 14) { // Click on the Punishment History book
                String targetName = title.replace(CheckGui.GUI_TITLE_PREFIX, "");
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                CheckGui.openHistoryGui(plugin, player, target);
            }
        } 
        // Full Punishment History Log GUI
        else if (title.startsWith(CheckGui.HISTORY_TITLE_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();

            if (slot == 49) { // Back Arrow
                String targetName = title.replace(CheckGui.HISTORY_TITLE_PREFIX, "");
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                CheckGui.openGui(plugin, player, target);
            }
        }
    }
}