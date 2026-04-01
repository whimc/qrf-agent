package edu.whimc.overworld_agent.utils;

import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Allowed expert-agent mob types: {@link EntityType#PLAYER} and living {@link Animals} (same rules as
 * {@code /agents spawn}).
 */
public final class AgentEntityTypes {

    private AgentEntityTypes() {}

    /**
     * Player first, then animal types sorted by enum name (matches spawn tab order for animals).
     */
    public static List<EntityType> selectableAgentTypes() {
        List<EntityType> animals = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            if (t == EntityType.UNKNOWN || !t.isAlive()) {
                continue;
            }
            Class<?> c = t.getEntityClass();
            if (c != null && Animals.class.isAssignableFrom(c)) {
                animals.add(t);
            }
        }
        animals.sort(Comparator.comparing(EntityType::name));
        List<EntityType> out = new ArrayList<>();
        out.add(EntityType.PLAYER);
        out.addAll(animals);
        return out;
    }

    public static List<String> animalNamesLowercaseSorted() {
        return selectableAgentTypes().stream()
                .filter(t -> t != EntityType.PLAYER)
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }
}
