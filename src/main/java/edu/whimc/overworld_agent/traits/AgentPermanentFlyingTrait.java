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
 * Non-{@link EntityType#PLAYER} agents use no gravity and glide toward a hover point behind their
 * owner. Player-shaped agents use normal gravity and walking (unchanged).
 */
public class AgentPermanentFlyingTrait extends Trait {

    private static final String CONFIG_HOVER_KEY = "agent-non-player-hover-height";
    private static final String CONFIG_SPEED_MULT_KEY = "agent-non-player-navigator-speed-modifier";
    private static final String CONFIG_MOB_FOLLOW_SPEED = "agent-mob-follow-speed";
    private static final String CONFIG_MOB_STOP_DISTANCE = "agent-mob-follow-stop-distance";

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
        if (entity.getType() == EntityType.PLAYER) {
            return;
        }

        Player owner = AgentFollowCatchUp.followedPlayer(npc);
        if (owner == null || !owner.isOnline()) {
            entity.setVelocity(new Vector(0, 0, 0));
            return;
        }
        if (!owner.getWorld().equals(entity.getWorld())) {
            return;
        }

        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }

        double hover = plugin.getConfig().getDouble(CONFIG_HOVER_KEY, 2.0);
        if (hover <= 0) {
            return;
        }

        Location target = AgentFollowCatchUp.mobFollowTarget(plugin, owner, entity.getHeight());
        if (target == null) {
            return;
        }

        Location current = entity.getLocation();
        Vector delta = target.toVector().subtract(current.toVector());
        double distance = delta.length();
        double stopDistance = plugin.getConfig().getDouble(CONFIG_MOB_STOP_DISTANCE, 0.35);
        if (distance <= stopDistance) {
            entity.setVelocity(new Vector(0, 0, 0));
            return;
        }

        double speed = plugin.getConfig().getDouble(CONFIG_MOB_FOLLOW_SPEED, 0.32);
        Vector velocity = delta.normalize().multiply(Math.min(speed, distance));
        entity.setVelocity(velocity);
    }
}
