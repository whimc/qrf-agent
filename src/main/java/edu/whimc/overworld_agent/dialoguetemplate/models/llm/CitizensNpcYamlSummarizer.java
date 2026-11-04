package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns Citizens saves.yml-style NPC storage into compact readable text for RAG.
 * Skips skins, signatures, UUIDs, inventory blobs, and other non-dialogue noise.
 */
public final class CitizensNpcYamlSummarizer {

    private CitizensNpcYamlSummarizer() {}

    public static boolean looksLikeCitizensSaves(FileConfiguration config) {
        ConfigurationSection npc = config.getConfigurationSection("npc");
        return npc != null && !npc.getKeys(false).isEmpty();
    }

    public static String summarize(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!looksLikeCitizensSaves(config)) {
            return null;
        }
        return summarize(config, file.getName());
    }

    public static String summarize(FileConfiguration config, String sourceName) {
        ConfigurationSection npcs = config.getConfigurationSection("npc");
        if (npcs == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append("Citizens NPC directory (summarized from ").append(sourceName).append(")\n");
        out.append("Use for world lore, NPC dialogue, roles, and locations. Ignore missing skin/UUID data.\n\n");

        List<String> ids = new ArrayList<>(npcs.getKeys(false));
        ids.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        int included = 0;
        for (String id : ids) {
            ConfigurationSection npc = npcs.getConfigurationSection(id);
            if (npc == null) {
                continue;
            }
            String block = summarizeNpc(id, npc);
            if (block.isBlank()) {
                continue;
            }
            if (included > 0) {
                out.append('\n');
            }
            out.append(block);
            included++;
        }

        if (included == 0) {
            return "Citizens NPC file " + sourceName + " contained no extractable NPC dialogue or names.\n";
        }
        out.append("\n(").append(included).append(" NPCs summarized)\n");
        return out.toString();
    }

    private static String summarizeNpc(String id, ConfigurationSection npc) {
        String name = trimToNull(npc.getString("name"));
        ConfigurationSection traits = npc.getConfigurationSection("traits");
        if (traits == null) {
            traits = npc;
        }

        List<String> dialogue = extractDialogue(traits);
        List<String> holograms = extractHologramLines(traits);
        String type = trimToNull(traits.getString("type"));
        String location = formatLocation(traits.getConfigurationSection("location"));

        boolean hasContent = name != null
                || !dialogue.isEmpty()
                || !holograms.isEmpty()
                || location != null;
        if (!hasContent) {
            return "";
        }

        StringBuilder block = new StringBuilder();
        block.append("NPC #").append(id);
        if (name != null) {
            block.append(": ").append(stripColor(name));
        }
        block.append('\n');

        if (type != null && !"PLAYER".equalsIgnoreCase(type)) {
            block.append("  type: ").append(type).append('\n');
        }
        if (location != null) {
            block.append("  location: ").append(location).append('\n');
        }
        if (!holograms.isEmpty()) {
            block.append("  labels: ");
            block.append(String.join(" | ", holograms.stream().map(CitizensNpcYamlSummarizer::stripColor).toList()));
            block.append('\n');
        }
        if (!dialogue.isEmpty()) {
            block.append("  dialogue:\n");
            for (String line : dialogue) {
                block.append("    - ").append(stripColor(line)).append('\n');
            }
        }
        return block.toString();
    }

    private static List<String> extractDialogue(ConfigurationSection traits) {
        List<String> lines = new ArrayList<>();
        ConfigurationSection text = traits.getConfigurationSection("text");
        if (text == null) {
            return lines;
        }
        List<String> stringList = text.getStringList("text");
        if (!stringList.isEmpty()) {
            for (String line : stringList) {
                String s = trimToNull(line);
                if (s != null) {
                    lines.add(s);
                }
            }
            return lines;
        }
        appendTextValues(text.get("text"), lines);
        return lines;
    }

    private static List<String> extractHologramLines(ConfigurationSection traits) {
        List<String> lines = new ArrayList<>();
        ConfigurationSection hologram = traits.getConfigurationSection("hologramtrait");
        if (hologram == null) {
            return lines;
        }
        List<Map<?, ?>> mapList = hologram.getMapList("lines");
        if (!mapList.isEmpty()) {
            for (Map<?, ?> map : mapList) {
                Object text = map.get("text");
                if (text != null) {
                    String s = trimToNull(String.valueOf(text));
                    if (s != null) {
                        lines.add(s);
                    }
                }
            }
            return lines;
        }
        List<?> rawLines = hologram.getList("lines");
        if (rawLines == null) {
            return lines;
        }
        for (Object entry : rawLines) {
            if (entry instanceof Map<?, ?> map) {
                Object text = map.get("text");
                if (text != null) {
                    String s = trimToNull(String.valueOf(text));
                    if (s != null) {
                        lines.add(s);
                    }
                }
            } else if (entry instanceof ConfigurationSection section) {
                String s = trimToNull(section.getString("text"));
                if (s != null) {
                    lines.add(s);
                }
            }
        }
        return lines;
    }

    private static String formatLocation(ConfigurationSection location) {
        if (location == null) {
            return null;
        }
        String world = trimToNull(location.getString("world"));
        if (!location.contains("x") && world == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (world != null) {
            sb.append(world).append(' ');
        }
        if (location.contains("x")) {
            sb.append(String.format(Locale.US, "(%.1f, %.1f, %.1f)",
                    location.getDouble("x"),
                    location.getDouble("y"),
                    location.getDouble("z")));
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static void appendTextValues(Object raw, List<String> lines) {
        if (raw == null) {
            return;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String s = trimToNull(String.valueOf(item));
                if (s != null) {
                    lines.add(s);
                }
            }
            return;
        }
        String s = trimToNull(String.valueOf(raw));
        if (s != null) {
            lines.add(s);
        }
    }

    private static String stripColor(String input) {
        if (input == null) {
            return "";
        }
        // Minecraft color/format codes: &a, &l, &#rrggbb, etc.
        return input
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("(?i)§[0-9a-fk-or]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
