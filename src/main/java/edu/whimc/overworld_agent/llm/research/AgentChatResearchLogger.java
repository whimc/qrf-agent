package edu.whimc.overworld_agent.llm.research;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.llm.context.AgentChatContextItem;
import edu.whimc.overworld_agent.llm.context.AgentChatEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Persists turns to {@code whimc_agent_chat_*} for interactive chat and dialogue discussion.
 */
public final class AgentChatResearchLogger {

    private AgentChatResearchLogger() {
    }

    public static void storeTurn(OverworldAgent plugin, AgentChatResearchTurn turn) {
        if (plugin.getQueryer() == null) {
            plugin.getLogger().warning("[OverworldAgent][ResearchDB] Queryer is null; cannot store chat research turn.");
            return;
        }

        plugin.getQueryer().storeAgentChatResearchTurnWithContextItems(
                turn.conversationId(),
                turn.turnId(),
                turn.turnIndex(),
                turn.time(),
                turn.playerUuid(),
                turn.username(),
                turn.playerResearchId(),
                turn.sessionId(),
                turn.worldName(),
                turn.agentType(),
                turn.agentName(),
                turn.command(),
                turn.userMessage(),
                turn.assistantResponse(),
                turn.providerName(),
                turn.modelName(),
                "default",
                turn.systemPromptHash(),
                turn.ragEnabled(),
                turn.userMessage(),
                turn.requestStartedAt(),
                turn.responseReceivedAt(),
                turn.latencyMs(),
                turn.status(),
                turn.errorMessage(),
                turn.contextItems() == null ? List.of() : turn.contextItems(),
                turn.events() == null ? List.of() : turn.events()
        );
    }

    public static AgentChatEvent simpleEvent(String turnId, long time, String eventType, String message) {
        String payload =
                "{" +
                        "\"event_type\":\"" + jsonEscape(eventType) + "\"," +
                        "\"message\":\"" + jsonEscape(message) + "\"" +
                        "}";

        return new AgentChatEvent(turnId, time, eventType, payload);
    }

    public static AgentChatEvent llmRequestPayloadEvent(
            String turnId,
            long time,
            String conversationId,
            String traceId,
            String command,
            String providerName,
            String modelName,
            String systemPrompt,
            String userMessage,
            boolean ragEnabled,
            List<AgentChatContextItem> contextItems
    ) {
        String payload =
                "{" +
                        "\"trace_id\":\"" + jsonEscape(traceId) + "\"," +
                        "\"conversation_id\":\"" + jsonEscape(conversationId) + "\"," +
                        "\"turn_id\":\"" + jsonEscape(turnId) + "\"," +
                        "\"command\":\"" + jsonEscape(command) + "\"," +
                        "\"provider\":\"" + jsonEscape(providerName) + "\"," +
                        "\"model\":\"" + jsonEscape(modelName) + "\"," +
                        "\"rag_enabled\":" + ragEnabled + "," +
                        "\"context_item_count\":" + (contextItems == null ? 0 : contextItems.size()) + "," +
                        "\"messages\":[" +
                        "{" +
                        "\"role\":\"system\"," +
                        "\"content\":\"" + jsonEscape(systemPrompt) + "\"" +
                        "}," +
                        "{" +
                        "\"role\":\"user\"," +
                        "\"content\":\"" + jsonEscape(userMessage) + "\"" +
                        "}" +
                        "]" +
                        "}";

        return new AgentChatEvent(turnId, time, "llm_request_payload", payload);
    }

    public static AgentChatEvent llmResponsePayloadEvent(
            String turnId,
            long time,
            String traceId,
            String response,
            String status,
            Integer latencyMs,
            String errorMessage
    ) {
        String payload =
                "{" +
                        "\"trace_id\":\"" + jsonEscape(traceId) + "\"," +
                        "\"status\":\"" + jsonEscape(status) + "\"," +
                        "\"latency_ms\":" + (latencyMs == null ? "null" : latencyMs) + "," +
                        "\"response\":\"" + jsonEscape(response) + "\"," +
                        "\"error_message\":\"" + jsonEscape(errorMessage) + "\"" +
                        "}";

        return new AgentChatEvent(turnId, time, "llm_response_payload", payload);
    }

    public static AgentChatEvent pmmlIntentEvent(
            String turnId,
            long time,
            int predictedClass,
            double certainty,
            String intentLabel
    ) {
        String payload =
                "{" +
                        "\"predicted_class\":" + predictedClass + "," +
                        "\"certainty\":" + certainty + "," +
                        "\"intent_label\":\"" + jsonEscape(intentLabel) + "\"" +
                        "}";

        return new AgentChatEvent(turnId, time, "pmml_intent", payload);
    }

    public static String sha256OrNull(String value) {
        if (value == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    public static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }

        return escaped.toString();
    }
}
