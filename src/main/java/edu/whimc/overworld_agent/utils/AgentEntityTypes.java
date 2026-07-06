package edu.whimc.overworld_agent.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Allowed expert-agent mob types: {@link EntityType#PLAYER} plus entries from {@code agent-mob-types} in config.yml.
 * Types are resolved with {@link EntityType#valueOf(String)}; constants missing on the runtime server are skipped.
 */
public final class AgentEntityTypes {

    private static final List<String> DEFAULT_MOB_IDS = List.of(
            "AXOLOTL",
            "OCELOT",
            "TURTLE",
            "SHEEP",
            "PIG",
            "STRIDER",
            "SNIFFER",
            "NAUTILUS",
            "HAPPY_GHAST",
            "BEE",
            "PARROT"
    );

    private static List<EntityType> allowedMobs = List.of();

    private AgentEntityTypes() {}

    /** Reads {@code agent-mob-types} from the plugin config. Call on enable (and after config reload). */
    public static void load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        List<String> raw = config.getStringList("agent-mob-types");
        if (raw == null || raw.isEmpty()) {
            raw = DEFAULT_MOB_IDS;
        }
        allowedMobs = Collections.unmodifiableList(resolveMobTypes(raw, plugin.getLogger(), true));
    }

    private static List<EntityType> resolveMobTypes(List<String> raw, Logger logger, boolean allowFallback) {
        List<EntityType> list = new ArrayList<>();
        for (String id : raw) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String normalized = id.trim().toUpperCase(Locale.ROOT);
            if ("PLAYER".equals(normalized)) {
                logger.warning("agent-mob-types: PLAYER is always available and should not be listed; skipping.");
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(normalized);
                if (type.getEntityClass() == null || !type.isAlive()) {
                    logger.warning("agent-mob-types: " + id + " is not a living mob, skipping.");
                    continue;
                }
                if (!list.contains(type)) {
                    list.add(type);
                }
            } catch (IllegalArgumentException ex) {
                logger.warning("agent-mob-types: unknown EntityType '" + id + "', skipping.");
            }
        }
        if (list.isEmpty() && allowFallback) {
            logger.warning("agent-mob-types: no valid mob types configured; using built-in defaults.");
            return resolveMobTypes(DEFAULT_MOB_IDS, logger, false);
        }
        return list;
    }

    /**
     * Player first, then allowed mob types in config order (minus any absent at runtime).
     */
    public static List<EntityType> selectableAgentTypes() {
        List<EntityType> out = new ArrayList<>();
        out.add(EntityType.PLAYER);
        out.addAll(allowedMobs);
        return out;
    }

    /** Lowercase {@link EntityType} names for tab-complete, in config order. */
    public static List<String> animalNamesLowercaseSorted() {
        return allowedMobs.stream()
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    /** Whether {@code type} is a non-player mob allowed as an embodied agent (spawn + edit menu). */
    public static boolean isAllowedNonPlayerAgent(EntityType type) {
        return type != null && type != EntityType.PLAYER && allowedMobs.contains(type);
    }
}
