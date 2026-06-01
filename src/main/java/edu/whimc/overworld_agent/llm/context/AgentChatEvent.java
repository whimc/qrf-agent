package edu.whimc.overworld_agent.llm.context;

public record AgentChatEvent(
        String turnId,
        long time,
        String eventType,
        String eventPayload
) {
}