package com.taffy.streamlink.listeners;

import com.taffy.streamlink.streamlink;
import com.taffy.streamlink.utils.DeviceFlowTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.util.UUID;

public class StreamLinkListener implements Listener {
    private final streamlink plugin;
    private final com.taffy.streamlink.managers.LogManager log;

    public StreamLinkListener(streamlink plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Migrate old group names if player is linked
        if (plugin.getDataManager().isLinked(player.getUniqueId())) {
            plugin.getPermissionManager().migrateOldGroups(player);
            log.debug("Migrated old groups for " + player.getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clean up active tasks
        DeviceFlowTask task = plugin.getActiveTasks().remove(playerId);
        if (task != null) {
            task.cancel();
            log.debug("Cancelled device flow task for " + player.getName());
        }

        // Clean up permission attachments
        plugin.getPermissionManager().cleanupPlayer(player);
        log.debug("Cleaned up permissions for " + player.getName());
    }
}
