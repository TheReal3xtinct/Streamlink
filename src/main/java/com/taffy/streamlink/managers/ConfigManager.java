package com.taffy.streamlink.managers;

import com.taffy.streamlink.config.ConfigKeys;
import com.taffy.streamlink.exceptions.ConfigException;
import com.taffy.streamlink.streamlink;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager extends ManagerBase {
    private final int CONFIG_VERSION = 2;

    public ConfigManager(streamlink plugin) {
        super(plugin);
    }

    public void validateAndSetup() throws ConfigException {
        try {
            FileConfiguration config = plugin.getConfig();

            // Set config version if missing
            if (!config.contains(ConfigKeys.CONFIG_VERSION)) {
                config.set(ConfigKeys.CONFIG_VERSION, CONFIG_VERSION);
            }

            // Validate all sections
            validateTwitchConfig();
            validateLiveStatusConfig();
            validatePermissionConfig();
            validateMetricsConfig();

            // Save if any changes were made
            plugin.saveConfig();
            log.info("Config validation completed successfully");

        } catch (Exception e) {
            throw new ConfigException("Failed to validate config: " + e.getMessage(), e);
        }
    }

    private void validateTwitchConfig() throws ConfigException {
        FileConfiguration config = plugin.getConfig();

        if (!config.contains(ConfigKeys.TWITCH_CLIENT_ID)) {
            config.set(ConfigKeys.TWITCH_CLIENT_ID, "your_twitch_client_id_here");
            log.warn("Twitch Client ID not configured! Please set '" + ConfigKeys.TWITCH_CLIENT_ID + "' in config.yml");
        }

        if (!config.contains(ConfigKeys.TWITCH_CLIENT_SECRET)) {
            config.set(ConfigKeys.TWITCH_CLIENT_SECRET, "your_twitch_client_secret_here");
            log.warn("Twitch Client Secret not configured! Please set '" + ConfigKeys.TWITCH_CLIENT_SECRET + "' in config.yml");
        }

        String clientId = getTwitchClientId();
        String clientSecret = getTwitchClientSecret();

    }

    // Type-safe getter methods
    public String getTwitchClientId() {
        return getString(ConfigKeys.TWITCH_CLIENT_ID, "your_twitch_client_id_here");
    }

    public String getTwitchClientSecret() {
        return getString(ConfigKeys.TWITCH_CLIENT_SECRET, "your_twitch_client_secret_here");
    }

    public int getCheckInterval() {
        return getInt(ConfigKeys.LIVE_STATUS_CHECK_INTERVAL, 300);
    }

    public boolean isBroadcastLive() {
        return getBoolean(ConfigKeys.LIVE_STATUS_BROADCAST_LIVE, true);
    }

    public boolean isBroadcastOffline() {
        return getBoolean(ConfigKeys.LIVE_STATUS_BROADCAST_OFFLINE, true);
    }

    public String getLivePrefix() {
        return getString(ConfigKeys.LIVE_STATUS_LIVE_PREFIX, "&c[LIVE] &r");
    }

    public String getLiveAnnouncement() {
        return getString(ConfigKeys.LIVE_STATUS_ANNOUNCEMENT_LIVE,
                "&6🎥 &b{player} &ais now LIVE on Twitch! &7[{viewers} viewers]");
    }

    public String getOfflineAnnouncement() {
        return getString(ConfigKeys.LIVE_STATUS_ANNOUNCEMENT_OFFLINE,
                "&7📴 &b{player} &chas gone offline.");
    }

    public boolean isDebugMode() {
        return getBoolean(ConfigKeys.DEBUG_MODE, false);
    }

    // Helper methods
    private String getString(String path, String defaultValue) {
        String value = plugin.getConfig().getString(path);
        return value != null && !value.trim().isEmpty() ? value : defaultValue;
    }

    private int getInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    private boolean getBoolean(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }

    private List<String> getStringList(String path) {
        return plugin.getConfig().getStringList(path);
    }

    public boolean isTwitchConfigured() {
        String clientId = getTwitchClientId();
        String clientSecret = getTwitchClientSecret();

        return !clientId.equals("your_twitch_client_id_here") &&
                !clientSecret.equals("your_twitch_client_secret_here") &&
                !clientId.trim().isEmpty() &&
                !clientSecret.trim().isEmpty();
    }

    // ... other validation methods for live status, permissions, metrics
    private void validateLiveStatusConfig() {
        FileConfiguration config = plugin.getConfig();

        if (!config.contains(ConfigKeys.LIVE_STATUS_CHECK_INTERVAL)) {
            config.set(ConfigKeys.LIVE_STATUS_CHECK_INTERVAL, 300);
        }
        if (!config.contains(ConfigKeys.LIVE_STATUS_BROADCAST_LIVE)) {
            config.set(ConfigKeys.LIVE_STATUS_BROADCAST_LIVE, true);
        }
        if (!config.contains(ConfigKeys.LIVE_STATUS_BROADCAST_OFFLINE)) {
            config.set(ConfigKeys.LIVE_STATUS_BROADCAST_OFFLINE, true);
        }
        if (!config.contains(ConfigKeys.LIVE_STATUS_LIVE_PREFIX)) {
            config.set(ConfigKeys.LIVE_STATUS_LIVE_PREFIX, "&c[LIVE] &r");
        }
    }

    private void validatePermissionConfig() {
        FileConfiguration config = plugin.getConfig();

        // Auto-setup default permission nodes if missing
        if (!config.contains("permissions.bukkit.partner-permissions")) {
            config.set("permissions.bukkit.partner-permissions", java.util.Arrays.asList(
                    "streamlink.partner", "streamlink.emotes", "streamlink.vip", "streamlink.colorchat"
            ));
        }
        // ... other permission validations
    }

    private void validateMetricsConfig() {
        FileConfiguration config = plugin.getConfig();

        if (!config.contains(ConfigKeys.METRICS_REPORT_INTERVAL)) {
            config.set(ConfigKeys.METRICS_REPORT_INTERVAL, 3600);
        }
        if (!config.contains(ConfigKeys.METRICS_AUTO_RESET)) {
            config.set(ConfigKeys.METRICS_AUTO_RESET, true);
        }
    }

    private void validateStreamlabsConfig() {
        FileConfiguration config = plugin.getConfig();

        if (!config.contains("streamlabs.channel"))        config.set("streamlabs.channel", "YOUR_STREAMER_TWITCH_LOGIN");
        if (!config.contains("streamlabs.access-token"))   config.set("streamlabs.access-token", "");
        if (!config.contains("streamlabs.refresh-token"))  config.set("streamlabs.refresh-token", "");
        if (!config.contains("streamlabs.oauth-helper"))   config.set("streamlabs.oauth-helper", "http://localhost:3000");

        if (!config.contains("streamlabs.loyalty-tiers.tier1")) config.set("streamlabs.loyalty-tiers.tier1", 100);
        if (!config.contains("streamlabs.loyalty-tiers.tier2")) config.set("streamlabs.loyalty-tiers.tier2", 1000);
        if (!config.contains("streamlabs.loyalty-tiers.tier3")) config.set("streamlabs.loyalty-tiers.tier3", 5000);
        if (!config.contains("streamlabs.loyalty-tiers.tier4")) config.set("streamlabs.loyalty-tiers.tier4", 10000);
    }
}
