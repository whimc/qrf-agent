package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.dialoguetemplate.Dialogue;
import edu.whimc.overworld_agent.dialoguetemplate.models.Chatbot;
import edu.whimc.overworld_agent.dialoguetemplate.models.LlmProvider;
import edu.whimc.overworld_agent.llm.context.AgentChatContextItem;
import edu.whimc.overworld_agent.llm.context.AgentChatEvent;
import edu.whimc.overworld_agent.llm.context.NpcContextProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class ChatCommand extends AbstractSubCommand implements Listener {

    private static final String START_COMMAND = "/agent chat test";
    private static final String END_COMMAND = "/agent chat end";

    private static final int MAX_HISTORY_MESSAGES = 10;

    private final Map<UUID, ActiveChatSession> activeSessions = new ConcurrentHashMap<>();

    public ChatCommand(OverworldAgent plugin, String baseCommand, String subCommand) {
        super(plugin, baseCommand, subCommand);
        super.description("Opens a dialogue menu to chat to your agent");
        super.arguments("");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            startInteractiveLlmChat(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("end")) {
            endInteractiveLlmChat(sender);
            return true;
        }

        Player player;
        boolean text = true;
        boolean embodied = false;

        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player");
            return true;
        } else {
            player = (Player) sender;
        }

        // Single merged menu: guide options (guidance, scores, discussion, edit) plus the
        // builder submenu (templates, base feedback) — no /agents chat_type switch needed.
        plugin.ensureAgentEdits(player);
        Dialogue dialogue = new Dialogue(plugin, player, text, embodied);
        dialogue.doDialogue();

        return true;
    }

    private void startInteractiveLlmChat(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player");
            return;
        }

        Player player = (Player) sender;
        LlmProvider provider = plugin.getLlmProvider();

        if (provider == null) {
            sender.sendMessage("LLM provider is null.");
            plugin.getLogger().warning("[OverworldAgent][LLM chat] Could not start chat: LLM provider is null.");
            return;
        }

        if (!provider.isConfigured()) {
            sender.sendMessage("LLM provider is not configured. Check config.yml.");
            plugin.getLogger().warning("[OverworldAgent][LLM chat] Could not start chat: LLM provider is not configured.");
            return;
        }

        UUID playerId = player.getUniqueId();

        ActiveChatSession session = new ActiveChatSession(
                UUID.randomUUID().toString(),
                playerId.toString() + "-" + System.currentTimeMillis()
        );

        activeSessions.put(playerId, session);

        player.sendMessage("LLM chat mode started. Press T and type your message.");
        player.sendMessage("Type exit, quit, or stop to end this chat mode. You can also use /agent chat end.");

        plugin.getLogger().info(
                "[OverworldAgent][LLM chat] Started interactive chat. " +
                        "player=" + player.getName() +
                        ", conversationId=" + session.conversationId()
        );
    }

    private void endInteractiveLlmChat(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player");
            return;
        }

        Player player = (Player) sender;
        ActiveChatSession removed = activeSessions.remove(player.getUniqueId());

        if (removed == null) {
            player.sendMessage("You are not currently in LLM chat mode.");
        } else {
            player.sendMessage("LLM chat mode ended.");
            plugin.getLogger().info(
                    "[OverworldAgent][LLM chat] Ended interactive chat. " +
                            "player=" + player.getName() +
                            ", conversationId=" + removed.conversationId()
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ActiveChatSession session = activeSessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage();

        Bukkit.getScheduler().runTask(plugin, () -> handleInteractiveChatMessage(player, session, message));
    }

    private void handleInteractiveChatMessage(Player player, ActiveChatSession session, String userMessage) {
        String normalized = userMessage.trim();

        if (normalized.isEmpty()) {
            player.sendMessage("Please type a message, or type exit to end LLM chat mode.");
            return;
        }

        if (isExitMessage(normalized)) {
            activeSessions.remove(player.getUniqueId());
            player.sendMessage("LLM chat mode ended.");
            return;
        }

        if (session.isWaitingForResponse()) {
            player.sendMessage("Please wait for the previous LLM response before sending another message.");
            return;
        }

        // Echo the user message privately because the public chat event is cancelled.
        player.sendMessage("You: " + normalized);

        session.setWaitingForResponse(true);

        runLlmChatTurn(player, session, normalized);
    }

    private void runLlmChatTurn(Player player, ActiveChatSession session, String userMessage) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String conversationId = session.conversationId();
        String turnId = UUID.randomUUID().toString();
        int turnIndex = session.nextTurnIndex();

        long requestStartedAt = System.currentTimeMillis();
        long startedAtNanos = System.nanoTime();

        String playerUuid = player.getUniqueId().toString();
        String username = player.getName();
        String playerResearchId = playerUuid; // Replace later with a de-identified research ID if needed.
        String sessionId = session.sessionId();
        String worldName = player.getWorld().getName();
        // Research-log label; the old Guide/Builder mode switch was removed (menus are merged).
        String agentType = "GUIDE";
        String agentName = "interactive-chat-agent";

        String providerName = plugin.getConfig().getString("llm.provider", "unknown");
        String modelName = plugin.getConfig().getString("llm.model", "unknown");
        boolean ragEnabled = plugin.getConfig().getBoolean("llm.rag.enabled", false);

        logStage(traceId, "START", "Received interactive chat message.");
        logStage(traceId, "REQUEST", "User message: \"" + userMessage + "\"");
        logStage(traceId, "CONTEXT",
                "uuid=" + playerUuid +
                        ", username=" + username +
                        ", world=" + worldName +
                        ", agentType=" + agentType +
                        ", conversationId=" + conversationId +
                        ", turnIndex=" + turnIndex);

        LlmProvider provider = plugin.getLlmProvider();

        if (provider == null) {
            String error = "LLM provider is null.";
            logStage(traceId, "ERROR", error);

            session.setWaitingForResponse(false);

            storeResearchTurnWithContextItems(
                    conversationId,
                    turnId,
                    turnIndex,
                    requestStartedAt,
                    playerUuid,
                    username,
                    playerResearchId,
                    sessionId,
                    worldName,
                    agentType,
                    agentName,
                    "chat_message",
                    userMessage,
                    providerName,
                    modelName,
                    null,
                    ragEnabled,
                    requestStartedAt,
                    null,
                    null,
                    "FAILED",
                    error,
                    null,
                    List.of(),
                    List.of(buildSimpleEvent(turnId, requestStartedAt, "llm_provider_null", error))
            );

            player.sendMessage(error);
            return;
        }

        if (!provider.isConfigured()) {
            String error = "LLM provider is not configured.";
            logStage(traceId, "ERROR", error);

            session.setWaitingForResponse(false);

            storeResearchTurnWithContextItems(
                    conversationId,
                    turnId,
                    turnIndex,
                    requestStartedAt,
                    playerUuid,
                    username,
                    playerResearchId,
                    sessionId,
                    worldName,
                    agentType,
                    agentName,
                    "chat_message",
                    userMessage,
                    providerName,
                    modelName,
                    null,
                    ragEnabled,
                    requestStartedAt,
                    null,
                    null,
                    "NOT_CONFIGURED",
                    error,
                    null,
                    List.of(),
                    List.of(buildSimpleEvent(turnId, requestStartedAt, "llm_provider_not_configured", error))
            );

            player.sendMessage("LLM provider is not configured. Check config.yml.");
            return;
        }

        List<AgentChatContextItem> contextItems = List.of();
        String npcPromptContext = "";

        boolean npcContextEnabled = plugin.getConfig().getBoolean("llm.npc-context.enabled", true);

        if (npcContextEnabled) {
            try {
                double npcContextRadius = plugin.getConfig().getDouble("llm.npc-context.radius", 25.0);
                int npcContextMaxItems = plugin.getConfig().getInt("llm.npc-context.max-items", 3);

                NpcContextProvider npcContextProvider = new NpcContextProvider(plugin);

                contextItems = npcContextProvider.getNearbyNpcContext(
                        player,
                        turnId,
                        npcContextMaxItems,
                        npcContextRadius
                );

                npcPromptContext = npcContextProvider.formatForPrompt(contextItems);

                logStage(traceId, "NPC_CONTEXT",
                        "Collected " + contextItems.size() +
                                " nearby NPC context items. Prompt context length=" +
                                npcPromptContext.length());

            } catch (Exception e) {
                logStage(traceId, "NPC_CONTEXT_ERROR",
                        "Failed to collect NPC context. Continuing without NPC context. " +
                                e.getClass().getSimpleName() + ": " + e.getMessage());

                contextItems = List.of();
                npcPromptContext = "";
            }
        } else {
            logStage(traceId, "NPC_CONTEXT", "NPC context is disabled.");
        }

        String baseSystemPrompt = plugin.getConfig().getString(
                "llm.system-prompt",
                "You are a friendly in-game science education assistant. Answer clearly and briefly; keep content appropriate for students."
        );

        String systemPrompt;

        try {
            logStage(traceId, "PROMPT_BASE",
                    "Base system prompt length: " + baseSystemPrompt.length() + " characters");

            logStage(traceId, "RAG_CONTEXT", "Starting system prompt augmentation.");
            systemPrompt = plugin.augmentLlmSystemPrompt(baseSystemPrompt);

            if (!npcPromptContext.isBlank()) {
                systemPrompt = systemPrompt + npcPromptContext;
            }

            logStage(traceId, "RAG_CONTEXT",
                    "Finished system prompt augmentation. Final system prompt length: " +
                            systemPrompt.length() + " characters");

        } catch (Exception e) {
            String error = "Failed while preparing system prompt/context: " +
                    e.getClass().getSimpleName() + ": " + e.getMessage();

            logStage(traceId, "ERROR", error);

            session.setWaitingForResponse(false);

            storeResearchTurnWithContextItems(
                    conversationId,
                    turnId,
                    turnIndex,
                    requestStartedAt,
                    playerUuid,
                    username,
                    playerResearchId,
                    sessionId,
                    worldName,
                    agentType,
                    agentName,
                    "chat_message",
                    userMessage,
                    providerName,
                    modelName,
                    null,
                    ragEnabled,
                    requestStartedAt,
                    null,
                    null,
                    "FAILED",
                    error,
                    null,
                    contextItems,
                    List.of(buildSimpleEvent(turnId, requestStartedAt, "prompt_preparation_failed", error))
            );

            player.sendMessage("LLM chat failed while preparing context.");
            return;
        }

        String llmUserMessage = buildUserMessageWithHistory(session, userMessage);

        final List<AgentChatContextItem> finalContextItems = contextItems;
        final String finalSystemPrompt = systemPrompt;
        final String finalLlmUserMessage = llmUserMessage;
        final String systemPromptHash = sha256OrNull(systemPrompt);

        player.sendMessage("Thinking...");

        CompletableFuture
                .supplyAsync(() -> {
                    long llmStartedAtNanos = System.nanoTime();

                    try {
                        logStage(traceId, "LLM_CALL",
                                "Calling provider.complete(...) on thread: " +
                                        Thread.currentThread().getName());

                        Chatbot chatbot = new Chatbot(finalLlmUserMessage);
                        String response = chatbot.generateLlmReply(provider, finalSystemPrompt);

                        logStage(traceId, "LLM_RESPONSE",
                                "LLM call completed in " +
                                        elapsedMs(llmStartedAtNanos) +
                                        " ms. Response length: " +
                                        (response == null ? "null" : response.length() + " characters"));

                        return response;

                    } catch (Exception e) {
                        logStage(traceId, "LLM_ERROR",
                                "LLM call failed after " +
                                        elapsedMs(llmStartedAtNanos) +
                                        " ms: " +
                                        e.getClass().getSimpleName() +
                                        ": " +
                                        e.getMessage());

                        throw new CompletionException(e);
                    }
                })
                .thenAccept(response -> Bukkit.getScheduler().runTask(plugin, () -> {
                    long responseReceivedAt = System.currentTimeMillis();
                    int latencyMs = (int) (responseReceivedAt - requestStartedAt);

                    String trimmed = response == null ? "" : response.trim();

                    session.setWaitingForResponse(false);

                    if (trimmed.isEmpty()) {
                        String status = "EMPTY_RESPONSE";
                        String error = "LLM returned no response.";

                        logStage(traceId, status, error);

                        List<AgentChatEvent> events = List.of(
                                buildLlmRequestPayloadEvent(
                                        turnId,
                                        requestStartedAt,
                                        conversationId,
                                        traceId,
                                        providerName,
                                        modelName,
                                        finalSystemPrompt,
                                        finalLlmUserMessage,
                                        ragEnabled,
                                        finalContextItems
                                ),
                                buildLlmResponsePayloadEvent(
                                        turnId,
                                        responseReceivedAt,
                                        traceId,
                                        null,
                                        status,
                                        latencyMs,
                                        error
                                )
                        );

                        storeResearchTurnWithContextItems(
                                conversationId,
                                turnId,
                                turnIndex,
                                requestStartedAt,
                                playerUuid,
                                username,
                                playerResearchId,
                                sessionId,
                                worldName,
                                agentType,
                                agentName,
                                "chat_message",
                                userMessage,
                                providerName,
                                modelName,
                                systemPromptHash,
                                ragEnabled,
                                requestStartedAt,
                                responseReceivedAt,
                                latencyMs,
                                status,
                                error,
                                null,
                                finalContextItems,
                                events
                        );

                        player.sendMessage("LLM returned no response.");

                    } else {
                        String status = "SUCCESS";

                        session.addMessage("user", userMessage);
                        session.addMessage("assistant", trimmed);

                        logStage(traceId, "DISPLAY",
                                "Displaying response to player. Trimmed response length: " +
                                        trimmed.length() + " characters");

                        List<AgentChatEvent> events = List.of(
                                buildLlmRequestPayloadEvent(
                                        turnId,
                                        requestStartedAt,
                                        conversationId,
                                        traceId,
                                        providerName,
                                        modelName,
                                        finalSystemPrompt,
                                        finalLlmUserMessage,
                                        ragEnabled,
                                        finalContextItems
                                ),
                                buildLlmResponsePayloadEvent(
                                        turnId,
                                        responseReceivedAt,
                                        traceId,
                                        trimmed,
                                        status,
                                        latencyMs,
                                        null
                                )
                        );

                        storeResearchTurnWithContextItems(
                                conversationId,
                                turnId,
                                turnIndex,
                                requestStartedAt,
                                playerUuid,
                                username,
                                playerResearchId,
                                sessionId,
                                worldName,
                                agentType,
                                agentName,
                                "chat_message",
                                userMessage,
                                providerName,
                                modelName,
                                systemPromptHash,
                                ragEnabled,
                                requestStartedAt,
                                responseReceivedAt,
                                latencyMs,
                                status,
                                null,
                                trimmed,
                                finalContextItems,
                                events
                        );

                        player.sendMessage("Agent: " + trimmed);
                    }

                    logStage(traceId, "DONE",
                            "Finished interactive chat turn in " + elapsedMs(startedAtNanos) + " ms.");
                }))
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        long responseReceivedAt = System.currentTimeMillis();
                        int latencyMs = (int) (responseReceivedAt - requestStartedAt);

                        session.setWaitingForResponse(false);

                        Throwable root = unwrapCompletionException(ex);

                        String status = "FAILED";
                        String error = root.getClass().getSimpleName() + ": " + root.getMessage();

                        logStage(traceId, "FAILED",
                                "Request failed after " +
                                        elapsedMs(startedAtNanos) +
                                        " ms: " +
                                        error);

                        List<AgentChatEvent> events = List.of(
                                buildLlmRequestPayloadEvent(
                                        turnId,
                                        requestStartedAt,
                                        conversationId,
                                        traceId,
                                        providerName,
                                        modelName,
                                        finalSystemPrompt,
                                        finalLlmUserMessage,
                                        ragEnabled,
                                        finalContextItems
                                ),
                                buildLlmResponsePayloadEvent(
                                        turnId,
                                        responseReceivedAt,
                                        traceId,
                                        null,
                                        status,
                                        latencyMs,
                                        error
                                )
                        );

                        storeResearchTurnWithContextItems(
                                conversationId,
                                turnId,
                                turnIndex,
                                requestStartedAt,
                                playerUuid,
                                username,
                                playerResearchId,
                                sessionId,
                                worldName,
                                agentType,
                                agentName,
                                "chat_message",
                                userMessage,
                                providerName,
                                modelName,
                                systemPromptHash,
                                ragEnabled,
                                requestStartedAt,
                                responseReceivedAt,
                                latencyMs,
                                status,
                                error,
                                null,
                                finalContextItems,
                                events
                        );

                        player.sendMessage("LLM chat failed: " + root.getMessage());
                    });

                    return null;
                });
    }

    private boolean isExitMessage(String message) {
        return message.equalsIgnoreCase("exit")
                || message.equalsIgnoreCase("quit")
                || message.equalsIgnoreCase("stop")
                || message.equalsIgnoreCase("end");
    }

    private String buildUserMessageWithHistory(ActiveChatSession session, String currentUserMessage) {
        List<ChatHistoryMessage> history = session.historySnapshot();

        if (history.isEmpty()) {
            return "Current player message:\n" + currentUserMessage;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("Recent conversation history:\n");

        for (ChatHistoryMessage message : history) {
            if ("assistant".equals(message.role())) {
                builder.append("Assistant: ");
            } else {
                builder.append("User: ");
            }

            builder.append(message.content()).append("\n");
        }

        builder.append("\nCurrent player message:\n");
        builder.append(currentUserMessage);

        return builder.toString();
    }

    private void storeResearchTurnWithContextItems(
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
            String providerName,
            String modelName,
            String systemPromptHash,
            boolean ragEnabled,
            long requestStartedAt,
            Long responseReceivedAt,
            Integer latencyMs,
            String status,
            String errorMessage,
            String assistantResponse,
            List<AgentChatContextItem> contextItems,
            List<AgentChatEvent> events
    ) {
        if (plugin.getQueryer() == null) {
            plugin.getLogger().warning("[OverworldAgent][ResearchDB] Queryer is null; cannot store chat research turn.");
            return;
        }

        plugin.getQueryer().storeAgentChatResearchTurnWithContextItems(
                conversationId,
                turnId,
                turnIndex,
                time,
                playerUuid,
                username,
                playerResearchId,
                sessionId,
                worldName,
                agentType,
                agentName,
                command,
                userMessage,
                assistantResponse,
                providerName,
                modelName,
                "default",
                systemPromptHash,
                ragEnabled,
                userMessage,
                requestStartedAt,
                responseReceivedAt,
                latencyMs,
                status,
                errorMessage,
                contextItems == null ? List.of() : contextItems,
                events == null ? List.of() : events
        );
    }

    private AgentChatEvent buildSimpleEvent(
            String turnId,
            long time,
            String eventType,
            String message
    ) {
        String payload =
                "{" +
                        "\"event_type\":\"" + jsonEscape(eventType) + "\"," +
                        "\"message\":\"" + jsonEscape(message) + "\"" +
                        "}";

        return new AgentChatEvent(turnId, time, eventType, payload);
    }

    private AgentChatEvent buildLlmRequestPayloadEvent(
            String turnId,
            long time,
            String conversationId,
            String traceId,
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
                        "\"command\":\"chat_message\"," +
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

        return new AgentChatEvent(
                turnId,
                time,
                "llm_request_payload",
                payload
        );
    }

    private AgentChatEvent buildLlmResponsePayloadEvent(
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

        return new AgentChatEvent(
                turnId,
                time,
                "llm_response_payload",
                payload
        );
    }

    private String jsonEscape(String value) {
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

    private void logStage(String traceId, String stage, String message) {
        plugin.getLogger().info("[OverworldAgent][LLM chat " + traceId + "][" + stage + "] " + message);
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }

    private String sha256OrNull(String value) {
        if (value == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            plugin.getLogger().warning("[OverworldAgent][LLM chat] Could not hash system prompt: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("test", "end");
        }

        return Arrays.asList();
    }

    private static final class ActiveChatSession {
        private final String conversationId;
        private final String sessionId;
        private final List<ChatHistoryMessage> history = new ArrayList<>();

        private int turnIndex = 0;
        private boolean waitingForResponse = false;

        private ActiveChatSession(String conversationId, String sessionId) {
            this.conversationId = conversationId;
            this.sessionId = sessionId;
        }

        private String conversationId() {
            return conversationId;
        }

        private String sessionId() {
            return sessionId;
        }

        private int nextTurnIndex() {
            turnIndex++;
            return turnIndex;
        }

        private boolean isWaitingForResponse() {
            return waitingForResponse;
        }

        private void setWaitingForResponse(boolean waitingForResponse) {
            this.waitingForResponse = waitingForResponse;
        }

        private void addMessage(String role, String content) {
            history.add(new ChatHistoryMessage(role, content));

            while (history.size() > MAX_HISTORY_MESSAGES) {
                history.remove(0);
            }
        }

        private List<ChatHistoryMessage> historySnapshot() {
            return new ArrayList<>(history);
        }
    }

    private record ChatHistoryMessage(String role, String content) {
    }
}