package com.taffy.streamlink.managers;

import com.taffy.streamlink.models.PlayerData;
import com.taffy.streamlink.streamlink;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
        playerData.setTwitchUsername(twitchUsername.toLowerCase());

        savePlayerData(playerData);
        playerCache.put(playerId, playerData);

        log.debug("Linked player " + playerId + " to Twitch: " + twitchUsername);

        // Register with StreamLabs manager
        if (plugin.getStreamLabsManager() != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.getStreamLabsManager().registerPlayerTwitchName(player, twitchUsername);
            }
        }
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
        playerData.setWatchMinutes(playerDataConfig.getLong(basePath + ".watchMinutes", 0L));

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
        playerDataConfig.set(basePath + ".watchMinutes", playerData.getWatchMinutes());

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

    public void setLoyaltyPoints(UUID playerId, int points) {
        PlayerData data = getOrCreatePlayerData(playerId);
        data.setLoyaltyPoints(points);
        savePlayerData(data); // this already writes .loyaltyPoints to playerdata.yml
    }

    public void setLiveStatus(UUID playerId, boolean isLive) {
        PlayerData playerData = getPlayerData(playerId);
        if (playerData != null) {
            playerData.setLive(isLive);
            savePlayerData(playerData);
        }
    }

    public void setWatchMinutes(UUID playerId, long minutes) {
        PlayerData data = getOrCreatePlayerData(playerId);
        data.setWatchMinutes(minutes);
        savePlayerData(data);
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

    private void setLoyaltyPointsNoFlush(UUID playerId, int points) {
        PlayerData data = getOrCreatePlayerData(playerId);
        data.setLoyaltyPoints(points);
        // no save here
    }
    private void setWatchMinutesNoFlush(UUID playerId, long minutes) {
        PlayerData data = getOrCreatePlayerData(playerId);
        data.setWatchMinutes(minutes);
        // no save here
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

    /* ================== CSV IMPORT (for /streamlink sync) ================== */

    /**
     * Import a CSV of loyalty data. Expected headers (case-insensitive):
     *   username, points, minutes  (minutes = watch time in minutes)
     * Only updates players who are already linked.
     *
     * @param csvFile CSV file path
     * @return summary string
     */
    /** dryRun=true → only counts & previews, no writes. */
    public String importLoyaltyCsv(File csvFile) {
        return importLoyaltyCsv(csvFile, false);
    }

    /** dryRun=true → only counts & previews, no writes. */
    public String importLoyaltyCsv(File csvFile, boolean dryRun) {
        if (!csvFile.exists()) {
            return "CSV not found: " + csvFile.getAbsolutePath();
        }

        int updated = 0, skippedNoLink = 0, skippedParse = 0, totalRows = 0;
        List<String> examples   = new ArrayList<>();
        List<String> notLinked  = new ArrayList<>();

        // header aliases: from config, or sensible defaults if missing
        List<String> watchHeaders = plugin.getConfig().getStringList("loyalty.csv_watch_minutes_headers");
        if (watchHeaders == null || watchHeaders.isEmpty()) {
            watchHeaders = Arrays.asList("minutes","time","watchtimeminutes","watch_time_minutes","time_watched");
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {

            String header = br.readLine();
            if (header == null) return "Empty CSV.";
            String[] headerCols = splitCsv(header);

            int idxUser   = indexOf(headerCols, "username");
            if (idxUser < 0) idxUser = indexOf(headerCols, "name");

            int idxPoints = indexOf(headerCols, "points");

            int idxMinutes = -1;
            for (String h : watchHeaders) {
                idxMinutes = indexOf(headerCols, h);
                if (idxMinutes >= 0) break;
            }

            if (idxUser < 0 || idxPoints < 0) {
                return "CSV must contain at least 'username' and 'points' columns.";
            }

            String line;
            while ((line = br.readLine()) != null) {
                totalRows++;
                String[] cols = splitCsv(line);
                if (cols.length <= Math.max(idxUser, Math.max(idxPoints, Math.max(idxMinutes, 0)))) {
                    skippedParse++; continue;
                }

                String username = cols[idxUser] == null ? "" : cols[idxUser].trim().toLowerCase();
                if (username.isEmpty()) { skippedParse++; continue; }

                Integer points = tryParseInt(cols[idxPoints]);
                if (points == null) { skippedParse++; continue; }

                Long minutes = (idxMinutes >= 0) ? tryParseLong(cols[idxMinutes]) : null;

                UUID playerId = findLinkedPlayerByTwitchUsername(username);
                if (playerId == null) {
                    skippedNoLink++;
                    if (notLinked.size() < 10) notLinked.add(username);
                    continue;
                }

                if (!dryRun) {
                    // update in-memory objects only; flush once after loop
                    setLoyaltyPointsNoFlush(playerId, points);
                    if (minutes != null) setWatchMinutesNoFlush(playerId, minutes);
                } else {
                    examples.add(username + " -> points=" + points + (minutes!=null? (", minutes=" + minutes) : ""));
                }
                updated++;
            }

            // single disk write if we actually changed anything
            if (!dryRun && updated > 0) {
                saveConfig();
            }

        } catch (Exception e) {
            log.warn("CSV import failed: " + e.getMessage(), e);
            return "Import failed: " + e.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(dryRun ? "[DRY-RUN] " : "")
                .append("Sync complete. Updated=").append(updated)
                .append(", Skipped (not linked)=").append(skippedNoLink)
                .append(", Skipped (parse errors)=").append(skippedParse)
                .append(", Total rows=").append(totalRows);

        if (!notLinked.isEmpty()) {
            sb.append(". Not linked (first 10): ").append(String.join(", ", notLinked));
        }
        if (dryRun && !examples.isEmpty()) {
            sb.append(". Examples: ").append(String.join("; ", examples.subList(0, Math.min(10, examples.size()))));
        }
        return sb.toString();
    }

    public String exportLoyaltyCsv(File outFile) {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            bw.write("Username,Points,Minutes\n");
            for (Map.Entry<UUID, PlayerData> e : playerCache.entrySet()) {
                PlayerData d = e.getValue();
                if (d == null || d.getTwitchUsername() == null) continue;
                String u = d.getTwitchUsername();
                int pts = d.getLoyaltyPoints();
                long mins = d.getWatchMinutes();
                bw.write(escape(u) + "," + pts + "," + mins + "\n");
            }
            return "Exported " + playerCache.size() + " rows to " + outFile.getAbsolutePath();
        } catch (Exception ex) {
            log.warn("CSV export failed: " + ex.getMessage(), ex);
            return "Export failed: " + ex.getMessage();
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"","\"\"") + "\"";
        }
        return s;
    }

    private UUID findLinkedPlayerByTwitchUsername(String usernameLower) {
        for (Map.Entry<UUID, PlayerData> e : playerCache.entrySet()) {
            PlayerData d = e.getValue();
            if (d != null && d.getTwitchUsername() != null &&
                    d.getTwitchUsername().equalsIgnoreCase(usernameLower)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
    private static Long tryParseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    // very light CSV splitter (handles simple quoted fields)
    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // escaped quote ""
                    cur.append('"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes; // toggle quote mode
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null && headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}
