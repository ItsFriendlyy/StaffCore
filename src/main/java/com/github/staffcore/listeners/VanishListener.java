package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishListener implements Listener {

    private final StaffCore plugin;
    public static final String SILENT_CONTAINER_PREFIX = ChatColor.DARK_GRAY + "Silent: " + ChatColor.DARK_AQUA;
    private final Map<UUID, Block> activeSilentBlocks = new ConcurrentHashMap<>();

    public VanishListener(StaffCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinLowest(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player.getUniqueId())) {
            event.setJoinMessage(null);
            player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        } else {
            player.removeMetadata("vanished", plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player.getUniqueId())) {
            event.setJoinMessage(null);
        }
        plugin.getVanishManager().onPlayerJoin(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitLowest(PlayerQuitEvent event) {
        if (plugin.getVanishManager().isVanished(event.getPlayer().getUniqueId())) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player.getUniqueId())) {
            event.setQuitMessage(null);
        }
        plugin.getVanishManager().onPlayerQuit(player);
        activeSilentBlocks.remove(player.getUniqueId());
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
     * Supports full 54-slot Double Chests and safely syncs edits directly to the live block state.
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
        if (!(state instanceof InventoryHolder)) return;

        // getInventory() returns full 54-slot DoubleChestInventory for double chests and 27-slot for single chests
        Inventory targetInv = ((InventoryHolder) state).getInventory();
        if (targetInv == null) return;

        int size = targetInv.getSize();
        String friendlyName = (isChest && size == 54) ? "Double Chest" : formatContainerName(matName);
        String title = SILENT_CONTAINER_PREFIX + friendlyName;
        if (title.length() > 32) title = title.substring(0, 32);

        // Open an unlinked custom mirror view so the chest block lid NEVER opens on client screens
        Inventory silentView;
        if (size % 9 == 0) {
            silentView = Bukkit.createInventory(player, size, title);
        } else {
            silentView = Bukkit.createInventory(player, targetInv.getType(), title);
        }

        ItemStack[] originalContents = targetInv.getContents();
        ItemStack[] clonedContents = new ItemStack[originalContents.length];
        for (int i = 0; i < originalContents.length; i++) {
            if (originalContents[i] != null) {
                clonedContents[i] = originalContents[i].clone();
            }
        }
        silentView.setContents(clonedContents);

        activeSilentBlocks.put(player.getUniqueId(), block);
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

        Block block = activeSilentBlocks.get(player.getUniqueId());
        if (block == null) return;

        Inventory top = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> syncMirrorToRealContainer(block, top));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSilentInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(SILENT_CONTAINER_PREFIX)) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Block block = activeSilentBlocks.get(player.getUniqueId());
        if (block == null) return;

        Inventory top = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> syncMirrorToRealContainer(block, top));
    }

    @EventHandler
    public void onSilentInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(SILENT_CONTAINER_PREFIX)) return;

        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Only remove from tracking map; do NOT overwrite contents on close so merely inspecting a chest never wipes items
        activeSilentBlocks.remove(player.getUniqueId());
    }

    private void syncMirrorToRealContainer(Block block, Inventory mirrorInv) {
        if (block == null || mirrorInv == null) return;
        try {
            BlockState state = block.getState();
            if (!(state instanceof InventoryHolder)) return;

            Inventory realInv = ((InventoryHolder) state).getInventory();
            if (realInv == null) return;

            ItemStack[] mirrorContents = mirrorInv.getContents();
            int limit = Math.min(realInv.getSize(), mirrorContents.length);
            for (int i = 0; i < limit; i++) {
                ItemStack item = mirrorContents[i];
                realInv.setItem(i, item != null ? item.clone() : null);
            }

            // For Shulker Boxes, ensure BlockState snapshot is flushed if not backed by a live Inventory
            String typeName = block.getType().name();
            if (typeName.contains("SHULKER")) {
                state.update(true, false);
            }
        } catch (Throwable ignored) {}
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