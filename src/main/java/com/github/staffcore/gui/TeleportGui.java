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

    public static final String HOME_GUI_PREFIX = ChatColor.DARK_GRAY + "Homes: " + ChatColor.BLUE;
    public static final String HOME_ICON_PICKER_PREFIX = ChatColor.DARK_GRAY + "Pick Icon: " + ChatColor.GOLD;
    public static final String HOME_ACTIONS_PREFIX = ChatColor.DARK_GRAY + "Manage: " + ChatColor.GOLD;
    public static final String WARP_GUI_PREFIX = ChatColor.DARK_GRAY + "Warps - Page ";
    public static final String WARP_ICON_PICKER_PREFIX = ChatColor.DARK_GRAY + "Warp Icon: " + ChatColor.GOLD;

    // Track active renaming sessions: player UUID -> old home name
    public static final Map<UUID, String> renamingHomeMap = new HashMap<>();

    // Track players who prefer Bedrock / Touch Sub-Menu Mode (UUID set)
    public static final Set<UUID> bedrockModePlayers = new HashSet<>();

    public static boolean isBedrockMode(UUID uuid) {
        return bedrockModePlayers.contains(uuid);
    }

    public static void toggleBedrockMode(UUID uuid) {
        if (bedrockModePlayers.contains(uuid)) {
            bedrockModePlayers.remove(uuid);
        } else {
            bedrockModePlayers.add(uuid);
        }
    }

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

        public ItemStack toItemStack() {
            ItemStack item;
            try {
                Material mat = Material.valueOf(modernMat);
                item = new ItemStack(mat);
            } catch (Throwable t) {
                try {
                    Material mat = Material.valueOf(legacyMat);
                    item = new ItemStack(mat, 1, legacyData);
                } catch (Throwable t2) {
                    item = new ItemStack(Material.PAPER);
                }
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    public static final List<IconChoice> ICON_CHOICES = Arrays.asList(
            new IconChoice("home", ChatColor.GREEN + "Cozy Cottage / Home", "RED_BED", "BED", (short) 0),
            new IconChoice("base", ChatColor.GRAY + "Fortress / Main Base", "STONE_BRICKS", "SMOOTH_BRICK", (short) 0),
            new IconChoice("farm", ChatColor.YELLOW + "Crop Farm / Barn", "WHEAT", "WHEAT", (short) 0),
            new IconChoice("spawner", ChatColor.DARK_PURPLE + "Mob Spawner / XP Grinder", "SPAWNER", "MOB_SPAWNER", (short) 0),
            new IconChoice("mine", ChatColor.GOLD + "Mining Quarry / Cave", "IRON_PICKAXE", "IRON_PICKAXE", (short) 0),
            new IconChoice("shop", ChatColor.AQUA + "Market / Player Shop", "OAK_SIGN", "SIGN", (short) 0),
            new IconChoice("nether", ChatColor.DARK_RED + "Nether Hub / Portal", "NETHERRACK", "NETHERRACK", (short) 0),
            new IconChoice("end", ChatColor.LIGHT_PURPLE + "End Base / Stronghold", "ENDER_EYE", "EYE_OF_ENDER", (short) 0),
            new IconChoice("vault", ChatColor.GOLD + "Storage Vault / Bank", "CHEST", "CHEST", (short) 0),
            new IconChoice("forge", ChatColor.DARK_GRAY + "Blacksmith / Forge", "ANVIL", "ANVIL", (short) 0),
            new IconChoice("library", ChatColor.BLUE + "Enchanting Library", "ENCHANTING_TABLE", "ENCHANTMENT_TABLE", (short) 0),
            new IconChoice("arena", ChatColor.RED + "PvP Arena / Colosseum", "DIAMOND_SWORD", "DIAMOND_SWORD", (short) 0),
            new IconChoice("dock", ChatColor.DARK_AQUA + "Fishing Pier / Port", "FISHING_ROD", "FISHING_ROD", (short) 0),
            new IconChoice("wood", ChatColor.DARK_GREEN + "Lumber Mill / Forest", "OAK_LOG", "LOG", (short) 0)
    );

    public static IconChoice getIconChoice(String iconId) {
        if (iconId == null) return ICON_CHOICES.get(0);
        for (IconChoice choice : ICON_CHOICES) {
            if (choice.id.equalsIgnoreCase(iconId)) {
                return choice;
            }
        }
        return ICON_CHOICES.get(0);
    }

    /**
     * Opens the Main Homes GUI (54 slots).
     */
    public static void openHomeGui(StaffCore plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = UtilityCommands.playerHomes.get(uuid);
        if (homes == null) homes = Collections.emptyMap();

        Inventory gui = Bukkit.createInventory(null, 54, HOME_GUI_PREFIX + player.getName());
        ItemStack filler = createGrayPane();

        // Fill bottom control row (slots 45-53)
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // Slot 45: Information Book
        int maxHomes = plugin.getConfig().getInt("homes.max-homes", 5);
        String limitStr = (player.hasPermission("staffcore.command.sethome.unlimited") || player.isOp()) ? "Unlimited" : String.valueOf(maxHomes);
        gui.setItem(45, createItem(Material.BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Home Overview",
                Arrays.asList(
                        ChatColor.GRAY + "Your Homes: " + ChatColor.GREEN + homes.size() + ChatColor.GRAY + "/" + ChatColor.YELLOW + limitStr,
                        ChatColor.GRAY + "Set a new home: " + ChatColor.WHITE + "/sethome <name>",
                        ChatColor.GRAY + "Delete a home: " + ChatColor.WHITE + "/delhome <name>"
                )));

        // Slot 49: Close Button
        gui.setItem(49, createItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close",
                Collections.singletonList(ChatColor.GRAY + "Click to close this menu")));

        // Slot 51: Mode Switcher (Bedrock / Mobile Friendly Toggle)
        boolean bedrockMode = isBedrockMode(uuid);
        Material toggleMat = bedrockMode ? matchCrossVersionMaterial("REDSTONE_TORCH", "REDSTONE_TORCH_ON") : Material.LEVER;
        String modeTitle = bedrockMode
                ? ChatColor.GOLD + "" + ChatColor.BOLD + "Mode: " + ChatColor.YELLOW + "Sub-Menu (Bedrock)"
                : ChatColor.GREEN + "" + ChatColor.BOLD + "Mode: " + ChatColor.AQUA + "Direct (Java)";

        List<String> toggleLore = new ArrayList<>();
        if (bedrockMode) {
            toggleLore.add(ChatColor.GRAY + "Current setting: " + ChatColor.YELLOW + "Sub-Menu Mode");
            toggleLore.add(ChatColor.WHITE + "• Single tap on any home opens a");
            toggleLore.add(ChatColor.WHITE + "  simple touch menu with big buttons");
            toggleLore.add(ChatColor.WHITE + "  for Teleport, Icon, Rename & Delete!");
            toggleLore.add("");
            toggleLore.add(ChatColor.YELLOW + "Tap to switch to " + ChatColor.AQUA + "Direct Java Mode" + ChatColor.YELLOW + ".");
        } else {
            toggleLore.add(ChatColor.GRAY + "Current setting: " + ChatColor.AQUA + "Direct Mode");
            toggleLore.add(ChatColor.WHITE + "• Left-Click: Instant Teleport");
            toggleLore.add(ChatColor.WHITE + "• Right-Click: Change Icon");
            toggleLore.add(ChatColor.WHITE + "• Shift+Left: Rename");
            toggleLore.add(ChatColor.WHITE + "• Shift+Right: Delete");
            toggleLore.add("");
            toggleLore.add(ChatColor.GOLD + "Bedrock / Mobile Players:");
            toggleLore.add(ChatColor.YELLOW + "Click here to enable " + ChatColor.GOLD + "Sub-Menu Mode" + ChatColor.YELLOW + "!");
        }
        gui.setItem(51, createItem(toggleMat, modeTitle, toggleLore));

        // Populate home items (slots 0 to 44)
        int slot = 0;
        for (Map.Entry<String, Location> entry : homes.entrySet()) {
            if (slot >= 45) break;

            String homeName = entry.getKey();
            Location loc = entry.getValue();
            String iconId = UtilityCommands.getHomeIcon(uuid, homeName);
            IconChoice choice = getIconChoice(iconId);

            ItemStack homeItem = choice.toItemStack();
            ItemMeta meta = homeItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + homeName);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Theme: " + choice.displayName);
                lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + (loc.getWorld() != null ? loc.getWorld().getName() : "Unknown"));
                lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                lore.add("");

                if (bedrockMode) {
                    lore.add(ChatColor.YELLOW + "» Tap to open Action Menu «");
                    lore.add(ChatColor.DARK_GRAY + "(Teleport, Change Icon, Rename, Delete)");
                } else {
                    lore.add(ChatColor.GREEN + "• Left-Click: " + ChatColor.WHITE + "Teleport to home");
                    lore.add(ChatColor.YELLOW + "• Right-Click: " + ChatColor.WHITE + "Change icon");
                    lore.add(ChatColor.AQUA + "• Shift + Left-Click: " + ChatColor.WHITE + "Rename home");
                    lore.add(ChatColor.RED + "• Shift + Right-Click: " + ChatColor.WHITE + "Delete home");
                }

                meta.setLore(lore);
                homeItem.setItemMeta(meta);
            }
            gui.setItem(slot++, homeItem);
        }

        player.openInventory(gui);
    }

    /**
     * Dedicated 27-slot Sub-Menu for Bedrock / Mobile players (1-tap dedicated buttons).
     */
    public static void openHomeActionsMenu(StaffCore plugin, Player player, String homeName) {
        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = UtilityCommands.playerHomes.get(uuid);
        if (homes == null || !homes.containsKey(homeName.toLowerCase())) {
            player.sendMessage(plugin.getMessageManager().color("&cHome &e" + homeName + " &cwas not found!"));
            openHomeGui(plugin, player);
            return;
        }

        Location loc = homes.get(homeName.toLowerCase());
        Inventory inv = Bukkit.createInventory(null, 27, HOME_ACTIONS_PREFIX + homeName);
        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 4: Current Home Info & Icon
        String iconId = UtilityCommands.getHomeIcon(uuid, homeName);
        IconChoice choice = getIconChoice(iconId);
        ItemStack currentHome = choice.toItemStack();
        ItemMeta curMeta = currentHome.getItemMeta();
        if (curMeta != null) {
            curMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + homeName);
            curMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Theme: " + choice.displayName,
                    ChatColor.GRAY + "World: " + ChatColor.WHITE + (loc.getWorld() != null ? loc.getWorld().getName() : "Unknown"),
                    ChatColor.GRAY + "Coordinates: " + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
            ));
            currentHome.setItemMeta(curMeta);
        }
        inv.setItem(4, currentHome);

        // Slot 10: 🟢 Teleport (Ender Pearl)
        inv.setItem(10, createItem(Material.ENDER_PEARL, ChatColor.GREEN + "" + ChatColor.BOLD + "Teleport to Home",
                Arrays.asList(
                        ChatColor.GRAY + "Tap to teleport directly",
                        ChatColor.GRAY + "to your home location."
                )));

        // Slot 12: 🎨 Change Icon (Painting / Item Frame)
        Material iconMat = matchCrossVersionMaterial("ITEM_FRAME", "PAINTING");
        inv.setItem(12, createItem(iconMat, ChatColor.YELLOW + "" + ChatColor.BOLD + "Change Icon",
                Arrays.asList(
                        ChatColor.GRAY + "Tap to pick from 14",
                        ChatColor.GRAY + "creative themed icons."
                )));

        // Slot 14: 🏷️ Rename (Name Tag)
        inv.setItem(14, createItem(Material.NAME_TAG, ChatColor.AQUA + "" + ChatColor.BOLD + "Rename Home",
                Arrays.asList(
                        ChatColor.GRAY + "Tap to type a new",
                        ChatColor.GRAY + "name for this home in chat."
                )));

        // Slot 16: 🔴 Delete (Redstone Block)
        inv.setItem(16, createItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Delete Home",
                Arrays.asList(
                        ChatColor.GRAY + "Tap to permanently remove",
                        ChatColor.GRAY + "this home from your list."
                )));

        // Slot 22: ⬅️ Back Arrow
        inv.setItem(22, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Back to Homes",
                Collections.singletonList(ChatColor.GRAY + "Click to return to your home list")));

        player.openInventory(inv);
    }

    /**
     * Opens the 14-Icon Picker Menu (27 slots).
     */
    public static void openHomeIconPicker(StaffCore plugin, Player player, String homeName) {
        Inventory inv = Bukkit.createInventory(null, 27, HOME_ICON_PICKER_PREFIX + homeName);
        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        for (int i = 0; i < ICON_CHOICES.size(); i++) {
            IconChoice choice = ICON_CHOICES.get(i);
            ItemStack item = choice.toItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(choice.displayName);
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Click to choose this icon for " + ChatColor.GOLD + homeName,
                        ChatColor.DARK_GRAY + "Theme: " + choice.displayName
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        // Slot 22: Back button
        inv.setItem(22, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Back",
                Collections.singletonList(ChatColor.GRAY + "Click to return to homes")));

        player.openInventory(inv);
    }

    /**
     * Opens the Server Warps Explorer GUI with Pagination (54 slots).
     */
    public static void openWarpGui(StaffCore plugin, Player player, int page) {
        Map<String, Location> warps = UtilityCommands.warps;
        List<String> warpNames = new ArrayList<>(warps.keySet());
        Collections.sort(warpNames);

        int warpsPerPage = 36;
        int totalPages = Math.max(1, (int) Math.ceil((double) warpNames.size() / warpsPerPage));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Inventory gui = Bukkit.createInventory(null, 54, WARP_GUI_PREFIX + page + "/" + totalPages);
        ItemStack filler = createGrayPane();

        for (int i = 36; i < 54; i++) {
            gui.setItem(i, filler);
        }

        int startIndex = (page - 1) * warpsPerPage;
        int endIndex = Math.min(startIndex + warpsPerPage, warpNames.size());

        for (int i = startIndex; i < endIndex; i++) {
            String warpName = warpNames.get(i);
            Location loc = warps.get(warpName);
            String iconId = UtilityCommands.getWarpIcon(warpName);
            IconChoice choice = getIconChoice(iconId);

            ItemStack item = choice.toItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + warpName);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + (loc.getWorld() != null ? loc.getWorld().getName() : "Unknown"));
                lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                lore.add("");
                lore.add(ChatColor.GREEN + "• Click to teleport to warp");

                if (player.hasPermission("staffcore.command.setwarp") || player.isOp()) {
                    lore.add(ChatColor.YELLOW + "• Staff Right-Click: " + ChatColor.WHITE + "Change warp icon");
                    lore.add(ChatColor.RED + "• Staff Shift+Right: " + ChatColor.WHITE + "Delete warp");
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(i - startIndex, item);
        }

        // Slot 45: Previous Page Arrow
        if (page > 1) {
            gui.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "« Previous Page",
                    Collections.singletonList(ChatColor.GRAY + "Go to page " + (page - 1))));
        } else {
            gui.setItem(45, filler);
        }

        // Slot 49: Close Button
        gui.setItem(49, createItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close",
                Collections.singletonList(ChatColor.GRAY + "Click to close warp browser")));

        // Slot 51: Info Book
        gui.setItem(51, createItem(Material.BOOK, ChatColor.AQUA + "" + ChatColor.BOLD + "Warp Directory",
                Arrays.asList(
                        ChatColor.GRAY + "Total Server Warps: " + ChatColor.YELLOW + warps.size(),
                        ChatColor.GRAY + "Page: " + ChatColor.WHITE + page + ChatColor.GRAY + " of " + ChatColor.WHITE + totalPages
                )));

        // Slot 53: Next Page Arrow
        if (page < totalPages) {
            gui.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Next Page »",
                    Collections.singletonList(ChatColor.GRAY + "Go to page " + (page + 1))));
        } else {
            gui.setItem(53, filler);
        }

        player.openInventory(gui);
    }

    /**
     * Opens the Warp Icon Picker GUI (27 slots).
     */
    public static void openWarpIconPicker(StaffCore plugin, Player player, String warpName) {
        Inventory inv = Bukkit.createInventory(null, 27, WARP_ICON_PICKER_PREFIX + warpName);
        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        for (int i = 0; i < ICON_CHOICES.size(); i++) {
            IconChoice choice = ICON_CHOICES.get(i);
            ItemStack item = choice.toItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(choice.displayName);
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Click to choose this icon for " + ChatColor.GOLD + warpName,
                        ChatColor.DARK_GRAY + "Theme: " + choice.displayName
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        // Slot 22: Back button
        inv.setItem(22, createItem(Material.ARROW, ChatColor.YELLOW + "" + ChatColor.BOLD + "Back",
                Collections.singletonList(ChatColor.GRAY + "Click to return to warps")));

        player.openInventory(inv);
    }

    public static ItemStack createGrayPane() {
        ItemStack pane;
        try {
            Material mat = Material.valueOf("GRAY_STAINED_GLASS_PANE");
            pane = new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            pane = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
        }
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public static Material matchCrossVersionMaterial(String modernName, String legacyName) {
        try {
            return Material.valueOf(modernName);
        } catch (IllegalArgumentException e) {
            return Material.valueOf(legacyName);
        }
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}