package com.github.staffcore.listeners;

import com.github.staffcore.commands.UtilityCommands;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

public class UtilityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
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
            }
        }
    }

    @EventHandler
    public void onInvseeClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith(UtilityCommands.INVSEE_PREFIX)) {
            // Cancel modification on separators or info indicator
            int rawSlot = event.getRawSlot();
            if (rawSlot >= 36 && rawSlot <= 39) {
                event.setCancelled(true);
            } else if (rawSlot >= 45 && rawSlot <= 53) {
                event.setCancelled(true);
            }
        }
    }
}
