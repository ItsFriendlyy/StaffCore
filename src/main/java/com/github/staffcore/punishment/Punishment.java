package com.github.staffcore.punishment;

import java.util.UUID;

public class Punishment {

    private final UUID id;
    private final UUID targetUUID;
    private final String targetName;
    private final String targetIp;
    private final String staffName;
    private final PunishmentType type;
    private final String reason;
    private final long timestamp;
    private final long duration; // -1 for permanent
    private final long expireTime; // -1 for permanent
    private boolean active;

    public Punishment(UUID id, UUID targetUUID, String targetName, String targetIp,
                      String staffName, PunishmentType type, String reason,
                      long timestamp, long duration, long expireTime, boolean active) {
        this.id = id;
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.targetIp = targetIp;
        this.staffName = staffName;
        this.type = type;
        this.reason = reason;
        this.timestamp = timestamp;
        this.duration = duration;
        this.expireTime = expireTime;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getTargetUUID() { return targetUUID; }
    public String getTargetName() { return targetName; }
    public String getTargetIp() { return targetIp; }
    public String getStaffName() { return staffName; }
    public PunishmentType getType() { return type; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    public long getDuration() { return duration; }
    public long getExpireTime() { return expireTime; }
    public boolean isActive() { return active; }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        if (duration <= 0 || expireTime <= 0) return false;
        return System.currentTimeMillis() >= expireTime;
    }
}
