package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private final StaffCore plugin;

    public VanishCommand(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().color("&cOnly in-game players can vanish."));
            return true;
        }

        Player player = (Player) sender;
        plugin.getVanishManager().toggleVanish(player);
        return true;
    }
}
