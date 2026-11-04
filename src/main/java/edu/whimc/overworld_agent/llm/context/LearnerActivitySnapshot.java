package edu.whimc.overworld_agent.llm.context;

import java.util.List;

/**
 * Filtered learner activity from WHIMC research tables for one LLM turn.
 */
public record LearnerActivitySnapshot(
        ProgressScore progress,
        PositionSummary positionSummary,
        List<ObservationRow> ownObservations,
        List<ObservationRow> peerObservations,
        List<ScienceToolRow> scienceTools
) {
    public static LearnerActivitySnapshot empty() {
        return new LearnerActivitySnapshot(null, null, List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return progress == null
                && (positionSummary == null || positionSummary.isEmpty())
                && (ownObservations == null || ownObservations.isEmpty())
                && (peerObservations == null || peerObservations.isEmpty())
                && (scienceTools == null || scienceTools.isEmpty());
    }

    public record ProgressScore(
            double observation,
            double scienceTools,
            double exploration,
            double quest,
            double poiExploration,
            double score,
            long time
    ) {}

    public record PositionSample(
            int x,
            int y,
            int z,
            String world,
            String biome,
            long time
    ) {}

    public record PositionSummary(
            PositionSample latest,
            List<String> recentBiomes,
            List<String> hotspotLabels
    ) {
        public boolean isEmpty() {
            return latest == null
                    && (recentBiomes == null || recentBiomes.isEmpty())
                    && (hotspotLabels == null || hotspotLabels.isEmpty());
        }
    }

    public record ObservationRow(
            String text,
            String username,
            String world,
            double x,
            double y,
            double z,
            String category,
            long time,
            Double distance
    ) {}

    public record ScienceToolRow(
            String tool,
            String measurement,
            String world,
            double x,
            double y,
            double z,
            long time
    ) {}
}
