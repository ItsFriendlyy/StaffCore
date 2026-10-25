package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.gui.CheckGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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
        if (title != null && title.startsWith(CheckGui.GUI_TITLE_PREFIX)) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }
            Player staff = (Player) event.getWhoClicked();

            // Slot 4: Player Skull - Right-click to reveal/hide IP address
            if (event.getRawSlot() == 4 && event.isRightClick()) {
                if (!staff.hasPermission("staffcore.check.ip") && !staff.isOp()) {
                    return;
                }

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

                String targetName = ChatColor.stripColor(title.substring(CheckGui.GUI_TITLE_PREFIX.length())).trim();
                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                @SuppressWarnings("deprecation")
                OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);

                StaffCore core = (this.plugin != null) ? this.plugin : JavaPlugin.getPlugin(StaffCore.class);
                CheckGui.openGui(core, staff, target, currentlyHidden);
                return;
            }

            // Slot 22: Barrier close slot
            if (event.getRawSlot() == 22) {
                staff.closeInventory();
            }
        }
    }
}