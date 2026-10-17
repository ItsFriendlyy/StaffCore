package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
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

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UtilityCommands implements CommandExecutor {

    private final StaffCore plugin;
    public static final String INVSEE_PREFIX = ChatColor.DARK_GRAY + "Inv: " + ChatColor.RED;
    private static final Set<UUID> invinciblePlayers = ConcurrentHashMap.newKeySet();

    public UtilityCommands(StaffCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isInvincible(UUID uuid) {
        return invinciblePlayers.contains(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "gmc":
                return handleGamemode(sender, args, GameMode.CREATIVE);
            case "gms":
                return handleGamemode(sender, args, GameMode.SURVIVAL);
            case "gma":
                return handleGamemode(sender, args, GameMode.ADVENTURE);
            case "gmsp":
                return handleGamemode(sender, args, GameMode.SPECTATOR);
            case "fly":
                return handleFly(sender, args);
            case "speed":
                return handleSpeed(sender, args);
            case "invincible":
            case "god":
                return handleInvincible(sender, args);
            case "tphere":
                return handleTphere(sender, args);
            case "tpall":
                return handleTpall(sender, args);
            case "invsee":
                return handleInvsee(sender, args);
            case "enchant":
                return handleEnchant(sender, args);
            case "sendserver":
                return handleBungeeSend(sender, args);
            default:
                return false;
        }
    }

    private boolean handleGamemode(CommandSender sender, String[] args, GameMode mode) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().color("&cConsole must specify a player!"));
                return true;
            }
            target = (Player) sender;
        }

        target.setGameMode(mode);
        target.sendMessage(plugin.getMessageManager().color("&aYour gamemode was set to &e" + mode.name() + "&a."));
        if (!target.equals(sender)) {
            sender.sendMessage(plugin.getMessageManager().color("&aSet &e" + target.getName() + "'s &agamemode to &e" + mode.name() + "&a."));
        }
        return true;
    }

    private boolean handleFly(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().color("&cConsole must specify a player!"));
                return true;
            }
            target = (Player) sender;
        }

        boolean allow = !target.getAllowFlight();
        target.setAllowFlight(allow);
        target.setFlying(allow);

        String status = allow ? "&aenabled" : "&cdisabled";
        target.sendMessage(plugin.getMessageManager().color("&aFlight mode " + status + "&a."));
        if (!target.equals(sender)) {
            sender.sendMessage(plugin.getMessageManager().color("&aFlight mode " + status + " &afor &e" + target.getName() + "&a."));
        }
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /speed <1-10> [player]"));
            return true;
        }

        int speedVal;
        try {
            speedVal = Integer.parseInt(args[0]);
            if (speedVal < 1 || speedVal > 10) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().color("&cSpeed must be an integer between 1 and 10!"));
            return true;
        }

        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().color("&cConsole must specify a player!"));
                return true;
            }
            target = (Player) sender;
        }

        float speedFloat = (float) speedVal / 10.0f;
        target.setFlySpeed(speedFloat);
        target.setWalkSpeed(speedFloat);

        target.sendMessage(plugin.getMessageManager().color("&aYour speed was set to &e" + speedVal + "&a."));
        if (!target.equals(sender)) {
            sender.sendMessage(plugin.getMessageManager().color("&aSet &e" + target.getName() + "'s &aspeed to &e" + speedVal + "&a."));
        }
        return true;
    }

    private boolean handleInvincible(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().color("&cConsole must specify a player!"));
                return true;
            }
            target = (Player) sender;
        }

        UUID uuid = target.getUniqueId();
        if (invinciblePlayers.contains(uuid)) {
            invinciblePlayers.remove(uuid);
            target.sendMessage(plugin.getMessageManager().color("&cInvincibility disabled!"));
            if (!target.equals(sender)) {
                sender.sendMessage(plugin.getMessageManager().color("&cDisabled invincibility for &e" + target.getName() + "&c."));
            }
        } else {
            invinciblePlayers.add(uuid);
            target.sendMessage(plugin.getMessageManager().color("&aInvincibility enabled! (Taking zero damage)"));
            if (!target.equals(sender)) {
                sender.sendMessage(plugin.getMessageManager().color("&aEnabled invincibility for &e" + target.getName() + "&a."));
            }
        }
        return true;
    }

    private boolean handleTphere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cOnly players can use /tphere!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /tphere <player>"));
            return true;
        }

        Player staff = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
            return true;
        }

        target.teleport(staff.getLocation());
        target.sendMessage(plugin.getMessageManager().color("&aYou were teleported to &e" + staff.getName() + "&a."));
        staff.sendMessage(plugin.getMessageManager().color("&aTeleported &e" + target.getName() + " &ato your location."));
        return true;
    }

    private boolean handleTpall(CommandSender sender, String[] args) {
        Location targetLoc = null;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().color("&cUsage: /tpall <player | x y z>"));
                return true;
            }
            targetLoc = ((Player) sender).getLocation();
        } else if (args.length == 1) {
            Player p = Bukkit.getPlayer(args[0]);
            if (p != null && p.isOnline()) {
                targetLoc = p.getLocation();
            } else {
                sender.sendMessage(plugin.getMessageManager().color("&cTarget player not found!"));
                return true;
            }
        } else if (args.length == 3) {
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                if (sender instanceof Player) {
                    targetLoc = new Location(((Player) sender).getWorld(), x, y, z);
                } else {
                    targetLoc = new Location(Bukkit.getWorlds().get(0), x, y, z);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageManager().color("&cInvalid coordinates! Format: /tpall <x> <y> <z>"));
                return true;
            }
        }

        if (targetLoc != null) {
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && online.equals(sender)) continue;
                online.teleport(targetLoc);
                online.sendMessage(plugin.getMessageManager().color("&eTeleported by staff!"));
                count++;
            }
            sender.sendMessage(plugin.getMessageManager().color("&aTeleported &e" + count + " &aplayers successfully."));
        }
        return true;
    }

    private boolean handleInvsee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cOnly in-game players can use /invsee!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /invsee <player>"));
            return true;
        }

        Player staff = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            staff.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
            return true;
        }

        openInvseeGui(staff, target);
        return true;
    }

    public static void openInvseeGui(Player staff, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54, INVSEE_PREFIX + target.getName());

        // Slots 0 to 35: Target storage & hotbar items
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            if (contents[i] != null) inv.setItem(i, contents[i]);
        }

        // Separators
        ItemStack pane = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
        ItemMeta pMeta = pane.getItemMeta();
        pMeta.setDisplayName(" ");
        pane.setItemMeta(pMeta);
        for (int i = 36; i < 40; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        // Armor (Slots 40, 41, 42, 43)
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null && armor.length >= 4) {
            inv.setItem(40, armor[3]); // Helmet
            inv.setItem(41, armor[2]); // Chestplate
            inv.setItem(42, armor[1]); // Leggings
            inv.setItem(43, armor[0]); // Boots
        }

        // Off-Hand item (Slot 44 via reflection for 1.9+)
        try {
            Method m = target.getInventory().getClass().getMethod("getItemInOffHand");
            ItemStack offHand = (ItemStack) m.invoke(target.getInventory());
            if (offHand != null && offHand.getType() != Material.AIR) {
                inv.setItem(44, offHand);
            }
        } catch (Exception ignored) {}

        // Health & Food Telemetry (Slot 49)
        ItemStack heart = new ItemStack(Material.APPLE);
        ItemMeta hMeta = heart.getItemMeta();
        hMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Player Health & Status");
        List<String> hLore = new ArrayList<String>();
        hLore.add(ChatColor.GRAY + "Health: " + ChatColor.RED + (int) target.getHealth() + "/" + (int) target.getMaxHealth() + " HP");
        hLore.add(ChatColor.GRAY + "Food: " + ChatColor.GOLD + target.getFoodLevel() + "/20");
        hLore.add(ChatColor.GRAY + "Level: " + ChatColor.AQUA + target.getLevel());
        hMeta.setLore(hLore);
        heart.setItemMeta(hMeta);
        inv.setItem(49, heart);

        staff.openInventory(inv);
    }

    private boolean handleEnchant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cOnly in-game players can use /enchant!"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /enchant <enchantment> <level>"));
            return true;
        }

        Player player = (Player) sender;
        @SuppressWarnings("deprecation")
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(plugin.getMessageManager().color("&cYou must hold an item in your hand!"));
            return true;
        }

        String enchantInput = args[0].toUpperCase();
        Enchantment ench = getEnchantmentByName(enchantInput);
        if (ench == null) {
            player.sendMessage(plugin.getMessageManager().color("&cInvalid enchantment name! Example: sharpness, protection, unbreaking, power"));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
            if (level < 0 || level > 32767) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessageManager().color("&cEnchantment level must be a number between 0 and 32767!"));
            return true;
        }

        if (level == 0) {
            item.removeEnchantment(ench);
            player.sendMessage(plugin.getMessageManager().color("&aRemoved &e" + ench.getName() + " &afrom your item."));
        } else {
            item.addUnsafeEnchantment(ench, level);
            player.sendMessage(plugin.getMessageManager().color("&aApplied &e" + ench.getName() + " " + level + " &ato your held item!"));
        }
        return true;
    }

    private Enchantment getEnchantmentByName(String name) {
        Enchantment ench = Enchantment.getByName(name);
        if (ench != null) return ench;

        // Common aliases
        switch (name) {
            case "SHARPNESS": return Enchantment.DAMAGE_ALL;
            case "PROTECTION": return Enchantment.PROTECTION_ENVIRONMENTAL;
            case "FIRE_PROTECTION": return Enchantment.PROTECTION_FIRE;
            case "FEATHER_FALLING": return Enchantment.PROTECTION_FALL;
            case "BLAST_PROTECTION": return Enchantment.PROTECTION_EXPLOSIONS;
            case "PROJECTILE_PROTECTION": return Enchantment.PROTECTION_PROJECTILE;
            case "RESPIRATION": return Enchantment.OXYGEN;
            case "AQUA_AFFINITY": return Enchantment.WATER_WORKER;
            case "THORNS": return Enchantment.THORNS;
            case "SMITE": return Enchantment.DAMAGE_UNDEAD;
            case "BANE_OF_ARTHROPODS": return Enchantment.DAMAGE_ARTHROPODS;
            case "KNOCKBACK": return Enchantment.KNOCKBACK;
            case "FIRE_ASPECT": return Enchantment.FIRE_ASPECT;
            case "LOOTING": return Enchantment.LOOT_BONUS_MOBS;
            case "EFFICIENCY": return Enchantment.DIG_SPEED;
            case "SILK_TOUCH": return Enchantment.SILK_TOUCH;
            case "UNBREAKING": return Enchantment.DURABILITY;
            case "FORTUNE": return Enchantment.LOOT_BONUS_BLOCKS;
            case "POWER": return Enchantment.ARROW_DAMAGE;
            case "PUNCH": return Enchantment.ARROW_KNOCKBACK;
            case "FLAME": return Enchantment.ARROW_FIRE;
            case "INFINITY": return Enchantment.ARROW_INFINITE;
            default: return null;
        }
    }

    private boolean handleBungeeSend(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /sendserver <player> <server>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getMessageManager().color("&cPlayer not found!"));
            return true;
        }

        String serverName = args[1];
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);

        target.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        sender.sendMessage(plugin.getMessageManager().color("&aSent &e" + target.getName() + " &ato server &e" + serverName + "&a."));
        return true;
    }
}
