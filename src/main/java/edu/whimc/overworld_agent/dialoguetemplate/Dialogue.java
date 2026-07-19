package edu.whimc.overworld_agent.dialoguetemplate;




import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.traits.AgentFollowTuning;
import edu.whimc.overworld_agent.traits.AgentPermanentFlyingTrait;
import edu.whimc.overworld_agent.dialoguetemplate.models.Chatbot;
import edu.whimc.overworld_agent.dialoguetemplate.models.DialoguePrompt;
import edu.whimc.overworld_agent.llm.context.AgentChatEvent;
import edu.whimc.overworld_agent.llm.context.LearnerActivityContextProvider;
import edu.whimc.overworld_agent.llm.research.AgentChatResearchLogger;
import edu.whimc.overworld_agent.llm.research.AgentChatResearchTurn;

import edu.whimc.overworld_agent.utils.AgentEntityTypes;
import edu.whimc.overworld_agent.utils.AgentPermissions;
import edu.whimc.overworld_agent.utils.CitizensSkinUrls;
import edu.whimc.overworld_agent.utils.Utils;
import edu.whimc.sciencetools.models.sciencetool.ScienceTool;
import edu.whimc.sciencetools.models.sciencetool.ScienceToolMeasureEvent;
import me.blackvein.quests.Objective;
import me.blackvein.quests.Quest;
import me.blackvein.quests.Quests;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import net.citizensnpcs.trait.SkinTrait;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.StringUtils;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;


public class Dialogue implements Listener {
    private SpigotCallback spigotCallback;
    /* Unicode for bullet character */
    private static final int GUIDANCE_NEAREST_LIMIT = 3;
    private static final String BULLET = "\u2022";
    private OverworldAgent plugin;
    private Player player;
    private final int PROFANITY_LABEL = -1;
    private final int UNKNOWN_LABEL = -2;
    private final double THRESHOLD = .5;
    private final int AGENT_EDIT_NUM = 5;
    private static final int MAX_DISCUSSION_HISTORY = 10;
    private static final String DIALOGUE_DISCUSSION_COMMAND = "dialogue_discussion";
    private String feedback;
    private String response;
    private boolean text;
    private boolean embodied;
    private Map<Integer, DialoguePrompt> prompts;
    /** Short-term memory for the ongoing free-discussion chat; only sent to the LLM path. */
    private final List<String> discussionHistory = new ArrayList<>();
    private String discussionConversationId;
    private String discussionSessionId;
    private int discussionTurnIndex;
    private DialoguePrompt builtInUnknownPrompt;
    /** Prevents overlapping free-discussion turns while activity + LLM are in flight. */
    private boolean discussionBusy;

    public Dialogue(OverworldAgent plugin, Player player, boolean text, boolean embodied) {
        this.spigotCallback = plugin.getSpigotCallback();
        this.plugin = plugin;
        this.player = player;
        feedback = "";
        //Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        response = "";
        this.text = text;
        this.embodied = embodied;
        prompts = new HashMap<>();

        String path = "prompts";
        List<Map<?, ?>> entries = plugin.getConfig().getMapList(path);
        for (Map<?, ?> entry : entries) {
            int label =  Integer.parseInt(String.valueOf(entry.get("label")));
            this.prompts.put(label,new DialoguePrompt(entry));
        }
    }

    private static String quoteIfNeeded(String value) {
        if (value == null) return "";
        if (value.contains(" ")) {
            return "\"" + value + "\"";
        }
        return value;
    }

    /**
     * Runs a public server waypoint journey. Uses {@code /journey server waypoint <name_id>}, not {@code /jt}:
     * {@code jt} is the {@code journeyto} root and only does scoped name resolution, which merges NPC/world scopes and
     * can throw {@code IllegalStateException: Duplicate key} when keys collide; {@code journey server waypoint} uses
     * {@link net.whimxiqal.journey.data.PublicWaypointManager#getWaypoint} directly (see Journey's grammar).
     */
    private void dispatchJourneyCommand(Player player, String rawDestination) {
        String destination = StringUtils.trimToEmpty(rawDestination);
        if (destination.isEmpty()) {
            plugin.getLogger().fine("[OverworldAgent][Journey] dispatch skipped: empty destination for " + player.getName());
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        String nameId = resolveJourneyPublicNameId(destination);
        Object manager = journeyPublicWaypointManager();
        Object waypoint = manager == null ? null : invokeGetWaypoint(manager, nameId);
        String displayName = waypoint == null ? null : extractWaypointName(waypoint);
        if (displayName == null && manager != null && waypoint != null) {
            displayName = lookupPublicWaypointDisplayName(manager, nameId);
        }
        Integer waypointDomain = waypoint == null ? null : waypointCellDomain(waypoint);
        Integer playerDomain = journeyDomainForWorldSafe(player.getWorld());
        boolean preferApi = !"command".equalsIgnoreCase(StringUtils.trimToEmpty(
                cfg.getString("journey.dispatch-command", "auto")));

        String journeyRoot = cfg.getString("journey.journey-command-root", "journey");
        String journeytoRoot = StringUtils.trimToEmpty(cfg.getString("journey.journeyto-command-root", "jt"));
        if (journeytoRoot.isBlank()) {
            journeytoRoot = "jt";
        }
        List<String> commands = buildJourneyDispatchCommands(cfg, journeyRoot, journeytoRoot, nameId, displayName);
        String primaryCmd = commands.get(0);

        if (waypoint == null) {
            plugin.getLogger().warning(
                    "[OverworldAgent][Journey] PublicWaypointManager.getWaypoint('"
                            + nameId
                            + "') returned null before dispatch for "
                            + player.getName()
                            + ". The id may not be in Journey's server-public scope even if it appears in listwaypoints.");
        } else if (cfg.getBoolean("journey.debug-log", false)) {
            plugin.getLogger().info(
                    "[OverworldAgent][Journey] resolved waypoint nameId="
                            + nameId
                            + " label="
                            + displayName
                            + " waypointDomain="
                            + waypointDomain
                            + " playerDomain="
                            + playerDomain
                            + " playerWorld="
                            + player.getWorld().getName());
        }

        if (!(preferApi && waypoint != null)) {
            plugin.getLogger().info(
                    "[OverworldAgent][Journey] dispatch as player "
                            + player.getName()
                            + ": /"
                            + primaryCmd
                            + (commands.size() > 1 ? " (fallbacks: " + commands.subList(1, commands.size()) + ")" : "")
                            + " (nameId="
                            + nameId
                            + ", raw="
                            + rawDestination
                            + ", dispatch-command="
                            + cfg.getString("journey.dispatch-command", "auto")
                            + ")");
        }
        Runnable runCommandDispatch = () -> {
            Throwable lastError = null;
            for (int attempt = 0; attempt < commands.size(); attempt++) {
                String cmd = commands.get(attempt);
                try {
                    if (player.performCommand(cmd)) {
                        plugin.getLogger().info(
                                "[OverworldAgent][Journey] performCommand returned true for "
                                        + player.getName()
                                        + " (/"
                                        + cmd
                                        + "). If no trail appears, Journey rejected the destination or could not pathfind—check in-game Journey messages.");
                        return;
                    }
                    plugin.getLogger().warning(
                            "[OverworldAgent][Journey] performCommand returned false for "
                                    + player.getName()
                                    + " (/"
                                    + cmd
                                    + ")");
                    lastError = null;
                } catch (Throwable ex) {
                    lastError = ex;
                    String dupLabel = extractDuplicateKeyLabel(ex);
                    if (dupLabel != null && attempt + 1 < commands.size()) {
                        plugin.getLogger().warning(
                                "[OverworldAgent][Journey] /"
                                        + cmd
                                        + " failed: duplicate Journey scope name \""
                                        + dupLabel
                                        + "\". Trying fallback /"
                                        + commands.get(attempt + 1));
                        continue;
                    }
                    plugin.getLogger().log(Level.WARNING, "Journey navigation failed for " + player.getName() + ": " + cmd, ex);
                    if (dupLabel != null) {
                        Utils.msgNoPrefix(player,
                                ChatColor.RED + "Journey could not start navigation: duplicate destination name \""
                                        + dupLabel
                                        + "\" in Journey scopes. An admin should rename or remove the duplicate in journey_waypoints / NPC data.");
                    } else {
                        Utils.msgNoPrefix(player, ChatColor.RED + "Journey failed to start navigation. See the server log for details.");
                    }
                    return;
                }
            }
            if (lastError == null) {
                Utils.msgNoPrefix(player,
                        ChatColor.RED + "Journey did not run that command. Try /" + primaryCmd + " manually.");
            }
        };
        if (preferApi && waypoint != null) {
            boolean debug = cfg.getBoolean("journey.debug-log", false);
            JourneyNavigationHelper.scheduleNavigation(plugin, player, nameId, plugin.getLogger(), debug, runCommandDispatch);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            runCommandDispatch.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runCommandDispatch);
        }
    }

    private static List<String> buildJourneyDispatchCommands(FileConfiguration cfg, String journeyRoot, String journeytoRoot,
            String nameId, String displayName) {
        String mode = StringUtils.trimToEmpty(cfg.getString("journey.dispatch-command", "auto"));
        if ("auto".equalsIgnoreCase(mode)) {
            return List.of(journeyRoot + " server waypoint " + quoteIfNeeded(nameId));
        }
        String primary = switch (mode.toLowerCase(Locale.ROOT)) {
            case "journeyto_plain" -> journeytoRoot + " " + quoteIfNeeded(nameId);
            case "journeyto_scoped" -> journeytoRoot + " server:" + nameId;
            case "server_waypoint_display" -> journeyRoot + " server waypoint "
                    + quoteIfNeeded(StringUtils.isNotBlank(displayName) ? displayName : nameId);
            default -> journeyRoot + " server waypoint " + quoteIfNeeded(nameId);
        };
        String fallback = journeyRoot + " server waypoint " + quoteIfNeeded(nameId);
        if (primary.equals(fallback)) {
            return List.of(primary);
        }
        return List.of(primary, fallback);
    }

    private static String extractDuplicateKeyLabel(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null || !m.contains("Duplicate key")) {
                continue;
            }
            int start = m.indexOf("Duplicate key ");
            if (start < 0) {
                return m;
            }
            start += "Duplicate key ".length();
            int end = m.indexOf(" (", start);
            return end > start ? m.substring(start, end) : m.substring(start);
        }
        return null;
    }

    private static boolean throwableChainMessageContains(Throwable ex, String fragment) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && m.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static final class JourneyWaypointChoice implements JourneyLlmBridge.JourneyWaypointChoiceView {
        final String jtKey;
        final String label;
        /** World position when known (for nearest-destination ranking). */
        final Location location;

        JourneyWaypointChoice(String jtKey, String label) {
            this(jtKey, label, null);
        }

        JourneyWaypointChoice(String jtKey, String label, Location location) {
            this.jtKey = jtKey;
            this.label = (label != null && !label.isBlank()) ? label : jtKey;
            this.location = location;
        }

        @Override
        public String jtKey() {
            return jtKey;
        }

        @Override
        public String label() {
            return label;
        }
    }

    private static List<JourneyWaypointChoice> sortUniqueChoices(Collection<JourneyWaypointChoice> choices) {
        Map<String, JourneyWaypointChoice> byKey = new LinkedHashMap<>();
        for (JourneyWaypointChoice c : choices) {
            if (c != null && c.jtKey != null && !c.jtKey.isBlank()) {
                String key = c.jtKey.toLowerCase(Locale.ROOT);
                JourneyWaypointChoice existing = byKey.get(key);
                // Prefer the entry that has a location when merging duplicates.
                if (existing == null || (existing.location == null && c.location != null)) {
                    byKey.put(key, c);
                }
            }
        }
        List<JourneyWaypointChoice> out = new ArrayList<>(byKey.values());
        out.sort(Comparator.comparing(c -> c.label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static boolean isPoiOrNpcKey(String jtKey) {
        if (jtKey == null) {
            return false;
        }
        String key = jtKey.toLowerCase(Locale.ROOT);
        return key.startsWith("poi-") || key.startsWith("npc-");
    }

    /**
     * Picks up to {@code limit} nearest POI/NPC destinations on the player's current world.
     */
    private static List<JourneyWaypointChoice> nearestGuidanceWaypointSample(
            Player player, List<JourneyWaypointChoice> source, int limit) {
        if (player == null || source == null || source.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }
        List<JourneyWaypointChoice> ranked = new ArrayList<>();
        for (JourneyWaypointChoice c : source) {
            if (c == null || !isPoiOrNpcKey(c.jtKey)) {
                continue;
            }
            if (c.location == null || c.location.getWorld() == null || !c.location.getWorld().equals(world)) {
                continue;
            }
            ranked.add(c);
        }
        ranked.sort(Comparator.comparingDouble(c -> c.location.distanceSquared(origin)));
        int take = Math.min(limit, ranked.size());
        return new ArrayList<>(ranked.subList(0, take));
    }

    private static List<Object> flattenWaypointContainer(Object all) {
        List<Object> out = new ArrayList<>();
        if (all instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object v = entry.getValue();
                if (v != null) {
                    out.add(v);
                }
            }
        } else if (all instanceof Iterable<?> it) {
            for (Object v : it) {
                if (v != null) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static String adventureComponentToPlain(Object component) {
        if (component == null) {
            return null;
        }
        try {
            Class<?> serCl = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object serializer;
            try {
                Method plainText = serCl.getMethod("plainText");
                serializer = plainText.invoke(null);
            } catch (NoSuchMethodException e) {
                Method get = serCl.getMethod("get");
                serializer = get.invoke(null);
            }
            Class<?> compCl = Class.forName("net.kyori.adventure.text.Component");
            Method serialize = serializer.getClass().getMethod("serialize", compCl);
            Object text = serialize.invoke(serializer, component);
            return text == null ? null : String.valueOf(text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer journeyDomainForWorldSafe(World world) {
        if (world == null) {
            return null;
        }
        try {
            Class<?> providerCl = Class.forName("net.whimxiqal.journey.bukkit.JourneyBukkitApiProvider");
            Method get = providerCl.getMethod("get");
            Object api = get.invoke(null);
            if (api == null) {
                return null;
            }
            Method toDomain = api.getClass().getMethod("toDomain", World.class);
            Object id = toDomain.invoke(api, world);
            if (id instanceof Integer) {
                return (Integer) id;
            }
            if (id instanceof Number) {
                return ((Number) id).intValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Integer waypointCellDomain(Object waypoint) {
        if (waypoint == null) {
            return null;
        }
        Integer direct = cellDomainIndex(waypoint);
        if (direct != null) {
            return direct;
        }
        Object cell = null;
        for (String accessor : new String[] {"location", "cell", "getLocation", "getCell"}) {
            try {
                Method m = waypoint.getClass().getMethod(accessor);
                if (m.getParameterCount() != 0) {
                    continue;
                }
                cell = m.invoke(waypoint);
                if (cell != null) {
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (cell == null) {
            return null;
        }
        try {
            Method domain = cell.getClass().getMethod("domain");
            Object d = domain.invoke(cell);
            if (d instanceof Integer) {
                return (Integer) d;
            }
            if (d instanceof Number) {
                return ((Number) d).intValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static String extractWaypointJtKey(Object waypoint) {
        if (waypoint == null) {
            return null;
        }
        String[] methodNames = {"nameId", "getNameId", "getName_id", "publicNameId", "getPublicNameId", "destinationId"};
        for (String methodName : methodNames) {
            try {
                Method m = waypoint.getClass().getMethod(methodName);
                if (m.getParameterCount() != 0) {
                    continue;
                }
                Object v = m.invoke(waypoint);
                if (v == null) {
                    continue;
                }
                String s = String.valueOf(v);
                if (!s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            java.lang.reflect.RecordComponent[] components = waypoint.getClass().getRecordComponents();
            if (components != null) {
                for (java.lang.reflect.RecordComponent rc : components) {
                    if (!"nameId".equals(rc.getName()) && !"name_id".equals(rc.getName())) {
                        continue;
                    }
                    Object v = rc.getAccessor().invoke(waypoint);
                    if (v != null) {
                        String s = String.valueOf(v);
                        if (!s.isBlank()) {
                            return s;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static String extractWaypointName(Object waypoint) {
        try {
            Method m = waypoint.getClass().getMethod("name");
            Object v = m.invoke(waypoint);
            if (v != null) {
                if (!(v instanceof String)) {
                    String plain = adventureComponentToPlain(v);
                    if (plain != null && !plain.isBlank()) {
                        return plain;
                    }
                } else {
                    return String.valueOf(v);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method m = waypoint.getClass().getMethod("getName");
            Object v = m.invoke(waypoint);
            return v == null ? null : String.valueOf(v);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static JourneyWaypointChoice choiceFromWaypointObject(Object waypoint, Object publicWaypointManager) {
        String label = extractWaypointName(waypoint);
        String key = extractWaypointJtKey(waypoint);
        if (key == null || key.isBlank() || invokeGetWaypoint(publicWaypointManager, key) == null) {
            key = resolveNameIdForWaypoint(publicWaypointManager, waypoint, label);
        }
        if (key == null || key.isBlank() || invokeGetWaypoint(publicWaypointManager, key) == null) {
            return null;
        }
        key = key.toLowerCase(Locale.ROOT);
        if (label == null || label.isBlank()) {
            label = key;
        }
        Location location = locationFromWaypointCell(waypoint);
        return new JourneyWaypointChoice(key, label, location);
    }

    private static Location locationFromWaypointCell(Object waypoint) {
        Object cell = extractWaypointCell(waypoint);
        Integer[] coords = cellCoords(cell);
        if (coords == null) {
            return null;
        }
        World world = null;
        Integer domain = cellDomainIndex(cell);
        if (domain != null) {
            world = worldForJourneyDomain(domain);
        }
        if (world == null) {
            return null;
        }
        return new Location(world, coords[0] + 0.5, coords[1], coords[2] + 0.5);
    }

    private static World worldForJourneyDomain(int domain) {
        for (World world : Bukkit.getWorlds()) {
            Integer id = journeyDomainForWorldSafe(world);
            if (id != null && id == domain) {
                return world;
            }
        }
        return null;
    }

    /**
     * Journey stores {@code name_id} (e.g. {@code npc-jorgeperezgallego}) separately from the friendly
     * {@code name} column ({@code Dr. Jorge Perez Gallego}). {@code getWaypoint} only accepts name_id.
     */
    private static String resolveNameIdForWaypoint(Object publicWaypointManager, Object waypoint, String displayName) {
        if (publicWaypointManager == null) {
            return displayName == null ? null : displayName.toLowerCase(Locale.ROOT);
        }
        String fromRecord = resolveNameIdFromWaypointRecord(publicWaypointManager, waypoint);
        if (fromRecord != null) {
            return fromRecord;
        }
        for (String candidate : buildNameIdCandidates(displayName)) {
            if (invokeGetWaypoint(publicWaypointManager, candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    /** Journey SQL stores name_id separately from display name; it is not always exposed on {@code Waypoint}. */
    private static String resolveNameIdFromWaypointRecord(Object publicWaypointManager, Object waypoint) {
        if (waypoint == null || publicWaypointManager == null) {
            return null;
        }
        try {
            java.lang.reflect.RecordComponent[] components = waypoint.getClass().getRecordComponents();
            if (components == null) {
                return null;
            }
            for (java.lang.reflect.RecordComponent rc : components) {
                String rcName = rc.getName();
                if ("nameId".equals(rcName) || "name_id".equals(rcName) || "id".equals(rcName)) {
                    Object v = rc.getAccessor().invoke(waypoint);
                    if (v != null) {
                        String s = String.valueOf(v).trim();
                        if (!s.isBlank() && invokeGetWaypoint(publicWaypointManager, s) != null) {
                            return s.toLowerCase(Locale.ROOT);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private String resolveJourneyPublicNameId(String rawInput) {
        Object manager = journeyPublicWaypointManager();
        if (manager == null || StringUtils.isBlank(rawInput)) {
            return StringUtils.trimToEmpty(rawInput).toLowerCase(Locale.ROOT);
        }
        for (String candidate : buildNameIdCandidates(rawInput)) {
            if (invokeGetWaypoint(manager, candidate) != null) {
                return candidate;
            }
        }
        String trimmed = rawInput.trim();
        if (trimmed.matches("(?i)(npc|poi)-[a-z0-9_-]+")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private Object journeyPublicWaypointManager() {
        try {
            Class<?> journeyClass = Class.forName("net.whimxiqal.journey.Journey");
            Object journey = journeyClass.getMethod("get").invoke(null);
            if (journey == null) {
                return null;
            }
            Object dataManager = journeyDataManager(journey);
            if (dataManager == null) {
                return null;
            }
            return dataManager.getClass().getMethod("publicWaypointManager").invoke(dataManager);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean nameIdMatchesWaypoint(Object publicWaypointManager, String nameId, Object waypoint) {
        if (publicWaypointManager == null || nameId == null || nameId.isBlank()) {
            return false;
        }
        Object found = invokeGetWaypoint(publicWaypointManager, nameId);
        if (found == null) {
            return false;
        }
        if (waypoint == null) {
            return true;
        }
        Object expected = extractWaypointCell(waypoint);
        if (expected == null) {
            return true;
        }
        return cellsMatch(found, expected);
    }

    private static Object invokeGetWaypoint(Object publicWaypointManager, String nameId) {
        try {
            return publicWaypointManager.getClass().getMethod("getWaypoint", String.class).invoke(publicWaypointManager, nameId);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String lookupPublicWaypointDisplayName(Object publicWaypointManager, String nameId) {
        if (publicWaypointManager == null || StringUtils.isBlank(nameId)) {
            return null;
        }
        try {
            Object all = publicWaypointManager.getClass().getMethod("getAll").invoke(publicWaypointManager);
            if (all == null) {
                return null;
            }
            String needle = nameId.toLowerCase(Locale.ROOT);
            for (Object item : flattenWaypointContainer(all)) {
                String label = extractWaypointName(item);
                String key = resolveNameIdForWaypoint(publicWaypointManager, item, label);
                if (needle.equals(key)) {
                    return StringUtils.isNotBlank(label) ? label : nameId;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static Object extractWaypointCell(Object waypoint) {
        if (waypoint == null) {
            return null;
        }
        if (cellCoords(waypoint) != null) {
            return waypoint;
        }
        for (String accessor : new String[] {"location", "cell", "getLocation", "getCell"}) {
            try {
                Method m = waypoint.getClass().getMethod(accessor);
                if (m.getParameterCount() != 0) {
                    continue;
                }
                Object cell = m.invoke(waypoint);
                if (cell != null) {
                    return cell;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static boolean cellsMatch(Object cellA, Object cellB) {
        if (cellA == null || cellB == null) {
            return false;
        }
        Integer[] a = cellCoords(cellA);
        Integer[] b = cellCoords(cellB);
        if (a == null || b == null) {
            return false;
        }
        if (!a[0].equals(b[0]) || !a[1].equals(b[1]) || !a[2].equals(b[2])) {
            return false;
        }
        Integer domainA = cellDomainIndex(cellA);
        Integer domainB = cellDomainIndex(cellB);
        return domainA == null || domainB == null || domainA.equals(domainB);
    }

    private static Integer[] cellCoords(Object cell) {
        if (cell == null) {
            return null;
        }
        for (String[] accessors : new String[][] {
                {"blockX", "blockY", "blockZ"},
                {"x", "y", "z"},
                {"getBlockX", "getBlockY", "getBlockZ"},
                {"getX", "getY", "getZ"},
        }) {
            try {
                int x = ((Number) cell.getClass().getMethod(accessors[0]).invoke(cell)).intValue();
                int y = ((Number) cell.getClass().getMethod(accessors[1]).invoke(cell)).intValue();
                int z = ((Number) cell.getClass().getMethod(accessors[2]).invoke(cell)).intValue();
                return new Integer[] {x, y, z};
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Integer cellDomainIndex(Object cell) {
        if (cell == null) {
            return null;
        }
        try {
            Method domain = cell.getClass().getMethod("domain");
            Object d = domain.invoke(cell);
            if (d instanceof Integer) {
                return (Integer) d;
            }
            if (d instanceof Number) {
                return ((Number) d).intValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /** Candidate {@code name_id} values for Journey SQL lookups (WHIMC uses npc-/poi- prefixes). */
    private static List<String> buildNameIdCandidates(String displayName) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (displayName == null || displayName.isBlank()) {
            return List.of();
        }
        String trimmed = displayName.trim();
        String fullSlug = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (!fullSlug.isBlank()) {
            addSlugVariants(out, fullSlug);
        }
        String[] words = trimmed.split("\\s+");
        int start = 0;
        if (words.length > 1 && words[0].matches("(?i)(dr|mr|mrs|ms|prof)\\.?")) {
            start = 1;
        }
        addWordJoinSlugs(out, words, start);
        if (start > 0) {
            addWordJoinSlugs(out, words, 0);
        }
        return new ArrayList<>(out);
    }

    private static void addSlugVariants(LinkedHashSet<String> out, String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        String s = slug.toLowerCase(Locale.ROOT);
        out.add(s);
        out.add("npc-" + s);
        out.add("poi-" + s);
    }

    /** Builds concatenated, hyphen-, and underscore-joined slugs (e.g. {@code solar_panel_power}). */
    private static void addWordJoinSlugs(LinkedHashSet<String> out, String[] words, int start) {
        StringBuilder concat = new StringBuilder();
        StringBuilder hyphen = new StringBuilder();
        StringBuilder underscore = new StringBuilder();
        for (int i = start; i < words.length; i++) {
            String w = words[i].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (w.isBlank()) {
                continue;
            }
            concat.append(w);
            if (hyphen.length() > 0) {
                hyphen.append('-');
            }
            hyphen.append(w);
            if (underscore.length() > 0) {
                underscore.append('_');
            }
            underscore.append(w);
        }
        addSlugVariants(out, concat.toString());
        String hyphenSlug = hyphen.toString();
        String underscoreSlug = underscore.toString();
        if (!hyphenSlug.isBlank() && !hyphenSlug.contentEquals(concat)) {
            addSlugVariants(out, hyphenSlug);
        }
        if (!underscoreSlug.isBlank()
                && !underscoreSlug.contentEquals(concat)
                && !underscoreSlug.contentEquals(hyphenSlug)) {
            addSlugVariants(out, underscoreSlug);
        }
    }

    private void openJourneyDestinationTextInput() {
        List<String> instruct = Arrays.asList(
                Utils.color("&0&lJourney"),
                "",
                Utils.color("&7Enter the waypoint or destination name in your next chat message."));
        openJourneyTextWithRetry(instruct);
    }

    private void openJourneyTextWithRetry(List<String> instruct) {
        plugin.getChatTextInputFactory().open(player, instruct, text -> {
            if (StringUtils.isBlank(text)) {
                Utils.msgNoPrefix(player, ChatColor.RED + "Please enter a destination in chat.");
                openJourneyTextWithRetry(instruct);
                return;
            }
            String destination = text.trim();
            plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Guidance"), id -> {
                dispatchJourneyCommand(player, destination);
            });
        });
    }

    private void openFreeDiscussionChatInput() {
        List<String> instruct = Arrays.asList(
                Utils.color("&0&lDiscuss"),
                "",
                Utils.color("&aAI chat mode started! Type what you want to say to your agent in chat."),
                Utils.color("&7You can keep going back and forth as an ongoing chat."),
                Utils.color("&7(When the LLM is enabled, this text will be sent there.)"));
        plugin.getChatTextInputFactory().openSession(player, instruct, text -> {
            if (StringUtils.isBlank(text)) {
                Utils.msgNoPrefix(player, ChatColor.RED + "Enter a message in chat, or type stop to end the chat.");
                return;
            }
            if (discussionBusy) {
                Utils.msgNoPrefix(player, ChatColor.GRAY + "Please wait for the previous reply before sending another message.");
                return;
            }
            response = StringUtils.trimToEmpty(text);
            // Echo privately because the public chat event is cancelled while in chat mode.
            Utils.msgNoPrefix(player, "&7You: &f" + response);
            doResponse();
        });
    }

    /**
     * Journey exposes {@link net.whimxiqal.journey.data.DataManager} at {@code Journey.get().proxy().dataManager()},
     * not as {@code Journey.dataManager()} (older code assumed it lived on {@code Journey} directly).
     */
    private static Object journeyDataManager(Object journey) {
        if (journey == null) {
            return null;
        }
        try {
            Method proxyMethod = journey.getClass().getMethod("proxy");
            Object proxy = proxyMethod.invoke(journey);
            if (proxy != null) {
                Method dm = proxy.getClass().getMethod("dataManager");
                return dm.invoke(proxy);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method legacy = journey.getClass().getMethod("dataManager");
            return legacy.invoke(journey);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Public Journey destinations for optional {@code domainFilter} (Journey world ids).
     * {@code null} = all domains. {@code jtKey} prefers {@code name_id}-style accessors when present.
     */
    private List<JourneyWaypointChoice> collectJourneyPublicWaypoints(Set<Integer> domainFilter) {
        try {
            Class<?> journeyClass = Class.forName("net.whimxiqal.journey.Journey");
            Method getMethod = journeyClass.getMethod("get");
            Object journey = getMethod.invoke(null);
            if (journey == null) {
                return Collections.emptyList();
            }

            Object dataManager = journeyDataManager(journey);
            if (dataManager == null) {
                return Collections.emptyList();
            }

            Method publicWaypointManagerMethod = dataManager.getClass().getMethod("publicWaypointManager");
            Object publicWaypointManager = publicWaypointManagerMethod.invoke(dataManager);
            if (publicWaypointManager == null) {
                return Collections.emptyList();
            }

            Method getAllMethod = publicWaypointManager.getClass().getMethod("getAll");
            Object all = getAllMethod.invoke(publicWaypointManager);
            if (all == null) {
                return Collections.emptyList();
            }

            List<JourneyWaypointChoice> choices = new ArrayList<>();
            for (Object item : flattenWaypointContainer(all)) {
                if (domainFilter != null && !domainFilter.isEmpty()) {
                    Integer dom = waypointCellDomain(item);
                    if (dom == null || !domainFilter.contains(dom)) {
                        continue;
                    }
                }
                JourneyWaypointChoice c = choiceFromWaypointObject(item, publicWaypointManager);
                if (c != null && c.jtKey != null && !c.jtKey.isBlank()) {
                    choices.add(c);
                }
            }
            return sortUniqueChoices(choices);
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException ignored) {
            return Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /**
     * Destinations for the "something cool" menu: public Journey waypoints + POI regions on the
     * player's <em>current</em> world only (locations attached when available).
     */
    private void loadSameWorldGuidanceDestinations(Player player, Consumer<List<JourneyWaypointChoice>> callback) {
        if (callback == null || player == null) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        World world = player.getWorld();
        Integer domain = journeyDomainForWorldSafe(world);
        Set<Integer> domains = domain == null ? Set.of() : Set.of(domain);

        List<JourneyWaypointChoice> choices = new ArrayList<>();
        if (!domains.isEmpty()) {
            choices.addAll(collectJourneyPublicWaypoints(domains));
        }

        String poiSource = cfg.getString("journey.poi-source", "both");
        boolean useWorldGuard = "worldguard".equalsIgnoreCase(poiSource) || "both".equalsIgnoreCase(poiSource);
        boolean useDatabase = "database".equalsIgnoreCase(poiSource) || "both".equalsIgnoreCase(poiSource);
        int poiFromWorldGuard = 0;
        if (cfg.getBoolean("journey.include-poi-regions", true) && useWorldGuard) {
            for (JourneyWaypointChoice poi : poiChoicesFromWorldGuard(world, cfg)) {
                choices.add(poi);
                poiFromWorldGuard++;
            }
        }
        List<JourneyWaypointChoice> mergedChoices = sortUniqueChoices(choices);
        final int journeyCount = (int) mergedChoices.stream().filter(c -> isPoiOrNpcKey(c.jtKey)).count();
        final int poiFromWorldGuardFinal = poiFromWorldGuard;

        Runnable finish = () -> {
            if (cfg.getBoolean("journey.debug-log", false)) {
                plugin.getLogger().info(
                        "[OverworldAgent][Journey] same-world guidance ("
                                + player.getName()
                                + " world="
                                + world.getName()
                                + " domain="
                                + domain
                                + " poiOrNpcChoices="
                                + journeyCount
                                + " poiFromWorldGuard="
                                + poiFromWorldGuardFinal
                                + " totalChoices="
                                + mergedChoices.size()
                                + ")");
            }
            callback.accept(mergedChoices);
        };

        if (cfg.getBoolean("journey.include-poi-regions", true) && useDatabase && plugin.getQueryer() != null) {
            String tablePrefix = cfg.getString("journey.worldguard-table-prefix", "rg_");
            String poiPrefix = cfg.getString("journey.poi-region-prefix", "poi-");
            // DB listing is by world-name prefix; filter to this exact world afterward.
            plugin.getQueryer().listPoiRegions(world.getName(), poiPrefix, tablePrefix, dbPoi -> {
                List<JourneyWaypointChoice> withDb = new ArrayList<>(mergedChoices);
                if (dbPoi != null) {
                    for (JourneyGuidanceCatalog.Destination poi : dbPoi) {
                        if (poi == null || poi.jtKey() == null) {
                            continue;
                        }
                        withDb.add(new JourneyWaypointChoice(poi.jtKey(), poi.label(), null));
                    }
                }
                List<JourneyWaypointChoice> finalChoices = sortUniqueChoices(withDb);
                if (cfg.getBoolean("journey.debug-log", false)) {
                    plugin.getLogger().info(
                            "[OverworldAgent][Journey] same-world guidance+db ("
                                    + player.getName()
                                    + " world="
                                    + world.getName()
                                    + " finalChoiceCount="
                                    + finalChoices.size()
                                    + ")");
                }
                callback.accept(finalChoices);
            });
            return;
        }
        finish.run();
    }

    private static List<JourneyWaypointChoice> poiChoicesFromWorldGuard(World world, FileConfiguration cfg) {
        if (world == null || Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return List.of();
        }
        String poiPrefix = cfg.getString("journey.poi-region-prefix", "poi-");
        if (poiPrefix == null) {
            poiPrefix = "poi-";
        }
        String prefixLower = poiPrefix.toLowerCase(Locale.ROOT);
        List<JourneyWaypointChoice> out = new ArrayList<>();
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return List.of();
            }
            for (Map.Entry<String, ProtectedRegion> entry : manager.getRegions().entrySet()) {
                String regionId = entry.getKey();
                if (regionId == null || !regionId.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                    continue;
                }
                ProtectedRegion region = entry.getValue();
                Location center = regionCenter(world, region);
                String key = regionId.toLowerCase(Locale.ROOT);
                out.add(new JourneyWaypointChoice(
                        key, JourneyGuidanceCatalog.formatPoiLabel(regionId, poiPrefix), center));
            }
        } catch (Throwable ignored) {
            return List.of();
        }
        return out;
    }

    private static Location regionCenter(World world, ProtectedRegion region) {
        if (world == null || region == null) {
            return null;
        }
        try {
            var min = region.getMinimumPoint();
            var max = region.getMaximumPoint();
            double x = (min.getX() + max.getX()) / 2.0;
            double y = (min.getY() + max.getY()) / 2.0;
            double z = (min.getZ() + max.getZ()) / 2.0;
            return new Location(world, x, y, z);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void loadGuidanceDestinations(Player player, Consumer<List<JourneyWaypointChoice>> callback) {
        FileConfiguration cfg = plugin.getConfig();
        List<World> linkedWorlds = JourneyGuidanceCatalog.linkedWorlds(player.getWorld(), cfg);
        Set<Integer> linkedDomains = JourneyGuidanceCatalog.journeyDomains(linkedWorlds);
        String linkedPrefix = JourneyGuidanceCatalog.linkedPrefixFor(player.getWorld(), cfg);

        List<JourneyWaypointChoice> choices = new ArrayList<>(collectJourneyPublicWaypoints(linkedDomains));
        int journeyInLinked = choices.size();

        String poiSource = cfg.getString("journey.poi-source", "both");
        boolean useWorldGuard = "worldguard".equalsIgnoreCase(poiSource) || "both".equalsIgnoreCase(poiSource);
        boolean useDatabase = "database".equalsIgnoreCase(poiSource) || "both".equalsIgnoreCase(poiSource);
        int poiFromWorldGuard = 0;
        if (cfg.getBoolean("journey.include-poi-regions", true) && useWorldGuard) {
            for (JourneyGuidanceCatalog.Destination poi : JourneyGuidanceCatalog.poiRegionsFromWorldGuard(linkedWorlds, cfg)) {
                choices.add(new JourneyWaypointChoice(poi.jtKey(), poi.label()));
                poiFromWorldGuard++;
            }
        }
        List<JourneyWaypointChoice> mergedChoices = sortUniqueChoices(choices);
        final int journeyInLinkedFinal = journeyInLinked;
        final int poiFromWorldGuardFinal = poiFromWorldGuard;

        Runnable finish = () -> {
            List<JourneyWaypointChoice> finalChoices = mergedChoices;
            if (finalChoices.isEmpty()) {
                finalChoices = collectJourneyPublicWaypoints(null);
            }
            if (cfg.getBoolean("journey.debug-log", false)) {
                plugin.getLogger().info(
                        "[OverworldAgent][Journey] guidance sources ("
                                + player.getName()
                                + " world="
                                + player.getWorld().getName()
                                + " linkedPrefix="
                                + linkedPrefix
                                + " linkedWorlds="
                                + linkedWorlds.stream().map(World::getName).toList()
                                + " journeyDomains="
                                + linkedDomains
                                + " journeyWaypointsInLinked="
                                + journeyInLinkedFinal
                                + " poiFromWorldGuard="
                                + poiFromWorldGuardFinal
                                + " finalChoiceCount="
                                + finalChoices.size()
                                + ")");
            }
            callback.accept(finalChoices);
        };

        if (cfg.getBoolean("journey.include-poi-regions", true) && useDatabase && plugin.getQueryer() != null) {
            String tablePrefix = cfg.getString("journey.worldguard-table-prefix", "rg_");
            String poiPrefix = cfg.getString("journey.poi-region-prefix", "poi-");
            final List<JourneyWaypointChoice> baseChoices = mergedChoices;
            plugin.getQueryer().listPoiRegions(linkedPrefix, poiPrefix, tablePrefix, dbPoi -> {
                List<JourneyWaypointChoice> withDb = baseChoices;
                if (dbPoi != null && !dbPoi.isEmpty()) {
                    List<JourneyWaypointChoice> merged = new ArrayList<>(baseChoices);
                    for (JourneyGuidanceCatalog.Destination poi : dbPoi) {
                        merged.add(new JourneyWaypointChoice(poi.jtKey(), poi.label()));
                    }
                    withDb = sortUniqueChoices(merged);
                }
                List<JourneyWaypointChoice> finalChoices = withDb;
                if (finalChoices.isEmpty()) {
                    finalChoices = collectJourneyPublicWaypoints(null);
                }
                if (cfg.getBoolean("journey.debug-log", false)) {
                    plugin.getLogger().info(
                            "[OverworldAgent][Journey] guidance sources ("
                                    + player.getName()
                                    + " world="
                                    + player.getWorld().getName()
                                    + " linkedPrefix="
                                    + linkedPrefix
                                    + " linkedWorlds="
                                    + linkedWorlds.stream().map(World::getName).toList()
                                    + " journeyDomains="
                                    + linkedDomains
                                    + " journeyWaypointsInLinked="
                                    + journeyInLinkedFinal
                                    + " poiFromWorldGuard="
                                    + poiFromWorldGuardFinal
                                    + " finalChoiceCount="
                                    + finalChoices.size()
                                    + ")");
                }
                callback.accept(finalChoices);
            });
            return;
        }
        finish.run();
    }

    private void showGuidanceDestinationMenu(Player player, String guidanceResponse, List<JourneyWaypointChoice> guidanceChoices) {
        final List<JourneyWaypointChoice> guidanceDisplay =
                nearestGuidanceWaypointSample(player, guidanceChoices, GUIDANCE_NEAREST_LIMIT);
        if (guidanceDisplay.isEmpty()) {
            return;
        }
        sendComponent(
                player,
                "&8" + BULLET + guidanceResponse,
                "&aNearest POIs/NPCs in this world — click to journey",
                p -> {
                    this.spigotCallback.clearCallbacks(player);
                    Utils.msgNoPrefix(player, "&lPick a destination:", "");

                    for (JourneyWaypointChoice wp : guidanceDisplay) {
                        sendComponent(
                                player,
                                "&8" + BULLET + " &r" + wp.label,
                                "&aClick here to select \"&r" + wp.label + "&a\"",
                                l -> {
                                    dispatchJourneyCommand(player, wp.jtKey);
                                    this.plugin.getQueryer().storeNewInteraction(
                                            new Interaction(plugin, player, "Guidance"), id -> { });
                                }
                        );
                    }
                    sendBackOption(this::doDialogue);
                }
        );
    }

    public void doDialogue() {
        plugin.relinkOwnedAgent(player);
        plugin.ensureAgentEdits(player);
        plugin.getLogger().fine("[OverworldAgent][Journey] doDialogue started");
        this.spigotCallback.clearCallbacks(player);
        FileConfiguration cfg = plugin.getConfig();
        Utils.msgNoPrefix(player, "&lWhat do you want to discuss?", "");
        String endResponse = cfg.getString("template-gui.text.end-your-own-response-speech",
                "&f&nClick here to stop query");
        String customResponse = cfg.getString("template-gui.text.write-your-own-response",
                "&f&nI want to discuss something");
        String seeDialogue = cfg.getString("template-gui.text.see-all-responses",
                "&f&nI want to see our conversation");
        String signHeader = cfg.getString("template-gui.text.custom-response-sign-header",
                "&f&nYour response");
        String guidanceResponse = cfg.getString("template-gui.text.guidance-response",
                "&f&nCan you show me something cool?");
        String scoreResponse = cfg.getString("template-gui.text.score-response",
                "&f&nShow me my scientist scores!");
        String agentEdit = cfg.getString("template-gui.text.agent-edit",
                "&f&nI want to edit my agent");

        // Discussion first (free text → PMML / LLM via chat)
        if (text) {
            sendComponent(
                    player,
                    "&8" + BULLET + customResponse,
                    "&aClick here, then type your message in chat",
                    p -> openFreeDiscussionChatInput()
            );
        } else {
            sendComponent(
                    player,
                    "&8" + BULLET + endResponse,
                    "&aClick here to see my response!",
                    p -> {
                        player.sendMessage(response);
                        doResponse();
                    });
        }

        // Agent Guidance Option (async — remaining menu items are sent after this completes)
        Runnable sendMenuTail = () -> sendDialogueMenuTail(cfg, scoreResponse, agentEdit);

        if (Bukkit.getPluginManager().getPlugin("Journey") != null) {
            loadSameWorldGuidanceDestinations(player, guidanceChoices -> {
                List<JourneyWaypointChoice> nearest =
                        nearestGuidanceWaypointSample(player, guidanceChoices, GUIDANCE_NEAREST_LIMIT);
                if (!nearest.isEmpty()) {
                    showGuidanceDestinationMenu(player, guidanceResponse, guidanceChoices);
                } else {
                    sendComponent(
                            player,
                            "&8" + BULLET + guidanceResponse,
                            "&aClick here to enter a Journey destination (chat)",
                            p -> openJourneyDestinationTextInput()
                    );
                }
                sendMenuTail.run();
            });
        } else {
            sendMenuTail.run();
        }
    }

    private void sendDialogueMenuTail(FileConfiguration cfg, String scoreResponse, String agentEdit) {
        //Agent Score option
        sendComponent(
                player,
                "&8" + BULLET + scoreResponse,
                "&aClick here to see your scientist scores!",
                p -> {

                    this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Progress"), id -> {
                        plugin.ensureStudentFeedbackSession(player);
                        Bukkit.dispatchCommand(player, "progress");
                    });
                });

        //Agent Build option (templates + base feedback; merged from the old chat_type Builder menu)
        if (plugin.isBuilderEnabled()) {
            String buildResponse = cfg.getString("template-gui.text.build-response",
                    "&f&nI want to build something!");
            sendComponent(
                    player,
                    "&8" + BULLET + buildResponse,
                    "&aClick here for build templates and base feedback!",
                    p -> openBuilderMenu()
            );
        }

        Map<String, Integer> edits = plugin.getAgentEdits().get(player);
        int skinChange = edits.get("Skin");
        int nameChange = edits.get("Name");
        int typeChange = edits.getOrDefault("Type", 0);
        boolean canEditName = AgentPermissions.canEditName(player) && nameChange < AGENT_EDIT_NUM;
        boolean canEditSkin = AgentPermissions.canEditSkin(player) && skinChange < AGENT_EDIT_NUM;
        boolean canEditType = AgentPermissions.canEditTypeMenu(player) && typeChange < AGENT_EDIT_NUM;
        if (embodied && AgentPermissions.canOpenEditMenu(player)
                && (canEditName || canEditSkin || canEditType)) {
            //Agent edit Option
            sendComponent(player, "&8" + BULLET + agentEdit, "&aClick here to change me!", p -> openEditMenu());
        }

        //Close option — always last
        sendComponent(
                player,
                "&8" + BULLET + " &7&nThat's all for now",
                "&aClick here to close this menu",
                p -> {
                    this.spigotCallback.clearCallbacks(player);
                    Utils.msgNoPrefix(player, "&7Talk to you later!");
                });
    }

    /**
     * Opens the builder menu (templates, demo builds, base feedback) for this player,
     * reusing an in-progress builder session when one exists so template state is kept.
     */
    private void openBuilderMenu() {
        if (!plugin.isBuilderEnabled()) {
            return;
        }
        BuilderDialogue bd = plugin.getInProgressTemplates().get(player);
        if (bd == null) {
            bd = new BuilderDialogue(plugin, player, embodied);
        }
        bd.setGoBack(this::doDialogue);
        bd.doDialogue();
    }

    /** Renders a "Go back" entry that clears this menu's callbacks and reopens the parent menu. */
    private void sendBackOption(Runnable onBack) {
        sendComponent(
                player,
                "&8" + BULLET + " &7&nGo back",
                "&aClick here to go back",
                p -> {
                    this.spigotCallback.clearCallbacks(player);
                    onBack.run();
                });
    }

    private void openEditMenu() {
        FileConfiguration cfg = plugin.getConfig();
        String signHeader = cfg.getString("template-gui.text.custom-response-sign-header",
                "&f&nYour response");
        Map<String, Integer> edits = plugin.getAgentEdits().get(player);
        int skinChange = edits.get("Skin");
        int nameChange = edits.get("Name");
        int typeChange = edits.getOrDefault("Type", 0);
        NPC ownedForEdit = plugin.getAgents().get(player.getName());
        boolean canEditSkin = AgentPermissions.canEditSkin(player)
                && skinChange < AGENT_EDIT_NUM
                && ownedForEdit != null && ownedForEdit.isSpawned() && ownedForEdit.getEntity() != null
                && ownedForEdit.getEntity().getType() == EntityType.PLAYER;

        this.spigotCallback.clearCallbacks(player);
        Utils.msgNoPrefix(player, "&lClick what you want to change:", "");

            if(canEditSkin) {
                sendComponent(
                        player,
                        "&8" + BULLET + " &rSkin",
                        "&aClick here to select \"&rskin change",
                        l -> openSkinSelectionMenu(skinChange));
            }
            if (AgentPermissions.canEditName(player) && nameChange < AGENT_EDIT_NUM) {
            sendComponent(
                    player,
                    "&8" + BULLET + " &rName",
                    "&aClick here to select \"&rname change",
                    l -> this.plugin.getSignMenuFactory()
                            .newMenu(Collections.singletonList(Utils.color(signHeader)))
                            .reopenIfFail(true)
                            .response((signPlayer, strings) -> {
                                String agentName = StringUtils.join(Arrays.copyOfRange(strings, 0, strings.length), ' ').trim();

                                if (agentName.isEmpty()) {
                                    return false;
                                } else if (agentName.length() > 25){
                                    agentName = agentName.substring(0,25);
                                }
                                Map<String, NPC> npcs = plugin.getAgents();
                                NPC npc = npcs.get(player.getName());

                                String finalAgentName = agentName;
                                this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Edit"), id -> {
                                    if (npc != null) {
                                        npc.setName(finalAgentName);
                                        plugin.getAgentEdits().get(player).replace("Name",nameChange+1);
                                        int numLeft = AGENT_EDIT_NUM - plugin.getAgentEdits().get(player).get("Name");
                                        player.sendMessage("Your agent's name has been changed to " + finalAgentName + ".\n You have " + numLeft + " name edits left.");
                                        String appearanceForDb;
                                        if (npc.isSpawned() && npc.getEntity() != null
                                                && npc.getEntity().getType() == EntityType.PLAYER) {
                                            SkinTrait st = npc.getTrait(SkinTrait.class);
                                            appearanceForDb = st != null ? st.getSkinName() : "";
                                        } else if (npc.isSpawned() && npc.getEntity() != null) {
                                            appearanceForDb = npc.getEntity().getType().name();
                                        } else {
                                            appearanceForDb = "";
                                        }
                                        plugin.getQueryer().storeNewAgent(player, "edit", finalAgentName, appearanceForDb, id2 -> {
                                            plugin.getAgents().put(player.getName(), npc);
                                        });
                                    } else {
                                        player.sendMessage("You need to have an AI friend first. Please try again");
                                    }
                                    this.spigotCallback.clearCallbacks(player);
                                });
                                return true;
                            })
                            .open(player)
            );}
            if (AgentPermissions.canEditTypeMenu(player) && typeChange < AGENT_EDIT_NUM) {
                sendComponent(
                        player,
                        "&8" + BULLET + " &rEntity Type",
                        "&aClick here to change what I am",
                        l -> {
                            this.spigotCallback.clearCallbacks(player);
                            Utils.msgNoPrefix(player, "&lClick what type you want me to be:", "");
                            boolean offeredType = false;
                            for (EntityType type : AgentEntityTypes.selectableAgentTypes()) {
                                if (!AgentPermissions.canEditEntityType(player, type)) {
                                    continue;
                                }
                                offeredType = true;
                                String label = StringUtils.capitalize(type.name().toLowerCase());
                                sendComponent(
                                        player,
                                        "&8" + BULLET + " &r" + label,
                                        "&aClick here to become \"&r" + label + "&a\"",
                                        m -> this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Edit"), id -> {
                                            if (!AgentPermissions.canEditEntityType(player, type)) {
                                                AgentPermissions.deny(player, type == EntityType.PLAYER
                                                        ? AgentPermissions.EDIT_TYPE
                                                        : AgentPermissions.EDIT_TYPE_ANIMAL);
                                                this.spigotCallback.clearCallbacks(player);
                                                return;
                                            }
                                            NPC npc = plugin.getAgents().get(player.getName());
                                            if (npc == null) {
                                                player.sendMessage("You need to have an AI friend first. Please try again");
                                                this.spigotCallback.clearCallbacks(player);
                                                return;
                                            }

                                            Location respawnAt;
                                            if (npc.isSpawned() && npc.getEntity() != null) {
                                                respawnAt = npc.getEntity().getLocation().clone();
                                            } else if (npc.getStoredLocation() != null) {
                                                respawnAt = npc.getStoredLocation().clone();
                                            } else {
                                                respawnAt = player.getLocation().clone();
                                            }

                                            if (type != EntityType.PLAYER && npc.hasTrait(SkinTrait.class)) {
                                                npc.removeTrait(SkinTrait.class);
                                            }

                                            npc.getOrAddTrait(AgentPermanentFlyingTrait.class);
                                            npc.setBukkitEntityType(type);
                                            if (!npc.isSpawned()) {
                                                npc.spawn(respawnAt);
                                            }
                                            npc.getOrAddTrait(AgentPermanentFlyingTrait.class).applyFlyingForCurrentEntity();
                                            AgentFollowTuning.applyForCurrentEntity(plugin, npc);
                                            AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);

                                            plugin.getAgentEdits().get(player).put("Type", typeChange + 1);
                                            int numLeft = AGENT_EDIT_NUM - plugin.getAgentEdits().get(player).get("Type");
                                            player.sendMessage("Your agent's entity type has been changed to " + label + ".\n You have " + numLeft + " type edits left.");

                                            plugin.getQueryer().storeNewAgent(player, "edit", npc.getName(), type.name(), id2 -> {
                                                plugin.getAgents().put(player.getName(), npc);
                                            });
                                            this.spigotCallback.clearCallbacks(player);
                                        })
                                );
                            }
                            if (!offeredType) {
                                Utils.msgNoPrefix(player, "&cYou do not have permission to change entity type.");
                            }
                            sendBackOption(this::openEditMenu);
                        }
                );
            }
        sendBackOption(this::doDialogue);
    }

    private void openSkinSelectionMenu(int skinChange) {
        this.spigotCallback.clearCallbacks(player);
        Utils.msgNoPrefix(player, "&lClick what skin you want me to have:", "");
        FileConfiguration config = plugin.getConfig();
        String path = "skins." + plugin.getSkinType();
        ConfigurationSection skinSection = config.getConfigurationSection(path);
        if (skinSection != null) {
            for (String key : skinSection.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(path + "." + key);
                if (section == null) {
                    continue;
                }
                String labelOpt = section.getString("dialogue_option");
                final String label = (labelOpt == null || labelOpt.isBlank()) ? key : labelOpt;
                String signature = section.getString("signature");
                String data = section.getString("data");
                final String skinName = key;
                sendComponent(
                        player,
                        "&8" + BULLET + " &r" + label,
                        "&aClick here to select \"&r" + label + "&a\"",
                        m -> this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Edit"), id ->
                                applyConfiguredSkinEdit(skinChange, skinName, signature, data, label)));
            }
        }
        if (plugin.getConfig().getBoolean("agent-spawn.allow-url-skins", true)) {
            sendComponent(
                    player,
                    "&8" + BULLET + " &rCustom URL skin",
                    "&aPaste a direct https:// link to a .png skin image",
                    m -> openCustomSkinUrlInput(skinChange));
        }
        sendBackOption(this::openEditMenu);
    }

    private void applyConfiguredSkinEdit(
            int skinChange,
            String skinName,
            String signature,
            String data,
            String label
    ) {
        NPC npc = plugin.getAgents().get(player.getName());
        if (npc == null) {
            player.sendMessage("You need to have an AI friend first. Please try again");
            this.spigotCallback.clearCallbacks(player);
            return;
        }
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinPersistent(skinName, signature, data);
        finishSkinEdit(skinChange, label, skinName, npc);
    }

    private void openCustomSkinUrlInput(int skinChange) {
        this.spigotCallback.clearCallbacks(player);
        List<String> instruct = Arrays.asList(
                Utils.color("&0&lCustom skin URL"),
                "",
                Utils.color("&7Paste a direct &fhttps:// &7link to a .png skin image in chat."));
        plugin.getChatTextInputFactory().open(player, instruct, text -> {
            if (StringUtils.isBlank(text)) {
                Utils.msgNoPrefix(player, ChatColor.RED + "Please enter a URL in chat.");
                openCustomSkinUrlInput(skinChange);
                return;
            }
            String url = text.trim();
            if (!CitizensSkinUrls.isHttpsUrl(url)) {
                Utils.msgNoPrefix(player, ChatColor.RED + "Use a direct https:// link to a .png file.");
                openCustomSkinUrlInput(skinChange);
                return;
            }
            if (!plugin.getConfig().getBoolean("agent-spawn.allow-url-skins", true)) {
                player.sendMessage("Custom URL skins are disabled on this server.");
                openSkinSelectionMenu(skinChange);
                return;
            }
            player.sendMessage("Fetching skin from URL...");
            CitizensSkinUrls.fetchFromUrl(
                    plugin,
                    url,
                    false,
                    skinData -> plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Edit"), id -> {
                        NPC npc = plugin.getAgents().get(player.getName());
                        if (npc == null) {
                            player.sendMessage("You need to have an AI friend first. Please try again");
                            this.spigotCallback.clearCallbacks(player);
                            return;
                        }
                        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
                        skinTrait.setSkinPersistent(
                                skinData.cacheId(), skinData.signature(), skinData.texture());
                        finishSkinEdit(skinChange, "custom URL skin", skinData.cacheId(), npc);
                    }),
                    error -> {
                        player.sendMessage(
                                "Could not load skin from that URL. Use a direct https:// link to a .png file. "
                                        + "(" + error + ")");
                        openCustomSkinUrlInput(skinChange);
                    }
            );
        });
    }

    private void finishSkinEdit(int skinChange, String label, String appearanceForDb, NPC npc) {
        plugin.getAgentEdits().get(player).replace("Skin", skinChange + 1);
        int numLeft = AGENT_EDIT_NUM - plugin.getAgentEdits().get(player).get("Skin");
        player.sendMessage(
                "Your agent's skin has been changed to " + label + ".\n You have " + numLeft + " skin edits left.");
        plugin.getQueryer().storeNewAgent(player, "edit", npc.getName(), appearanceForDb, id2 ->
                plugin.getAgents().put(player.getName(), npc));
        this.spigotCallback.clearCallbacks(player);
    }

    /**
     * Resolves a PMML label to a configured prompt, then {@link #UNKNOWN_LABEL}, then a built-in fallback.
     * Live servers with truncated {@code prompts} lists (missing label -2) used to NPE here during discussion.
     */
    private DialoguePrompt resolvePrompt(int label) {
        DialoguePrompt configured = prompts.get(label);
        if (configured != null) {
            return configured;
        }
        if (label != UNKNOWN_LABEL) {
            plugin.getLogger().warning(
                    "[OverworldAgent] Missing prompts entry for label " + label + " in config.yml; using unknown.");
        }
        DialoguePrompt unknown = prompts.get(UNKNOWN_LABEL);
        if (unknown != null) {
            return unknown;
        }
        plugin.getLogger().warning(
                "[OverworldAgent] Missing prompts label -2 (unknown) in config.yml; using built-in fallback.");
        return builtInUnknownPrompt();
    }

    private DialoguePrompt builtInUnknownPrompt() {
        if (builtInUnknownPrompt == null) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("prompt", "unknown");
            entry.put("tool", null);
            entry.put(
                    "feedback",
                    "Sorry I am not sure about this. Try talking to me about something else or maybe ask an instructor about this feature."
            );
            builtInUnknownPrompt = new DialoguePrompt(entry);
        }
        return builtInUnknownPrompt;
    }

    private void doResponse() {
        DialoguePrompt prompt = null;
        plugin.getLogger().fine("[OverworldAgent][Journey] doResponse started");
            Chatbot chatbot = new Chatbot(response);
            double[] prediction = chatbot.classifyDialogueIntent();
            int predictedClass = (int) prediction[0];
            double certainty = prediction[1];
            if (certainty > THRESHOLD) {
                DialoguePrompt matched = prompts.get(predictedClass);
                prompt = matched != null ? matched : resolvePrompt(UNKNOWN_LABEL);
                feedback = prompt.getFeedback();
                if (matched != null) {
                    this.fillIn();
                }
                if (prompt.getPrompt().equalsIgnoreCase("quest")) {
                    int ctr = 0;
                    Quests qp = (Quests) Bukkit.getServer().getPluginManager().getPlugin("Quests");
                    for (Quest quest : qp.getQuester(player.getUniqueId()).getCurrentQuests().keySet()) {
                        feedback += quest.getDescription();
                        ctr++;
                    }
                    if (ctr == 0) {
                        feedback = "You are not on any quest currently";
                    }

                } else if (prompt.getPrompt().equalsIgnoreCase("guidance")) {
                    String destination = StringUtils.trimToEmpty(response);
                    feedback = feedback.replace("{LOCATION}", destination);
                    if(destination.equals("")){
                        feedback = "Sorry, I could not find that location";
                    } else {
                        String finalDestination = destination;
                        Bukkit.getScheduler().runTask(plugin, () -> dispatchJourneyCommand(player, finalDestination));
                    }
                } else if (prompt.getPrompt().equalsIgnoreCase("npcs")) {
                    int ctr = 0;
                    Iterable<NPC> serverNPCs = CitizensAPI.getNPCRegistry().sorted();
                    for (NPC currNPC : serverNPCs) {
                        if ((currNPC.getStoredLocation() != null) && (currNPC.isSpawned()) && (currNPC.getStoredLocation().getWorld().equals(player.getWorld())) && (!plugin.getAgents().containsValue(currNPC))) {
                            feedback += currNPC.getName() + "'s location is (" + currNPC.getStoredLocation().getBlockX() + ", " + currNPC.getStoredLocation().getBlockY() + ", " + currNPC.getStoredLocation().getBlockZ() + ")\n";
                            ctr++;
                        }
                    }
                    if (ctr == 0) {
                        feedback = "There are currently no characters on your world";
                    }

                } else if (prompt.getPrompt().equalsIgnoreCase("science_tool")) {
                    DialoguePrompt finalPrompt = prompt;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(player, finalPrompt.getTool());
                    });
                }
            } else {
                prompt = resolvePrompt(UNKNOWN_LABEL);
                feedback = prompt.getFeedback();

            }
        //}
        String finalResponse = response;
        //Janky but waits until event is done before stores in db
        DialoguePrompt finalPrompt1 = prompt;
        final String[] feedbackOut = {feedback};
        final int finalPredictedClass = predictedClass;
        final double finalCertainty = certainty;
        final boolean[] dialogueResearchLogged = {false};

        Runnable storeAndSend = () -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
            discussionBusy = false;
            if (!dialogueResearchLogged[0]) {
                logDialogueDiscussionPmmlTurn(
                        finalResponse,
                        feedbackOut[0],
                        finalPrompt1,
                        finalPredictedClass,
                        finalCertainty
                );
            }
            // Always deliver the reply even if MySQL logging fails.
            if (finalPrompt1 != null && !finalPrompt1.getPrompt().equalsIgnoreCase("science_tool")) {
                if (player.isOnline()) {
                    player.sendMessage(feedbackOut[0]);
                }
            }
            if (plugin.getQueryer() == null) {
                return;
            }
            try {
                plugin.getQueryer().storeNewScienceInquiry(player, finalResponse, feedbackOut[0], id -> {
                    if (plugin.getQueryer() != null) {
                        plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Dialogue"), id2 -> {});
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to log dialogue turn: " + ex.getMessage());
            }
        }, 20L);

        if (plugin.getConfig().getBoolean("llm.use-for-reply", false)
                && plugin.getLlmProvider() != null
                && plugin.getLlmProvider().isConfigured()) {
            plugin.getLogger().fine("[OverworldAgent][Journey] LLM path started");
            discussionBusy = true;
            Utils.msgNoPrefix(player, ChatColor.GRAY + "Thinking...");
            boolean journeyActions = plugin.getConfig().getBoolean("llm.journey-actions.enabled", true)
                    && Bukkit.getPluginManager().getPlugin("Journey") != null;

            java.util.function.Consumer<List<JourneyWaypointChoice>> startLlm = guidanceChoices -> {
                List<JourneyGuidanceCatalog.Destination> destinations =
                        JourneyLlmBridge.fromWaypointChoices(guidanceChoices);

                LearnerActivityContextProvider.load(plugin, player, snapshot -> {
                    try {
                        String systemPrompt = plugin.buildLlmSystemPrompt(player);
                        if (journeyActions && !destinations.isEmpty()) {
                            int max = plugin.getConfig().getInt("llm.journey-actions.max-destinations", 60);
                            systemPrompt = JourneyLlmBridge.appendDestinationContext(systemPrompt, destinations, max);
                        }
                        String activityPrompt = LearnerActivityContextProvider.isEnabled(plugin)
                                ? LearnerActivityContextProvider.formatForPrompt(snapshot, player)
                                : "";
                        if (!activityPrompt.isBlank()) {
                            systemPrompt = systemPrompt + activityPrompt;
                            if (plugin.getConfig().getBoolean("llm.debug-log", false)) {
                                plugin.getLogger().info(
                                        "[OverworldAgent][LLM] activity-context chars=" + activityPrompt.length()
                                                + " for " + player.getName());
                            }
                        }
                        final String llmSystemPrompt = systemPrompt;
                        final String llmUserMessage = buildLlmMessageWithHistory(finalResponse);
                        final long requestStartedAt = System.currentTimeMillis();
                        final String turnId = newDiscussionTurnId();
                        final int turnIndex = nextDiscussionTurnIndex();
                        final String traceId = UUID.randomUUID().toString().substring(0, 8);
                        final String providerName = plugin.getConfig().getString("llm.provider", "unknown");
                        final String modelName = plugin.getConfig().getString("llm.model", "unknown");
                        final boolean ragEnabled = plugin.getConfig().getBoolean("llm.rag.enabled", false);
                        final String systemPromptHash = AgentChatResearchLogger.sha256OrNull(llmSystemPrompt);

                        Chatbot llmChatbot = new Chatbot(llmUserMessage);
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return llmChatbot.generateLlmReply(plugin.getLlmProvider(), llmSystemPrompt);
                            } catch (Exception ex) {
                                plugin.getLogger().warning("LLM reply failed: " + ex.getMessage());
                                return null;
                            }
                        }).whenComplete((llmText, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                            long responseReceivedAt = System.currentTimeMillis();
                            int latencyMs = (int) (responseReceivedAt - requestStartedAt);
                            String status;
                            String errorMessage = null;
                            String assistantForLog = feedbackOut[0];

                            if (error != null) {
                                status = "FALLBACK_PMML";
                                errorMessage = error.getMessage();
                                plugin.getLogger().warning("LLM reply failed: " + errorMessage);
                            } else if (llmText != null && !llmText.isBlank()) {
                                try {
                                    JourneyLlmBridge.ParsedReply parsed = JourneyLlmBridge.parseLlmReply(llmText);
                                    feedbackOut[0] = parsed.displayText().isBlank() ? llmText : parsed.displayText();
                                    assistantForLog = feedbackOut[0];
                                    String journeyTarget = parsed.journeyNameId();
                                    if (journeyTarget == null && journeyActions) {
                                        journeyTarget = JourneyLlmBridge.matchDestination(finalResponse, destinations).orElse(null);
                                    }
                                    if (journeyTarget != null && journeyActions) {
                                        plugin.getLogger().info(
                                                "[OverworldAgent][Journey] LLM-triggered navigation for "
                                                        + player.getName()
                                                        + ": "
                                                        + journeyTarget);
                                        dispatchJourneyCommand(player, journeyTarget);
                                    }
                                    status = "SUCCESS";
                                } catch (Exception parseEx) {
                                    feedbackOut[0] = llmText;
                                    assistantForLog = llmText;
                                    status = "SUCCESS";
                                    plugin.getLogger().warning("LLM journey parse failed: " + parseEx.getMessage());
                                }
                            } else {
                                status = "FALLBACK_PMML";
                                errorMessage = "LLM returned no response; PMML/template reply shown.";
                            }

                            dialogueResearchLogged[0] = true;
                            logDialogueDiscussionLlmTurn(
                                    turnId,
                                    turnIndex,
                                    requestStartedAt,
                                    responseReceivedAt,
                                    latencyMs,
                                    finalResponse,
                                    assistantForLog,
                                    providerName,
                                    modelName,
                                    llmSystemPrompt,
                                    llmUserMessage,
                                    systemPromptHash,
                                    ragEnabled,
                                    status,
                                    errorMessage,
                                    traceId,
                                    finalPrompt1,
                                    finalPredictedClass,
                                    finalCertainty
                            );
                            recordDiscussionTurn(finalResponse, feedbackOut[0]);
                            storeAndSend.run();
                        }));
                    } catch (Exception prepEx) {
                        plugin.getLogger().warning(
                                "LLM prompt preparation failed; using PMML reply: " + prepEx.getMessage());
                        recordDiscussionTurn(finalResponse, feedbackOut[0]);
                        storeAndSend.run();
                    }
                });
            };

            if (journeyActions) {
                loadGuidanceDestinations(player, startLlm);
            } else {
                startLlm.accept(List.of());
            }
        } else {
            recordDiscussionTurn(finalResponse, feedbackOut[0]);
            storeAndSend.run();
        }

    }

    private String buildLlmMessageWithHistory(String currentMessage) {
        if (discussionHistory.isEmpty()) {
            return currentMessage;
        }
        StringBuilder builder = new StringBuilder("Recent conversation history:\n");
        for (String line : discussionHistory) {
            builder.append(line).append('\n');
        }
        builder.append("\nCurrent player message:\n").append(currentMessage);
        return builder.toString();
    }

    private void recordDiscussionTurn(String userMessage, String agentReply) {
        discussionHistory.add("User: " + userMessage);
        discussionHistory.add("Assistant: " + agentReply);
        while (discussionHistory.size() > MAX_DISCUSSION_HISTORY) {
            discussionHistory.remove(0);
        }
    }

    private void ensureDiscussionConversation() {
        if (discussionConversationId == null) {
            discussionConversationId = UUID.randomUUID().toString();
            discussionSessionId = player.getUniqueId().toString() + "-" + System.currentTimeMillis();
            discussionTurnIndex = 0;
        }
    }

    private String newDiscussionTurnId() {
        ensureDiscussionConversation();
        return UUID.randomUUID().toString();
    }

    private int nextDiscussionTurnIndex() {
        ensureDiscussionConversation();
        discussionTurnIndex++;
        return discussionTurnIndex;
    }

    private String dialogueAgentType() {
        return embodied ? "EMBODIED_GUIDE" : "GUIDE";
    }

    private String dialogueAgentName() {
        NPC npc = plugin.getAgents().get(player.getName());
        if (npc != null && npc.getName() != null && !npc.getName().isBlank()) {
            return npc.getName();
        }
        return embodied ? "embodied-dialogue-agent" : "dialogue-agent";
    }

    private String dialogueIntentLabel(DialoguePrompt prompt) {
        if (prompt == null || prompt.getPrompt() == null) {
            return "unknown";
        }
        return prompt.getPrompt();
    }

    private void logDialogueDiscussionPmmlTurn(
            String userMessage,
            String assistantResponse,
            DialoguePrompt prompt,
            int predictedClass,
            double certainty
    ) {
        long time = System.currentTimeMillis();
        String turnId = newDiscussionTurnId();
        int turnIndex = nextDiscussionTurnIndex();
        String intentLabel = dialogueIntentLabel(prompt);

        List<AgentChatEvent> events = List.of(
                AgentChatResearchLogger.pmmlIntentEvent(turnId, time, predictedClass, certainty, intentLabel)
        );

        AgentChatResearchLogger.storeTurn(
                plugin,
                new AgentChatResearchTurn(
                        discussionConversationId,
                        turnId,
                        turnIndex,
                        time,
                        player.getUniqueId().toString(),
                        player.getName(),
                        player.getUniqueId().toString(),
                        discussionSessionId,
                        player.getWorld().getName(),
                        dialogueAgentType(),
                        dialogueAgentName(),
                        DIALOGUE_DISCUSSION_COMMAND,
                        userMessage,
                        assistantResponse,
                        "pmml",
                        "dialogue-intent",
                        null,
                        false,
                        time,
                        time,
                        0,
                        "SUCCESS",
                        null,
                        List.of(),
                        events
                )
        );
    }

    private void logDialogueDiscussionLlmTurn(
            String turnId,
            int turnIndex,
            long requestStartedAt,
            long responseReceivedAt,
            int latencyMs,
            String userMessage,
            String assistantResponse,
            String providerName,
            String modelName,
            String systemPrompt,
            String llmUserMessage,
            String systemPromptHash,
            boolean ragEnabled,
            String status,
            String errorMessage,
            String traceId,
            DialoguePrompt prompt,
            int predictedClass,
            double certainty
    ) {
        String intentLabel = dialogueIntentLabel(prompt);
        List<AgentChatEvent> events = List.of(
                AgentChatResearchLogger.pmmlIntentEvent(
                        turnId, requestStartedAt, predictedClass, certainty, intentLabel),
                AgentChatResearchLogger.llmRequestPayloadEvent(
                        turnId,
                        requestStartedAt,
                        discussionConversationId,
                        traceId,
                        DIALOGUE_DISCUSSION_COMMAND,
                        providerName,
                        modelName,
                        systemPrompt,
                        llmUserMessage,
                        ragEnabled,
                        List.of()),
                AgentChatResearchLogger.llmResponsePayloadEvent(
                        turnId,
                        responseReceivedAt,
                        traceId,
                        assistantResponse,
                        status,
                        latencyMs,
                        errorMessage)
        );

        AgentChatResearchLogger.storeTurn(
                plugin,
                new AgentChatResearchTurn(
                        discussionConversationId,
                        turnId,
                        turnIndex,
                        requestStartedAt,
                        player.getUniqueId().toString(),
                        player.getName(),
                        player.getUniqueId().toString(),
                        discussionSessionId,
                        player.getWorld().getName(),
                        dialogueAgentType(),
                        dialogueAgentName(),
                        DIALOGUE_DISCUSSION_COMMAND,
                        userMessage,
                        assistantResponse,
                        providerName,
                        modelName,
                        systemPromptHash,
                        ragEnabled,
                        requestStartedAt,
                        responseReceivedAt,
                        latencyMs,
                        status,
                        errorMessage,
                        List.of(),
                        events
                )
        );
    }

    private void sendComponent(Player player, String text, String hoverText, Consumer<Player> onClick) {
        player.spigot().sendMessage(createComponent(text, hoverText, onClick));
    }

    private TextComponent createComponent(String text, String hoverText, Consumer<Player> onClick) {
        TextComponent message = new TextComponent(Utils.color(text));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Utils.color(hoverText)).create()));
        addCallback(message, this.player.getUniqueId(), onClick);
        return message;
    }

    private void addCallback(TextComponent component, UUID playerUUID, Consumer<Player> onClick) {
        this.spigotCallback.createCommand(playerUUID, component, onClick);
    }

    private void fillIn() {
        feedback = feedback.replace("{NAME}", player.getName());
        feedback = feedback.replace("{PLANET}", player.getWorld().getName());
        if (plugin.getAgents().get(player.getName()) != null) {
            feedback = feedback.replace("{AGENT}", plugin.getAgents().get(player.getName()).getName());
        }
    }

    /*
    @EventHandler
    public void onToolUse(ScienceToolMeasureEvent measure) {
        Player eventPlayer = measure.getMeasurement().getPlayer();
        if (this.player.equals(eventPlayer)  && plugin.getAgents().get(player.getName()) != null) {
            ScienceTool tool = measure.getMeasurement().getTool();
            feedback = feedback.replace("{TOOL}", tool.getDisplayName());
            feedback = feedback.replace("{MEASUREMENT}", measure.getMeasurement().getMeasurement());
        }
    }


    @EventHandler
    public void onVoice(VoiceEvent e) {
        Player p = e.getPlayer();
        if(p.equals(player) && !text) {
            player.sendMessage(e.getSentence());
            response = e.getSentence();
        }
    }

    @EventHandler
    public void walkPath(BukkitFoundSolutionEvent path) {
        FoundSolutionEvent event = path.getSearchEvent();
        Player eventPlayer = Bukkit.getPlayer(event.getSession().getCallerId());
        if (this.player.equals(eventPlayer) && plugin.getAgents().get(player.getName()) != null) {
            NPC agent = plugin.getAgents().get(player.getName());
            if (agent.isSpawned()) {
                //player.sendMessage("Make sure to look around while we walk! If you want to check out other stuff, the path will still be here until later.");
                if(agent.getOrAddTrait(FollowTrait.class).isActive()) {
                    agent.getOrAddTrait(FollowTrait.class).toggle(player, false);
                }

                Itinerary itinerary = event.getItinerary();
                ArrayList<Step> steps = itinerary.getSteps();
                final int[] step = {Math.min(0, steps.size() - 1)};
                final int[] goal = {Math.min(step[0]+5, steps.size() - 1)};
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (goal[0] >= steps.size()-1) {
                            //player.sendMessage("Thanks for following me, try making an observation here about our surroundings!");
                            if(!agent.getOrAddTrait(FollowTrait.class).isActive()) {
                                agent.getOrAddTrait(FollowTrait.class).toggle(player, false);
                            }
                            cancel();
                        }
                        Cell cell = steps.get(goal[0]).location();
                        Location target = new Location(player.getWorld(), cell.blockX(), cell.blockY(), cell.blockZ());
                        agent.getNavigator().setTarget(target);
                        if(agent.getStoredLocation().distanceSquared(target) <= 9){
                            step[0] = goal[0];
                            goal[0] = Math.min(step[0] + 5, steps.size() - 1);
                        }

                        if (agent.getStoredLocation().distance(player.getLocation()) > 10) {
                            //player.sendMessage("Let's explore other areas of the map. This path will stay here and we can return to it later.");
                            if(!agent.getOrAddTrait(FollowTrait.class).isActive()) {
                                agent.getOrAddTrait(FollowTrait.class).toggle(player, false);
                            }
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin,0,0);
            }
        }
    }
    */

}

