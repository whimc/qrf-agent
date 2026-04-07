package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads plain-text snippets from {@link JavaPlugin#getDataFolder()} under {@code llm.context-directory}
 * for optional RAG-style augmentation of the system prompt.
 */
public final class LlmRagContextBuilder {

    private LlmRagContextBuilder() {}

    public static String appendIfEnabled(JavaPlugin plugin, String baseSystemPrompt) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("llm.rag.enabled", false)) {
            return baseSystemPrompt == null ? "" : baseSystemPrompt;
        }
        Path root = resolveContextRoot(plugin);
        if (!Files.isDirectory(root)) {
            return baseSystemPrompt == null ? "" : baseSystemPrompt;
        }

        int maxTotal = cfg.getInt("llm.rag.max-total-chars", 12_000);
        int maxFile = cfg.getInt("llm.rag.max-file-chars", 4_000);
        int maxDepth = cfg.getInt("llm.rag.max-directory-depth", 6);
        List<String> extensions = cfg.getStringList("llm.rag.include-extensions");
        if (extensions == null || extensions.isEmpty()) {
            extensions = List.of("txt", "md");
        }
        List<String> normalizedExt = extensions.stream()
                .map(e -> e.toLowerCase(Locale.ROOT).replace(".", ""))
                .collect(Collectors.toList());

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, maxDepth)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (dot < 0 || dot == name.length() - 1) {
                            return false;
                        }
                        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                        return normalizedExt.contains(ext);
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(files::add);
        } catch (IOException e) {
            plugin.getLogger().warning("LLM RAG: could not scan context directory: " + e.getMessage());
            return baseSystemPrompt == null ? "" : baseSystemPrompt;
        }

        StringBuilder rag = new StringBuilder();
        int used = 0;
        for (Path file : files) {
            if (used >= maxTotal) {
                break;
            }
            try {
                String rel = root.relativize(file).toString().replace('\\', '/');
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.length() > maxFile) {
                    content = content.substring(0, maxFile) + "\n...[truncated]\n";
                }
                String block = "--- file: " + rel + " ---\n" + content + "\n";
                if (used + block.length() > maxTotal) {
                    block = block.substring(0, Math.max(0, maxTotal - used));
                }
                rag.append(block);
                used += block.length();
            } catch (IOException ex) {
                plugin.getLogger().fine("LLM RAG: skip " + file + ": " + ex.getMessage());
            }
        }

        if (rag.isEmpty()) {
            return baseSystemPrompt == null ? "" : baseSystemPrompt;
        }

        String header = "\n\n## Reference material (from server context files — use only if relevant)\n\n";
        String sep = baseSystemPrompt == null || baseSystemPrompt.isBlank() ? "" : "\n\n";
        return (baseSystemPrompt == null ? "" : baseSystemPrompt) + sep + header + rag;
    }

    public static Path resolveContextRoot(JavaPlugin plugin) {
        String sub = plugin.getConfig().getString("llm.context-directory", "llm-context");
        if (sub == null || sub.isBlank()) {
            sub = "llm-context";
        }
        Path p = Path.of(plugin.getDataFolder().getAbsolutePath()).resolve(sub);
        try {
            Files.createDirectories(p);
        } catch (IOException ignored) {
            // directory may already exist or be unwritable — appendIfEnabled will no-op on missing dir
        }
        return p.normalize();
    }
}
