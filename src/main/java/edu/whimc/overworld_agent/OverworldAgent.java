package edu.whimc.overworld_agent;

import com.jyckos.speechreceiver.SpeechReceiver;
import edu.whimc.observations.models.Observation;
import edu.whimc.overworld_agent.commands.*;
import edu.whimc.overworld_agent.dialoguetemplate.BuilderDialogue;
import edu.whimc.overworld_agent.dialoguetemplate.SignMenuFactory;
import edu.whimc.overworld_agent.dialoguetemplate.Tag;
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
import net.citizensnpcs.api.npc.NPC;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        signMenuFactory = new SignMenuFactory(this);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
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
