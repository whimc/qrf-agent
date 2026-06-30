package edu.whimc.overworld_agent.dialoguetemplate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts Journey navigation the same way {@code /journey server waypoint} does internally.
 * Uses {@code SearchManager.launchIngameSearch} with player flag preferences so trails render.
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
        if (startViaLaunchIngameSearch(player, destinationCell, logger, debugLog)) {
            logger.info("[OverworldAgent][Journey] navigation started via launchIngameSearch for "
                    + player.getName()
                    + " -> "
                    + nameId);
            return true;
        }
        if (startViaSearchApi(player, destinationCell, logger, debugLog)) {
            logger.info("[OverworldAgent][Journey] navigation started via Journey API for "
                    + player.getName()
                    + " -> "
                    + nameId);
            return true;
        }
        return false;
    }

    /**
     * Schedules {@link #startNavigationOnMainThread} on the server main thread when needed.
     */
    public static void scheduleNavigation(JavaPlugin plugin, Player player, String nameId, Logger logger,
            boolean debugLog, Runnable onUnavailable) {
        Runnable start = () -> {
            if (!startNavigationOnMainThread(player, nameId, logger, debugLog)) {
                onUnavailable.run();
            }
        };
        if (Bukkit.isPrimaryThread()) {
            start.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, start);
        }
    }

    /**
     * Mirrors {@code JourneyExecutor.destinationSearch}: player caller, flag preferences, ingame launch.
     */
    private static boolean startViaLaunchIngameSearch(Player player, Object destinationCell, Logger logger,
            boolean debugLog) {
        try {
            Class<?> journeyClass = Class.forName("net.whimxiqal.journey.Journey");
            Object journey = journeyClass.getMethod("get").invoke(null);
            Object proxy = journey.getClass().getMethod("proxy").invoke(journey);
            Object platform = proxy.getClass().getMethod("platform").invoke(proxy);

            Object journeyPlayerOpt = platform.getClass()
                    .getMethod("onlinePlayer", UUID.class)
                    .invoke(platform, player.getUniqueId());
            if (!optionalPresent(journeyPlayerOpt)) {
                return false;
            }
            Object journeyPlayer = optionalValue(journeyPlayerOpt);

            Object startOpt = platform.getClass()
                    .getMethod("entityCellLocation", UUID.class)
                    .invoke(platform, player.getUniqueId());
            if (!optionalPresent(startOpt)) {
                logger.warning("[OverworldAgent][Journey] entityCellLocation empty for " + player.getName());
                return false;
            }
            Object startCell = optionalValue(startOpt);

            Class<?> journeyPlayerClass = Class.forName("net.whimxiqal.journey.JourneyPlayer");
            Class<?> cellClass = Class.forName("net.whimxiqal.journey.Cell");
            Class<?> sessionClass = Class.forName("net.whimxiqal.journey.search.DestinationGoalSearchSession");
            Object session = sessionClass.getConstructor(
                            journeyPlayerClass, cellClass, cellClass, boolean.class, boolean.class)
                    .newInstance(journeyPlayer, startCell, destinationCell, false, true);

            Object searchManager = journey.getClass().getMethod("searchManager").invoke(journey);
            Class<?> flagSetClass = Class.forName("net.whimxiqal.journey.search.flag.FlagSet");
            Object flagPrefs = searchManager.getClass()
                    .getMethod("getFlagPreferences", UUID.class, boolean.class)
                    .invoke(searchManager, player.getUniqueId(), false);
            sessionClass.getMethod("addFlags", flagSetClass).invoke(session, flagPrefs);

            Class<?> searchSessionClass = Class.forName("net.whimxiqal.journey.search.SearchSession");
            Object future = searchManager.getClass()
                    .getMethod("launchIngameSearch", searchSessionClass)
                    .invoke(searchManager, session);
            logSearchCompletion(future, player, logger, debugLog);
            return true;
        } catch (Exception ex) {
            if (debugLog) {
                logger.log(Level.FINE, "[OverworldAgent][Journey] launchIngameSearch unavailable", ex);
            }
            return false;
        }
    }

    /** Fallback when launchIngameSearch reflection fails. */
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
                if (params[0] == UUID.class && params[2] == boolean.class && params[3].isArray()) {
                    run = candidate;
                    break;
                }
            }
            if (run == null) {
                return false;
            }
            Object future = run.invoke(searchApi, player.getUniqueId(), destinationCell, true, emptyFlags);
            logSearchCompletion(future, player, logger, debugLog);
            return true;
        } catch (Exception ex) {
            if (debugLog) {
                logger.fine("[OverworldAgent][Journey] Journey API navigation unavailable: " + ex.getMessage());
            }
            return false;
        }
    }

    private static void logSearchCompletion(Object future, Player player, Logger logger, boolean debugLog) {
        if (!(future instanceof java.util.concurrent.CompletionStage<?> stage)) {
            return;
        }
        stage.whenComplete((result, error) -> {
            if (error != null) {
                logger.log(Level.WARNING,
                        "[OverworldAgent][Journey] navigation error for " + player.getName(),
                        error);
                return;
            }
            if (result == null) {
                logger.warning("[OverworldAgent][Journey] navigation returned null for " + player.getName());
                return;
            }
            try {
                Object state = result.getClass().getMethod("state").invoke(result);
                String stateName = String.valueOf(state);
                if (stateName.contains("SUCCESSFUL")) {
                    if (debugLog) {
                        logger.info("[OverworldAgent][Journey] navigation succeeded for " + player.getName());
                    }
                } else {
                    logger.warning("[OverworldAgent][Journey] navigation finished without trail for "
                            + player.getName()
                            + ": "
                            + stateName);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    private static boolean optionalPresent(Object optional) {
        if (optional instanceof java.util.Optional<?> opt) {
            return opt.isPresent();
        }
        try {
            return (boolean) optional.getClass().getMethod("isPresent").invoke(optional);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static Object optionalValue(Object optional) {
        if (optional instanceof java.util.Optional<?> opt) {
            return opt.orElse(null);
        }
        try {
            return optional.getClass().getMethod("get").invoke(optional);
        } catch (ReflectiveOperationException ex) {
            return null;
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
