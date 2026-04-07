package edu.whimc.overworld_agent.dialoguetemplate.models;

/**
 * Default {@link LlmProvider} when no LLM is wired in yet.
 */
public final class NoOpLlmProvider implements LlmProvider {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        throw new UnsupportedOperationException("No LLM provider registered");
    }
}
