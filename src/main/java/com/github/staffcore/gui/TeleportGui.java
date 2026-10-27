package com.github.staffcore.gui;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TeleportGui {

    public static final String HOME_GUI_PREFIX = ChatColor.DARK_GRAY + "Homes: " + ChatColor.DARK_AQUA;
    public static final String HOME_ICON_PICKER_PREFIX = ChatColor.DARK_GRAY + "Pick Icon: " + ChatColor.DARK_AQUA;
    public static final String WARP_GUI_PREFIX = ChatColor.DARK_GRAY + "Warps - Page ";
    public static final String WARP_ICON_PICKER_PREFIX = ChatColor.DARK_GRAY + "Warp Icon: " + ChatColor.GOLD;

    // Chat prompt listeners: Player UUID -> Target editing home/warp name
    public static final Map<UUID, String> renamingHomeMap = new HashMap<>();
    public static final Map<UUID, String> renamingWarpMap = new HashMap<>();

    // Available customizable icons for homes & warps
    public static class IconChoice {
        public final String id;
        public final String displayName;
        public final String modernMat;
        public final String legacyMat;
        public final short legacyData;

        public IconChoice(String id, String displayName, String modernMat, String legacyMat, short legacyData) {
            this.id = id;
            this.displayName = displayName;
            this.modernMat = modernMat;
            this.legacyMat = legacyMat;
            this.legacyData = legacyData;
        }

        public ItemStack createStack() {
            ItemStack stack;
            try {
                stack = new ItemStack(Material.valueOf(modernMat));
            } catch (Throwable t) {
                stack = new ItemStack(Material.valueOf(legacyMat), 1, legacyData);
            }
            return stack;
        }
    }

    public static final List<IconChoice> ICON_CHOICES = Arrays.asList(
            new IconChoice("home", "&b&lMain Home", "RED_BED", "BED", (short) 0),
            new IconChoice("base", "&6&lFortress / Base", "BRICKS", "BRICK", (short) 0),
            new IconChoice("farm", "&a&lCrop / Animal Farm", "WHEAT", "WHEAT", (short) 0),
            new IconChoice("mob", "&c&lMob Grinder / Spawner", "ROTTEN_FLESH", "ROTTEN_FLESH", (short) 0),
            new IconChoice("mine", "&7&lMining Quarry / Cave", "IRON_PICKAXE", "IRON_PICKAXE", (short) 0),
            new IconChoice("shop", "&e&lMarket / Shop", "EMERALD", "EMERALD", (short) 0),
            new IconChoice("nether", "&4&lNether Portal Base", "NETHERRACK", "NETHERRACK", (short) 0),
            new IconChoice("end", "&d&lThe End Base", "ENDER_EYE", "EYE_OF_ENDER", (short) 0),
            new IconChoice("chest", "&6&lStorage / Vault", "CHEST", "CHEST", (short) 0),
            new IconChoice("anvil", "&8&lForge / Blacksmith", "ANVIL", "ANVIL", (short) 0),
            new IconChoice("enchant", "&5&lEnchanting Library", "ENCHANTING_TABLE", "ENCHANTMENT_TABLE", (short) 0),
            new IconChoice("pvp", "&c&lPvP / Combat Arena", "DIAMOND_SWORD", "DIAMOND_SWORD", (short) 0),
            new IconChoice("dock", "&9&lHarbor / Fishing Dock", "FISHING_ROD", "FISHING_ROD", (short) 0),
            new IconChoice("tree", "&2&lWood / Tree Farm", "OAK_LOG", "LOG", (short) 0)
    );

    // =========================================================================
    //                            HOME GUI
    // =========================================================================

    public static void openHomeGui(StaffCore plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = UtilityCommands.playerHomes.get(uuid);
        int totalHomes = (homes != null) ? homes.size() : 0;

        Inventory inv = Bukkit.createInventory(null, 54, HOME_GUI_PREFIX + player.getName());
        ItemStack filler = createPane("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 7, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        if (homes != null && !homes.isEmpty()) {
            int slot = 0;
            for (Map.Entry<String, Location> entry : homes.entrySet()) {
                if (slot >= 45) break;
                String homeName = entry.getKey();
                Location loc = entry.getValue();

                ItemStack icon = resolveHomeIcon(plugin, uuid, homeName);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&l" + homeName.toUpperCase()));
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + (loc.getWorld() != null ? loc.getWorld().getName() : "Unknown"));
                    lore.add(ChatColor.GRAY + "X: " + ChatColor.YELLOW + loc.getBlockX() +
                            ChatColor.GRAY + " Y: " + ChatColor.YELLOW + loc.getBlockY() +
                            ChatColor.GRAY + " Z: " + ChatColor.YELLOW + loc.getBlockZ());
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Left-Click » " + ChatColor.GREEN + "Teleport to home");
                    lore.add(ChatColor.GOLD + "Right-Click » " + ChatColor.AQUA + "Customize icon");
                    lore.add(ChatColor.AQUA + "Shift + Left-Click » " + ChatColor.WHITE + "Rename home");
                    lore.add(ChatColor.RED + "Shift + Right-Click » " + ChatColor.RED + "Delete home");
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
                inv.setItem(slot++, icon);
            }
        } else {
            inv.setItem(22, createItem(Material.BARRIER, "&c&lNo Homes Set",
                    Arrays.asList("&7You haven't set any homes yet!", "&7Set one using &e/sethome <name>")));
        }

        // Slot 45: Stats info book
        int maxHomes = plugin.getConfig().getInt("homes.max-homes", 5);
        String limitStr = (player.hasPermission("staffcore.command.sethome.unlimited") || player.isOp()) ? "Unlimited" : String.valueOf(maxHomes);
        inv.setItem(45, createItem(matchMaterial("BOOK", "BOOK"), "&e&lYour Homes Overview",
                Arrays.asList(
                        "&7Homes Used: &a" + totalHomes + " &7/ &e" + limitStr,
                        "&7Command: &f/sethome <name>",
                        "",
                        "&8Click any home above to teleport!"
                )));

        // Slot 49: Close barrier
        inv.setItem(49, createItem(Material.BARRIER, "&c&lClose", Collections.singletonList("&7Click to close this menu")));

        player.openInventory(inv);
    }

    public static void openHomeIconPicker(StaffCore plugin, Player player, String homeName) {
        Inventory inv = Bukkit.createInventory(null, 27, HOME_ICON_PICKER_PREFIX + homeName);
        ItemStack filler = createPane("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 7, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        int slot = 0;
        for (IconChoice choice : ICON_CHOICES) {
            if (slot >= 18) break;
            ItemStack stack = choice.createStack();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', choice.displayName));
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Theme: " + ChatColor.WHITE + choice.id,
                        "",
                        ChatColor.YELLOW + "Click to select this icon for &e" + homeName + "!"
                ));
                stack.setItemMeta(meta);
            }
            inv.setItem(slot++, stack);
        }

        // Slot 22: Back button
        inv.setItem(22, createItem(Material.ARROW, "&e&lBack to Homes", Collections.singletonList("&7Click to return to your homes list")));
        player.openInventory(inv);
    }

    private static ItemStack resolveHomeIcon(StaffCore plugin, UUID uuid, String homeName) {
        String iconKey = UtilityCommands.getHomeIcon(uuid, homeName);
        if (iconKey != null) {
            for (IconChoice choice : ICON_CHOICES) {
                if (choice.id.equalsIgnoreCase(iconKey)) {
                    return choice.createStack();
                }
            }
        }
        return ICON_CHOICES.get(0).createStack();
    }

    // =========================================================================
    //                            WARP GUI
    // =========================================================================

    public static void openWarpGui(StaffCore plugin, Player player, int page) {
        List<String> warpNames = new ArrayList<>(UtilityCommands.warps.keySet());
        Collections.sort(warpNames);

        int totalWarps = warpNames.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalWarps / 36.0));
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        Inventory inv = Bukkit.createInventory(null, 54, WARP_GUI_PREFIX + page + "/" + maxPages);
        ItemStack filler = createPane("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 7, " ");
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int startIndex = (page - 1) * 36;
        int endIndex = Math.min(startIndex + 36, totalWarps);

        if (totalWarps > 0) {
            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                String warpName = warpNames.get(i);
                Location loc = UtilityCommands.warps.get(warpName);

                ItemStack icon = resolveWarpIcon(plugin, warpName);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&l" + warpName.toUpperCase()));
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + (loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "Unknown"));
                    if (loc != null) {
                        lore.add(ChatColor.GRAY + "X: " + ChatColor.YELLOW + loc.getBlockX() +
                                ChatColor.GRAY + " Y: " + ChatColor.YELLOW + loc.getBlockY() +
                                ChatColor.GRAY + " Z: " + ChatColor.YELLOW + loc.getBlockZ());
                    }
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Left-Click » " + ChatColor.GREEN + "Teleport to warp");
                    if (player.hasPermission("staffcore.command.setwarp") || player.isOp()) {
                        lore.add(ChatColor.GOLD + "Right-Click » " + ChatColor.AQUA + "Edit warp icon");
                        lore.add(ChatColor.RED + "Shift + Right-Click » " + ChatColor.RED + "Delete warp");
                    }
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
                inv.setItem(slot++, icon);
            }
        } else {
            inv.setItem(13, createItem(Material.BARRIER, "&c&lNo Warps Available",
                    Arrays.asList("&7No server warps have been created yet!", "&7Staff can set warps using &e/setwarp <name>")));
        }

        // Slot 45: Previous page
        if (page > 1) {
            inv.setItem(45, createItem(Material.ARROW, "&e&l« Previous Page", Collections.singletonList("&7Go to page " + (page - 1))));
        }

        // Slot 47: Total warps counter
        inv.setItem(47, createItem(matchMaterial("COMPASS", "COMPASS"), "&6&lServer Warps Explorer",
                Arrays.asList(
                        "&7Total Warps: &e" + totalWarps,
                        "&7Current Page: &a" + page + " &7/ &e" + maxPages,
                        "",
                        "&8Click any warp to travel immediately!"
                )));

        // Slot 49: Close menu
        inv.setItem(49, createItem(Material.BARRIER, "&c&lClose", Collections.singletonList("&7Click to close this menu")));

        // Slot 51: Edit / Staff Help
        if (player.hasPermission("staffcore.command.setwarp") || player.isOp()) {
            inv.setItem(51, createItem(matchMaterial("NAME_TAG", "NAME_TAG"), "&b&lWarp Management",
                    Arrays.asList(
                            "&7Staff Shortcuts:",
                            "&8• &f/setwarp <name> &7to add new",
                            "&8• &fRight-Click &7any warp to change icon",
                            "&8• &fShift+Right-Click &7to delete warp"
                    )));
        }

        // Slot 53: Next page
        if (page < maxPages) {
            inv.setItem(53, createItem(Material.ARROW, "&e&lNext Page »", Collections.singletonList("&7Go to page " + (page + 1))));
        }

        player.openInventory(inv);
    }

    public static void openWarpIconPicker(StaffCore plugin, Player player, String warpName) {
        Inventory inv = Bukkit.createInventory(null, 27, WARP_ICON_PICKER_PREFIX + warpName);
        ItemStack filler = createPane("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", (short) 7, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        int slot = 0;
        for (IconChoice choice : ICON_CHOICES) {
            if (slot >= 18) break;
            ItemStack stack = choice.createStack();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', choice.displayName));
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Theme: " + ChatColor.WHITE + choice.id,
                        "",
                        ChatColor.YELLOW + "Click to select this icon for warp &6" + warpName + "!"
                ));
                stack.setItemMeta(meta);
            }
            inv.setItem(slot++, stack);
        }

        // Slot 22: Back button
        inv.setItem(22, createItem(Material.ARROW, "&e&lBack to Warps", Collections.singletonList("&7Click to return to the warps menu")));
        player.openInventory(inv);
    }

    private static ItemStack resolveWarpIcon(StaffCore plugin, String warpName) {
        String iconKey = UtilityCommands.getWarpIcon(warpName);
        if (iconKey != null) {
            for (IconChoice choice : ICON_CHOICES) {
                if (choice.id.equalsIgnoreCase(iconKey)) {
                    return choice.createStack();
                }
            }
        }
        return matchMaterialStack("ENDER_PEARL", "ENDER_PEARL", (short) 0);
    }

    // =========================================================================
    //                        CROSS-VERSION HELPERS
    // =========================================================================

    public static ItemStack createPane(String modernMat, String legacyMat, short legacyData, String name) {
        ItemStack pane;
        try {
            pane = new ItemStack(Material.valueOf(modernMat));
        } catch (Throwable t) {
            pane = new ItemStack(Material.valueOf(legacyMat), 1, legacyData);
        }
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public static Material matchMaterial(String modernMat, String legacyMat) {
        try {
            return Material.valueOf(modernMat);
        } catch (Throwable t) {
            return Material.valueOf(legacyMat);
        }
    }

    public static ItemStack matchMaterialStack(String modernMat, String legacyMat, short legacyData) {
        try {
            return new ItemStack(Material.valueOf(modernMat));
        } catch (Throwable t) {
            return new ItemStack(Material.valueOf(legacyMat), 1, legacyData);
        }
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', l));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}