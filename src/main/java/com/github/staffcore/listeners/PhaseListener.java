package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.phase.PhaseManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public class PhaseListener implements Listener {

    private final StaffCore plugin;

    public PhaseListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPhaseManager().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPhaseManager().isPhased(player.getUniqueId()) && event.getNewGameMode() != GameMode.SPECTATOR) {
            plugin.getPhaseManager().remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhaseInvClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(PhaseManager.PHASE_INV_PREFIX)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot == 4 || (rawSlot >= 6 && rawSlot <= 17)) {
            event.setCancelled(true);
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Inventory top = event.getView().getTopInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPhaseManager().syncGuiToInventory(top, player);
                player.updateInventory();
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhaseInvDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(PhaseManager.PHASE_INV_PREFIX)) return;

        for (int slot : event.getRawSlots()) {
            if (slot == 4 || (slot >= 6 && slot <= 17)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Inventory top = event.getView().getTopInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPhaseManager().syncGuiToInventory(top, player);
                player.updateInventory();
            });
        }
    }

    @EventHandler
    public void onPhaseInvClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(PhaseManager.PHASE_INV_PREFIX)) return;

        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            plugin.getPhaseManager().syncGuiToInventory(event.getView().getTopInventory(), player);
            player.updateInventory();
        }
    }
}