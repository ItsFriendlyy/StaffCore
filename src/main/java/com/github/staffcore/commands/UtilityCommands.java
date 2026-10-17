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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UtilityCommands implements CommandExecutor {

    private final StaffCore plugin;
    public static final Set<UUID> invinciblePlayers = new HashSet<>();
    public static final String INVSEE_PREFIX = ChatColor.DARK_GRAY + "Invsee: " + ChatColor.DARK_RED;

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
            sender.sendMessage(ChatColor.RED + "Speed must be a number between 1 and 10!");
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

        float bukkitSpeed = speedVal / 10.0f;
        if (target.isFlying()) {
            target.setFlySpeed(bukkitSpeed);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aFly speed set to &e" + speedVal + "&a for &e" + target.getName() + "&a."));
        } else {
            target.setWalkSpeed(bukkitSpeed * 0.2f * 5.0f);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aWalk speed set to &e" + speedVal + "&a for &e" + target.getName() + "&a."));
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
            staff.sendMessage(ChatColor.RED + "Usage: /invsee <player> [direct]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            staff.sendMessage(ChatColor.RED + "Player " + args[0] + " not found or is offline!");
            return true;
        }

        if (args.length > 1 && args[1].equalsIgnoreCase("direct")) {
            staff.openInventory(target.getInventory());
            staff.sendMessage(ChatColor.GREEN + "Opened " + target.getName() + "'s direct inventory.");
            return true;
        }

        Inventory gui = Bukkit.createInventory(staff, 54, INVSEE_PREFIX + target.getName());

        for (int i = 0; i < 36; i++) {
            gui.setItem(i, target.getInventory().getItem(i));
        }

        Material glassMat = Material.matchMaterial("GRAY_STAINED_GLASS_PANE");
        if (glassMat == null) glassMat = Material.matchMaterial("STAINED_GLASS_PANE");
        ItemStack divider = new ItemStack(glassMat != null ? glassMat : Material.STONE);
        ItemMeta meta = divider.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "↑ Inventory | Armor ↓");
            divider.setItemMeta(meta);
        }
        for (int i = 36; i < 45; i++) {
            gui.setItem(i, divider);
        }

        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null) {
            if (armor.length > 0) gui.setItem(45, armor[0]); // Boots
            if (armor.length > 1) gui.setItem(46, armor[1]); // Leggings
            if (armor.length > 2) gui.setItem(47, armor[2]); // Chestplate
            if (armor.length > 3) gui.setItem(48, armor[3]); // Helmet
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Health: " + ChatColor.RED + Math.round(target.getHealth()) + "/" + Math.round(target.getMaxHealth()));
            lore.add(ChatColor.GRAY + "Food: " + ChatColor.GOLD + target.getFoodLevel() + "/20");
            lore.add(ChatColor.GREEN + "✔ Fully Editable in Real Time");
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(53, info);

        staff.openInventory(gui);
        staff.sendMessage(ChatColor.GREEN + "Opened " + target.getName() + "'s inventory. You can edit items and armor live.");
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

        String enchantName = args[0].toUpperCase().replace("MINECRAFT:", "");
        Enchantment ench = Enchantment.getByName(enchantName);
        if (ench == null) {
            for (Enchantment e : Enchantment.values()) {
                if (e.getName().equalsIgnoreCase(enchantName) || e.getName().contains(enchantName)) {
                    ench = e;
                    break;
                }
            }
        }

        if (ench == null) {
            staff.sendMessage(ChatColor.RED + "Enchantment '" + args[0] + "' not found!");
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