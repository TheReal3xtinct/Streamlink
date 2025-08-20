package com.taffy.streamlink.managers;

import com.taffy.streamlink.streamlink;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsManager {
    private final streamlink plugin;
    private final AtomicInteger successfulLinks = new AtomicInteger(0);
    private final AtomicInteger failedLinks = new AtomicInteger(0);
    private final AtomicInteger liveStreamsDetected = new AtomicInteger(0);

    public MetricsManager(streamlink plugin) {
        this.plugin = plugin;
    }

    public void incrementSuccessfulLink() {
        successfulLinks.incrementAndGet();
    }

    public void incrementFailedLink() {
        failedLinks.incrementAndGet();
    }

    public void incrementLiveStream() {
        liveStreamsDetected.incrementAndGet();
    }

    public void reportMetrics() {
        plugin.getLogger().info("=== StreamLink Metrics ===");
        plugin.getLogger().info("Successful Links: " + successfulLinks.get());
        plugin.getLogger().info("Failed Links: " + failedLinks.get());
        plugin.getLogger().info("Live Streams Detected: " + liveStreamsDetected.get());
    }

    // Optional getters for external access
    public int getSuccessfulLinks() { return successfulLinks.get(); }
    public int getFailedLinks() { return failedLinks.get(); }
    public int getLiveStreamsDetected() { return liveStreamsDetected.get(); }

    public void resetMetrics() {
        successfulLinks.set(0);
        failedLinks.set(0);
        liveStreamsDetected.set(0);
    }
}
