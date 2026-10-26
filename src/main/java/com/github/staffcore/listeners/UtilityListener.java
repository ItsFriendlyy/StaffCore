package com.github.staffcore.listeners;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import com.github.staffcore.punishment.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class UtilityListener implements Listener {

    private final StaffCore plugin;

    public UtilityListener() {
        this(JavaPlugin.getPlugin(StaffCore.class));
    }

    public UtilityListener(StaffCore plugin) {
        this.plugin = (plugin != null) ? plugin : JavaPlugin.getPlugin(StaffCore.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cache IP and username on join as well for /check alt tracking
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            plugin.getPunishmentManager().cachePlayerIp(uuid, player.getName(), player.getAddress().getAddress().getHostAddress());
        }

        // If player is actively jailed, teleport them to their assigned jail
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(uuid);
        if (activeJail != null) {
            Location jailLoc = plugin.getPunishmentManager().getAssignedJailLocation(uuid);
            if (jailLoc != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(jailLoc);
                    }
                }, 2L);
            }
        }

        applySavedStates(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applySavedStates(player);
            }
        }, 2L);
    }

    private void applySavedStates(Player player) {
        UUID uuid = player.getUniqueId();

        if (UtilityCommands.isFlyEnabled(uuid)) {
            player.setAllowFlight(true);
            if (!player.isOnGround()) {
                player.setFlying(true);
            }
        }

        if (UtilityCommands.walkSpeeds.containsKey(uuid)) {
            float walkSpeed = UtilityCommands.walkSpeeds.get(uuid);
            player.setWalkSpeed(Math.min(1.0f, Math.max(0.0f, walkSpeed)));
        }

        if (UtilityCommands.flySpeeds.containsKey(uuid)) {
            float flySpeed = UtilityCommands.flySpeeds.get(uuid);
            player.setFlySpeed(Math.min(1.0f, Math.max(0.0f, flySpeed)));
        }
    }

    /**
     * Respawn jailed players back inside jail when they die.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
        if (activeJail != null) {
            Location jailLoc = plugin.getPunishmentManager().getAssignedJailLocation(player.getUniqueId());
            if (jailLoc != null) {
                event.setRespawnLocation(jailLoc);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(jailLoc);
                    }
                }, 1L);
            }
        }
    }

    /**
     * Block commands while jailed.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onJailedCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Punishment activeJail = plugin.getPunishmentManager().getActiveJail(player.getUniqueId());
        if (activeJail != null && !player.hasPermission("staffcore.bypass.jail") && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.command-blocked-jailed", "&cYou cannot execute commands while jailed!")));
        }
    }

    /**
     * Block throwing Ender Pearls, Splash Potions, Tridents, Wind Charges, etc. while jailed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            if (plugin.getPunishmentManager().getActiveJail(player.getUniqueId()) != null) {
                event.setCancelled(true);
                player.updateInventory();
                player.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.action-blocked-jailed", "&cYou cannot interact, throw items, or break blocks while jailed!")));
            }
        }
    }

    /**
     * Block right-clicking Ender Pearls, Chorus Fruit, Buckets, Boats, Chests, etc. while jailed.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJailedInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPunishmentManager().getActiveJail(player.getUniqueId()) != null) {
            event.setCancelled(true);
            if (event.getItem() != null) {
                String matName = event.getItem().getType().name();
                if (matName.contains("PEARL") || matName.contains("CHORUS") || matName.contains("POTION") ||
                    matName.contains("WIND_CHARGE") || matName.contains("FIREWORK") || matName.contains("BOW")) {
                    player.updateInventory();
                    player.sendMessage(plugin.getMessageManager().color(
                            plugin.getConfig().getString("messages.action-blocked-jailed", "&cYou cannot interact, throw items, or break blocks while jailed!")));
                }
            }
        }
    }

    /**
     * Block eating Chorus Fruit or drinking potions while jailed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPunishmentManager().getActiveJail(player.getUniqueId()) != null) {
            String matName = event.getItem().getType().name();
            if (matName.contains("CHORUS") || matName.contains("POTION") || matName.contains("MILK")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.action-blocked-jailed", "&cYou cannot interact, throw items, or break blocks while jailed!")));
            }
        }
    }

    /**
     * Block Ender Pearl, Chorus Fruit, and Portal teleports while jailed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPunishmentManager().getActiveJail(player.getUniqueId()) != null) {
            String cause = event.getCause().name();
            if (cause.equals("ENDER_PEARL") || cause.equals("CHORUS_FRUIT") ||
                cause.equals("NETHER_PORTAL") || cause.equals("END_PORTAL") || cause.equals("END_GATEWAY")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.action-blocked-jailed", "&cYou cannot interact, throw items, or break blocks while jailed!")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getPunishmentManager().getActiveJail(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.action-blocked-jailed", "&cYou cannot interact, throw items, or break blocks while jailed!")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getPunishmentManager().getActiveJail(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.action-blocked-jailed", "&cYou cannot interact, throw items, or break blocks while jailed!")));
        }
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

        // Cancel click on any gray glass pane slots
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