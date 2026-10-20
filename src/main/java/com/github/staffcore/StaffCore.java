package com.github.staffcore;

import com.github.staffcore.commands.*;
import com.github.staffcore.config.MessageManager;
import com.github.staffcore.database.DatabaseManager;
import com.github.staffcore.freeze.FreezeManager;
import com.github.staffcore.listeners.*;
import com.github.staffcore.punishment.PunishmentManager;
import com.github.staffcore.staffchat.StaffChatManager;
import com.github.staffcore.util.UpdateChecker;
import com.github.staffcore.vanish.VanishManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffCore extends JavaPlugin {

    private MessageManager messageManager;
    private PunishmentManager punishmentManager;
    private FreezeManager freezeManager;
    private StaffChatManager staffChatManager;
    private VanishManager vanishManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Initialize Managers
        this.messageManager = new MessageManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.freezeManager = new FreezeManager(this);
        this.staffChatManager = new StaffChatManager(this);
        this.vanishManager = new VanishManager(this);

        // 2. Register BungeeCord Plugin Messaging Channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // 3. Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PunishmentListener(this), this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(new VanishListener(this), this);
        pm.registerEvents(new GuiListener(), this);
        pm.registerEvents(new UtilityListener(this), this);

        // 4. Asynchronous GitHub Release Update Checker
        this.updateChecker = new UpdateChecker(this);
        pm.registerEvents(this.updateChecker, this);
        this.updateChecker.checkForUpdates();

        // 5. Register Commands & Tab Completers
        registerCommands();

        getLogger().info("StaffCore v" + getDescription().getVersion() + " enabled successfully! Ready for moderation.");
    }

    @Override
    public void onDisable() {
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

        // Moderation & Punishment Commands
        PunishCommands punishExecutor = new PunishCommands(this);
        String[] punishCmds = {"ban", "tempban", "ipban", "tempipban", "unban", "unbanip", "mute", "tempmute", "unmute", "warn"};
        for (String cmdName : punishCmds) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(punishExecutor);
                cmd.setTabCompleter(tabCompleter);
            }
        }

        // Staff Utilities
        UtilityCommands utilExecutor = new UtilityCommands(this);
        String[] utilCmds = {"gmc", "gms", "gmsp", "gma", "fly", "speed", "invincible", "tphere", "tpall", "invsee", "enchant", "sendserver", "heal", "feed", "repair", "struck", "sudo"};
        for (String cmdName : utilCmds) {
            PluginCommand cmd = getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(utilExecutor);
                cmd.setTabCompleter(tabCompleter);
            }
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

        // StaffCore Admin Command
        PluginCommand adminCmd = getCommand("staffcore");
        if (adminCmd != null) {
            adminCmd.setExecutor((sender, command, label, args) -> {
                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("reload")) {
                        reloadConfig();
                        punishmentManager.loadData();
                        sender.sendMessage(messageManager.getPrefixedMessage("messages.reload-success"));
                        updateChecker.checkForUpdates(sender);
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("update") || args[0].equalsIgnoreCase("version") || args[0].equalsIgnoreCase("checkupdate")) {
                        updateChecker.checkForUpdates(sender);
                        return true;
                    }
                }
                sender.sendMessage(messageManager.color("&cUsage: /staffcore <reload | update>"));
                return true;
            });
        }
    }

    public MessageManager getMessageManager() { return messageManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public StaffChatManager getStaffChatManager() { return staffChatManager; }
    public VanishManager getVanishManager() { return vanishManager; }
    public DatabaseManager getDatabaseManager() { return punishmentManager != null ? punishmentManager.getDatabaseManager() : null; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}