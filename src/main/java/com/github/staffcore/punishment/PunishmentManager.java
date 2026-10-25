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
    private DatabaseManager databaseManager;

    private final List<Punishment> punishments = new ArrayList<>();
    private final Map<UUID, String> ipHistory = new HashMap<>();
    private final Map<UUID, String> uuidToName = new HashMap<>();
    private final Map<String, String> nameToIpHistory = new HashMap<>();

    public PunishmentManager(StaffCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "punishments.yml");

        if (plugin.getConfig().getBoolean("database.mysql-enabled", false)) {
            try {
                this.databaseManager = new DatabaseManager(plugin);
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not initialize DatabaseManager: " + t.getMessage());
            }
        }

        loadData();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void loadData() {
        if (!file.exists()) {
            plugin.saveResource("punishments.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(file);
        punishments.clear();
        ipHistory.clear();
        uuidToName.clear();
        nameToIpHistory.clear();

        // 1. Load punishments from punishments.yml
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

        // 2. Load IP history & account names
        ConfigurationSection ipSection = data.getConfigurationSection("ip-history");
        if (ipSection != null) {
            for (String key : ipSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String ip = ipSection.getString(key + ".ip", "");
                    String name = ipSection.getString(key + ".name", "");
                    if (!ip.isEmpty()) {
                        ipHistory.put(uuid, ip);
                        if (!name.isEmpty()) {
                            uuidToName.put(uuid, name);
                            nameToIpHistory.put(name.toLowerCase(), ip);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Also cache any currently online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getAddress() != null && p.getAddress().getAddress() != null) {
                cachePlayerIp(p.getUniqueId(), p.getName(), p.getAddress().getAddress().getHostAddress());
            }
        }
    }

    public void saveData() {
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

        for (Map.Entry<UUID, String> entry : ipHistory.entrySet()) {
            UUID uuid = entry.getKey();
            String key = "ip-history." + uuid.toString();
            data.set(key + ".ip", entry.getValue());
            String name = uuidToName.get(uuid);
            if (name != null && !name.isEmpty()) {
                data.set(key + ".name", name);
            }
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save punishments.yml: " + e.getMessage());
        }
    }

    public void cachePlayerIp(UUID uuid, String name, String ip) {
        if (uuid == null || ip == null || ip.isEmpty()) return;
        ipHistory.put(uuid, ip);
        if (name != null && !name.isEmpty()) {
            uuidToName.put(uuid, name);
            nameToIpHistory.put(name.toLowerCase(), ip);
        }
        saveData();
    }

    public String getLastKnownIp(UUID uuid) {
        if (uuid == null) return null;
        return ipHistory.get(uuid);
    }

    public String getLastKnownIpByName(String name) {
        if (name == null) return null;
        return nameToIpHistory.get(name.toLowerCase());
    }

    /**
     * Returns all known account names that have logged in from the specified IP address.
     */
    public List<String> getAccountsByIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) return Collections.emptyList();
        String cleanIp = ip.trim();
        Set<String> accounts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1. Check IP history map
        for (Map.Entry<UUID, String> entry : ipHistory.entrySet()) {
            if (cleanIp.equalsIgnoreCase(entry.getValue())) {
                String name = uuidToName.get(entry.getKey());
                if (name != null && !name.isEmpty()) {
                    accounts.add(name);
                } else {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    if (op != null && op.getName() != null) {
                        accounts.add(op.getName());
                    }
                }
            }
        }

        // 2. Check nameToIpHistory map
        for (Map.Entry<String, String> entry : nameToIpHistory.entrySet()) {
            if (cleanIp.equalsIgnoreCase(entry.getValue())) {
                accounts.add(entry.getKey());
            }
        }

        // 3. Check punishment records
        for (Punishment p : punishments) {
            if (p.getTargetIp() != null && cleanIp.equalsIgnoreCase(p.getTargetIp())) {
                if (p.getTargetName() != null && !p.getTargetName().isEmpty() && !p.getTargetName().equalsIgnoreCase("Unknown")) {
                    accounts.add(p.getTargetName());
                }
            }
        }

        // 4. Check currently online players
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getAddress() != null && online.getAddress().getAddress() != null) {
                if (cleanIp.equalsIgnoreCase(online.getAddress().getAddress().getHostAddress())) {
                    accounts.add(online.getName());
                }
            }
        }

        return new ArrayList<>(accounts);
    }

    /**
     * Returns all punishments recorded for a specific player UUID (used by CheckGui)
     */
    public List<Punishment> getAllPunishments(UUID targetUUID) {
        if (targetUUID == null) return Collections.emptyList();
        List<Punishment> list = new ArrayList<>();
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID)) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Returns all punishments across the entire server
     */
    public List<Punishment> getAllPunishments() {
        return Collections.unmodifiableList(punishments);
    }

    public Punishment createPunishment(UUID targetUUID, String targetName, String targetIp,
                                       String staffName, PunishmentType type, String reason,
                                       long duration, boolean silent) {
        long now = System.currentTimeMillis();
        long expireTime = duration > 0 ? now + duration : -1;
        Punishment p = new Punishment(UUID.randomUUID(), targetUUID, targetName, targetIp, staffName, type, reason, now, duration, expireTime, true);
        punishments.add(p);
        saveData();

        broadcastPunishment(p, silent);

        // 1. UUID Ban: kick target player
        if (type == PunishmentType.BAN || type == PunishmentType.TEMPBAN) {
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : Bukkit.getPlayer(targetName);
            if (target != null && target.isOnline()) {
                final Player kickTarget = target;
                Bukkit.getScheduler().runTask(plugin, () -> kickTarget.kickPlayer(getBanKickMessage(p)));
            }
        }

        // 2. IP Ban: kick ALL players sharing that IP address
        if (type == PunishmentType.IP_BAN || type == PunishmentType.TEMP_IP_BAN) {
            final String banIp = targetIp;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getAddress() != null) {
                    String playerIp = player.getAddress().getAddress().getHostAddress();
                    if (playerIp.equalsIgnoreCase(banIp) || (targetUUID != null && player.getUniqueId().equals(targetUUID))) {
                        final Player kickTarget = player;
                        Bukkit.getScheduler().runTask(plugin, () -> kickTarget.kickPlayer(getBanKickMessage(p)));
                    }
                }
            }
        }

        // 3. Warn & Auto-Ban Check
        if (type == PunishmentType.WARN) {
            int maxWarnings = plugin.getConfig().getInt("warning.max-warnings", 3);
            int activeWarns = targetUUID != null ? getActiveWarnCount(targetUUID) : 1;

            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : Bukkit.getPlayer(targetName);
            if (target != null && target.isOnline()) {
                List<String> warnLines = plugin.getMessageManager().getMessageList("warning.received");
                for (String line : warnLines) {
                    target.sendMessage(line
                            .replace("%staff%", staffName)
                            .replace("%reason%", reason)
                            .replace("%count%", String.valueOf(activeWarns))
                            .replace("%max%", String.valueOf(maxWarnings)));
                }
            }

            // Auto-ban when reaching threshold (default 3 warnings), using the final warning's reason as the ban reason
            if (maxWarnings > 0 && targetUUID != null && activeWarns >= maxWarnings) {
                clearActiveWarnings(targetUUID);
                String resolvedIp = (targetIp != null && !targetIp.isEmpty()) ? targetIp : getLastKnownIp(targetUUID);
                createPunishment(targetUUID, targetName, resolvedIp != null ? resolvedIp : "", staffName, PunishmentType.BAN, reason, -1, silent);
            }
        }

        return p;
    }

    public int getActiveWarnCount(UUID targetUUID) {
        if (targetUUID == null) return 0;
        int count = 0;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() && p.getType() == PunishmentType.WARN) {
                count++;
            }
        }
        return count;
    }

    public void clearActiveWarnings(UUID targetUUID) {
        if (targetUUID == null) return;
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() && p.getType() == PunishmentType.WARN) {
                p.setActive(false);
                changed = true;
            }
        }
        if (changed) saveData();
    }

    public boolean unban(UUID targetUUID) {
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMPBAN)) {
                p.setActive(false);
                changed = true;
            }
        }
        if (changed) saveData();
        return changed;
    }

    /**
     * Unbans an IP address by the player's In-Game Name (IGN) or direct IP.
     */
    public String unbanIpByIgn(String ignOrIp) {
        if (ignOrIp == null || ignOrIp.trim().isEmpty()) return null;
        String input = ignOrIp.trim();
        String unbannedIp = null;
        boolean changed = false;

        for (Punishment p : punishments) {
            if (!p.isActive()) continue;
            if (p.getType() != PunishmentType.IP_BAN && p.getType() != PunishmentType.TEMP_IP_BAN) continue;

            boolean matchName = p.getTargetName() != null && p.getTargetName().equalsIgnoreCase(input);
            boolean matchIp = p.getTargetIp() != null && p.getTargetIp().equalsIgnoreCase(input);

            if (matchName || matchIp) {
                p.setActive(false);
                changed = true;
                if (p.getTargetIp() != null && !p.getTargetIp().isEmpty()) {
                    unbannedIp = p.getTargetIp();
                }
            }
        }

        if (!changed) {
            String historyIp = getLastKnownIpByName(input);
            if (historyIp != null && !historyIp.isEmpty()) {
                for (Punishment p : punishments) {
                    if (p.isActive() && (p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN)) {
                        if (p.getTargetIp() != null && p.getTargetIp().equalsIgnoreCase(historyIp)) {
                            p.setActive(false);
                            changed = true;
                            unbannedIp = historyIp;
                        }
                    }
                }
            }
        }

        if (unbannedIp != null) {
            try {
                Bukkit.unbanIP(unbannedIp);
            } catch (Exception ignored) {}
        } else if (input.contains(".") || input.contains(":")) {
            try {
                Bukkit.unbanIP(input);
            } catch (Exception ignored) {}
            unbannedIp = input;
        }

        if (changed) saveData();
        return unbannedIp;
    }

    public boolean unbanIp(String input) {
        return unbanIpByIgn(input) != null;
    }

    public boolean unmute(UUID targetUUID) {
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.MUTE || p.getType() == PunishmentType.TEMP_MUTE)) {
                p.setActive(false);
                changed = true;
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
                    return null;
                }
                return p;
            }
        }
        return null;
    }

    public Punishment getActiveIpBan(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        for (Punishment p : punishments) {
            if (p.isActive() && (p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN)) {
                if (p.getTargetIp() != null && p.getTargetIp().equalsIgnoreCase(ip)) {
                    if (p.isExpired()) {
                        p.setActive(false);
                        saveData();
                        return null;
                    }
                    return p;
                }
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
                    return null;
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

    /**
     * Collects all currently banned player names from StaffCore and Bukkit's ban list.
     */
    public List<String> getBannedPlayerNames() {
        Set<String> banned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1. StaffCore internal active bans
        for (Punishment p : punishments) {
            if (p.isActive() && (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMPBAN)) {
                if (p.isExpired()) {
                    p.setActive(false);
                    continue;
                }
                if (p.getTargetName() != null && !p.getTargetName().equalsIgnoreCase("Unknown") && !p.getTargetName().isEmpty()) {
                    banned.add(p.getTargetName());
                }
            }
        }

        // 2. Bukkit native BanList (BanList.Type.NAME)
        try {
            for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
                if (entry != null && entry.getTarget() != null) {
                    banned.add(entry.getTarget());
                }
            }
        } catch (Throwable ignored) {}

        // 3. Bukkit getBannedPlayers() for older Spigot compatibility
        try {
            for (org.bukkit.OfflinePlayer op : Bukkit.getBannedPlayers()) {
                if (op != null && op.getName() != null) {
                    banned.add(op.getName());
                }
            }
        } catch (Throwable ignored) {}

        return new ArrayList<>(banned);
    }

    /**
     * Collects all currently IP-banned player names and raw IP addresses.
     */
    public List<String> getIpBannedNamesOrIps() {
        Set<String> ipBanned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // 1. StaffCore internal active IP bans
        for (Punishment p : punishments) {
            if (p.isActive() && (p.getType() == PunishmentType.IP_BAN || p.getType() == PunishmentType.TEMP_IP_BAN)) {
                if (p.isExpired()) {
                    p.setActive(false);
                    continue;
                }
                if (p.getTargetName() != null && !p.getTargetName().equalsIgnoreCase("Unknown") && !p.getTargetName().isEmpty()) {
                    ipBanned.add(p.getTargetName());
                }
                if (p.getTargetIp() != null && !p.getTargetIp().isEmpty()) {
                    ipBanned.add(p.getTargetIp());
                }
            }
        }

        // 2. Bukkit IP ban list
        try {
            for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.IP).getBanEntries()) {
                if (entry != null && entry.getTarget() != null) {
                    ipBanned.add(entry.getTarget());
                }
            }
        } catch (Throwable ignored) {}

        return new ArrayList<>(ipBanned);
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
}