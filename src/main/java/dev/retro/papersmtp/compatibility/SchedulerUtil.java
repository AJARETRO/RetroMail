package dev.retro.papersmtp.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class SchedulerUtil {
    private static boolean isFolia = false;
    private static Object asyncScheduler = null;
    private static Object globalRegionScheduler = null;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            isFolia = true;

            // Get schedulers via reflection to compile under older APIs
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncScheduler.invoke(null);

            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            globalRegionScheduler = getGlobalRegionScheduler.invoke(null);
        } catch (Exception e) {
            isFolia = false;
        }
    }

    public static boolean isFolia() {
        return isFolia;
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        if (isFolia && asyncScheduler != null) {
            try {
                Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                Method runNow = asyncSchedulerClass.getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
                runNow.invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) consumer -> runnable.run());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Folia runAsync reflection error: ", e);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runSync(Plugin plugin, Runnable runnable) {
        if (isFolia && globalRegionScheduler != null) {
            try {
                Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Method run = globalSchedulerClass.getMethod("run", Plugin.class, java.util.function.Consumer.class);
                run.invoke(globalRegionScheduler, plugin, (java.util.function.Consumer<Object>) consumer -> runnable.run());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Folia runSync reflection error: ", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runLater(Plugin plugin, Runnable runnable, long ticks) {
        if (isFolia && globalRegionScheduler != null) {
            try {
                Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Method runDelayed = globalSchedulerClass.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
                runDelayed.invoke(globalRegionScheduler, plugin, (java.util.function.Consumer<Object>) consumer -> runnable.run(), ticks);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Folia runLater reflection error: ", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
        }
    }

    public static void runForPlayer(Plugin plugin, Player player, Runnable runnable) {
        if (isFolia) {
            try {
                Method getScheduler = player.getClass().getMethod("getScheduler");
                Object entityScheduler = getScheduler.invoke(player);
                Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Method run = entitySchedulerClass.getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class);
                run.invoke(entityScheduler, plugin, (java.util.function.Consumer<Object>) consumer -> runnable.run(), null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Folia runForPlayer reflection error: ", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}
