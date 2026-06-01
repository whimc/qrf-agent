package edu.whimc.overworld_agent.llm.context;

public record AgentChatContextItem(
        String turnId,
        long time,
        int contextRank,
        String contextType,
        String sourceId,
        String sourceTitle,
        String worldName,
        Double x,
        Double y,
        Double z,
        Double distance,
        String traitType,
        String contextText,
        String contextHash,
        Double score
) {
}