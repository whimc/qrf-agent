package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.StuckAction;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Replaces Citizens default {@code TeleportStuckAction}, which snaps the NPC onto the follow target.
 * Only catch-up teleports when the followed player is beyond {@link AgentFollowCatchUp#catchUpDistance}.
 */
public final class AgentFollowStuckAction implements StuckAction {

    private final OverworldAgent plugin;

    public AgentFollowStuckAction() {
        plugin = JavaPlugin.getPlugin(OverworldAgent.class);
    }

    @Override
    public boolean run(NPC npc, Navigator navigator) {
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null || navigator == null) {
            return false;
        }
        Player player = AgentFollowCatchUp.followedPlayer(npc);
        if (player == null) {
            return false;
        }
        double horizontal = horizontalDistance(npc.getEntity().getLocation(), player.getLocation());
        if (horizontal <= AgentFollowCatchUp.catchUpDistance(plugin)) {
            navigator.setTarget(player, false);
            return true;
        }
        AgentFollowCatchUp.teleportBeside(plugin, npc, player);
        navigator.setTarget(player, false);
        return true;
    }

    private static double horizontalDistance(org.bukkit.Location a, org.bukkit.Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
