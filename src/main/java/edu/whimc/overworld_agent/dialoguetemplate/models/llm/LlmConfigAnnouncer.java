package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import edu.whimc.overworld_agent.OverworldAgent;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Logs active LLM provider settings, per-world prompts, and RAG document bindings to the server console.
 */
public final class LlmConfigAnnouncer {

    private LlmConfigAnnouncer() {}

    public static void announce(OverworldAgent plugin) {
        Logger log = plugin.getLogger();
        FileConfiguration cfg = plugin.getConfig();
        String provider = cfg.getString("llm.provider", "none");
        String model = cfg.getString("llm.model", "");
        String baseUrl = StringUtils.trimToEmpty(cfg.getString("llm.base-url"));
        boolean useForReply = cfg.getBoolean("llm.use-for-reply", false);
        boolean ragEnabled = cfg.getBoolean("llm.rag.enabled", false);
        String apiKeyEnv = StringUtils.trimToEmpty(cfg.getString("llm.api-key-env"));
        boolean inlineKey = StringUtils.isNotBlank(cfg.getString("llm.api-key"));
        boolean configured = plugin.getLlmProvider() != null && plugin.getLlmProvider().isConfigured();

        log.info("[OverworldAgent][LLM] ---- active configuration ----");
        log.info("[OverworldAgent][LLM] provider=" + provider
                + " model=" + (model.isBlank() ? "(default)" : model)
                + " use-for-reply=" + useForReply
                + " configured=" + configured);
        if (!baseUrl.isBlank()) {
            log.info("[OverworldAgent][LLM] base-url=" + baseUrl);
        }
        if (!apiKeyEnv.isBlank()) {
            log.info("[OverworldAgent][LLM] api-key-env=" + apiKeyEnv);
        } else if (inlineKey) {
            log.info("[OverworldAgent][LLM] api-key=(set inline in config.yml)");
        }
        log.info("[OverworldAgent][LLM] global RAG enabled=" + ragEnabled
                + " context-directory=" + cfg.getString("llm.context-directory", "llm-context"));
        log.info("[OverworldAgent][LLM] activity-context enabled="
                + cfg.getBoolean("llm.activity-context.enabled", false)
                + " peers=" + cfg.getBoolean("llm.activity-context.include-peer-observations", true)
                + " progress=" + cfg.getBoolean("llm.activity-context.include-progress", true));
        if (ragEnabled) {
            announceRagDirectory(log, "global", LlmRagContextBuilder.resolveContextRoot(plugin), plugin);
        }

        WorldLlmPromptRegistry registry = plugin.getWorldLlmPromptRegistry();
        for (Map.Entry<String, List<String>> entry : registry.worldNamesBySourceFile().entrySet()) {
            Optional<WorldLlmPrompt> sample = registry.getForWorld(entry.getValue().get(0));
            if (sample.isEmpty()) {
                continue;
            }
            WorldLlmPrompt wp = sample.get();
            log.info("[OverworldAgent][LLM] world-prompts/" + entry.getKey()
                    + " -> worlds=[" + String.join(", ", entry.getValue()) + "]"
                    + " promptChars=" + wp.getPrompt().length());
            if (wp.hasRagDirectory()) {
                Path ragRoot = WorldLlmPromptRegistry.resolveRagPath(plugin, wp.getRagDirectory());
                log.info("[OverworldAgent][LLM]   rag-directory=" + wp.getRagDirectory()
                        + " resolved=" + ragRoot);
                announceRagDirectory(log, wp.getRagDirectory(), ragRoot, plugin);
            } else {
                log.info("[OverworldAgent][LLM]   rag-directory=(none)");
            }
        }
        registry.getDefaultPrompt().ifPresent(wp -> {
            log.info("[OverworldAgent][LLM] world-prompts/default.yml (fallback)"
                    + " promptChars=" + wp.getPrompt().length());
            if (wp.hasRagDirectory()) {
                Path ragRoot = WorldLlmPromptRegistry.resolveRagPath(plugin, wp.getRagDirectory());
                log.info("[OverworldAgent][LLM]   rag-directory=" + wp.getRagDirectory()
                        + " resolved=" + ragRoot);
                announceRagDirectory(log, wp.getRagDirectory(), ragRoot, plugin);
            }
        });
        if (registry.allWorldPrompts().isEmpty() && registry.getDefaultPrompt().isEmpty()) {
            String fallback = cfg.getString("llm.system-prompt", "");
            log.info("[OverworldAgent][LLM] no world-prompts loaded; fallback config llm.system-prompt chars="
                    + (fallback == null ? 0 : fallback.length()));
        }
        log.info("[OverworldAgent][LLM] ------------------------------");
    }

    private static void announceRagDirectory(Logger log, String label, Path root, OverworldAgent plugin) {
        List<String> docs = LlmRagContextBuilder.listDocumentPaths(root, plugin);
        if (docs.isEmpty()) {
            log.info("[OverworldAgent][LLM]   RAG documents for " + label + ": (none — missing dir or no matching files)");
            return;
        }
        log.info("[OverworldAgent][LLM]   RAG documents for " + label + " (" + docs.size() + "): "
                + docs.stream().limit(12).collect(Collectors.joining(", "))
                + (docs.size() > 12 ? " …" : ""));
    }
}
