package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.entity.EntityType;

/**
 * Per–entity-type follow distances: player-shaped agents path and walk like normal NPCs;
 * mob agents stay close while hovering with {@link AgentPermanentFlyingTrait}.
 */
public final class AgentFollowTuning {

    private static final String CFG_PLAYER_RANGE = "agent-player-follow-path-range";
    private static final String CFG_PLAYER_MARGIN = "agent-player-follow-margin";
    private static final String CFG_MOB_RANGE = "agent-mob-follow-path-range";
    private static final String CFG_MOB_MARGIN = "agent-mob-follow-margin";

    private AgentFollowTuning() {}

    /**
     * Use when {@link NPC#getEntity()} is not spawned yet (e.g. right after {@link NPC#createNPC}).
     */
    public static void applyForPlannedType(OverworldAgent plugin, NPC npc, EntityType type) {
        if (npc == null || type == null) {
            return;
        }
        FollowTrait ft = npc.getOrAddTrait(FollowTrait.class);
        if (type == EntityType.PLAYER) {
            float range = (float) plugin.getConfig().getDouble(CFG_PLAYER_RANGE, 10);
            double margin = plugin.getConfig().getDouble(CFG_PLAYER_MARGIN, 2.5);
            npc.getNavigator().getDefaultParameters().range(range);
            npc.getNavigator().getLocalParameters().range(range);
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
