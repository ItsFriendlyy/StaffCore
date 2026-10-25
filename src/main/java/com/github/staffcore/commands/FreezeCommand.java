package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FreezeCommand implements CommandExecutor {

    private final StaffCore plugin;

    public FreezeCommand(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /freeze <player|*>"));
            return true;
        }

        Player staffPlayer = (sender instanceof Player) ? (Player) sender : null;

        if (args[0].equals("*")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player && target.equals(sender)) continue;
                if (target.hasPermission("staffcore.bypass.freeze") || target.isOp()) continue;
                plugin.getFreezeManager().toggleFreeze(target, staffPlayer);
                count++;
            }
            sender.sendMessage(plugin.getMessageManager().color("&aToggled freeze for &e" + count + " &aonline players."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found!")
                            .replace("%target%", args[0])));
            return true;
        }

        if (sender instanceof Player && target.equals(sender)) {
            sender.sendMessage(plugin.getMessageManager().getPrefixedMessage("messages.cannot-punish-self"));
            return true;
        }

        if (target.hasPermission("staffcore.bypass.freeze")) {
            sender.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.cannot-freeze-exempt", "&cCannot freeze exempt player!")
                            .replace("%target%", target.getName())));
            return true;
        }

        plugin.getFreezeManager().toggleFreeze(target, staffPlayer);
        return true;
    }
}