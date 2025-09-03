package com.taffy.streamlink.commands;

import com.taffy.streamlink.managers.LogManager;
import com.taffy.streamlink.managers.PlayerDataManager;
import com.taffy.streamlink.managers.UniversalPermissionManager;
import com.taffy.streamlink.streamlink;
import com.taffy.streamlink.utils.DeviceFlowTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.File;

public class StreamLinkCommand implements CommandExecutor {
    private final streamlink plugin;
    private final LogManager log;

    public StreamLinkCommand(streamlink plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogManager();
    }

    private boolean isAdmin(CommandSender sender) {
        if (!(sender instanceof Player p)) return true; // console treated as admin
        return p.isOp() || p.hasPermission("streamlink.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        if (args.length == 0) return showPlayerHelp(player);

        switch (args[0].toLowerCase()) {
            // ---------- player commands ----------
            case "link":     return handleLink(player);
            case "check":    return handleCheck(player);
            case "unlink":   return handleUnlink(player);
            case "points":   return handlePointsStored(player);

            // ---------- admin commands ----------
            case "admin":    return showAdminHelp(player);
            case "setup":    return requireAdmin(player) && handleSetup(player);
            case "migrate":  return requireAdmin(player) && handleMigrate(player);
            case "sync":     return requireAdmin(player) && handleSyncCsv(player, args);
            case "export":   return requireAdmin(player) && handleExport(player, args);
            case "debug":    return requireAdmin(player) && handleDebug(player, args);
            case "streamlabs": return requireAdmin(player) && handleStreamLabs(player);

            default:
                return showPlayerHelp(player);
        }
    }

    private boolean requireAdmin(Player player) {
        if (!isAdmin(player)) {
            player.sendMessage(ChatColor.RED + "You don't have permission for this!");
            return false;
        }
        return true;
    }

    // -------------------- HELP (player/admin) --------------------

    private boolean showPlayerHelp(Player player) {
        boolean isAdmin = player.isOp() || player.hasPermission("streamlink.admin");

        player.sendMessage(ChatColor.GOLD + "╔════════ StreamLink Help ════════╗");
        // Player-facing
        player.sendMessage(ChatColor.YELLOW + "/streamlink link"   + ChatColor.GRAY + " - Link your Twitch account");
        player.sendMessage(ChatColor.YELLOW + "/streamlink check"  + ChatColor.GRAY + " - Check your link status");
        player.sendMessage(ChatColor.YELLOW + "/streamlink unlink" + ChatColor.GRAY + " - Remove Twitch connection");
        player.sendMessage(ChatColor.YELLOW + "/streamlink points" + ChatColor.GRAY + " - Show stored loyalty data");

        if (isAdmin) {
            player.sendMessage(ChatColor.DARK_GRAY + "—");
            player.sendMessage(ChatColor.AQUA + "Admin tools:");
            player.sendMessage(ChatColor.YELLOW + "/streamlink setup"   + ChatColor.GRAY + " - Setup guidance");
            player.sendMessage(ChatColor.YELLOW + "/streamlink migrate" + ChatColor.GRAY + " - Migrate old groups");
            player.sendMessage(ChatColor.YELLOW + "/streamlink streamlabs"+ ChatColor.GRAY + " - Get OAuth link");
            player.sendMessage(ChatColor.YELLOW + "/streamlink sync [file]" + ChatColor.GRAY + " - Import loyalty CSV");
        }
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════╝");
        return true;
    }

    private boolean showAdminHelp(Player player) {
        if (!isAdmin(player)) {
            player.sendMessage(ChatColor.RED + "You don't have permission for this!");
            return true;
        }
        player.sendMessage(ChatColor.DARK_AQUA + "╔════════ StreamLink Admin ════════╗");
        player.sendMessage(ChatColor.AQUA + "/streamlink setup" + ChatColor.GRAY + " - Show setup guidance");
        player.sendMessage(ChatColor.AQUA + "/streamlink migrate" + ChatColor.GRAY + " - Migrate old groups");
        player.sendMessage(ChatColor.YELLOW + "/streamlink streamlabs"+ ChatColor.GRAY + " - Get OAuth link");
        player.sendMessage(ChatColor.AQUA + "/streamlink sync [file]" + ChatColor.GRAY + " - Import loyalty CSV");
        player.sendMessage(ChatColor.DARK_AQUA + "╚══════════════════════════════════╝");
        return true;
    }

    // -------------------- PLAYER COMMANDS --------------------

    private boolean handleLink(Player player) {
        if (plugin.getDataManager().isLinked(player.getUniqueId())) {
            try {
                String accessToken = plugin.getDataManager().getAccessToken(player.getUniqueId());
                if (accessToken != null) {
                    String twitchName = plugin.getTwitchAPI().getTwitchUsername(accessToken).toLowerCase();
                    player.sendMessage(ChatColor.GREEN + "✅ Already linked to: " + ChatColor.AQUA + twitchName);
                } else {
                    player.sendMessage(ChatColor.GREEN + "✅ Your account is already linked to Twitch!");
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GREEN + "✅ Your account is already linked to Twitch!");
                log.warn("Failed to get Twitch display name for " + player.getName() + ": " + e.getMessage());
            }
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/streamlink unlink" + ChatColor.GRAY + " to disconnect");
            return true;
        }

        if (plugin.getActiveTasks().containsKey(player.getUniqueId())) {
            DeviceFlowTask oldTask = plugin.getActiveTasks().get(player.getUniqueId());
            if (oldTask != null) oldTask.cancel();
        }

        try {
            String[] flowInfo = plugin.getTwitchAPI().startDeviceFlow();
            String userCode = flowInfo[1];
            String verificationUri = flowInfo[2];

            player.sendMessage(ChatColor.GREEN + "╔════════ Twitch Linking ════════╗");
            player.sendMessage(ChatColor.GREEN + "1. Visit: " + ChatColor.AQUA + verificationUri);
            player.sendMessage(ChatColor.GREEN + "2. Enter code: " + ChatColor.YELLOW + userCode);
            player.sendMessage(ChatColor.GRAY + "This code expires in 10 minutes");
            player.sendMessage(ChatColor.GREEN + "╚════════════════════════════════╝");

            DeviceFlowTask task = new DeviceFlowTask(plugin, player, flowInfo[0]);
            task.runTaskTimerAsynchronously(plugin, 0, 20 * 5);
            plugin.getActiveTasks().put(player.getUniqueId(), task);

            log.info("Started device flow for " + player.getName());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            log.severe("Twitch linking error for " + player.getName() + ": " + e.getMessage(), e);
        }
        return true;
    }

    private boolean handleCheck(Player player) {
        if (plugin.getDataManager().isLinked(player.getUniqueId())) {
            String twitchId = plugin.getDataManager().getTwitchId(player.getUniqueId());
            try {
                String accessToken = plugin.getDataManager().getAccessToken(player.getUniqueId());
                String refreshToken = plugin.getDataManager().getRefreshToken(player.getUniqueId());

                try {
                    String displayName = plugin.getTwitchAPI().getTwitchDisplayName(accessToken);
                    player.sendMessage(ChatColor.GREEN + "✅ Linked to: " + ChatColor.AQUA + displayName + ChatColor.GRAY + " (" + twitchId + ")");
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("401") && refreshToken != null) {
                        try {
                            String newAccessToken = plugin.getTwitchAPI().getAccessTokenFromRefresh(refreshToken);
                            plugin.getDataManager().updateAccessToken(player.getUniqueId(), newAccessToken);
                            String displayName = plugin.getTwitchAPI().getTwitchDisplayName(newAccessToken);
                            player.sendMessage(ChatColor.GREEN + "✅ Linked to: " + ChatColor.AQUA + displayName + ChatColor.GRAY + " (" + twitchId + ")");
                            player.sendMessage(ChatColor.YELLOW + "↻ Token was automatically refreshed!");
                        } catch (Exception refreshError) {
                            player.sendMessage(ChatColor.GREEN + "✅ Linked to Twitch ID: " + ChatColor.AQUA + twitchId);
                            player.sendMessage(ChatColor.YELLOW + "⚠ Token expired, could not refresh");
                        }
                    } else {
                        throw e;
                    }
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GREEN + "✅ Linked to Twitch ID: " + ChatColor.AQUA + twitchId);
                log.warn("Failed to get Twitch display name for " + player.getName(), e);
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "⚠ Your account is not linked to Twitch");
            player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.GREEN + "/streamlink link" + ChatColor.YELLOW + " to connect your account");
        }
        return true;
    }

    private boolean handleUnlink(Player player) {
        if (!plugin.getDataManager().isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your account isn't linked to Twitch!");
            return true;
        }
        plugin.getDataManager().unlinkPlayer(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Successfully unlinked your Twitch account!");
        log.info("Unlinked Twitch account for " + player.getName());
        return true;
    }

    private boolean handlePointsStored(Player player) {
        if (!plugin.getDataManager().isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "⚠ You need to link your Twitch account first!");
            player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.GREEN + "/streamlink link");
            return true;
        }
        var dm = plugin.getDataManager();
        var data = dm.getPlayerData(player.getUniqueId());
        int points = (data != null) ? data.getLoyaltyPoints() : 0;
        long minutes = (data != null) ? data.getWatchMinutes() : 0L;

        player.sendMessage(ChatColor.GOLD + "StreamLabs (stored)");
        player.sendMessage(ChatColor.GRAY + "Points: " + ChatColor.GREEN + points);
        player.sendMessage(ChatColor.GRAY + "Watch time: " + ChatColor.GREEN + String.format("%.1f", minutes / 60.0) + "h");
        return true;
    }

    // -------------------- ADMIN COMMANDS --------------------

    private boolean handleSetup(Player player) {
        UniversalPermissionManager permManager = plugin.getPermissionManager();

        player.sendMessage(ChatColor.GOLD + "╔════════ StreamLink Setup ════════╗");
        if (permManager.isUsingLuckPerms()) {
            player.sendMessage(ChatColor.GREEN + "✓ LuckPerms detected!");
            player.sendMessage(ChatColor.YELLOW + "Create these groups in LuckPerms:");
            player.sendMessage(ChatColor.AQUA + "- twitch-partner");
            player.sendMessage(ChatColor.AQUA + "- twitch-affiliate");
            player.sendMessage(ChatColor.AQUA + "- twitch-viewer");
            player.sendMessage(ChatColor.AQUA + "- twitch-live");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Using Bukkit permission system");
            player.sendMessage(ChatColor.GREEN + "Permissions will be managed automatically");
        }
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        log.debug("Setup command executed by " + player.getName());
        return true;
    }

    private boolean handleMigrate(Player player) {
        int migrated = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (plugin.getDataManager().isLinked(onlinePlayer.getUniqueId())) {
                plugin.getPermissionManager().migrateOldGroups(onlinePlayer);
                migrated++;
            }
        }
        player.sendMessage(ChatColor.GREEN + "Migrated " + migrated + " players from old group names!");
        log.info("Migration completed by " + player.getName() + ". Migrated " + migrated + " players.");
        return true;
    }

    private boolean handleSyncCsv(Player player, String[] args) {
        boolean dry = false;
        String pathArg = null;
        for (int i = 1; i < args.length; i++) {
            if ("--dry".equalsIgnoreCase(args[i]) || "-n".equalsIgnoreCase(args[i])) dry = true;
            else pathArg = args[i];
        }

        File csv = (pathArg != null)
                ? new File(pathArg)
                : new File(plugin.getDataFolder(), plugin.getConfig().getString("loyalty.csv.path", "loyalty.csv"));

        final boolean dryRun = dry;                 // <-- make final
        final File csvFile = csv;                   // <-- make final

        player.sendMessage(ChatColor.GRAY + (dryRun ? "[DRY-RUN] " : "") +
                "Syncing from: " + ChatColor.AQUA + csvFile.getAbsolutePath());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Make sure PlayerDataManager has this overload: importLoyaltyCsv(File file, boolean dryRun)
            String summary = plugin.getDataManager().importLoyaltyCsv(csvFile, dryRun);
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.YELLOW + summary));
        });
        return true;
    }

    private boolean handleExport(Player player, String[] args) {
        File out = (args.length >= 2) ? new File(args[1])
                : new File(plugin.getDataFolder(), "export-loyalty.csv");
        final File outFile = out; // <-- make final

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = plugin.getDataManager().exportLoyaltyCsv(outFile);
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.YELLOW + result));
        });
        return true;
    }

    private boolean handleDebug(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /streamlink debug <on|off>");
            return true;
        }
        boolean on = "on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]);
        plugin.getConfig().set("debug", on);
        plugin.saveConfig();

        // If you later add a setter, call it here. For now, just inform the user.
        player.sendMessage(ChatColor.GRAY + "Debug mode: " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        log.info("Debug set to " + on + " via command.");
        return true;
    }

    private boolean handleStreamLabs(Player player) {
        // Only show your Worker URL (or docs) to admins
        final String authUrl = "https://streamlink.3xtaffy.workers.dev/";

        player.sendMessage(ChatColor.GOLD + "╔════════ StreamLabs OAuth ════════╗");
        player.sendMessage(ChatColor.YELLOW + "1) Open: " + ChatColor.AQUA + authUrl);
        player.sendMessage(ChatColor.YELLOW + "2) Authenticate → copy Access/Refresh tokens");
        player.sendMessage(ChatColor.YELLOW + "3) Put them in config.yml under 'streamlabs:' and restart");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        return true;
    }
}
