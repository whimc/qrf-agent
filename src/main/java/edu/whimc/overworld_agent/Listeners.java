package edu.whimc.overworld_agent;

import edu.whimc.overworld_agent.traits.AgentPermanentFlyingTrait;
import edu.whimc.overworld_agent.dialoguetemplate.events.BuildAssessEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Listeners  implements Listener {

    private final OverworldAgent plugin;
    public Listeners(OverworldAgent plugin) {
        this.plugin = plugin;
    }

    /**
     * When players leave their agent is despawned
     * @param event PlayerQuitEvent
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){

        Player player = event.getPlayer();
        HashMap<Player,HashMap<String, Integer>> agentEdits = plugin.getAgentEdits();
        agentEdits.remove(player);
        HashMap<Player,Long> sessions = plugin.getPlayerSessions();
        sessions.remove(player);
        Map<String, NPC> agents = plugin.getAgents();
        NPC npc = agents.get(player.getName());
        if(npc != null) {
            npc.despawn();
        }
    }

    /**
     * When players join their agent is spawned
     * @param event PlayerJoinEvent
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        HashMap<Player,Long> sessions = plugin.getPlayerSessions();
        sessions.putIfAbsent(player, System.currentTimeMillis());
        HashMap<String, Integer> edits = new HashMap<>();
        edits.put("Name", 0);
        edits.put("Skin", 0);
        edits.put("Type", 0);
        HashMap<Player,HashMap<String, Integer>> agentEdits = plugin.getAgentEdits();
        agentEdits.putIfAbsent(player, edits);
        Map<String, NPC> agents = plugin.getAgents();
        NPC npc = agents.get(player.getName());
        if(npc != null) {
            npc.getOrAddTrait(AgentPermanentFlyingTrait.class);
            npc.spawn(player.getLocation());
        }
    }
}
