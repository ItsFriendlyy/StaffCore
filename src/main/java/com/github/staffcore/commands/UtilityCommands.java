package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
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

    public static final String INVSEE_PREFIX = ChatColor.DARK_GRAY + "Invsee: " + ChatColor.DARK_RED;
    public static final String ARMOR_PREFIX = ChatColor.DARK_GRAY + "Armor: " + ChatColor.DARK_RED;

    private static Set<String> cachedGiveableItemNames = null;

    public UtilityCommands(StaffCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isInvincible(UUID uuid) {
        return invinciblePlayers.contains(uuid);
    }

    public static void setInvincible(UUID uuid, boolean state) {
        if (state) {
            invinciblePlayers.add(uuid);
        } else {
            invinciblePlayers.remove(uuid);
        }
    }

    public static boolean isFlyEnabled(UUID uuid) {
        return flyPlayers.contains(uuid);
    }

    public static void setFlyEnabled(UUID uuid, boolean state) {
        if (state) {
            flyPlayers.add(uuid);
        } else {
            flyPlayers.remove(uuid);
        }
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

        for (String s : data.getStringList("invincible")) {
            try {
                invinciblePlayers.add(UUID.fromString(s));
            } catch (Exception ignored) {}
        }

        for (String s : data.getStringList("fly")) {
            try {
                flyPlayers.add(UUID.fromString(s));
            } catch (Exception ignored) {}
        }

        ConfigurationSection walkSec = data.getConfigurationSection("walk-speed");
        if (walkSec != null) {
            for (String key : walkSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    float speed = (float) walkSec.getDouble(key, 0.2);
                    walkSpeeds.put(uuid, speed);
                } catch (Exception ignored) {}
            }
        }

        ConfigurationSection flySec = data.getConfigurationSection("fly-speed");
        if (flySec != null) {
            for (String key : flySec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    float speed = (float) flySec.getDouble(key, 0.1);
                    flySpeeds.put(uuid, speed);
                } catch (Exception ignored) {}
            }
        }
    }

    public static void saveStates(StaffCore plugin) {
        if (plugin == null) return;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File file = new File(plugin.getDataFolder(), "states.yml");
        FileConfiguration data = new YamlConfiguration();

        List<String> invList = new ArrayList<>();
        for (UUID uuid : invinciblePlayers) {
            invList.add(uuid.toString());
        }
        data.set("invincible", invList);

        List<String> flyList = new ArrayList<>();
        for (UUID uuid : flyPlayers) {
            flyList.add(uuid.toString());
        }
        data.set("fly", flyList);

        for (Map.Entry<UUID, Float> entry : walkSpeeds.entrySet()) {
            data.set("walk-speed." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<UUID, Float> entry : flySpeeds.entrySet()) {
            data.set("fly-speed." + entry.getKey().toString(), entry.getValue());
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save states.yml: " + e.getMessage());
        }
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

        // Row 0: Equipment (Slots 0 to 8)
        gui.setItem(0, target.getInventory().getHelmet());
        gui.setItem(1, target.getInventory().getChestplate());
        gui.setItem(2, target.getInventory().getLeggings());
        gui.setItem(3, target.getInventory().getBoots());

        // Slot 4: Spacer between Armor and Off-Hand
        gui.setItem(4, pane);

        // Slot 5: Off-Hand item (Reflection for 1.9+ servers)
        try {
            Method m = target.getInventory().getClass().getMethod("getItemInOffHand");
            ItemStack offhand = (ItemStack) m.invoke(target.getInventory());
            if (offhand != null) {
                gui.setItem(5, offhand);
            }
        } catch (Throwable ignored) {}

        // Slots 6, 7, 8: Gray glass panes
        gui.setItem(6, pane);
        gui.setItem(7, pane);
        gui.setItem(8, pane);

        // Row 1: Divider row (Slots 9 to 17)
        for (int i = 9; i <= 17; i++) {
            gui.setItem(i, pane);
        }

        // Rows 2, 3, 4: Main Inventory (Slots 18 to 44 -> target slots 9 to 35)
        for (int i = 9; i <= 35; i++) {
            gui.setItem(18 + (i - 9), target.getInventory().getItem(i));
        }

        // Row 5: Hotbar (Slots 45 to 53 -> target slots 0 to 8)
        for (int i = 0; i <= 8; i++) {
            gui.setItem(45 + i, target.getInventory().getItem(i));
        }

        staff.openInventory(gui);
        staff.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aOpened inventory of &e" + target.getName() + "&a."));
    }

    public static void syncGuiToTarget(Inventory top, Player target) {
        if (top == null || target == null || !target.isOnline()) return;

        // 1. Armor (Slots 0 to 3)
        target.getInventory().setHelmet(top.getItem(0));
        target.getInventory().setChestplate(top.getItem(1));
        target.getInventory().setLeggings(top.getItem(2));
        target.getInventory().setBoots(top.getItem(3));

        // 2. Off-hand (Slot 5) via reflection for 1.9+
        try {
            Method m = target.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class);
            m.invoke(target.getInventory(), top.getItem(5));
        } catch (Throwable ignored) {}

        // 3. Main inventory (Slots 18-44 -> target slots 9-35)
        for (int i = 9; i <= 35; i++) {
            target.getInventory().setItem(i, top.getItem(18 + (i - 9)));
        }

        // 4. Hotbar (Slots 45-53 -> target slots 0-8)
        for (int i = 0; i <= 8; i++) {
            target.getInventory().setItem(i, top.getItem(45 + i));
        }

        target.updateInventory();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (!(sender instanceof Player) && (cmd.equals("fly") || cmd.equals("speed") || cmd.equals("tphere") || cmd.equals("invsee") || cmd.equals("enchant") || cmd.equals("repair") || cmd.equals("clear"))) {
            if (args.length == 0 || cmd.equals("tphere") || cmd.equals("invsee") || cmd.equals("enchant") || cmd.equals("repair")) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player in-game!");
                return true;
            }
        }

        switch (cmd) {
            case "gmc":
                return handleGamemode(sender, args, GameMode.CREATIVE, "Creative");
            case "gms":
                return handleGamemode(sender, args, GameMode.SURVIVAL, "Survival");
            case "gmsp":
                return handleGamemode(sender, args, GameMode.SPECTATOR, "Spectator");
            case "gma":
                return handleGamemode(sender, args, GameMode.ADVENTURE, "Adventure");
            case "fly":
                return handleFly(sender, args);
            case "speed":
                return handleSpeed(sender, args);
            case "invincible":
                return handleInvincible(sender, args);
            case "tphere":
                return handleTpHere((Player) sender, args);
            case "tpall":
                return handleTpAll(sender, args);
            case "invsee":
                return handleInvsee((Player) sender, args);
            case "enchant":
                return handleEnchant((Player) sender, args);
            case "sendserver":
                return handleSendServer(sender, args);
            case "heal":
                return handleHeal(sender, args);
            case "feed":
                return handleFeed(sender, args);
            case "repair":
                return handleRepair((Player) sender, args);
            case "struck":
            case "strike":
                return handleStruck(sender, args);
            case "sudo":
                return handleSudo(sender, args);
            case "give":
                return handleGive(sender, args);
            case "effect":
                return handleEffect(sender, args);
            case "cleargrounditems":
            case "cleargrouditems":
            case "clearitems":
            case "clearlag":
                return handleClearGroundItems(sender);
            case "clear":
            case "ci":
            case "clearinventory":
                return handleClear(sender, args);
            default:
                return false;
        }
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
                    if (!p.equals(sender)) {
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour game mode was set to &e" + modeName + "&a by staff."));
                    }
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
            if (!target.equals(sender)) {
                target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour game mode was set to &e" + modeName + "&a by staff."));
            }
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
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour flight mode was " + status + " &aby staff."));
                }
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
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour flight mode was " + status + " &aby staff."));
        }
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
            sender.sendMessage(ChatColor.RED + "Speed must be a number between 1 and 10! (1 is normal, 10 is max)");
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
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aSpeed set to &e" + speedVal + (speedVal == 1 ? " (Normal)" : "") + "&a for &e" + count + " &aonline players."));
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
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aFly speed set to &e" + speedVal + (speedVal == 1 ? " (Normal)" : "") + "&a for &e" + target.getName() + "&a."));
        } else {
            target.setWalkSpeed(walkSpeed);
            walkSpeeds.put(target.getUniqueId(), walkSpeed);
            saveStates(plugin);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aWalk speed set to &e" + speedVal + (speedVal == 1 ? " (Normal)" : "") + "&a for &e" + target.getName() + "&a."));
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
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour invincibility was " + status + " &aby staff."));
                }
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
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour invincibility was " + status + " &aby staff."));
        }
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
                staff.sendMessage(ChatColor.RED + "Level must be an integer between 0 and 255! (Max 255)");
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
            case "bane_of_arthropods":
            case "baneofarthropods":
            case "boa": return Enchantment.getByName("DAMAGE_ARTHROPODS");
            case "knockback": return Enchantment.getByName("KNOCKBACK");
            case "fire_aspect":
            case "fireaspect": return Enchantment.getByName("FIRE_ASPECT");
            case "looting": return Enchantment.getByName("LOOT_BONUS_MOBS");
            case "efficiency": return Enchantment.getByName("DIG_SPEED");
            case "silk_touch":
            case "silktouch": return Enchantment.getByName("SILK_TOUCH");
            case "unbreaking": return Enchantment.getByName("DURABILITY");
            case "fortune": return Enchantment.getByName("LOOT_BONUS_BLOCKS");
            case "power": return Enchantment.getByName("ARROW_DAMAGE");
            case "punch": return Enchantment.getByName("ARROW_KNOCKBACK");
            case "flame": return Enchantment.getByName("ARROW_FIRE");
            case "infinity": return Enchantment.getByName("ARROW_INFINITE");
            case "protection":
            case "prot": return Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            case "fire_protection":
            case "fireprotection":
            case "fireprot": return Enchantment.getByName("PROTECTION_FIRE");
            case "feather_falling":
            case "featherfalling": return Enchantment.getByName("PROTECTION_FALL");
            case "blast_protection":
            case "blastprotection": return Enchantment.getByName("PROTECTION_EXPLOSIONS");
            case "projectile_protection":
            case "projprot": return Enchantment.getByName("PROTECTION_PROJECTILE");
            case "respiration": return Enchantment.getByName("OXYGEN");
            case "aqua_affinity":
            case "aquaaffinity": return Enchantment.getByName("WATER_WORKER");
            case "thorns": return Enchantment.getByName("THORNS");
            case "depth_strider":
            case "depthstrider": return Enchantment.getByName("DEPTH_STRIDER");
            case "luck_of_the_sea":
            case "luckofthesea": return Enchantment.getByName("LUCK");
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
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou have been fully healed by staff."));
                }
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
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou have been fully healed by staff."));
        }
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
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour appetite has been fully sated by staff."));
                }
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
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour appetite has been fully sated by staff."));
        }
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
                    String chatMessage = input.substring(2).trim();
                    target.chat(chatMessage);
                } else {
                    String commandToRun = input.startsWith("/") ? input.substring(1) : input;
                    target.performCommand(commandToRun);
                }
                count++;
            }
            if (input.toLowerCase().startsWith("m:")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aForced &e" + count + " &aplayers to chat: &f" + input.substring(2).trim()));
            } else {
                String commandToRun = input.startsWith("/") ? input.substring(1) : input;
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aForced &e" + count + " &aplayers to execute: &f/" + commandToRun));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found or is offline!");
            return true;
        }

        if (input.toLowerCase().startsWith("m:")) {
            String chatMessage = input.substring(2).trim();
            target.chat(chatMessage);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aForced &e" + target.getName() + " &ato chat: &f" + chatMessage));
        } else {
            String commandToRun = input.startsWith("/") ? input.substring(1) : input;
            target.performCommand(commandToRun);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aForced &e" + target.getName() + " &ato execute: &f/" + commandToRun));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /give <player|*> <item> [amount]");
            return true;
        }

        Material mat = matchEasyMaterial(args[1]);
        if (mat == null || getRawEnumName(mat).equals("AIR")) {
            sender.sendMessage(ChatColor.RED + "Item '" + args[1] + "' not found! Press TAB to autocomplete item names.");
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > 2304) amount = 2304;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount '" + args[2] + "'! Must be a positive number.");
                return true;
            }
        }

        String itemName = getRawEnumName(mat).toLowerCase().replace("legacy_", "").replace("_", " ");

        if (args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                ItemStack stack = new ItemStack(mat, amount);
                target.getInventory().addItem(stack);
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&aYou received &e" + amount + "x " + itemName + " &afrom staff."));
                }
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aGave &e" + amount + "x " + itemName + " &ato &e" + count + " &aonline players."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }

        ItemStack stack = new ItemStack(mat, amount);
        target.getInventory().addItem(stack);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aGave &e" + amount + "x " + itemName + " &ato &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aYou received &e" + amount + "x " + itemName + " &afrom staff."));
        }
        return true;
    }

    /**
     * Reads the true enum name via java.lang.Enum#name so Spigot's Commodore bytecode remapper
     * cannot intercept Material#name.
     */
    public static String getRawEnumName(Material m) {
        if (m == null) return "";
        return ((Enum<?>) m).name();
    }

    /**
     * Bypasses Spigot's Commodore bytecode filter by loading org.bukkit.Material dynamically
     * via Class.forName and returning all runtime enum constants.
     */
    public static Material[] getAllRuntimeMaterials() {
        try {
            Class<?> matClass = Class.forName("org.bukkit." + new String("Material"));
            Object[] constants = matClass.getEnumConstants();
            if (constants instanceof Material[] && constants.length > 0) {
                return (Material[]) constants;
            }
        } catch (Throwable ignored) {}
        return Material.values();
    }

    /**
     * Builds and caches the complete set of giveable item names on the server (1.8.8 - 1.21+).
     */
    public static Set<String> getAllGiveableItemNames() {
        if (cachedGiveableItemNames != null && !cachedGiveableItemNames.isEmpty()) {
            return cachedGiveableItemNames;
        }

        Set<String> items = new TreeSet<>();
        Method isItemMethod = null;
        try {
            Class<?> matClass = Class.forName("org.bukkit." + new String("Material"));
            isItemMethod = matClass.getMethod("isItem");
        } catch (Throwable ignored) {}

        for (Material m : getAllRuntimeMaterials()) {
            if (m == null) continue;
            String rawName = getRawEnumName(m);
            if (rawName.startsWith("LEGACY_") || rawName.equals("AIR") || rawName.endsWith("_AIR")) {
                continue;
            }
            if (isItemMethod != null) {
                try {
                    Boolean giveable = (Boolean) isItemMethod.invoke(m);
                    if (giveable != null && !giveable) {
                        continue;
                    }
                } catch (Throwable ignored) {}
            }
            items.add(rawName.toLowerCase());
        }

        // Ensure common shorthand aliases are also suggested
        items.addAll(Arrays.asList(
            "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_hoe",
            "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
            "netherite_ingot", "netherite_scrap", "netherite_block", "netherite_upgrade_smithing_template",
            "ancient_debris", "trident", "crossbow", "elytra", "shulker_box", "mace", "wind_charge", "heavy_core",
            "golden_apple", "enchanted_golden_apple", "totem_of_undying", "ender_pearl", "experience_bottle", "command_block", "steak"
        ));

        cachedGiveableItemNames = Collections.unmodifiableSet(items);
        return cachedGiveableItemNames;
    }

    public static Material matchEasyMaterial(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String clean = input.trim().toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");
        if (clean.contains("NETHERRITE")) {
            clean = clean.replace("NETHERRITE", "NETHERITE");
        }

        // 1. Scan actual runtime enum constants first using Enum#name (bypasses Commodore legacy filter)
        Material[] allMaterials = getAllRuntimeMaterials();
        for (Material m : allMaterials) {
            String rawName = getRawEnumName(m);
            if (rawName.startsWith("LEGACY_")) continue;
            if (rawName.equalsIgnoreCase(clean)) {
                return m;
            }
        }

        // 2. Try Spigot 1.13+ non-legacy matchMaterial(name, false) via reflection
        try {
            Class<?> matClass = Class.forName("org.bukkit." + new String("Material"));
            Method matchModern = matClass.getMethod("matchMaterial", String.class, boolean.class);
            Material m = (Material) matchModern.invoke(null, clean, false);
            if (m != null) return m;
        } catch (Throwable ignored) {}

        // 3. Common shorthand aliases (including Netherite shorthands)
        String compact = clean.toLowerCase().replace("_", "");
        switch (compact) {
            case "gap":
            case "gapple":
            case "goldenapple":
                return Material.GOLDEN_APPLE;
            case "egap":
            case "notchapple":
            case "enchantedgoldenapple":
                return findRuntimeMaterial("ENCHANTED_GOLDEN_APPLE", "GOLDEN_APPLE");
            case "totem":
            case "totemofundying":
                return findRuntimeMaterial("TOTEM_OF_UNDYING", "TOTEM");
            case "pearl":
            case "enderpearl":
                return Material.ENDER_PEARL;
            case "xpbottle":
            case "expbottle":
            case "experiencebottle":
                return findRuntimeMaterial("EXPERIENCE_BOTTLE", "EXP_BOTTLE");
            case "commandblock":
            case "cmdblock":
                return findRuntimeMaterial("COMMAND_BLOCK", "COMMAND");
            case "steak":
                return Material.COOKED_BEEF;
            case "netheritesword":
                return findRuntimeMaterial("NETHERITE_SWORD", null);
            case "netheritepick":
            case "netheritepickaxe":
                return findRuntimeMaterial("NETHERITE_PICKAXE", null);
            case "netheriteaxe":
                return findRuntimeMaterial("NETHERITE_AXE", null);
            case "netheriteshovel":
                return findRuntimeMaterial("NETHERITE_SHOVEL", null);
            case "netheritehoe":
                return findRuntimeMaterial("NETHERITE_HOE", null);
            case "netheritehelmet":
                return findRuntimeMaterial("NETHERITE_HELMET", null);
            case "netheritechestplate":
            case "netheritechest":
                return findRuntimeMaterial("NETHERITE_CHESTPLATE", null);
            case "netheriteleggings":
            case "netheritelegs":
                return findRuntimeMaterial("NETHERITE_LEGGINGS", null);
            case "netheriteboots":
                return findRuntimeMaterial("NETHERITE_BOOTS", null);
            case "netheriteingot":
                return findRuntimeMaterial("NETHERITE_INGOT", null);
            case "netheritescrap":
                return findRuntimeMaterial("NETHERITE_SCRAP", null);
            case "netheriteblock":
                return findRuntimeMaterial("NETHERITE_BLOCK", null);
            case "ancientdebris":
            case "debris":
                return findRuntimeMaterial("ANCIENT_DEBRIS", null);
            default:
                break;
        }

        // 4. Fuzzy match against all non-legacy runtime enum constants
        for (Material m : allMaterials) {
            String rawName = getRawEnumName(m);
            if (rawName.startsWith("LEGACY_")) continue;
            if (rawName.replace("_", "").equalsIgnoreCase(compact)) {
                return m;
            }
        }
        for (Material m : allMaterials) {
            String rawName = getRawEnumName(m);
            if (rawName.startsWith("LEGACY_")) continue;
            if (rawName.toLowerCase().startsWith(clean.toLowerCase())) {
                return m;
            }
        }

        // 5. Fallback to legacy matchMaterial
        return Material.matchMaterial(clean);
    }

    private static Material findRuntimeMaterial(String primary, String fallback) {
        for (Material m : getAllRuntimeMaterials()) {
            String rawName = getRawEnumName(m);
            if (primary != null && rawName.equalsIgnoreCase(primary)) {
                return m;
            }
        }
        if (fallback != null) {
            for (Material m : getAllRuntimeMaterials()) {
                String rawName = getRawEnumName(m);
                if (rawName.equalsIgnoreCase(fallback)) {
                    return m;
                }
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
                    for (PotionEffect pe : target.getActivePotionEffects()) {
                        target.removePotionEffect(pe.getType());
                    }
                    count++;
                }
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aCleared all potion effects for &e" + count + " &aonline players."));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
            for (PotionEffect pe : target.getActivePotionEffects()) {
                target.removePotionEffect(pe.getType());
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aCleared all potion effects for &e" + target.getName() + "&a."));
            return true;
        }

        PotionEffectType type = getPotionEffectByName(effectArg);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Potion effect '" + effectArg + "' not found! Press TAB to view available effects.");
            return true;
        }

        int seconds = 60;
        if (args.length >= 3) {
            try {
                seconds = Integer.parseInt(args[2]);
                if (seconds < 0) seconds = 0;
                if (seconds > 1000000) seconds = 1000000;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Duration must be a valid number of seconds!");
                return true;
            }
        }

        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
                if (level < 1) level = 1;
                if (level > 255) level = 255;
            } catch (NumberFormatException e) {
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
                if (seconds > 0) {
                    target.addPotionEffect(new PotionEffect(type, ticks, amplifier));
                }
                count++;
            }
            if (seconds == 0) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aRemoved &e" + type.getName() + " &afrom &e" + count + " &aonline players."));
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aApplied &e" + type.getName() + " " + level + " &a(" + seconds + "s) to &e" + count + " &aonline players."));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }

        target.removePotionEffect(type);
        if (seconds == 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aRemoved &e" + type.getName() + " &afrom &e" + target.getName() + "&a."));
        } else {
            target.addPotionEffect(new PotionEffect(type, ticks, amplifier));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aApplied &e" + type.getName() + " " + level + " &a(" + seconds + "s) to &e" + target.getName() + "&a."));
        }
        return true;
    }

    public static PotionEffectType getPotionEffectByName(String name) {
        if (name == null) return null;
        String clean = name.toUpperCase().replace("MINECRAFT:", "").trim();

        PotionEffectType direct = PotionEffectType.getByName(clean);
        if (direct != null) return direct;

        switch (clean.toLowerCase().replace("_", "")) {
            case "speed":
            case "swiftness": return PotionEffectType.SPEED;
            case "slowness":
            case "slow": return PotionEffectType.SLOW;
            case "haste":
            case "fastdigging": return PotionEffectType.FAST_DIGGING;
            case "miningfatigue":
            case "fatigue":
            case "slowdigging": return PotionEffectType.SLOW_DIGGING;
            case "strength":
            case "increasedamage": return PotionEffectType.INCREASE_DAMAGE;
            case "instanthealth":
            case "heal":
            case "health": return PotionEffectType.HEAL;
            case "instantdamage":
            case "harm":
            case "damage": return PotionEffectType.HARM;
            case "jump":
            case "jumpboost": return PotionEffectType.JUMP;
            case "nausea":
            case "confusion": return PotionEffectType.CONFUSION;
            case "regeneration":
            case "regen": return PotionEffectType.REGENERATION;
            case "resistance":
            case "res":
            case "damageresistance": return PotionEffectType.DAMAGE_RESISTANCE;
            case "fireresistance":
            case "fireres": return PotionEffectType.FIRE_RESISTANCE;
            case "waterbreathing": return PotionEffectType.WATER_BREATHING;
            case "invisibility":
            case "invis": return PotionEffectType.INVISIBILITY;
            case "blindness":
            case "blind": return PotionEffectType.BLINDNESS;
            case "nightvision":
            case "nv": return PotionEffectType.NIGHT_VISION;
            case "hunger": return PotionEffectType.HUNGER;
            case "weakness":
            case "weak": return PotionEffectType.WEAKNESS;
            case "poison": return PotionEffectType.POISON;
            case "wither": return PotionEffectType.WITHER;
            case "healthboost": return PotionEffectType.HEALTH_BOOST;
            case "absorption":
            case "absorb": return PotionEffectType.ABSORPTION;
            case "saturation": return PotionEffectType.SATURATION;
            default: break;
        }

        for (PotionEffectType pet : PotionEffectType.values()) {
            if (pet != null && pet.getName() != null) {
                if (pet.getName().equalsIgnoreCase(clean) || pet.getName().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                    return pet;
                }
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
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aCleared &e" + removed + " &aground items across all worlds."));
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                clearSinglePlayerInventory(target);
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour inventory was cleared by staff."));
                }
                count++;
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aCleared inventories for &e" + count + " &aonline players."));
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
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aCleared inventory of &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aYour inventory was cleared by staff."));
        }
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