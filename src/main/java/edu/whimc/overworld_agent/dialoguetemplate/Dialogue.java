package edu.whimc.overworld_agent.dialoguetemplate;




import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.traits.AgentPermanentFlyingTrait;
import edu.whimc.overworld_agent.dialoguetemplate.models.Chatbot;
import edu.whimc.overworld_agent.dialoguetemplate.models.DialoguePrompt;

import edu.whimc.overworld_agent.utils.AgentEntityTypes;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;


public class Dialogue implements Listener {
    private SpigotCallback spigotCallback;
    /* Unicode for bullet character */
    private static final String BULLET = "\u2022";
    private OverworldAgent plugin;
    private Player player;
    private final int PROFANITY_LABEL = -1;
    private final int UNKNOWN_LABEL = -2;
    private final double THRESHOLD = .5;
    private final int AGENT_EDIT_NUM = 5;
    private String feedback;
    private String response;
    private boolean text;
    private boolean embodied;
    private Map<Integer, DialoguePrompt> prompts;
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
        // Public waypoint lookups use name_id (lowercase in SQL); keep keys stable for getWaypoint(name).
        String nameId = destination.toLowerCase(Locale.ROOT);
        String journeyRoot = plugin.getConfig().getString("journey.journey-command-root", "journey");
        String cmd = journeyRoot + " server waypoint " + quoteIfNeeded(nameId);
        plugin.getLogger().info(
                "[OverworldAgent][Journey] dispatch as player "
                        + player.getName()
                        + ": /"
                        + cmd
                        + " (nameId="
                        + nameId
                        + ", raw="
                        + rawDestination
                        + ", journey-command-root=config:"
                        + journeyRoot
                        + ")");
        try {
            if (!Bukkit.dispatchCommand(player, cmd)) {
                plugin.getLogger().warning(
                        "[OverworldAgent][Journey] Bukkit.dispatchCommand returned **false** for "
                                + player.getName()
                                + ". Command line: /"
                                + cmd
                                + ". Common causes: (1) player lacks permission for that Journey command or for `"
                                + journeyRoot
                                + "`, (2) wrong `journey.journey-command-root` (must match the label players use, e.g. journey vs jo), "
                                + "(3) the command is not registered / Journey disabled. "
                                + "If dispatch is true but navigation still fails, Journey may have run but rejected the waypoint id—check in-game Journey messages.");
                Utils.msgNoPrefix(player, ChatColor.RED + "Journey did not run that command. Check permissions and the destination name.");
            } else {
                plugin.getLogger().info(
                        "[OverworldAgent][Journey] dispatchCommand returned true for "
                                + player.getName()
                                + " (Journey should handle the rest; if nothing happens, verify waypoint `"
                                + nameId
                                + "` exists for server public scope).");
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Journey navigation failed for " + player.getName() + ": " + cmd, ex);
            if (throwableChainMessageContains(ex, "Duplicate key")) {
                Utils.msgNoPrefix(player,
                        ChatColor.RED + "Journey failed: duplicate destination keys in Journey scopes or data (see server log). "
                                + "An administrator should dedupe `journey_waypoints` / NPC scopes.");
            } else {
                Utils.msgNoPrefix(player, ChatColor.RED + "Journey failed to start navigation. See the server log for details.");
            }
        }
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

    private static final class JourneyWaypointChoice {
        final String jtKey;
        final String label;

        JourneyWaypointChoice(String jtKey, String label) {
            this.jtKey = jtKey;
            this.label = (label != null && !label.isBlank()) ? label : jtKey;
        }
    }

    private static List<JourneyWaypointChoice> sortUniqueChoices(Collection<JourneyWaypointChoice> choices) {
        Map<String, JourneyWaypointChoice> byKey = new LinkedHashMap<>();
        for (JourneyWaypointChoice c : choices) {
            if (c != null && c.jtKey != null && !c.jtKey.isBlank()) {
                byKey.putIfAbsent(c.jtKey.toLowerCase(Locale.ROOT), c);
            }
        }
        List<JourneyWaypointChoice> out = new ArrayList<>(byKey.values());
        out.sort(Comparator.comparing(c -> c.label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Picks a random subset of server (public) waypoints for the guidance menu: 3–5 when enough exist,
     * otherwise all available (1–2).
     */
    private static List<JourneyWaypointChoice> randomGuidanceWaypointSample(List<JourneyWaypointChoice> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<JourneyWaypointChoice> copy = new ArrayList<>(source);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        int maxTake = Math.min(5, copy.size());
        int minTake = Math.min(3, copy.size());
        int count = (minTake == maxTake)
                ? minTake
                : (minTake + ThreadLocalRandom.current().nextInt(maxTake - minTake + 1));
        return new ArrayList<>(copy.subList(0, count));
    }

    private static List<Object> flattenWaypointContainer(Object all) {
        List<Object> out = new ArrayList<>();
        if (all instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
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

    private static JourneyWaypointChoice choiceFromWaypointObject(Object waypoint) {
        String key = extractWaypointJtKey(waypoint);
        if (key == null || key.isBlank()) {
            key = extractWaypointName(waypoint);
        }
        if (key != null && !key.isBlank()) {
            key = key.toLowerCase(Locale.ROOT);
        }
        String label = extractWaypointName(waypoint);
        if (label == null || label.isBlank()) {
            label = key;
        }
        return new JourneyWaypointChoice(key, label);
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
                Utils.color("&7Type what you want to say to your agent in chat."),
                Utils.color("&7(When the LLM is enabled, this text will be sent there.)"));
        plugin.getChatTextInputFactory().open(player, instruct, text -> {
            if (StringUtils.isBlank(text)) {
                Utils.msgNoPrefix(player, ChatColor.RED + "Enter a message in chat, or type cancel.");
                openFreeDiscussionChatInput();
                return;
            }
            response = StringUtils.trimToEmpty(text);
            doResponse();
        });
    }

    private void openPlanetTagTextInput() {
        List<String> instruct = Arrays.asList(
                Utils.color("&0&lObservation"),
                "",
                Utils.color("&7What do you want to show or ask about on this planet?"),
                Utils.color("&7Send it as your next chat message."));
        openPlanetTagTextWithRetry(instruct);
    }

    private void openPlanetTagTextWithRetry(List<String> instruct) {
        plugin.getChatTextInputFactory().open(player, instruct, text -> {
            String normalized = StringUtils.trimToEmpty(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.isEmpty()) {
                Utils.msgNoPrefix(player, ChatColor.RED + "Please enter something in chat.");
                openPlanetTagTextWithRetry(instruct);
                return;
            }
            plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Tag"), id -> {
                Tag tag = new Tag(plugin, player, normalized);
                tag.sendFeedback();
            });
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
     * Public Journey destinations for optional {@code domainFilter} (Journey world id).
     * {@code jtKey} prefers {@code name_id}-style accessors when present; otherwise the waypoint display name.
     */
    private List<JourneyWaypointChoice> collectJourneyPublicWaypoints(Integer domainFilter) {
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
                if (domainFilter != null) {
                    Integer dom = waypointCellDomain(item);
                    if (dom == null || !dom.equals(domainFilter)) {
                        continue;
                    }
                }
                JourneyWaypointChoice c = choiceFromWaypointObject(item);
                if (c.jtKey != null && !c.jtKey.isBlank()) {
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

    public void doDialogue() {
        plugin.relinkOwnedAgent(player);
        plugin.ensureAgentEdits(player);
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
        String showResponse = cfg.getString("template-gui.text.show-response",
                "&f&nI want to show you something unique to this environment!");
        String tagScoreResponse = cfg.getString("template-gui.text.tag-score-response",
                "&f&nI want to see my tag scores");
        String scoreResponse = cfg.getString("template-gui.text.score-response",
                "&f&nI want to see my scores");
        String agentEdit = cfg.getString("template-gui.text.agent-edit",
                "&f&nI want to edit my agent");
        // Agent Guidance Option
        // NOTE: Journey 1.3.x may throw when opening its GUI via plain `/jt` on some servers.
        // We avoid that by enumerating public waypoints (if available) and dispatching `jt <waypoint>`
        // which does not require opening the GUI.
        if (Bukkit.getPluginManager().getPlugin("Journey") != null) {
            Integer worldDomain = journeyDomainForWorldSafe(player.getWorld());
            // Prefer waypoints in the player's current Journey domain (same Bukkit world).
            List<JourneyWaypointChoice> guidanceChoices = (worldDomain != null)
                    ? collectJourneyPublicWaypoints(worldDomain)
                    : Collections.emptyList();
            int domainFilteredCount = guidanceChoices.size();
            // If domain resolution fails or nothing matches (API/layout changes), fall back to all public
            // waypoints so players still get a random short list instead of only manual chat entry.
            final boolean sameWorldOnly = !guidanceChoices.isEmpty();
            if (guidanceChoices.isEmpty()) {
                guidanceChoices = collectJourneyPublicWaypoints(null);
            }
            if (plugin.getConfig().getBoolean("journey.debug-log", false)) {
                plugin.getLogger().info(
                        "[OverworldAgent][Journey] guidance menu ("
                                + player.getName()
                                + " world="
                                + player.getWorld().getName()
                                + "): journeyDomainId="
                                + worldDomain
                                + " publicWaypointsInDomain="
                                + domainFilteredCount
                                + " usedAllDomainsFallback="
                                + (!sameWorldOnly && !guidanceChoices.isEmpty())
                                + " finalPublicWaypointCount="
                                + guidanceChoices.size());
            }
            if (!guidanceChoices.isEmpty()) {
                final List<JourneyWaypointChoice> guidanceDisplay = randomGuidanceWaypointSample(guidanceChoices);
                String hoverPick = sameWorldOnly
                        ? "&aA few random places in this world — click to journey"
                        : "&aA few random public waypoints — click to journey (not limited to this world)";
                sendComponent(
                        player,
                        "&8" + BULLET + guidanceResponse,
                        hoverPick,
                        p -> {
                            Utils.msgNoPrefix(player, "&lPick a destination:", "");

                            for (JourneyWaypointChoice wp : guidanceDisplay) {
                                sendComponent(
                                        player,
                                        "&8" + BULLET + " &r" + wp.label,
                                        "&aClick here to select \"&r" + wp.label + "&a\"",
                                        l -> this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Guidance"), id -> {
                                            dispatchJourneyCommand(player, wp.jtKey);
                                        })
                                );
                            }
                        }
                );
            } else {
                // Fallback: avoid bare `/jt` GUI; use chat for longer input than a sign (Paper 1.21.11+ book API requires written_book only).
                sendComponent(
                        player,
                        "&8" + BULLET + guidanceResponse,
                        "&aClick here to enter a Journey destination (chat)",
                        p -> openJourneyDestinationTextInput()
                );
            }
        }
        //Agent Tag option
        Map<Player, Map<World, Integer>> playerTags = Tag.getPlayerTags();
        int numTags = 0;
        if(playerTags.get(player) != null && playerTags.get(player).get(player.getWorld()) != null){
            numTags = playerTags.get(player).get(player.getWorld());
        }
        if (Tag.maxTags(player.getWorld()) != null && numTags < Tag.maxTags(player.getWorld()) && Tag.getDialogueTags().get(player.getWorld()) != null) {
            sendComponent(
                    player,
                    "&8" + BULLET + showResponse,
                    "&aClick here to show or ask about something unique (chat)",
                    p -> openPlanetTagTextInput()
            );
        }

        //Agent Score option
        sendComponent(
                player,
                "&8" + BULLET + scoreResponse,
                "&aClick here to see your scores!",
                p -> {

                    this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Progress"), id -> {
                        plugin.ensureStudentFeedbackSession(player);
                        Bukkit.dispatchCommand(player, "progress");
                    });
                });

        //Agent Dialogue option (free text → PMML / future LLM via chat, not sign)
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
/*
        //Agent Reflection Option
        sendComponent(
                player,
                "&8" + BULLET + seeDialogue,
                "&aClick here to see our conversation so far!",
                p -> {
                    plugin.getQueryer().getSessionConversation(player, plugin.getPlayerSessions().get(player), conversation -> {
                        HashMap<String, List<String>> dialogue = (HashMap<String, List<String>>) conversation;
                        for (Map.Entry<String, List<String>> entry : dialogue.entrySet()) {
                            String world = entry.getKey();
                            List<String> discussion = entry.getValue();
                            for (int k = 0; k < discussion.size(); k++) {
                                if (k % 2 == 0) {
                                    player.sendMessage(world + ": " + player.getName() + ": " + discussion.get(k));
                                } else {
                                    player.sendMessage(world + ": " + plugin.getAgents().get(player.getName()).getName() + ": " + discussion.get(k));
                                }
                            }
                        }
                    });
                    this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Reflection"), id -> {

                    });
                });
*/
        Map<String, Integer> edits = plugin.getAgentEdits().get(player);
        int skinChange = edits.get("Skin");
        int nameChange = edits.get("Name");
        int typeChange = edits.getOrDefault("Type", 0);
        NPC ownedForEdit = plugin.getAgents().get(player.getName());
        boolean canEditSkin = ownedForEdit != null && ownedForEdit.isSpawned() && ownedForEdit.getEntity() != null
                && ownedForEdit.getEntity().getType() == EntityType.PLAYER;
        if((skinChange < AGENT_EDIT_NUM || nameChange < AGENT_EDIT_NUM || typeChange < AGENT_EDIT_NUM) && embodied){
        //Agent edit Option
        sendComponent(player, "&8" + BULLET + agentEdit, "&aClick here to change me!", p -> {
            this.spigotCallback.clearCallbacks(player);
            Utils.msgNoPrefix(player, "&lClick what you want to change:", "");

            if(skinChange < AGENT_EDIT_NUM && canEditSkin) {
                sendComponent(
                        player,
                        "&8" + BULLET + " &rSkin",
                        "&aClick here to select \"&rskin change",
                        l -> {
                            Utils.msgNoPrefix(player, "&lClick what skin you want me to have:", "");
                            FileConfiguration config = plugin.getConfig();
                            String path = "skins."+plugin.getSkinType();
                            for (String key : config.getConfigurationSection(path).getKeys(false)) {
                                ConfigurationSection section = config.getConfigurationSection(path + "." + key);
                                String labelOpt = section.getString("dialogue_option");
                                final String label = (labelOpt == null || labelOpt.isBlank()) ? key : labelOpt;
                                String signature = section.getString("signature");
                                String data = section.getString("data");
                                final String skinName = key;
                                sendComponent(
                                        player,
                                        "&8" + BULLET + " &r" + label,
                                        "&aClick here to select \"&r" + label + "&a\"",
                                        m -> {
                                            this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Edit"), id -> {
                                                Map<String, NPC> npcs = plugin.getAgents();
                                                NPC npc = npcs.get(player.getName());
                                                if (npc != null) {
                                                    SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
                                                    skinTrait.setSkinPersistent(skinName, signature, data);
                                                    plugin.getAgentEdits().get(player).replace("Skin",skinChange+1);
                                                    int numLeft = AGENT_EDIT_NUM - plugin.getAgentEdits().get(player).get("Skin");
                                                    player.sendMessage("Your agent's skin has been changed to " + label + ".\n You have " + numLeft + " skin edits left.");
                                                    plugin.getQueryer().storeNewAgent(player, "edit", npc.getName(), skinName, id2 -> {
                                                        plugin.getAgents().put(player.getName(), npc);
                                                    });
                                                } else {
                                                    player.sendMessage("You need to have an AI friend first. Please try again");
                                                }
                                                this.spigotCallback.clearCallbacks(player);
                                            });
                                        });
                            }
                        });
            }
            if(nameChange < AGENT_EDIT_NUM){
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
                            .open(p)
            );}
            if (typeChange < AGENT_EDIT_NUM) {
                sendComponent(
                        player,
                        "&8" + BULLET + " &rEntity Type",
                        "&aClick here to change what I am",
                        l -> {
                            Utils.msgNoPrefix(player, "&lClick what type you want me to be:", "");
                            for (EntityType type : AgentEntityTypes.selectableAgentTypes()) {
                                String label = StringUtils.capitalize(type.name().toLowerCase());
                                sendComponent(
                                        player,
                                        "&8" + BULLET + " &r" + label,
                                        "&aClick here to become \"&r" + label + "&a\"",
                                        m -> this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Edit"), id -> {
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
                                            npc.getOrAddTrait(FollowTrait.class).follow(player);

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
                        }
                );
            }
        });}
    }


    private void doResponse() {
        DialoguePrompt prompt = null;

            Chatbot chatbot = new Chatbot(response);
            double[] prediction = chatbot.classifyDialogueIntent();
            int predictedClass = (int) prediction[0];
            double certainty = prediction[1];
            if (certainty > THRESHOLD) {
                prompt = prompts.get(predictedClass);
                if (prompt == null) {
                    prompt = prompts.get(UNKNOWN_LABEL);
                    feedback = prompt.getFeedback();
                } else {
                    feedback = prompt.getFeedback();
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
                prompt = prompts.get(UNKNOWN_LABEL);
                feedback = prompt.getFeedback();

            }
        //}
        String finalResponse = response;
        //Janky but waits until event is done before stores in db
        DialoguePrompt finalPrompt1 = prompt;
        final String[] feedbackOut = {feedback};

        Runnable storeAndSend = () -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
            this.plugin.getQueryer().storeNewScienceInquiry(player, finalResponse, feedbackOut[0], id -> {
                this.plugin.getQueryer().storeNewInteraction(new Interaction(plugin, player, "Dialogue"), id2 -> {
                    if (finalPrompt1 != null && !finalPrompt1.getPrompt().equalsIgnoreCase("science_tool")) {
                        player.sendMessage(feedbackOut[0]);
                    }
                });
            });
        }, 20L);

        if (plugin.getConfig().getBoolean("llm.use-for-reply", false)
                && plugin.getLlmProvider() != null
                && plugin.getLlmProvider().isConfigured()) {
            String systemPrompt = plugin.augmentLlmSystemPrompt(plugin.getConfig().getString("llm.system-prompt",
                    "You are a friendly in-game science education assistant. "
                            + "Answer clearly and briefly; keep content appropriate for students."));
            CompletableFuture.supplyAsync(() -> {
                try {
                    return chatbot.generateLlmReply(plugin.getLlmProvider(), systemPrompt);
                } catch (Exception ex) {
                    plugin.getLogger().warning("LLM reply failed: " + ex.getMessage());
                    return null;
                }
            }).thenAccept(llmText -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (llmText != null && !llmText.isBlank()) {
                    feedbackOut[0] = llmText;
                }
                storeAndSend.run();
            }));
        } else {
            storeAndSend.run();
        }

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

