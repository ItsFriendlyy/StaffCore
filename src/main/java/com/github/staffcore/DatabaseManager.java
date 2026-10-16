package com.github.staffcore.database;

import com.github.staffcore.StaffCore;
import com.github.staffcore.punishment.Punishment;
import com.github.staffcore.punishment.PunishmentType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private final StaffCore plugin;
    private Connection connection;
    private final String tableName;
    private final boolean enabled;

    public DatabaseManager(StaffCore plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("database.enabled", false);
        this.tableName = plugin.getConfig().getString("database.table-prefix", "staffcore_") + "punishments";

        if (enabled) {
            connect();
            createTable();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void connect() {
        if (!enabled) return;
        try {
            if (connection != null && !connection.isClosed()) return;

            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String db = plugin.getConfig().getString("database.database", "staffcore");
            String user = plugin.getConfig().getString("database.username", "root");
            String pass = plugin.getConfig().getString("database.password", "");
            boolean ssl = plugin.getConfig().getBoolean("database.use-ssl", false);

            String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl + "&autoReconnect=true";
            connection = DriverManager.getConnection(url, user, pass);
            plugin.getLogger().info("Successfully connected to MySQL network database!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to MySQL database: " + e.getMessage());
        }
    }

    private void createTable() {
        if (!enabled || connection == null) return;
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "target_uuid VARCHAR(36), " +
                "target_name VARCHAR(16), " +
                "target_ip VARCHAR(45), " +
                "staff_name VARCHAR(16), " +
                "type VARCHAR(20), " +
                "reason TEXT, " +
                "timestamp BIGINT, " +
                "duration BIGINT, " +
                "expire_time BIGINT, " +
                "active BOOLEAN" +
                ");";
        try {
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            stmt.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database table: " + e.getMessage());
        }
    }

    public synchronized void insertPunishment(Punishment p) {
        if (!enabled) return;
        connect();
        String sql = "INSERT INTO " + tableName + " (id, target_uuid, target_name, target_ip, staff_name, type, reason, timestamp, duration, expire_time, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, p.getId().toString());
            ps.setString(2, p.getTargetUUID() != null ? p.getTargetUUID().toString() : null);
            ps.setString(3, p.getTargetName());
            ps.setString(4, p.getTargetIp());
            ps.setString(5, p.getStaffName());
            ps.setString(6, p.getType().name());
            ps.setString(7, p.getReason());
            ps.setLong(8, p.getTimestamp());
            ps.setLong(9, p.getDuration());
            ps.setLong(10, p.getExpireTime());
            ps.setBoolean(11, p.isActive());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not insert punishment to MySQL: " + e.getMessage());
        }
    }

    public synchronized void updateActiveStatus(UUID punishmentId, boolean active) {
        if (!enabled) return;
        connect();
        String sql = "UPDATE " + tableName + " SET active = ? WHERE id = ?;";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setBoolean(1, active);
            ps.setString(2, punishmentId.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not update punishment status in MySQL: " + e.getMessage());
        }
    }

    public synchronized List<Punishment> loadAllPunishments() {
        List<Punishment> list = new ArrayList<Punishment>();
        if (!enabled) return list;
        connect();
        String sql = "SELECT * FROM " + tableName + ";";
        try {
            Statement s = connection.createStatement();
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String uStr = rs.getString("target_uuid");
                UUID targetUUID = uStr != null ? UUID.fromString(uStr) : null;
                String targetName = rs.getString("target_name");
                String targetIp = rs.getString("target_ip");
                String staffName = rs.getString("staff_name");
                PunishmentType type = PunishmentType.valueOf(rs.getString("type"));
                String reason = rs.getString("reason");
                long timestamp = rs.getLong("timestamp");
                long duration = rs.getLong("duration");
                long expireTime = rs.getLong("expire_time");
                boolean active = rs.getBoolean("active");

                list.add(new Punishment(id, targetUUID, targetName, targetIp, staffName, type, reason, timestamp, duration, expireTime, active));
            }
            rs.close();
            s.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load punishments from MySQL: " + e.getMessage());
        }
        return list;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }
}