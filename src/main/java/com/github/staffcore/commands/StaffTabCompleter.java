package com.github.staffcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StaffTabCompleter implements TabCompleter {

    private static final List<String> DURATIONS = Arrays.asList("30m", "1h", "2h", "12h", "1d", "7d", "30d", "permanent");
    private static final List<String> REASONS = Arrays.asList("Cheating/Hacking", "Toxic Behavior", "Spamming", "Advertising", "Griefing", "Staff Disrespect");
    private static final List<String> CHAT_SUBS = Arrays.asList("staff", "all", "global");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("chat")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], CHAT_SUBS, completions);
            }
            return completions;
        }

        if (cmd.equals("vanish") || cmd.equals("v")) {
            return Collections.emptyList();
        }

        if (cmd.equals("freeze") || cmd.equals("check") || cmd.equals("unban") || cmd.equals("unmute") || cmd.equals("unbanip")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0]);
            }
            return Collections.emptyList();
        }

        if (cmd.equals("ban") || cmd.equals("ipban") || cmd.equals("mute") || cmd.equals("warn")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0]);
            } else if (args.length == 2) {
                List<String> options = new ArrayList<>(REASONS);
                options.add("-s");
                StringUtil.copyPartialMatches(args[1], options, completions);
                return completions;
            } else {
                if (!args[args.length - 2].equalsIgnoreCase("-s")) {
                    StringUtil.copyPartialMatches(args[args.length - 1], Collections.singletonList("-s"), completions);
                }
                return completions;
            }
        }

        if (cmd.equals("tempban") || cmd.equals("tempipban") || cmd.equals("tempmute")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0]);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], DURATIONS, completions);
                return completions;
            } else if (args.length == 3) {
                List<String> options = new ArrayList<>(REASONS);
                options.add("-s");
                StringUtil.copyPartialMatches(args[2], options, completions);
                return completions;
            } else {
                if (!args[args.length - 2].equalsIgnoreCase("-s")) {
                    StringUtil.copyPartialMatches(args[args.length - 1], Collections.singletonList("-s"), completions);
                }
                return completions;
            }
        }

        return completions;
    }

    private List<String> getPlayerNameCompletions(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, names, matches);
        Collections.sort(matches);
        return matches;
    }
}
