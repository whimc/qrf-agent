package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Teleports an agent beside its followed player when they are too far behind, and nudges them off
 * the player's position when Citizens {@link FollowTrait} or stuck recovery snaps them on top.
 */
public final class AgentFollowCatchUp {

    private static final String CFG_CATCH_UP_DISTANCE = "agent-follow-catch-up-distance";
    private static final String CFG_CATCH_UP_OFFSET = "agent-follow-catch-up-offset";
    private static final String CFG_RECOVERY_MAX_VERTICAL = "agent-follow-recovery-max-vertical";

    private AgentFollowCatchUp() {}

    public static int recoveryIntervalTicks(OverworldAgent plugin) {
        double seconds = plugin.getConfig().getDouble("agent-follow-recovery-interval-seconds", 10.0);
        return Math.max(20, (int) Math.round(seconds * 20.0));
    }

    public static double catchUpDistance(OverworldAgent plugin) {
        return plugin.getConfig().getDouble(CFG_CATCH_UP_DISTANCE, 16.0);
    }

    public static double besideOffset(OverworldAgent plugin) {
        // Default ~2 blocks so the owner can see the agent after catch-up / teleport.
        return plugin.getConfig().getDouble(CFG_CATCH_UP_OFFSET, 2.0);
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
     * Spawn location for hovering mob agents: same settle point as {@link #mobFollowTarget} (front-side when idle).
     */
    public static Location mobSpawnLocation(OverworldAgent plugin, Player player) {
        Location spot = mobFollowTarget(plugin, player, 0.6);
        return spot != null ? spot : player.getLocation();
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
     * Horizontal follow point for hovering mob agents. When the owner is idle, the mob settles to
     * the side (clickable); while moving, it trails behind. In spectator / flight, tracks the
     * owner's Y instead of hovering above the ground.
     */
    public static Location mobFollowTarget(OverworldAgent plugin, Player player, double entityHeight) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        double followDistance = plugin.getConfig().getDouble("agent-mob-follow-distance", 3.5);
        double sideOffset = plugin.getConfig().getDouble("agent-mob-idle-side-offset", 1.5);
        double idleSpeed = plugin.getConfig().getDouble("agent-mob-idle-settle-speed", 0.08);
        double hover = plugin.getConfig().getDouble("agent-non-player-hover-height", 2.0);

        Location base = player.getLocation();
        Vector forward = base.getDirection();
        forward.setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        Location spot;
        if (isPlayerHorizontallyIdle(player, idleSpeed)) {
            // Side of the player (stable for clicking) — not continuously chasing look-direction "front".
            spot = base.clone().add(right.clone().multiply(Math.max(sideOffset, 1.5)));
        } else {
            spot = base.clone().subtract(forward.multiply(followDistance));
        }
        if (ownerNeedsAirFollow(player)) {
            spot.setY(base.getY());
            return spot;
        }
        if (hover <= 0) {
            return spot;
        }
        return withMobHoverHeight(plugin, spot, entityHeight);
    }

    /**
     * True when the owner is not on the ground pathing graph (spectator / creative flight) so
     * agents should match their air position instead of pathfinding.
     */
    public static boolean ownerNeedsAirFollow(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        return player.isFlying() && !player.isOnGround();
    }

    /**
     * Soft-follow a player-shaped agent beside the owner while they are in spectator / flight.
     * Ground pathfinding cannot track noclip players; NPCs also cannot use true spectator mode.
     *
     * @return true if air-follow handled this tick (caller should skip normal land follow)
     */
    public static boolean tickPlayerAirFollow(OverworldAgent plugin, NPC npc, Entity entity, Player owner) {
        if (plugin == null || npc == null || entity == null || owner == null || !owner.isOnline()) {
            return false;
        }
        if (entity.getType() != EntityType.PLAYER) {
            return false;
        }
        if (!owner.getWorld().equals(entity.getWorld())) {
            return false;
        }
        if (!ownerNeedsAirFollow(owner)) {
            if (!entity.hasGravity()) {
                entity.setGravity(true);
            }
            return false;
        }

        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        entity.setGravity(false);

        Location target = besidePlayer(owner, besideOffset(plugin), true);
        if (target == null) {
            return true;
        }
        Location current = entity.getLocation();
        Vector delta = target.toVector().subtract(current.toVector());
        double distance = delta.length();
        if (distance <= 0.5) {
            entity.setVelocity(new Vector(0, 0, 0));
            if (distance > 0.15) {
                npc.teleport(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            return true;
        }
        double speed = plugin.getConfig().getDouble("agent-player-air-follow-speed", 0.45);
        if (distance > 12.0) {
            npc.teleport(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            entity.setVelocity(new Vector(0, 0, 0));
            return true;
        }
        entity.setVelocity(delta.normalize().multiply(Math.min(speed, distance)));
        return true;
    }

    /**
     * If the owned agent exists but is despawned, respawn beside the player.
     * @return true if a despawned agent was brought back
     */
    public static boolean respawnOwnedIfDespawned(OverworldAgent plugin, Player player) {
        if (plugin == null || player == null) {
            return false;
        }
        NPC npc = plugin.getAgents().get(player.getName());
        if (npc == null || npc.isSpawned()) {
            return false;
        }
        respawnBesideOwner(plugin, npc, player);
        return true;
    }

    /** Human-readable location for status messages. */
    public static String describeAgentLocation(NPC npc) {
        if (npc == null) {
            return "unknown";
        }
        if (!npc.isSpawned() || npc.getEntity() == null) {
            Location stored = npc.getStoredLocation();
            if (stored == null || stored.getWorld() == null) {
                return "despawned (no stored location)";
            }
            return String.format("despawned near %s (%d, %d, %d)",
                    stored.getWorld().getName(),
                    stored.getBlockX(), stored.getBlockY(), stored.getBlockZ());
        }
        Location loc = npc.getEntity().getLocation();
        return String.format("%s (%d, %d, %d)",
                loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** True when the owner is not moving horizontally (standing still, looking around). */
    private static boolean isPlayerHorizontallyIdle(Player player, double maxHorizontalSpeed) {
        if (player.getVehicle() != null) {
            return false;
        }
        if (player.isGliding() || player.isRiptiding()) {
            return false;
        }
        // Spectators always "fly"; only treat them as moving when velocity says so.
        if (player.getGameMode() != GameMode.SPECTATOR && player.isFlying()) {
            return false;
        }
        Vector velocity = player.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        return horizontal <= maxHorizontalSpeed;
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
     * Teleports a <em>spawned</em> agent to the owner when it is in another world or lost (void / too
     * far). Despawned agents are intentionally left alone.
     */
    public static void recoverIfNeeded(OverworldAgent plugin, NPC npc, Player player) {
        if (plugin == null || npc == null || player == null || !player.isOnline()) {
            return;
        }
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        Location agentLoc = npc.getEntity().getLocation();
        Location playerLoc = player.getLocation();
        if (!agentLoc.getWorld().equals(playerLoc.getWorld()) || isLostFromOwner(plugin, agentLoc, playerLoc)) {
            teleportBeside(plugin, npc, player);
            AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
        }
    }

    private static boolean isLostFromOwner(OverworldAgent plugin, Location agentLoc, Location playerLoc) {
        if (horizontalDistance(agentLoc, playerLoc) > catchUpDistance(plugin)) {
            return true;
        }
        double maxVertical = plugin.getConfig().getDouble(CFG_RECOVERY_MAX_VERTICAL, 32.0);
        if (Math.abs(agentLoc.getY() - playerLoc.getY()) > maxVertical) {
            return true;
        }
        World world = agentLoc.getWorld();
        return agentLoc.getY() < world.getMinHeight() + 4;
    }

    /**
     * Teleport catch-up when horizontal distance exceeds the configured threshold, or when the agent
     * is stacked on the player (same world only — use {@link #recoverIfNeeded} for cross-world).
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
            AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
        }
    }

    /**
     * Always place the agent beside the owner and restore follow — used after player teleports /
     * world changes so they are never stacked on the player.
     */
    public static void bringBesideOwner(OverworldAgent plugin, NPC npc, Player player) {
        if (plugin == null || npc == null || player == null || !player.isOnline()) {
            return;
        }
        if (!npc.isSpawned()) {
            respawnBesideOwner(plugin, npc, player);
            return;
        }
        teleportBeside(plugin, npc, player);
        AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
    }

    /** Despawn (if needed) and spawn beside the owner — used on login after intentional quit despawn. */
    public static void respawnBesideOwner(OverworldAgent plugin, NPC npc, Player player) {
        Location dest = destinationBesideOwner(plugin, npc, player);
        if (dest == null) {
            return;
        }
        if (npc.isSpawned()) {
            npc.despawn();
        }
        npc.spawn(dest);
        AgentFollowTuning.applyForCurrentEntity(plugin, npc);
        AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
    }

    private static Location destinationBesideOwner(OverworldAgent plugin, NPC npc, Player player) {
        if (npc != null && npc.isSpawned() && npc.getEntity() != null
                && npc.getEntity().getType() != EntityType.PLAYER) {
            return mobSpawnLocation(plugin, player);
        }
        return besidePlayer(player, besideOffset(plugin));
    }

    /** Reposition agents that Citizens stacked on the owner's position. */
    private static void nudgeOffPlayer(OverworldAgent plugin, NPC npc, Player player) {
        if (npc == null || player == null || !npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        if (npc.getEntity().getType() == EntityType.PLAYER) {
            teleportBeside(plugin, npc, player);
            AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
            return;
        }
        Location dest = mobSpawnLocation(plugin, player);
        npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public static void teleportBeside(OverworldAgent plugin, NPC npc, Player player) {
        if (npc == null || player == null || !player.isOnline()) {
            return;
        }
        // Always match the owner's feet Y (never world-highest-block — that snaps onto roofs/floors).
        Location dest = besidePlayer(player, besideOffset(plugin), true);
        if (dest == null) {
            return;
        }
        if (npc.isSpawned() && npc.getEntity() != null
                && npc.getEntity().getType() != EntityType.PLAYER
                && !ownerNeedsAirFollow(player)) {
            dest = withMobHoverHeight(plugin, dest, npc.getEntity().getHeight());
        }
        if (!npc.isSpawned()) {
            npc.spawn(dest);
            return;
        }
        npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        AgentFollowTuning.applyForCurrentEntity(plugin, npc);
    }

    /** Spawn / respawn location: beside the player at the player's feet height. */
    public static Location besidePlayer(Player player, double offset) {
        return besidePlayer(player, offset, true);
    }

    /**
     * Horizontal offset beside the player. When {@code matchPlayerY} is true (default for catch-up),
     * uses the player's feet Y so agents stay on the same level instead of snapping to roofs or
     * cave floors via {@code getHighestBlockYAt}.
     */
    public static Location besidePlayer(Player player, double offset, boolean matchPlayerY) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        double side = Math.max(1.0, offset);
        Location base = player.getLocation();
        Vector forward = base.getDirection();
        forward.setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        // Perpendicular "to the right" of where the player is facing.
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize().multiply(side);
        Location dest = base.clone().add(right);
        dest.setPitch(base.getPitch());
        dest.setYaw(base.getYaw());
        // Same height as the player (catch-up / spawn). Optional +1 via config if needed later.
        dest.setY(base.getY());
        if (!matchPlayerY) {
            // Legacy ground snap — only when explicitly requested; still clamped near player Y.
            World world = base.getWorld();
            int groundY = world.getHighestBlockYAt(dest);
            // Prefer highest block only if it is within a few blocks of the player (same "floor").
            if (Math.abs(groundY + 1.0 - base.getY()) <= 3.0) {
                dest.setY(groundY + 1.0);
            }
        }
        return dest;
    }

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
