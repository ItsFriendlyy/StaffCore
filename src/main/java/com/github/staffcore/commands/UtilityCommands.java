package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UtilityCommands implements CommandExecutor {

    private final StaffCore plugin;
    public static final Set<UUID> invinciblePlayers = new HashSet<>();
    public static final String INVSEE_PREFIX = ChatColor.DARK_GRAY + "Invsee: " + ChatColor.DARK_RED;
    public static final String ARMOR_PREFIX = ChatColor.DARK_GRAY + "Armor: " + ChatColor.DARK_RED;

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
        // Armor (Slots 0 to 3: Helmet, Chestplate, Leggings, Boots)
        gui.setItem(0, target.getInventory().getHelmet());
        gui.setItem(1, target.getInventory().getChestplate());
        gui.setItem(2, target.getInventory().getLeggings());
        gui.setItem(3, target.getInventory().getBoots());

        // Slot 4: One slot/item frame spacer between Armor and Off-Hand
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

        if (!(sender instanceof Player) && (cmd.equals("fly") || cmd.equals("speed") || cmd.equals("tphere") || cmd.equals("invsee") || cmd.equals("enchant") || cmd.equals("repair"))) {
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
            default:
                return false;
        }
    }

    private boolean handleGamemode(CommandSender sender, String[] args, GameMode mode, String modeName) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("staffcore.command.gamemode.others") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to change other players' game modes!");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /" + modeName.toLowerCase() + " <player>");
            return true;
        }

        target.setGameMode(mode);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aSet game mode to &e" + modeName + "&a for &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour game mode was set to &e" + modeName + "&a by staff."));
        }
        return true;
    }

    private boolean handleFly(CommandSender sender, String[] args) {
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
            sender.sendMessage(ChatColor.RED + "Usage: /fly <player>");
            return true;
        }

        boolean state = !target.getAllowFlight();
        target.setAllowFlight(state);
        if (!state) target.setFlying(false);

        String status = state ? "&2enabled" : "&4disabled";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFlight mode " + status + " &afor &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour flight mode was " + status + " &aby staff."));
        }
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /speed <1-10> [player]");
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
            sender.sendMessage(ChatColor.RED + "Usage: /speed <1-10> <player>");
            return true;
        }

        if (target.isFlying()) {
            float flySpeed = (speedVal == 1) ? 0.1f : (0.1f + (speedVal - 1) * (0.9f / 9.0f));
            target.setFlySpeed(Math.min(1.0f, flySpeed));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&aFly speed set to &e" + speedVal + (speedVal == 1 ? " (Normal)" : "") + "&a for &e" + target.getName() + "&a."));
        } else {
            float walkSpeed = (speedVal == 1) ? 0.2f : (0.2f + (speedVal - 1) * (0.8f / 9.0f));
            target.setWalkSpeed(Math.min(1.0f, walkSpeed));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&aWalk speed set to &e" + speedVal + (speedVal == 1 ? " (Normal)" : "") + "&a for &e" + target.getName() + "&a."));
        }
        return true;
    }

    private boolean handleInvincible(CommandSender sender, String[] args) {
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
            sender.sendMessage(ChatColor.RED + "Usage: /invincible <player>");
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean nowInvincible = !isInvincible(uuid);
        setInvincible(uuid, nowInvincible);

        String status = nowInvincible ? "&2enabled" : "&4disabled";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aInvincibility " + status + " &afor &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYour invincibility was " + status + " &aby staff."));
        }
        return true;
    }

    private boolean handleTpHere(Player staff, String[] args) {
        if (args.length < 1) {
            staff.sendMessage(ChatColor.RED + "Usage: /tphere <player>");
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

        // 1. Direct Bukkit match
        Enchantment direct = Enchantment.getByName(clean);
        if (direct != null) return direct;

        // 2. Check friendly aliases across 1.8.8 through 1.20+
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

        // 3. Fuzzy search in all enchantments
        for (Enchantment e : Enchantment.values()) {
            if (e.getName().equalsIgnoreCase(clean) || e.getName().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                return e;
            }
        }
        return null;
    }

    private boolean handleSendServer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /sendserver <player> <server>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found!");
            return true;
        }

        String serverName = args[1];
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
            sender.sendMessage(ChatColor.RED + "Usage from console: /heal <player>");
            return true;
        }

        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setExhaustion(0.0f);
        target.setFireTicks(0);
        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFully healed &e" + target.getName() + "&a."));
        if (!target.equals(sender)) {
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou have been fully healed by staff."));
        }
        return true;
    }

    private boolean handleFeed(CommandSender sender, String[] args) {
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
            sender.sendMessage(ChatColor.RED + "Usage from console: /feed <player>");
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
        Location loc = null;
        String targetDesc = "target location";

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
            sender.sendMessage(ChatColor.RED + "Usage from console: /struck <player>");
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
            sender.sendMessage(ChatColor.RED + "Usage: /sudo <player> <command | m:message>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + args[0] + " not found or is offline!");
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) sb.append(" ");
            sb.append(args[i]);
        }
        String input = sb.toString().trim();

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
}