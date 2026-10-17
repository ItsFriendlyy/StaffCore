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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class UtilityListener implements Listener {

    private final StaffCore plugin;

    public UtilityListener() {
        this(JavaPlugin.getPlugin(StaffCore.class));
    }

    public UtilityListener(StaffCore plugin) {
        this.plugin = (plugin != null) ? plugin : JavaPlugin.getPlugin(StaffCore.class);
    }

    /**
     * Prevents damage to players with /invincible enabled
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (UtilityCommands.isInvincible(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents hunger loss for players with /invincible enabled
     */
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

    /**
     * Synchronizes edits made in /invsee back to the target player in real time.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(UtilityCommands.INVSEE_PREFIX)) {
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

        int rawSlot = event.getRawSlot();

        // Cancel clicks on divider glass panes (36-44) and info paper (53)
        if ((rawSlot >= 36 && rawSlot <= 44) || rawSlot == 53) {
            event.setCancelled(true);
            return;
        }

        // Allow all player inventory (0-35) and armor (45-48) edits
        // Sync modified items to the target player on the next game tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory top = event.getView().getTopInventory();
            syncGuiToPlayer(top, target);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(UtilityCommands.INVSEE_PREFIX)) {
            return;
        }

        String targetName = ChatColor.stripColor(title.substring(UtilityCommands.INVSEE_PREFIX.length())).trim();
        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            Inventory top = event.getView().getTopInventory();
            syncGuiToPlayer(top, target);
        }
    }

    private void syncGuiToPlayer(Inventory top, Player target) {
        if (top == null || target == null || !target.isOnline()) return;

        // 1. Sync storage slots (0-35)
        for (int i = 0; i < 36; i++) {
            target.getInventory().setItem(i, top.getItem(i));
        }

        // 2. Sync armor slots (45: Boots, 46: Leggings, 47: Chestplate, 48: Helmet)
        ItemStack boots = top.getItem(45);
        ItemStack leggings = top.getItem(46);
        ItemStack chestplate = top.getItem(47);
        ItemStack helmet = top.getItem(48);
        target.getInventory().setArmorContents(new ItemStack[]{boots, leggings, chestplate, helmet});

        target.updateInventory();
    }
}