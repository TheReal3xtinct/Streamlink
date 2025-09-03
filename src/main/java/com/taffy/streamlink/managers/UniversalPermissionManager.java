package com.taffy.streamlink.managers;

import com.taffy.streamlink.streamlink;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UniversalPermissionManager extends ManagerBase {
    private final LuckPerms luckPerms;
    private final ConcurrentHashMap<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();
    private boolean useLuckPerms = false;
    private boolean useEssentials = false;

    public UniversalPermissionManager(streamlink plugin) {
        super(plugin);
        var reg = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        this.luckPerms = (reg != null) ? reg.getProvider() : null;
        detectPermissionPlugins();
    }

    private void detectPermissionPlugins() {
        // Check for LuckPerms
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms != null && luckPerms.isEnabled()) {
            useLuckPerms = true;
            log.info("LuckPerms detected! Using LuckPerms for permission management.");
        }

        // Check for Essentials
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials != null && essentials.isEnabled()) {
            useEssentials = true;
            log.info("Essentials detected!");
        }

        if (!useLuckPerms) {
            log.info("Using Bukkit's native permission system.");
        }
    }

    public void applyTwitchRank(Player player, String accessToken) {
        try {
            String broadcasterType = plugin.getTwitchAPI().getBroadcasterType(accessToken);

            // Remove any previous Twitch permissions first
            removeSecondaryGroupsOnly(player);

            switch (broadcasterType) {
                case "partner":
                    applyPartnerPermissions(player);
                    player.sendMessage("§6§l🎉 You've been granted Twitch Partner perks!");
                    break;
                case "affiliate":
                    applyAffiliatePermissions(player);
                    player.sendMessage("§a§l🎉 You've been granted Twitch Affiliate perks!");
                    break;
                default:
                    applyViewerPermissions(player);
                    player.sendMessage("§7You've been granted basic viewer permissions!");
                    break;
            }
        } catch (Exception e) {
            log.warn("Failed to apply Twitch rank for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void applyTwitchRankAsync(Player player, String accessToken) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                applyTwitchRank(player, accessToken);
            } catch (Exception e) {
                log.warn("Async permission error: " + e.getMessage());
            }
        });
    }

    private void applyPartnerPermissions(Player player) {
        if (useLuckPerms) {
            setPrimaryLuckPermsGroup(player, "twitch-partner");
        } else {
            addBukkitPermissions(player, getPartnerPermissions());
        }
    }

    private void applyAffiliatePermissions(Player player) {
        if (useLuckPerms) {
            setPrimaryLuckPermsGroup(player, "twitch-affiliate");
        } else {
            addBukkitPermissions(player, getAffiliatePermissions());
        }
    }

    private void applyViewerPermissions(Player player) {
        if (useLuckPerms) {
            setPrimaryLuckPermsGroup(player, "twitch-viewer");
        } else {
            addBukkitPermissions(player, getViewerPermissions());
        }
    }

    private void removeSecondaryGroupsOnly(Player player) {
        if (!useLuckPerms) return;

        // Remove only secondary groups, preserve primary Twitch group
        removeSecondaryLuckPermsGroup(player, "twitch-live");

        // Remove any loyalty groups if you have them
        removeSecondaryLuckPermsGroup(player, "streamlabs-tier1");
        removeSecondaryLuckPermsGroup(player, "streamlabs-tier2");
        removeSecondaryLuckPermsGroup(player, "streamlabs-tier3");
        removeSecondaryLuckPermsGroup(player, "streamlabs-tier4");

        log.info("Removed secondary groups from " + player.getName() + " (primary group preserved)");
    }

    // LuckPerms Integration - Set PRIMARY group
    private void setPrimaryLuckPermsGroup(Player player, String groupName) {
        if (!useLuckPerms) return;

        // ADD DEBUG LOGGING:
        log.debug("SETTING PRIMARY GROUP: " + player.getName() + " -> " + groupName);
        log.debug("Stack trace:");
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(stackTrace.length, 7); i++) {
            log.debug("  at " + stackTrace[i].toString());
        }

        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            user.setPrimaryGroup(groupName);
            luckPerms.getUserManager().saveUser(user);
            log.info("Set " + player.getName() + "'s primary group to " + groupName);
        });
    }

    private void addSecondaryLuckPermsGroup(Player player, String groupName) {
        if (!useLuckPerms) return;

        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            user.data().add(Node.builder("group." + groupName).build());
            luckPerms.getUserManager().saveUser(user);
            log.info("Added " + player.getName() + " to secondary group: " + groupName);
        });
    }

    private void removeSecondaryLuckPermsGroup(Player player, String groupName) {
        if (!useLuckPerms) return;

        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            user.data().remove(Node.builder("group." + groupName).build());
            luckPerms.getUserManager().saveUser(user);
            log.info("Removed " + player.getName() + " from secondary group: " + groupName);
        });
    }

    private void removeLuckPermsGroups(Player player) {
        if (!useLuckPerms) return;

        try {
            removeSecondaryGroupsOnly(player);

        } catch (Exception e) {
            log.warn("Failed to remove LuckPerms groups: " + e.getMessage());
        }
    }

    // Bukkit Native Permission System
    private void addBukkitPermissions(Player player, String[] permissions) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PermissionAttachment attachment = permissionAttachments.computeIfAbsent(
                    player.getUniqueId(),
                    uuid -> player.addAttachment(plugin)
            );

            for (String permission : permissions) {
                attachment.setPermission(permission, true);
            }

            log.info("Added permissions to " + player.getName());
        });
    }

    private void removeBukkitPermissions(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PermissionAttachment attachment = permissionAttachments.remove(player.getUniqueId());
            if (attachment != null) {
                player.removeAttachment(attachment);
            }
        });
    }

    private void removeSpecificBukkitPermissions(Player player, String[] permissions) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PermissionAttachment attachment = permissionAttachments.get(player.getUniqueId());
            if (attachment != null) {
                for (String permission : permissions) {
                    attachment.unsetPermission(permission);
                }
                log.info("Removed specific permissions from " + player.getName());
            }
        });
    }

    private void fallbackToBukkitPermissions(Player player, String groupType) {
        String[] permissions;
        switch (groupType) {
            case "twitch-partner":
                permissions = getPartnerPermissions();
                break;
            case "twitch-affiliate":
                permissions = getAffiliatePermissions();
                break;
            case "twitch-viewer":
                permissions = getViewerPermissions();
                break;
            default:
                permissions = getViewerPermissions();
                break;
        }
        addBukkitPermissions(player, permissions);
    }

    // Permission configuration methods - Read from config
    private String[] getPartnerPermissions() {
        return plugin.getConfig().getStringList("permissions.bukkit.partner-permissions")
                .toArray(new String[0]);
    }

    private String[] getAffiliatePermissions() {
        return plugin.getConfig().getStringList("permissions.bukkit.affiliate-permissions")
                .toArray(new String[0]);
    }

    private String[] getViewerPermissions() {
        return plugin.getConfig().getStringList("permissions.bukkit.viewer-permissions")
                .toArray(new String[0]);
    }

    private String[] getLivePermissions() {
        return new String[]{
                "streamlink.viewer",
                "streamlink.basic",
                "streamlink.live",
                "streamlink.emotes.extra",
                "streamlink.interact",
                "streamlink.alert"
        };
    }

    private java.util.List<String> lpTierGroups(int tier) {
        var base = "permissions.luckperms.tiers.tier" + tier;
        return plugin.getConfig().getStringList(base);
    }

    public void migrateOldGroups(Player player) {
        if (!useLuckPerms) return;

        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            if ("twitch-streamer".equals(user.getPrimaryGroup())) {
                user.setPrimaryGroup("twitch-viewer");
                luckPerms.getUserManager().saveUser(user);
                log.info("Migrated " + player.getName() + " from twitch-streamer to twitch-viewer");
            }
        });
    }

    public void cleanupPlayer(Player player) {
        removeBukkitPermissions(player);
        permissionAttachments.remove(player.getUniqueId());
    }

    public void applyLivePermissions(Player player) {
        if (useLuckPerms) {
            addSecondaryLuckPermsGroup(player, "twitch-live");
        } else {
            addBukkitPermissions(player, getLivePermissions());
        }

        // Add special live-only permissions
        addBukkitPermissions(player, new String[]{
                "streamlink.live",
                "streamlink.notifications"
        });
    }

    public void removeLivePermissions(Player player) {
        if (useLuckPerms) {
            removeSecondaryLuckPermsGroup(player, "twitch-live");
        } else {
            removeSpecificBukkitPermissions(player, getLivePermissions());
        }

        // Remove live-only permissions
        removeSpecificBukkitPermissions(player, new String[]{
                "streamlink.live",
                "streamlink.notifications"
        });
    }

    public void applyTier1Permissions(Player player) {
        if (useLuckPerms) {
            for (String g : lpTierGroups(1)) addSecondaryLuckPermsGroup(player, g);
        } else {
            addBukkitPermissions(player, new String[]{"streamlink.loyalty.tier1","streamlink.emotes.basic","streamlink.color.basic"});
        }
        player.sendMessage("§a§l🎉 You've reached Loyalty Tier 1!");
    }

    public void applyTier2Permissions(Player player) {
        if (useLuckPerms) {
            for (String g : lpTierGroups(1)) addSecondaryLuckPermsGroup(player, g);
        } else {
            addBukkitPermissions(player, new String[]{"streamlink.loyalty.tier1","streamlink.emotes.basic","streamlink.color.basic"});
        }
        player.sendMessage("§a§l🎉 You've reached Loyalty Tier 1!");
    }

    public void applyTier3Permissions(Player player) {
        if (useLuckPerms) {
            for (String g : lpTierGroups(1)) addSecondaryLuckPermsGroup(player, g);
        } else {
            addBukkitPermissions(player, new String[]{"streamlink.loyalty.tier1","streamlink.emotes.basic","streamlink.color.basic"});
        }
        player.sendMessage("§a§l🎉 You've reached Loyalty Tier 1!");
    }

    public void applyTier4Permissions(Player player) {
        if (useLuckPerms) {
            for (String g : lpTierGroups(1)) addSecondaryLuckPermsGroup(player, g);
        } else {
            addBukkitPermissions(player, new String[]{"streamlink.loyalty.tier1","streamlink.emotes.basic","streamlink.color.basic"});
        }
        player.sendMessage("§a§l🎉 You've reached Loyalty Tier 1!");
    }

    public boolean isUsingLuckPerms() {
        return useLuckPerms;
    }

    public boolean isUsingEssentials() {
        return useEssentials;
    }

    public void removeLoyaltyPermissions(Player player) {
        if (useLuckPerms) {
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier1");
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier2");
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier3");
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier4");
        } else {
            removeSpecificBukkitPermissions(player, new String[]{
                    "streamlink.loyalty.tier1",
                    "streamlink.loyalty.tier2",
                    "streamlink.loyalty.tier3",
                    "streamlink.loyalty.tier4",
                    "streamlink.emotes.basic",
                    "streamlink.emotes.advanced",
                    "streamlink.emotes.all",
                    "streamlink.emotes.unlimited",
                    "streamlink.color.basic",
                    "streamlink.color.rainbow",
                    "streamlink.color.gradient",
                    "streamlink.color.animated",
                    "streamlink.vip.chat",
                    "streamlink.vip.all",
                    "streamlink.vip.ultimate",
                    "streamlink.fly.basic",
                    "streamlink.fly.creative",
                    "streamlink.teleport"
            });
        }
    }
    public void removeTierPermissions(Player player) {
        if (useLuckPerms) {
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier1");
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier2");
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier3");
            removeSecondaryLuckPermsGroup(player, "streamlabs-tier4");
        } else {
            removeSpecificBukkitPermissions(player, new String[]{
                    "streamlink.loyalty.tier1",
                    "streamlink.loyalty.tier2",
                    "streamlink.loyalty.tier3",
                    "streamlink.loyalty.tier4",
                    "streamlink.emotes.basic",
                    "streamlink.emotes.advanced",
                    "streamlink.emotes.all",
                    "streamlink.emotes.unlimited",
                    "streamlink.color.basic",
                    "streamlink.color.rainbow",
                    "streamlink.color.gradient",
                    "streamlink.color.animated",
                    "streamlink.vip.chat",
                    "streamlink.vip.all",
                    "streamlink.vip.ultimate",
                    "streamlink.fly.basic",
                    "streamlink.fly.creative",
                    "streamlink.teleport"
            });
        }
        log.info("Removed all loyalty tier permissions from " + player.getName());
    }
}