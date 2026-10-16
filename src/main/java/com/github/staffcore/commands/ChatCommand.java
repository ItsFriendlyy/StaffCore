package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommand implements CommandExecutor {

    private final StaffCore plugin;

    public ChatCommand(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cOnly in-game players can toggle chat channels."));
            return true;
        }

        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage(plugin.getMessageManager().color("&cUsage: /chat <staff|all|global>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("staff") || sub.equals("sc")) {
            plugin.getStaffChatManager().setStaffChat(player, true);
        } else if (sub.equals("all") || sub.equals("global") || sub.equals("public")) {
            plugin.getStaffChatManager().setStaffChat(player, false);
        } else {
            player.sendMessage(plugin.getMessageManager().color("&cUnknown chat channel! Choose 'staff' or 'all'."));
        }

        return true;
    }
}
