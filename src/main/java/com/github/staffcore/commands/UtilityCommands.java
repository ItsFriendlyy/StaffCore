package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import com.github.staffcore.gui.TeleportGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public class UtilityCommands implements CommandExecutor, Listener {

    private static StaffCore pluginInstance;

    // Active toggle sets
    public static final Set<UUID> vanishedPlayers = new HashSet<>();
    public static final Set<UUID> godPlayers = new HashSet<>();
    public static final Set<UUID> flyPlayers = new HashSet<>();

    // Speeds tracking
    public static final Map<UUID, Float> walkSpeeds = new HashMap<>();
    public static final Map<UUID, Float> flySpeeds = new HashMap<>();

    // Invsee Title Prefix
    public static final String INVSEE_PREFIX = ChatColor.DARK_GRAY + "Inv: " + ChatColor.GOLD;

    // Spawn Location
    public static Location spawnLocation = null;

    // Warps storage
    public static final Map<String, Location> warps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public static final Map<String, String> warpIcons = new HashMap<>();
    public static final Map<String, UUID> warpOwners = new HashMap<>();
    public static final Set<String> privateWarps = new HashSet<>();
    public static final Map<String, Long> warpPublicDates = new HashMap<>();

    // Jails storage
    public static final Map<String, Location> jails = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // Homes storage: UUID -> (HomeName -> Location)
    public static final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    public static final Map<UUID, Map<String, String>> playerHomeIcons = new HashMap<>();
    // Alias for compatibility
    public static final Map<UUID, Map<String, String>> homeIcons = playerHomeIcons;

    public UtilityCommands(StaffCore plugin) {
        pluginInstance = plugin;
        loadStates(plugin);
    }

    public static StaffCore getPlugin() {
        return pluginInstance;
    }

    // =========================================================================
    //                             COMMAND DISPATCHER
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String commandName = cmd.getName().toLowerCase();

        switch (commandName) {
            case "v":
            case "vanish":
                return handleVanish(sender, args);

            case "fly":
                return handleFly(sender, args);

            case "god":
            case "invincible":
                return handleGod(sender, args);

            case "heal":
                return handleHeal(sender, args);

            case "feed":
                return handleFeed(sender, args);

            case "speed":
                return handleSpeed(sender, args);

            case "craft":
            case "workbench":
                return handleWorkbench(sender);

            case "anvil":
                return handleAnvil(sender);

            case "ec":
            case "enderchest":
                return handleEnderChest(sender, args);

            case "trash":
            case "disposal":
                return handleTrash(sender);

            case "cleargrounditems":
            case "clearlag":
            case "clearitems":
                return handleClearGroundItems(sender);

            case "give":
                return handleGive(sender, args);

            case "effect":
                return handleEffect(sender, args);

            case "home":
            case "homes":
                return handleHome(sender, args);

            case "sethome":
                return handleSetHome(sender, args);

            case "delhome":
                return handleDelHome(sender, args);

            case "warp":
            case "warps":
                return handleWarp(sender, args);

            case "mywarps":
                return handleMyWarps(sender);

            case "setwarp":
                return handleSetWarp(sender, args);

            case "delwarp":
                return handleDelWarp(sender, args);

            case "spawn":
                return handleSpawn(sender);

            case "setspawn":
                return handleSetSpawn(sender);

            case "staffcore":
                return handleStaffCore(sender, args);

            default:
                return false;
        }
    }

    // =========================================================================
    //                        VANISH WITH FAKE JOIN/LEAVE
    // =========================================================================

    private boolean handleVanish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use vanish.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("staffcore.vanish") && !player.hasPermission("staffcore.v") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use vanish.");
            return true;
        }

        toggleVanish(player, pluginInstance);
        saveStates(pluginInstance);
        return true;
    }

    public static boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public static void toggleVanish(Player player, StaffCore plugin) {
        UUID uuid = player.getUniqueId();
        boolean fakeMessagesEnabled = (plugin != null) && plugin.getConfig().getBoolean("vanish.fake-messages.enabled",
                plugin.getConfig().getBoolean("vanish.fake-messages", true));

        if (vanishedPlayers.contains(uuid)) {
            // UNVANISH: Turning vanish OFF -> Becoming VISIBLE
            vanishedPlayers.remove(uuid);

            for (Player online : Bukkit.getOnlinePlayers()) {
                showPlayerCrossVersion(online, player, plugin);
            }

            player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.RED + "Vanish disabled. You are now visible!");

            if (fakeMessagesEnabled) {
                String joinFormat = plugin.getConfig().getString("vanish.fake-join-message",
                        plugin.getConfig().getString("vanish.fake-messages.join", "&e%player% joined the game"));
                String fakeJoinMsg = ChatColor.translateAlternateColorCodes('&', joinFormat.replace("%player%", player.getName()));
                Bukkit.broadcastMessage(fakeJoinMsg);
            }

            notifyStaff(player, ChatColor.GRAY + "[" + ChatColor.GOLD + "StaffCore" + ChatColor.GRAY + "] "
                    + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " unvanished"
                    + (fakeMessagesEnabled ? " (fake join broadcasted)." : "."));

        } else {
            // VANISH: Turning vanish ON -> Becoming INVISIBLE
            vanishedPlayers.add(uuid);

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("staffcore.vanish.see") && !online.isOp() && !online.equals(player)) {
                    hidePlayerCrossVersion(online, player, plugin);
                }
            }

            player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Vanish enabled. You are now invisible!");

            if (fakeMessagesEnabled) {
                String quitFormat = plugin.getConfig().getString("vanish.fake-quit-message",
                        plugin.getConfig().getString("vanish.fake-messages.quit", "&e%player% left the game"));
                String fakeLeaveMsg = ChatColor.translateAlternateColorCodes('&', quitFormat.replace("%player%", player.getName()));
                Bukkit.broadcastMessage(fakeLeaveMsg);
            }

            notifyStaff(player, ChatColor.GRAY + "[" + ChatColor.GOLD + "StaffCore" + ChatColor.GRAY + "] "
                    + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " vanished"
                    + (fakeMessagesEnabled ? " (fake leave broadcasted)." : "."));
        }
    }

    private static void notifyStaff(Player exclude, String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(exclude)) continue;
            if (online.hasPermission("staffcore.vanish.see") || online.isOp()) {
                online.sendMessage(message);
            }
        }
    }

    public static void hidePlayerCrossVersion(Player observer, Player target, StaffCore plugin) {
        if (observer.equals(target)) return;
        try {
            Method m = Player.class.getMethod("hidePlayer", org.bukkit.plugin.Plugin.class, Player.class);
            m.invoke(observer, plugin, target);
        } catch (Throwable t) {
            try {
                Method m2 = Player.class.getMethod("hidePlayer", Player.class);
                m2.invoke(observer, target);
            } catch (Throwable ignored) {}
        }
    }

    public static void showPlayerCrossVersion(Player observer, Player target, StaffCore plugin) {
        if (observer.equals(target)) return;
        try {
            Method m = Player.class.getMethod("showPlayer", org.bukkit.plugin.Plugin.class, Player.class);
            m.invoke(observer, plugin, target);
        } catch (Throwable t) {
            try {
                Method m2 = Player.class.getMethod("showPlayer", Player.class);
                m2.invoke(observer, target);
            } catch (Throwable ignored) {}
        }
    }

    // =========================================================================
    //                             EVENT LISTENERS
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();

        // 1. Hide existing vanished players from the joining player if they lack permission
        if (!joiningPlayer.hasPermission("staffcore.vanish.see") && !joiningPlayer.isOp()) {
            for (UUID vanishedUuid : vanishedPlayers) {
                Player vanished = Bukkit.getPlayer(vanishedUuid);
                if (vanished != null && vanished.isOnline()) {
                    hidePlayerCrossVersion(joiningPlayer, vanished, pluginInstance);
                }
            }
        }

        // 2. If the joining player is already vanished, silence their join message and hide them
        if (vanishedPlayers.contains(joiningPlayer.getUniqueId())) {
            event.setJoinMessage(null); // Silent join
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("staffcore.vanish.see") && !online.isOp() && !online.equals(joiningPlayer)) {
                    hidePlayerCrossVersion(online, joiningPlayer, pluginInstance);
                }
            }
            joiningPlayer.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.YELLOW + "You logged in while vanished!");
        }

        // Re-apply flight state if saved
        if (flyPlayers.contains(joiningPlayer.getUniqueId())) {
            joiningPlayer.setAllowFlight(true);
            joiningPlayer.setFlying(true);
        }

        // Re-apply walk and fly speeds if saved
        if (walkSpeeds.containsKey(joiningPlayer.getUniqueId())) {
            joiningPlayer.setWalkSpeed(walkSpeeds.get(joiningPlayer.getUniqueId()));
        }
        if (flySpeeds.containsKey(joiningPlayer.getUniqueId())) {
            joiningPlayer.setFlySpeed(flySpeeds.get(joiningPlayer.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        if (vanishedPlayers.contains(quittingPlayer.getUniqueId())) {
            event.setQuitMessage(null); // Silent quit
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player) {
            Player p = (Player) event.getTarget();
            if (vanishedPlayers.contains(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (godPlayers.contains(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    // =========================================================================
    //                        FLY & GODMODE HANDLERS
    // =========================================================================

    public static boolean isFlyEnabled(UUID uuid) {
        return flyPlayers.contains(uuid);
    }

    public static boolean isInvincible(UUID uuid) {
        return godPlayers.contains(uuid);
    }

    private boolean handleFly(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0 && sender.hasPermission("staffcore.fly.others")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
            if (!target.hasPermission("staffcore.fly") && !target.isOp()) {
                target.sendMessage(ChatColor.RED + "No permission for /fly.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Specify a player name from console.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean enable = !flyPlayers.contains(uuid);
        if (enable) {
            flyPlayers.add(uuid);
            target.setAllowFlight(true);
            target.setFlying(true);
            target.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Flight enabled.");
            if (!sender.equals(target)) {
                sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Flight enabled for " + target.getName() + ".");
            }
        } else {
            flyPlayers.remove(uuid);
            target.setFlying(false);
            target.setAllowFlight(false);
            target.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.RED + "Flight disabled.");
            if (!sender.equals(target)) {
                sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.RED + "Flight disabled for " + target.getName() + ".");
            }
        }
        saveStates(pluginInstance);
        return true;
    }

    private boolean handleGod(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0 && sender.hasPermission("staffcore.god.others")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
            if (!target.hasPermission("staffcore.god") && !target.isOp()) {
                target.sendMessage(ChatColor.RED + "No permission for /god.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Specify a player name from console.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean enable = !godPlayers.contains(uuid);
        if (enable) {
            godPlayers.add(uuid);
            target.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Godmode enabled.");
            if (!sender.equals(target)) {
                sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Godmode enabled for " + target.getName() + ".");
            }
        } else {
            godPlayers.remove(uuid);
            target.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.RED + "Godmode disabled.");
            if (!sender.equals(target)) {
                sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.RED + "Godmode disabled for " + target.getName() + ".");
            }
        }
        saveStates(pluginInstance);
        return true;
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : (sender instanceof Player ? (Player) sender : null);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        try {
            Method m = target.getClass().getMethod("resetMaxHealth");
            m.invoke(target);
        } catch (Throwable ignored) {}
        target.setHealth(20.0);
        target.setFoodLevel(20);
        target.setFireTicks(0);
        target.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "You have been healed!");
        if (!sender.equals(target)) {
            sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Healed " + target.getName() + ".");
        }
        return true;
    }

    private boolean handleFeed(CommandSender sender, String[] args) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : (sender instanceof Player ? (Player) sender : null);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Your appetite has been sated!");
        if (!sender.equals(target)) {
            sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Fed " + target.getName() + ".");
        }
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /speed.");
            return true;
        }
        Player p = (Player) sender;
        if (args.length == 0) {
            p.sendMessage(ChatColor.RED + "Usage: /speed <1-10>");
            return true;
        }
        try {
            float val = Float.parseFloat(args[0]);
            val = Math.max(1, Math.min(10, val)) / 10.0f;
            if (p.isFlying()) {
                p.setFlySpeed(val);
                flySpeeds.put(p.getUniqueId(), val);
                p.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Fly speed set to " + args[0] + ".");
            } else {
                p.setWalkSpeed(val);
                walkSpeeds.put(p.getUniqueId(), val);
                p.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Walk speed set to " + args[0] + ".");
            }
        } catch (NumberFormatException e) {
            p.sendMessage(ChatColor.RED + "Please provide a valid number between 1 and 10.");
        }
        return true;
    }

    private boolean handleWorkbench(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.openWorkbench(null, true);
        }
        return true;
    }

    private boolean handleAnvil(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            Inventory anvil = Bukkit.createInventory(p, org.bukkit.event.inventory.InventoryType.ANVIL);
            p.openInventory(anvil);
        }
        return true;
    }

    private boolean handleEnderChest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (args.length > 0 && p.hasPermission("staffcore.enderchest.others")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                p.openInventory(target.getEnderChest());
                return true;
            }
        }
        p.openInventory(p.getEnderChest());
        return true;
    }

    private boolean handleTrash(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            Inventory trash = Bukkit.createInventory(null, 36, ChatColor.DARK_GRAY + "Disposal Bin");
            p.openInventory(trash);
        }
        return true;
    }

    private boolean handleClearGroundItems(CommandSender sender) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    count++;
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Removed " + count + " ground items.");
        return true;
    }

    // =========================================================================
    //                        SPAWN & SETSPAWN
    // =========================================================================

    public static Location getSpawnLocation() {
        if (spawnLocation != null) return spawnLocation;
        World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return (w != null) ? w.getSpawnLocation() : null;
    }

    public static void setSpawnLocation(Location loc) {
        spawnLocation = loc;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        Location spawn = getSpawnLocation();
        if (spawn != null) {
            p.teleport(spawn);
            p.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Teleported to spawn.");
        } else {
            p.sendMessage(ChatColor.RED + "Spawn location is not set.");
        }
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (!p.hasPermission("staffcore.setspawn") && !p.isOp()) {
            p.sendMessage(ChatColor.RED + "No permission to set spawn.");
            return true;
        }
        setSpawnLocation(p.getLocation());
        saveStates(pluginInstance);
        p.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Spawn location set successfully!");
        return true;
    }

    // =========================================================================
    //                        INVSEE HELPER
    // =========================================================================

    public static void syncGuiToTarget(Inventory inv, Player target) {
        if (inv == null || target == null || !target.isOnline()) return;
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < 36 && i < inv.getSize() && i < contents.length; i++) {
            ItemStack item = inv.getItem(i);
            target.getInventory().setItem(i, item);
        }
        target.updateInventory();
    }

    // =========================================================================
    //                        FAST SELF-GIVE & FAST SELF-EFFECT
    // =========================================================================

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /give [player] <item> [amount]");
            return true;
        }

        Player target = null;
        String matName = null;
        int amount = 1;

        Player potentialPlayer = Bukkit.getPlayerExact(args[0]);
        if (potentialPlayer != null && args.length >= 2) {
            target = potentialPlayer;
            matName = args[1];
            if (args.length >= 3) {
                try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
            matName = args[0];
            if (args.length >= 2) {
                try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player name: /give <player> <item> [amount]");
            return true;
        }

        Material mat = matchEasyMaterial(matName);
        if (mat == null) {
            sender.sendMessage(ChatColor.RED + "Unknown item: " + matName);
            return true;
        }

        target.getInventory().addItem(new ItemStack(mat, amount));
        sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Gave " + amount + "x " + mat.name() + " to " + target.getName() + ".");
        return true;
    }

    private boolean handleEffect(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /effect [player] <effect|clear> [seconds] [amplifier]");
            return true;
        }

        Player target = null;
        String effectName = null;
        int duration = 30;
        int amplifier = 0;

        Player potentialPlayer = Bukkit.getPlayerExact(args[0]);
        if (potentialPlayer != null && args.length >= 2) {
            target = potentialPlayer;
            effectName = args[1];
            if (args.length >= 3) {
                try { duration = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
            if (args.length >= 4) {
                try { amplifier = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
            effectName = args[0];
            if (args.length >= 2) {
                try { duration = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
            if (args.length >= 3) {
                try { amplifier = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /effect <player> <effect> ...");
            return true;
        }

        if (effectName.equalsIgnoreCase("clear")) {
            for (PotionEffect pe : target.getActivePotionEffects()) {
                target.removePotionEffect(pe.getType());
            }
            sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Cleared all effects from " + target.getName() + ".");
            return true;
        }

        PotionEffectType type = getPotionEffectByName(effectName);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown potion effect: " + effectName);
            return true;
        }

        target.addPotionEffect(new PotionEffect(type, duration * 20, amplifier));
        sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Applied " + type.getName() + " to " + target.getName() + ".");
        return true;
    }

    // =========================================================================
    //                        HOMES & WARPS EXECUTION
    // =========================================================================

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (args.length == 0) {
            TeleportGui.openHomeGui(pluginInstance, player);
            return true;
        }

        String homeName = args[0];
        Map<String, Location> homes = playerHomes.get(player.getUniqueId());
        if (homes != null && homes.containsKey(homeName)) {
            player.teleport(homes.get(homeName));
            player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Teleported to home " + homeName + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Home '" + homeName + "' not found.");
        }
        return true;
    }

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String homeName = (args.length > 0) ? args[0] : "home";

        UUID uuid = player.getUniqueId();
        Map<String, Location> homes = playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());

        int maxHomes = getMaxHomes(player, pluginInstance);
        if (!homes.containsKey(homeName) && homes.size() >= maxHomes) {
            player.sendMessage(ChatColor.RED + "You have reached your limit of " + maxHomes + " homes.");
            return true;
        }

        homes.put(homeName, player.getLocation());
        saveStates(pluginInstance);
        player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Home '" + homeName + "' set successfully!");
        return true;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /delhome <name>");
            return true;
        }

        String homeName = args[0];
        Map<String, Location> homes = playerHomes.get(player.getUniqueId());
        if (homes != null && homes.remove(homeName) != null) {
            saveStates(pluginInstance);
            player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.YELLOW + "Deleted home '" + homeName + "'.");
        } else {
            player.sendMessage(ChatColor.RED + "Home '" + homeName + "' does not exist.");
        }
        return true;
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (args.length == 0) {
            TeleportGui.openWarpGui(pluginInstance, player, 1);
            return true;
        }

        String warpName = args[0];
        Location loc = warps.get(warpName);
        if (loc != null) {
            if (!canAccessWarp(player, warpName)) {
                player.sendMessage(ChatColor.RED + "This warp is private.");
                return true;
            }

            player.teleport(loc);
            player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Teleported to warp " + warpName + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Warp '" + warpName + "' does not exist.");
        }
        return true;
    }

    private boolean handleMyWarps(CommandSender sender) {
        if (sender instanceof Player) {
            TeleportGui.openMyWarpsGui(pluginInstance, (Player) sender);
        }
        return true;
    }

    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /setwarp <name>");
            return true;
        }

        String warpName = args[0];
        UUID uuid = player.getUniqueId();
        boolean isAdmin = player.hasPermission("staffcore.warps.admin") || player.isOp();

        if (!isAdmin) {
            int max = getMaxWarps(player, pluginInstance);
            Set<String> myWarps = getPlayerWarps(uuid);
            if (!warps.containsKey(warpName) && myWarps.size() >= max) {
                player.sendMessage(ChatColor.RED + "You have reached your limit of " + max + " personal warps.");
                return true;
            }
        }

        warps.put(warpName, player.getLocation());
        warpOwners.put(warpName, uuid);
        warpPublicDates.put(warpName.toLowerCase(), System.currentTimeMillis());
        saveStates(pluginInstance);
        player.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Warp '" + warpName + "' set successfully!");
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /delwarp <name>");
            return true;
        }

        String warpName = args[0];
        if (!warps.containsKey(warpName)) {
            sender.sendMessage(ChatColor.RED + "Warp '" + warpName + "' not found.");
            return true;
        }

        if (sender instanceof Player) {
            Player p = (Player) sender;
            boolean isAdmin = p.hasPermission("staffcore.warps.admin") || p.isOp();
            boolean isOwner = isWarpOwner(warpName, p.getUniqueId());
            if (!isAdmin && !isOwner) {
                p.sendMessage(ChatColor.RED + "You do not own this warp.");
                return true;
            }
        }

        warps.remove(warpName);
        warpOwners.remove(warpName);
        warpIcons.remove(warpName.toLowerCase());
        privateWarps.remove(warpName.toLowerCase());
        warpPublicDates.remove(warpName.toLowerCase());
        saveStates(pluginInstance);
        sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.YELLOW + "Warp '" + warpName + "' deleted.");
        return true;
    }

    private boolean handleStaffCore(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("staffcore.admin") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "No permission to reload StaffCore.");
                return true;
            }
            if (pluginInstance != null) {
                pluginInstance.reloadConfig();
                loadStates(pluginInstance);
            }
            sender.sendMessage(ChatColor.GOLD + "[StaffCore] " + ChatColor.GREEN + "Configuration and states reloaded successfully!");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "StaffCore v1.0.0 by StaffCore Team.");
        sender.sendMessage(ChatColor.YELLOW + "Use /staffcore reload to reload data.");
        return true;
    }

    // =========================================================================
    //                        HELPERS & GETTERS/SETTERS
    // =========================================================================

    public static Set<String> getWarpNames() {
        return Collections.unmodifiableSet(warps.keySet());
    }

    public static Set<String> getPlayerHomeNames(UUID uuid) {
        Map<String, Location> homes = playerHomes.get(uuid);
        return (homes != null) ? Collections.unmodifiableSet(homes.keySet()) : Collections.emptySet();
    }

    public static boolean canAccessWarp(Player player, String warpName) {
        if (warpName == null || !warps.containsKey(warpName)) return false;
        if (player.hasPermission("staffcore.warps.admin") || player.isOp()) return true;
        if (!isWarpPrivate(warpName)) return true;
        return isWarpOwner(warpName, player.getUniqueId());
    }

    public static int getMaxHomes(Player player, StaffCore plugin) {
        if (player.hasPermission("staffcore.homes.unlimited") || player.isOp()) return Integer.MAX_VALUE;
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("staffcore.homes." + i)) return i;
        }
        int def = (plugin != null) ? plugin.getConfig().getInt("homes.default-max", 5) : 5;
        return def;
    }

    public static int getMaxWarps(Player player, StaffCore plugin) {
        if (player.hasPermission("staffcore.warps.unlimited") || player.isOp()) return Integer.MAX_VALUE;
        for (int i = 50; i >= 1; i--) {
            if (player.hasPermission("staffcore.warps." + i)) return i;
        }
        int def = (plugin != null) ? plugin.getConfig().getInt("warps.default-max", 3) : 3;
        return def;
    }

    public static String getHomeIcon(UUID uuid, String homeName) {
        Map<String, String> icons = playerHomeIcons.get(uuid);
        return (icons != null) ? icons.getOrDefault(homeName, "home") : "home";
    }

    public static void setHomeIcon(UUID uuid, String homeName, String iconId) {
        playerHomeIcons.computeIfAbsent(uuid, k -> new HashMap<>()).put(homeName, iconId);
    }

    public static String getWarpIcon(String warpName) {
        return warpIcons.getOrDefault(warpName.toLowerCase(), "star");
    }

    public static void setWarpIcon(String warpName, String iconId) {
        warpIcons.put(warpName.toLowerCase(), iconId);
    }

    public static boolean isWarpPrivate(String warpName) {
        return privateWarps.contains(warpName.toLowerCase());
    }

    public static void setWarpPrivate(String warpName, boolean isPrivate) {
        if (isPrivate) {
            privateWarps.add(warpName.toLowerCase());
        } else {
            privateWarps.remove(warpName.toLowerCase());
            if (!warpPublicDates.containsKey(warpName.toLowerCase())) {
                warpPublicDates.put(warpName.toLowerCase(), System.currentTimeMillis());
            }
        }
    }

    public static boolean isWarpOwner(String warpName, UUID uuid) {
        UUID owner = warpOwners.get(warpName);
        return owner != null && owner.equals(uuid);
    }

    public static long getWarpPublicDate(String warpName) {
        return warpPublicDates.getOrDefault(warpName.toLowerCase(), 0L);
    }

    public static Set<String> getPlayerWarps(UUID uuid) {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, UUID> entry : warpOwners.entrySet()) {
            if (entry.getValue().equals(uuid) && warps.containsKey(entry.getKey())) {
                set.add(entry.getKey());
            }
        }
        return set;
    }

    public static Material matchEasyMaterial(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.matchMaterial(name.toUpperCase());
        }
    }

    public static PotionEffectType getPotionEffectByName(String name) {
        if (name == null || name.isEmpty()) return null;
        return PotionEffectType.getByName(name.toUpperCase());
    }

    // =========================================================================
    //                        PERSISTENCE (states.yml)
    // =========================================================================

    public static void loadStates() {
        loadStates(pluginInstance);
    }

    public static void loadStates(StaffCore plugin) {
        if (plugin == null) plugin = pluginInstance;
        if (plugin == null) return;

        File file = new File(plugin.getDataFolder(), "states.yml");
        if (!file.exists()) return;
        FileConfiguration states = YamlConfiguration.loadConfiguration(file);

        // Load Fly
        flyPlayers.clear();
        for (String id : states.getStringList("fly")) {
            try { flyPlayers.add(UUID.fromString(id)); } catch (Exception ignored) {}
        }

        // Load God
        godPlayers.clear();
        for (String id : states.getStringList("Invincible")) {
            try { godPlayers.add(UUID.fromString(id)); } catch (Exception ignored) {}
        }

        // Load Vanish
        vanishedPlayers.clear();
        for (String id : states.getStringList("vanished")) {
            try { vanishedPlayers.add(UUID.fromString(id)); } catch (Exception ignored) {}
        }

        // Load Spawn
        if (states.isConfigurationSection("spawn")) {
            String wName = states.getString("spawn.world");
            World w = (wName != null) ? Bukkit.getWorld(wName) : null;
            if (w != null) {
                double x = states.getDouble("spawn.x");
                double y = states.getDouble("spawn.y");
                double z = states.getDouble("spawn.z");
                float yaw = (float) states.getDouble("spawn.yaw");
                float pitch = (float) states.getDouble("spawn.pitch");
                spawnLocation = new Location(w, x, y, z, yaw, pitch);
            }
        }

        // Load Warps
        warps.clear();
        warpOwners.clear();
        warpIcons.clear();
        privateWarps.clear();
        warpPublicDates.clear();

        if (states.isConfigurationSection("warps")) {
            ConfigurationSection sec = states.getConfigurationSection("warps");
            for (String key : sec.getKeys(false)) {
                String path = "warps." + key;
                String wName = states.getString(path + ".world");
                World w = (wName != null) ? Bukkit.getWorld(wName) : null;
                if (w != null) {
                    double x = states.getDouble(path + ".x");
                    double y = states.getDouble(path + ".y");
                    double z = states.getDouble(path + ".z");
                    float yaw = (float) states.getDouble(path + ".yaw");
                    float pitch = (float) states.getDouble(path + ".pitch");
                    warps.put(key, new Location(w, x, y, z, yaw, pitch));

                    if (states.getBoolean(path + ".private", false)) {
                        privateWarps.add(key.toLowerCase());
                    }
                    if (states.contains(path + ".owner")) {
                        try { warpOwners.put(key, UUID.fromString(states.getString(path + ".owner"))); } catch (Exception ignored) {}
                    }
                    if (states.contains(path + ".icon")) {
                        warpIcons.put(key.toLowerCase(), states.getString(path + ".icon"));
                    }
                    if (states.contains(path + ".publicDate")) {
                        warpPublicDates.put(key.toLowerCase(), states.getLong(path + ".publicDate"));
                    }
                }
            }
        }

        // Load Warp Icons root mapping
        if (states.isConfigurationSection("warp-icons")) {
            ConfigurationSection iconSec = states.getConfigurationSection("warp-icons");
            for (String key : iconSec.getKeys(false)) {
                warpIcons.put(key.toLowerCase(), iconSec.getString(key));
            }
        }

        // Load Private Warps root list
        for (String pWarp : states.getStringList("private-warps")) {
            privateWarps.add(pWarp.toLowerCase());
        }

        // Load Homes
        playerHomes.clear();
        playerHomeIcons.clear();
        if (states.isConfigurationSection("homes")) {
            ConfigurationSection sec = states.getConfigurationSection("homes");
            for (String uuidStr : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection playerSec = sec.getConfigurationSection(uuidStr);
                    if (playerSec != null) {
                        Map<String, Location> map = new HashMap<>();
                        for (String homeName : playerSec.getKeys(false)) {
                            String path = "homes." + uuidStr + "." + homeName;
                            String wName = states.getString(path + ".world");
                            World w = (wName != null) ? Bukkit.getWorld(wName) : null;
                            if (w != null) {
                                double x = states.getDouble(path + ".x");
                                double y = states.getDouble(path + ".y");
                                double z = states.getDouble(path + ".z");
                                float yaw = (float) states.getDouble(path + ".yaw");
                                float pitch = (float) states.getDouble(path + ".pitch");
                                map.put(homeName, new Location(w, x, y, z, yaw, pitch));

                                if (states.contains(path + ".icon")) {
                                    setHomeIcon(uuid, homeName, states.getString(path + ".icon"));
                                }
                            }
                        }
                        playerHomes.put(uuid, map);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Load Jails
        jails.clear();
        ConfigurationSection jailSec = states.getConfigurationSection("jails");
        if (jailSec == null) jailSec = states.getConfigurationSection("Jails");
        if (jailSec != null) {
            for (String jName : jailSec.getKeys(false)) {
                String path = jailSec.getCurrentPath() + "." + jName;
                String wName = states.getString(path + ".world-name", states.getString(path + ".world"));
                World w = (wName != null) ? Bukkit.getWorld(wName) : null;
                if (w == null && states.contains(path + ".world-uuid")) {
                    try { w = Bukkit.getWorld(UUID.fromString(states.getString(path + ".world-uuid"))); } catch (Exception ignored) {}
                }
                if (w != null) {
                    double x = states.getDouble(path + ".x");
                    double y = states.getDouble(path + ".y");
                    double z = states.getDouble(path + ".z");
                    float yaw = (float) states.getDouble(path + ".yaw");
                    float pitch = (float) states.getDouble(path + ".pitch");
                    jails.put(jName, new Location(w, x, y, z, yaw, pitch));
                }
            }
        }
    }

    public static void saveStates() {
        saveStates(pluginInstance);
    }

    public static void saveStates(StaffCore plugin) {
        if (plugin == null) plugin = pluginInstance;
        if (plugin == null) return;

        File file = new File(plugin.getDataFolder(), "states.yml");
        FileConfiguration states = YamlConfiguration.loadConfiguration(file);

        // Save Fly
        List<String> flyList = new ArrayList<>();
        for (UUID u : flyPlayers) flyList.add(u.toString());
        states.set("fly", flyList);

        // Save God
        List<String> godList = new ArrayList<>();
        for (UUID u : godPlayers) godList.add(u.toString());
        states.set("Invincible", godList);

        // Save Vanish
        List<String> vanishList = new ArrayList<>();
        for (UUID u : vanishedPlayers) vanishList.add(u.toString());
        states.set("vanished", vanishList);

        // Save Spawn
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            states.set("spawn.world", spawnLocation.getWorld().getName());
            states.set("spawn.x", spawnLocation.getX());
            states.set("spawn.y", spawnLocation.getY());
            states.set("spawn.z", spawnLocation.getZ());
            states.set("spawn.yaw", spawnLocation.getYaw());
            states.set("spawn.pitch", spawnLocation.getPitch());
        }

        // Save Warps
        for (Map.Entry<String, Location> entry : warps.entrySet()) {
            String path = "warps." + entry.getKey();
            Location loc = entry.getValue();
            if (loc.getWorld() != null) {
                states.set(path + ".world", loc.getWorld().getName());
                states.set(path + ".x", loc.getX());
                states.set(path + ".y", loc.getY());
                states.set(path + ".z", loc.getZ());
                states.set(path + ".yaw", loc.getYaw());
                states.set(path + ".pitch", loc.getPitch());
                states.set(path + ".private", isWarpPrivate(entry.getKey()));
                if (warpOwners.containsKey(entry.getKey())) {
                    states.set(path + ".owner", warpOwners.get(entry.getKey()).toString());
                }
                if (warpIcons.containsKey(entry.getKey().toLowerCase())) {
                    states.set(path + ".icon", warpIcons.get(entry.getKey().toLowerCase()));
                }
                if (warpPublicDates.containsKey(entry.getKey().toLowerCase())) {
                    states.set(path + ".publicDate", warpPublicDates.get(entry.getKey().toLowerCase()));
                }
            }
        }

        // Save Homes
        for (Map.Entry<UUID, Map<String, Location>> pEntry : playerHomes.entrySet()) {
            String uuidStr = pEntry.getKey().toString();
            for (Map.Entry<String, Location> hEntry : pEntry.getValue().entrySet()) {
                String path = "homes." + uuidStr + "." + hEntry.getKey();
                Location loc = hEntry.getValue();
                if (loc.getWorld() != null) {
                    states.set(path + ".world", loc.getWorld().getName());
                    states.set(path + ".x", loc.getX());
                    states.set(path + ".y", loc.getY());
                    states.set(path + ".z", loc.getZ());
                    states.set(path + ".yaw", loc.getYaw());
                    states.set(path + ".pitch", loc.getPitch());
                    String icon = getHomeIcon(pEntry.getKey(), hEntry.getKey());
                    states.set(path + ".icon", icon);
                }
            }
        }

        try {
            states.save(file);
        } catch (IOException e) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to save states.yml: " + e.getMessage());
            }
        }
    }
}