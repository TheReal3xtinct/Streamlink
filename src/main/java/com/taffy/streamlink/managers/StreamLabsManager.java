package com.taffy.streamlink.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taffy.streamlink.streamlink;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.java_websocket.client.WebSocketClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StreamLabsManager extends ManagerBase {
    // (Socket kept here in case you later re-enable alerts; unused for points)
    private WebSocketClient socketClient;
    private boolean connected = false;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private TokenManager tokenManager;

    private final Map<UUID, Integer> playerLoyaltyPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastAppliedTier     = new ConcurrentHashMap<>();
    private final Map<String, UUID> twitchToMinecraftMap = new ConcurrentHashMap<>();

    private boolean isCsvSource() {
        return "csv".equalsIgnoreCase(plugin.getConfig().getString("loyalty.source", "csv"));
    }

    private boolean isApiSource() {
        return "api".equalsIgnoreCase(plugin.getConfig().getString("loyalty.source", "csv"));
    }

    public StreamLabsManager(streamlink plugin) {
        super(plugin);
    }

    @Override
    public void initialize() {
        this.tokenManager = new TokenManager(plugin, plugin.getConfig(), plugin::saveConfig);

        final String source = plugin.getConfig().getString("loyalty.source", "api").trim().toLowerCase();
        final boolean pollingEnabled = plugin.getConfig().getBoolean("streamlabs.polling-enabled", true);

        if ("api".equals(source) && pollingEnabled) {
            startLoyaltyPointsPolling();
        } else {
            log.info("StreamLabs polling disabled (loyalty.source=" + source + ", polling-enabled=" + pollingEnabled + ")");
        }
    }

    @Override
    public void shutdown() {
        disconnect();
    }

    /* ------------------------- public API ------------------------- */

    /** Register a player’s Twitch login to be polled. */
    public void registerPlayerTwitchName(Player player, String twitchLogin) {
        if (twitchLogin == null || twitchLogin.isEmpty()) return;
        if (!isApiSource()) return;
        String viewer = twitchLogin.toLowerCase();
        twitchToMinecraftMap.put(viewer, player.getUniqueId());

        // fetch once immediately so /streamlink points returns fast
        fetchLoyaltyPoints(viewer, player.getUniqueId());
    }

    /** Cached value for quick lookups. */
    public int getLoyaltyPoints(UUID playerId) {
        return playerLoyaltyPoints.getOrDefault(playerId, 0);
    }

    /** Simple guard used by the command to see if we’re configured. */
    public boolean hasLoyaltyPointsAccess() {
        String at = (tokenManager == null) ? "" : tokenManager.getAccessToken();
        return at != null && !at.isEmpty();
    }

    public void disconnect() {
        if (socketClient != null) socketClient.close();
        connected = false;
    }

    /* ----------------------- polling / fetching ----------------------- */

    private void startLoyaltyPointsPolling() {
        if (!isApiSource()) {
            log.info("Loyalty source=csv; StreamLabs API polling disabled.");
            return;
        }
        if (!hasLoyaltyPointsAccess()) {
            log.warn("StreamLabs access token not configured. Loyalty polling disabled.");
            return;
        }

        // every 30s poll any registered viewers
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                    if (twitchToMinecraftMap.isEmpty()) return;
                    for (Map.Entry<String, UUID> e : twitchToMinecraftMap.entrySet()) {
                        fetchLoyaltyPoints(e.getKey(), e.getValue());
                    }
                },
                0L,
                20L * 30
        );
    }

    private void persistLoyalty(UUID playerId, int newPoints, long newMinutes) {
        boolean preferStored = plugin.getConfig().getBoolean("streamlabs.prefer-stored", true);

        var dm = plugin.getDataManager();
        var pd = dm.getPlayerData(playerId);

        int curPoints  = (pd != null) ? pd.getLoyaltyPoints()  : 0;
        long curMins   = (pd != null) ? pd.getWatchMinutes()   : 0L;

        int pointsToWrite  = preferStored ? Math.max(curPoints,  newPoints)  : newPoints;
        long minutesToWrite = preferStored ? Math.max(curMins,   newMinutes) : newMinutes;

        // Save once
        dm.setLoyaltyPoints(playerId, pointsToWrite);
        dm.setWatchMinutes(playerId, minutesToWrite);

        // Keep in-memory cache in sync and apply tiers based on *effective* points
        playerLoyaltyPoints.put(playerId, pointsToWrite);
        applyTierIfChanged(playerId, pointsToWrite);
    }

    /**
     * Background poller path:
     * - parses points (minutes may be missing/incorrect here)
     * - optionally keeps stored value if API returns lower (prefer-stored)
     * - DOES NOT touch watchMinutes to avoid overwriting CSV imports
     */
    private void fetchLoyaltyPoints(String viewerLoginLower, UUID playerId) {
        final String channel = plugin.getConfig().getString("streamlabs.channel", "").trim().toLowerCase();
        if (channel.isEmpty()) {
            log.warn("Set streamlabs.channel (your Twitch login) in config.yml.");
            return;
        }

        final String url = String.format(
                "https://streamlabs.com/api/v2.0/points/user_points?username=%s&channel=%s&platform=twitch",
                URLEncoder.encode(viewerLoginLower, StandardCharsets.UTF_8),
                URLEncoder.encode(channel, StandardCharsets.UTF_8)
        );

        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                            .header("User-Agent", "StreamLink-Plugin/1.0")
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // On 401, try one refresh and retry
            if (resp.statusCode() == 401 && tokenManager.hasRefreshToken() && tokenManager.refreshIfNeeded()) {
                resp = http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                                .header("User-Agent", "StreamLink-Plugin/1.0")
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
            }

            if (resp.statusCode() != 200) {
                if (log.isDebugMode()) {
                    log.debug("Streamlabs points HTTP=" + resp.statusCode() + " body=" + truncate(resp.body(), 200));
                }
                return;
            }

            // ---- Parse response ----
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            int  points  = 0;
            long minutes = 0L;

            if (root.has("points") && root.get("points").isJsonObject()) {
                JsonObject wrapper = root.getAsJsonObject("points");
                if (wrapper.has("data") && wrapper.get("data").isJsonArray() && wrapper.getAsJsonArray("data").size() > 0) {
                    JsonObject row = wrapper.getAsJsonArray("data").get(0).getAsJsonObject();
                    if (row.has("points"))        points  = row.get("points").getAsInt();
                    if (row.has("time_watched"))  minutes = row.get("time_watched").getAsLong(); // minutes
                }
            } else if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("points"))        points  = data.get("points").getAsInt();
                if (data.has("time_watched"))  minutes = data.get("time_watched").getAsLong();
            }

            final int  finalPoints  = points;
            final long finalMinutes = minutes;

            // Do state updates on main thread; persistLoyalty should respect streamlabs.prefer-stored
            Bukkit.getScheduler().runTask(plugin, () -> {
                persistLoyalty(playerId, finalPoints, finalMinutes);
                if (log.isDebugMode()) {
                    log.debug("SL points viewer=" + viewerLoginLower +
                            " -> new=" + finalPoints + " (" + finalMinutes + " min)" +
                            " preferStored=" + plugin.getConfig().getBoolean("streamlabs.prefer-stored", true));
                }
            });

            if (log.isDebugMode()) {
                log.debug("SL points viewer=" + viewerLoginLower + " -> " + points);
            }
        } catch (Exception e) {
            if (log.isDebugMode()) log.debug("Streamlabs fetch error for " + viewerLoginLower + ": " + e.getMessage());
        }
    }

    /**
     * One-shot fetch used by /streamlink points:
     * - writes points and minutes if present
     */
    public void fetchPointsOnce(Player player, String twitchViewerName) {
        if (!isApiSource()) {
            player.sendMessage("§7(Using CSV as source; stored values only)");
            return;
        }

        final String channel = plugin.getConfig().getString("streamlabs.channel", "").trim().toLowerCase();
        if (channel.isEmpty()) {
            player.sendMessage("§eAdmin setup required: set §astreamlabs.channel §ein config.yml");
            return;
        }

        final String viewer = twitchViewerName.toLowerCase();
        final UUID playerId = player.getUniqueId();
        final String url = String.format(
                "https://streamlabs.com/api/v2.0/points/user_points?username=%s&channel=%s&platform=twitch",
                URLEncoder.encode(viewer, StandardCharsets.UTF_8),
                URLEncoder.encode(channel, StandardCharsets.UTF_8)
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String token = tokenManager.getAccessToken();
                if (log.isDebugMode()) {
                    log.debug("fetchPointsOnce url=" + url);
                    log.debug("token len=" + (token == null ? 0 : token.length()));
                }

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .header("User-Agent", "StreamLink-Plugin/1.0")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                String body = resp.body();

                if ((code == 401 || code == 302) && tokenManager.hasRefreshToken() && tokenManager.refreshIfNeeded()) {
                    token = tokenManager.getAccessToken();
                    req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + token)
                            .header("Accept", "application/json")
                            .header("User-Agent", "StreamLink-Plugin/1.0")
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
                    resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    code = resp.statusCode();
                    body = resp.body();
                }

                if (log.isDebugMode()) {
                    log.debug("Streamlabs points HTTP=" + code + " body=" +
                            (body == null ? "" : (body.length() > 200 ? body.substring(0, 200) + "..." : body)));
                }

                if (code != 200) {
                    final int fCode = code;
                    final String fBody = (body == null) ? "" : (body.length() > 140 ? body.substring(0,140) + "..." : body);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§cStreamLabs error (" + fCode + "): " + fBody)
                    );
                    if (code == 302) {
                        log.warn("Got 302 from Streamlabs API. This usually means the Authorization header was not accepted.");
                        log.warn("Double-check the token has 'points.read' scope and matches the one that works in curl/PowerShell.");
                    }
                    return;
                }

                // Parse body
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                int points = 0;
                long minutes = 0;

                if (root.has("points") && root.get("points").isJsonObject()) {
                    JsonObject wrapper = root.getAsJsonObject("points");
                    if (wrapper.has("data") && wrapper.get("data").isJsonArray() && wrapper.getAsJsonArray().size() > 0) {
                        JsonObject row = wrapper.getAsJsonArray("data").get(0).getAsJsonObject();
                        if (row.has("points"))       points  = row.get("points").getAsInt();
                        if (row.has("time_watched")) minutes = row.get("time_watched").getAsLong();
                    }
                } else if (root.has("data") && root.get("data").isJsonObject()) {
                    JsonObject data = root.getAsJsonObject("data");
                    if (data.has("points"))       points  = data.get("points").getAsInt();
                    if (data.has("time_watched")) minutes = data.get("time_watched").getAsLong();
                }

                final int finalPoints = points;
                final long finalMinutes = minutes;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Persist honoring prefer-stored
                    persistLoyalty(playerId, finalPoints, finalMinutes);

                    // Read back what actually “won” and show that to the player
                    var pd = plugin.getDataManager().getPlayerData(playerId);
                    int effectivePoints = (pd != null) ? pd.getLoyaltyPoints() : finalPoints;
                    long effectiveMinutes = (pd != null) ? pd.getWatchMinutes() : finalMinutes;

                    player.sendMessage("§6§lStreamLabs §7→ §ePoints: §a" + effectivePoints +
                            " §7| §eWatch time: §a" + String.format("%.1f", effectiveMinutes / 60.0) + "h" +
                            (plugin.getConfig().getBoolean("streamlabs.prefer-stored", true) ? " §7(Prefer stored)" : ""));
                });

                if (log.isDebugMode()) {
                    log.debug("SL points viewer=" + viewer + " -> " + points + " (" + minutes + " min)");
                }

            } catch (Exception e) {
                final String msg = e.getMessage();
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§cStreamLabs request failed: " + msg)
                );
                if (log.isDebugMode()) {
                    log.debug("StreamLabs fetch error for " + viewer + ": " + msg);
                }
            }
        });
    }

    public boolean forceRefreshTokens() {
        if (tokenManager == null) return false;
        return tokenManager.refreshIfNeeded();
    }

    /* ------------------------- tiers / perms ------------------------- */

    private void applyTierIfChanged(UUID playerId, int points) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        int tier1 = plugin.getConfig().getInt("streamlabs.loyalty-tiers.tier1", 100);
        int tier2 = plugin.getConfig().getInt("streamlabs.loyalty-tiers.tier2", 1000);
        int tier3 = plugin.getConfig().getInt("streamlabs.loyalty-tiers.tier3", 5000);
        int tier4 = plugin.getConfig().getInt("streamlabs.loyalty-tiers.tier4", 10000);

        int tier = 0;
        if (points >= tier4) tier = 4;
        else if (points >= tier3) tier = 3;
        else if (points >= tier2) tier = 2;
        else if (points >= tier1) tier = 1;

        Integer last = lastAppliedTier.get(playerId);
        if (last != null && last == tier) return; // no change

        // clear any previous tier first
        plugin.getPermissionManager().removeTierPermissions(player);

        switch (tier) {
            case 4 -> plugin.getPermissionManager().applyTier4Permissions(player);
            case 3 -> plugin.getPermissionManager().applyTier3Permissions(player);
            case 2 -> plugin.getPermissionManager().applyTier2Permissions(player);
            case 1 -> plugin.getPermissionManager().applyTier1Permissions(player);
            default -> {
                // below tier1: nothing
            }
        }
        lastAppliedTier.put(playerId, tier);
    }

    /* ---------------------------- utils ---------------------------- */

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

