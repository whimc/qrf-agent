package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Periodic follow catch-up: teleports beside the owner when far behind, and nudges off the player's
 * block when Citizens snaps the agent on top (cross-world follow or stuck recovery).
 */
public class AgentFollowCatchUpTrait extends net.citizensnpcs.api.trait.Trait {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private final OverworldAgent plugin;
    private int tickCounter;

    public AgentFollowCatchUpTrait() {
        super("agentfollowcatchup");
        plugin = JavaPlugin.getPlugin(OverworldAgent.class);
    }

    @Override
    public void run() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (!npc.hasTrait(FollowTrait.class) || !npc.getTrait(FollowTrait.class).isEnabled()) {
            return;
        }
        Player player = AgentFollowCatchUp.followedPlayer(npc);
        if (player == null) {
            return;
        }
        AgentFollowCatchUp.applyIfNeeded(plugin, npc, player);
    }
}
