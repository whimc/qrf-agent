package edu.whimc.overworld_agent.utils;

import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Allowed expert-agent mob types: {@link EntityType#PLAYER} plus a fixed whitelist (see
 * {@link #ALLOWED_MOB_IDS}). Types are resolved with {@link EntityType#valueOf(String)} so builds against an
 * older {@code paper-api} still compile; at runtime, constants missing on that server version are skipped.
 */
public final class AgentEntityTypes {

    /**
     * Lowercase {@link EntityType} names in menu / tab order. Resolved on first use;
     * entries not present on the runtime server are omitted.
     */
    public static final String[] ALLOWED_MOB_IDS = {
            "AXOLOTL",
            "OCELOT",
            "TURTLE",
            "SHEEP",
            "STRIDER",
            "SNIFFER",
            "NAUTILUS",
            "HAPPY_GHAST",
            "BEE",
            "PARROT",
    };

    private static List<EntityType> cachedAllowedMobs;

    private AgentEntityTypes() {}

    private static List<EntityType> allowedMobs() {
        if (cachedAllowedMobs == null) {
            synchronized (AgentEntityTypes.class) {
                if (cachedAllowedMobs == null) {
                    List<EntityType> list = new ArrayList<>();
                    for (String id : ALLOWED_MOB_IDS) {
                        try {
                            list.add(EntityType.valueOf(id));
                        } catch (IllegalArgumentException ignored) {
                            // Not present in this server's EntityType enum (older game/API).
                        }
                    }
                    cachedAllowedMobs = Collections.unmodifiableList(list);
                }
            }
        }
        return cachedAllowedMobs;
    }

    /**
     * Player first, then allowed mob types in {@link #ALLOWED_MOB_IDS} order (minus any absent at runtime).
     */
    public static List<EntityType> selectableAgentTypes() {
        List<EntityType> out = new ArrayList<>();
        out.add(EntityType.PLAYER);
        out.addAll(allowedMobs());
        return out;
    }

    /** Lowercase {@link EntityType} names for tab-complete, in {@link #ALLOWED_MOB_IDS} order (minus absent-at-runtime types). */
    public static List<String> animalNamesLowercaseSorted() {
        return allowedMobs().stream()
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    /** Whether {@code type} is a non-player mob allowed as an embodied agent (spawn + edit menu). */
    public static boolean isAllowedNonPlayerAgent(EntityType type) {
        return type != null && type != EntityType.PLAYER && allowedMobs().contains(type);
    }
}
