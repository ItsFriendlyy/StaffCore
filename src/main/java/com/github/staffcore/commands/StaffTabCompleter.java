package com.github.staffcore.commands;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StringUtil;

import java.util.*;

public class StaffTabCompleter implements TabCompleter {

    private final StaffCore plugin;
    private static final List<String> DURATIONS = Arrays.asList("30m", "1h", "2h", "12h", "1d", "7d", "30d", "permanent");
    private static final List<String> REASONS = Arrays.asList("Cheating/Hacking", "Toxic Behavior", "Spamming", "Advertising", "Griefing", "Staff Disrespect");
    private static final List<String> CHAT_SUBS = Arrays.asList("staff", "all", "global");
    private static final List<String> STAFFCORE_SUBS = Arrays.asList("reload", "update");

    public StaffTabCompleter() {
        this(JavaPlugin.getPlugin(StaffCore.class));
    }

    public StaffTabCompleter(StaffCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        // 0. /staffcore
        if (cmd.equals("staffcore")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], STAFFCORE_SUBS, completions);
                Collections.sort(completions);
            }
            return completions;
        }

        // 1. /chat
        if (cmd.equals("chat")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], CHAT_SUBS, completions);
            }
            return completions;
        }

        // 2. /vanish, /repair, /cleargrounditems, /setjail
        if (cmd.equals("vanish") || cmd.equals("v") || cmd.equals("repair") || cmd.equals("setjail") ||
            cmd.equals("cleargrounditems") || cmd.equals("cleargrouditems") || cmd.equals("clearitems") || cmd.equals("clearlag")) {
            return Collections.emptyList();
        }

        // 2b. /deljail <jail_name>
        if (cmd.equals("deljail")) {
            if (args.length == 1) {
                return getJailNameCompletions(args[0]);
            }
            return Collections.emptyList();
        }

        // 2c. /jail <player> <jail_name> [reason] [-s]
        if (cmd.equals("jail")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], false);
            } else if (args.length == 2) {
                return getJailNameCompletions(args[1]);
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

        // 2d. /tempjail <player> <jail_name> <duration> [reason] [-s]
        if (cmd.equals("tempjail")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], false);
            } else if (args.length == 2) {
                return getJailNameCompletions(args[1]);
            } else if (args.length == 3) {
                StringUtil.copyPartialMatches(args[2], DURATIONS, completions);
                return completions;
            } else if (args.length == 4) {
                List<String> options = new ArrayList<>(REASONS);
                options.add("-s");
                StringUtil.copyPartialMatches(args[3], options, completions);
                return completions;
            } else {
                if (!args[args.length - 2].equalsIgnoreCase("-s")) {
                    StringUtil.copyPartialMatches(args[args.length - 1], Collections.singletonList("-s"), completions);
                }
                return completions;
            }
        }

        // 3. /enchant tab completion (all Minecraft enchantments + suggestions up to 255)
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
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5", "10", "50", "100", "255");
                StringUtil.copyPartialMatches(args[1], levels, completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 4. /give <player|*> <item> [amount] (includes ALL runtime 1.8 - 1.21+ items & Netherite)
        if (cmd.equals("give")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], true);
            } else if (args.length == 2) {
                Set<String> itemNames = new TreeSet<>();
                for (Material m : UtilityCommands.getAllRuntimeMaterials()) {
                    if (m != null && m != Material.AIR && !m.name().startsWith("LEGACY_")) {
                        itemNames.add(m.name().toLowerCase());
                    }
                }
                itemNames.addAll(Arrays.asList(
                    "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_hoe",
                    "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
                    "netherite_ingot", "netherite_scrap", "netherite_block", "netherite_upgrade_smithing_template",
                    "ancient_debris", "trident", "crossbow", "elytra", "mace", "wind_charge", "heavy_core",
                    "golden_apple", "enchanted_golden_apple", "totem_of_undying", "ender_pearl", "experience_bottle", "command_block", "steak"
                ));
                StringUtil.copyPartialMatches(args[1].toLowerCase(), itemNames, completions);
                return completions;
            } else if (args.length == 3) {
                List<String> amounts = Arrays.asList("1", "16", "32", "64");
                StringUtil.copyPartialMatches(args[2], amounts, completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 5. /effect <player|*> <potion|clear> [seconds] [level]
        if (cmd.equals("effect")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], true);
            } else if (args.length == 2) {
                Set<String> effects = new TreeSet<>();
                effects.add("clear");
                for (PotionEffectType pet : PotionEffectType.values()) {
                    if (pet != null && pet.getName() != null) {
                        effects.add(pet.getName().toLowerCase());
                    }
                }
                effects.addAll(Arrays.asList(
                    "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health",
                    "instant_damage", "jump_boost", "nausea", "regeneration", "resistance",
                    "fire_resistance", "water_breathing", "invisibility", "blindness", "night_vision",
                    "hunger", "weakness", "poison", "wither", "health_boost", "absorption", "saturation",
                    "glowing", "levitation", "slow_falling", "conduit_power", "dolphins_grace", "darkness"
                ));
                StringUtil.copyPartialMatches(args[1].toLowerCase(), effects, completions);
                return completions;
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("clear")) {
                List<String> secs = Arrays.asList("30", "60", "120", "300", "600", "3600");
                StringUtil.copyPartialMatches(args[2], secs, completions);
                return completions;
            } else if (args.length == 4 && !args[1].equalsIgnoreCase("clear")) {
                List<String> levels = Arrays.asList("1", "2", "3", "4", "5", "10", "255");
                StringUtil.copyPartialMatches(args[3], levels, completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 6. /speed tab completion
        if (cmd.equals("speed")) {
            if (args.length == 1) {
                List<String> speeds = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
                StringUtil.copyPartialMatches(args[0], speeds, completions);
                return completions;
            } else if (args.length == 2) {
                return getPlayerNameCompletions(args[1], true);
            }
            return Collections.emptyList();
        }

        // 7. Single-player only commands (NO wildcard *): /invsee, /check, /tpall, /freeze, /unmute, /unjail
        if (cmd.equals("invsee") || cmd.equals("check") || cmd.equals("tpall") || cmd.equals("freeze") || cmd.equals("unmute") || cmd.equals("unjail")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], false);
            }
            return Collections.emptyList();
        }

        // 8. /unban and /pardon tab completion directly from ban list (NO wildcard *)
        if (cmd.equals("unban") || cmd.equals("pardon")) {
            if (args.length == 1) {
                return getBannedPlayerCompletions(args[0]);
            }
            return Collections.emptyList();
        }

        // 9. /unbanip tab completion directly from active IP bans (NO wildcard *)
        if (cmd.equals("unbanip") || cmd.equals("unban-ip") || cmd.equals("pardon-ip") || cmd.equals("pardonip")) {
            if (args.length == 1) {
                return getIpBannedCompletions(args[0]);
            }
            return Collections.emptyList();
        }

        // 10. /sudo tab completion (with *)
        if (cmd.equals("sudo")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], true);
            } else if (args.length == 2) {
                List<String> suggestions = Arrays.asList("m:", "spawn", "help", "msg");
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 11. /sendserver tab completion (with *)
        if (cmd.equals("sendserver")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], true);
            } else if (args.length == 2) {
                List<String> servers = Arrays.asList("lobby", "hub", "survival", "skyblock");
                StringUtil.copyPartialMatches(args[1], servers, completions);
                return completions;
            }
            return Collections.emptyList();
        }

        // 12. Utility player commands supporting *: heal, feed, struck, strike, fly, invincible, gmc, gms, gma, gmsp, tphere, clear
        if (cmd.equals("heal") || cmd.equals("feed") || cmd.equals("struck") || cmd.equals("strike") ||
            cmd.equals("fly") || cmd.equals("invincible") ||
            cmd.equals("gmc") || cmd.equals("gms") || cmd.equals("gma") || cmd.equals("gmsp") || cmd.equals("tphere") ||
            cmd.equals("clear") || cmd.equals("ci") || cmd.equals("clearinventory")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], true);
            }
            return Collections.emptyList();
        }

        // 13. All single-durationless punish commands (NO wildcard *): /ban, /ipban, /ban-ip, /banip, /mute, /warn
        if (cmd.equals("ban") || cmd.equals("ipban") || cmd.equals("ban-ip") || cmd.equals("banip") ||
            cmd.equals("mute") || cmd.equals("warn")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], false);
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

        // 14. All temporary punish commands (NO wildcard *): /tempban, /tempipban, /tempban-ip, /tempbanip, /tempmute
        if (cmd.equals("tempban") || cmd.equals("tempipban") || cmd.equals("tempban-ip") || cmd.equals("tempbanip") ||
            cmd.equals("tempmute")) {
            if (args.length == 1) {
                return getPlayerNameCompletions(args[0], false);
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

    private List<String> getJailNameCompletions(String prefix) {
        List<String> matches = new ArrayList<>();
        try {
            StaffCore core = (this.plugin != null) ? this.plugin : JavaPlugin.getPlugin(StaffCore.class);
            if (core != null && core.getPunishmentManager() != null) {
                StringUtil.copyPartialMatches(prefix.toLowerCase(), core.getPunishmentManager().getJailNames(), matches);
                Collections.sort(matches);
            }
        } catch (Throwable ignored) {}
        return matches;
    }

    private List<String> getPlayerNameCompletions(String prefix, boolean includeWildcard) {
        List<String> names = new ArrayList<>();
        if (includeWildcard) {
            names.add("*");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, names, matches);
        Collections.sort(matches);
        return matches;
    }

    private List<String> getBannedPlayerCompletions(String prefix) {
        Set<String> banned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        try {
            StaffCore core = (this.plugin != null) ? this.plugin : JavaPlugin.getPlugin(StaffCore.class);
            if (core != null && core.getPunishmentManager() != null) {
                banned.addAll(core.getPunishmentManager().getBannedPlayerNames());
            }
        } catch (Throwable ignored) {}

        try {
            for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
                if (entry != null && entry.getTarget() != null) {
                    banned.add(entry.getTarget());
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (org.bukkit.OfflinePlayer op : Bukkit.getBannedPlayers()) {
                if (op != null && op.getName() != null) {
                    banned.add(op.getName());
                }
            }
        } catch (Throwable ignored) {}

        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, banned, matches);
        Collections.sort(matches);
        return matches;
    }

    private List<String> getIpBannedCompletions(String prefix) {
        Set<String> ipBanned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        try {
            StaffCore core = (this.plugin != null) ? this.plugin : JavaPlugin.getPlugin(StaffCore.class);
            if (core != null && core.getPunishmentManager() != null) {
                ipBanned.addAll(core.getPunishmentManager().getIpBannedNamesOrIps());
            }
        } catch (Throwable ignored) {}

        try {
            for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.IP).getBanEntries()) {
                if (entry != null && entry.getTarget() != null) {
                    ipBanned.add(entry.getTarget());
                }
            }
        } catch (Throwable ignored) {}

        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, ipBanned, matches);
        Collections.sort(matches);
        return matches;
    }
}