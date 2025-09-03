package com.taffy.streamlink.listeners;

import com.taffy.streamlink.streamlink;
import com.taffy.streamlink.models.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class StoredSummaryListener implements Listener {
    private final streamlink plugin;
    public StoredSummaryListener(streamlink plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        var dm = plugin.getDataManager();
        var data = dm.getPlayerData(p.getUniqueId());
        if (data != null && data.isLinked()) {
            int pts = data.getLoyaltyPoints();
            double hrs = data.getWatchMinutes() / 60.0;
            p.sendMessage("§6§lStreamLabs (stored) §7→ §ePoints: §a" + pts + " §7| §eWatch time: §a" + String.format("%.1f", hrs) + "h");
        }
    }
}