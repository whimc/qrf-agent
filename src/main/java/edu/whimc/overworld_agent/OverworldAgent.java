package edu.whimc.overworld_agent;

import com.jyckos.speechreceiver.SpeechReceiver;
import edu.whimc.observations.models.Observation;
import edu.whimc.overworld_agent.commands.*;
import edu.whimc.overworld_agent.commands.subcommands.ExpertSpawnCommand;
import edu.whimc.overworld_agent.dialoguetemplate.BuilderDialogue;
import edu.whimc.overworld_agent.dialoguetemplate.ChatTextInputFactory;
import edu.whimc.overworld_agent.dialoguetemplate.SpigotCallback;
import edu.whimc.overworld_agent.dialoguetemplate.SignMenuFactory;
import edu.whimc.overworld_agent.dialoguetemplate.models.LlmProvider;
import edu.whimc.overworld_agent.dialoguetemplate.models.NoOpLlmProvider;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.LlmConfigAnnouncer;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.LlmProviderFactory;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.LlmRagContextBuilder;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.WorldLlmPromptRegistry;
import edu.whimc.overworld_agent.dialoguetemplate.models.BuildTemplate;
import edu.whimc.overworld_agent.utils.AgentEntityTypes;
import edu.whimc.overworld_agent.utils.sql.Queryer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import edu.whimc.overworld_agent.traits.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

import java.lang.reflect.Method;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.logging.Level;


import org.bukkit.event.Listener;

//This is your bukkit plugin class. Use it to hook your trait into Citizens and handle any commands.

/**
 * Class to create plugin and enable it in MC
 * @author sam
 */
public class OverworldAgent extends JavaPlugin {
    private Map<String, NPC> agents;
    private Queryer queryer;
    private List<String> profanity;
    private SignMenuFactory signMenuFactory;
    private ChatTextInputFactory chatTextInputFactory;
    /** Single instance; {@link edu.whimc.overworld_agent.dialoguetemplate.Dialogue} registers clicks here (see /oacallback). */
    private SpigotCallback spigotCallback;
    private LlmProvider llmProvider = new NoOpLlmProvider();
    private final WorldLlmPromptRegistry worldLlmPromptRegistry = new WorldLlmPromptRegistry();
    private ExpertSpawnCommand expertSpawnCommand;
    private HashMap<Player,Long> sessions;
    //private SpeechReceiver receiver;
    private HashMap<Player,HashMap<String,Integer>> agentEdits;
    private HashMap<Player, List<BuildTemplate>> buildTemplates;
    private HashMap<Player, BuilderDialogue> inProgressTemplates;
    private String skinType;
    public static final String PERM_PREFIX = "whimc-agent";


    /**
     * Method to enable plugin
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        AgentEntityTypes.load(this);
        //receiver = (SpeechReceiver) Bukkit.getServer().getPluginManager().getPlugin("SpeechReceiver");
        sessions = new HashMap<>();
        buildTemplates = new HashMap<>();
        inProgressTemplates = new HashMap<>();

        this.queryer = new Queryer(this, q -> {
            // If we couldn't connect to the database disable the plugin
            if (q == null) {
                this.queryer = null;
                this.getLogger().severe("Could not establish MySQL connection! Disabling plugin...");
                getCommand("agent").setExecutor(this);
                return;
            }

        });

        //check if Citizens is present and enabled.
        agents = new HashMap<>();
        agentEdits = new HashMap<>();
        if(getServer().getPluginManager().getPlugin("Citizens") == null || getServer().getPluginManager().getPlugin("Citizens").isEnabled() == false) {
            getLogger().log(Level.SEVERE, "Citizens 2.0 not found or not enabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Permission parent = new Permission(PERM_PREFIX + ".*");
        Bukkit.getPluginManager().addPermission(parent);

        skinType = this.getConfig().getString("agent_type");

        //Register your traits with Citizens.
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(SpawnNoviceTrait.class).withName("noviceagentspawn"));
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(SpawnExpertTrait.class).withName("expertagentspawn"));
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(AgentPermanentFlyingTrait.class).withName("agentpermanentflying"));
        net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(AgentFollowCatchUpTrait.class).withName("agentfollowcatchup"));

        expertSpawnCommand = new ExpertSpawnCommand(this, "agent", "spawn");

        AgentCommand agentCommand = new AgentCommand(this);
        getCommand("agent").setExecutor(agentCommand);
        getCommand("agent").setTabCompleter(agentCommand);

        edu.whimc.overworld_agent.utils.AgentPermissions.register();

        HabitatAssessCommand assessCommand = new HabitatAssessCommand(this);
        getCommand("assess-habitat").setExecutor(assessCommand);
        getCommand("assess-habitat").setTabCompleter(assessCommand);

        // Internal command used by clickable chat components (see SpigotCallback)
        if (getCommand("oacallback") != null) {
            getCommand("oacallback").setExecutor((sender, command, label, args) -> true);
        }

        spigotCallback = new SpigotCallback(this);
        signMenuFactory = new SignMenuFactory(this);
        chatTextInputFactory = new ChatTextInputFactory(this);
        try {
            Files.createDirectories(LlmRagContextBuilder.resolveContextRoot(this));
            saveResource(WorldLlmPromptRegistry.PROMPTS_FOLDER + "/" + WorldLlmPromptRegistry.DEFAULT_FILE, false);
            saveResource(WorldLlmPromptRegistry.PROMPTS_FOLDER + "/colder.yml", false);
            worldLlmPromptRegistry.reload(this);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not initialize world LLM prompts: " + e.getMessage());
        }
        setupLlmFromConfig();
        LlmConfigAnnouncer.announce(this);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
    }

    private void setupLlmFromConfig() {
        setLlmProvider(LlmProviderFactory.create(this));
        LlmProvider p = getLlmProvider();
        String name = getConfig().getString("llm.provider", "none");
        if (p.isConfigured()) {
            getLogger().info("LLM provider ready (" + name + ").");
        } else if (name != null && !name.isBlank() && !"none".equalsIgnoreCase(name.trim())) {
            getLogger().warning("LLM provider '" + name + "' is not configured (check llm.api-key / llm.api-key-env / llm.model).");
        }
    }

    /**
     * Directory for RAG text files ({@code llm.context-directory} under the plugin data folder). Created on enable when possible.
     */
    public Path getLlmContextDirectory() {
        return LlmRagContextBuilder.resolveContextRoot(this);
    }

    /**
     * When {@code llm.rag.enabled} is true, appends bounded excerpts from {@link #getLlmContextDirectory()} to the system prompt.
     */
    public String augmentLlmSystemPrompt(String baseSystemPrompt) {
        return LlmRagContextBuilder.appendIfEnabled(this, baseSystemPrompt);
    }

    public WorldLlmPromptRegistry getWorldLlmPromptRegistry() {
        return worldLlmPromptRegistry;
    }

    /**
     * Resolves the LLM system prompt for a world (per-world YAML, then default.yml, then config.yml).
     */
    public String buildLlmSystemPrompt(World world) {
        if (world == null) {
            return worldLlmPromptRegistry.buildSystemPrompt(this, null);
        }
        return worldLlmPromptRegistry.buildSystemPrompt(this, world.getName());
    }

    public String buildLlmSystemPrompt(Player player) {
        if (player == null) {
            return buildLlmSystemPrompt((World) null);
        }
        return buildLlmSystemPrompt(player.getWorld());
    }

    /**
     * Method when server is stopped
     */
    @Override
    public void onDisable(){
        for (Map.Entry<String,NPC> entry : agents.entrySet()){
            NPC npc = entry.getValue();
            npc.destroy();
        }
        removeAgents();
    }

    /**
     * Returns current sessions on server
     * @return sessions on server
     */
    public HashMap<Player,Long> getPlayerSessions(){return this.sessions;}

    public Queryer getQueryer(){return queryer;}

    public Map<String, NPC> getAgents(){return agents;}

    /** When false, hides builder dialogue options and blocks {@code /agent rebuilderspawn}. */
    public boolean isBuilderEnabled() {
        return getConfig().getBoolean("builder.enabled", false);
    }

    public void removeAgents(){
        agents = new HashMap<>();
    }

    public void removeAgent(String playerName){
        agents.remove(playerName);
    }

    /**
     * Permanently removes every Citizens NPC assigned to {@code playerName} and drops them from {@link #agents}.
     * Destroy must clear Citizens persistence, not just the in-memory map, or {@link #relinkOwnedAgent} will
     * resurrect the agent on the next login.
     */
    public void destroyOwnedAgent(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        agents.remove(playerName);
        List<NPC> snapshot = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            snapshot.add(npc);
        }
        for (NPC npc : snapshot) {
            if (!playerName.equalsIgnoreCase(resolveNpcOwnerName(npc))) {
                continue;
            }
            destroyCitizensNpc(npc);
        }
    }

    /** Removes all player-owned agent NPCs from Citizens and clears {@link #agents}. */
    public void destroyAllOwnedAgents() {
        for (String owner : new ArrayList<>(agents.keySet())) {
            destroyOwnedAgent(owner);
        }
        List<NPC> snapshot = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            snapshot.add(npc);
        }
        for (NPC npc : snapshot) {
            if (resolveNpcOwnerName(npc) != null) {
                destroyCitizensNpc(npc);
            }
        }
        agents.clear();
    }

    private static void destroyCitizensNpc(NPC npc) {
        if (npc == null) {
            return;
        }
        try {
            if (npc.isSpawned()) {
                npc.despawn();
            }
            npc.destroy();
        } catch (Exception ex) {
            JavaPlugin.getPlugin(OverworldAgent.class).getLogger().warning(
                    "Failed to destroy agent NPC " + npc.getId() + ": " + ex.getMessage());
        }
    }

    /** @return owning player name for guide/builder agent NPCs, or null */
    public static String resolveNpcOwnerName(NPC npc) {
        if (npc == null) {
            return null;
        }
        if (npc.hasTrait(SpawnExpertTrait.class)) {
            return npc.getOrAddTrait(SpawnExpertTrait.class).getAssignedPlayerName();
        }
        if (npc.hasTrait(SpawnNoviceTrait.class)) {
            SpawnNoviceTrait trait = npc.getTrait(SpawnNoviceTrait.class);
            return trait != null ? trait.getAssignedPlayerName() : null;
        }
        if (npc.hasTrait(RebuilderTrait.class)) {
            RebuilderTrait trait = npc.getTrait(RebuilderTrait.class);
            return trait != null ? trait.getTargetPlayerName() : null;
        }
        return null;
    }

    public SignMenuFactory getSignMenuFactory(){return signMenuFactory; }

    public ChatTextInputFactory getChatTextInputFactory() {
        return chatTextInputFactory;
    }

    public SpigotCallback getSpigotCallback() {
        return spigotCallback;
    }

    /**
     * LLM backend for natural chat replies ({@code llm.use-for-reply} in config). On startup this is set from
     * {@code llm.provider} (OpenAI, Gemini, OpenAI-compatible local). Call {@link #setLlmProvider(LlmProvider)}
     * from another plugin to override.
     */
    public LlmProvider getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(LlmProvider llmProvider) {
        this.llmProvider = llmProvider != null ? llmProvider : new NoOpLlmProvider();
    }

    public ExpertSpawnCommand getExpertSpawnCommand() {
        return expertSpawnCommand;
    }

    /**
     * Ensures edit quotas exist (join handler normally creates them; this covers late loads or edge cases).
     */
    public void ensureAgentEdits(Player player) {
        if (player == null) {
            return;
        }
        agentEdits.computeIfAbsent(player, p -> {
            HashMap<String, Integer> e = new HashMap<>();
            e.put("Name", 0);
            e.put("Skin", 0);
            e.put("Type", 0);
            return e;
        });
    }

    /**
     * Re-populates {@link #agents} after restart or reconnect by scanning Citizens NPCs with
     * {@link SpawnExpertTrait} / {@link RebuilderTrait} (in-memory map is not persisted).
     */
    /**
     * Ensures {@code WHIMC-StudentFeedback} has a session start time for this player.
     * {@code ProgressCommand} returns without output when {@code sessionStart == null}.
     */
    public void ensureStudentFeedbackSession(Player player) {
        if (player == null || Bukkit.getPluginManager().getPlugin("WHIMC-StudentFeedback") == null) {
            return;
        }
        try {
            Class<?> cl = Class.forName("edu.whimc.feedback.StudentFeedback");
            Method getInstance = cl.getMethod("getInstance");
            Object feedback = getInstance.invoke(null);
            if (feedback == null) {
                return;
            }
            Method getSessions = cl.getMethod("getPlayerSessions");
            Object sessions = getSessions.invoke(feedback);
            if (sessions instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<Player, Long> typed = (Map<Player, Long>) map;
                typed.putIfAbsent(player, System.currentTimeMillis());
            }
        } catch (Throwable ex) {
            getLogger().fine("Could not ensure StudentFeedback session for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void relinkOwnedAgent(Player player) {
        if (player == null) {
            return;
        }
        String name = player.getName();
        if (agents.containsKey(name)) {
            return;
        }
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            String owner = resolveNpcOwnerName(npc);
            if (owner != null && name.equalsIgnoreCase(owner)) {
                agents.put(name, npc);
                return;
            }
        }
    }
    public HashMap<Player,HashMap<String, Integer>> getAgentEdits(){
        return agentEdits;
    }
    public void addTemplate(Player player, BuildTemplate template){
        if(!buildTemplates.containsKey(player)){
            buildTemplates.put(player, new ArrayList<>());
        }
        buildTemplates.get(player).add(template);
    }
    public void addInProgressTemplate(Player player, BuilderDialogue dialogue){
        inProgressTemplates.put(player,dialogue);
    }
    public void removeInProgressTemplate(Player player){
        inProgressTemplates.remove(player);
    }
    public HashMap<Player, List<BuildTemplate>> getBuildTemplates(){
        return buildTemplates;
    }
    public HashMap<Player, BuilderDialogue> getInProgressTemplates(){
        return inProgressTemplates;
    }
    public void resetTemplates(String toRemove){
        if(toRemove.equalsIgnoreCase("all")){
            buildTemplates = new HashMap<>();
        } else {
            buildTemplates.get(Bukkit.getPlayer(toRemove)).clear();
        }
    }
    public void removeTemplate(Player player, BuildTemplate template){
        buildTemplates.get(player).remove(template);
    }
    public String getSkinType(){
        return skinType;
    }
    public void setSkinType(String skinType){
        this.skinType = skinType;
    }

}
