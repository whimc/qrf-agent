package edu.whimc.overworld_agent.llm.context;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.ObservationRow;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.PositionSample;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.PositionSummary;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.ProgressScore;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.ScienceToolRow;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loads and formats WHIMC learner activity (observations, science tools, positions, progress)
 * for LLM system prompts.
 */
public final class LearnerActivityContextProvider {

    private LearnerActivityContextProvider() {}

    public record QueryOptions(
            int maxOwnObservations,
            int maxPeerObservations,
            double nearbyObservationRadius,
            boolean includePeerObservations,
            int maxScienceTools,
            long positionLookbackMs,
            int positionRowLimit,
            boolean includeProgress
    ) {
        public static QueryOptions fromConfig(FileConfiguration cfg) {
            return new QueryOptions(
                    Math.max(0, cfg.getInt("llm.activity-context.max-own-observations", 10)),
                    Math.max(0, cfg.getInt("llm.activity-context.max-peer-observations", 10)),
                    Math.max(1.0, cfg.getDouble("llm.activity-context.nearby-observation-radius", 80.0)),
                    cfg.getBoolean("llm.activity-context.include-peer-observations", true),
                    Math.max(0, cfg.getInt("llm.activity-context.max-science-tools", 12)),
                    Math.max(60_000L, cfg.getLong("llm.activity-context.position-lookback-ms", 900_000L)),
                    Math.max(20, cfg.getInt("llm.activity-context.position-row-limit", 200)),
                    cfg.getBoolean("llm.activity-context.include-progress", true)
            );
        }
    }

    public static boolean isEnabled(OverworldAgent plugin) {
        return plugin.getConfig().getBoolean("llm.activity-context.enabled", false);
    }

    /**
     * Async load; callback runs on the main thread. Passes an empty snapshot when disabled or Queryer is null.
     */
    public static void load(OverworldAgent plugin, Player player, Consumer<LearnerActivitySnapshot> callback) {
        if (callback == null) {
            return;
        }
        if (!isEnabled(plugin) || plugin.getQueryer() == null || player == null) {
            callback.accept(LearnerActivitySnapshot.empty());
            return;
        }
        plugin.getQueryer().loadLearnerActivityContext(player, QueryOptions.fromConfig(plugin.getConfig()), callback);
    }

    public static String formatForPrompt(LearnerActivitySnapshot snapshot) {
        return formatForPrompt(snapshot, null);
    }

    public static String formatForPrompt(LearnerActivitySnapshot snapshot, Player player) {
        if (snapshot == null || snapshot.isEmpty()) {
            // Still show live location if we have a player and no DB rows.
            if (player == null || !player.isOnline()) {
                return "";
            }
        }

        boolean hasDb = snapshot != null && !snapshot.isEmpty();
        if (!hasDb && (player == null || !player.isOnline())) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        out.append("\n\n## Learner activity context (server data — use for grounding directions)\n");
        out.append("Prefer these facts over guessing. Encourage noticing strong peer observations when relevant.\n");
        out.append("Give directions with landmarks/biomes when possible; avoid raw coordinate dumps unless asked.\n");
        out.append("If data is missing or stale, say you are unsure — do not invent readings or observations.\n");

        if (player != null && player.isOnline()) {
            Location live = player.getLocation();
            String world = live.getWorld() == null ? "unknown" : live.getWorld().getName();
            out.append("\n### Current location (live)\n");
            out.append(world)
                    .append(" (")
                    .append(fmt(live.getX())).append(", ")
                    .append(fmt(live.getY())).append(", ")
                    .append(fmt(live.getZ())).append(")\n");
        }

        if (!hasDb) {
            return out.toString();
        }

        ProgressScore progress = snapshot.progress();
        if (progress != null) {
            out.append("\n### Progress (latest)\n");
            out.append(String.format(Locale.ROOT,
                    "observation=%.2f science_tools=%.2f exploration=%.2f quest=%.2f poi_exploration=%.2f overall=%.2f\n",
                    progress.observation(),
                    progress.scienceTools(),
                    progress.exploration(),
                    progress.quest(),
                    progress.poiExploration(),
                    progress.score()));
        }

        PositionSummary positions = snapshot.positionSummary();
        if (positions != null && !positions.isEmpty()) {
            out.append("\n### Where you've been (summary from recent samples)\n");
            PositionSample latest = positions.latest();
            if (latest != null) {
                out.append("last recorded: ")
                        .append(latest.world())
                        .append(" (")
                        .append(latest.x()).append(", ")
                        .append(latest.y()).append(", ")
                        .append(latest.z()).append(")")
                        .append(" biome ")
                        .append(blankToUnknown(latest.biome()))
                        .append('\n');
            }
            if (positions.recentBiomes() != null && !positions.recentBiomes().isEmpty()) {
                out.append("frequent biomes: ").append(String.join(", ", positions.recentBiomes())).append('\n');
            }
            if (positions.hotspotLabels() != null && !positions.hotspotLabels().isEmpty()) {
                out.append("areas visited: ").append(String.join("; ", positions.hotspotLabels())).append('\n');
            }
        }

        List<ScienceToolRow> tools = snapshot.scienceTools();
        if (tools != null && !tools.isEmpty()) {
            out.append("\n### Your recent science measurements\n");
            for (ScienceToolRow row : tools) {
                out.append("- [")
                        .append(blankToUnknown(row.tool()))
                        .append("] ")
                        .append(truncate(blankToUnknown(row.measurement()), 160))
                        .append(" @ ")
                        .append(row.world())
                        .append(" (")
                        .append(fmt(row.x())).append(", ")
                        .append(fmt(row.y())).append(", ")
                        .append(fmt(row.z())).append(")\n");
            }
        }

        List<ObservationRow> own = snapshot.ownObservations();
        if (own != null && !own.isEmpty()) {
            out.append("\n### Your recent observations\n");
            for (ObservationRow row : own) {
                appendObservationLine(out, row, false);
            }
        }

        List<ObservationRow> peers = snapshot.peerObservations();
        if (peers != null && !peers.isEmpty()) {
            out.append("\n### Nearby public observations (peers)\n");
            out.append("These are visible in-world; help the learner notice and compare when they are useful.\n");
            for (ObservationRow row : peers) {
                appendObservationLine(out, row, true);
            }
        }

        return out.toString();
    }

    public static List<AgentChatContextItem> toContextItems(String turnId, LearnerActivitySnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        List<AgentChatContextItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        int rank = 1;

        if (snapshot.progress() != null) {
            ProgressScore p = snapshot.progress();
            String text = String.format(Locale.ROOT,
                    "progress observation=%.2f science_tools=%.2f exploration=%.2f quest=%.2f poi=%.2f overall=%.2f",
                    p.observation(), p.scienceTools(), p.exploration(), p.quest(), p.poiExploration(), p.score());
            items.add(item(turnId, now, rank++, "progress", "whimc_progress", "progress", null,
                    null, null, null, null, null, text, 1.0));
        }

        if (snapshot.positionSummary() != null && snapshot.positionSummary().latest() != null) {
            PositionSample latest = snapshot.positionSummary().latest();
            String text = "latest position biome=" + blankToUnknown(latest.biome());
            items.add(item(turnId, now, rank++, "position", "whimc_player_positions", "position-summary",
                    latest.world(), (double) latest.x(), (double) latest.y(), (double) latest.z(),
                    null, null, text, 0.9));
        }

        if (snapshot.scienceTools() != null) {
            for (ScienceToolRow row : snapshot.scienceTools()) {
                String text = row.tool() + ": " + truncate(row.measurement(), 120);
                items.add(item(turnId, now, rank++, "science_tool", "whimc_sciencetools:" + row.tool(),
                        row.tool(), row.world(), row.x(), row.y(), row.z(), null, null, text, 0.8));
            }
        }

        if (snapshot.ownObservations() != null) {
            for (ObservationRow row : snapshot.ownObservations()) {
                items.add(item(turnId, now, rank++, "observation", "whimc_observations:own",
                        "own-observation", row.world(), row.x(), row.y(), row.z(), row.distance(),
                        row.category(), truncate(row.text(), 200), 0.7));
            }
        }

        if (snapshot.peerObservations() != null) {
            for (ObservationRow row : snapshot.peerObservations()) {
                items.add(item(turnId, now, rank++, "peer_observation", "whimc_observations:peer",
                        row.username(), row.world(), row.x(), row.y(), row.z(), row.distance(),
                        row.category(), truncate(row.text(), 200), 0.65));
            }
        }

        return items;
    }

    public static PositionSummary summarizePositions(List<PositionSample> samples, int maxBiomes, int maxHotspots) {
        if (samples == null || samples.isEmpty()) {
            return null;
        }
        List<PositionSample> chronological = new ArrayList<>(samples);
        chronological.sort(Comparator.comparingLong(PositionSample::time));
        PositionSample latest = chronological.get(chronological.size() - 1);

        Map<String, Integer> biomeCounts = new LinkedHashMap<>();
        for (int i = chronological.size() - 1; i >= 0; i--) {
            String biome = blankToUnknown(chronological.get(i).biome());
            biomeCounts.merge(biome, 1, Integer::sum);
        }
        List<String> biomes = biomeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.max(1, maxBiomes))
                .map(e -> e.getKey() + " (~" + e.getValue() + " samples)")
                .toList();

        Map<String, int[]> cells = new LinkedHashMap<>();
        for (PositionSample sample : chronological) {
            int cx = Math.floorDiv(sample.x(), 16);
            int cz = Math.floorDiv(sample.z(), 16);
            String key = sample.world() + "|" + cx + "|" + cz;
            int[] agg = cells.computeIfAbsent(key, k -> new int[] {0, 0, 0, 0, 0});
            // count, sumX, sumY, sumZ, (world unused)
            agg[0]++;
            agg[1] += sample.x();
            agg[2] += sample.y();
            agg[3] += sample.z();
        }
        List<String> hotspots = cells.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(Math.max(1, maxHotspots))
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", 3);
                    int[] agg = e.getValue();
                    int avgX = agg[1] / agg[0];
                    int avgY = agg[2] / agg[0];
                    int avgZ = agg[3] / agg[0];
                    return parts[0] + " (~" + avgX + ", " + avgY + ", " + avgZ + ") x" + agg[0];
                })
                .toList();

        return new PositionSummary(latest, biomes, hotspots);
    }

    private static void appendObservationLine(StringBuilder out, ObservationRow row, boolean includeAuthor) {
        out.append("- \"").append(truncate(blankToUnknown(row.text()), 180)).append('"');
        if (includeAuthor && row.username() != null && !row.username().isBlank()) {
            out.append(" by ").append(row.username());
        }
        out.append(" @ ").append(row.world())
                .append(" (")
                .append(fmt(row.x())).append(", ")
                .append(fmt(row.y())).append(", ")
                .append(fmt(row.z())).append(")");
        if (row.distance() != null) {
            out.append(String.format(Locale.ROOT, " ~%.0fm away", row.distance()));
        }
        if (row.category() != null && !row.category().isBlank()) {
            out.append(" [").append(row.category()).append(']');
        }
        out.append('\n');
    }

    private static AgentChatContextItem item(
            String turnId,
            long time,
            int rank,
            String type,
            String sourceId,
            String title,
            String world,
            Double x,
            Double y,
            Double z,
            Double distance,
            String trait,
            String text,
            double score
    ) {
        return new AgentChatContextItem(
                turnId,
                time,
                rank,
                type,
                sourceId,
                title,
                world,
                x,
                y,
                z,
                distance,
                trait,
                text,
                sha256OrNull(text),
                score
        );
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String sha256OrNull(String value) {
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
}
