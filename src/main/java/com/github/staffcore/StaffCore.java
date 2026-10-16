package com.github.staffcore;

import com.github.staffcore.commands.*;
import com.github.staffcore.config.MessageManager;
import com.github.staffcore.freeze.FreezeManager;
import com.github.staffcore.listeners.FreezeListener;
import com.github.staffcore.listeners.GuiListener;
import com.github.staffcore.listeners.PunishmentListener;
import com.github.staffcore.listeners.VanishListener;
import com.github.staffcore.punishment.PunishmentManager;
import com.github.staffcore.staffchat.StaffChatManager;
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

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize Managers
        this.messageManager = new MessageManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.freezeManager = new FreezeManager(this);
        this.staffChatManager = new StaffChatManager(this);
        this.vanishManager = new VanishManager(this);

        // Register Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PunishmentListener(this), this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(new VanishListener(this), this);
        pm.registerEvents(new GuiListener(), this);

        // Register Commands & Tab Completers
        registerCommands();

        getLogger().info("StaffCore has been successfully enabled! Ready for moderation.");
    }

    @Override
    public void onDisable() {
        if (freezeManager != null) {
            freezeManager.stop();
        }
        if (punishmentManager != null) {
            punishmentManager.saveData();
        }
        getLogger().info("StaffCore has been successfully disabled.");
    }

    private void registerCommands() {
        PunishCommands punishExecutor = new PunishCommands(this);
        StaffTabCompleter tabCompleter = new StaffTabCompleter();

        String[] punishCmds = {"ban", "tempban", "ipban", "tempipban", "unban", "unbanip", "mute", "tempmute", "unmute", "warn"};
        for (String cmdName : punishCmds) {
            PluginCommand cmd = getCommand(cmdName);
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

        // StaffCore Admin Command
        PluginCommand adminCmd = getCommand("staffcore");
        if (adminCmd != null) {
            adminCmd.setExecutor((sender, command, label, args) -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    punishmentManager.loadData();
                    sender.sendMessage(messageManager.getPrefixedMessage("messages.reload-success"));
                    return true;
                }
                sender.sendMessage(messageManager.color("&cUsage: /staffcore reload"));
                return true;
            });
        }
    }

    public MessageManager getMessageManager() { return messageManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public StaffChatManager getStaffChatManager() { return staffChatManager; }
    public VanishManager getVanishManager() { return vanishManager; }
}
