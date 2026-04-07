package edu.whimc.overworld_agent.dialoguetemplate.models;

import org.pmml4s.model.Model;

import java.io.InputStream;

/**
 * Dialogue helper: legacy PMML intent classification for template routing, plus a forward-looking
 * hook for natural-language replies via {@link LlmProvider}.
 */
public class Chatbot {

    private final String userMessage;

    public Chatbot(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Legacy PMML classifier: maps free-text to a prompt label index and a confidence score.
     * Used by {@link edu.whimc.overworld_agent.dialoguetemplate.Dialogue} to pick quest / guidance /
     * tool branches.
     *
     * @return {@code [predictedClassIndex, confidence]}; confidence is compared to a threshold in the caller.
     */
    public double[] classifyDialogueIntent() {
        String[] columns = {this.userMessage};
        Model model = loadPmmlModel();
        if (model == null) {
            return new double[]{0, 0.0};
        }
        Object[] result = model.predict(columns);
        double[] predicted = new double[2];
        int max = 0;
        for (int k = 1; k < result.length; k++) {
            if ((double) result[max] < (double) result[k]) {
                max = k;
            }
        }
        predicted[0] = max;
        predicted[1] = (double) result[max];
        return predicted;
    }

    /**
     * @deprecated use {@link #classifyDialogueIntent()}
     */
    @Deprecated
    public double[] predict() {
        return classifyDialogueIntent();
    }

    private static Model loadPmmlModel() {
        try (InputStream in = Chatbot.class.getResourceAsStream("/model.pmml")) {
            if (in == null) {
                return null;
            }
            return Model.fromInputStream(in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Natural-language assistant reply. Call from an async worker; post results to the main thread
     * before touching Bukkit API.
     *
     * @return {@code null} if no provider or not configured
     */
    public String generateLlmReply(LlmProvider provider, String systemPrompt) throws Exception {
        if (provider == null || !provider.isConfigured()) {
            return null;
        }
        return provider.complete(systemPrompt, userMessage);
    }
}
