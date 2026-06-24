package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Teleports an agent beside its followed player when they are too far behind, and nudges them off
 * the player's position when Citizens {@link FollowTrait} or stuck recovery snaps them on top.
 */
public final class AgentFollowCatchUp {

    private static final String CFG_CATCH_UP_DISTANCE = "agent-follow-catch-up-distance";
    private static final String CFG_CATCH_UP_OFFSET = "agent-follow-catch-up-offset";

    private AgentFollowCatchUp() {}

    public static double catchUpDistance(OverworldAgent plugin) {
        return plugin.getConfig().getDouble(CFG_CATCH_UP_DISTANCE, 16.0);
    }

    public static double besideOffset(OverworldAgent plugin) {
        return plugin.getConfig().getDouble(CFG_CATCH_UP_OFFSET, 1.5);
    }

    /**
     * @return the player this agent is following, or null
     */
    public static Player followedPlayer(NPC npc) {
        if (npc == null || !npc.hasTrait(FollowTrait.class)) {
            return null;
        }
        FollowTrait follow = npc.getTrait(FollowTrait.class);
        if (!follow.isEnabled()) {
            return null;
        }
        Entity entity = follow.getFollowing();
        if (entity instanceof Player player && player.isOnline()) {
            return player;
        }
        return null;
    }

    /**
     * Teleport catch-up when horizontal distance exceeds the configured threshold, or when the agent
     * is stacked on the player (Citizens cross-world / stuck recovery).
     */
    public static void applyIfNeeded(OverworldAgent plugin, NPC npc, Player player) {
        if (plugin == null || npc == null || player == null || !npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        Location agentLoc = npc.getEntity().getLocation();
        Location playerLoc = player.getLocation();
        if (!agentLoc.getWorld().equals(playerLoc.getWorld())) {
            return;
        }

        double catchUp = catchUpDistance(plugin);
        double horizontal = horizontalDistance(agentLoc, playerLoc);
        if (horizontal > catchUp) {
            teleportBeside(plugin, npc, player);
        }
    }

    public static void teleportBeside(OverworldAgent plugin, NPC npc, Player player) {
        if (npc == null || player == null || !player.isOnline()) {
            return;
        }
        Location dest = besidePlayer(player, besideOffset(plugin));
        if (dest == null) {
            return;
        }
        if (!npc.isSpawned()) {
            npc.spawn(dest);
            return;
        }
        npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        AgentFollowTuning.applyForCurrentEntity(plugin, npc);
    }

    /** Spawn / respawn location: beside the player, same world, feet on ground when possible. */
    public static Location besidePlayer(Player player, double offset) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        Location base = player.getLocation();
        World world = base.getWorld();
        Vector forward = base.getDirection();
        forward.setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        // Perpendicular "to the right" of where the player is facing.
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize().multiply(offset);
        Location dest = base.clone().add(right);
        dest.setPitch(base.getPitch());
        dest.setYaw(base.getYaw());
        int groundY = world.getHighestBlockYAt(dest);
        dest.setY(groundY + 1.0);
        return dest;
    }

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
