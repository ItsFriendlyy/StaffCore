package com.github.staffcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
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

        // 1. /chat
        if (cmd.equals("chat")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], CHAT_SUBS, completions);
            }
            return completions;
        }

        // 2. /vanish
        if (cmd.equals("vanish") || cmd.equals("v")) {
            return Collections.emptyList();
        }

        // 3. /enchant tab completion (every enchantment in Minecraft + level suggestions)
        if (cmd.equals("enchant")) {
            if (args.length == 1) {
                List<String> enchantNames = new ArrayList<>();
                for (Enchantment e : Enchantment.values()) {
                    if (e != null && e.getName() != null) {
                        enchantNames.add(e.getName().toLowerCase());
                    }
                }
                String[] aliases = {
                    "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting",
                    "efficiency", "silk_touch", "unbreaking", "fortune",
                    "power", "punch", "flame", "infinity",
                    "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection",
                    "respiration", "aqua_affinity", "thorns", "depth_strider",
                    "frost_walker", "mending", "sweeping_edge", "loyalty", "impaling", "riptide",
                    "channeling", "multishot", "quick_charge", "piercing", "soul_speed", "swift_sneak"
                };
                for (String a : aliases) {
                    if (!enchantNames.contains(a)) {
                        enchantNames.add(a);
                    }
                }
                StringUtil.copyPartialMatches(args[0], enchantNames, completions);
                Collections.sort(completions);
                return completions;
            } else if (args.length == 2) {
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5", "10", "50", "100", "255", "1000", "32767");
                StringUtil.copyPartialMatches(args[1], levels, completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 4. /speed tab completion
        if (cmd.equals("speed")) {
            if (args.length == 1) {
                List<String> speeds = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
                StringUtil.copyPartialMatches(args[0], speeds, completions);
                return completions;
            } else if (args.length == 2) {
                return getPlayerNameCompletions(args[1]);
            }
            return Collections.emptyList();
        }

        // 5. /invsee tab completion
        if (cmd.equals("invsee")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0]);
            } else if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Collections.singletonList("armor"), completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 6. Utility player commands and unbans
        if (cmd.equals("freeze") || cmd.equals("check") || cmd.equals("unban") || cmd.equals("pardon") ||
            cmd.equals("unmute") || cmd.equals("unbanip") || cmd.equals("unban-ip") || cmd.equals("pardon-ip") ||
            cmd.equals("pardonip") || cmd.equals("fly") || cmd.equals("invincible") || cmd.equals("god") ||
            cmd.equals("gmc") || cmd.equals("gms") || cmd.equals("gma") || cmd.equals("gmsp") || cmd.equals("tphere")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0]);
            }
            return Collections.emptyList();
        }

        // 7. /ban, /ipban, /ban-ip, /banip, /mute, /warn
        if (cmd.equals("ban") || cmd.equals("ipban") || cmd.equals("ban-ip") || cmd.equals("banip") ||
            cmd.equals("mute") || cmd.equals("warn")) {
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

        // 8. /tempban, /tempipban, /tempban-ip, /tempbanip, /tempmute
        if (cmd.equals("tempban") || cmd.equals("tempipban") || cmd.equals("tempban-ip") || cmd.equals("tempbanip") || cmd.equals("tempmute")) {
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