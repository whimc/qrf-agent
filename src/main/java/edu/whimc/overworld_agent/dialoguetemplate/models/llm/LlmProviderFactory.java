package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.dialoguetemplate.models.LlmProvider;
import edu.whimc.overworld_agent.dialoguetemplate.models.NoOpLlmProvider;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Builds a {@link LlmProvider} from {@code config.yml} {@code llm.*} keys.
 */
public final class LlmProviderFactory {

    private LlmProviderFactory() {}

    public static LlmProvider create(OverworldAgent plugin) {
        String raw = plugin.getConfig().getString("llm.provider", "none");
        String provider = raw == null ? "none" : raw.trim().toLowerCase(Locale.ROOT);
        if (provider.isEmpty() || "none".equals(provider) || "noop".equals(provider)) {
            return new NoOpLlmProvider();
        }

        int timeout = plugin.getConfig().getInt("llm.request-timeout-seconds", 60);
        String model = StringUtils.trimToEmpty(plugin.getConfig().getString("llm.model"));
        String apiKey = LlmApiKeyResolver.resolve(plugin.getConfig());

        try {
            switch (provider) {
                case "openai":
                    return new OpenAiCompatibleLlmProvider(
                            "https://api.openai.com/v1",
                            apiKey,
                            model.isEmpty() ? "gpt-4o-mini" : model,
                            timeout,
                            true);
                case "openai_compatible":
                case "openai-compatible":
                    String base = StringUtils.trimToEmpty(plugin.getConfig().getString("llm.base-url"));
                    if (base.isEmpty()) {
                        base = "http://127.0.0.1:11434/v1";
                    }
                    return new OpenAiCompatibleLlmProvider(
                            base,
                            apiKey,
                            model.isEmpty() ? "llama3.2" : model,
                            timeout,
                            false);
                case "gemini":
                case "google":
                    return new GeminiLlmProvider(
                            apiKey,
                            model.isEmpty() ? "gemini-1.5-flash" : model,
                            timeout);
                default:
                    plugin.getLogger().warning("Unknown llm.provider '" + raw + "' — use none, openai, gemini, openai_compatible.");
                    return new NoOpLlmProvider();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create LLM provider", e);
            return new NoOpLlmProvider();
        }
    }
}
