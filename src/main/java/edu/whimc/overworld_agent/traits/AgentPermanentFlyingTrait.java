package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.trait.FollowTrait;
import net.citizensnpcs.trait.Gravity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Non-{@link EntityType#PLAYER} agents use no gravity and glide toward a hover point near their
 * owner (beside when idle, behind while moving). Player-shaped agents use normal gravity and walking,
 * except when the owner is in spectator / flight (soft air-follow).
 */
public class AgentPermanentFlyingTrait extends Trait {

    private static final String CONFIG_HOVER_KEY = "agent-non-player-hover-height";
    private static final String CONFIG_SPEED_MULT_KEY = "agent-non-player-navigator-speed-modifier";
    private static final String CONFIG_MOB_FOLLOW_SPEED = "agent-mob-follow-speed";
    private static final String CONFIG_MOB_STOP_DISTANCE = "agent-mob-follow-stop-distance";
    private static final String CONFIG_IDLE_LOCK_MOVE = "agent-mob-idle-lock-move-blocks";

    private final OverworldAgent plugin;

    /** Locked idle hover spot so looking around does not make the mob orbit. */
    private Location lockedIdleSpot;
    private Location idleLockAnchor;
    private boolean wasAirFollowing;

    public AgentPermanentFlyingTrait() {
        super("agentpermanentflying");
        plugin = JavaPlugin.getPlugin(OverworldAgent.class);
    }

    @Override
    public void onSpawn() {
        applyFlyingForCurrentEntity();
        lockedIdleSpot = null;
        idleLockAnchor = null;
        wasAirFollowing = false;
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
            npc.getEntity().setGravity(true);
            if (npc.getEntity() instanceof Player player) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
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
        npc.getEntity().setGravity(false);

        double mult = plugin.getConfig().getDouble(CONFIG_SPEED_MULT_KEY, 1.65);
        float modifier = mult > 0 ? (float) mult : 1.0F;
        npc.getNavigator().getDefaultParameters().speedModifier(modifier);
        npc.getNavigator().getLocalParameters().speedModifier(modifier);

        if (npc.hasTrait(FollowTrait.class)) {
            npc.getTrait(FollowTrait.class).follow(null);
        }
        AgentFollowTuning.applyForCurrentEntity(plugin, npc);
    }

    @Override
    public void run() {
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        Entity entity = npc.getEntity();
        Player owner = AgentFollowCatchUp.followedPlayer(npc);
        if (owner == null || !owner.isOnline()) {
            if (entity.getType() != EntityType.PLAYER) {
                entity.setVelocity(new Vector(0, 0, 0));
            }
            clearIdleLock();
            return;
        }
        if (!owner.getWorld().equals(entity.getWorld())) {
            return;
        }

        // Player agents: soft-follow in spectator / flight; otherwise walk via FollowTrait.
        if (entity.getType() == EntityType.PLAYER) {
            boolean air = AgentFollowCatchUp.tickPlayerAirFollow(plugin, npc, entity, owner);
            if (wasAirFollowing && !air) {
                // Left spectator/flight — restore normal walking follow.
                AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, owner);
            }
            wasAirFollowing = air;
            return;
        }

        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }

        double hover = plugin.getConfig().getDouble(CONFIG_HOVER_KEY, 2.0);
        if (hover <= 0 && !AgentFollowCatchUp.ownerNeedsAirFollow(owner)) {
            return;
        }

        double idleSpeed = plugin.getConfig().getDouble("agent-mob-idle-settle-speed", 0.08);
        boolean idle = isOwnerIdle(owner, idleSpeed);
        Location target;
        if (idle) {
            target = resolveLockedIdleTarget(owner, entity.getHeight());
        } else {
            clearIdleLock();
            target = AgentFollowCatchUp.mobFollowTarget(plugin, owner, entity.getHeight());
        }
        if (target == null) {
            return;
        }

        Location current = entity.getLocation();
        Vector delta = target.toVector().subtract(current.toVector());
        double distance = delta.length();
        // Larger stop distance while idle so they hold still once near the settle spot.
        double stopDistance = idle
                ? plugin.getConfig().getDouble("agent-mob-idle-stop-distance", 0.85)
                : plugin.getConfig().getDouble(CONFIG_MOB_STOP_DISTANCE, 0.35);
        if (distance <= stopDistance) {
            entity.setVelocity(new Vector(0, 0, 0));
            return;
        }

        double speed = plugin.getConfig().getDouble(CONFIG_MOB_FOLLOW_SPEED, 0.32);
        if (idle) {
            speed = Math.min(speed, 0.18);
        }
        Vector velocity = delta.normalize().multiply(Math.min(speed, distance));
        entity.setVelocity(velocity);
    }

    private static boolean isOwnerIdle(Player owner, double idleSpeed) {
        // Mirror AgentFollowCatchUp idle rules without exposing the private helper.
        if (owner.getVehicle() != null) {
            return false;
        }
        if (owner.isGliding() || owner.isRiptiding()) {
            return false;
        }
        if (owner.getGameMode() != org.bukkit.GameMode.SPECTATOR && owner.isFlying()) {
            return false;
        }
        Vector velocity = owner.getVelocity();
        return Math.hypot(velocity.getX(), velocity.getZ()) <= idleSpeed;
    }

    private Location resolveLockedIdleTarget(Player owner, double entityHeight) {
        double lockMove = plugin.getConfig().getDouble(CONFIG_IDLE_LOCK_MOVE, 0.75);
        Location ownerLoc = owner.getLocation();
        if (lockedIdleSpot == null || idleLockAnchor == null
                || !idleLockAnchor.getWorld().equals(ownerLoc.getWorld())
                || horizontalDistance(idleLockAnchor, ownerLoc) > lockMove) {
            lockedIdleSpot = AgentFollowCatchUp.mobFollowTarget(plugin, owner, entityHeight);
            idleLockAnchor = ownerLoc.clone();
        } else if (AgentFollowCatchUp.ownerNeedsAirFollow(owner) && lockedIdleSpot != null) {
            // Keep X/Z locked; track spectator Y so they stay at the owner's height.
            lockedIdleSpot.setY(ownerLoc.getY());
        }
        return lockedIdleSpot == null ? null : lockedIdleSpot.clone();
    }

    private void clearIdleLock() {
        lockedIdleSpot = null;
        idleLockAnchor = null;
    }

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
