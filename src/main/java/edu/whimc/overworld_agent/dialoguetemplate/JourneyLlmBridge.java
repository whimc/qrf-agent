package edu.whimc.overworld_agent.dialoguetemplate;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets the optional LLM trigger Journey navigation via a structured {@code JOURNEY:name_id}
 * directive (own line or trailing) and fuzzy matching against the guidance catalog.
 */
public final class JourneyLlmBridge {

    /** Own line: {@code JOURNEY:poi-foo} */
    private static final Pattern JOURNEY_LINE = Pattern.compile(
            "(?im)^\\s*JOURNEY\\s*:\\s*(\\S+)\\s*$");

    /**
     * Inline / trailing forms models often emit under short-reply constraints, e.g.
     * {@code ... visit the museum. Journey:poi-museum_of_mynoa}
     */
    private static final Pattern JOURNEY_INLINE = Pattern.compile(
            "(?i)(?:^|[\\s.!?])JOURNEY\\s*:\\s*([A-Za-z0-9_.:/-]+)\\s*$");

    public record ParsedReply(String displayText, String journeyNameId) {}

    private JourneyLlmBridge() {}

    public static String appendDestinationContext(String systemPrompt,
            List<JourneyGuidanceCatalog.Destination> destinations, int maxDestinations) {
        if (destinations == null || destinations.isEmpty()) {
            return systemPrompt;
        }
        int cap = maxDestinations <= 0 ? 60 : maxDestinations;
        List<JourneyGuidanceCatalog.Destination> sorted = new ArrayList<>(destinations);
        sorted.sort(Comparator.comparing(JourneyGuidanceCatalog.Destination::label, String.CASE_INSENSITIVE_ORDER));
        if (sorted.size() > cap) {
            sorted = sorted.subList(0, cap);
        }

        StringBuilder block = new StringBuilder();
        block.append("\n\n## Navigation (Journey plugin)\n");
        block.append("When you want the server to take the player somewhere from the list below, ");
        block.append("end your reply with a FINAL line that contains ONLY this (no other words on that line):\n");
        block.append("JOURNEY:<name_id>\n");
        block.append("That line is stripped before the player sees your message — it does not count toward ");
        block.append("your 2-sentence limit. Use the name_id from the list (not the display label). ");
        block.append("Do not write Journey: inline inside a sentence.\n");
        block.append("Available destinations (name_id — label):\n");
        for (JourneyGuidanceCatalog.Destination d : sorted) {
            block.append("- ").append(d.jtKey()).append(" — ").append(d.label()).append('\n');
        }
        return (systemPrompt == null ? "" : systemPrompt) + block;
    }

    public static ParsedReply parseLlmReply(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            return new ParsedReply("", null);
        }

        String nameId = null;
        Matcher lineMatcher = JOURNEY_LINE.matcher(llmText);
        while (lineMatcher.find()) {
            nameId = lineMatcher.group(1).toLowerCase(Locale.ROOT);
        }

        String withoutLines = JOURNEY_LINE.matcher(llmText).replaceAll("").trim();

        if (nameId == null) {
            Matcher inlineMatcher = JOURNEY_INLINE.matcher(withoutLines);
            if (inlineMatcher.find()) {
                nameId = inlineMatcher.group(1).toLowerCase(Locale.ROOT);
                // Strip trailing "Journey:id" (and a preceding space/punct if present)
                withoutLines = withoutLines.substring(0, inlineMatcher.start()).replaceAll("[\\s.!?]+$", "").trim();
            }
        }

        if (nameId != null) {
            // Strip accidental wrapping punctuation models sometimes add
            nameId = nameId.replaceAll("^[^a-z0-9]+|[^a-z0-9_.:/-]+$", "");
        }

        return new ParsedReply(withoutLines, StringUtils.isBlank(nameId) ? null : nameId);
    }

    /**
     * Best-effort match of free text (player message or destination phrase) to a catalog entry.
     */
    public static Optional<String> matchDestination(String text, List<JourneyGuidanceCatalog.Destination> destinations) {
        if (StringUtils.isBlank(text) || destinations == null || destinations.isEmpty()) {
            return Optional.empty();
        }
        String norm = normalize(text);
        if (norm.isBlank()) {
            return Optional.empty();
        }

        JourneyGuidanceCatalog.Destination best = null;
        int bestScore = 0;
        for (JourneyGuidanceCatalog.Destination d : destinations) {
            int score = scoreMatch(norm, d);
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        if (best != null && bestScore >= 80) {
            return Optional.of(best.jtKey());
        }
        return Optional.empty();
    }

    private static int scoreMatch(String norm, JourneyGuidanceCatalog.Destination d) {
        String labelNorm = normalize(d.label());
        String keyNorm = normalize(stripScopePrefix(d.jtKey()));
        if (norm.equals(labelNorm) || norm.equals(keyNorm)) {
            return 100;
        }
        if (labelNorm.contains(norm) || norm.contains(labelNorm)) {
            return 90;
        }
        if (keyNorm.contains(norm) || norm.contains(keyNorm)) {
            return 85;
        }
        if (labelNorm.length() >= 4 && norm.contains(labelNorm)) {
            return 88;
        }
        if (keyNorm.length() >= 4 && norm.contains(keyNorm)) {
            return 86;
        }
        return 0;
    }

    private static String stripScopePrefix(String jtKey) {
        if (jtKey == null) {
            return "";
        }
        String k = jtKey.toLowerCase(Locale.ROOT);
        if (k.startsWith("poi-") || k.startsWith("npc-")) {
            return k.substring(4);
        }
        return k;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    public static List<JourneyGuidanceCatalog.Destination> fromWaypointChoices(
            Iterable<? extends JourneyWaypointChoiceView> choices) {
        List<JourneyGuidanceCatalog.Destination> out = new ArrayList<>();
        if (choices == null) {
            return out;
        }
        for (JourneyWaypointChoiceView c : choices) {
            if (c != null && c.jtKey() != null && !c.jtKey().isBlank()) {
                out.add(new JourneyGuidanceCatalog.Destination(c.jtKey(), c.label()));
            }
        }
        return out;
    }

    /** Minimal view so {@link Dialogue}'s private waypoint type can be passed without exposing it. */
    public interface JourneyWaypointChoiceView {
        String jtKey();
        String label();
    }
}
