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
 * Lets the optional LLM trigger Journey navigation via a structured {@code JOURNEY:name_id} line
 * and fuzzy matching against the same destination catalog used by the guidance menu.
 */
public final class JourneyLlmBridge {

    private static final Pattern JOURNEY_DIRECTIVE = Pattern.compile(
            "(?im)^\\s*JOURNEY\\s*:\\s*(\\S+)\\s*$");

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
        block.append("When the player asks to go somewhere, visit a place, meet a character, or explore a region, ");
        block.append("and you can identify a destination from the list below, end your reply with a line exactly:\n");
        block.append("JOURNEY:<name_id>\n");
        block.append("Use the name_id from the list (not the display label). ");
        block.append("The JOURNEY line is for the server only — do not explain it to the player.\n");
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
        Matcher matcher = JOURNEY_DIRECTIVE.matcher(llmText);
        String nameId = null;
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1).toLowerCase(Locale.ROOT);
        }
        if (last != null) {
            nameId = last;
        }
        String display = JOURNEY_DIRECTIVE.matcher(llmText).replaceAll("").trim();
        return new ParsedReply(display, nameId);
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
