package com.github.staffcore.punishment;

import com.github.staffcore.StaffCore;
import com.github.staffcore.database.DatabaseManager;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PunishmentManager {

    private final StaffCore plugin;
    private final File file;
    private FileConfiguration data;
    private final DatabaseManager databaseManager;

    private final List<Punishment> punishments = new ArrayList<Punishment>();

    public PunishmentManager(StaffCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "punishments.yml");
        this.databaseManager = new DatabaseManager(plugin);
        loadData();
    }

    public void loadData() {
        // 1. If MySQL is enabled, load records from database
        if (databaseManager != null && databaseManager.isEnabled()) {
            punishments.clear();
            punishments.addAll(databaseManager.loadAllPunishments());
            return;
        }

        // 2. Local YAML fallback
        if (!file.exists()) {
            plugin.saveResource("punishments.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(file);
        punishments.clear();

        ConfigurationSection section = data.getConfigurationSection("punishments");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String targetUuidStr = section.getString(key + ".targetUUID");
                    UUID targetUUID = targetUuidStr != null ? UUID.fromString(targetUuidStr) : null;
                    String targetName = section.getString(key + ".targetName", "Unknown");
                    String targetIp = section.getString(key + ".targetIp", "");
                    String staffName = section.getString(key + ".staffName", "Console");
                    PunishmentType type = PunishmentType.valueOf(section.getString(key + ".type"));
                    String reason = section.getString(key + ".reason", "No reason specified");
                    long timestamp = section.getLong(key + ".timestamp");
                    long duration = section.getLong(key + ".duration");
                    long expireTime = section.getLong(key + ".expireTime");
                    boolean active = section.getBoolean(key + ".active");

                    punishments.add(new Punishment(id, targetUUID, targetName, targetIp, staffName, type, reason, timestamp, duration, expireTime, active));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load punishment: " + key);
                }
            }
        }
    }

    public void saveData() {
        if (databaseManager != null && databaseManager.isEnabled()) {
            return; // Managed in MySQL
        }

        data.set("punishments", null);
        for (Punishment p : punishments) {
            String key = "punishments." + p.getId().toString();
            data.set(key + ".targetUUID", p.getTargetUUID() != null ? p.getTargetUUID().toString() : null);
            data.set(key + ".targetName", p.getTargetName());
            data.set(key + ".targetIp", p.getTargetIp());
            data.set(key + ".staffName", p.getStaffName());
            data.set(key + ".type", p.getType().name());
            data.set(key + ".reason", p.getReason());
            data.set(key + ".timestamp", p.getTimestamp());
            data.set(key + ".duration", p.getDuration());
            data.set(key + ".expireTime", p.getExpireTime());
            data.set(key + ".active", p.isActive());
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save punishments.yml: " + e.getMessage());
        }
    }

    public Punishment createPunishment(UUID targetUUID, String targetName, String targetIp,
                                       String staffName, PunishmentType type, String reason,
                                       long duration, boolean silent) {
        long now = System.currentTimeMillis();
        long expireTime = duration > 0 ? now + duration : -1;
        Punishment p = new Punishment(UUID.randomUUID(), targetUUID, targetName, targetIp, staffName, type, reason, now, duration, expireTime, true);
        punishments.add(p);
        saveData();

        // Sync to MySQL if enabled
        if (databaseManager != null && databaseManager.isEnabled()) {
            databaseManager.insertPunishment(p);
        }

        broadcastPunishment(p, silent);

        // Kick online players if banned
        if (type == PunishmentType.BAN || type == PunishmentType.TEMPBAN ||
            type == PunishmentType.IP_BAN || type == PunishmentType.TEMP_IP_BAN) {
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : Bukkit.getPlayer(targetName);
            if (target != null && target.isOnline()) {
                final Player kickTarget = target;
                final String kickMsg = getBanKickMessage(p);
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        kickTarget.kickPlayer(kickMsg);
                    }
                });
            }
        }

        // Warn target in chat if warned
        if (type == PunishmentType.WARN) {
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : Bukkit.getPlayer(targetName);
            if (target != null && target.isOnline()) {
                List<String> warnLines = plugin.getMessageManager().getMessageList("warning.received");
                for (String line : warnLines) {
                    target.sendMessage(line.replace("%staff%", staffName).replace("%reason%", reason));
                }
            }
        }

        return p;
    }

    public boolean unban(UUID targetUUID) {
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMPBAN)) {
                p.setActive(false);
                changed = true;
                if (databaseManager != null && databaseManager.isEnabled()) {
                    databaseManager.updateActiveStatus(p.getId(), false);
                }
            }
        }
        if (changed) saveData();
        return changed;
    }

    public boolean unbanIp(String ip) {
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetIp() != null && p.getTargetIp().equalsIgnoreCase(ip) && p.isActive() &&
                (p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN)) {
                p.setActive(false);
                changed = true;
                if (databaseManager != null && databaseManager.isEnabled()) {
                    databaseManager.updateActiveStatus(p.getId(), false);
                }
            }
        }
        if (changed) saveData();
        return changed;
    }

    public boolean unmute(UUID targetUUID) {
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.MUTE || p.getType() == PunishmentType.TEMP_MUTE)) {
                p.setActive(false);
                changed = true;
                if (databaseManager != null && databaseManager.isEnabled()) {
                    databaseManager.updateActiveStatus(p.getId(), false);
                }
            }
        }
        if (changed) saveData();
        return changed;
    }

    public Punishment getActiveBan(UUID targetUUID) {
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMPBAN)) {
                if (p.isExpired()) {
                    p.setActive(false);
                    saveData();
                    if (databaseManager != null && databaseManager.isEnabled()) {
                        databaseManager.updateActiveStatus(p.getId(), false);
                    }
                    continue;
                }
                return p;
            }
        }
        return null;
    }

    public Punishment getActiveIpBan(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        for (Punishment p : punishments) {
            if (p.getTargetIp() != null && p.getTargetIp().equalsIgnoreCase(ip) && p.isActive() &&
                (p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN)) {
                if (p.isExpired()) {
                    p.setActive(false);
                    saveData();
                    if (databaseManager != null && databaseManager.isEnabled()) {
                        databaseManager.updateActiveStatus(p.getId(), false);
                    }
                    continue;
                }
                return p;
            }
        }
        return null;
    }

    public Punishment getActiveMute(UUID targetUUID) {
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.MUTE || p.getType() == PunishmentType.TEMP_MUTE)) {
                if (p.isExpired()) {
                    p.setActive(false);
                    saveData();
                    if (databaseManager != null && databaseManager.isEnabled()) {
                        databaseManager.updateActiveStatus(p.getId(), false);
                    }
                    continue;
                }
                return p;
            }
        }
        return null;
    }

    public int getBanCount(UUID targetUUID) {
        int count = 0;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) &&
                (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMPBAN ||
                 p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN)) {
                count++;
            }
        }
        return count;
    }

    public int getMuteCount(UUID targetUUID) {
        int count = 0;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) &&
                (p.getType() == PunishmentType.MUTE || p.getType() == PunishmentType.TEMP_MUTE)) {
                count++;
            }
        }
        return count;
    }

    public int getWarnCount(UUID targetUUID) {
        int count = 0;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.getType() == PunishmentType.WARN) {
                count++;
            }
        }
        return count;
    }

    public List<Punishment> getAllPunishments(UUID targetUUID) {
        List<Punishment> list = new ArrayList<Punishment>();
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID)) {
                list.add(p);
            }
        }
        return list;
    }

    public String getBanKickMessage(Punishment p) {
        String screenKey = "screens.permanent-ban";
        if (p.getType() == PunishmentType.TEMPBAN) screenKey = "screens.temporary-ban";
        else if (p.getType() == PunishmentType.IP_BAN) screenKey = "screens.permanent-ipban";
        else if (p.getType() == PunishmentType.TEMP_IP_BAN) screenKey = "screens.temporary-ipban";

        List<String> lines = plugin.getMessageManager().getMessageList(screenKey);
        String remaining = p.getDuration() > 0 ? TimeUtil.formatRemaining(p.getExpireTime()) : "Permanent";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i)
                    .replace("%target%", p.getTargetName())
                    .replace("%staff%", p.getStaffName())
                    .replace("%reason%", p.getReason())
                    .replace("%remaining%", remaining);
            sb.append(line);
            if (i < lines.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    public void broadcastPunishment(Punishment p, boolean silent) {
        String durationStr = p.getDuration() > 0 ? TimeUtil.formatDuration(p.getDuration()) : "Permanent";

        if (silent) {
            String formatKey = "broadcasts.silent." + p.getType().name().toLowerCase().replace("_", "");
            String msg = plugin.getConfig().getString(formatKey);
            if (msg == null) msg = plugin.getConfig().getString("broadcasts.silent.ban", "&8[&cSilent&8] &7%staff% punished %target% for: %reason%");

            String formatted = plugin.getMessageManager().color(msg
                    .replace("%target%", p.getTargetName())
                    .replace("%staff%", p.getStaffName())
                    .replace("%reason%", p.getReason())
                    .replace("%duration%", durationStr)
                    .replace("%type%", p.getType().name()));

            Bukkit.getConsoleSender().sendMessage(formatted);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("staffcore.broadcast.silent") || player.hasPermission("staffcore.staff")) {
                    player.sendMessage(formatted);
                }
            }
        } else {
            String formatKey = "broadcasts.public." + p.getType().name().toLowerCase().replace("_", "");
            String msg = plugin.getConfig().getString(formatKey);
            if (msg == null) msg = plugin.getConfig().getString("broadcasts.public.ban", "&c&lPUNISH &8» &f%target% was punished by &f%staff% for: %reason%");

            String formatted = plugin.getMessageManager().color(msg
                    .replace("%target%", p.getTargetName())
                    .replace("%staff%", p.getStaffName())
                    .replace("%reason%", p.getReason())
                    .replace("%duration%", durationStr));

            Bukkit.broadcastMessage(formatted);
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}