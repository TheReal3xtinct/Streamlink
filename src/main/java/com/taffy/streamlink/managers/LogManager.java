package com.taffy.streamlink.managers;

import com.taffy.streamlink.streamlink;
import java.util.logging.Level;

public class LogManager {
    private final streamlink plugin;
    private final boolean debugMode;

    public LogManager(streamlink plugin) {
        this.plugin = plugin;
        this.debugMode = plugin.getConfig().getBoolean("debug-mode", false);
    }

    public void debug(String message) {
        if (debugMode) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public void info(String message) {
        plugin.getLogger().info(message);
    }

    public void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public void warn(String message, Exception e) {
        plugin.getLogger().log(Level.WARNING, message, e);
    }

    public void severe(String message) {
        plugin.getLogger().severe(message);
    }

    public void severe(String message, Exception e) {
        plugin.getLogger().log(Level.SEVERE, message, e);
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}