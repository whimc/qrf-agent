package edu.whimc.overworld_agent.dialoguetemplate.models;

/**
 * Backend for LLM chat. The plugin can load {@link edu.whimc.overworld_agent.dialoguetemplate.models.llm.OpenAiCompatibleLlmProvider},
 * {@link edu.whimc.overworld_agent.dialoguetemplate.models.llm.GeminiLlmProvider}, etc. from {@code config.yml}
 * ({@code llm.provider}), or you may register a custom implementation with
 * {@link edu.whimc.overworld_agent.OverworldAgent#setLlmProvider(LlmProvider)}.
 */
public interface LlmProvider {

    /**
     * @return false until API keys/base URL/model are configured for the live provider.
     */
    boolean isConfigured();

    /**
     * Produce an assistant reply to the player's message. Implementations should apply timeouts
     * and rate limits; callers should invoke this from a worker thread, not the server main thread.
     *
     * @param systemPrompt high-level behavior and safety instructions for the assistant
     * @param userMessage  text the player typed
     * @return plain-text reply suitable for {@link org.bukkit.entity.Player#sendMessage}
     */
    String complete(String systemPrompt, String userMessage) throws Exception;
}
