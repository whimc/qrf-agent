package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import edu.whimc.overworld_agent.OverworldAgent;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads per-world LLM prompts from {@code plugins/WHIMC-QRF-Agent/world-prompts/*.yml}.
 */
public final class WorldLlmPromptRegistry {

    public static final String PROMPTS_FOLDER = "world-prompts";
    public static final String DEFAULT_FILE = "default.yml";

    private static final String DEFAULT_PROMPT =
            "You are a friendly in-game science education assistant. "
                    + "Answer clearly and briefly; keep content appropriate for students.";

    private final Map<String, WorldLlmPrompt> byWorldKey = new LinkedHashMap<>();
    private WorldLlmPrompt defaultPrompt;

    public static Path promptsDirectory(OverworldAgent plugin) {
        return Path.of(plugin.getDataFolder().getAbsolutePath(), PROMPTS_FOLDER);
    }

    public int reload(OverworldAgent plugin) throws IOException {
        Path dir = promptsDirectory(plugin);
        Files.createDirectories(dir);

        Map<String, WorldLlmPrompt> next = new LinkedHashMap<>();
        WorldLlmPrompt nextDefault = null;
        int filesLoaded = 0;

        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                boolean isDefaultFile = DEFAULT_FILE.equalsIgnoreCase(fileName);
                String stem = fileName;
                int dot = stem.lastIndexOf('.');
                if (dot > 0) {
                    stem = stem.substring(0, dot);
                }
                Optional<PromptFile> parsed = loadFile(plugin, file, isDefaultFile ? null : stem);
                if (parsed.isEmpty()) {
                    continue;
                }
                filesLoaded++;
                PromptFile bundle = parsed.get();
                if (bundle.worldNames.isEmpty()) {
                    if (!isDefaultFile) {
                        plugin.getLogger().warning("world-prompts/" + fileName
                                + ": no worlds listed (set worlds:, world:, or use <WorldName>.yml)");
                        continue;
                    }
                    nextDefault = bundle.asPrompt("default");
                    continue;
                }
                for (String worldName : bundle.worldNames) {
                    String key = worldKey(worldName);
                    if (next.containsKey(key)) {
                        plugin.getLogger().warning("world-prompts/" + fileName + ": overwriting prompt for world '"
                                + worldName + "' (was " + next.get(key).getSourceFile() + ")");
                    }
                    next.put(key, bundle.asPrompt(worldName));
                }
            }
        }

        byWorldKey.clear();
        byWorldKey.putAll(next);
        defaultPrompt = nextDefault;
        return filesLoaded;
    }

    private static Optional<PromptFile> loadFile(OverworldAgent plugin, File file, String fallbackWorldName) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> worldNames = resolveWorldNames(yaml, fallbackWorldName);
        String prompt = yaml.getString("prompt");
        if (StringUtils.isBlank(prompt)) {
            plugin.getLogger().warning("world-prompts/" + file.getName() + ": missing or empty prompt");
            return Optional.empty();
        }
        String ragDirectory = yaml.getString("rag-directory");
        return Optional.of(new PromptFile(worldNames, prompt, ragDirectory, file.getName()));
    }

    /**
     * {@code worlds:} applies one prompt to many worlds. {@code world:} is a single-world shorthand.
     * Filename stem is used when neither is set (e.g. {@code ColderStrip.yml} -> ColderStrip).
     */
    private static List<String> resolveWorldNames(YamlConfiguration yaml, String fallbackWorldName) {
        Set<String> names = new LinkedHashSet<>();
        for (String entry : yaml.getStringList("worlds")) {
            String trimmed = StringUtils.trimToNull(entry);
            if (trimmed != null) {
                names.add(trimmed);
            }
        }
        String single = StringUtils.trimToNull(yaml.getString("world"));
        if (single != null) {
            names.add(single);
        }
        if (names.isEmpty() && fallbackWorldName != null) {
            names.add(fallbackWorldName);
        }
        return List.copyOf(names);
    }

    private static final class PromptFile {
        private final List<String> worldNames;
        private final String prompt;
        private final String ragDirectory;
        private final String sourceFile;

        private PromptFile(List<String> worldNames, String prompt, String ragDirectory, String sourceFile) {
            this.worldNames = worldNames;
            this.prompt = prompt;
            this.ragDirectory = ragDirectory;
            this.sourceFile = sourceFile;
        }

        private WorldLlmPrompt asPrompt(String worldName) {
            return new WorldLlmPrompt(worldName, prompt, ragDirectory, sourceFile);
        }
    }

    public List<WorldLlmPrompt> allWorldPrompts() {
        return Collections.unmodifiableList(new ArrayList<>(byWorldKey.values()));
    }

    /** Groups loaded world bindings by source file (for admin listings). */
    public Map<String, List<String>> worldNamesBySourceFile() {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (WorldLlmPrompt wp : byWorldKey.values()) {
            grouped.computeIfAbsent(wp.getSourceFile(), k -> new ArrayList<>()).add(wp.getWorldName());
        }
        return grouped;
    }

    public Optional<WorldLlmPrompt> getDefaultPrompt() {
        return Optional.ofNullable(defaultPrompt);
    }

    public Optional<WorldLlmPrompt> getForWorld(String worldName) {
        if (worldName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byWorldKey.get(worldKey(worldName)));
    }

    /**
     * Resolves the effective prompt definition for a world (specific file, then default.yml, then absent).
     */
    public Optional<WorldLlmPrompt> resolveDefinition(String worldName) {
        Optional<WorldLlmPrompt> specific = getForWorld(worldName);
        if (specific.isPresent()) {
            return specific;
        }
        return getDefaultPrompt();
    }

    public String buildSystemPrompt(OverworldAgent plugin, String worldName) {
        Optional<WorldLlmPrompt> definition = resolveDefinition(worldName);
        String built;
        if (definition.isPresent()) {
            WorldLlmPrompt wp = definition.get();
            String base = wp.getPrompt();
            if (wp.hasRagDirectory()) {
                Path ragRoot = resolveRagPath(plugin, wp.getRagDirectory());
                built = LlmRagContextBuilder.appendFromDirectory(plugin, base, ragRoot);
            } else {
                built = base;
            }
        } else {
            String base = plugin.getConfig().getString("llm.system-prompt", DEFAULT_PROMPT);
            built = LlmRagContextBuilder.appendIfEnabled(plugin, base);
        }
        if (plugin.getConfig().getBoolean("llm.debug-log", false) && worldName != null) {
            boolean hasRag = built.contains("## Reference material");
            plugin.getLogger().info("[OverworldAgent][LLM] system prompt for world "
                    + worldName + ": " + built.length() + " chars"
                    + (hasRag ? " (includes RAG)" : " (no RAG block)"));
        }
        return built;
    }

    /** Summary for admin commands: prompt source, size, and whether RAG was appended. */
    public String describeBuiltPrompt(OverworldAgent plugin, String worldName) {
        String built = buildSystemPrompt(plugin, worldName);
        boolean hasRag = built.contains("## Reference material");
        StringBuilder sb = new StringBuilder(describeForWorld(worldName));
        sb.append(" -> ").append(built.length()).append(" chars sent to LLM");
        if (hasRag) {
            sb.append(" (RAG appended)");
        } else {
            sb.append(" (no RAG block — check rag-directory path and .txt/.md files)");
        }
        return sb.toString();
    }

    public static Path resolveRagPath(OverworldAgent plugin, String ragDirectory) {
        String sub = StringUtils.trimToEmpty(ragDirectory);
        Path p = Path.of(sub);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return Path.of(plugin.getDataFolder().getAbsolutePath()).resolve(sub).normalize();
    }

    public static String worldKey(String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }

    /**
     * Human-readable summary after reload (for admin command output).
     */
    public String describeForWorld(String worldName) {
        Optional<WorldLlmPrompt> definition = resolveDefinition(worldName);
        if (definition.isEmpty()) {
            return "world '" + worldName + "' uses llm.system-prompt from config.yml"
                    + (byWorldKey.isEmpty() && defaultPrompt == null ? " (no world-prompts files loaded)" : "");
        }
        WorldLlmPrompt wp = definition.get();
        StringBuilder sb = new StringBuilder();
        sb.append("world '").append(worldName).append("' -> ").append(wp.getSourceFile());
        sb.append(" (").append(wp.getPrompt().length()).append(" chars");
        if (wp.hasRagDirectory()) {
            sb.append(", RAG: ").append(wp.getRagDirectory());
        }
        sb.append(")");
        return sb.toString();
    }
}
