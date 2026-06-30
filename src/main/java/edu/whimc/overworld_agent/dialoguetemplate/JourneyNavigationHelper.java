package edu.whimc.overworld_agent.dialoguetemplate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts Journey navigation without {@code /journey server waypoint} when possible.
 * Journey's command lexer does not allow {@code _} in unquoted identifiers, so name_ids such as
 * {@code poi-underground_habitat} are truncated or rejected by the command parser even though SQL lookup works.
 */
public final class JourneyNavigationHelper {

    private JourneyNavigationHelper() {}

    /**
     * Starts navigation on the main thread using the resolved public waypoint cell.
     *
     * @return true when Journey accepted the navigation request
     */
    public static boolean startNavigationOnMainThread(Player player, String nameId, Logger logger, boolean debugLog) {
        Object manager = journeyPublicWaypointManager();
        if (manager == null) {
            return false;
        }
        Object destinationCell = invokeGetWaypoint(manager, nameId);
        if (destinationCell == null) {
            return false;
        }
        if (!startViaSearchApi(player, destinationCell, logger, debugLog)) {
            return false;
        }
        logger.info("[OverworldAgent][Journey] navigation started via Journey API for "
                + player.getName()
                + " -> "
                + nameId);
        return true;
    }

    /**
     * Schedules {@link #startNavigationOnMainThread} on the server main thread when needed.
     */
    public static void scheduleNavigation(JavaPlugin plugin, Player player, String nameId, Logger logger,
            boolean debugLog, Runnable onApiUnavailable) {
        Runnable start = () -> {
            if (!startNavigationOnMainThread(player, nameId, logger, debugLog)) {
                onApiUnavailable.run();
            }
        };
        if (Bukkit.isPrimaryThread()) {
            start.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, start);
        }
    }

    private static boolean startViaSearchApi(Player player, Object destinationCell, Logger logger, boolean debugLog) {
        try {
            Class<?> provider = Class.forName("net.whimxiqal.journey.JourneyApiProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object searchApi = api.getClass().getMethod("searching").invoke(api);
            Class<?> searchFlagClass = Class.forName("net.whimxiqal.journey.search.SearchFlag");
            Object emptyFlags = Array.newInstance(searchFlagClass, 0);
            Method run = null;
            for (Method candidate : searchApi.getClass().getMethods()) {
                if (!"runPlayerDestinationSearch".equals(candidate.getName()) || candidate.getParameterCount() != 4) {
                    continue;
                }
                Class<?>[] params = candidate.getParameterTypes();
                if (params[0] == java.util.UUID.class && params[2] == boolean.class && params[3].isArray()) {
                    run = candidate;
                    break;
                }
            }
            if (run == null) {
                return false;
            }
            Object future = run.invoke(searchApi, player.getUniqueId(), destinationCell, true, emptyFlags);
            if (future instanceof java.util.concurrent.CompletionStage<?> stage) {
                stage.whenComplete((result, error) -> {
                    if (error != null) {
                        logger.log(Level.WARNING,
                                "[OverworldAgent][Journey] API navigation error for " + player.getName(),
                                error);
                    } else if (debugLog && result != null) {
                        logger.fine("[OverworldAgent][Journey] API navigation completed for "
                                + player.getName()
                                + ": "
                                + result);
                    }
                });
            }
            return true;
        } catch (Exception ex) {
            if (debugLog) {
                logger.fine("[OverworldAgent][Journey] Journey API navigation unavailable: " + ex.getMessage());
            }
            return false;
        }
    }

    private static Object journeyPublicWaypointManager() {
        try {
            Class<?> journeyClass = Class.forName("net.whimxiqal.journey.Journey");
            Object journey = journeyClass.getMethod("get").invoke(null);
            if (journey == null) {
                return null;
            }
            Object proxy = journey.getClass().getMethod("proxy").invoke(journey);
            Object dataManager = proxy.getClass().getMethod("dataManager").invoke(proxy);
            return dataManager.getClass().getMethod("publicWaypointManager").invoke(dataManager);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeGetWaypoint(Object publicWaypointManager, String nameId) {
        try {
            return publicWaypointManager.getClass()
                    .getMethod("getWaypoint", String.class)
                    .invoke(publicWaypointManager, nameId.toLowerCase(Locale.ROOT));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
