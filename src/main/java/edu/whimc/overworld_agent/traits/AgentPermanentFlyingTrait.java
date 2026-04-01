package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.trait.Gravity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Non-{@link EntityType#PLAYER} agents use no gravity and are kept a configurable height above
 * the surface under them. Player-shaped agents use normal gravity and walking.
 */
public class AgentPermanentFlyingTrait extends Trait {

    private static final String CONFIG_HOVER_KEY = "agent-non-player-hover-height";
    private static final String CONFIG_SPEED_MULT_KEY = "agent-non-player-navigator-speed-modifier";
    private static final double ADJUST_EPSILON = 0.04;
    private static final double MAX_VERTICAL_STEP_PER_TICK = 0.45;

    private final OverworldAgent plugin;

    public AgentPermanentFlyingTrait() {
        super("agentpermanentflying");
        plugin = JavaPlugin.getPlugin(OverworldAgent.class);
    }

    @Override
    public void onSpawn() {
        applyFlyingForCurrentEntity();
    }

    /**
     * Applies gravity/flight state from the live entity type (after mob type changes and respawn).
     */
    public void applyFlyingForCurrentEntity() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        EntityType type = npc.getEntity().getType();
        boolean nonPlayer = type != EntityType.PLAYER;

        Gravity gravity = npc.getOrAddTrait(Gravity.class);
        gravity.setHasGravity(!nonPlayer);

        if (npc.getEntity() instanceof Player player) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }

        float baseline = npc.getNavigator().getDefaultParameters().speed();
        npc.setFlyable(nonPlayer);

        if (nonPlayer) {
            double mult = plugin.getConfig().getDouble(CONFIG_SPEED_MULT_KEY, 1.65);
            if (mult > 0) {
                npc.getNavigator().getLocalParameters().speedModifier(baseline * (float) mult);
            } else {
                npc.getNavigator().getLocalParameters().speedModifier(baseline);
            }
        } else {
            npc.getNavigator().getLocalParameters().speedModifier(baseline);
        }
    }

    @Override
    public void run() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        Entity entity = npc.getEntity();
        if (entity.getType() == EntityType.PLAYER) {
            return;
        }
        double hover = plugin.getConfig().getDouble(CONFIG_HOVER_KEY, 2.0);
        if (hover <= 0) {
            return;
        }

        // Per-tick teleport fights Citizens pathfinding / FollowTrait; only lock height when idle.
        if (npc.getNavigator().isNavigating()) {
            return;
        }

        Location cur = entity.getLocation();
        World world = cur.getWorld();
        if (world == null) {
            return;
        }

        double surfaceY = surfaceYBelow(world, cur, entity.getHeight());
        double targetY = surfaceY + hover;
        if (Math.abs(cur.getY() - targetY) < ADJUST_EPSILON) {
            return;
        }

        double dy = targetY - cur.getY();
        double newY = cur.getY() + Math.signum(dy) * Math.min(MAX_VERTICAL_STEP_PER_TICK, Math.abs(dy));
        Location to = cur.clone();
        to.setY(newY);
        entity.teleport(to);
    }

    /**
     * Top Y of the walkable surface below the agent (feet height), using a short downward ray
     * from above the mob so caves and porches resolve to local floor, not the skylight map.
     */
    private static double surfaceYBelow(World world, Location feet, double entityHeight) {
        double clearance = Math.max(1.0, entityHeight * 0.95);
        double startY = Math.min(feet.getY() + clearance, world.getMaxHeight() - 1.0);
        Location start = new Location(world, feet.getX(), startY, feet.getZ());
        double maxLen = Math.max(2.0, startY - world.getMinHeight() + 4.0);
        RayTraceResult hit = world.rayTraceBlocks(start, new Vector(0, -1, 0), maxLen, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlock() != null) {
            return hit.getHitBlock().getY() + 1.0;
        }
        return world.getHighestBlockYAt(feet) + 1.0;
    }
}
