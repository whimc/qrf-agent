package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.entity.EntityType;

/**
 * Per–entity-type follow distances: player-shaped agents path and walk like normal NPCs;
 * mob agents stay close while hovering with {@link AgentPermanentFlyingTrait}.
 *
 * @see <a href="https://jd.citizensnpcs.co/net/citizensnpcs/api/ai/NavigatorParameters.html">NavigatorParameters</a>
 */
public final class AgentFollowTuning {

    private static final String CFG_PLAYER_RANGE = "agent-player-follow-path-range";
    private static final String CFG_PLAYER_MARGIN = "agent-player-follow-margin";
    private static final String CFG_PLAYER_DEST_TELEPORT = "agent-player-nav-destination-teleport-margin";
    private static final String CFG_PLAYER_STATIONARY_TICKS = "agent-player-nav-stationary-ticks";
    private static final String CFG_MOB_RANGE = "agent-mob-follow-path-range";
    private static final String CFG_MOB_MARGIN = "agent-mob-follow-margin";

    private static final AgentFollowStuckAction PLAYER_STUCK_ACTION = new AgentFollowStuckAction();

    private AgentFollowTuning() {}

    /**
     * Re-attach follow and navigator tuning on the next tick after {@link NPC#spawn(org.bukkit.Location)}.
     * Citizens often needs this so {@link FollowTrait} and pathfinding see a fully spawned entity (see Citizens2 #2353).
     */
    public static void scheduleFollowAndApplyTraits(OverworldAgent plugin, NPC npc, org.bukkit.entity.Player player) {
        if (plugin == null || npc == null || player == null) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!npc.isSpawned() || !player.isOnline()) {
                return;
            }
            npc.getOrAddTrait(FollowTrait.class).follow(player);
            npc.getOrAddTrait(AgentFollowCatchUpTrait.class);
            npc.getOrAddTrait(AgentPermanentFlyingTrait.class).applyFlyingForCurrentEntity();
        });
    }

    /**
     * Use when {@link NPC#getEntity()} is not spawned yet (e.g. right after {@link NPC#createNPC}).
     */
    public static void applyForPlannedType(OverworldAgent plugin, NPC npc, EntityType type) {
        if (npc == null || type == null) {
            return;
        }
        FollowTrait ft = npc.getOrAddTrait(FollowTrait.class);
        if (type == EntityType.PLAYER) {
            float range = (float) plugin.getConfig().getDouble(CFG_PLAYER_RANGE, 48);
            double margin = plugin.getConfig().getDouble(CFG_PLAYER_MARGIN, 2.5);
            double destTele = plugin.getConfig().getDouble(CFG_PLAYER_DEST_TELEPORT, -1.0);
            int stationaryTicks = plugin.getConfig().getInt(CFG_PLAYER_STATIONARY_TICKS, 1200);
            npc.getNavigator().getDefaultParameters().range(range);
            npc.getNavigator().getLocalParameters().range(range);
            npc.getNavigator().getDefaultParameters().destinationTeleportMargin(destTele);
            npc.getNavigator().getLocalParameters().destinationTeleportMargin(destTele);
            npc.getNavigator().getDefaultParameters().stationaryTicks(stationaryTicks);
            npc.getNavigator().getLocalParameters().stationaryTicks(stationaryTicks);
            // Walk like the original guide agents: straight-line targeting makes Citizens steer
            // directly at the player (gliding over terrain) instead of A* pathfinding + walking.
            // 0 disables it so player-shaped agents always pathfind and WALK while following
            // (Citizens wiki "Making an NPC Move": navigator.setTarget -> pathfind).
            npc.getNavigator().getDefaultParameters().straightLineTargetingDistance(0);
            npc.getNavigator().getLocalParameters().straightLineTargetingDistance(0);
            npc.getNavigator().getDefaultParameters().stuckAction(PLAYER_STUCK_ACTION);
            npc.getNavigator().getLocalParameters().stuckAction(PLAYER_STUCK_ACTION);
            ft.setFollowingMargin(margin);
        } else {
            float range = (float) plugin.getConfig().getDouble(CFG_MOB_RANGE, 5);
            double margin = plugin.getConfig().getDouble(CFG_MOB_MARGIN, 1.25);
            npc.getNavigator().getDefaultParameters().range(range);
            npc.getNavigator().getLocalParameters().range(range);
            // Hovering mobs: switch to direct steering sooner so pathfinding does not leave them sliding in XZ at the wrong Y.
            npc.getNavigator().getDefaultParameters().straightLineTargetingDistance(Math.min(range, 6.0f));
            npc.getNavigator().getLocalParameters().straightLineTargetingDistance(Math.min(range, 6.0f));
            ft.setFollowingMargin(margin);
        }
    }

    /**
     * Use after spawn or after {@link NPC#setBukkitEntityType} when the live entity is available.
     */
    public static void applyForCurrentEntity(OverworldAgent plugin, NPC npc) {
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        applyForPlannedType(plugin, npc, npc.getEntity().getType());
    }
}
