package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PhaseCommand implements CommandExecutor {

    private final StaffCore plugin;

    public PhaseCommand(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cThis command can only be executed by a player in-game!"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("staffcore.command.phase") && !player.hasPermission("staffcore.phase") && !player.isOp()) {
            player.sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.no-permission"));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("inv") || args[0].equalsIgnoreCase("inventory"))) {
            plugin.getPhaseManager().openSurvivalInventory(player);
            return true;
        }

        plugin.getPhaseManager().togglePhase(player);
        return true;
    }
}