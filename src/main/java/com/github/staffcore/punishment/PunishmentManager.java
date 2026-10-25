package com.github.staffcore.punishment;

import com.github.staffcore.StaffCore;
import com.github.staffcore.database.DatabaseManager;
import com.github.staffcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PunishmentManager {

    private final StaffCore plugin;
    private final File file;
    private FileConfiguration data;
    private DatabaseManager databaseManager;
    private BukkitTask jailCheckTask;

    private final List<Punishment> punishments = new ArrayList<>();
    private final Map<UUID, String> ipHistory = new HashMap<>();
    private final Map<UUID, String> uuidToName = new HashMap<>();
    private final Map<String, String> nameToIpHistory = new HashMap<>();

    // Jail storage: jailName (lowercase) -> Location
    private final Map<String, Location> jails = new HashMap<>();
    // Jailed player UUID -> jailName
    private final Map<UUID, String> playerJailMap = new HashMap<>();
    // Jailed player UUID -> previous location before being jailed
    private final Map<UUID, Location> previousLocations = new HashMap<>();

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
        startJailExpiryTask();
    }

    private void startJailExpiryTask() {
        if (jailCheckTask != null) {
            jailCheckTask.cancel();
        }
        jailCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                UUID uuid = online.getUniqueId();
                if (playerJailMap.containsKey(uuid)) {
                    // Automatically unjails if expired
                    getActiveJail(uuid);
                }
            }
        }, 20L, 20L);
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
        jails.clear();
        playerJailMap.clear();
        previousLocations.clear();

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

        // 3. Load saved jails
        ConfigurationSection jailSection = data.getConfigurationSection("jails");
        if (jailSection != null) {
            for (String jailName : jailSection.getKeys(false)) {
                Location loc = deserializeLocation(jailSection.getConfigurationSection(jailName));
                if (loc != null) {
                    jails.put(jailName.toLowerCase(), loc);
                }
            }
        }

        // 4. Load active player jail assignments
        ConfigurationSection activeJailSec = data.getConfigurationSection("active-jails");
        if (activeJailSec != null) {
            for (String uuidStr : activeJailSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String jailName = activeJailSec.getString(uuidStr + ".jail", "");
                    if (!jailName.isEmpty()) {
                        playerJailMap.put(uuid, jailName.toLowerCase());
                    }
                    Location prev = deserializeLocation(activeJailSec.getConfigurationSection(uuidStr + ".prev"));
                    if (prev != null) {
                        previousLocations.put(uuid, prev);
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

        data.set("jails", null);
        for (Map.Entry<String, Location> entry : jails.entrySet()) {
            serializeLocation("jails." + entry.getKey(), entry.getValue());
        }

        data.set("active-jails", null);
        for (Map.Entry<UUID, String> entry : playerJailMap.entrySet()) {
            String base = "active-jails." + entry.getKey().toString();
            data.set(base + ".jail", entry.getValue());
            Location prev = previousLocations.get(entry.getKey());
            if (prev != null) {
                serializeLocation(base + ".prev", prev);
            }
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save punishments.yml: " + e.getMessage());
        }
    }

    private void serializeLocation(String path, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", (double) loc.getYaw());
        data.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection sec) {
        if (sec == null) return null;
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = sec.getDouble("x");
        double y = sec.getDouble("y");
        double z = sec.getDouble("z");
        float yaw = (float) sec.getDouble("yaw");
        float pitch = (float) sec.getDouble("pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    // =========================================================================
    //                          JAIL MANAGEMENT
    // =========================================================================

    public void setJailLocation(String jailName, Location location) {
        if (jailName == null || location == null) return;
        jails.put(jailName.toLowerCase(), location.clone());
        saveData();
    }

    public boolean deleteJail(String jailName) {
        if (jailName == null) return false;
        boolean removed = jails.remove(jailName.toLowerCase()) != null;
        if (removed) saveData();
        return removed;
    }

    public Location getJailLocation(String jailName) {
        if (jailName == null) return null;
        Location loc = jails.get(jailName.toLowerCase());
        return loc != null ? loc.clone() : null;
    }

    public Set<String> getJailNames() {
        return Collections.unmodifiableSet(jails.keySet());
    }

    public Location getAssignedJailLocation(UUID targetUUID) {
        if (targetUUID == null) return null;
        String jailName = playerJailMap.get(targetUUID);
        if (jailName != null) {
            Location loc = getJailLocation(jailName);
            if (loc != null) return loc;
        }
        if (!jails.isEmpty()) {
            return jails.values().iterator().next().clone();
        }
        return null;
    }

    public Punishment jailPlayer(UUID targetUUID, String targetName, String jailName,
                                 String staffName, String reason, long duration, boolean silent) {
        String cleanJail = jailName.toLowerCase();
        Location jailLoc = getJailLocation(cleanJail);

        // Deactivate any previous active jail records for this player
        for (Punishment existing : punishments) {
            if (existing.getTargetUUID() != null && existing.getTargetUUID().equals(targetUUID) && existing.isActive() &&
                (existing.getType() == PunishmentType.JAIL || existing.getType() == PunishmentType.TEMP_JAIL)) {
                existing.setActive(false);
            }
        }

        Player online = targetUUID != null ? Bukkit.getPlayer(targetUUID) : Bukkit.getPlayerExact(targetName);
        if (online != null && online.isOnline()) {
            if (!previousLocations.containsKey(online.getUniqueId())) {
                previousLocations.put(online.getUniqueId(), online.getLocation().clone());
            }
            if (jailLoc != null) {
                online.teleport(jailLoc);
            }
            String timeStr = duration > 0 ? TimeUtil.formatDuration(duration) : "Permanent";
            online.sendMessage(plugin.getMessageManager().color("&c&m--------------------------------------------------"));
            online.sendMessage(plugin.getMessageManager().color("&c&lYOU HAVE BEEN JAILED! &7(Jail: &e" + cleanJail + "&7)"));
            online.sendMessage(plugin.getMessageManager().color("&7Staff: &f" + staffName));
            online.sendMessage(plugin.getMessageManager().color("&7Reason: &f" + reason));
            online.sendMessage(plugin.getMessageManager().color("&7Duration: &e" + timeStr));
            online.sendMessage(plugin.getMessageManager().color("&c&m--------------------------------------------------"));
        }

        if (targetUUID != null) {
            playerJailMap.put(targetUUID, cleanJail);
        }

        String ip = (online != null && online.getAddress() != null && online.getAddress().getAddress() != null)
                ? online.getAddress().getAddress().getHostAddress() : getLastKnownIp(targetUUID);
        PunishmentType type = duration > 0 ? PunishmentType.TEMP_JAIL : PunishmentType.JAIL;
        return createPunishment(targetUUID, targetName, ip != null ? ip : "", staffName, type, reason, duration, silent);
    }

    public boolean unjail(UUID targetUUID) {
        if (targetUUID == null) return false;
        boolean changed = false;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.JAIL || p.getType() == PunishmentType.TEMP_JAIL)) {
                p.setActive(false);
                changed = true;
            }
        }
        if (playerJailMap.remove(targetUUID) != null) {
            changed = true;
        }
        Location prev = previousLocations.remove(targetUUID);
        if (changed) {
            saveData();
            Player online = Bukkit.getPlayer(targetUUID);
            if (online != null && online.isOnline()) {
                if (prev != null && prev.getWorld() != null) {
                    online.teleport(prev);
                } else if (!Bukkit.getWorlds().isEmpty()) {
                    online.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                online.sendMessage(plugin.getMessageManager().color("&8[&cStaffCore&8] &aYou have been released from jail!"));
            }
        }
        return changed;
    }

    public Punishment getActiveJail(UUID targetUUID) {
        if (targetUUID == null) return null;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) && p.isActive() &&
                (p.getType() == PunishmentType.JAIL || p.getType() == PunishmentType.TEMP_JAIL)) {
                if (p.isExpired()) {
                    unjail(targetUUID);
                    return null;
                }
                return p;
            }
        }
        return null;
    }

    public int getJailCount(UUID targetUUID) {
        if (targetUUID == null) return 0;
        int count = 0;
        for (Punishment p : punishments) {
            if (p.getTargetUUID() != null && p.getTargetUUID().equals(targetUUID) &&
                (p.getType() == PunishmentType.JAIL || p.getType() == PunishmentType.TEMP_JAIL)) {
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    //                        IP & PUNISHMENT METHODS
    // =========================================================================

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

    public List<String> getAccountsByIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) return Collections.emptyList();
        String cleanIp = ip.trim();
        Set<String> accounts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

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

        for (Map.Entry<String, String> entry : nameToIpHistory.entrySet()) {
            if (cleanIp.equalsIgnoreCase(entry.getValue())) {
                accounts.add(entry.getKey());
            }
        }

        for (Punishment p : punishments) {
            if (p.getTargetIp() != null && cleanIp.equalsIgnoreCase(p.getTargetIp())) {
                if (p.getTargetName() != null && !p.getTargetName().isEmpty() && !p.getTargetName().equalsIgnoreCase("Unknown")) {
                    accounts.add(p.getTargetName());
                }
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getAddress() != null && online.getAddress().getAddress() != null) {
                if (cleanIp.equalsIgnoreCase(online.getAddress().getAddress().getHostAddress())) {
                    accounts.add(online.getName());
                }
            }
        }

        return new ArrayList<>(accounts);
    }

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

    public List<String> getBannedPlayerNames() {
        Set<String> banned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

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

        return new ArrayList<>(banned);
    }

    public List<String> getIpBannedNamesOrIps() {
        Set<String> ipBanned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

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
            if (msg == null) {
                if (p.getType() == PunishmentType.JAIL) {
                    msg = "&8[&cSilent&8] &7%staff% permanently jailed &e%target% &7for: &f%reason%";
                } else if (p.getType() == PunishmentType.TEMP_JAIL) {
                    msg = "&8[&cSilent&8] &7%staff% temp-jailed &e%target% &7for &e%duration% &7for: &f%reason%";
                } else {
                    msg = plugin.getConfig().getString("broadcasts.silent.ban", "&8[&cSilent&8] &7%staff% punished %target% for: %reason%");
                }
            }

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
            if (msg == null) {
                if (p.getType() == PunishmentType.JAIL) {
                    msg = "&c&lJAIL &8» &f%target% &7was permanently jailed by &f%staff% &7for: &e%reason%";
                } else if (p.getType() == PunishmentType.TEMP_JAIL) {
                    msg = "&c&lJAIL &8» &f%target% &7was jailed for &e%duration% &7by &f%staff% &7for: &e%reason%";
                } else {
                    msg = plugin.getConfig().getString("broadcasts.public.ban", "&c&lPUNISH &8» &f%target% was punished by &f%staff% for: %reason%");
                }
            }

            String formatted = plugin.getMessageManager().color(msg
                    .replace("%target%", p.getTargetName())
                    .replace("%staff%", p.getStaffName())
                    .replace("%reason%", p.getReason())
                    .replace("%duration%", durationStr));

            Bukkit.broadcastMessage(formatted);
        }
    }
}