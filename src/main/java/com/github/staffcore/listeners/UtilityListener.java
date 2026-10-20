package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public class UtilityListener implements Listener {

    private final StaffCore plugin;

    public UtilityListener() {
        this(JavaPlugin.getPlugin(StaffCore.class));
    }

    public UtilityListener(StaffCore plugin) {
        this.plugin = (plugin != null) ? plugin : JavaPlugin.getPlugin(StaffCore.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (UtilityCommands.isInvincible(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (UtilityCommands.isInvincible(player.getUniqueId())) {
                event.setCancelled(true);
                player.setFoodLevel(20);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInvseeClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(UtilityCommands.INVSEE_PREFIX)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();

        // Cancel click on any gray glass pane slots (spots that do nothing)
        // Slot 4: spacer between Armor and Off-Hand
        // Slots 6, 7, 8: empty spots in top row
        // Slots 9 to 17: divider row
        if (rawSlot == 4 || (rawSlot >= 6 && rawSlot <= 17)) {
            event.setCancelled(true);
            return;
        }

        String targetName = ChatColor.stripColor(title.substring(UtilityCommands.INVSEE_PREFIX.length())).trim();
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            event.getWhoClicked().sendMessage(ChatColor.RED + "Target player is no longer online.");
            return;
        }

        // Live two-way synchronization on the next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            UtilityCommands.syncGuiToTarget(top, target);
            if (event.getWhoClicked() instanceof Player) {
                ((Player) event.getWhoClicked()).updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInvseeDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(UtilityCommands.INVSEE_PREFIX)) {
            return;
        }

        // Cancel drag if it touches any gray glass pane
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == 4 || (rawSlot >= 6 && rawSlot <= 17)) {
                event.setCancelled(true);
                return;
            }
        }

        String targetName = ChatColor.stripColor(title.substring(UtilityCommands.INVSEE_PREFIX.length())).trim();
        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            Inventory top = event.getView().getTopInventory();
            Bukkit.getScheduler().runTask(plugin, () -> UtilityCommands.syncGuiToTarget(top, target));
        }
    }

    @EventHandler
    public void onInvseeClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(UtilityCommands.INVSEE_PREFIX)) {
            return;
        }

        String targetName = ChatColor.stripColor(title.substring(UtilityCommands.INVSEE_PREFIX.length())).trim();
        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            UtilityCommands.syncGuiToTarget(event.getView().getTopInventory(), target);
        }
    }
}