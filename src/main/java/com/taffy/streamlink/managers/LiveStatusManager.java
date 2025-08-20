package com.taffy.streamlink.managers;

import com.google.gson.JsonObject;
import com.taffy.streamlink.streamlink;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LiveStatusManager extends ManagerBase {
    private final ConcurrentHashMap<UUID, Boolean> liveStatusCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> livePlayerPrefixes = new ConcurrentHashMap<>();
    private int taskId = -1;

    public LiveStatusManager(streamlink plugin) {
        super(plugin);
        startLiveCheckTask();
    }

    public void startLiveCheckTask() {
        int interval = plugin.getConfig().getInt("live-status.check-interval", 120);
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkAllLiveStatuses,
                0,
                20 * interval
        ).getTaskId();
        log.info("Live status check task started with " + interval + " second interval");
    }

    public void stopLiveCheckTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
            log.info("Live status check task stopped");
        }
    }

    private void checkAllLiveStatuses() {
        Set<UUID> linkedPlayers = plugin.getDataManager().getAllLinkedPlayers();
        log.debug("Checking live status for " + linkedPlayers.size() + " linked players");

        for (UUID playerId : linkedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                checkPlayerLiveStatus(player);
            }
        }
    }

    public void checkPlayerLiveStatus(Player player) {
        UUID playerId = player.getUniqueId();
        String accessToken = plugin.getDataManager().getAccessToken(playerId);
        String twitchId = plugin.getDataManager().getTwitchId(playerId);

        if (accessToken == null || twitchId == null) {
            log.debug("Skipping live check for " + player.getName() + " - missing tokens");
            return;
        }

        try {
            boolean isLive = plugin.getTwitchAPI().isUserLive(accessToken, twitchId);
            boolean wasLive = liveStatusCache.getOrDefault(playerId, false);

            if (isLive && !wasLive) {
                // Player just went live
                onPlayerWentLive(player, accessToken, twitchId);
            } else if (!isLive && wasLive) {
                // Player just went offline
                onPlayerWentOffline(player);
            }

            liveStatusCache.put(playerId, isLive);

        } catch (Exception e) {
            log.warn("Failed to check live status for " + player.getName() + ": " + e.getMessage());

            // Try to refresh token if it's invalid
            if (e.getMessage().contains("401") || e.getMessage().contains("Invalid")) {
                tryRefreshToken(player);
            }
        }
    }

    private void onPlayerWentLive(Player player, String accessToken, String twitchId) {
        try {
            JsonObject streamInfo = plugin.getTwitchAPI().getStreamInfo(accessToken, twitchId);
            String streamTitle = streamInfo.get("title").getAsString();
            String gameName = streamInfo.get("game_name").getAsString();
            int viewerCount = streamInfo.get("viewer_count").getAsInt();

            // Apply live permissions
            plugin.getPermissionManager().applyLivePermissions(player);

            // Set live prefix
            setLivePrefix(player, true);

            // Broadcast announcement
            broadcastLiveAnnouncement(player, streamTitle, gameName, viewerCount);

            metrics.incrementLiveStream();
            log.info(player.getName() + " is now live on Twitch with " + viewerCount + " viewers!");

        } catch (Exception e) {
            log.warn("Failed to get stream info for " + player.getName(), e);
        }
    }

    private void onPlayerWentOffline(Player player) {
        // Remove live permissions
        plugin.getPermissionManager().removeLivePermissions(player);

        // Remove live prefix
        setLivePrefix(player, false);

        // Broadcast offline notice
        broadcastOfflineAnnouncement(player);

        log.info(player.getName() + " is no longer live on Twitch.");
    }

    private void setLivePrefix(Player player, boolean isLive) {
        if (isLive) {
            String prefix = ChatColor.RED + "[LIVE] " + ChatColor.RESET;
            livePlayerPrefixes.put(player.getUniqueId(), prefix);

            // Update player display name
            player.setDisplayName(prefix + player.getName());
            player.setPlayerListName(prefix + player.getName());
        } else {
            livePlayerPrefixes.remove(player.getUniqueId());

            // Reset player display name
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
    }

    private void broadcastLiveAnnouncement(Player player, String title, String game, int viewers) {
        String message = ChatColor.GOLD + "🎥 " + ChatColor.AQUA + player.getName() +
                ChatColor.GREEN + " is now LIVE on Twitch!" +
                ChatColor.GRAY + " [" + viewers + " viewers]\n" +
                ChatColor.WHITE + "Title: " + ChatColor.YELLOW + title + "\n" +
                ChatColor.WHITE + "Game: " + ChatColor.YELLOW + game;

        Bukkit.broadcastMessage(message);
    }

    private void broadcastOfflineAnnouncement(Player player) {
        String message = ChatColor.GRAY + "📴 " + ChatColor.AQUA + player.getName() +
                ChatColor.RED + " has gone offline.";

        Bukkit.broadcastMessage(message);
    }

    private void tryRefreshToken(Player player) {
        try {
            String refreshToken = plugin.getDataManager().getRefreshToken(player.getUniqueId());
            if (refreshToken != null) {
                String newAccessToken = plugin.getTwitchAPI().getAccessTokenFromRefresh(refreshToken);
                plugin.getDataManager().updateAccessToken(player.getUniqueId(), newAccessToken);
                log.info("Refreshed access token for " + player.getName());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh token for " + player.getName(), e);
        }
    }

    public boolean isPlayerLive(UUID playerId) {
        return liveStatusCache.getOrDefault(playerId, false);
    }

    public String getLivePrefix(UUID playerId) {
        return livePlayerPrefixes.get(playerId);
    }

    public void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        liveStatusCache.remove(playerId);
        livePlayerPrefixes.remove(playerId);
        log.debug("Cleaned up live status for " + player.getName());
    }
}