package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Resolves API keys from {@code llm.api-key} and optional {@code llm.api-key-env}.
 */
public final class LlmApiKeyResolver {

    private LlmApiKeyResolver() {}

    /**
     * If {@code api-key-env} is set, reads {@link System#getenv(String)}; otherwise uses {@code api-key} from config.
     */
    public static String resolve(FileConfiguration config) {
        String envName = StringUtils.trimToEmpty(config.getString("llm.api-key-env"));
        if (!envName.isEmpty()) {
            String fromEnv = System.getenv(envName);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv;
            }
            return "";
        }
        return StringUtils.trimToEmpty(config.getString("llm.api-key"));
    }
}
