package com.github.staffcore.util;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker implements Listener {

    private final StaffCore plugin;
    private final String currentVersion;
    private final String updateUrl = "https://github.com/ItsFriendlyy/StaffCore/releases";
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(StaffCore plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdates() {
        checkForUpdates(null);
    }

    public void checkForUpdates(CommandSender requester) {
        if (requester != null) {
            requester.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    "&8[&cStaffCore&8] &7Checking GitHub for updates..."));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean found = false;
            try {
                // Try 1: GitHub API (fast and direct)
                URL apiUrl = URI.create("https://api.github.com/repos/ItsFriendlyy/StaffCore/releases/latest").toURL();
                HttpURLConnection apiConn = (HttpURLConnection) apiUrl.openConnection();
                apiConn.setRequestMethod("GET");
                apiConn.setRequestProperty("User-Agent", "StaffCore-UpdateChecker");
                apiConn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                apiConn.setConnectTimeout(5000);
                apiConn.setReadTimeout(5000);

                if (apiConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(apiConn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("\"tag_name\"")) {
                            int idx = line.indexOf("\"tag_name\"");
                            int q1 = line.indexOf("\"", idx + 10);
                            int q2 = line.indexOf("\"", q1 + 1);
                            if (q1 != -1 && q2 != -1) {
                                String rawTag = line.substring(q1 + 1, q2).trim();
                                if (evaluateReleaseTag(rawTag)) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }
                    reader.close();
                }
            } catch (Exception ignored) {}

            // Try 2: HTML Scraping Fallback
            if (!found) {
                try {
                    URL htmlUrl = URI.create("https://github.com/ItsFriendlyy/StaffCore/releases").toURL();
                    HttpURLConnection conn = (HttpURLConnection) htmlUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "StaffCore-UpdateChecker");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setInstanceFollowRedirects(true);

                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("/releases/tag/")) {
                                int tagIndex = line.indexOf("/releases/tag/");
                                int start = tagIndex + "/releases/tag/".length();
                                int end = line.indexOf("\"", start);
                                if (end != -1) {
                                    String rawTag = line.substring(start, end).trim();
                                    if (rawTag.contains("/")) {
                                        rawTag = rawTag.substring(0, rawTag.indexOf("/"));
                                    }
                                    if (evaluateReleaseTag(rawTag)) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                        reader.close();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not check for StaffCore updates: " + e.getMessage());
                }
            }

            // Sync back to main thread: instantly alert all online staff players
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (updateAvailable) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        sendUpdateAlert(player);
                    }
                    if (requester != null && !(requester instanceof Player)) {
                        requester.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                                "&8[&cStaffCore&8] &eUpdate available: &av" + latestVersion + " &e(Current: v" + currentVersion + ")"));
                    }
                } else if (requester != null) {
                    requester.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            "&8[&cStaffCore&8] &aStaffCore is up to date (v" + currentVersion + ")."));
                }
            });
        });
    }

    private boolean evaluateReleaseTag(String rawTag) {
        String versionDigits = extractVersionDigits(rawTag);
        if (versionDigits.isEmpty()) {
            return false;
        }

        this.latestVersion = versionDigits;
        if (isNewer(this.latestVersion, this.currentVersion)) {
            this.updateAvailable = true;
            plugin.getLogger().warning("=================================================");
            plugin.getLogger().warning("A new version of StaffCore is available: v" + latestVersion);
            plugin.getLogger().warning("Your current version: v" + currentVersion);
            plugin.getLogger().warning("Download here: " + updateUrl);
            plugin.getLogger().warning("=================================================");
        } else {
            this.updateAvailable = false;
            plugin.getLogger().info("StaffCore is up to date (v" + currentVersion + ").");
        }
        return true;
    }

    private String extractVersionDigits(String input) {
        if (input == null) return "";
        Matcher matcher = Pattern.compile("(\\d+(\\.\\d+)+)").matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private boolean isNewer(String remote, String current) {
        if (remote == null || current == null || remote.isEmpty() || current.isEmpty()) return false;
        String[] rParts = remote.split("\\.");
        String[] cParts = current.split("\\.");
        int length = Math.max(rParts.length, cParts.length);

        for (int i = 0; i < length; i++) {
            int r = i < rParts.length ? parseSafeInt(rParts[i]) : 0;
            int c = i < cParts.length ? parseSafeInt(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public void sendUpdateAlert(CommandSender sender) {
        if (!updateAvailable) return;

        if (sender.isOp() || sender.hasPermission("staffcore.staff") || sender.hasPermission("staffcore.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&cStaffCore&8] &eA new version &a(v" + latestVersion + ") &eis available!"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&cStaffCore&8] &eDownload here: &b" + updateUrl));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("staffcore.staff") || player.hasPermission("staffcore.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    sendUpdateAlert(player);
                }
            }, 40L);
        }
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