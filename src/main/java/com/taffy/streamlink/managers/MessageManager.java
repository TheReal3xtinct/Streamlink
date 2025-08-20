package com.taffy.streamlink.managers;

import com.taffy.streamlink.streamlink;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class MessageManager extends ManagerBase {

    private final Map<String, String> messages = new HashMap<>();

    public MessageManager(streamlink plugin) {
        super(plugin);
        loadDefaultMessages();
    }

    private void loadDefaultMessages() {
        // Link messages
        messages.put("link.success", "&a✅ Successfully linked to Twitch: &b{displayName}");
        messages.put("link.timeout", "&c❌ Linking timed out. Please try again.");
        messages.put("link.already_linked", "&a✅ Already linked to: &b{displayName}");
        messages.put("link.instructions",
                "&a╔════════ Twitch Linking ════════╗\n" +
                        "&a1. Visit: &b{verificationUri}\n" +
                        "&a2. Enter code: &e{userCode}\n" +
                        "&7This code expires in 10 minutes\n" +
                        "&a╚════════════════════════════════╝");

        // Check messages
        messages.put("check.linked", "&a✅ Linked to: &b{displayName} &7({twitchId})");
        messages.put("check.not_linked", "&e⚠ Your account is not linked to Twitch");
        messages.put("check.suggestion", "&eUse &a/streamlink link &eto connect your account");

        // Live status messages
        messages.put("live.announcement",
                "&6🎥 &b{player} &ais now LIVE on Twitch! &7[{viewers} viewers]\n" +
                        "&fTitle: &e{title}\n" +
                        "&fGame: &e{game}");
        messages.put("offline.announcement", "&7📴 &b{player} &chas gone offline.");

        // Error messages
        messages.put("error.generic", "&c❌ Error: {message}");
        messages.put("error.token_refresh", "&e⚠ Token expired, could not refresh");
        messages.put("error.token_refreshed", "&a↻ Token was automatically refreshed!");

        // Permission messages
        messages.put("permission.partner", "&6&l🎉 You've been granted Twitch Partner perks!");
        messages.put("permission.affiliate", "&a&l🎉 You've been granted Twitch Affiliate perks!");
        messages.put("permission.viewer", "&7You've been granted basic viewer permissions!");
    }

    public void send(Player player, String messageKey) {
        send(player, messageKey, new HashMap<>());
    }

    public void send(Player player, String messageKey, Map<String, String> placeholders) {
        if (player == null || !player.isOnline()) return;

        String message = messages.getOrDefault(messageKey, "&cMessage not found: " + messageKey);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public String getRawMessage(String messageKey, Map<String, String> placeholders) {
        String message = messages.getOrDefault(messageKey, "");

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void broadcast(String messageKey, Map<String, String> placeholders) {
        String message = getRawMessage(messageKey, placeholders);
        plugin.getServer().broadcastMessage(message);
    }

    // Helper methods for common messages
    public void sendLinkInstructions(Player player, String verificationUri, String userCode) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("verificationUri", verificationUri);
        placeholders.put("userCode", userCode);
        send(player, "link.instructions", placeholders);
    }

    public void sendLinkedSuccess(Player player, String displayName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("displayName", displayName);
        send(player, "link.success", placeholders);
    }

    public void sendAlreadyLinked(Player player, String displayName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("displayName", displayName);
        send(player, "link.already_linked", placeholders);
    }
}