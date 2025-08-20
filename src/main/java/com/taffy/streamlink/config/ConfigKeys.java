package com.taffy.streamlink.config;

public class ConfigKeys {
    // Twitch Configuration
    public static final String TWITCH_CLIENT_ID = "twitch.client-id";
    public static final String TWITCH_CLIENT_SECRET = "twitch.client-secret";

    // Live Status Configuration
    public static final String LIVE_STATUS_CHECK_INTERVAL = "live-status.check-interval";
    public static final String LIVE_STATUS_BROADCAST_LIVE = "live-status.broadcast-live";
    public static final String LIVE_STATUS_BROADCAST_OFFLINE = "live-status.broadcast-offline";
    public static final String LIVE_STATUS_LIVE_PREFIX = "live-status.live-prefix";
    public static final String LIVE_STATUS_ANNOUNCEMENT_LIVE = "live-status.announcements.live";
    public static final String LIVE_STATUS_ANNOUNCEMENT_OFFLINE = "live-status.announcements.offline";

    // Permissions Configuration
    public static final String PERMISSIONS_LUCKPERMS_PARTNER_GROUP = "permissions.luckperms.partner-group";
    public static final String PERMISSIONS_LUCKPERMS_AFFILIATE_GROUP = "permissions.luckperms.affiliate-group";
    public static final String PERMISSIONS_LUCKPERMS_VIEWER_GROUP = "permissions.luckperms.viewer-group";
    public static final String PERMISSIONS_LUCKPERMS_LIVE_GROUP = "permissions.luckperms.live-group";

    // Metrics Configuration
    public static final String METRICS_REPORT_INTERVAL = "metrics.report-interval";
    public static final String METRICS_AUTO_RESET = "metrics.auto-reset";

    // Debug Configuration
    public static final String DEBUG_MODE = "debug-mode";
    public static final String CONFIG_VERSION = "config-version";
}
