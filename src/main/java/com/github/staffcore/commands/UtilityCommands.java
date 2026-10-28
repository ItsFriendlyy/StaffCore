package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import com.github.staffcore.gui.TeleportGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public class UtilityCommands implements CommandExecutor {

    private final StaffCore plugin;
    public static final Set<UUID> invinciblePlayers = new HashSet<>();
    public static final Set<UUID> flyPlayers = new HashSet<>();
    public static final Map<UUID, Float> walkSpeeds = new HashMap<>();
    public static final Map<UUID, Float> flySpeeds = new HashMap<>();

    // Spawn, Warps, and Homes storage (persisted in states.yml)
    public static Location spawnLocation = null;
    public static final Map<String, Location> warps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public static final Map<String, String> warpIcons = new HashMap<>(); // warpName -> iconId
    public static final Map<String, UUID> warpOwners = new HashMap<>(); // warpName -> owner UUID
    public static final Set<String> privateWarps = new HashSet<>(); // lowercase warp names that are private

    public static final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    public static final Map<UUID, Map<String, String>> playerHomeIcons = new HashMap<>(); // uuid -> (homeName -> iconId)

    public static final String INVSEE_PREFIX = ChatColor.DARK_GRAY + "Invsee: " + ChatColor.DARK_RED;
    public static final String ARMOR_PREFIX = ChatColor.DARK_GRAY + "Armor: " + ChatColor.DARK_RED;

    public UtilityCommands(StaffCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isInvincible(UUID uuid) {
        return invinciblePlayers.contains(uuid);
    }

    public static void setInvincible(UUID uuid, boolean state) {
        if (state) invinciblePlayers.add(uuid);
        else invinciblePlayers.remove(uuid);
    }

    public static boolean isFlyEnabled(UUID uuid) {
        return flyPlayers.contains(uuid);
    }

    public static void setFlyEnabled(UUID uuid, boolean state) {
        if (state) flyPlayers.add(uuid);
        else flyPlayers.remove(uuid);
    }

    public static Set<String> getWarpNames() {
        return Collections.unmodifiableSet(warps.keySet());
    }

    public static Set<String> getPlayerHomeNames(UUID uuid) {
        if (uuid == null) return Collections.emptySet();
        Map<String, Location> homes = playerHomes.get(uuid);
        if (homes == null) return Collections.emptySet();
        return Collections.unmodifiableSet(homes.keySet());
    }

    public static String getWarpIcon(String warpName) {
        if (warpName == null) return "end";
        return warpIcons.getOrDefault(warpName.toLowerCase(), "end");
    }

    public static void setWarpIcon(String warpName, String iconId) {
        if (warpName == null || iconId == null) return;
        warpIcons.put(warpName.toLowerCase(), iconId.toLowerCase());
    }

    public static String getHomeIcon(UUID uuid, String homeName) {
        if (uuid == null || homeName == null) return "home";
        Map<String, String> map = playerHomeIcons.get(uuid);
        if (map == null) return "home";
        return map.getOrDefault(homeName.toLowerCase(), "home");
    }

    public static void setHomeIcon(UUID uuid, String homeName, String iconId) {
        if (uuid == null || homeName == null || iconId == null) return;
        Map<String, String> map = playerHomeIcons.computeIfAbsent(uuid, k -> new HashMap<>());
        map.put(homeName.toLowerCase(), iconId.toLowerCase());
    }

    public static Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    public static Set<String> getPlayerWarps(UUID uuid) {
        if (uuid == null) return Collections.emptySet();
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, UUID> entry : warpOwners.entrySet()) {
            if (uuid.equals(entry.getValue()) && warps.containsKey(entry.getKey())) {
                set.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public static int getPlayerWarpCount(UUID uuid) {
        if (uuid == null) return 0;
        int count = 0;
        for (Map.Entry<String, UUID> entry : warpOwners.entrySet()) {
            if (uuid.equals(entry.getValue()) && warps.containsKey(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    public static boolean isWarpOwner(String warpName, UUID uuid) {
        if (warpName == null || uuid == null) return false;
        UUID owner = warpOwners.get(warpName.toLowerCase());
        return uuid.equals(owner);
    }

    public static boolean isWarpPrivate(String warpName) {
        if (warpName == null) return false;
        return privateWarps.contains(warpName.toLowerCase());
    }

    public static void setWarpPrivate(String warpName, boolean isPrivate) {
        if (warpName == null) return;
        if (isPrivate) {
            privateWarps.add(warpName.toLowerCase());
        } else {
            privateWarps.remove(warpName.toLowerCase());
        }
    }

    public static boolean canAccessWarp(Player player, String warpName) {
        if (warpName == null) return false;
        if (!isWarpPrivate(warpName)) return true;
        if (player == null) return true;
        if (player.hasPermission("staffcore.warps.admin")
                || player.hasPermission("staffcore.command.warp.others")
                || player.hasPermission("staffcore.admin")
                || player.isOp()) {
            return true;
        }
        return isWarpOwner(warpName, player.getUniqueId());
    }

    public static int getMaxHomes(Player player, StaffCore plugin) {
        if (player == null) return 5;
        if (player.hasPermission("staffcore.command.sethome.unlimited") || player.isOp()) {
            return Integer.MAX_VALUE;
        }
        int limit = (plugin != null) ? plugin.getConfig().getInt("homes.max-homes", 5) : 5;
        if (player.hasPermission("staffcore.homes.15")) limit = Math.max(limit, 15);
        if (player.hasPermission("staffcore.homes.10")) limit = Math.max(limit, 10);

        for (org.bukkit.permissions.PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase();
            if (perm.startsWith("staffcore.homes.") && pai.getValue()) {
                try {
                    int n = Integer.parseInt(perm.substring("staffcore.homes.".length()));
                    limit = Math.max(limit, n);
                } catch (NumberFormatException ignored) {}
            }
        }
        return limit;
    }

    public static int getMaxWarps(Player player, StaffCore plugin) {
        if (player == null) return 2;
        if (player.hasPermission("staffcore.warps.unlimited")
                || player.hasPermission("staffcore.command.setwarp.unlimited")
                || player.isOp()) {
            return Integer.MAX_VALUE;
        }
        int limit = (plugin != null) ? plugin.getConfig().getInt("warps.max-warps", 2) : 2;
        if (player.hasPermission("staffcore.warps.8")) limit = Math.max(limit, 8);
        if (player.hasPermission("staffcore.warps.4")) limit = Math.max(limit, 4);

        for (org.bukkit.permissions.PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase();
            if (perm.startsWith("staffcore.warps.") && pai.getValue()) {
                try {
                    int n = Integer.parseInt(perm.substring("staffcore.warps.".length()));
                    limit = Math.max(limit, n);
                } catch (NumberFormatException ignored) {}
            }
        }
        return limit;
    }

    public static void loadStates(StaffCore plugin) {
        if (plugin == null) return;
        File file = new File(plugin.getDataFolder(), "states.yml");
        if (!file.exists()) return;

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        invinciblePlayers.clear();
        flyPlayers.clear();
        walkSpeeds.clear();
        flySpeeds.clear();
        spawnLocation = null;
        warps.clear();
        warpIcons.clear();
        warpOwners.clear();
        privateWarps.clear();
        playerHomes.clear();
        playerHomeIcons.clear();

        for (String s : data.getStringList("invincible")) {
            try { invinciblePlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }

        for (String s : data.getStringList("fly")) {
            try { flyPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }

        ConfigurationSection walkSec = data.getConfigurationSection("walk-speed");
        if (walkSec != null) {
            for (String key : walkSec.getKeys(false)) {
                try { walkSpeeds.put(UUID.fromString(key), (float) walkSec.getDouble(key, 0.2)); } catch (Exception ignored) {}
            }
        }

        ConfigurationSection flySec = data.getConfigurationSection("fly-speed");
        if (flySec != null) {
            for (String key : flySec.getKeys(false)) {
                try { flySpeeds.put(UUID.fromString(key), (float) flySec.getDouble(key, 0.1)); } catch (Exception ignored) {}
            }
        }

        ConfigurationSection spawnSec = data.getConfigurationSection("spawn");
        if (spawnSec != null) {
            spawnLocation = deserializeLocation(spawnSec);
        }

        ConfigurationSection warpSec = data.getConfigurationSection("warps");
        if (warpSec != null) {
            for (String warpName : warpSec.getKeys(false)) {
                Location loc = deserializeLocation(warpSec.getConfigurationSection(warpName));
                if (loc != null) warps.put(warpName.toLowerCase(), loc);
            }
        }

        ConfigurationSection warpIconsSec = data.getConfigurationSection("warp-icons");
        if (warpIconsSec != null) {
            for (String warpName : warpIconsSec.getKeys(false)) {
                String icon = warpIconsSec.getString(warpName);
                if (icon != null) warpIcons.put(warpName.toLowerCase(), icon);
            }
        }

        ConfigurationSection ownersSec = data.getConfigurationSection("warp-owners");
        if (ownersSec != null) {
            for (String warpName : ownersSec.getKeys(false)) {
                try { warpOwners.put(warpName.toLowerCase(), UUID.fromString(ownersSec.getString(warpName))); } catch (Exception ignored) {}
            }
        }

        for (String s : data.getStringList("private-warps")) {
            if (s != null && !s.trim().isEmpty()) privateWarps.add(s.trim().toLowerCase());
        }

        ConfigurationSection homesSec = data.getConfigurationSection("homes");
        if (homesSec != null) {
            for (String uuidStr : homesSec.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(uuidStr);
                    ConfigurationSection userHomesSec = homesSec.getConfigurationSection(uuidStr);
                    if (userHomesSec != null) {
                        Map<String, Location> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                        for (String homeName : userHomesSec.getKeys(false)) {
                            Location loc = deserializeLocation(userHomesSec.getConfigurationSection(homeName));
                            if (loc != null) map.put(homeName.toLowerCase(), loc);
                        }
                        if (!map.isEmpty()) playerHomes.put(u, map);
                    }
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection homeIconsSec = data.getConfigurationSection("home-icons");
        if (homeIconsSec != null) {
            for (String uuidStr : homeIconsSec.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(uuidStr);
                    ConfigurationSection userIcons = homeIconsSec.getConfigurationSection(uuidStr);
                    if (userIcons != null) {
                        Map<String, String> iconMap = new HashMap<>();
                        for (String homeName : userIcons.getKeys(false)) {
                            String icon = userIcons.getString(homeName);
                            if (icon != null) iconMap.put(homeName.toLowerCase(), icon);
                        }
                        if (!iconMap.isEmpty()) playerHomeIcons.put(u, iconMap);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public static void saveStates(StaffCore plugin) {
        if (plugin == null) return;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), "states.yml");
        FileConfiguration data = new YamlConfiguration();

        List<String> invList = new ArrayList<>();
        for (UUID uuid : invinciblePlayers) invList.add(uuid.toString());
        data.set("invincible", invList);

        List<String> flyList = new ArrayList<>();
        for (UUID uuid : flyPlayers) flyList.add(uuid.toString());
        data.set("fly", flyList);

        for (Map.Entry<UUID, Float> entry : walkSpeeds.entrySet()) data.set("walk-speed." + entry.getKey().toString(), entry.getValue());
        for (Map.Entry<UUID, Float> entry : flySpeeds.entrySet()) data.set("fly-speed." + entry.getKey().toString(), entry.getValue());

        if (spawnLocation != null) serializeLocation(data, "spawn", spawnLocation);

        data.set("warps", null);
        for (Map.Entry<String, Location> entry : warps.entrySet()) serializeLocation(data, "warps." + entry.getKey().toLowerCase(), entry.getValue());

        data.set("warp-icons", null);
        for (Map.Entry<String, String> entry : warpIcons.entrySet()) data.set("warp-icons." + entry.getKey().toLowerCase(), entry.getValue());

        data.set("warp-owners", null);
        for (Map.Entry<String, UUID> entry : warpOwners.entrySet()) {
            if (entry.getValue() != null && warps.containsKey(entry.getKey())) {
                data.set("warp-owners." + entry.getKey().toLowerCase(), entry.getValue().toString());
            }
        }

        List<String> privList = new ArrayList<>();
        for (String w : privateWarps) {
            if (warps.containsKey(w)) privList.add(w.toLowerCase());
        }
        data.set("private-warps", privList);

        data.set("homes", null);
        for (Map.Entry<UUID, Map<String, Location>> userEntry : playerHomes.entrySet()) {
            String base = "homes." + userEntry.getKey().toString();
            for (Map.Entry<String, Location> homeEntry : userEntry.getValue().entrySet()) {
                serializeLocation(data, base + "." + homeEntry.getKey().toLowerCase(), homeEntry.getValue());
            }
        }

        data.set("home-icons", null);
        for (Map.Entry<UUID, Map<String, String>> userEntry : playerHomeIcons.entrySet()) {
            String base = "home-icons." + userEntry.getKey().toString();
            for (Map.Entry<String, String> iconEntry : userEntry.getValue().entrySet()) {
                data.set(base + "." + iconEntry.getKey().toLowerCase(), iconEntry.getValue());
            }
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save states.yml: " + e.getMessage());
        }
    }

    private static void serializeLocation(FileConfiguration data, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", (double) loc.getYaw());
        data.set(path + ".pitch", (double) loc.getPitch());
    }

    private static Location deserializeLocation(ConfigurationSection sec) {
        if (sec == null) return null;
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"), (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
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
            meta.setDisplayName(ChatColor.GRAY + " ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public static void openInvsee(Player staff, Player target) {
        Inventory gui = Bukkit.createInventory(null, 54, INVSEE_PREFIX + target.getName());
        ItemStack pane = createGrayPane();

        gui.setItem(0, target.getInventory().getHelmet());
        gui.setItem(1, target.getInventory().getChestplate());
        gui.setItem(2, target.getInventory().getLeggings());
        gui.setItem(3, target.getInventory().getBoots());
        gui.setItem(4, pane);

        try {
            Method m = target.getInventory().getClass().getMethod("getItemInOffHand");
            ItemStack offhand = (ItemStack) m.invoke(target.getInventory());
            if (offhand != null) gui.setItem(5, offhand);
        } catch (Throwable ignored) {}

        for (int i = 6; i <= 17; i++) gui.setItem(i, pane);
        for (int i = 9; i <= 35; i++) gui.setItem(18 + (i - 9), target.getInventory().getItem(i));
        for (int i = 0; i <= 8; i++) gui.setItem(45 + i, target.getInventory().getItem(i));

        staff.openInventory(gui);
        staff.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aOpened inventory of &e" + target.getName() + "&a."));
    }

    public static void syncGuiToTarget(Inventory top, Player target) {
        if (top == null || target == null || !target.isOnline()) return;

        target.getInventory().setHelmet(top.getItem(0));
        target.getInventory().setChestplate(top.getItem(1));
        target.getInventory().setLeggings(top.getItem(2));
        target.getInventory().setBoots(top.getItem(3));

        try {
            Method m = target.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class);
            m.invoke(target.getInventory(), top.getItem(5));
        } catch (Throwable ignored) {}

        for (int i = 9; i <= 35; i++) target.getInventory().setItem(i, top.getItem(18 + (i - 9)));
        for (int i = 0; i <= 8; i++) target.getInventory().setItem(i, top.getItem(45 + i));

        target.updateInventory();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (!(sender instanceof Player)) {
            if (cmd.equals("setspawn") || cmd.equals("setwarp") || cmd.equals("sethome") || cmd.equals("delhome") || cmd.equals("home")) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player in-game!");
                return true;
            }
            if ((cmd.equals("fly") || cmd.equals("speed") || cmd.equals("tphere") || cmd.equals("invsee") || cmd.equals("enchant") || cmd.equals("repair") || cmd.equals("clear")) && args.length == 0) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player in-game!");
                return true;
            }
        }

        switch (cmd) {
            case "setspawn": return handleSetSpawn((Player) sender);
            case "spawn": return handleSpawn(sender, args);
            case "setwarp": return handleSetWarp((Player) sender, args);
            case "delwarp": return handleDelWarp(sender, args);
            case "warp": return handleWarp(sender, args);
            case "sethome": return handleSetHome((Player) sender, args);
            case "delhome": return handleDelHome((Player) sender, args);
            case "home": return handleHome((Player) sender, args);
            case "gmc": return handleGamemode(sender, args, GameMode.CREATIVE, "Creative");
            case "gms": return handleGamemode(sender, args, GameMode.SURVIVAL, "Survival");
            case "gmsp": return handleGamemode(sender, args, GameMode.SPECTATOR, "Spectator");
            case "gma": return handleGamemode(sender, args, GameMode.ADVENTURE, "Adventure");
            case "fly": return handleFly(sender, args);
            case "speed": return handleSpeed(sender, args);
            case "invincible": return handleInvincible(sender, args);
            case "tphere": return handleTpHere((Player) sender, args);
            case "tpall": return handleTpAll(sender, args);
            case "invsee": return handleInvsee((Player) sender, args);
            case "enchant": return handleEnchant((Player) sender, args);
            case "sendserver": return handleSendServer(sender, args);
            case "heal": return handleHeal(sender, args);
            case "feed": return handleFeed(sender, args);
            case "repair": return handleRepair((Player) sender, args);
            case "struck":
            case "strike": return handleStruck(sender, args);
            case "sudo": return handleSudo(sender, args);
            case "give": return handleGive(sender, args);
            case "effect": return handleEffect(sender, args);
            case "cleargrounditems":
            case "cleargrouditems":
            case "clearitems":
            case "clearlag": return handleClearGroundItems(sender);
            case "clear":
            case "ci":
            case "clearinventory": return handleClear(sender, args);
            default: return false;
        }
    }

    private boolean handleSetSpawn(Player player) {
        Location loc = player.getLocation().clone();
        spawnLocation = loc;
        if (loc.getWorld() != null) {
            try { loc.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()); } catch (Throwable ignored) {}
        }
        saveStates(plugin);
        player.sendMessage(plugin.getMessageManager().color(
                plugin.getConfig().getString("messages.setspawn-success", "&aSuccessfully set the server spawn at your current location!")));
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String[] args) {
        Location dest = getSpawnLocation();
        if (dest == null) {
            if (sender instanceof Player && ((Player) sender).getWorld() != null) {
                dest = ((Player) sender).getWorld().getSpawnLocation();
            } else if (!Bukkit.getWorlds().isEmpty()) {
                dest = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
        }
        if (dest == null) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.spawn-not-set", "&cThe server spawn has not been set yet! Use &e/setspawn&c.")));
            return true;
        }

        if (args.length > 0 && (sender.hasPermission("staffcore.command.spawn.others") || sender.hasPermission("staffcore.admin") || sender.isOp())) {
            if (args[0].equals("*")) {
                int count = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.teleport(dest);
                    if (!p.equals(sender)) {
                        p.sendMessage(plugin.getMessageManager().color(
                                plugin.getConfig().getString("messages.spawn-teleported", "&aTeleported to &espawn&a.")));
                    }
                    count++;
                }
                sender.sendMessage(plugin.getMessageManager().color("&aTeleported &e" + count + " &aonline players to &espawn&a."));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.player-not-found", "&cPlayer &e%target% &cwas not found!").replace("%target%", args[0])));
                return true;
            }
            target.teleport(dest);
            sender.sendMessage(plugin.getMessageManager().color("&aTeleported &e" + target.getName() + " &ato &espawn&a."));
            if (!target.equals(sender)) {
                target.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.spawn-teleported", "&aTeleported to &espawn&a.")));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Usage from console: /spawn <player|*>");
            return true;
        }

        Player player = (Player) sender;
        player.teleport(dest);
        player.sendMessage(plugin.getMessageManager().color(
                plugin.getConfig().getString("messages.spawn-teleported", "&aTeleported to &espawn&a.")));
        return true;
    }

    private boolean handleSetWarp(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getMessageManager().color("&cUsage: /setwarp <warp_name>"));
            return true;
        }
        String warpName = args[0].toLowerCase();

        if (warps.containsKey(warpName)) {
            UUID existingOwner = warpOwners.get(warpName);
            if (existingOwner != null && !existingOwner.equals(player.getUniqueId())
                    && !player.hasPermission("staffcore.command.setwarp.admin")
                    && !player.hasPermission("staffcore.admin")
                    && !player.isOp()) {
                player.sendMessage(plugin.getMessageManager().color(
                        "&cYou do not own the warp &e" + warpName + "&c! You cannot overwrite it."));
                return true;
            }
        } else {
            int maxWarps = getMaxWarps(player, plugin);
            int currentWarps = getPlayerWarpCount(player.getUniqueId());
            if (maxWarps != Integer.MAX_VALUE && maxWarps > 0 && currentWarps >= maxWarps) {
                player.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.warp-limit-reached", "&cYou have reached the maximum limit of &e%max% &cwarps!")
                                .replace("%max%", String.valueOf(maxWarps))));
                return true;
            }
            warpOwners.put(warpName, player.getUniqueId());
        }

        warps.put(warpName, player.getLocation().clone());
        if (!warpIcons.containsKey(warpName)) {
            warpIcons.put(warpName, "end");
        }
        saveStates(plugin);
        player.sendMessage(plugin.getMessageManager().color(
                plugin.getConfig().getString("messages.setwarp-success", "&aSuccessfully set warp &e%warp% &aat your current location.")
                        .replace("%warp%", warpName)));
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /delwarp <warp_name>"));
            return true;
        }
        String warpName = args[0].toLowerCase();

        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID owner = warpOwners.get(warpName);
            if (owner != null && !owner.equals(player.getUniqueId())
                    && !player.hasPermission("staffcore.command.delwarp.admin")
                    && !player.hasPermission("staffcore.admin")
                    && !player.isOp()) {
                player.sendMessage(plugin.getMessageManager().color(
                        "&cYou do not own the warp &e" + warpName + "&c! You can only delete your own warps."));
                return true;
            }
        }

        if (warps.remove(warpName) != null) {
            warpIcons.remove(warpName);
            warpOwners.remove(warpName);
            privateWarps.remove(warpName);
            saveStates(plugin);
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.delwarp-success", "&aSuccessfully deleted warp &e%warp%&a.")
                            .replace("%warp%", warpName)));
        } else {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.warp-not-found", "&cWarp &e%warp% &cwas not found!")
                            .replace("%warp%", warpName)));
        }
        return true;
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            if (sender instanceof Player) {
                TeleportGui.openWarpGui(plugin, (Player) sender, 1);
                return true;
            }
            if (warps.isEmpty()) {
                sender.sendMessage(plugin.getMessageManager().color("&cNo warps have been set yet! Usage: /warp <warp_name>"));
            } else {
                sender.sendMessage(plugin.getMessageManager().color(
                        "&aAvailable warps (&e" + warps.size() + "&a): &f" + String.join("&7, &f", warps.keySet())));
            }
            return true;
        }

        String warpName = args[0].toLowerCase();

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!canAccessWarp(player, warpName)) {
                player.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.warp-is-private", "&cThis warp is private!")));
                return true;
            }
        }

        Location loc = warps.get(warpName);
        if (loc == null || loc.getWorld() == null) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.warp-not-found", "&cWarp &e%warp% &cwas not found!")
                            .replace("%warp%", warpName)));
            return true;
        }

        if (args.length >= 2 && (sender.hasPermission("staffcore.command.warp.others") || sender.hasPermission("staffcore.admin") || sender.isOp())) {
            if (args[1].equals("*")) {
                int count = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.teleport(loc);
                    if (!p.equals(sender)) {
                        p.sendMessage(plugin.getMessageManager().color(
                                plugin.getConfig().getString("messages.warp-teleported", "&aTeleported to warp &e%warp%&a.")
                                        .replace("%warp%", warpName)));
                    }
                    count++;
                }
                sender.sendMessage(plugin.getMessageManager().color("&aTeleported &e" + count + " &aonline players to warp &e" + warpName + "&a."));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.player-not-found", "&cPlayer &e%target% &cwas not found!").replace("%target%", args[1])));
                return true;
            }
            target.teleport(loc);
            sender.sendMessage(plugin.getMessageManager().color("&aTeleported &e" + target.getName() + " &ato warp &e" + warpName + "&a."));
            if (!target.equals(sender)) {
                target.sendMessage(plugin.getMessageManager().color(
                        plugin.getConfig().getString("messages.warp-teleported", "&aTeleported to warp &e%warp%&a.")
                                .replace("%warp%", warpName)));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Usage from console: /warp <warp_name> <player|*>");
            return true;
        }

        Player player = (Player) sender;
        player.teleport(loc);
        player.sendMessage(plugin.getMessageManager().color(
                plugin.getConfig().getString("messages.warp-teleported", "&aTeleported to warp &e%warp%&a.")
                        .replace("%warp%", warpName)));
        return true;
    }

    private boolean handleSetHome(Player player, String[] args) {
        String homeName = args.length >= 1 ? args[0].toLowerCase() : "home";
        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = playerHomes.computeIfAbsent(uuid, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));

        int maxHomes = getMaxHomes(player, plugin);
        if (maxHomes != Integer.MAX_VALUE && maxHomes > 0 && !homes.containsKey(homeName) && homes.size() >= maxHomes) {
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.home-limit-reached", "&cYou have reached the maximum limit of &e%max% &chomes!")
                            .replace("%max%", String.valueOf(maxHomes))));
            return true;
        }

        homes.put(homeName, player.getLocation().clone());
        if (!getHomeIcon(uuid, homeName).equals("home")) {
            // keep existing custom icon
        } else {
            setHomeIcon(uuid, homeName, "home");
        }
        saveStates(plugin);
        player.sendMessage(plugin.getMessageManager().color(
                plugin.getConfig().getString("messages.sethome-success", "&aSuccessfully set home &e%home% &aat your current location.")
                        .replace("%home%", homeName)));
        return true;
    }

    private boolean handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getMessageManager().color("&cUsage: /delhome <home_name>"));
            return true;
        }
        String homeName = args[0].toLowerCase();
        Map<String, Location> homes = playerHomes.get(player.getUniqueId());
        if (homes != null && homes.remove(homeName) != null) {
            Map<String, String> iconMap = playerHomeIcons.get(player.getUniqueId());
            if (iconMap != null) iconMap.remove(homeName);
            saveStates(plugin);
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.delhome-success", "&aSuccessfully deleted home &e%home%&a.")
                            .replace("%home%", homeName)));
        } else {
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.home-not-found", "&cHome &e%home% &cwas not found!")
                            .replace("%home%", homeName)));
        }
        return true;
    }

    private boolean handleHome(Player player, String[] args) {
        Map<String, Location> homes = playerHomes.get(player.getUniqueId());

        if (args.length < 1) {
            TeleportGui.openHomeGui(plugin, player);
            return true;
        }

        String homeName = args[0].toLowerCase();
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.home-not-found", "&cHome &e%home% &cwas not found!")
                            .replace("%home%", homeName)));
            return true;
        }

        Location loc = homes.get(homeName);
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.home-not-found", "&cHome &e%home% &cwas not found!")
                            .replace("%home%", homeName)));
            return true;
        }

        player.teleport(loc);
        player.sendMessage(plugin.getMessageManager().color(
                plugin.getConfig().getString("messages.home-teleported", "&aTeleported to home &e%home%&a.")
                        .replace("%home%", homeName)));
        return true;
    }

    private boolean handleGamemode(CommandSender sender, String[] args, GameMode mode, String modeName) {
        if (args.length > 0) {
            if (!sender.hasPermission("staffcore.command.gamemode.others") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to change other players' game modes!");
                return true;
            }
            if (args[0].equals("*")) {
                int count = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setGameMode(mode);
                    if (!p.equals(sender)) p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour game mode was set to &e" + modeName + "&a by staff."));
                    count++;
                }
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSet game mode to &e" + modeName + "&a for &e" + count + " &aonline players."));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
            target.setGameMode(mode);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSet game mode to &e" + modeName + "&a for &e" + target.getName() + "&a."));
            if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour game mode was set to &e" + modeName + "&a by staff."));
            return true;
        } else if (sender instanceof Player) {
            Player target = (Player) sender;
            target.setGameMode(mode);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSet game mode to &e" + modeName + "&a for &e" + target.getName() + "&a."));
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /" + modeName.toLowerCase() + " <player|*>");
            return true;
        }
    }

    private boolean handleFly(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                boolean state = !target.getAllowFlight();
                target.setAllowFlight(state);
                if (!state) target.setFlying(false);
                setFlyEnabled(target.getUniqueId(), state);
                String status = state ? "&2enabled" : "&4disabled";
                if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour flight mode was " + status + " &aby staff."));
                count++;
            }
            saveStates(plugin);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aToggled flight mode for &e" + count + " &aonline players."));
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /fly <player|*>");
            return true;
        }

        boolean state = !target.getAllowFlight();
        target.setAllowFlight(state);
        if (!state) target.setFlying(false);
        setFlyEnabled(target.getUniqueId(), state);
        saveStates(plugin);

        String status = state ? "&2enabled" : "&4disabled";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFlight mode " + status + " &afor &e" + target.getName() + "&a."));
        if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour flight mode was " + status + " &aby staff."));
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /speed <1-10> [player|*]");
            return true;
        }

        int speedVal;
        try {
            speedVal = Integer.parseInt(args[0]);
            if (speedVal < 1 || speedVal > 10) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Speed must be between 1 and 10!");
            return true;
        }

        float flySpeed = Math.min(1.0f, (speedVal == 1) ? 0.1f : (0.1f + (speedVal - 1) * (0.9f / 9.0f)));
        float walkSpeed = Math.min(1.0f, (speedVal == 1) ? 0.2f : (0.2f + (speedVal - 1) * (0.8f / 9.0f)));

        if (args.length > 1 && args[1].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.isFlying()) {
                    target.setFlySpeed(flySpeed);
                    flySpeeds.put(target.getUniqueId(), flySpeed);
                } else {
                    target.setWalkSpeed(walkSpeed);
                    walkSpeeds.put(target.getUniqueId(), walkSpeed);
                }
                count++;
            }
            saveStates(plugin);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSpeed set to &e" + speedVal + "&a for &e" + count + " &aonline players."));
            return true;
        }

        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[1] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /speed <1-10> <player|*>");
            return true;
        }

        if (target.isFlying()) {
            target.setFlySpeed(flySpeed);
            flySpeeds.put(target.getUniqueId(), flySpeed);
            saveStates(plugin);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFly speed set to &e" + speedVal + "&a for &e" + target.getName() + "&a."));
        } else {
            target.setWalkSpeed(walkSpeed);
            walkSpeeds.put(target.getUniqueId(), walkSpeed);
            saveStates(plugin);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aWalk speed set to &e" + speedVal + "&a for &e" + target.getName() + "&a."));
        }
        return true;
    }

    private boolean handleInvincible(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                UUID uuid = target.getUniqueId();
                boolean nowInvincible = !isInvincible(uuid);
                setInvincible(uuid, nowInvincible);
                String status = nowInvincible ? "&2enabled" : "&4disabled";
                if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour invincibility was " + status + " &aby staff."));
                count++;
            }
            saveStates(plugin);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aToggled invincibility for &e" + count + " &aonline players."));
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /invincible <player|*>");
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean nowInvincible = !isInvincible(uuid);
        setInvincible(uuid, nowInvincible);
        saveStates(plugin);

        String status = nowInvincible ? "&2enabled" : "&4disabled";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aInvincibility " + status + " &afor &e" + target.getName() + "&a."));
        if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour invincibility was " + status + " &aby staff."));
        return true;
    }

    private boolean handleTpHere(Player staff, String[] args) {
        if (args.length < 1) {
            staff.sendMessage(ChatColor.RED + "Usage: /tphere <player|*>");
            return true;
        }
        if (args[0].equals("*")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(staff)) continue;
                p.teleport(staff.getLocation());
                p.sendMessage(ChatColor.YELLOW + "You were teleported to " + staff.getName() + ".");
                count++;
            }
            staff.sendMessage(ChatColor.GREEN + "Teleported " + count + " players to your location.");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }
        target.teleport(staff.getLocation());
        staff.sendMessage(ChatColor.GREEN + "Teleported " + target.getName() + " to your location.");
        target.sendMessage(ChatColor.YELLOW + "You were teleported to " + staff.getName() + ".");
        return true;
    }

    private boolean handleTpAll(CommandSender sender, String[] args) {
        Location dest = null;
        String destName = "your location";

        if (args.length == 3 && sender instanceof Player) {
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                dest = new Location(((Player) sender).getWorld(), x, y, z);
                destName = String.format("%.1f, %.1f, %.1f", x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid coordinates! Usage: /tpall [player | x y z]");
                return true;
            }
        } else if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
            dest = target.getLocation();
            destName = target.getName() + "'s location";
        } else if (sender instanceof Player) {
            dest = ((Player) sender).getLocation();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage from console: /tpall <player>");
            return true;
        }

        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player && p.equals(sender)) continue;
            p.teleport(dest);
            p.sendMessage(ChatColor.YELLOW + "You were teleported to " + destName + ".");
            count++;
        }
        sender.sendMessage(ChatColor.GREEN + "Teleported " + count + " players to " + destName + ".");
        return true;
    }

    private boolean handleInvsee(Player staff, String[] args) {
        if (args.length < 1) {
            staff.sendMessage(ChatColor.RED + "Usage: /invsee <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            staff.sendMessage(ChatColor.RED + "Player " + args[0] + " not found or is offline!");
            return true;
        }
        if (target.equals(staff)) {
            staff.sendMessage(ChatColor.RED + "You cannot invsee yourself!");
            return true;
        }
        openInvsee(staff, target);
        return true;
    }

    private boolean handleEnchant(Player staff, String[] args) {
        if (args.length < 1) {
            staff.sendMessage(ChatColor.RED + "Usage: /enchant <enchantment> [level]");
            return true;
        }

        ItemStack item = staff.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            staff.sendMessage(ChatColor.RED + "You must be holding an item in your main hand to enchant!");
            return true;
        }

        Enchantment ench = getEnchantmentByName(args[0]);
        if (ench == null) {
            staff.sendMessage(ChatColor.RED + "Enchantment '" + args[0] + "' not found! Press TAB to view all available enchantments.");
            return true;
        }

        int level = 1;
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
                if (level < 0 || level > 255) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                staff.sendMessage(ChatColor.RED + "Level must be between 0 and 255!");
                return true;
            }
        } else {
            level = ench.getMaxLevel();
        }

        if (level == 0) {
            item.removeEnchantment(ench);
            staff.sendMessage(ChatColor.YELLOW + "Removed " + ench.getName() + " from held item.");
        } else {
            item.addUnsafeEnchantment(ench, level);
            staff.sendMessage(ChatColor.GREEN + "Applied " + ench.getName() + " level " + level + " to your held item!");
        }
        return true;
    }

    private Enchantment getEnchantmentByName(String name) {
        if (name == null) return null;
        String clean = name.toUpperCase().replace("MINECRAFT:", "").trim();
        Enchantment direct = Enchantment.getByName(clean);
        if (direct != null) return direct;

        switch (clean.toLowerCase()) {
            case "sharpness": return Enchantment.getByName("DAMAGE_ALL");
            case "smite": return Enchantment.getByName("DAMAGE_UNDEAD");
            case "bane_of_arthropods": case "baneofarthropods": case "boa": return Enchantment.getByName("DAMAGE_ARTHROPODS");
            case "knockback": return Enchantment.getByName("KNOCKBACK");
            case "fire_aspect": case "fireaspect": return Enchantment.getByName("FIRE_ASPECT");
            case "looting": return Enchantment.getByName("LOOT_BONUS_MOBS");
            case "efficiency": return Enchantment.getByName("DIG_SPEED");
            case "silk_touch": case "silktouch": return Enchantment.getByName("SILK_TOUCH");
            case "unbreaking": return Enchantment.getByName("DURABILITY");
            case "fortune": return Enchantment.getByName("LOOT_BONUS_BLOCKS");
            case "power": return Enchantment.getByName("ARROW_DAMAGE");
            case "punch": return Enchantment.getByName("ARROW_KNOCKBACK");
            case "flame": return Enchantment.getByName("ARROW_FIRE");
            case "infinity": return Enchantment.getByName("ARROW_INFINITE");
            case "protection": case "prot": return Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            case "fire_protection": case "fireprotection": case "fireprot": return Enchantment.getByName("PROTECTION_FIRE");
            case "feather_falling": case "featherfalling": return Enchantment.getByName("PROTECTION_FALL");
            case "blast_protection": case "blastprotection": return Enchantment.getByName("PROTECTION_EXPLOSIONS");
            case "projectile_protection": case "projprot": return Enchantment.getByName("PROTECTION_PROJECTILE");
            case "respiration": return Enchantment.getByName("OXYGEN");
            case "aqua_affinity": case "aquaaffinity": return Enchantment.getByName("WATER_WORKER");
            case "thorns": return Enchantment.getByName("THORNS");
            case "depth_strider": case "depthstrider": return Enchantment.getByName("DEPTH_STRIDER");
            case "luck_of_the_sea": case "luckofthesea": return Enchantment.getByName("LUCK");
            case "lure": return Enchantment.getByName("LURE");
            default: break;
        }

        for (Enchantment e : Enchantment.values()) {
            if (e.getName().equalsIgnoreCase(clean) || e.getName().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                return e;
            }
        }
        return null;
    }

    private boolean handleSendServer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /sendserver <player|*> <server>");
            return true;
        }

        String serverName = args[1];
        if (args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(b);
                    out.writeUTF("Connect");
                    out.writeUTF(serverName);
                    target.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
                    count++;
                } catch (Exception ignored) {}
            }
            sender.sendMessage(ChatColor.GREEN + "Sending " + count + " players to server " + serverName + "...");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }

        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            target.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            sender.sendMessage(ChatColor.GREEN + "Sending " + target.getName() + " to server " + serverName + "...");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to send player to server: " + e.getMessage());
        }
        return true;
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                healSinglePlayer(target);
                if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou have been fully healed by staff."));
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFully healed &e" + count + " &aonline players."));
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage from console: /heal <player|*>");
            return true;
        }

        healSinglePlayer(target);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFully healed &e" + target.getName() + "&a."));
        if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou have been fully healed by staff."));
        return true;
    }

    private void healSinglePlayer(Player target) {
        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setExhaustion(0.0f);
        target.setFireTicks(0);
        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }
    }

    private boolean handleFeed(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                target.setFoodLevel(20);
                target.setSaturation(20.0f);
                target.setExhaustion(0.0f);
                if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour appetite has been fully sated by staff."));
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFully fed &e" + count + " &aonline players."));
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage from console: /feed <player|*>");
            return true;
        }

        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setExhaustion(0.0f);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFully fed &e" + target.getName() + "&a."));
        if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour appetite has been fully sated by staff."));
        return true;
    }

    private boolean handleRepair(Player staff, String[] args) {
        ItemStack item = staff.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            staff.sendMessage(ChatColor.RED + "You must be holding an item in your hand to repair!");
            return true;
        }
        if (item.getType().getMaxDurability() <= 0) {
            staff.sendMessage(ChatColor.RED + "This item does not have durability and cannot be repaired!");
            return true;
        }
        item.setDurability((short) 0);
        staff.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aSuccessfully repaired &e" + item.getType().name().replace("_", " ") + "&a to full durability!"));
        return true;
    }

    private boolean handleStruck(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && target.equals(sender)) continue;
                Location loc = target.getLocation();
                if (loc.getWorld() != null) {
                    loc.getWorld().strikeLightning(loc);
                    count++;
                }
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aStruck lightning on &e" + count + " &aonline players!"));
            return true;
        }

        Location loc;
        String targetDesc;

        if (args.length > 0) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
            loc = target.getLocation();
            targetDesc = target.getName();
        } else if (sender instanceof Player) {
            Player p = (Player) sender;
            try {
                loc = p.getTargetBlock((Set<Material>) null, 120).getLocation();
            } catch (Throwable t) {
                loc = p.getLocation();
            }
            targetDesc = "your crosshair";
        } else {
            sender.sendMessage(ChatColor.RED + "Usage from console: /struck <player|*>");
            return true;
        }

        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().strikeLightning(loc);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aStruck lightning at &e" + targetDesc + "&a!"));
        }
        return true;
    }

    private boolean handleSudo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /sudo <player|*> <command | m:message>");
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String input = sb.toString().trim();

        if (args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && target.equals(sender)) continue;
                if (input.toLowerCase().startsWith("m:")) {
                    target.chat(input.substring(2).trim());
                } else {
                    target.performCommand(input.startsWith("/") ? input.substring(1) : input);
                }
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aForced &e" + count + " &aplayers to execute: &f" + input));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found or is offline!");
            return true;
        }

        if (input.toLowerCase().startsWith("m:")) {
            target.chat(input.substring(2).trim());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aForced &e" + target.getName() + " &ato chat: &f" + input.substring(2).trim()));
        } else {
            target.performCommand(input.startsWith("/") ? input.substring(1) : input);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aForced &e" + target.getName() + " &ato execute: &f" + input));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /give <player|*> <item> [amount]");
            return true;
        }

        Material mat = matchEasyMaterial(args[1]);
        if (mat == null || mat == Material.AIR) {
            sender.sendMessage(ChatColor.RED + "Item '" + args[1] + "' not found! Press TAB to autocomplete item names.");
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount '" + args[2] + "'!");
                return true;
            }
        }

        String itemName = mat.name().toLowerCase().replace("legacy_", "").replace("_", " ");

        if (args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                target.getInventory().addItem(new ItemStack(mat, amount));
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou received &e" + amount + "x " + itemName + " &afrom staff."));
                }
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aGave &e" + amount + "x " + itemName + " &ato &e" + count + " &aonline players."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }

        target.getInventory().addItem(new ItemStack(mat, amount));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aGave &e" + amount + "x " + itemName + " &ato &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou received &e" + amount + "x " + itemName + " &afrom staff."));
        }
        return true;
    }

    public static Material[] getAllRuntimeMaterials() {
        try {
            Material[] constants = Material.class.getEnumConstants();
            if (constants != null && constants.length > 0) return constants;
        } catch (Throwable ignored) {}
        return Material.values();
    }

    public static Material matchEasyMaterial(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String clean = input.trim().toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");
        if (clean.contains("NETHERRITE")) clean = clean.replace("NETHERRITE", "NETHERITE");

        try {
            Method matchModern = Material.class.getMethod("matchMaterial", String.class, boolean.class);
            Material m = (Material) matchModern.invoke(null, clean, false);
            if (m != null) return m;
        } catch (Throwable ignored) {}

        Material[] allMaterials = getAllRuntimeMaterials();
        for (Material m : allMaterials) {
            if (m.name().startsWith("LEGACY_")) continue;
            if (m.name().equalsIgnoreCase(clean)) return m;
        }

        Material direct = Material.matchMaterial(clean);
        if (direct != null) return direct;

        try { return Material.valueOf(clean); } catch (IllegalArgumentException ignored) {}

        String compact = clean.toLowerCase().replace("_", "");
        switch (compact) {
            case "gap": case "gapple": return Material.GOLDEN_APPLE;
            case "egap": case "notchapple": return findRuntimeMaterial("ENCHANTED_GOLDEN_APPLE", "GOLDEN_APPLE");
            case "totem": return findRuntimeMaterial("TOTEM_OF_UNDYING", "TOTEM");
            case "pearl": return Material.ENDER_PEARL;
            case "xpbottle": return findRuntimeMaterial("EXPERIENCE_BOTTLE", "EXP_BOTTLE");
            case "commandblock": case "cmdblock": return findRuntimeMaterial("COMMAND_BLOCK", "COMMAND");
            case "steak": return Material.COOKED_BEEF;
            case "netheritesword": return findRuntimeMaterial("NETHERITE_SWORD", null);
            case "netheritepick": case "netheritepickaxe": return findRuntimeMaterial("NETHERITE_PICKAXE", null);
            case "netheriteaxe": return findRuntimeMaterial("NETHERITE_AXE", null);
            case "netheriteingot": return findRuntimeMaterial("NETHERITE_INGOT", null);
            case "ancientdebris": return findRuntimeMaterial("ANCIENT_DEBRIS", null);
            default: break;
        }

        for (Material m : allMaterials) {
            if (m.name().startsWith("LEGACY_")) continue;
            if (m.name().replace("_", "").equalsIgnoreCase(compact) || m.name().toLowerCase().startsWith(clean.toLowerCase())) {
                return m;
            }
        }
        return null;
    }

    private static Material findRuntimeMaterial(String primary, String fallback) {
        for (Material m : getAllRuntimeMaterials()) {
            if (primary != null && m.name().equalsIgnoreCase(primary)) return m;
        }
        if (fallback != null) {
            for (Material m : getAllRuntimeMaterials()) {
                if (m.name().equalsIgnoreCase(fallback)) return m;
            }
        }
        return null;
    }

    private boolean handleEffect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /effect <player|*> <potion|clear> [seconds] [level]");
            return true;
        }

        String effectArg = args[1].trim();
        if (effectArg.equalsIgnoreCase("clear") || effectArg.equalsIgnoreCase("none") || effectArg.equalsIgnoreCase("remove")) {
            if (args[0].equals("*")) {
                int count = 0;
                for (Player target : Bukkit.getOnlinePlayers()) {
                    for (PotionEffect pe : target.getActivePotionEffects()) target.removePotionEffect(pe.getType());
                    count++;
                }
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aCleared all potion effects for &e" + count + " &aonline players."));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
            for (PotionEffect pe : target.getActivePotionEffects()) target.removePotionEffect(pe.getType());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aCleared all potion effects for &e" + target.getName() + "&a."));
            return true;
        }

        PotionEffectType type = getPotionEffectByName(effectArg);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Potion effect '" + effectArg + "' not found! Press TAB to view available effects.");
            return true;
        }

        int seconds = 60;
        if (args.length >= 3) {
            try { seconds = Math.max(0, Math.min(1000000, Integer.parseInt(args[2]))); } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Duration must be a valid number of seconds!");
                return true;
            }
        }

        int level = 1;
        if (args.length >= 4) {
            try { level = Math.max(1, Math.min(255, Integer.parseInt(args[3]))); } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Level must be between 1 and 255!");
                return true;
            }
        }

        int ticks = type.isInstant() ? 1 : (seconds * 20);
        int amplifier = level - 1;

        if (args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                target.removePotionEffect(type);
                if (seconds > 0) target.addPotionEffect(new PotionEffect(type, ticks, amplifier));
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    (seconds == 0) ? "&aRemoved &e" + type.getName() + " &afrom &e" + count + " &aonline players."
                            : "&aApplied &e" + type.getName() + " " + level + " &a(" + seconds + "s) to &e" + count + " &aonline players."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }

        target.removePotionEffect(type);
        if (seconds == 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRemoved &e" + type.getName() + " &afrom &e" + target.getName() + "&a."));
        } else {
            target.addPotionEffect(new PotionEffect(type, ticks, amplifier));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aApplied &e" + type.getName() + " " + level + " &a(" + seconds + "s) to &e" + target.getName() + "&a."));
        }
        return true;
    }

    public static PotionEffectType getPotionEffectByName(String name) {
        if (name == null) return null;
        String clean = name.toUpperCase().replace("MINECRAFT:", "").trim();
        PotionEffectType direct = PotionEffectType.getByName(clean);
        if (direct != null) return direct;

        switch (clean.toLowerCase().replace("_", "")) {
            case "speed": case "swiftness": return PotionEffectType.SPEED;
            case "slowness": case "slow": return PotionEffectType.SLOW;
            case "haste": case "fastdigging": return PotionEffectType.FAST_DIGGING;
            case "miningfatigue": case "fatigue": return PotionEffectType.SLOW_DIGGING;
            case "strength": return PotionEffectType.INCREASE_DAMAGE;
            case "instanthealth": case "heal": return PotionEffectType.HEAL;
            case "instantdamage": case "harm": return PotionEffectType.HARM;
            case "jump": case "jumpboost": return PotionEffectType.JUMP;
            case "nausea": return PotionEffectType.CONFUSION;
            case "regeneration": case "regen": return PotionEffectType.REGENERATION;
            case "resistance": case "res": return PotionEffectType.DAMAGE_RESISTANCE;
            case "fireresistance": case "fireres": return PotionEffectType.FIRE_RESISTANCE;
            case "waterbreathing": return PotionEffectType.WATER_BREATHING;
            case "invisibility": case "invis": return PotionEffectType.INVISIBILITY;
            case "blindness": return PotionEffectType.BLINDNESS;
            case "nightvision": case "nv": return PotionEffectType.NIGHT_VISION;
            case "hunger": return PotionEffectType.HUNGER;
            case "weakness": return PotionEffectType.WEAKNESS;
            case "poison": return PotionEffectType.POISON;
            case "wither": return PotionEffectType.WITHER;
            case "healthboost": return PotionEffectType.HEALTH_BOOST;
            case "absorption": return PotionEffectType.ABSORPTION;
            case "saturation": return PotionEffectType.SATURATION;
            default: break;
        }

        for (PotionEffectType pet : PotionEffectType.values()) {
            if (pet != null && pet.getName() != null && (pet.getName().equalsIgnoreCase(clean) || pet.getName().replace("_", "").equalsIgnoreCase(clean.replace("_", "")))) {
                return pet;
            }
        }
        return null;
    }

    private boolean handleClearGroundItems(CommandSender sender) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item && !entity.isDead()) {
                    entity.remove();
                    removed++;
                }
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aCleared &e" + removed + " &aground items across all worlds."));
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                clearSinglePlayerInventory(target);
                if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour inventory was cleared by staff."));
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aCleared inventories for &e" + count + " &aonline players."));
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /clear <player|*>");
            return true;
        }

        clearSinglePlayerInventory(target);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aCleared inventory of &e" + target.getName() + "&a."));
        if (!target.equals(sender)) target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour inventory was cleared by staff."));
        return true;
    }

    private void clearSinglePlayerInventory(Player target) {
        target.getInventory().clear();
        target.getInventory().setHelmet(null);
        target.getInventory().setChestplate(null);
        target.getInventory().setLeggings(null);
        target.getInventory().setBoots(null);
        try {
            Method m = target.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class);
            m.invoke(target.getInventory(), (ItemStack) null);
        } catch (Throwable ignored) {}
        target.updateInventory();
    }
}