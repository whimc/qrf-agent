package edu.whimc.overworld_agent;

import edu.whimc.overworld_agent.traits.AgentPermanentFlyingTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

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
     * Ow agent NPCs use rideable mob types (horse, pig, strider, …); block mounting so right-click
     * only opens dialogue and does not put the player in the saddle.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityMount(EntityMountEvent event) {
        Entity mount = event.getMount();
        if (mount == null) {
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(mount);
        if (npc == null) {
            return;
        }
        if (plugin.getAgents().containsValue(npc)) {
            event.setCancelled(true);
        }
    }

    /**
     * When players join their agent is respawned and edit quotas are ensured.
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
        plugin.relinkOwnedAgent(player);
        Map<String, NPC> agents = plugin.getAgents();
        NPC npc = agents.get(player.getName());
        if(npc != null) {
            npc.getOrAddTrait(AgentPermanentFlyingTrait.class);
            npc.spawn(player.getLocation());
            // Despawn on quit clears the live follow target; re-attach so pathing resumes after reconnect.
            npc.getOrAddTrait(FollowTrait.class).follow(player);
        }
    }
}
