package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.whimc.overworld_agent.dialoguetemplate.models.LlmProvider;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Google AI <a href="https://ai.google.dev/api/generate-content">Gemini generateContent</a> (API key auth).
 */
public final class GeminiLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public GeminiLlmProvider(String apiKey, String model, int timeoutSeconds) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(apiKey) && StringUtils.isNotBlank(model);
    }

    @Override
    public String complete(String systemPrompt, String userMessage) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini LLM is not configured");
        }
        String encKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        URI uri = URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + encKey);

        JsonObject root = new JsonObject();
        if (StringUtils.isNotBlank(systemPrompt)) {
            JsonObject sysInst = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemPrompt);
            sysParts.add(sysPart);
            sysInst.add("parts", sysParts);
            root.add("systemInstruction", sysInst);
        }
        JsonArray contents = new JsonArray();
        JsonObject turn = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userMessage == null ? "" : userMessage);
        parts.add(part);
        turn.add("parts", parts);
        contents.add(turn);
        root.add("contents", contents);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        if (body.has("error")) {
            throw new IllegalStateException("API error: " + body.get("error"));
        }
        JsonArray candidates = body.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates in Gemini response (blocked or empty)");
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray outParts = content.getAsJsonArray("parts");
        if (outParts == null || outParts.isEmpty()) {
            throw new IllegalStateException("No content.parts in Gemini response");
        }
        return outParts.get(0).getAsJsonObject().get("text").getAsString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
