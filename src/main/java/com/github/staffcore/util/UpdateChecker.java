package com.github.staffcore.util;

import com.github.staffcore.StaffCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UpdateChecker {

    private final StaffCore plugin;
    private final String repoOwner;
    private final String repoName;
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(StaffCore plugin, String repoOwner, String repoName) {
        this.plugin = plugin;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    String apiUrl = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest";
                    URL url = URI.create(apiUrl).toURL();
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("User-Agent", "StaffCore-UpdateChecker");
                    con.setConnectTimeout(5000);
                    con.setReadTimeout(5000);

                    if (con.getResponseCode() == 200) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();

                        String body = response.toString();
                        int tagIndex = body.indexOf("\"tag_name\":");
                        if (tagIndex != -1) {
                            int start = body.indexOf("\"", tagIndex + 11) + 1;
                            int end = body.indexOf("\"", start);
                            latestVersion = body.substring(start, end).replace("v", "").trim();

                            String currentVersion = plugin.getDescription().getVersion().replace("v", "").replace("-Universal", "").trim();

                            if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                                updateAvailable = true;
                                plugin.getLogger().info("==================================================");
                                plugin.getLogger().info("A new update of StaffCore is available: " + latestVersion);
                                plugin.getLogger().info("Your current version: " + currentVersion);
                                plugin.getLogger().info("Download: https://github.com/" + repoOwner + "/" + repoName + "/releases");
                                plugin.getLogger().info("==================================================");
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
                }
            }
        });
    }

    public void notifyPlayer(Player player) {
        if (updateAvailable && (player.isOp() || player.hasPermission("staffcore.admin"))) {
            if (plugin.getConfig().getBoolean("update-checker.notify-staff-on-join", true)) {
                player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "StaffCore" + ChatColor.DARK_GRAY + "] " +
                        ChatColor.YELLOW + "A new update (" + ChatColor.GREEN + "v" + latestVersion + ChatColor.YELLOW + ") is available!");
                player.sendMessage(ChatColor.DARK_GRAY + " » " + ChatColor.AQUA + "https://github.com/" + repoOwner + "/" + repoName + "/releases");
            }
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}