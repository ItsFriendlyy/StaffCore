package com.github.staffcore;

import com.github.staffcore.commands.*;
import com.github.staffcore.config.MessageManager;
import com.github.staffcore.freeze.FreezeManager;
import com.github.staffcore.listeners.FreezeListener;
import com.github.staffcore.listeners.GuiListener;
import com.github.staffcore.listeners.PunishmentListener;
import com.github.staffcore.listeners.UtilityListener;
import com.github.staffcore.listeners.VanishListener;
import com.github.staffcore.punishment.PunishmentManager;
import com.github.staffcore.staffchat.StaffChatManager;
import com.github.staffcore.util.UpdateChecker;
import com.github.staffcore.vanish.VanishManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        // Save default config.yml
        saveDefaultConfig();

        // 1. Register BungeeCord Messaging Channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // 2. Initialize GitHub Update Checker
        this.updateChecker = new UpdateChecker(this, "ItsFriendlyy", "StaffCore");
        this.updateChecker.checkForUpdates();

        // 3. Initialize Managers
        this.messageManager = new MessageManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.freezeManager = new FreezeManager(this);
        this.staffChatManager = new StaffChatManager(this);
        this.vanishManager = new VanishManager(this);

        // 4. Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PunishmentListener(this), this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(new VanishListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new UtilityListener(), this);

        // 5. Register Punishment Commands
        PunishCommands punishExecutor = new PunishCommands(this);
        StaffTabCompleter tabCompleter = new StaffTabCompleter();

        String[] punishCmds = new String[]{"ban", "tempban", "ipban", "tempipban", "unban", "unbanip", "mute", "tempmute", "unmute", "warn"};
        for (int i = 0; i < punishCmds.length; i++) {
            PluginCommand cmd = getCommand(punishCmds[i]);
            if (cmd != null) {
                cmd.setExecutor(punishExecutor);
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

        // Chat
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

        // 6. Register Utility & Access Commands
        UtilityCommands utilExecutor = new UtilityCommands(this);
        String[] utilCmds = new String[]{"gmc", "gms", "gma", "gmsp", "fly", "speed", "invincible", "tphere", "tpall", "invsee", "enchant", "sendserver"};
        for (int i = 0; i < utilCmds.length; i++) {
            PluginCommand cmd = getCommand(utilCmds[i]);
            if (cmd != null) {
                cmd.setExecutor(utilExecutor);
                cmd.setTabCompleter(tabCompleter);
            }
        }

        // Admin Reload Command
        PluginCommand adminCmd = getCommand("staffcore");
        if (adminCmd != null) {
            adminCmd.setExecutor(new CommandExecutor() {
                @Override
                public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                        reloadConfig();
                        punishmentManager.loadData();
                        vanishManager.loadData();
                        if (updateChecker != null) {
                            updateChecker.checkForUpdates();
                        }
                        sender.sendMessage(messageManager.getPrefixedMessage("messages.reload-success"));
                        return true;
                    }
                    sender.sendMessage(messageManager.color("&cUsage: /staffcore reload"));
                    return true;
                }
            });
        }

        getLogger().info("StaffCore has been successfully enabled! Ready for moderation.");
    }

    @Override
    public void onDisable() {
        if (freezeManager != null) {
            freezeManager.stop();
        }
        if (vanishManager != null) {
            vanishManager.stop();
        }
        if (punishmentManager != null) {
            punishmentManager.saveData();
            if (punishmentManager.getDatabaseManager() != null) {
                punishmentManager.getDatabaseManager().close();
            }
        }
        getLogger().info("StaffCore has been successfully disabled.");
    }

    public MessageManager getMessageManager() { return messageManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public StaffChatManager getStaffChatManager() { return staffChatManager; }
    public VanishManager getVanishManager() { return vanishManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
