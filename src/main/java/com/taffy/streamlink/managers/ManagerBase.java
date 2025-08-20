package com.taffy.streamlink.managers;

import com.taffy.streamlink.streamlink;

public class ManagerBase {
    protected final streamlink plugin;
    protected final LogManager log;
    protected final MetricsManager metrics;

    public ManagerBase(streamlink plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogManager();
        this.metrics = plugin.getMetricsManager();
    }
}
