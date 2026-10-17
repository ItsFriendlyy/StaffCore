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

import java.lang.reflect.Method;

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
    public void onArmorClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title == null) return;

        boolean isArmor = title.startsWith(UtilityCommands.ARMOR_PREFIX);
        boolean isInvsee = title.startsWith(UtilityCommands.INVSEE_PREFIX);

        if (!isArmor && !isInvsee) {
            return;
        }

        String prefix = isArmor ? UtilityCommands.ARMOR_PREFIX : UtilityCommands.INVSEE_PREFIX;
        String targetName = ChatColor.stripColor(title.substring(prefix.length())).trim();
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            event.getWhoClicked().sendMessage(ChatColor.RED + "Target player is no longer online.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory top = event.getView().getTopInventory();
            syncArmorToPlayer(top, target);
            if (event.getWhoClicked() instanceof Player) {
                ((Player) event.getWhoClicked()).updateInventory();
            }
        });
    }

    @EventHandler
    public void onArmorClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null) return;

        boolean isArmor = title.startsWith(UtilityCommands.ARMOR_PREFIX);
        boolean isInvsee = title.startsWith(UtilityCommands.INVSEE_PREFIX);

        if (!isArmor && !isInvsee) {
            return;
        }

        String prefix = isArmor ? UtilityCommands.ARMOR_PREFIX : UtilityCommands.INVSEE_PREFIX;
        String targetName = ChatColor.stripColor(title.substring(prefix.length())).trim();
        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.isOnline()) {
            Inventory top = event.getView().getTopInventory();
            syncArmorToPlayer(top, target);
            if (event.getPlayer() instanceof Player) {
                ((Player) event.getPlayer()).updateInventory();
            }
        }
    }

    private void syncArmorToPlayer(Inventory top, Player target) {
        if (top == null || target == null || !target.isOnline()) return;

        ItemStack boots = top.getItem(0);
        ItemStack leggings = top.getItem(1);
        ItemStack chestplate = top.getItem(2);
        ItemStack helmet = top.getItem(3);
        target.getInventory().setArmorContents(new ItemStack[]{boots, leggings, chestplate, helmet});

        // Safe reflection for off-hand across 1.8.8 through 1.20+
        try {
            Method m = target.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class);
            m.invoke(target.getInventory(), top.getItem(4));
        } catch (Throwable ignored) {}

        target.updateInventory();
    }
}