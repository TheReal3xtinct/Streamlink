package com.taffy.streamlink.managers;

import com.taffy.streamlink.models.PlayerData;
import com.taffy.streamlink.streamlink;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager extends ManagerBase {
    private FileConfiguration playerDataConfig;
    private File playerDataFile;
    private final Map<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();

    public PlayerDataManager(streamlink plugin) {
        super(plugin);
        setupPlayerData();
        loadAllPlayerData();
    }

    private void setupPlayerData() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            plugin.saveResource("playerdata.yml", false);
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
        log.info("Player data loaded successfully");
    }

    private void loadAllPlayerData() {
        ConfigurationSection linksSection = playerDataConfig.getConfigurationSection("links");
        if (linksSection != null) {
            for (String uuidString : linksSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidString);
                    PlayerData playerData = loadPlayerData(playerId);
                    playerCache.put(playerId, playerData);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID in playerdata: " + uuidString);
                }
            }
        }
        log.info("Loaded " + playerCache.size() + " players into cache");
    }

    public void linkPlayer(UUID playerId, String twitchId, String accessToken, String refreshToken, String twitchUsername) {
        PlayerData playerData = getOrCreatePlayerData(playerId);
        playerData.setTwitchId(twitchId);
        playerData.setAccessToken(accessToken);
        playerData.setRefreshToken(refreshToken);
        playerData.setTwitchUsername(twitchUsername);

        savePlayerData(playerData);
        playerCache.put(playerId, playerData);

        log.debug("Linked player " + playerId + " to Twitch: " + twitchUsername);
    }

    public PlayerData getPlayerData(UUID playerId) {
        return playerCache.get(playerId);
    }

    public PlayerData getOrCreatePlayerData(UUID playerId) {
        return playerCache.computeIfAbsent(playerId, id -> {
            PlayerData data = new PlayerData(id);
            // Try to load from config if it exists
            PlayerData savedData = loadPlayerData(id);
            return savedData != null ? savedData : data;
        });
    }

    private PlayerData loadPlayerData(UUID playerId) {
        String basePath = "links." + playerId.toString();
        if (!playerDataConfig.contains(basePath)) {
            return null;
        }

        PlayerData playerData = new PlayerData(playerId);
        playerData.setTwitchId(playerDataConfig.getString(basePath + ".twitchId"));
        playerData.setTwitchUsername(playerDataConfig.getString(basePath + ".twitchUsername"));
        playerData.setAccessToken(playerDataConfig.getString(basePath + ".accessToken"));
        playerData.setRefreshToken(playerDataConfig.getString(basePath + ".refreshToken"));
        playerData.setLive(playerDataConfig.getBoolean(basePath + ".isLive", false));
        playerData.setLoyaltyPoints(playerDataConfig.getInt(basePath + ".loyaltyPoints", 0));

        return playerData;
    }

    private void savePlayerData(PlayerData playerData) {
        String basePath = "links." + playerData.getPlayerId().toString();
        playerDataConfig.set(basePath + ".twitchId", playerData.getTwitchId());
        playerDataConfig.set(basePath + ".twitchUsername", playerData.getTwitchUsername());
        playerDataConfig.set(basePath + ".accessToken", playerData.getAccessToken());
        playerDataConfig.set(basePath + ".refreshToken", playerData.getRefreshToken());
        playerDataConfig.set(basePath + ".isLive", playerData.isLive());
        playerDataConfig.set(basePath + ".loyaltyPoints", playerData.getLoyaltyPoints());

        saveConfig();
    }

    public boolean isLinked(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        return playerData != null && playerData.isLinked();
    }

    public String getTwitchId(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        return playerData != null ? playerData.getTwitchId() : null;
    }

    public String getAccessToken(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        return playerData != null ? playerData.getAccessToken() : null;
    }

    public String getRefreshToken(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        return playerData != null ? playerData.getRefreshToken() : null;
    }

    public String getTwitchUsername(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        return playerData != null ? playerData.getTwitchUsername() : null;
    }

    public void updateAccessToken(UUID playerId, String newAccessToken) {
        PlayerData playerData = getPlayerData(playerId);
        if (playerData != null) {
            playerData.setAccessToken(newAccessToken);
            savePlayerData(playerData);
            log.debug("Updated access token for player " + playerId);
        }
    }

    public void setLiveStatus(UUID playerId, boolean isLive) {
        PlayerData playerData = getPlayerData(playerId);
        if (playerData != null) {
            playerData.setLive(isLive);
            savePlayerData(playerData);
        }
    }

    public boolean isLive(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        return playerData != null && playerData.isLive();
    }

    public void unlinkPlayer(UUID playerId) {
        playerDataConfig.set("links." + playerId.toString(), null);
        playerCache.remove(playerId);
        saveConfig();
        log.info("Unlinked player " + playerId + " from Twitch");
    }

    public Set<UUID> getAllLinkedPlayers() {
        return new HashSet<>(playerCache.keySet());
    }

    private void saveConfig() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            log.severe("Could not save playerdata.yml: " + e.getMessage(), e);
        }
    }

    public void backupPlayerData() {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) backupDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupFile = new File(backupDir, "playerdata-backup-" + timestamp + ".yml");

        try {
            Files.copy(playerDataFile.toPath(), backupFile.toPath());
            log.info("Player data backed up to: " + backupFile.getName());
        } catch (IOException e) {
            log.warn("Failed to create backup: " + e.getMessage(), e);
        }
    }
}
