package edu.whimc.overworld_agent;

import com.jyckos.speechreceiver.SpeechReceiver;
import edu.whimc.observations.models.Observation;
import edu.whimc.overworld_agent.commands.*;
import edu.whimc.overworld_agent.commands.subcommands.ExpertSpawnCommand;
import edu.whimc.overworld_agent.dialoguetemplate.BuilderDialogue;
import edu.whimc.overworld_agent.dialoguetemplate.ChatTextInputFactory;
import edu.whimc.overworld_agent.dialoguetemplate.SpigotCallback;
import edu.whimc.overworld_agent.dialoguetemplate.SignMenuFactory;
import edu.whimc.overworld_agent.dialoguetemplate.Tag;
import edu.whimc.overworld_agent.dialoguetemplate.models.LlmProvider;
import edu.whimc.overworld_agent.dialoguetemplate.models.NoOpLlmProvider;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.LlmProviderFactory;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.LlmRagContextBuilder;
import edu.whimc.overworld_agent.dialoguetemplate.models.BuildTemplate;
import edu.whimc.overworld_agent.dialoguetemplate.models.DialogueType;
import edu.whimc.overworld_agent.utils.sql.Queryer;

import org.bukkit.Bukkit;
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
    private DialogueType agentType;
    private Queryer queryer;
    private List<String> profanity;
    private SignMenuFactory signMenuFactory;
    private ChatTextInputFactory chatTextInputFactory;
    /** Single instance; {@link edu.whimc.overworld_agent.dialoguetemplate.Dialogue} registers clicks here (see /oacallback). */
    private SpigotCallback spigotCallback;
    private LlmProvider llmProvider = new NoOpLlmProvider();
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
        //receiver = (SpeechReceiver) Bukkit.getServer().getPluginManager().getPlugin("SpeechReceiver");
        agentType = DialogueType.GUIDE;
        sessions = new HashMap<>();
        buildTemplates = new HashMap<>();
        inProgressTemplates = new HashMap<>();
        Tag.instantiate(this);

        this.queryer = new Queryer(this, q -> {
            // If we couldn't connect to the database disable the plugin
            if (q == null) {
                this.getLogger().severe("Could not establish MySQL connection! Disabling plugin...");
                getCommand("agent").setExecutor(this);
                return;
            }

        });

        Tag.startExpiredObservationScanningTask(this);

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

        expertSpawnCommand = new ExpertSpawnCommand(this, "agents", "spawn");

        AgentCommand agentCommand = new AgentCommand(this);
        getCommand("agent").setExecutor(agentCommand);
        getCommand("agent").setTabCompleter(agentCommand);

        AgentsCommand agentsCommand = new AgentsCommand(this);
        getCommand("agents").setExecutor(agentsCommand);
        getCommand("agents").setTabCompleter(agentsCommand);

        TagAdminCommand tagCommand = new TagAdminCommand(this);
        getCommand("admintags").setExecutor(tagCommand);
        getCommand("admintags").setTabCompleter(tagCommand);

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
        } catch (IOException e) {
            getLogger().log(Level.FINE, "LLM context directory not created yet: " + e.getMessage());
        }
        setupLlmFromConfig();
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

    public void removeAgents(){
        agents = new HashMap<>();
    }

    public void removeAgent(String playerName){
        agents.remove(playerName);
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
            Object plugin = getInstance.invoke(null);
            if (plugin == null) {
                return;
            }
            Method getSessions = cl.getMethod("getPlayerSessions");
            Object sessions = getSessions.invoke(plugin);
            if (sessions instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<Player, Object> typed = (Map<Player, Object>) map;
                typed.putIfAbsent(player, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {
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
            if (npc.hasTrait(SpawnExpertTrait.class)) {
                SpawnExpertTrait t = npc.getOrAddTrait(SpawnExpertTrait.class);
                if (name.equals(t.getAssignedPlayerName())) {
                    agents.put(name, npc);
                    return;
                }
            }
            if (npc.hasTrait(SpawnNoviceTrait.class)) {
                SpawnNoviceTrait t = npc.getTrait(SpawnNoviceTrait.class);
                if (t != null && name.equals(t.getAssignedPlayerName())) {
                    agents.put(name, npc);
                    return;
                }
            }
            if (npc.hasTrait(RebuilderTrait.class)) {
                RebuilderTrait t = npc.getTrait(RebuilderTrait.class);
                if (t != null && name.equals(t.getTargetPlayerName())) {
                    agents.put(name, npc);
                    return;
                }
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
    public void setAgentType(DialogueType type){this.agentType = type;}
    public DialogueType getAgentType(){return agentType;}

}
