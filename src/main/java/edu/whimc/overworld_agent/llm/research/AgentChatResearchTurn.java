package edu.whimc.overworld_agent.llm.research;

import edu.whimc.overworld_agent.llm.context.AgentChatContextItem;
import edu.whimc.overworld_agent.llm.context.AgentChatEvent;

import java.util.List;

public record AgentChatResearchTurn(
        String conversationId,
        String turnId,
        int turnIndex,
        long time,
        String playerUuid,
        String username,
        String playerResearchId,
        String sessionId,
        String worldName,
        String agentType,
        String agentName,
        String command,
        String userMessage,
        String assistantResponse,
        String providerName,
        String modelName,
        String systemPromptHash,
        boolean ragEnabled,
        long requestStartedAt,
        Long responseReceivedAt,
        Integer latencyMs,
        String status,
        String errorMessage,
        List<AgentChatContextItem> contextItems,
        List<AgentChatEvent> events
) {
}
