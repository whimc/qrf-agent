package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.trait.FollowTrait;
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
        if (npc.isSpawned() && npc.getEntity() != null) {
            npc.getEntity().getPassengers().forEach(npc.getEntity()::removePassenger);
        }
    }

    /**
     * Applies gravity/flight state from the live entity type (after mob type changes and respawn).
     * <p>Player-shaped NPCs: do not use Citizens {@link Gravity} (it fights vanilla walking + {@link FollowTrait} on
     * modern Paper). Mob agents keep no-gravity hover behavior.
     */
    public void applyFlyingForCurrentEntity() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        EntityType type = npc.getEntity().getType();
        boolean nonPlayer = type != EntityType.PLAYER;

        if (!nonPlayer) {
            npc.setFlyable(false);
            if (npc.hasTrait(Gravity.class)) {
                npc.removeTrait(Gravity.class);
            }
            // Removing the Gravity trait does not necessarily restore entity gravity (e.g. after a mob form).
            npc.getEntity().setGravity(true);
            if (npc.getEntity() instanceof Player player) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            // speedModifier() is a percentage (1.0 = normal walk speed). The old code passed speed()
            // (absolute blocks/tick, ~0.2 for players), which made player agents crawl-glide at ~20%
            // speed instead of walking. Set on default params too: locals are recreated from defaults
            // every time FollowTrait re-targets, so local-only values are lost between path updates.
            npc.getNavigator().getDefaultParameters().speedModifier(1.0F);
            npc.getNavigator().getLocalParameters().speedModifier(1.0F);
            if (npc.hasTrait(FollowTrait.class)) {
                AgentFollowTuning.applyForCurrentEntity(plugin, npc);
            }
            return;
        }

        Gravity gravity = npc.getOrAddTrait(Gravity.class);
        gravity.setHasGravity(false);

        npc.setFlyable(true);

        double mult = plugin.getConfig().getDouble(CONFIG_SPEED_MULT_KEY, 1.65);
        float modifier = mult > 0 ? (float) mult : 1.0F;
        npc.getNavigator().getDefaultParameters().speedModifier(modifier);
        npc.getNavigator().getLocalParameters().speedModifier(modifier);

        if (npc.hasTrait(FollowTrait.class)) {
            AgentFollowTuning.applyForCurrentEntity(plugin, npc);
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
        // While Citizens is actively pathfinding, do not teleport — hover fights horizontal navigation.
        // FollowTrait can be "active" between path updates; allow vertical correction then so hover height catches up.
        if (npc.getNavigator().isNavigating()) {
            return;
        }

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
