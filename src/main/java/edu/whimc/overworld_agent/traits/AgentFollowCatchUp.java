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
     * @return the player this agent is assigned to / following, or null
     */
    public static Player followedPlayer(NPC npc) {
        if (npc == null) {
            return null;
        }
        if (npc.hasTrait(FollowTrait.class)) {
            FollowTrait follow = npc.getTrait(FollowTrait.class);
            if (follow.isEnabled()) {
                Entity entity = follow.getFollowing();
                if (entity instanceof Player player && player.isOnline()) {
                    return player;
                }
            }
        }
        if (npc.hasTrait(edu.whimc.overworld_agent.traits.SpawnExpertTrait.class)) {
            String name = npc.getOrAddTrait(edu.whimc.overworld_agent.traits.SpawnExpertTrait.class)
                    .getAssignedPlayerName();
            if (name != null) {
                Player player = org.bukkit.Bukkit.getPlayerExact(name);
                if (player != null && player.isOnline()) {
                    return player;
                }
            }
        }
        return null;
    }

    /**
     * Spawn location for hovering mob agents: beside the owner at configured hover height.
     */
    public static Location mobSpawnLocation(OverworldAgent plugin, Player player) {
        Location beside = besidePlayer(player, besideOffset(plugin));
        if (beside == null) {
            return player.getLocation();
        }
        return withMobHoverHeight(plugin, beside, 0.6);
    }

    /** Sets Y to {@code surface + hover} for a mob agent at this X/Z. */
    public static Location withMobHoverHeight(OverworldAgent plugin, Location location, double entityHeight) {
        if (location == null || location.getWorld() == null) {
            return location;
        }
        double hover = plugin.getConfig().getDouble("agent-non-player-hover-height", 2.0);
        if (hover <= 0) {
            return location;
        }
        double surfaceY = surfaceYBelow(location.getWorld(), location, entityHeight);
        location.setY(surfaceY + hover);
        return location;
    }

    /**
     * Horizontal follow point behind the player for hovering mob agents.
     */
    public static Location mobFollowTarget(OverworldAgent plugin, Player player, double entityHeight) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        double followDistance = plugin.getConfig().getDouble("agent-mob-follow-distance", 3.5);
        double hover = plugin.getConfig().getDouble("agent-non-player-hover-height", 2.0);

        Location base = player.getLocation();
        Vector forward = base.getDirection();
        forward.setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        Location spot = base.clone().subtract(forward.multiply(followDistance));
        if (hover <= 0) {
            return spot;
        }
        return withMobHoverHeight(plugin, spot, entityHeight);
    }

    private static double surfaceYBelow(World world, Location feet, double entityHeight) {
        double clearance = Math.max(1.0, entityHeight * 0.95);
        double startY = Math.min(feet.getY() + clearance, world.getMaxHeight() - 1.0);
        Location start = new Location(world, feet.getX(), startY, feet.getZ());
        double maxLen = Math.max(2.0, startY - world.getMinHeight() + 4.0);
        org.bukkit.util.RayTraceResult hit = world.rayTraceBlocks(
                start, new Vector(0, -1, 0), maxLen, org.bukkit.FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlock() != null) {
            return hit.getHitBlock().getY() + 1.0;
        }
        return world.getHighestBlockYAt(feet) + 1.0;
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
        if (horizontal < 0.75) {
            nudgeOffPlayer(plugin, npc, player);
            return;
        }
        if (horizontal > catchUp) {
            teleportBeside(plugin, npc, player);
        }
    }

    /** Reposition mob agents that Citizens stacked on the owner's head. */
    private static void nudgeOffPlayer(OverworldAgent plugin, NPC npc, Player player) {
        if (npc == null || player == null || !npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        if (npc.getEntity().getType() == org.bukkit.entity.EntityType.PLAYER) {
            return;
        }
        Location dest = mobSpawnLocation(plugin, player);
        npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public static void teleportBeside(OverworldAgent plugin, NPC npc, Player player) {
        if (npc == null || player == null || !player.isOnline()) {
            return;
        }
        Location dest = besidePlayer(player, besideOffset(plugin));
        if (dest == null) {
            return;
        }
        if (npc.isSpawned() && npc.getEntity() != null
                && npc.getEntity().getType() != org.bukkit.entity.EntityType.PLAYER) {
            dest = withMobHoverHeight(plugin, dest, npc.getEntity().getHeight());
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
