package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
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
     * Horizontal follow point for hovering mob agents. When the owner is idle, the mob settles in
     * front of their view (slightly to the side) so it stays clickable; while moving, it trails behind.
     * In water, the target tracks the owner's Y (swim alongside) instead of the seabed.
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
            spot = base.clone()
                    .add(forward.clone().multiply(followDistance))
                    .add(right.clone().multiply(sideOffset));
        } else {
            spot = base.clone().subtract(forward.multiply(followDistance));
        }
        if (isAquatic(player)) {
            spot.setY(base.getY());
            return spot;
        }
        if (hover <= 0) {
            return spot;
        }
        return withMobHoverHeight(plugin, spot, entityHeight);
    }

    /** True when the player is in/under water (or swimming). */
    public static boolean isAquatic(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (player.isInWater() || player.isSwimming()) {
            return true;
        }
        try {
            if (player.isUnderWater()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // Older API
        }
        return isAquatic(player.getLocation()) || isAquatic(player.getEyeLocation());
    }

    public static boolean isAquatic(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        org.bukkit.block.Block block = location.getBlock();
        if (block.isLiquid()) {
            return true;
        }
        org.bukkit.Material type = block.getType();
        return type == org.bukkit.Material.WATER
                || type == org.bukkit.Material.BUBBLE_COLUMN
                || type.name().endsWith("_WATER");
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
        if (player.isGliding() || player.isRiptiding() || player.isFlying()) {
            return false;
        }
        if (player.getVehicle() != null) {
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
            boolean aquatic = isAquatic(player) || isAquatic(npc.getEntity().getLocation());
            if (!aquatic) {
                AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
            } else if (npc.getNavigator().isNavigating()) {
                npc.getNavigator().cancelNavigation();
            }
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
        // Seabed / void: agent sank far below the owner (classic ocean hover bug).
        if (playerLoc.getY() - agentLoc.getY() > 8.0) {
            return true;
        }
        World world = agentLoc.getWorld();
        return agentLoc.getY() < world.getMinHeight() + 4;
    }

    /**
     * Keep agents with the owner in water. Player NPCs cannot pathfind-swim reliably — cancel
     * navigation and match the owner's height every tick (via {@link AgentPermanentFlyingTrait}).
     */
    public static void syncAquaticIfNeeded(OverworldAgent plugin, NPC npc, Player player) {
        if (plugin == null || npc == null || player == null || !player.isOnline()) {
            return;
        }
        if (!npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        Location agentLoc = npc.getEntity().getLocation();
        Location playerLoc = player.getLocation();
        if (!agentLoc.getWorld().equals(playerLoc.getWorld())) {
            return;
        }
        boolean aquatic = isAquatic(player) || isAquatic(agentLoc);
        if (!aquatic) {
            return;
        }
        // Stop Citizens ground pathfinding — it walks/sinks player agents to the seabed.
        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        double horizontal = horizontalDistance(agentLoc, playerLoc);
        double vertical = Math.abs(agentLoc.getY() - playerLoc.getY());
        // Snap when clearly below/away; fine motion is handled every tick for player agents.
        if (horizontal > 4.0 || vertical > 1.5 || playerLoc.getY() - agentLoc.getY() > 1.0) {
            Location dest = swimBesidePlayer(player, besideOffset(plugin));
            if (dest == null) {
                return;
            }
            npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            // Do not re-enable FollowTrait pathfinding here — it undoes the snap.
        }
    }

    /**
     * Swim/follow target beside the owner at the owner's Y (never seabed / highest-block).
     */
    public static Location swimBesidePlayer(Player player, double offset) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        Location base = player.getLocation();
        Vector forward = base.getDirection();
        forward.setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize().multiply(offset);
        Location dest = base.clone().add(right);
        dest.setY(base.getY());
        dest.setPitch(base.getPitch());
        dest.setYaw(base.getYaw());
        return dest;
    }

    /**
     * Glide a player-shaped agent toward the owner while either is in water.
     * @return true if aquatic handling ran (caller should skip land follow)
     */
    public static boolean tickPlayerAquaticSwim(OverworldAgent plugin, NPC npc, Entity entity, Player owner) {
        if (plugin == null || npc == null || entity == null || owner == null || !owner.isOnline()) {
            return false;
        }
        if (entity.getType() != EntityType.PLAYER) {
            return false;
        }
        if (!owner.getWorld().equals(entity.getWorld())) {
            return false;
        }
        boolean aquatic = isAquatic(owner) || isAquatic(entity.getLocation());
        if (!aquatic) {
            // Leaving water: restore normal walking gravity if we disabled it.
            if (!entity.hasGravity()) {
                entity.setGravity(true);
            }
            return false;
        }

        if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
        entity.setGravity(false);

        Location target = swimBesidePlayer(owner, besideOffset(plugin));
        if (target == null) {
            return true;
        }
        Location current = entity.getLocation();
        Vector delta = target.toVector().subtract(current.toVector());
        double distance = delta.length();
        if (distance <= 0.4) {
            entity.setVelocity(new Vector(0, 0, 0));
            // Hard-correct lingering depth error (teleport-back-but-too-deep).
            if (Math.abs(current.getY() - target.getY()) > 0.35) {
                current.setY(target.getY());
                npc.teleport(current, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            return true;
        }
        double speed = plugin.getConfig().getDouble("agent-player-swim-speed", 0.35);
        entity.setVelocity(delta.normalize().multiply(Math.min(speed, distance)));
        return true;
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
        }
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
        boolean aquatic = isAquatic(player)
                || (npc.isSpawned() && npc.getEntity() != null && isAquatic(npc.getEntity().getLocation()));
        Location dest = aquatic
                ? swimBesidePlayer(player, besideOffset(plugin))
                : besidePlayer(player, besideOffset(plugin));
        if (dest == null) {
            return;
        }
        if (!aquatic && npc.isSpawned() && npc.getEntity() != null
                && npc.getEntity().getType() != org.bukkit.entity.EntityType.PLAYER) {
            dest = withMobHoverHeight(plugin, dest, npc.getEntity().getHeight());
        }
        if (!npc.isSpawned()) {
            npc.spawn(dest);
            return;
        }
        npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!aquatic) {
            AgentFollowTuning.applyForCurrentEntity(plugin, npc);
        } else if (npc.getNavigator().isNavigating()) {
            npc.getNavigator().cancelNavigation();
        }
    }

    /** Spawn / respawn location: beside the player; matches player Y in water instead of seabed. */
    public static Location besidePlayer(Player player, double offset) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        if (isAquatic(player)) {
            return swimBesidePlayer(player, offset);
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
        // Prefer the player's Y when the destination column is water (ocean surface / reefs).
        if (isAquatic(dest) || isAquatic(dest.clone().add(0, 1, 0))) {
            dest.setY(base.getY());
            return dest;
        }
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
