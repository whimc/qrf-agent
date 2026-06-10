package edu.whimc.overworld_agent.dialoguetemplate;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves portal-linked worlds (shared name prefix) and POI destinations for the guidance menu.
 */
public final class JourneyGuidanceCatalog {

    public record Destination(String jtKey, String label) {}

    private JourneyGuidanceCatalog() {}

    /**
     * Worlds that share a name prefix with {@code current} (e.g. ColderCold, ColderHot, ColderStrip).
     */
    public static List<World> linkedWorlds(World current, FileConfiguration cfg) {
        if (current == null) {
            return List.of();
        }
        String configured = cfg.getString("journey.linked-world-prefix", "");
        if (configured != null && !configured.isBlank()) {
            return worldsWithPrefix(configured.trim());
        }
        String prefix = detectLinkedPrefix(current.getName(), cfg.getInt("journey.linked-world-min-siblings", 2));
        if (prefix == null || prefix.isBlank()) {
            return List.of(current);
        }
        List<World> linked = worldsWithPrefix(prefix);
        return linked.isEmpty() ? List.of(current) : linked;
    }

    public static String linkedPrefixFor(World current, FileConfiguration cfg) {
        if (current == null) {
            return "";
        }
        String configured = cfg.getString("journey.linked-world-prefix", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String detected = detectLinkedPrefix(current.getName(), cfg.getInt("journey.linked-world-min-siblings", 2));
        return detected == null ? current.getName() : detected;
    }

    public static Set<Integer> journeyDomains(Collection<World> worlds) {
        Set<Integer> out = new LinkedHashSet<>();
        if (worlds == null) {
            return out;
        }
        for (World world : worlds) {
            Integer domain = journeyDomainForWorldSafe(world);
            if (domain != null) {
                out.add(domain);
            }
        }
        return out;
    }

    public static List<Destination> poiRegionsFromWorldGuard(Collection<World> worlds, FileConfiguration cfg) {
        if (worlds == null || worlds.isEmpty() || !cfg.getBoolean("journey.include-poi-regions", true)) {
            return List.of();
        }
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return List.of();
        }
        String poiPrefix = cfg.getString("journey.poi-region-prefix", "poi-");
        if (poiPrefix == null) {
            poiPrefix = "poi-";
        }
        String prefixLower = poiPrefix.toLowerCase(Locale.ROOT);
        Map<String, Destination> byKey = new LinkedHashMap<>();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        for (World world : worlds) {
            if (world == null) {
                continue;
            }
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) {
                continue;
            }
            for (Map.Entry<String, ProtectedRegion> entry : manager.getRegions().entrySet()) {
                String regionId = entry.getKey();
                if (regionId == null || !regionId.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                    continue;
                }
                String key = regionId.toLowerCase(Locale.ROOT);
                byKey.putIfAbsent(key, new Destination(key, formatPoiLabel(regionId, poiPrefix)));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<Destination> mergeDestinations(Collection<Destination>... groups) {
        Map<String, Destination> byKey = new LinkedHashMap<>();
        if (groups == null) {
            return List.of();
        }
        for (Collection<Destination> group : groups) {
            if (group == null) {
                continue;
            }
            for (Destination d : group) {
                if (d != null && d.jtKey() != null && !d.jtKey().isBlank()) {
                    byKey.putIfAbsent(d.jtKey().toLowerCase(Locale.ROOT), d);
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static String formatPoiLabel(String regionId, String poiPrefix) {
        if (regionId == null || regionId.isBlank()) {
            return regionId;
        }
        String body = regionId;
        if (poiPrefix != null && !poiPrefix.isBlank()
                && regionId.toLowerCase(Locale.ROOT).startsWith(poiPrefix.toLowerCase(Locale.ROOT))) {
            body = regionId.substring(poiPrefix.length());
        }
        body = body.replace('-', ' ').replace('_', ' ').trim();
        if (body.isBlank()) {
            return regionId;
        }
        StringBuilder out = new StringBuilder();
        for (String word : body.split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private static List<World> worldsWithPrefix(String prefix) {
        List<World> out = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (world != null && world.getName().startsWith(prefix)) {
                out.add(world);
            }
        }
        return out;
    }

    /**
     * Longest CamelCase prefix shared by at least {@code minSiblings} worlds (e.g. {@code Colder} from ColderStrip).
     */
    static String detectLinkedPrefix(String worldName, int minSiblings) {
        if (worldName == null || worldName.length() < 2 || minSiblings < 2) {
            return null;
        }
        String best = null;
        for (int i = 1; i < worldName.length(); i++) {
            if (!Character.isUpperCase(worldName.charAt(i))) {
                continue;
            }
            String prefix = worldName.substring(0, i);
            int siblings = 0;
            for (World world : Bukkit.getWorlds()) {
                String name = world.getName();
                if (name.startsWith(prefix) && name.length() > prefix.length()) {
                    siblings++;
                }
            }
            if (siblings >= minSiblings) {
                best = prefix;
            }
        }
        return best;
    }

    private static Integer journeyDomainForWorldSafe(World world) {
        if (world == null) {
            return null;
        }
        try {
            Class<?> providerCl = Class.forName("net.whimxiqal.journey.bukkit.JourneyBukkitApiProvider");
            Method get = providerCl.getMethod("get");
            Object api = get.invoke(null);
            if (api == null) {
                return null;
            }
            Method toDomain = api.getClass().getMethod("toDomain", World.class);
            Object id = toDomain.invoke(api, world);
            if (id instanceof Integer) {
                return (Integer) id;
            }
            if (id instanceof Number) {
                return ((Number) id).intValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
