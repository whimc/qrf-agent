package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Flattens generic YAML into readable text for RAG, skipping noisy binary-ish keys.
 */
public final class GenericYamlTextConverter {

    private static final Set<String> SKIP_KEYS = Set.of(
            "signature",
            "textureraw",
            "player-skin-textures",
            "player-skin-signature",
            "player-skin-name",
            "cached-skin-uuid",
            "cached-skin-uuid-name",
            "uuid",
            "worldid",
            "traitnames",
            "equipment",
            "inventory",
            "skintrait",
            "skinlayers",
            "navigator",
            "metadata"
    );

    private GenericYamlTextConverter() {}

    public static String convert(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        StringBuilder out = new StringBuilder();
        out.append("YAML document: ").append(file.getName()).append('\n');
        appendSection(config, "", out, 0);
        return out.toString().trim() + '\n';
    }

    private static void appendSection(ConfigurationSection section, String path, StringBuilder out, int depth) {
        if (section == null || depth > 12) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (shouldSkip(key)) {
                continue;
            }
            String childPath = path.isEmpty() ? key : path + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                out.append(indent(depth)).append(key).append(":\n");
                appendSection(child, childPath, out, depth + 1);
                continue;
            }
            if (value instanceof List<?> list) {
                out.append(indent(depth)).append(key).append(":\n");
                for (Object item : list) {
                    if (item instanceof ConfigurationSection child) {
                        appendSection(child, childPath, out, depth + 1);
                    } else {
                        String text = stringify(item);
                        if (!text.isBlank()) {
                            out.append(indent(depth + 1)).append("- ").append(text).append('\n');
                        }
                    }
                }
                continue;
            }
            String text = stringify(value);
            if (!text.isBlank()) {
                out.append(indent(depth)).append(key).append(": ").append(text).append('\n');
            }
        }
    }

    private static boolean shouldSkip(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (SKIP_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("signature")
                || normalized.contains("texture")
                || normalized.endsWith("uuid")
                || normalized.endsWith("uuid-name");
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.length() > 400) {
            // Likely base64 or other blob slipped through
            return text.substring(0, 120) + "…";
        }
        return text
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    private static String indent(int depth) {
        return "  ".repeat(Math.max(0, depth));
    }
}
