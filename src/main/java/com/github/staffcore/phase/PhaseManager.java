package com.github.staffcore.phase;

import com.github.staffcore.StaffCore;
import com.github.staffcore.commands.UtilityCommands;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseManager {

    private final StaffCore plugin;
    private final Set<UUID> phasedPlayers = ConcurrentHashMap.newKeySet();

    public PhaseManager(StaffCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPhased(UUID uuid) {
        return phasedPlayers.contains(uuid);
    }

    public boolean togglePhase(Player player) {
        boolean state = !isPhased(player.getUniqueId());
        setPhased(player, state);
        return state;
    }

    public void setPhased(Player player, boolean phased) {
        UUID uuid = player.getUniqueId();
        if (phased) {
            phasedPlayers.add(uuid);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.enabled", "&8[&cStaffCore&8] &aPhase mode &2enabled&a! You can now walk and fly through blocks.")));
        } else {
            phasedPlayers.remove(uuid);
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                if (!UtilityCommands.isFlyEnabled(uuid)) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }
            player.sendMessage(plugin.getMessageManager().color(
                    plugin.getConfig().getString("phase.disabled", "&8[&cStaffCore&8] &cPhase mode &4disabled&c.")));
        }
    }

    public void remove(UUID uuid) {
        phasedPlayers.remove(uuid);
    }

    public void clearAll() {
        phasedPlayers.clear();
    }
}