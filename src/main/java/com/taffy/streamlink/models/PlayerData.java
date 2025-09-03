package com.taffy.streamlink.models;

import java.util.UUID;

public class PlayerData {
    private final UUID playerId;
    private String twitchId;
    private String twitchUsername;
    private String accessToken;
    private String refreshToken;
    private boolean isLive;
    private int loyaltyPoints;
    private long watchMinutes;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }
    public String getTwitchId() { return twitchId; }
    public void setTwitchId(String twitchId) { this.twitchId = twitchId; }
    public String getTwitchUsername() { return twitchUsername; }
    public void setTwitchUsername(String twitchUsername) { this.twitchUsername = twitchUsername; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public boolean isLive() { return isLive; }
    public void setLive(boolean live) { isLive = live; }
    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    public long getWatchMinutes() { return watchMinutes; }
    public void setWatchMinutes(long minutes) { this.watchMinutes = minutes; }

    public boolean isLinked() {
        return twitchId != null && !twitchId.isEmpty()
                && accessToken != null && !accessToken.isEmpty();
    }
}