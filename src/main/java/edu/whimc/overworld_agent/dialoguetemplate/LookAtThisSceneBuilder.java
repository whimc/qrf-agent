package edu.whimc.overworld_agent.dialoguetemplate;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import edu.whimc.overworld_agent.OverworldAgent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds a compact "scene" summary for the Look at this! dialogue option (Journey + WorldGuard + local blocks).
 */
public final class LookAtThisSceneBuilder {

    public record NearbyDestination(String jtKey, String label, Location location) {}

    private LookAtThisSceneBuilder() {}

    public static String build(OverworldAgent plugin, Player player, List<NearbyDestination> nearbyWaypoints) {
        if (player == null || !player.isOnline()) {
            return "## Scene context\n(unavailable)\n";
        }
        FileConfiguration cfg = plugin.getConfig();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        StringBuilder out = new StringBuilder();
        out.append("## Scene context (live; trust this over guessing)\n");
        out.append("- World: ").append(world != null ? world.getName() : "?").append('\n');
        out.append("- Position: ")
                .append(loc.getBlockX()).append(", ")
                .append(loc.getBlockY()).append(", ")
                .append(loc.getBlockZ()).append('\n');
        out.append("- Biome: ").append(biomeName(loc)).append('\n');
        out.append("- Standing on: ").append(blockBelow(loc)).append('\n');
        out.append("- Light (block/sky): ")
                .append(loc.getBlock().getLightFromBlocks())
                .append('/')
                .append(loc.getBlock().getLightFromSky())
                .append('\n');

        appendWorldGuardRegions(out, player);
        appendNearbyWaypoints(out, player, nearbyWaypoints, cfg);
        appendNearbyBlocks(out, loc, cfg);
        out.append('\n');
        return out.toString();
    }

    private static String biomeName(Location loc) {
        try {
            Biome biome = loc.getBlock().getBiome();
            return biome == null ? "?" : biome.getKey().getKey();
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String blockBelow(Location loc) {
        Block below = loc.clone().subtract(0, 0.2, 0).getBlock();
        Material type = below.getType();
        return type == null ? "?" : type.name().toLowerCase(Locale.ROOT);
    }

    private static void appendWorldGuardRegions(StringBuilder out, Player player) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            out.append("- WorldGuard regions: (plugin not present)\n");
            return;
        }
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));
            List<String> ids = new ArrayList<>();
            for (ProtectedRegion region : set) {
                if (region != null && region.getId() != null && !"__global__".equalsIgnoreCase(region.getId())) {
                    ids.add(region.getId());
                }
            }
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            if (ids.isEmpty()) {
                out.append("- WorldGuard regions at feet: (none)\n");
            } else {
                out.append("- WorldGuard regions at feet: ").append(String.join(", ", ids)).append('\n');
            }
        } catch (Throwable ex) {
            out.append("- WorldGuard regions: (error reading)\n");
        }
    }

    private static void appendNearbyWaypoints(
            StringBuilder out,
            Player player,
            List<NearbyDestination> nearbyWaypoints,
            FileConfiguration cfg
    ) {
        if (nearbyWaypoints == null || nearbyWaypoints.isEmpty()) {
            out.append("- Nearby Journey/POI/NPC destinations: (none found nearby)\n");
            return;
        }
        Location origin = player.getLocation();
        int max = Math.max(1, cfg.getInt("template-gui.look-at-this.max-waypoints", 5));
        List<NearbyDestination> ranked = new ArrayList<>();
        for (NearbyDestination c : nearbyWaypoints) {
            if (c == null || c.location() == null || c.location().getWorld() == null) {
                continue;
            }
            if (!c.location().getWorld().equals(origin.getWorld())) {
                continue;
            }
            ranked.add(c);
        }
        ranked.sort(Comparator.comparingDouble(c -> c.location().distanceSquared(origin)));
        int take = Math.min(max, ranked.size());
        if (take == 0) {
            out.append("- Nearby Journey/POI/NPC destinations: (none with known locations)\n");
            return;
        }
        out.append("- Nearby Journey/POI/NPC destinations:\n");
        for (int i = 0; i < take; i++) {
            NearbyDestination c = ranked.get(i);
            double dist = Math.sqrt(c.location().distanceSquared(origin));
            out.append("  - ")
                    .append(c.label())
                    .append(" (")
                    .append(c.jtKey())
                    .append(") ~")
                    .append(String.format(Locale.ROOT, "%.0f", dist))
                    .append("m\n");
        }
    }

    private static void appendNearbyBlocks(StringBuilder out, Location loc, FileConfiguration cfg) {
        int radius = Math.max(1, Math.min(4, cfg.getInt("template-gui.look-at-this.block-sample-radius", 2)));
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), by - 1); y <= Math.min(world.getMaxHeight() - 1, by + 2); y++) {
                for (int z = bz - radius; z <= bz + radius; z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type.isAir() || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                        continue;
                    }
                    String key = type.name().toLowerCase(Locale.ROOT);
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }
        if (counts.isEmpty()) {
            out.append("- Nearby notable blocks: (none)\n");
            return;
        }
        List<java.util.Map.Entry<String, Integer>> top = new ArrayList<>(counts.entrySet());
        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int limit = Math.min(8, top.size());
        out.append("- Nearby notable blocks: ");
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(top.get(i).getKey()).append('×').append(top.get(i).getValue());
        }
        out.append('\n');
    }
}
