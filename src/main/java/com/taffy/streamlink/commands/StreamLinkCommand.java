package com.taffy.streamlink.commands;

import com.taffy.streamlink.managers.LogManager;
import com.taffy.streamlink.managers.UniversalPermissionManager;
import com.taffy.streamlink.streamlink;
import com.taffy.streamlink.utils.DeviceFlowTask;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StreamLinkCommand implements CommandExecutor {
    private final streamlink plugin;
    private final LogManager log;
    private final Map<UUID, DeviceFlowTask> activeTasks = new HashMap<>();

    public StreamLinkCommand(streamlink plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        if (args.length == 0) {
            return showHelp(player);
        }

        switch (args[0].toLowerCase()) {
            case "link":
                return handleLink(player);
            case "check":
                return handleCheck(player);
            case "unlink":
                return handleUnlink(player);
            case "setup":
                return handleSetup(player);
            case "migrate":
                return handleMigrate(player);
            default:
                return showHelp(player);
        }
    }

    private boolean showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════════ StreamLink Help ════════╗");
        player.sendMessage(ChatColor.YELLOW + "/streamlink link" + ChatColor.GRAY + " - Link your Twitch account");
        player.sendMessage(ChatColor.YELLOW + "/streamlink check" + ChatColor.GRAY + " - Check your link status");
        player.sendMessage(ChatColor.YELLOW + "/streamlink unlink" + ChatColor.GRAY + " - Remove Twitch connection");
        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════╝");
        return true;
    }

    private boolean handleLink(Player player) {
        if (plugin.getDataManager().isLinked(player.getUniqueId())) {
            try {
                String accessToken = plugin.getDataManager().getAccessToken(player.getUniqueId());
                if (accessToken != null) {
                    String twitchName = plugin.getTwitchAPI().getTwitchDisplayName(accessToken);
                    player.sendMessage(ChatColor.GREEN + "✅ Already linked to: " +
                            ChatColor.AQUA + twitchName);
                } else {
                    player.sendMessage(ChatColor.GREEN + "✅ Your account is already linked to Twitch!");
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GREEN + "✅ Your account is already linked to Twitch!");
                log.warn("Failed to get Twitch display name for " + player.getName() + ": " + e.getMessage());
            }
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW +
                    "/streamlink unlink" + ChatColor.GRAY + " to disconnect");
            return true;
        }

        if (plugin.getActiveTasks().containsKey(player.getUniqueId())) {
            DeviceFlowTask oldTask = plugin.getActiveTasks().get(player.getUniqueId());
            if (oldTask != null) {
                oldTask.cancel();
            }
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

                // Try to get display name with current token
                try {
                    String displayName = plugin.getTwitchAPI().getTwitchDisplayName(accessToken);
                    player.sendMessage(ChatColor.GREEN + "✅ Linked to: " +
                            ChatColor.AQUA + displayName + ChatColor.GRAY + " (" + twitchId + ")");
                } catch (Exception e) {
                    // Token expired, try to refresh it
                    if (e.getMessage().contains("401") && refreshToken != null) {
                        try {
                            String newAccessToken = plugin.getTwitchAPI().getAccessTokenFromRefresh(refreshToken);
                            plugin.getDataManager().updateAccessToken(player.getUniqueId(), newAccessToken);

                            // Try again with new token
                            String displayName = plugin.getTwitchAPI().getTwitchDisplayName(newAccessToken);
                            player.sendMessage(ChatColor.GREEN + "✅ Linked to: " +
                                    ChatColor.AQUA + displayName + ChatColor.GRAY + " (" + twitchId + ")");
                            player.sendMessage(ChatColor.YELLOW + "↻ Token was automatically refreshed!");
                        } catch (Exception refreshError) {
                            player.sendMessage(ChatColor.GREEN + "✅ Linked to Twitch ID: " +
                                    ChatColor.AQUA + twitchId);
                            player.sendMessage(ChatColor.YELLOW + "⚠ Token expired, could not refresh");
                        }
                    } else {
                        throw e; // Re-throw if it's not a 401 error
                    }
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GREEN + "✅ Linked to Twitch ID: " +
                        ChatColor.AQUA + twitchId);
                log.warn("Failed to get Twitch display name for " + player.getName(), e);
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "⚠ Your account is not linked to Twitch");
            player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.GREEN + "/streamlink link" +
                    ChatColor.YELLOW + " to connect your account");
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

    private boolean handleSetup(Player player) {
        if (!player.hasPermission("streamlink.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission for this!");
            return true;
        }

        UniversalPermissionManager permManager = plugin.getPermissionManager();

        player.sendMessage(ChatColor.GOLD + "╔════════ StreamLink Setup ════════╗");

        if (permManager.isUsingLuckPerms()) {
            player.sendMessage(ChatColor.GREEN + "✓ LuckPerms detected!");
            player.sendMessage(ChatColor.YELLOW + "Please create these groups in LuckPerms:");
            player.sendMessage(ChatColor.AQUA + "- twitch-partner");
            player.sendMessage(ChatColor.AQUA + "- twitch-affiliate");
            player.sendMessage(ChatColor.AQUA + "- twitch-viewer");
            player.sendMessage(ChatColor.AQUA + "- twitch-live");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Using Bukkit permission system");
            player.sendMessage(ChatColor.GREEN + "Permissions will be automatically managed");
        }

        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        log.debug("Setup command executed by " + player.getName());
        return true;
    }

    private boolean handleMigrate(Player player) {
        if (!player.hasPermission("streamlink.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission for this!");
            return true;
        }

        // Migrate all online players
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
}