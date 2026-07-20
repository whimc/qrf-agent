package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Periodic recovery: teleports a <em>spawned</em> agent to its owner when they are in different
 * worlds or the agent is lost (void / too far). Interval from
 * {@code agent-follow-recovery-interval-seconds} in config (default 10s).
 */
public class AgentFollowCatchUpTrait extends net.citizensnpcs.api.trait.Trait {

    private final OverworldAgent plugin;
    private int tickCounter;
    private int checkIntervalTicks = -1;

    public AgentFollowCatchUpTrait() {
        super("agentfollowcatchup");
        plugin = JavaPlugin.getPlugin(OverworldAgent.class);
    }

    @Override
    public void run() {
        if (checkIntervalTicks < 0) {
            checkIntervalTicks = AgentFollowCatchUp.recoveryIntervalTicks(plugin);
        }
        if (++tickCounter < checkIntervalTicks) {
            return;
        }
        tickCounter = 0;
        checkIntervalTicks = AgentFollowCatchUp.recoveryIntervalTicks(plugin);
        Player player = AgentFollowCatchUp.followedPlayer(npc);
        if (player == null || !player.isOnline()) {
            return;
        }
        AgentFollowCatchUp.recoverIfNeeded(plugin, npc, player);
        // Nudge off the player if Citizens stacked the agent on top after a teleport/stuck snap.
        AgentFollowCatchUp.applyIfNeeded(plugin, npc, player);
    }
}
