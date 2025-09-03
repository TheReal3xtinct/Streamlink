package com.taffy.streamlink.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taffy.streamlink.streamlink;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class TokenManager {
    private final HttpClient http;
    private final FileConfiguration config;
    private final Runnable saveConfig; // pass plugin::saveConfig
    private final streamlink plugin;

    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile String oauthHelperBase; // e.g., https://your-worker.workers.dev

    // refresh backoff
    private volatile long lastRefreshAttemptMs = 0L;
    private static final long MIN_REFRESH_INTERVAL_MS = 5_000L; // 5s

    public TokenManager(streamlink plugin, FileConfiguration config, Runnable saveConfig) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.plugin = plugin;
        this.config = config;
        this.saveConfig = saveConfig;
        reloadFromConfig();
    }

    /* ---------------- getters / helpers ---------------- */

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getOAuthHelperBase() {
        return oauthHelperBase;
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    public boolean isConfigured() {
        return oauthHelperBase != null && !oauthHelperBase.isEmpty();
    }

    public void reloadFromConfig() {
        this.accessToken     = value("streamlabs.access-token");
        this.refreshToken    = value("streamlabs.refresh-token");
        this.oauthHelperBase = value("streamlabs.oauth-helper");
    }

    /** Persist both tokens to config and memory. */
    public void setTokens(String newAccess, String newRefresh) {
        if (newAccess != null && !newAccess.isEmpty()) {
            this.accessToken = newAccess;
            config.set("streamlabs.access-token", newAccess);
        }
        if (newRefresh != null && !newRefresh.isEmpty()) {
            this.refreshToken = newRefresh;
            config.set("streamlabs.refresh-token", newRefresh);
        }
        saveConfig.run();
    }

    private String value(String path) {
        String v = config.getString(path);
        return (v == null) ? "" : v.trim();
    }

    /* ---------------- refresh flow ---------------- */

    /**
     * Caller decides WHEN to refresh (e.g., after 401). This method:
     * - debounces rapid calls
     * - returns false if missing config/refresh token
     * - updates access/refresh in memory + config if successful
     */
    public synchronized boolean refreshIfNeeded() {
        // sanity checks
        if (!hasRefreshToken()) {
            plugin.getLogger().warning("[StreamLink] No refresh token; cannot refresh.");
            return false;
        }
        if (!isConfigured()) {
            plugin.getLogger().warning("[StreamLink] Missing streamlabs.oauth-helper in config.yml");
            return false;
        }

        // debounce multiple callers
        long now = System.currentTimeMillis();
        if (now - lastRefreshAttemptMs < MIN_REFRESH_INTERVAL_MS) {
            return false;
        }
        lastRefreshAttemptMs = now;

        try {
            String url = oauthHelperBase.endsWith("/")
                    ? oauthHelperBase + "token/refresh"
                    : oauthHelperBase + "/token/refresh";

            String body = "{\"refresh_token\":\"" + escapeJson(refreshToken) + "\"}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                plugin.getLogger().warning("[StreamLink] Refresh failed: HTTP "
                        + resp.statusCode() + " body=" + truncate(resp.body(), 300));
                return false;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            String newAccess  = json.has("access_token")  && !json.get("access_token").isJsonNull()
                    ? json.get("access_token").getAsString() : null;
            String newRefresh = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                    ? json.get("refresh_token").getAsString() : null;

            if ((newAccess == null || newAccess.isEmpty()) &&
                    (newRefresh == null || newRefresh.isEmpty())) {
                plugin.getLogger().warning("[StreamLink] Refresh response missing tokens.");
                return false;
            }

            setTokens(newAccess, newRefresh);
            plugin.getLogger().info("[StreamLink] Streamlabs token refresh: OK");
            return true;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[StreamLink] Refresh interrupted.");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[StreamLink] Refresh exception: " + e.getMessage());
            return false;
        }
    }

    /* ---------------- utils ---------------- */

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}