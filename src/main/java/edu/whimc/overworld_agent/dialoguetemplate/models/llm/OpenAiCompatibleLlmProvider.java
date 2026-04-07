package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.whimc.overworld_agent.dialoguetemplate.models.LlmProvider;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI <a href="https://platform.openai.com/docs/api-reference/chat/create">chat completions</a> or any
 * OpenAI-compatible HTTP API (Ollama {@code /v1}, LM Studio, vLLM, etc.).
 */
public final class OpenAiCompatibleLlmProvider implements LlmProvider {

    private final URI chatCompletionsUri;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final boolean requireApiKey;

    public OpenAiCompatibleLlmProvider(String baseUrl, String apiKey, String model, int timeoutSeconds,
            boolean requireApiKey) {
        this.chatCompletionsUri = chatCompletionsUri(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = Objects.requireNonNull(model, "model");
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.requireApiKey = requireApiKey;
    }

    private static URI chatCompletionsUri(String baseUrl) {
        String b = baseUrl.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!b.endsWith("/chat/completions")) {
            b = b + "/chat/completions";
        }
        return URI.create(b);
    }

    @Override
    public boolean isConfigured() {
        if (StringUtils.isBlank(model)) {
            return false;
        }
        if (requireApiKey && StringUtils.isBlank(apiKey)) {
            return false;
        }
        return true;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI-compatible LLM is not configured");
        }
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt == null ? "" : systemPrompt);
        messages.add(sys);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userMessage == null ? "" : userMessage);
        messages.add(user);
        root.add("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(chatCompletionsUri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(apiKey)) {
            rb.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = client.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        if (body.has("error")) {
            throw new IllegalStateException("API error: " + body.get("error"));
        }
        JsonArray choices = body.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices in response");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IllegalStateException("Missing message.content");
        }
        return message.get("content").getAsString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
