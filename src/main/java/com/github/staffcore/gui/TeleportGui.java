package com.github.staffcore.gui;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

public class TeleportGui {

    public static final String HOME_GUI_PREFIX = ChatColor.DARK_GRAY + "Homes: " + ChatColor.GOLD;
    public static final String HOME_ACTIONS_PREFIX = ChatColor.DARK_GRAY + "Manage: " + ChatColor.GOLD;
    public static final String HOME_ICON_PICKER_PREFIX = ChatColor.DARK_GRAY + "Icon: " + ChatColor.GOLD;

    public static final String WARP_GUI_PREFIX = ChatColor.DARK_GRAY + "Public Warps: " + ChatColor.GOLD;
    public static final String WARP_ICON_PICKER_PREFIX = ChatColor.DARK_GRAY + "Warp Icon: " + ChatColor.GOLD;
    public static final String MY_WARPS_GUI_PREFIX = ChatColor.DARK_GRAY + "My Warps: " + ChatColor.GOLD;
    public static final String WARP_ACTIONS_PREFIX = ChatColor.DARK_GRAY + "Manage Warp: " + ChatColor.GOLD;

    // Track players who prefer Touch/Bedrock sub-menu mode over Direct Java mode
    public static final Set<UUID> bedrockModePlayers = new HashSet<>();
    public static final Map<UUID, Boolean> bedrockModeOverrides = new HashMap<>();

    // Temporary chat input tracking for renaming homes and warps
    public static final Map<UUID, String> renamingHomeMap = new HashMap<>();
    public static final Map<UUID, String> renamingWarpMap = new HashMap<>();

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

        public ItemStack createItem(String currentIconId) {
            ItemStack item = createCrossVersionItem(modernMat, legacyMat, legacyData);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + displayName);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Identifier: " + ChatColor.YELLOW + id);
                if (id.equalsIgnoreCase(currentIconId)) {
                    lore.add(ChatColor.GREEN + ChatColor.BOLD.toString() + "✔ CURRENTLY SELECTED");
                } else {
                    lore.add(ChatColor.YELLOW + "Click to select this icon");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    public static final List<IconChoice> ICON_CHOICES = Collections.unmodifiableList(Arrays.asList(
            new IconChoice("home", "Cozy Bed", "RED_BED", "BED", (short) 14),
            new IconChoice("wood", "Wooden Door", "OAK_DOOR", "WOOD_DOOR", (short) 0),
            new IconChoice("iron", "Iron Security Door", "IRON_DOOR", "IRON_DOOR", (short) 0),
            new IconChoice("chest", "Storage Vault", "CHEST", "CHEST", (short) 0),
            new IconChoice("crafting", "Crafting Workshop", "CRAFTING_TABLE", "WORKBENCH", (short) 0),
            new IconChoice("furnace", "Blacksmith Forge", "FURNACE", "FURNACE", (short) 0),
            new IconChoice("anvil", "Heavy Anvil", "ANVIL", "ANVIL", (short) 0),
            new IconChoice("bookshelf", "Grand Library", "BOOKSHELF", "BOOKSHELF", (short) 0),
            new IconChoice("emerald", "Trading Post", "EMERALD", "EMERALD", (short) 0),
            new IconChoice("diamond", "Treasury", "DIAMOND", "DIAMOND", (short) 0),
            new IconChoice("camp", "Wilderness Camp", "CAMPFIRE", "TORCH", (short) 0),
            new IconChoice("castle", "Stone Fortress", "STONE_BRICKS", "SMOOTH_BRICK", (short) 0),
            new IconChoice("farm", "Golden Harvest", "HAY_BLOCK", "HAY_BLOCK", (short) 0),
            new IconChoice("nether", "Nether Portal", "NETHERRACK", "NETHERRACK", (short) 0),
            new IconChoice("end", "End Gateway", "ENDER_EYE", "EYE_OF_ENDER", (short) 0),
            new IconChoice("mine", "Mining Shaft", "IRON_PICKAXE", "IRON_PICKAXE", (short) 0),
            new IconChoice("arena", "Combat Arena", "DIAMOND_SWORD", "DIAMOND_SWORD", (short) 0),
            new IconChoice("star", "Sacred Shrine", "NETHER_STAR", "NETHER_STAR", (short) 0)
    ));

    /**
     * Automatically detects whether a player is connecting from Bedrock Edition via Floodgate
     * or by checking their username prefix (e.g. '.').
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;

        // 1. Direct Floodgate API reflection check (zero compile-time dependency, works on all versions)
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstanceMethod = floodgateApiClass.getMethod("getInstance");
                Object apiInstance = getInstanceMethod.invoke(null);
                if (apiInstance != null) {
                    Method isFloodgatePlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
                    Object result = isFloodgatePlayerMethod.invoke(apiInstance, player.getUniqueId());
                    if (result instanceof Boolean && (Boolean) result) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Floodgate API reflection fallback
        }

        // 2. Geyser/Floodgate username prefix fallback (default prefix is '.' or '*')
        String name = player.getName();
        if (name != null && (name.startsWith(".") || name.startsWith("*"))) {
            return true;
        }

        return false;
    }

    public static boolean isBedrockMode(UUID uuid) {
        if (bedrockModeOverrides.containsKey(uuid)) {
            return bedrockModeOverrides.get(uuid);
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return isBedrockMode(player);
        }
        return bedrockModePlayers.contains(uuid);
    }

    public static boolean isBedrockMode(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (bedrockModeOverrides.containsKey(uuid)) {
            return bedrockModeOverrides.get(uuid);
        }
        if (bedrockModePlayers.contains(uuid)) {
            return true;
        }
        return isBedrockPlayer(player);
    }

    public static void toggleBedrockMode(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        boolean current = (player != null) ? isBedrockMode(player) : isBedrockMode(uuid);
        boolean next = !current;
        bedrockModeOverrides.put(uuid, next);
        if (next) {
            bedrockModePlayers.add(uuid);
        } else {
            bedrockModePlayers.remove(uuid);
        }
    }

    // =========================================================================
    //                        HOMES EXPLORER GUI (/home)
    // =========================================================================

    public static void openHomeGui(StaffCore plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = UtilityCommands.playerHomes.get(uuid);
        int homeCount = (homes != null) ? homes.size() : 0;
        int maxHomes = UtilityCommands.getMaxHomes(player, plugin);

        Inventory inv = Bukkit.createInventory(null, 54, HOME_GUI_PREFIX + player.getName());

        // Fill bottom control bar
        ItemStack filler = createGrayPane();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Slot 49: Close
        inv.setItem(49, createButton(Material.BARRIER, "BARRIER", (short) 0,
                ChatColor.RED + ChatColor.BOLD.toString() + "Close Menu",
                Collections.singletonList(ChatColor.GRAY + "Click to close this window.")));

        // Slot 51: Mode Switcher toggle
        boolean bedrock = isBedrockMode(player);
        inv.setItem(51, createButton(
                bedrock ? Material.COMPASS : matchCrossVersionMaterial("REDSTONE_TORCH", "REDSTONE_TORCH_ON"),
                bedrock ? "COMPASS" : "REDSTONE_TORCH_ON",
                (short) 0,
                bedrock ? ChatColor.GOLD + ChatColor.BOLD.toString() + "Mode: Touch / Sub-Menu"
                        : ChatColor.AQUA + ChatColor.BOLD.toString() + "Mode: Direct Java Shortcuts",
                Arrays.asList(
                        ChatColor.GRAY + "Current interaction style:",
                        bedrock ? ChatColor.YELLOW + "  • Single click opens actions menu"
                                : ChatColor.YELLOW + "  • Left-click teleports instantly",
                        bedrock ? ChatColor.YELLOW + "  • Perfect for Bedrock & Mobile"
                                : ChatColor.YELLOW + "  • Shift/Right-click shortcuts active",
                        "",
                        ChatColor.GREEN + "Click to toggle mode!"
                )));

        // Slot 53: Home limits counter
        String limitStr = (maxHomes == Integer.MAX_VALUE) ? "Unlimited" : String.valueOf(maxHomes);
        inv.setItem(53, createButton(Material.BOOK, "BOOK", (short) 0,
                ChatColor.YELLOW + ChatColor.BOLD.toString() + "Home Statistics",
                Arrays.asList(
                        ChatColor.GRAY + "Homes Used: " + ChatColor.GOLD + homeCount + ChatColor.GRAY + " / " + ChatColor.YELLOW + limitStr,
                        ChatColor.GRAY + "Create home: " + ChatColor.WHITE + "/sethome <name>"
                )));

        if (homes != null && !homes.isEmpty()) {
            int slot = 0;
            for (Map.Entry<String, Location> entry : homes.entrySet()) {
                if (slot >= 45) break;

                String homeName = entry.getKey();
                Location loc = entry.getValue();
                String iconId = UtilityCommands.getHomeIcon(uuid, homeName);
                ItemStack iconItem = getIconItem(iconId);

                ItemMeta meta = iconItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + homeName);
                    List<String> lore = new ArrayList<>();
                    if (loc != null && loc.getWorld() != null) {
                        lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
                        lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
                    }
                    lore.add("");

                    if (bedrock) {
                        lore.add(ChatColor.AQUA + "▶ Click: " + ChatColor.WHITE + "Open Home Actions Menu");
                    } else {
                        lore.add(ChatColor.GREEN + "▶ Left-Click: " + ChatColor.WHITE + "Teleport to home");
                        lore.add(ChatColor.YELLOW + "▶ Right-Click: " + ChatColor.WHITE + "Change icon");
                        lore.add(ChatColor.GOLD + "▶ Shift + Left-Click: " + ChatColor.WHITE + "Rename home");
                        lore.add(ChatColor.RED + "▶ Shift + Right-Click: " + ChatColor.WHITE + "Delete home");
                    }
                    meta.setLore(lore);
                    iconItem.setItemMeta(meta);
                }
                inv.setItem(slot++, iconItem);
            }
        } else {
            inv.setItem(22, createButton(Material.BOOK, "BOOK", (short) 0,
                    ChatColor.YELLOW + ChatColor.BOLD.toString() + "You have no homes set!",
                    Arrays.asList(
                            ChatColor.GRAY + "You haven't set any homes yet.",
                            ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/sethome <name>" + ChatColor.GRAY + " to create one!"
                    )));
        }

        player.openInventory(inv);
    }

    public static void openHomeActionsMenu(StaffCore plugin, Player player, String homeName) {
        Inventory inv = Bukkit.createInventory(null, 27, HOME_ACTIONS_PREFIX + homeName);

        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Slot 10: Teleport Button
        inv.setItem(10, createButton(
                matchCrossVersionMaterial("ENDER_PEARL", "ENDER_PEARL"), "ENDER_PEARL", (short) 0,
                ChatColor.GREEN + ChatColor.BOLD.toString() + "Teleport",
                Arrays.asList(ChatColor.GRAY + "Teleport instantly to home " + ChatColor.GOLD + homeName + ChatColor.GRAY + ".")));

        // Slot 12: Change Icon Button
        inv.setItem(12, createButton(
                Material.PAINTING, "PAINTING", (short) 0,
                ChatColor.YELLOW + ChatColor.BOLD.toString() + "Change Icon",
                Arrays.asList(ChatColor.GRAY + "Pick a custom icon for " + ChatColor.GOLD + homeName + ChatColor.GRAY + ".")));

        // Slot 14: Rename Button
        inv.setItem(14, createButton(
                matchCrossVersionMaterial("NAME_TAG", "NAME_TAG"), "NAME_TAG", (short) 0,
                ChatColor.GOLD + ChatColor.BOLD.toString() + "Rename Home",
                Arrays.asList(ChatColor.GRAY + "Type a new name in chat for " + ChatColor.GOLD + homeName + ChatColor.GRAY + ".")));

        // Slot 16: Delete Button
        inv.setItem(16, createButton(
                matchCrossVersionMaterial("REDSTONE_BLOCK", "REDSTONE_BLOCK"), "REDSTONE_BLOCK", (short) 0,
                ChatColor.RED + ChatColor.BOLD.toString() + "Delete Home",
                Arrays.asList(ChatColor.GRAY + "Permanently remove home " + ChatColor.GOLD + homeName + ChatColor.GRAY + ".")));

        // Slot 22: Back Button
        inv.setItem(22, createButton(
                Material.ARROW, "ARROW", (short) 0,
                ChatColor.WHITE + ChatColor.BOLD.toString() + "◀ Back to Homes",
                Collections.singletonList(ChatColor.GRAY + "Return to your homes list.")));

        player.openInventory(inv);
    }

    public static void openHomeIconPicker(StaffCore plugin, Player player, String homeName) {
        Inventory inv = Bukkit.createInventory(null, 27, HOME_ICON_PICKER_PREFIX + homeName);

        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        String currentIcon = UtilityCommands.getHomeIcon(player.getUniqueId(), homeName);
        for (int i = 0; i < ICON_CHOICES.size() && i < 18; i++) {
            inv.setItem(i, ICON_CHOICES.get(i).createItem(currentIcon));
        }

        // Slot 22: Back Button
        inv.setItem(22, createButton(
                Material.ARROW, "ARROW", (short) 0,
                ChatColor.WHITE + ChatColor.BOLD.toString() + "◀ Back",
                Collections.singletonList(ChatColor.GRAY + "Return without changing.")));

        player.openInventory(inv);
    }

    // =========================================================================
    //                        PUBLIC WARPS EXPLORER GUI (/warp)
    // =========================================================================

    public static void openWarpGui(StaffCore plugin, Player player, int page) {
        UUID uuid = player.getUniqueId();
        boolean isAdmin = player.hasPermission("staffcore.warps.admin")
                || player.hasPermission("staffcore.command.warp.others")
                || player.hasPermission("staffcore.admin")
                || player.isOp();

        // Filter warps: public warps, plus caller personal warps, or all warps if admin
        List<String> visibleWarps = new ArrayList<>();
        for (String warpName : UtilityCommands.warps.keySet()) {
            boolean isPrivate = UtilityCommands.isWarpPrivate(warpName);
            boolean isOwner = UtilityCommands.isWarpOwner(warpName, uuid);
            if (isAdmin || !isPrivate || isOwner) {
                visibleWarps.add(warpName);
            }
        }
        Collections.sort(visibleWarps);

        int totalWarps = visibleWarps.size();
        int itemsPerPage = 45; // Starts at row 1 slot 1 (slot 0) and fills through slot 44
        int maxPages = Math.max(1, (int) Math.ceil((double) totalWarps / itemsPerPage));
        int currentPage = Math.max(1, Math.min(page, maxPages));

        String title = WARP_GUI_PREFIX + currentPage + " / " + maxPages;
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill bottom control bar (Slots 45 to 53)
        ItemStack filler = createGrayPane();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Slot 45: Previous Page
        if (currentPage > 1) {
            inv.setItem(45, createButton(Material.ARROW, "ARROW", (short) 0,
                    ChatColor.YELLOW + "◀ Previous Page (" + (currentPage - 1) + ")",
                    Collections.singletonList(ChatColor.GRAY + "View previous warps.")));
        }

        // Slot 47: My Warps Button (Personal warps management - Player's own skull)
        ItemStack myWarpsBtn = createSkullItem();
        SkullMeta skullMeta = (SkullMeta) myWarpsBtn.getItemMeta();
        if (skullMeta != null) {
            setSkullOwner(skullMeta, player, player.getName());
            skullMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "★ My Personal Warps");
            skullMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to open your personal warps menu.",
                    ChatColor.YELLOW + "• Only shows warps created by you",
                    ChatColor.YELLOW + "• Customize icons & rename",
                    ChatColor.YELLOW + "• Toggle Public / Private visibility",
                    ChatColor.YELLOW + "• Delete your personal warps",
                    "",
                    ChatColor.GREEN + "▶ Click to view your personal warps!"
            ));
            myWarpsBtn.setItemMeta(skullMeta);
        }
        inv.setItem(47, myWarpsBtn);

        // Slot 49: Barrier Close Button
        inv.setItem(49, createButton(Material.BARRIER, "BARRIER", (short) 0,
                ChatColor.RED + ChatColor.BOLD.toString() + "Close Menu",
                Collections.singletonList(ChatColor.GRAY + "Click to close this window.")));

        // Slot 51: Mode Switcher / Bedrock Mode Toggle
        boolean bedrock = isBedrockMode(player);
        inv.setItem(51, createButton(
                bedrock ? Material.COMPASS : matchCrossVersionMaterial("REDSTONE_TORCH", "REDSTONE_TORCH_ON"),
                bedrock ? "COMPASS" : "REDSTONE_TORCH_ON",
                (short) 0,
                bedrock ? ChatColor.GOLD + ChatColor.BOLD.toString() + "Mode: Sub-Menu (Bedrock)"
                        : ChatColor.AQUA + ChatColor.BOLD.toString() + "Mode: Direct Teleport",
                Arrays.asList(
                        ChatColor.GRAY + "Click to toggle interaction style.",
                        bedrock ? ChatColor.YELLOW + "Opens action sub-menu for owned/admin warps"
                                : ChatColor.YELLOW + "Left-click teleports directly"
                )));

        // Slot 53: Next Page
        if (currentPage < maxPages) {
            inv.setItem(53, createButton(Material.ARROW, "ARROW", (short) 0,
                    ChatColor.YELLOW + "Next Page (" + (currentPage + 1) + ") ▶",
                    Collections.singletonList(ChatColor.GRAY + "View more warps.")));
        }

        // Warps start at the first row, first slot (slot 0) exactly like My Warps!
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalWarps);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        if (totalWarps > 0) {
            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                String warpName = visibleWarps.get(i);
                Location loc = UtilityCommands.warps.get(warpName);
                String iconId = UtilityCommands.getWarpIcon(warpName);
                ItemStack iconItem = getIconItem(iconId);

                boolean isPrivate = UtilityCommands.isWarpPrivate(warpName);
                boolean isOwner = UtilityCommands.isWarpOwner(warpName, uuid);

                ItemMeta meta = iconItem.getItemMeta();
                if (meta != null) {
                    String titleBadge = isPrivate ? ChatColor.RED + " [Private]" : "";
                    meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + warpName + titleBadge);
                    List<String> lore = new ArrayList<>();
                    if (loc != null && loc.getWorld() != null) {
                        lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
                        lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
                    }

                    // Display Owner info for EVERYONE (Java fast usage)
                    UUID ownerUuid = UtilityCommands.warpOwners.get(warpName);
                    String ownerName = "Server";
                    if (ownerUuid != null) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
                        ownerName = (op != null && op.getName() != null) ? op.getName() : "Unknown";
                    }
                    if (isOwner) {
                        lore.add(ChatColor.GRAY + "Owner: " + ChatColor.GREEN + "You (" + player.getName() + ")");
                    } else {
                        lore.add(ChatColor.GRAY + "Owner: " + ChatColor.YELLOW + ownerName);
                    }

                    // Status and "Since when is this warp public"
                    if (isPrivate) {
                        lore.add(ChatColor.RED + "Status: Private " + ChatColor.DARK_GRAY + "(Visible only to owner & staff)");
                    } else {
                        lore.add(ChatColor.GREEN + "Status: Public " + ChatColor.GRAY + "(Visible to everyone)");
                        long pubTime = UtilityCommands.getWarpPublicDate(warpName);
                        if (pubTime > 0) {
                            lore.add(ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + sdf.format(new Date(pubTime)));
                        } else {
                            lore.add(ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + "Server Origin");
                        }
                    }

                    lore.add("");
                    if (bedrock && (isOwner || isAdmin)) {
                        lore.add(ChatColor.AQUA + "▶ Click: " + ChatColor.WHITE + "Open Warp Actions Menu");
                    } else {
                        lore.add(ChatColor.GREEN + "▶ Left-Click: " + ChatColor.WHITE + "Teleport to warp");
                        if (isOwner || isAdmin) {
                            lore.add(ChatColor.YELLOW + "▶ Right-Click: " + ChatColor.WHITE + "Change warp icon (Admin)");
                            lore.add(ChatColor.GOLD + "▶ Shift + Left-Click: " + ChatColor.WHITE + "Rename warp (Admin)");
                            lore.add(ChatColor.RED + "▶ Shift + Right-Click: " + ChatColor.WHITE + "Delete warp (Admin)");
                        }
                    }
                    meta.setLore(lore);
                    iconItem.setItemMeta(meta);
                }
                inv.setItem(slot++, iconItem);
            }
        } else {
            inv.setItem(22, createButton(Material.BOOK, "BOOK", (short) 0,
                    ChatColor.YELLOW + ChatColor.BOLD.toString() + "No Warps Set!",
                    Arrays.asList(
                            ChatColor.GRAY + "No public warps are currently available.",
                            ChatColor.GRAY + "Create one with " + ChatColor.YELLOW + "/setwarp <name>"
                    )));
        }

        player.openInventory(inv);
    }

    // =========================================================================
    //                       "MY WARPS" MANAGEMENT GUI
    // =========================================================================

    public static void openMyWarpsGui(StaffCore plugin, Player player) {
        UUID uuid = player.getUniqueId();
        // Strictly personal warps only - never show other players' warps on this page, even for admins
        Set<String> myWarps = UtilityCommands.getPlayerWarps(uuid);
        int warpCount = myWarps.size();
        int maxWarps = UtilityCommands.getMaxWarps(player, plugin);

        String title = MY_WARPS_GUI_PREFIX + player.getName();
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill bottom control bar
        ItemStack filler = createGrayPane();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Slot 45: Back to Public Warps
        inv.setItem(45, createButton(Material.ARROW, "ARROW", (short) 0,
                ChatColor.WHITE + ChatColor.BOLD.toString() + "◀ Public Warps",
                Collections.singletonList(ChatColor.GRAY + "Return to the public warps explorer.")));

        // Slot 49: Barrier Close Button
        inv.setItem(49, createButton(Material.BARRIER, "BARRIER", (short) 0,
                ChatColor.RED + ChatColor.BOLD.toString() + "Close Menu",
                Collections.singletonList(ChatColor.GRAY + "Click to close this window.")));

        // Slot 51: Mode Switcher
        boolean bedrock = isBedrockMode(player);
        inv.setItem(51, createButton(
                bedrock ? Material.COMPASS : matchCrossVersionMaterial("REDSTONE_TORCH", "REDSTONE_TORCH_ON"),
                bedrock ? "COMPASS" : "REDSTONE_TORCH_ON",
                (short) 0,
                bedrock ? ChatColor.GOLD + ChatColor.BOLD.toString() + "Mode: Touch / Sub-Menu"
                        : ChatColor.AQUA + ChatColor.BOLD.toString() + "Mode: Direct Java Shortcuts",
                Arrays.asList(
                        ChatColor.GRAY + "Current interaction style:",
                        bedrock ? ChatColor.YELLOW + "  • Single click opens actions menu"
                                : ChatColor.YELLOW + "  • Left-click teleports instantly",
                        bedrock ? ChatColor.YELLOW + "  • Perfect for Bedrock & Mobile"
                                : ChatColor.YELLOW + "  • Shift/Right-click shortcuts active",
                        "",
                        ChatColor.GREEN + "Click to toggle mode!"
                )));

        // Slot 53: Personal Warp limits counter
        String limitStr = (maxWarps == Integer.MAX_VALUE) ? "Unlimited" : String.valueOf(maxWarps);
        inv.setItem(53, createButton(Material.BOOK, "BOOK", (short) 0,
                ChatColor.YELLOW + ChatColor.BOLD.toString() + "Personal Warp Stats",
                Arrays.asList(
                        ChatColor.GRAY + "Your Warps: " + ChatColor.GOLD + warpCount + ChatColor.GRAY + " / " + ChatColor.YELLOW + limitStr,
                        ChatColor.GRAY + "Create warp: " + ChatColor.WHITE + "/setwarp <name>"
                )));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        if (!myWarps.isEmpty()) {
            int slot = 0;
            for (String warpName : myWarps) {
                if (slot >= 45) break;

                Location loc = UtilityCommands.warps.get(warpName);
                String iconId = UtilityCommands.getWarpIcon(warpName);
                ItemStack iconItem = getIconItem(iconId);

                boolean isPrivate = UtilityCommands.isWarpPrivate(warpName);

                ItemMeta meta = iconItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + warpName);
                    List<String> lore = new ArrayList<>();
                    if (loc != null && loc.getWorld() != null) {
                        lore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
                        lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
                    }

                    // Display Owner info
                    lore.add(ChatColor.GRAY + "Owner: " + ChatColor.GREEN + "You (" + player.getName() + ")");

                    if (isPrivate) {
                        lore.add(ChatColor.RED + "Visibility: Private " + ChatColor.DARK_GRAY + "(Only you can see/tp)");
                    } else {
                        lore.add(ChatColor.GREEN + "Visibility: Public " + ChatColor.GRAY + "(Everyone can see/tp)");
                        long pubTime = UtilityCommands.getWarpPublicDate(warpName);
                        if (pubTime > 0) {
                            lore.add(ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + sdf.format(new Date(pubTime)));
                        } else {
                            lore.add(ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + "Server Origin");
                        }
                    }
                    lore.add("");

                    if (bedrock) {
                        lore.add(ChatColor.AQUA + "▶ Click: " + ChatColor.WHITE + "Open Warp Actions Menu");
                    } else {
                        lore.add(ChatColor.GREEN + "▶ Left-Click: " + ChatColor.WHITE + "Teleport to warp");
                        lore.add(ChatColor.YELLOW + "▶ Right-Click: " + ChatColor.WHITE + "Change icon");
                        lore.add(ChatColor.GOLD + "▶ Shift + Left-Click: " + ChatColor.WHITE + "Rename warp");
                        lore.add(ChatColor.RED + "▶ Shift + Right-Click: " + ChatColor.WHITE + "Delete warp");
                    }
                    meta.setLore(lore);
                    iconItem.setItemMeta(meta);
                }
                inv.setItem(slot++, iconItem);
            }
        } else {
            inv.setItem(22, createButton(Material.BOOK, "BOOK", (short) 0,
                    ChatColor.YELLOW + ChatColor.BOLD.toString() + "You have no personal warps!",
                    Arrays.asList(
                            ChatColor.GRAY + "You haven't created any personal warps yet.",
                            ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/setwarp <name>" + ChatColor.GRAY + " to create your own warp!"
                    )));
        }

        player.openInventory(inv);
    }

    public static void openWarpActionsMenu(StaffCore plugin, Player player, String warpName) {
        Inventory inv = Bukkit.createInventory(null, 27, WARP_ACTIONS_PREFIX + warpName);

        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        boolean isPrivate = UtilityCommands.isWarpPrivate(warpName);
        boolean isOwner = UtilityCommands.isWarpOwner(warpName, player.getUniqueId());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        // Resolve Owner Name
        UUID ownerUuid = UtilityCommands.warpOwners.get(warpName);
        String ownerName = "Server";
        if (ownerUuid != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
            ownerName = (op != null && op.getName() != null) ? op.getName() : "Unknown";
        }

        long pubTime = UtilityCommands.getWarpPublicDate(warpName);
        String pubSinceStr = pubTime > 0 ? sdf.format(new Date(pubTime)) : "Server Origin";

        // Slot 4: Warp Overview & Metadata Header (Bedrock Sub-Menu)
        ItemStack overviewItem = getIconItem(UtilityCommands.getWarpIcon(warpName));
        ItemMeta overviewMeta = overviewItem.getItemMeta();
        if (overviewMeta != null) {
            overviewMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + warpName + (isPrivate ? ChatColor.RED + " [Private]" : ""));
            List<String> overviewLore = new ArrayList<>();
            Location loc = UtilityCommands.warps.get(warpName);
            if (loc != null && loc.getWorld() != null) {
                overviewLore.add(ChatColor.GRAY + "World: " + ChatColor.YELLOW + loc.getWorld().getName());
                overviewLore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
            }

            // Display Owner in Bedrock Sub-Menu
            if (isOwner) {
                overviewLore.add(ChatColor.GRAY + "Owner: " + ChatColor.GREEN + "You (" + player.getName() + ")");
            } else {
                overviewLore.add(ChatColor.GRAY + "Owner: " + ChatColor.YELLOW + ownerName);
            }

            // Display "Since when is this warp public" in Bedrock Sub-Menu
            if (isPrivate) {
                overviewLore.add(ChatColor.RED + "Status: Private " + ChatColor.DARK_GRAY + "(Hidden from public)");
            } else {
                overviewLore.add(ChatColor.GREEN + "Status: Public " + ChatColor.GRAY + "(Visible to all)");
                overviewLore.add(ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + pubSinceStr);
            }

            overviewMeta.setLore(overviewLore);
            overviewItem.setItemMeta(overviewMeta);
        }
        inv.setItem(4, overviewItem);

        // Slot 10: Teleport Button
        inv.setItem(10, createButton(
                matchCrossVersionMaterial("ENDER_PEARL", "ENDER_PEARL"), "ENDER_PEARL", (short) 0,
                ChatColor.GREEN + ChatColor.BOLD.toString() + "Teleport",
                Arrays.asList(
                        ChatColor.GRAY + "Teleport instantly to " + ChatColor.GOLD + warpName + ChatColor.GRAY + ".",
                        ChatColor.GRAY + "Owner: " + (isOwner ? ChatColor.GREEN + "You" : ChatColor.YELLOW + ownerName),
                        !isPrivate ? ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + pubSinceStr : ChatColor.RED + "Status: Private"
                )));

        // Slot 12: Change Icon Button
        inv.setItem(12, createButton(
                Material.PAINTING, "PAINTING", (short) 0,
                ChatColor.YELLOW + ChatColor.BOLD.toString() + "Change Icon",
                Arrays.asList(ChatColor.GRAY + "Pick a custom icon for " + ChatColor.GOLD + warpName + ChatColor.GRAY + ".")));

        // Slot 14: Rename Button
        inv.setItem(14, createButton(
                matchCrossVersionMaterial("NAME_TAG", "NAME_TAG"), "NAME_TAG", (short) 0,
                ChatColor.GOLD + ChatColor.BOLD.toString() + "Rename Warp",
                Arrays.asList(ChatColor.GRAY + "Type a new name in chat for " + ChatColor.GOLD + warpName + ChatColor.GRAY + ".")));

        // Slot 15: Toggle Public / Private Visibility
        if (isPrivate) {
            inv.setItem(15, createButton(
                    matchCrossVersionMaterial("FERMENTED_SPIDER_EYE", "REDSTONE_TORCH_ON"), "FERMENTED_SPIDER_EYE", (short) 0,
                    ChatColor.RED + ChatColor.BOLD.toString() + "Visibility: Private",
                    Arrays.asList(
                            ChatColor.GRAY + "Currently: " + ChatColor.RED + "Private (Hidden)",
                            ChatColor.GRAY + "Only owner and staff can view/use this warp.",
                            "",
                            ChatColor.YELLOW + "Click to make " + ChatColor.GREEN + ChatColor.BOLD.toString() + "PUBLIC" + ChatColor.YELLOW + "!"
                    )));
        } else {
            inv.setItem(15, createButton(
                    matchCrossVersionMaterial("ENDER_EYE", "EYE_OF_ENDER"), "EYE_OF_ENDER", (short) 0,
                    ChatColor.GREEN + ChatColor.BOLD.toString() + "Visibility: Public",
                    Arrays.asList(
                            ChatColor.GRAY + "Currently: " + ChatColor.GREEN + "Public (Visible to all)",
                            ChatColor.GRAY + "Public Since: " + ChatColor.AQUA + pubSinceStr,
                            "",
                            ChatColor.YELLOW + "Click to make " + ChatColor.RED + ChatColor.BOLD.toString() + "PRIVATE" + ChatColor.YELLOW + "!"
                    )));
        }

        // Slot 16: Delete Button
        inv.setItem(16, createButton(
                matchCrossVersionMaterial("REDSTONE_BLOCK", "REDSTONE_BLOCK"), "REDSTONE_BLOCK", (short) 0,
                ChatColor.RED + ChatColor.BOLD.toString() + "Delete Warp",
                Arrays.asList(ChatColor.GRAY + "Permanently remove warp " + ChatColor.GOLD + warpName + ChatColor.GRAY + ".")));

        // Slot 22: Back Button
        inv.setItem(22, createButton(
                Material.ARROW, "ARROW", (short) 0,
                ChatColor.WHITE + ChatColor.BOLD.toString() + "◀ Back",
                Collections.singletonList(ChatColor.GRAY + "Return to previous menu.")));

        player.openInventory(inv);
    }

    // =========================================================================
    //                         WARP ICON PICKER GUI
    // =========================================================================

    public static void openWarpIconPicker(StaffCore plugin, Player player, String warpName) {
        Inventory inv = Bukkit.createInventory(null, 27, WARP_ICON_PICKER_PREFIX + warpName);

        ItemStack filler = createGrayPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        String currentIcon = UtilityCommands.getWarpIcon(warpName);
        for (int i = 0; i < ICON_CHOICES.size() && i < 18; i++) {
            inv.setItem(i, ICON_CHOICES.get(i).createItem(currentIcon));
        }

        // Slot 22: Back Button
        inv.setItem(22, createButton(
                Material.ARROW, "ARROW", (short) 0,
                ChatColor.WHITE + ChatColor.BOLD.toString() + "◀ Back",
                Collections.singletonList(ChatColor.GRAY + "Return without changing.")));

        player.openInventory(inv);
    }

    // =========================================================================
    //                         HELPER METHODS
    // =========================================================================

    public static ItemStack getIconItem(String iconId) {
        if (iconId != null) {
            for (IconChoice choice : ICON_CHOICES) {
                if (choice.id.equalsIgnoreCase(iconId)) {
                    return createCrossVersionItem(choice.modernMat, choice.legacyMat, choice.legacyData);
                }
            }
        }
        return createCrossVersionItem("ENDER_EYE", "EYE_OF_ENDER", (short) 0);
    }

    public static ItemStack createSkullItem() {
        try {
            Material mat = Material.valueOf("PLAYER_HEAD");
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (short) 3);
        }
    }

    public static void setSkullOwner(SkullMeta meta, OfflinePlayer target, String targetName) {
        try {
            Method m = meta.getClass().getMethod("setOwningPlayer", OfflinePlayer.class);
            m.invoke(meta, target);
        } catch (Throwable t) {
            try {
                meta.setOwner(targetName);
            } catch (Throwable ignored) {}
        }
    }

    private static ItemStack createGrayPane() {
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

    private static Material matchCrossVersionMaterial(String modernName, String legacyName) {
        try {
            return Material.valueOf(modernName);
        } catch (IllegalArgumentException e) {
            return Material.valueOf(legacyName);
        }
    }

    private static ItemStack createCrossVersionItem(String modernName, String legacyName, short legacyData) {
        try {
            Material mat = Material.valueOf(modernName);
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            Material mat = Material.valueOf(legacyName);
            return new ItemStack(mat, 1, legacyData);
        }
    }

    private static ItemStack createButton(Material material, String legacyFallback, short data, String name, List<String> lore) {
        ItemStack item;
        try {
            item = new ItemStack(material);
        } catch (Throwable t) {
            item = new ItemStack(Material.valueOf(legacyFallback), 1, data);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}