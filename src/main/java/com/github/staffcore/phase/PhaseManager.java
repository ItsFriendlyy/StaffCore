package com.github.staffcore.phase;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseManager {

    private final StaffCore plugin;
    public static final String PHASE_INV_PREFIX = ChatColor.DARK_GRAY + "My Inventory: " + ChatColor.DARK_RED;
    private final Map<UUID, GameMode> previousGameModes = new ConcurrentHashMap<>();

    public PhaseManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPhased(UUID uuid) {
        return previousGameModes.containsKey(uuid);
    }

    public boolean togglePhase(Player player) {
        UUID uuid = player.getUniqueId();
        if (isPhased(uuid)) {
            setPhased(player, false);
            return false;
        } else {
            setPhased(player, true);
            return true;
        }
    }

    public void setPhased(Player player, boolean phased) {
        UUID uuid = player.getUniqueId();
        if (phased) {
            GameMode current = player.getGameMode();
            if (current == GameMode.SPECTATOR) {
                current = GameMode.SURVIVAL;
            }
            previousGameModes.put(uuid, current);

            // Activate spectator mode for genuine vanilla noclip through blocks
            player.setGameMode(GameMode.SPECTATOR);

            // Automatically vanish so normal players never see your floating head
            if (!plugin.getVanishManager().isVanished(uuid)) {
                plugin.getVanishManager().setVanished(player, true);
            }

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.enabled", "&8[&cStaffCore&8] &aPhase mode &2enabled&a! Smooth spectator noclip active.")));
            player.sendMessage(plugin.getMessageManager().color(
                    "&7Tip: Type &e/phase inv &7to view/manage your survival items while phasing!"));
        } else {
            GameMode prev = previousGameModes.remove(uuid);
            if (prev == null || prev == GameMode.SPECTATOR) {
                prev = GameMode.SURVIVAL;
            }
            player.setGameMode(prev);

            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.disabled", "&8[&cStaffCore&8] &cPhase mode &4disabled&c. Returned to &e" + prev.name() + "&c with your items ready!")));
        }
    }

    /**
     * Opens an interactive GUI allowing you to view and manage your survival/creative items & armor while in spectator phase.
     */
    public void openSurvivalInventory(Player player) {
        Inventory gui = Bukkit.createInventory(player, 54, PHASE_INV_PREFIX + player.getName());
        ItemStack pane = createGrayPane();

        // Row 0: Armor (Slots 0 to 3: Helmet, Chestplate, Leggings, Boots)
        gui.setItem(0, player.getInventory().getHelmet());
        gui.setItem(1, player.getInventory().getChestplate());
        gui.setItem(2, player.getInventory().getLeggings());
        gui.setItem(3, player.getInventory().getBoots());

        // Slot 4: Divider
        gui.setItem(4, pane);

        // Slot 5: Off-Hand item
        try {
            Method m = player.getInventory().getClass().getMethod("getItemInOffHand");
            ItemStack offhand = (ItemStack) m.invoke(player.getInventory());
            if (offhand != null) {
                gui.setItem(5, offhand);
            }
        } catch (Throwable ignored) {}

        // Slots 6 to 17: Divider row
        for (int i = 6; i <= 17; i++) {
            gui.setItem(i, pane);
        }

        // Rows 2, 3, 4: Main Inventory (Slots 18 to 44 -> inventory slots 9 to 35)
        for (int i = 9; i <= 35; i++) {
            gui.setItem(18 + (i - 9), player.getInventory().getItem(i));
        }

        // Row 5: Hotbar (Slots 45 to 53 -> inventory slots 0 to 8)
        for (int i = 0; i <= 8; i++) {
            gui.setItem(45 + i, player.getInventory().getItem(i));
        }

        player.openInventory(gui);
    }

    public void syncGuiToInventory(Inventory top, Player player) {
        if (top == null || player == null) return;

        // Armor (Slots 0 to 3)
        player.getInventory().setHelmet(top.getItem(0));
        player.getInventory().setChestplate(top.getItem(1));
        player.getInventory().setLeggings(top.getItem(2));
        player.getInventory().setBoots(top.getItem(3));

        // Off-hand (Slot 5)
        try {
            Method m = player.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class);
            m.invoke(player.getInventory(), top.getItem(5));
        } catch (Throwable ignored) {}

        // Main inventory (Slots 18-44 -> slots 9-35)
        for (int i = 9; i <= 35; i++) {
            player.getInventory().setItem(i, top.getItem(18 + (i - 9)));
        }

        // Hotbar (Slots 45-53 -> slots 0-8)
        for (int i = 0; i <= 8; i++) {
            player.getInventory().setItem(i, top.getItem(45 + i));
        }
    }

    private ItemStack createGrayPane() {
        ItemStack pane;
        try {
            Material mat = Material.valueOf("GRAY_STAINED_GLASS_PANE");
            pane = new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            pane = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
        }
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + " ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public GameMode getPreviousGameMode(UUID uuid) {
        return previousGameModes.get(uuid);
    }

    public void remove(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        GameMode prev = previousGameModes.remove(uuid);
        if (player != null && player.isOnline() && prev != null) {
            player.setGameMode(prev);
        }
    }

    public void clearAll() {
        for (Map.Entry<UUID, GameMode> entry : previousGameModes.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.setGameMode(entry.getValue());
            }
        }
        previousGameModes.clear();
    }
}