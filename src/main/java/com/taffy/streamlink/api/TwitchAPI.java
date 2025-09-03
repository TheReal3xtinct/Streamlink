package com.taffy.streamlink.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taffy.streamlink.managers.ManagerBase;
import com.taffy.streamlink.streamlink;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TwitchAPI extends ManagerBase {
    private final String clientId;
    private final String clientSecret;
    private final ConcurrentHashMap<UUID, String> authCodes;
    private final Cache<String, JsonObject> userInfoCache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
    private final Cache<String, Boolean> liveStatusCache = CacheBuilder.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();

    public TwitchAPI(streamlink plugin) {
        super(plugin);
        this.clientId = getConfigValue("twitch.client-id");
        this.clientSecret = getSecureSecret("twitch.client-secret");
        this.authCodes = new ConcurrentHashMap<>();

        if (clientId == null || clientId.equals("MISSING_CONFIG") || clientId.equals("your_twitch_client_id_here")) {
            log.severe("Twitch Client ID not configured in config.yml!");
        }

        if (clientSecret == null) {
            log.severe("Twitch Client Secret not configured in config.yml!");
        }

        if ((clientId != null && !clientId.equals("MISSING_CONFIG") && !clientId.equals("your_twitch_client_id_here")) &&
                clientSecret != null) {
            log.info("TwitchAPI initialized with Client ID: " + clientId);
        } else {
            log.severe("Twitch features will be disabled due to missing configuration.");
        }
    }

    public JsonObject getCachedUserInfo(String accessToken, String twitchId) throws Exception {
        String cacheKey = twitchId + "_userinfo";
        JsonObject cached = userInfoCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        JsonObject freshData = getUserInfo(accessToken);
        userInfoCache.put(cacheKey, freshData);
        return freshData;
    }

    public String[] startDeviceFlow() throws Exception {
        String response = makeHttpRequest(
                "https://id.twitch.tv/oauth2/device" +
                        "?client_id=" + clientId +
                        "&scope=user:read:email+channel:read:subscriptions",
                "POST"
        );

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        // Add proper error handling
        if (json.has("error")) {
            String error = json.get("error").getAsString();
            String errorDescription = json.has("error_description") ?
                    json.get("error_description").getAsString() : "Unknown error";
            throw new Exception("Device flow error: " + error + " - " + errorDescription);
        }

        String deviceCode = json.get("device_code").getAsString();
        String userCode = json.get("user_code").getAsString();
        String verificationUri = json.get("verification_uri").getAsString();
        int interval = json.get("interval").getAsInt();

        return new String[]{deviceCode, userCode, verificationUri, String.valueOf(interval)};
    }

    public String getAccessToken(String deviceCode) throws Exception {
        String response = makeHttpRequest(
                "https://id.twitch.tv/oauth2/token" +
                        "?client_id=" + clientId +
                        "&device_code=" + deviceCode +
                        "&grant_type=urn:ietf:params:oauth:grant-type:device_code",
                "POST"
        );

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        // Check for errors first
        if (json.has("error")) {
            String error = json.get("error").getAsString();
            String errorDescription = json.has("error_description") ?
                    json.get("error_description").getAsString() : "Unknown error";
            throw new Exception("Token error: " + error + " - " + errorDescription);
        }

        String accessToken = json.get("access_token").getAsString();
        String refreshToken = json.get("refresh_token").getAsString();

        return accessToken + "|" + refreshToken;
    }

    private String getSecureSecret(String configKey) {
        String secret = plugin.getConfig().getString(configKey);
        if (secret == null || secret.trim().isEmpty() || secret.equals("your_twitch_client_secret_here")) {
            secret = System.getenv("TWITCH_CLIENT_SECRET"); // <-- real env var
            if (secret == null || secret.trim().isEmpty()) {
                log.severe("FATAL: Twitch Client Secret not configured! Set twitch.client-secret in config.yml or TWITCH_CLIENT_SECRET env.");
                return null;
            }
        }
        return secret;
    }

    private String getConfigValue(String path) {
        String value = plugin.getConfig().getString(path);
        if (value == null || value.trim().isEmpty()) {
            log.severe("Missing config value: " + path);
            return "MISSING_CONFIG";
        }
        return value;
    }

    /**
     * Get complete user info from Twitch API
     */
    public JsonObject getUserInfo(String accessToken) throws Exception {
        String response = makeHttpRequest(
                "https://api.twitch.tv/helix/users",
                "GET",
                "Authorization", "Bearer " + accessToken,
                "Client-Id", clientId
        );

        return JsonParser.parseString(response).getAsJsonObject();
    }

    /**
     * Get stream info from Twitch API
     */
    public JsonObject getStreamInfo(String accessToken, String twitchId) throws Exception {
        String response = makeHttpRequest(
                "https://api.twitch.tv/helix/streams?user_id=" + twitchId,
                "GET",
                "Authorization", "Bearer " + accessToken,
                "Client-Id", clientId
        );

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray data = json.getAsJsonArray("data");

        if (data.size() > 0) {
            return data.get(0).getAsJsonObject();
        }
        return null;
    }

    /**
     * Get just the Twitch user ID
     */
    public String getTwitchUserId(String accessToken) throws Exception {
        JsonObject userJson = getUserInfo(accessToken);
        return userJson.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
    }

    /**
     * Get the Twitch username (login name)
     */
    public String getTwitchUsername(String accessToken) throws Exception {
        JsonObject userJson = getUserInfo(accessToken);
        return userJson.getAsJsonArray("data").get(0).getAsJsonObject().get("login").getAsString();
    }

    /**
     * Get the Twitch display name
     */
    public String getTwitchDisplayName(String accessToken) throws Exception {
        JsonObject userJson = getUserInfo(accessToken);
        return userJson.getAsJsonArray("data").get(0).getAsJsonObject().get("display_name").getAsString();
    }

    /**
     * Core HTTP request handler with rate limit detection
     */
    private String makeHttpRequest(String urlString, String method, String... headers) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);

        // Add headers if provided
        for (int i = 0; i < headers.length; i += 2) {
            connection.setRequestProperty(headers[i], headers[i + 1]);
        }

        int responseCode = connection.getResponseCode();

        // Handle rate limiting (429 status code)
        if (responseCode == 429) {
            String retryAfter = connection.getHeaderField("Ratelimit-Retry-After");
            throw new Exception("Twitch API rate limit exceeded. Try again in " + retryAfter + " seconds");
        }

        // Handle other non-200 responses
        if (responseCode != 200) {
            // Try to read error details
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }

                // Provide more specific error messages
                if (responseCode == 401) {
                    throw new Exception("Access token expired or invalid (401). Token needs refresh.");
                } else if (responseCode == 403) {
                    throw new Exception("Forbidden (403): Check your Twitch API permissions");
                } else if (responseCode == 404) {
                    throw new Exception("Not found (404): Invalid API endpoint");
                } else {
                    throw new Exception("HTTP request failed (" + responseCode + "): " + errorResponse.toString());
                }
            } catch (Exception e) {
                throw new Exception("HTTP request failed with code: " + responseCode);
            }
        }

        // Read successful response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    public String getBroadcasterType(String accessToken) throws Exception {
        JsonObject userJson = getUserInfo(accessToken);
        JsonObject userData = userJson.getAsJsonArray("data").get(0).getAsJsonObject();

        // Broadcaster type can be: "", "affiliate", or "partner"
        return userData.get("broadcaster_type").getAsString();
    }

    public String getAccessTokenFromRefresh(String refreshToken) throws Exception {
        String response = makeHttpRequest(
                "https://id.twitch.tv/oauth2/token" +
                        "?client_id=" + clientId +
                        "&client_secret=" + clientSecret +
                        "&refresh_token=" + refreshToken +
                        "&grant_type=refresh_token",
                "POST"
        );

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return json.get("access_token").getAsString();
    }

    public String getValidAccessToken(UUID playerId, String currentToken, String refreshToken) throws Exception {
        if (validateAccessToken(currentToken)) {
            return currentToken;
        }

        plugin.getLogger().info("Access token expired for " + playerId + ", refreshing...");
        try {
            String newToken = getAccessTokenFromRefresh(refreshToken);
            plugin.getDataManager().updateAccessToken(playerId, newToken);
            return newToken;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to refresh token for " + playerId + ": " + e.getMessage());
            throw new Exception("Token refresh failed", e);
        }
    }

    public boolean validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return false;
        }
        try {
            String response = makeHttpRequest(
                    "https://id.twitch.tv/oauth2/validate",
                    "GET",
                    "Authorization", "Bearer " + accessToken
            );

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.has("client_id") && json.has("user_id") && !json.has("error");
        } catch (Exception e) {
            plugin.getLogger().warning("Token validation failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isUserLive(String accessToken, String twitchId) throws Exception {
        String cacheKey = twitchId + "_live";
        Boolean cached = liveStatusCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        String response = makeHttpRequest(
                "https://api.twitch.tv/helix/streams?user_id=" + twitchId,
                "GET",
                "Authorization", "Bearer " + accessToken,
                "Client-Id", clientId
        );

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray data = json.getAsJsonArray("data");

        boolean isLive = data.size() > 0 && !data.get(0).getAsJsonObject().get("type").getAsString().equals("");
        liveStatusCache.put(cacheKey, isLive);
        return isLive;
    }

    public boolean isAffiliate(String accessToken) throws Exception {
        String broadcasterType = getBroadcasterType(accessToken);
        return "affiliate".equals(broadcasterType);
    }

    public boolean isPartner(String accessToken) throws Exception {
        String broadcasterType = getBroadcasterType(accessToken);
        return "partner".equals(broadcasterType);
    }
}