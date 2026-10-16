package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import com.github.staffcore.gui.CheckGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CheckCommand implements CommandExecutor {

    private final StaffCore plugin;

    public CheckCommand(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cOnly in-game players can open the check GUI."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().color("&cUsage: /check <player>"));
            return true;
        }

        Player staff = (Player) sender;
        String targetName = args[0];

        Player onlineTarget = Bukkit.getPlayer(targetName);
        OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            staff.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found!")
                            .replace("%target%", targetName)));
            return true;
        }

        CheckGui.openGui(plugin, staff, target);
        return true;
    }
}
