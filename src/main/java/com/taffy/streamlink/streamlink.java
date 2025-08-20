package com.taffy.streamlink;

import com.taffy.streamlink.api.TwitchAPI;
import com.taffy.streamlink.commands.StreamLinkCommand;
import com.taffy.streamlink.exceptions.ConfigException;
import com.taffy.streamlink.listeners.StreamLinkListener;
import com.taffy.streamlink.managers.*;
import com.taffy.streamlink.utils.DeviceFlowTask;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class streamlink extends JavaPlugin implements Listener {
    private TwitchAPI twitchAPI;
    private PlayerDataManager dataManager;
    private UniversalPermissionManager permissionManager;
    private LiveStatusManager liveStatusManager;
    private MetricsManager metricsManager;
    private LogManager logManager;
    private ConfigManager configManager;
    private final ConcurrentHashMap<UUID, DeviceFlowTask> activeTasks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Initialize core logging first
        this.logManager = new LogManager(this);

        // Config handling - save default config if it doesn't exist
        saveDefaultConfig();

        // Initialize config manager next
        try {
            this.configManager = new ConfigManager(this);
            configManager.validateAndSetup();
        } catch (ConfigException e) {
            logManager.severe("Configuration error: " + e.getMessage());
        }
        // Initialize metrics after config
        this.metricsManager = new MetricsManager(this);

        // Check for both configurations
        String clientId = getConfig().getString("twitch.client-id");
        String clientSecret = getConfig().getString("twitch.client-secret");

        if (clientId == null || clientId.equals("your_twitch_client_id_here") || clientId.trim().isEmpty()) {
            logManager.severe("Twitch Client ID not configured in config.yml!");
            logManager.severe("Please set 'twitch.client-id' in config.yml");
        }

        if (clientSecret == null || clientSecret.equals("your_twitch_client_secret_here") || clientSecret.trim().isEmpty()) {
            logManager.severe("Twitch Client Secret not configured in config.yml!");
            logManager.severe("Please set 'twitch.client-secret' in config.yml");
            logManager.severe("Twitch integration will be disabled.");
        }

        if ((clientId != null && !clientId.equals("your_twitch_client_id_here") && !clientId.trim().isEmpty()) &&
                (clientSecret != null && !clientSecret.equals("your_twitch_client_secret_here") && !clientSecret.trim().isEmpty())) {
            logManager.info("Twitch credentials configured properly in config.yml");
        }

        // Initialize other components
        this.dataManager = new PlayerDataManager(this);
        this.twitchAPI = new TwitchAPI(this);
        this.permissionManager = new UniversalPermissionManager(this);
        this.liveStatusManager = new LiveStatusManager(this);

        // Register command and events
        getCommand("streamlink").setExecutor(new StreamLinkCommand(this));
        getServer().getPluginManager().registerEvents(new StreamLinkListener(this), this);

        // Schedule metrics reporting if enabled
        if (getConfig().getBoolean("metrics.auto-reset", true)) {
            int interval = getConfig().getInt("metrics.report-interval", 3600);
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                metricsManager.reportMetrics();
                metricsManager.resetMetrics();
            }, interval * 20L, interval * 20L);
        }

        logManager.info("StreamLink v" + getDescription().getVersion() + " enabled!");

        if (!configManager.isTwitchConfigured()) {
            logManager.warn("Plugin enabled with limited functionality. Twitch features disabled.");
        } else {
            logManager.info("Twitch integration is configured and ready!");
        }
    }

    @Override
    public void onDisable() {
        // Report final metrics if metricsManager is initialized
        if (metricsManager != null) {
            metricsManager.reportMetrics();
        }

        // Stop live checking task if liveStatusManager is initialized
        if (liveStatusManager != null) {
            liveStatusManager.stopLiveCheckTask();
        }

        // Clean up all active tasks
        for (DeviceFlowTask task : activeTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeTasks.clear();

        // Backup player data if dataManager is initialized
        if (dataManager != null) {
            dataManager.backupPlayerData();
        }

        if (logManager != null) {
            logManager.info("StreamLink disabled. Goodbye!");
        } else {
            getLogger().info("StreamLink disabled. Goodbye!");
        }
    }

    // Getters with null checks
    public LiveStatusManager getLiveStatusManager() {
        return liveStatusManager != null ? liveStatusManager : null;
    }

    public TwitchAPI getTwitchAPI() {
        return twitchAPI != null ? twitchAPI : null;
    }

    public PlayerDataManager getDataManager() {
        return dataManager != null ? dataManager : null;
    }

    public ConcurrentHashMap<UUID, DeviceFlowTask> getActiveTasks() {
        return activeTasks;
    }

    public UniversalPermissionManager getPermissionManager() {
        return permissionManager != null ? permissionManager : null;
    }

    public MetricsManager getMetricsManager() {
        return metricsManager != null ? metricsManager : null;
    }

    public LogManager getLogManager() {
        return logManager != null ? logManager : null;
    }

    public ConfigManager getConfigManager() {
        return configManager != null ? configManager : null;
    }

}