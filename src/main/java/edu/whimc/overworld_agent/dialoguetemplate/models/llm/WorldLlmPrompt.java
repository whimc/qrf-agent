package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import org.apache.commons.lang3.StringUtils;

/**
 * Per-world LLM system prompt binding (loaded from {@code world-prompts/*.yml}).
 * Several worlds may share one file via a {@code worlds:} list.
 */
public final class WorldLlmPrompt {

    private final String worldName;
    private final String prompt;
    private final String ragDirectory;
    private final String sourceFile;

    public WorldLlmPrompt(String worldName, String prompt, String ragDirectory, String sourceFile) {
        this.worldName = worldName;
        this.prompt = prompt == null ? "" : prompt;
        this.ragDirectory = StringUtils.trimToNull(ragDirectory);
        this.sourceFile = sourceFile;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPrompt() {
        return prompt;
    }

    public boolean hasRagDirectory() {
        return ragDirectory != null;
    }

    public String getRagDirectory() {
        return ragDirectory;
    }

    public String getSourceFile() {
        return sourceFile;
    }
}
