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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (!(sender instanceof Player) && (cmd.equals("fly") || cmd.equals("speed") || cmd.equals("tphere") || cmd.equals("invsee") || cmd.equals("enchant"))) {
            if (args.length == 0 || cmd.equals("tphere") || cmd.equals("invsee") || cmd.equals("enchant")) {
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
            // Speed 1 = 0.1f (vanilla default fly speed), Speed 10 = 1.0f (max speed)
            float flySpeed = (speedVal == 1) ? 0.1f : (0.1f + (speedVal - 1) * (0.9f / 9.0f));
            target.setFlySpeed(Math.min(1.0f, flySpeed));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&aFly speed set to &e" + speedVal + (speedVal == 1 ? " (Normal)" : "") + "&a for &e" + target.getName() + "&a."));
        } else {
            // Speed 1 = 0.2f (vanilla default walk speed), Speed 10 = 1.0f (max speed)
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
            staff.sendMessage(ChatColor.RED + "Usage: /invsee <player> [armor]");
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

        // View and edit armor
        if (args.length > 1 && args[1].equalsIgnoreCase("armor")) {
            Inventory armorGui = Bukkit.createInventory(null, 9, ARMOR_PREFIX + target.getName());
            ItemStack[] armor = target.getInventory().getArmorContents();
            if (armor != null) {
                if (armor.length > 0) armorGui.setItem(0, armor[0]); // Boots
                if (armor.length > 1) armorGui.setItem(1, armor[1]); // Leggings
                if (armor.length > 2) armorGui.setItem(2, armor[2]); // Chestplate
                if (armor.length > 3) armorGui.setItem(3, armor[3]); // Helmet
            }
            
            // Safe reflection for off-hand on 1.9+ servers
            try {
                Method m = target.getInventory().getClass().getMethod("getItemInOffHand");
                ItemStack offhand = (ItemStack) m.invoke(target.getInventory());
                if (offhand != null) {
                    armorGui.setItem(4, offhand);
                }
            } catch (Throwable ignored) {}

            staff.openInventory(armorGui);
            staff.sendMessage(ChatColor.GREEN + "Opened " + target.getName() + "'s armor. You can take or swap armor pieces.");
            return true;
        }

        // Direct native inventory: drag or shift-click items straight into your inventory with zero item loss
        staff.openInventory(target.getInventory());
        staff.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                "&aOpened &e" + target.getName() + "&a's inventory. You can take items directly to your inventory! &7(Type &e/invsee " + target.getName() + " armor &7to view armor)"));
        return true;
    }

    private boolean handleEnchant(Player staff, String[] args) {
        if (args.length < 2) {
            staff.sendMessage(ChatColor.RED + "Usage: /enchant <enchantment> <level>");
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

        int level;
        try {
            level = Integer.parseInt(args[1]);
            if (level < 0 || level > 32767) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            staff.sendMessage(ChatColor.RED + "Level must be an integer between 0 and 32767!");
            return true;
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
}