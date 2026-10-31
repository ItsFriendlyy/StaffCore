package com.github.staffcore;

import com.github.staffcore.commands.*;
import com.github.staffcore.config.MessageManager;
import com.github.staffcore.database.DatabaseManager;
import com.github.staffcore.freeze.FreezeManager;
import com.github.staffcore.hooks.PlaceholderHook;
import com.github.staffcore.hooks.VoicechatHook;
import com.github.staffcore.listeners.*;
import com.github.staffcore.phase.PhaseManager;
import com.github.staffcore.punishment.PunishmentManager;
import com.github.staffcore.staffchat.StaffChatManager;
import com.github.staffcore.util.UpdateChecker;
import com.github.staffcore.vanish.VanishManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffCore extends JavaPlugin {

    private MessageManager messageManager;
    private PunishmentManager punishmentManager;
    private FreezeManager freezeManager;
    private StaffChatManager staffChatManager;
    private VanishManager vanishManager;
    private PhaseManager phaseManager;
    private UpdateChecker updateChecker;
    private PunishCommands punishExecutor;
    private UtilityCommands utilExecutor;

    public static final int BSTATS_PLUGIN_ID = 34872;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Initialize Managers
        this.messageManager = new MessageManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.freezeManager = new FreezeManager(this);
        this.staffChatManager = new StaffChatManager(this);
        this.vanishManager = new VanishManager(this);
        this.phaseManager = new PhaseManager(this);

        // Load persistent utility states (/fly, /speed, /invincible, /spawn, /warps, /homes)
        UtilityCommands.loadStates(this);

        // 2. Register BungeeCord Plugin Messaging Channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // 3. Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PunishmentListener(this), this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(new VanishListener(this), this);
        pm.registerEvents(new PhaseListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new UtilityListener(this), this);

        // 4. Asynchronous GitHub Release Update Checker
        this.updateChecker = new UpdateChecker(this);
        pm.registerEvents(this.updateChecker, this);
        this.updateChecker.checkForUpdates();

        // 5. Initialize bStats Metrics
        try {
            int bstatsId = getConfig().getInt("bstats.plugin-id", BSTATS_PLUGIN_ID);
            if (bstatsId <= 0) {
                bstatsId = BSTATS_PLUGIN_ID;
            }
            if (getConfig().getBoolean("bstats.enabled", true)) {
                new Metrics(this, bstatsId);
                getLogger().info("bStats metrics enabled (Plugin ID: " + bstatsId + ")");
            } else {
                getLogger().info("bStats metrics is disabled in config.yml.");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not initialize bStats metrics: " + t.getMessage());
        }

        // 6. External Hooks (PlaceholderAPI & Simple Voice Chat)
        PlaceholderHook.tryRegister(this);
        VoicechatHook.tryRegister(this);

        // 7. Register Commands & Tab Completers
        registerCommands();

        getLogger().info("StaffCore v" + getDescription().getVersion() + " enabled successfully! Ready for moderation.");
    }

    @Override
    public void onDisable() {
        UtilityCommands.saveStates(this);

        if (phaseManager != null) {
            phaseManager.clearAll();
        }
        if (freezeManager != null) {
            freezeManager.stop();
        }
        if (vanishManager != null) {
            vanishManager.saveData();
        }
        if (punishmentManager != null) {
            punishmentManager.saveData();
            if (punishmentManager.getDatabaseManager() != null) {
                punishmentManager.getDatabaseManager().close();
            }
        }
        getLogger().info("StaffCore has been successfully disabled.");
    }

    private void registerCommands() {
        StaffTabCompleter tabCompleter = new StaffTabCompleter(this);

        // Moderation & Punishment Commands (including Jail commands)
        this.punishExecutor = new PunishCommands(this);
       String[] punishCmds = {
            "ban", "tempban", "ipban", "tempipban", "unban", "unbanip",
            "mute", "tempmute", "unmute", "warn", "kick",
            "setjail", "deljail", "jail", "tempjail", "unjail"
        };
        for (String cmdName : punishCmds) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(this.punishExecutor);
                cmd.setTabCompleter(tabCompleter);
            }
        }

        // Staff Utilities & Teleportation (Spawn, Warps, Homes)
        this.utilExecutor = new UtilityCommands(this);
        String[] utilCmds = {
            "gmc", "gms", "gmsp", "gma", "fly", "speed", "invincible",
            "tphere", "tpall", "invsee", "enchant", "sendserver",
            "heal", "feed", "repair", "struck", "sudo",
            "give", "effect", "cleargrounditems", "clear",
            "setspawn", "spawn", "setwarp", "delwarp", "warp",
            "sethome", "delhome", "home"
        };
        for (String cmdName : utilCmds) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(this.utilExecutor);
                cmd.setTabCompleter(tabCompleter);
            }
        }

        // Phase Command
        PluginCommand phaseCmd = getCommand("phase");
        if (phaseCmd != null) {
            phaseCmd.setExecutor(new PhaseCommand(this));
            phaseCmd.setTabCompleter(tabCompleter);
        }

        // Freeze
        PluginCommand freezeCmd = getCommand("freeze");
        if (freezeCmd != null) {
            freezeCmd.setExecutor(new FreezeCommand(this));
            freezeCmd.setTabCompleter(tabCompleter);
        }

        // Check GUI
        PluginCommand checkCmd = getCommand("check");
        if (checkCmd != null) {
            checkCmd.setExecutor(new CheckCommand(this));
            checkCmd.setTabCompleter(tabCompleter);
        }

        // Staff Chat
        PluginCommand chatCmd = getCommand("chat");
        if (chatCmd != null) {
            chatCmd.setExecutor(new ChatCommand(this));
            chatCmd.setTabCompleter(tabCompleter);
        }

        // Vanish
        PluginCommand vanishCmd = getCommand("vanish");
        if (vanishCmd != null) {
            vanishCmd.setExecutor(new VanishCommand(this));
            vanishCmd.setTabCompleter(tabCompleter);
        }

        // StaffCore Admin Command (/staffcore reload | /staffcore update)
        PluginCommand adminCmd = getCommand("staffcore");
        if (adminCmd != null) {
            adminCmd.setTabCompleter(tabCompleter);
            adminCmd.setExecutor((sender, command, label, args) -> {
                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("reload")) {
                        try {
                            saveDefaultConfig();
                            reloadConfig();
                            punishmentManager.loadData();
                            UtilityCommands.loadStates(this);

                            String rawReloadMsg = getConfig().getString("messages.reload-success");
                            if (rawReloadMsg == null || ChatColor.stripColor(messageManager.color(rawReloadMsg)).trim().isEmpty()) {
                                rawReloadMsg = "&aStaffCore configuration and data reloaded successfully!";
                            }
                            String prefix = messageManager.getPrefix();
                            if (prefix == null || ChatColor.stripColor(prefix).trim().isEmpty()) {
                                prefix = ChatColor.translateAlternateColorCodes('&', "&8[&cStaffCore&8] &r");
                            }
                            sender.sendMessage(prefix + messageManager.color(rawReloadMsg));
                        } catch (Exception e) {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&8[&cStaffCore&8] &cFailed to reload configuration: " + e.getMessage()));
                        }
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("update") || args[0].equalsIgnoreCase("version") || args[0].equalsIgnoreCase("checkupdate")) {
                        updateChecker.checkForUpdates(sender);
                        return true;
                    }
                }
                sender.sendMessage(messageManager.color("&8[&cStaffCore&8] &cUsage: /staffcore <reload | update>"));
                return true;
            });
        }
    }

    /**
     * Fallback command router so Bukkit never prints white usage text from plugin.yml.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (punishExecutor != null && punishExecutor.onCommand(sender, command, label, args)) {
            return true;
        }
        if (utilExecutor != null && utilExecutor.onCommand(sender, command, label, args)) {
            return true;
        }
        return true;
    }

    public MessageManager getMessageManager() { return messageManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public StaffChatManager getStaffChatManager() { return staffChatManager; }
    public VanishManager getVanishManager() { return vanishManager; }
    public PhaseManager getPhaseManager() { return phaseManager; }
    public DatabaseManager getDatabaseManager() { return punishmentManager != null ? punishmentManager.getDatabaseManager() : null; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}