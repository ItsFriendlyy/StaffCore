package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishListener implements Listener {

    private final StaffCore plugin;
    public static final String SILENT_CONTAINER_PREFIX = ChatColor.DARK_GRAY + "Silent: " + ChatColor.DARK_AQUA;
    private final Map<UUID, InventoryHolder> activeSilentContainers = new ConcurrentHashMap<>();

    public VanishListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getVanishManager().onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getVanishManager().onPlayerQuit(event.getPlayer());
        activeSilentContainers.remove(event.getPlayer().getUniqueId());
    }

   @EventHandler
    public void onPing(ServerListPingEvent event) {
        int vanishedCount = plugin.getVanishManager().getVanishedCount();
        if (vanishedCount > 0) {
            try {
                int realCount = event.getNumPlayers();
                java.lang.reflect.Method m = event.getClass().getMethod("setNumPlayers", int.class);
                m.invoke(event, Math.max(0, realCount - vanishedCount));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Silent Chest & Shulker Opening:
     * When vanished or in /phase, right-clicking containers opens them without animation or sound.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        boolean isVanished = plugin.getVanishManager().isVanished(player.getUniqueId());
        boolean isPhased = plugin.getPhaseManager() != null && plugin.getPhaseManager().isPhased(player.getUniqueId());

        if (!isVanished && !isPhased) return;

        Material mat = block.getType();
        String matName = mat.name();

        boolean isChest = matName.contains("CHEST") && !matName.contains("ENDER");
        boolean isShulker = matName.contains("SHULKER");
        boolean isBarrel = matName.contains("BARREL");
        boolean isEnderChest = matName.contains("ENDER_CHEST");
        boolean isOtherContainer = matName.contains("HOPPER") || matName.contains("DROPPER") || matName.contains("DISPENSER");

        if (!isChest && !isShulker && !isBarrel && !isEnderChest && !isOtherContainer) {
            return;
        }

        // Cancel vanilla event so NO animation and NO sound is played for other players
        event.setCancelled(true);

        if (isEnderChest) {
            player.openInventory(player.getEnderChest());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bVanish&8] &7Opened &eEnder Chest &7silently."));
            return;
        }

        BlockState state = block.getState();
        Inventory targetInv = null;
        InventoryHolder holder = null;

        if (state instanceof InventoryHolder) {
            holder = (InventoryHolder) state;
            if (state instanceof Chest) {
                targetInv = ((Chest) state).getBlockInventory();
            } else {
                targetInv = ((InventoryHolder) state).getInventory();
            }
        }

        if (targetInv == null) return;

        String friendlyName = formatContainerName(matName);
        String title = SILENT_CONTAINER_PREFIX + friendlyName;
        if (title.length() > 32) title = title.substring(0, 32);

        // Open an unlinked custom mirror view so the chest block lid NEVER opens on client screens
        Inventory silentView = Bukkit.createInventory(player, targetInv.getSize(), title);
        silentView.setContents(targetInv.getContents());

        activeSilentContainers.put(player.getUniqueId(), holder);
        player.openInventory(silentView);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bVanish&8] &7Opened &e" + friendlyName + " &7silently (no animation or sound)."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSilentInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(SILENT_CONTAINER_PREFIX)) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        InventoryHolder holder = activeSilentContainers.get(player.getUniqueId());
        if (holder == null) return;

        Inventory top = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                holder.getInventory().setContents(top.getContents());
            } catch (Throwable ignored) {}
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSilentInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(SILENT_CONTAINER_PREFIX)) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        InventoryHolder holder = activeSilentContainers.get(player.getUniqueId());
        if (holder == null) return;

        Inventory top = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                holder.getInventory().setContents(top.getContents());
            } catch (Throwable ignored) {}
        });
    }

    @EventHandler
    public void onSilentInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(SILENT_CONTAINER_PREFIX)) return;

        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        InventoryHolder holder = activeSilentContainers.remove(player.getUniqueId());
        if (holder != null) {
            try {
                holder.getInventory().setContents(event.getView().getTopInventory().getContents());
            } catch (Throwable ignored) {}
        }
    }

    private String formatContainerName(String raw) {
        String clean = raw.toLowerCase().replace("trapped_", "").replace("_", " ");
        if (clean.contains("shulker box")) return "Shulker Box";
        if (clean.contains("chest")) return "Chest";
        if (clean.contains("barrel")) return "Barrel";
        if (clean.contains("hopper")) return "Hopper";
        if (clean.contains("dispenser")) return "Dispenser";
        if (clean.contains("dropper")) return "Dropper";
        return "Container";
    }
}