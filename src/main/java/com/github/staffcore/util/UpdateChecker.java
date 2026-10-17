package com.github.staffcore.util;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UpdateChecker implements Listener {

    private final StaffCore plugin;
    private final String currentVersion;
    private final String repo;
    private String updateUrl = "https://github.com/ItsFriendlyy/StaffCore/releases";
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(StaffCore plugin) {
        this(plugin, "ItsFriendlyy/StaffCore", "https://github.com/ItsFriendlyy/StaffCore/releases");
    }

    public UpdateChecker(StaffCore plugin, String repo, String updateUrl) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.repo = (repo != null && !repo.trim().isEmpty()) ? repo.trim() : "ItsFriendlyy/StaffCore";
        if (updateUrl != null && !updateUrl.trim().isEmpty()) {
            this.updateUrl = updateUrl.trim();
        } else {
            this.updateUrl = "https://github.com/ItsFriendlyy/StaffCore/releases";
        }
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = URI.create("https://github.com/ItsFriendlyy/StaffCore/releases").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "StaffCore-UpdateChecker");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setInstanceFollowRedirects(true);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Check for GitHub release tags in HTML (/releases/tag/v1.0.2)
                        if (line.contains("/releases/tag/")) {
                            int tagIndex = line.indexOf("/releases/tag/");
                            int start = tagIndex + "/releases/tag/".length();
                            int end = line.indexOf("\"", start);
                            if (end != -1) {
                                String tag = line.substring(start, end).trim();
                                if (tag.contains("/")) {
                                    tag = tag.substring(0, tag.indexOf("/"));
                                }
                                parseReleaseTag(tag);
                                break;
                            }
                        }
                        // Fallback check if response returns JSON format
                        if (line.contains("\"tag_name\"")) {
                            int idx = line.indexOf("\"tag_name\"");
                            int q1 = line.indexOf("\"", idx + 10);
                            int q2 = line.indexOf("\"", q1 + 1);
                            if (q1 != -1 && q2 != -1) {
                                String tag = line.substring(q1 + 1, q2).trim();
                                parseReleaseTag(tag);
                                break;
                            }
                        }
                    }
                    reader.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for StaffCore updates: " + e.getMessage());
            }
        });
    }

    private void parseReleaseTag(String tag) {
        this.latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;

        if (!this.latestVersion.equalsIgnoreCase(this.currentVersion)) {
            this.updateAvailable = true;
            plugin.getLogger().warning("=================================================");
            plugin.getLogger().warning("A new version of StaffCore is available: " + tag);
            plugin.getLogger().warning("Your current version: v" + currentVersion);
            plugin.getLogger().warning("Download here: " + updateUrl);
            plugin.getLogger().warning("=================================================");
        } else {
            plugin.getLogger().info("StaffCore is up to date (v" + currentVersion + ").");
        }
    }

    /**
     * Called by PunishmentListener or join handlers to alert staff members when an update is available.
     */
    public void notifyPlayer(Player player) {
        if (!updateAvailable) return;

        if (player.isOp() || player.hasPermission("staffcore.staff") || player.hasPermission("staffcore.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&cStaffCore&8] &eA new version &a(" + (latestVersion != null ? "v" + latestVersion : "update") + ") &eis available!"));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&cStaffCore&8] &eDownload here: &b" + updateUrl));
            }, 40L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        notifyPlayer(event.getPlayer());
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }
}