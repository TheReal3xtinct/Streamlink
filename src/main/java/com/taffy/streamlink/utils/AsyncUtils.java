package com.taffy.streamlink.utils;

import com.taffy.streamlink.streamlink;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AsyncUtils {

    private AsyncUtils() {
        // Utility class - prevent instantiation
    }

    // Basic async execution
    public static void runAsync(streamlink plugin, Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void runAsyncIf(streamlink plugin, boolean condition, Runnable task) {
        if (condition) {
            runAsync(plugin, task);
        }
    }

    // Scheduled async tasks
    public static BukkitTask runTimerAsync(streamlink plugin, Runnable task, long delay, long period) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
    }

    public static BukkitTask runLaterAsync(streamlink plugin, Runnable task, long delay) {
        return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
    }

    // CompletableFuture wrappers
    public static <T> CompletableFuture<T> supplyAsync(streamlink plugin, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runAsync(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // Renamed this method to avoid conflict
    public static CompletableFuture<Void> runAsyncFuture(streamlink plugin, Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAsync(plugin, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}