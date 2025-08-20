package com.taffy.streamlink.utils;

import com.taffy.streamlink.managers.LogManager;
import com.taffy.streamlink.managers.MetricsManager;
import com.taffy.streamlink.streamlink;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DeviceFlowTask extends BukkitRunnable {
    private final streamlink plugin;
    private final Player player;
    private final String deviceCode;
    private final LogManager log;
    private final MetricsManager metrics;
    private int attempts = 0;
    private boolean isComplete = false;

    public DeviceFlowTask(streamlink plugin, Player player, String deviceCode) {
        this.plugin = plugin;
        this.log = plugin.getLogManager();
        this.metrics = plugin.getMetricsManager();
        this.player = player;
        this.deviceCode = deviceCode;
        log.debug("Device flow task created for " + player.getName());
    }

    @Override
    public void run() {
        // Skip if already completed
        if (isComplete) {
            cancel();
            plugin.getActiveTasks().remove(player.getUniqueId());
            return;
        }

        attempts++;
        log.debug("Device flow attempt " + attempts + " for " + player.getName());

        // Timeout after 12 attempts (1 minute)
        if (attempts > 12) {
            if (!plugin.getDataManager().isLinked(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Twitch linking timed out. Please try again.");
            }
            cancel();
            plugin.getActiveTasks().remove(player.getUniqueId());
            log.debug("Device flow timed out for " + player.getName());
            return;
        }

        try {
            String accessTokenResponse = plugin.getTwitchAPI().getAccessToken(deviceCode);
            String[] tokens = accessTokenResponse.split("\\|", 2);
            String accessToken = tokens[0];
            String refreshToken = tokens.length > 1 ? tokens[1] : "refresh_token_placeholder";

            String twitchId = plugin.getTwitchAPI().getTwitchUserId(accessToken);
            String displayName = plugin.getTwitchAPI().getTwitchDisplayName(accessToken);
            String twitchUsername = plugin.getTwitchAPI().getTwitchUsername(accessToken);

            if (!plugin.getDataManager().isLinked(player.getUniqueId())) {
                plugin.getDataManager().linkPlayer(player.getUniqueId(), twitchId, accessToken, refreshToken, twitchUsername);
                metrics.incrementSuccessfulLink();
                log.info("Successfully linked " + player.getName() + " to Twitch: " + displayName);

                // Apply permissions based on Twitch status
                plugin.getPermissionManager().applyTwitchRank(player, accessToken);

                // Immediately check live status
                plugin.getLiveStatusManager().checkPlayerLiveStatus(player);
            }

            // Mark as complete and cancel
            isComplete = true;
            cancel();
            plugin.getActiveTasks().remove(player.getUniqueId());
            log.debug("Device flow completed successfully for " + player.getName());

        } catch (Exception e) {
            metrics.incrementFailedLink();

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("400")) {
                log.debug("Device flow polling (expected): " + errorMsg); // Change to debug level
            } else {
                log.warn("Linking error for " + player.getName() + ": " + errorMsg);
            }

            // Only send waiting message if not already linked
            if (!plugin.getDataManager().isLinked(player.getUniqueId())) {
                if (attempts % 4 == 0) { // Every 20 seconds
                    player.sendMessage(ChatColor.YELLOW + "Still waiting for you to authorize on Twitch...");
                }
            } else {
                // Already linked - cancel task
                cancel();
                plugin.getActiveTasks().remove(player.getUniqueId());
            }
        }
    }
}
