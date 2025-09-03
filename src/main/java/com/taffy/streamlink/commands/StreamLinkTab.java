package com.taffy.streamlink.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StreamLinkTab implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        boolean admin = !(sender instanceof Player) || sender.isOp() || sender.hasPermission("streamlink.admin");

        if (args.length == 1) {
            var base = Arrays.asList("link","check","unlink","points","admin");
            var adminOnly = Arrays.asList("setup","migrate","sync","export","debug");
            return (admin ? concat(base, adminOnly) : base).stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && admin) {
            if ("sync".equalsIgnoreCase(args[0])) return Arrays.asList("--dry", "<path>");
            if ("debug".equalsIgnoreCase(args[0])) return Arrays.asList("on","off");
            if ("export".equalsIgnoreCase(args[0])) return Arrays.asList("<path>");
        }
        return List.of();
    }

    private static List<String> concat(List<String> a, List<String> b){
        return new java.util.ArrayList<>() {{ addAll(a); addAll(b); }};
    }
}
