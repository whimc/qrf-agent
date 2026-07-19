package edu.whimc.overworld_agent.utils.sql;


import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.dialoguetemplate.Interaction;
import edu.whimc.overworld_agent.dialoguetemplate.JourneyGuidanceCatalog;
import edu.whimc.overworld_agent.dialoguetemplate.models.BuildTemplate;
import edu.whimc.overworld_agent.llm.context.AgentChatContextItem;
import edu.whimc.overworld_agent.llm.context.AgentChatEvent;
import edu.whimc.overworld_agent.llm.context.LearnerActivityContextProvider;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.ObservationRow;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.PositionSample;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.PositionSummary;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.ProgressScore;
import edu.whimc.overworld_agent.llm.context.LearnerActivitySnapshot.ScienceToolRow;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles storing agent data
 *
 * @author Sam
 */
public class Queryer {

    /**
     * Query for inserting an observation into the database.
     */
    private static final String QUERY_SAVE_NPC =
            "INSERT INTO whimc_agents " +
                    "(time, uuid, username, command, agent_name, agent_skin) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
    /**
     * Query for inserting a progress entry into the database.
     */
    private static final String QUERY_SAVE_SCIENCE_INQUIRY =
            "INSERT INTO whimc_dialog_science " +
                    "(uuid, username, world, time, science_inquiry, agent_response) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
    /**
     * Query for inserting a progress entry into the database.
     */
    private static final String QUERY_SAVE_INTERACTION =
            "INSERT INTO whimc_dialogue_interaction" +
                    "(uuid, username, world, time, interaction, x, y, z) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Query for inserting a build interaction entry into the database.
     */
    private static final String QUERY_SAVE_BUILD_INTERACTION =
            "INSERT INTO whimc_dialogue_builder_interaction" +
                    "(uuid, username, world, x, y, z, time, interaction, build_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Query for inserting a template entry into the database.
     */
    private static final String QUERY_SAVE_TEMPLATE =
            "INSERT INTO whimc_build_templates" +
                    "(uuid, username, template_name, start_time, end_time) " +
                    "VALUES (?, ?, ?, ?, ?)";
    //Query for getting build template during session from the database.
    private static final String QUERY_GET_BUILD_TEMPLATE =
            "SELECT * FROM whimc_build_templates "+
                    "WHERE rowid = ?;";

    //Query for getting science tool use during session from the database.
    private static final String QUERY_GET_SESSION_CONVERSATION =
            "SELECT * FROM whimc_dialog_science "+
                    "WHERE uuid=? AND time > ? ";
    /**
     * Query for saving an agent chat conversation.
     */
    private static final String QUERY_SAVE_AGENT_CHAT_CONVERSATION =
            "INSERT INTO whimc_agent_chat_conversations " +
                    "(conversation_id, time, uuid, username, player_research_id, session_id, world_name, agent_type, research_allowed) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "time = VALUES(time), " +
                    "uuid = VALUES(uuid), " +
                    "username = VALUES(username), " +
                    "player_research_id = VALUES(player_research_id), " +
                    "session_id = VALUES(session_id), " +
                    "world_name = VALUES(world_name), " +
                    "agent_type = VALUES(agent_type), " +
                    "research_allowed = VALUES(research_allowed)";

    /**
     * Query for saving an agent chat turn.
     */
    private static final String QUERY_SAVE_AGENT_CHAT_TURN =
            "INSERT INTO whimc_agent_chat_turns " +
                    "(turn_id, conversation_id, turn_index, time, uuid, username, player_research_id, session_id, " +
                    "world_name, agent_type, agent_name, command, user_message, assistant_response, " +
                    "llm_provider, llm_model, system_prompt_version, system_prompt_hash, rag_enabled, rag_query, " +
                    "request_started_at, response_received_at, latency_ms, status, error_message) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String QUERY_SAVE_AGENT_CHAT_CONTEXT_ITEM =
            "INSERT INTO whimc_agent_chat_context_items " +
                    "(turn_id, time, context_rank, context_type, source_id, source_title, world_name, " +
                    "x, y, z, distance, trait_type, context_text, context_hash, score) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String QUERY_SAVE_AGENT_CHAT_EVENT =
            "INSERT INTO whimc_agent_chat_events " +
                    "(turn_id, time, event_type, event_payload) " +
                    "VALUES (?, ?, ?, ?)";

    private static final String QUERY_LIST_POI_REGIONS =
            "SELECT r.id, w.name AS world_name FROM %sregion r "
                    + "INNER JOIN %sworld w ON r.world_id = w.id "
                    + "WHERE r.id LIKE ? AND w.name LIKE ?";

    private static final String QUERY_LATEST_PROGRESS =
            "SELECT observation, science_tools, exploration, quest, score, poi_exploration, time "
                    + "FROM whimc_progress WHERE uuid = ? ORDER BY time DESC LIMIT 1";

    private static final String QUERY_OWN_OBSERVATIONS =
            "SELECT observation_color_stripped, observation, x, y, z, world, time, category, username "
                    + "FROM whimc_observations WHERE uuid = ? AND active = 1 "
                    + "AND (expiration IS NULL OR expiration <= 0 OR expiration > ?) "
                    + "ORDER BY time DESC LIMIT ?";

    /** Any active floating observation near the player (own or peer). */
    private static final String QUERY_NEARBY_OBSERVATIONS =
            "SELECT observation_color_stripped, observation, x, y, z, world, time, category, username, uuid "
                    + "FROM whimc_observations WHERE LOWER(world) = LOWER(?) AND active = 1 "
                    + "AND (expiration IS NULL OR expiration <= 0 OR expiration > ?) "
                    + "AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? "
                    + "ORDER BY time DESC LIMIT ?";

    private static final String QUERY_RECENT_SCIENCE_TOOLS =
            "SELECT tool, measurement, x, y, z, world, time FROM whimc_sciencetools "
                    + "WHERE uuid = ? ORDER BY time DESC LIMIT ?";

    private static final String QUERY_RECENT_POSITIONS =
            "SELECT x, y, z, world, biome, time FROM whimc_player_positions "
                    + "WHERE uuid = ? AND time >= ? ORDER BY time DESC LIMIT ?";

    private final OverworldAgent plugin;
    private final MySQLConnection sqlConnection;

    public Queryer(OverworldAgent plugin, Consumer<Queryer> callback) {
        this.plugin = plugin;
        this.sqlConnection = new MySQLConnection(plugin);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean success = sqlConnection.initialize();
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(success ? this : null));
        });
    }

    /**
     * Generated a PreparedStatement for saving a new npc.
     *
     * @param connection MySQL Connection
     * @param player player who spawned the agent
     * @param command used to spawn agent
     * @param agentName name of the agent
     * @param agentSkin skin of the agent
     * @return PreparedStatement
     * @throws SQLException
     */
    private PreparedStatement getStatement(Connection connection, Player player, String command, String agentName, String agentSkin) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(QUERY_SAVE_NPC, Statement.RETURN_GENERATED_KEYS);

        statement.setLong(1, System.currentTimeMillis());
        statement.setString(2, player.getUniqueId().toString());
        statement.setString(3, player.getName());
        statement.setString(4, command);
        statement.setString(5, agentName);
        statement.setString(6, agentSkin);
        return statement;
    }


    /**
     * Stores an observation into the database and returns the obervation's ID
     * @param player player who spawned the agent
     * @param command used to spawn agent
     * @param agentName name of the agent
     * @param agentSkin skin of the agent
     * @param callback    Function to call once the observation has been saved
     */
    public void storeNewAgent(Player player, String command, String agentName, String agentSkin, Consumer<Integer> callback) {
        async(() -> {

            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = getStatement(connection, player, command, agentName, agentSkin)) {
                    String query = statement.toString().substring(statement.toString().indexOf(" ") + 1);
                    statement.executeUpdate();

                    try (ResultSet idRes = statement.getGeneratedKeys()) {
                        idRes.next();
                        int id = idRes.getInt(1);

                        sync(callback, id);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    /**
     * Generated a PreparedStatement for saving a new progress session.
     * @param connection MySQL Connection
     * @param player player using command
     * @param inquiry student inquiry
     * @param response agent response to player
     * @return PreparedStatement
     * @throws SQLException
     */
    private PreparedStatement insertScienceInquiry(Connection connection, Player player, String inquiry, String response) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(QUERY_SAVE_SCIENCE_INQUIRY, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, player.getUniqueId().toString());
        statement.setString(2, player.getName());
        statement.setString(3, player.getWorld().getName());
        statement.setLong(4, System.currentTimeMillis());
        statement.setString(5, inquiry);
        statement.setString(6, response);
        return statement;
    }

    /**
     * Stores a progress command into the database and returns the obervation's ID
     * @param player player using agent
     * @param inquiry student inquiry to agent
     * @param response agent response to player
     * @param callback    Function to call once the observation has been saved
     */
    public void storeNewScienceInquiry(Player player, String inquiry, String response, Consumer<Integer> callback) {
        async(() -> {

            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = insertScienceInquiry(connection, player, inquiry, response)) {
                    String query = statement.toString().substring(statement.toString().indexOf(" ") + 1);
                    statement.executeUpdate();

                    try (ResultSet idRes = statement.getGeneratedKeys()) {
                        idRes.next();
                        int id = idRes.getInt(1);

                        sync(callback, id);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Generated a PreparedStatement for saving a new progress session.
     * @param connection MySQL Connection
     * @param interaction player interaction with agent
     * @return PreparedStatement
     * @throws SQLException
     */
    private PreparedStatement insertInteraction(Connection connection, Interaction interaction) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(QUERY_SAVE_INTERACTION, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, interaction.getPlayer().getUniqueId().toString());
        statement.setString(2, interaction.getPlayer().getName());
        statement.setString(3, interaction.getPlayer().getWorld().getName());
        statement.setLong(4, interaction.getTime().getTime());
        statement.setString(5, interaction.getInteraction());
        statement.setDouble(6, interaction.getLocation().getX());
        statement.setDouble(7, interaction.getLocation().getY());
        statement.setDouble(8, interaction.getLocation().getZ());
        return statement;
    }

    /**
     * Stores a progress command into the database and returns the obervation's ID
     * @param interaction player interaction with agent
     * @param callback    Function to call once the observation has been saved
     */
    public void storeNewInteraction(Interaction interaction, Consumer<Integer> callback) {
        async(() -> {

            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = insertInteraction(connection, interaction)) {
                    statement.executeUpdate();

                    try (ResultSet idRes = statement.getGeneratedKeys()) {
                        idRes.next();
                        int id = idRes.getInt(1);

                        sync(callback, id);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }


    /**
     * Method to get skills for a player
     * @param player Player to get the skills for
     * @param callback callback to signify process completion
     */
    public void getSessionConversation(Player player, Long sessionStart, Consumer callback){
        HashMap<String, List<String>> conversation = new HashMap<>();
        async(() -> {
            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement(QUERY_GET_SESSION_CONVERSATION)) {
                    statement.setString(1, player.getUniqueId().toString());
                    statement.setLong(2, sessionStart);
                    ResultSet results = statement.executeQuery();
                    while (results.next()) {
                        String world = results.getString("world");
                        String input = results.getString("science_inquiry");
                        String response = results.getString("agent_response");
                        if(!conversation.containsKey(world)){
                            conversation.put(world,new ArrayList<>());
                        }
                        conversation.get(world).add(input);
                        conversation.get(world).add(response);
                    }
                    sync(callback,conversation);
                }
            } catch (SQLException exc) {
                exc.printStackTrace();
            }
        });
    }

    /**
     * Generated a PreparedStatement for saving a new progress session.
     * @param connection MySQL Connection
     * @param interaction player interaction with agent
     * @return PreparedStatement
     * @throws SQLException
     */
    private PreparedStatement insertBuildInteraction(Connection connection, Interaction interaction, int buildID) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(QUERY_SAVE_BUILD_INTERACTION, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, interaction.getPlayer().getUniqueId().toString());
        statement.setString(2, interaction.getPlayer().getName());
        statement.setString(3, interaction.getPlayer().getWorld().getName());
        statement.setDouble(4, interaction.getLocation().getX());
        statement.setDouble(5, interaction.getLocation().getY());
        statement.setDouble(6, interaction.getLocation().getZ());
        statement.setLong(7, interaction.getTime().getTime());
        statement.setString(8, interaction.getInteraction());
        statement.setInt(9, buildID);
        return statement;
    }

    /**
     * Stores a progress command into the database and returns the obervation's ID
     * @param interaction player interaction with agent
     * @param callback    Function to call once the observation has been saved
     */
    public void storeNewBuildInteraction(Interaction interaction, int buildID, Consumer<Integer> callback) {
        async(() -> {

            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = insertBuildInteraction(connection, interaction, buildID)) {
                    statement.executeUpdate();

                    try (ResultSet idRes = statement.getGeneratedKeys()) {
                        idRes.next();
                        int id = idRes.getInt(1);

                        sync(callback, id);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Generated a PreparedStatement for saving a new progress session.
     * @param connection MySQL Connection
     * @param bt player build template
     * @return PreparedStatement
     * @throws SQLException
     */
    private PreparedStatement insertTemplate(Connection connection, BuildTemplate bt) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(QUERY_SAVE_TEMPLATE, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, bt.getPlayer().getUniqueId().toString());
        statement.setString(2, bt.getPlayer().getName());
        statement.setString(3, bt.getName());
        statement.setLong(4, bt.getStartTime().getTime());
        statement.setLong(5, bt.getEndTime().getTime());
        return statement;
    }

    /**
     * Stores a progress command into the database and returns the obervation's ID
     * @param bt player build template
     * @param callback    Function to call once the observation has been saved
     */
    public void storeNewTemplate(BuildTemplate bt, Consumer<Integer> callback) {
        async(() -> {
            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = insertTemplate(connection, bt)) {
                    statement.executeUpdate();

                    try (ResultSet idRes = statement.getGeneratedKeys()) {
                        idRes.next();
                        int id = idRes.getInt(1);

                        sync(callback, id);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }


    /**
     * Method to get skills for a player
     * @param buildID id of template to retrieve
     * @param sender sender of command
     * @param callback callback to signify process completion
     */
    public void getBuildTemplate(int buildID, Player sender, Consumer callback){
        async(() -> {
            BuildTemplate template = null;
            try (Connection connection = this.sqlConnection.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement(QUERY_GET_BUILD_TEMPLATE)) {
                    statement.setInt(1, buildID);
                    ResultSet results = statement.executeQuery();
                    while (results.next()) {
                        String user = results.getString("username");
                        Player creator = Bukkit.getPlayer(user);
                        String templateName = results.getString("template_name");
                        Timestamp startTime = new Timestamp(results.getLong("start_time"));
                        Timestamp endTime = new Timestamp(results.getLong("end_time"));
                        template = new BuildTemplate(plugin, sender, templateName, startTime, endTime, creator);
                    }
                    sync(callback,template);
                }
            } catch (SQLException exc) {
                exc.printStackTrace();
            }
        });
    }

    public void storeAgentChatResearchTurn(
            String conversationId,
            String turnId,
            int turnIndex,
            long time,
            String uuid,
            String username,
            String playerResearchId,
            String sessionId,
            String worldName,
            String agentType,
            String agentName,
            String command,
            String userMessage,
            String assistantResponse,
            String llmProvider,
            String llmModel,
            String systemPromptVersion,
            String systemPromptHash,
            boolean ragEnabled,
            String ragQuery,
            long requestStartedAt,
            Long responseReceivedAt,
            Integer latencyMs,
            String status,
            String errorMessage
    ) {
        async(() -> {
            try (Connection connection = this.sqlConnection.getConnection()) {

                if (connection == null) {
                    plugin.getLogger().warning("[OverworldAgent][ResearchDB] Could not store agent chat turn: MySQL connection is null.");
                    return;
                }

                connection.setAutoCommit(false);

                try {
                    try (PreparedStatement conversationStatement =
                                 connection.prepareStatement(QUERY_SAVE_AGENT_CHAT_CONVERSATION)) {

                        conversationStatement.setString(1, conversationId);
                        conversationStatement.setLong(2, time);
                        conversationStatement.setString(3, uuid);
                        conversationStatement.setString(4, username);
                        conversationStatement.setString(5, playerResearchId);
                        conversationStatement.setString(6, sessionId);
                        conversationStatement.setString(7, worldName);
                        conversationStatement.setString(8, agentType);
                        conversationStatement.setBoolean(9, true);

                        conversationStatement.executeUpdate();
                    }

                    try (PreparedStatement turnStatement =
                                 connection.prepareStatement(QUERY_SAVE_AGENT_CHAT_TURN, Statement.RETURN_GENERATED_KEYS)) {

                        turnStatement.setString(1, turnId);
                        turnStatement.setString(2, conversationId);
                        turnStatement.setInt(3, turnIndex);
                        turnStatement.setLong(4, time);
                        turnStatement.setString(5, uuid);
                        turnStatement.setString(6, username);
                        turnStatement.setString(7, playerResearchId);
                        turnStatement.setString(8, sessionId);
                        turnStatement.setString(9, worldName);
                        turnStatement.setString(10, agentType);
                        turnStatement.setString(11, agentName);
                        turnStatement.setString(12, command);
                        turnStatement.setString(13, userMessage);
                        turnStatement.setString(14, assistantResponse);
                        turnStatement.setString(15, llmProvider);
                        turnStatement.setString(16, llmModel);
                        turnStatement.setString(17, systemPromptVersion);
                        turnStatement.setString(18, systemPromptHash);
                        turnStatement.setBoolean(19, ragEnabled);
                        turnStatement.setString(20, ragQuery);
                        turnStatement.setLong(21, requestStartedAt);

                        if (responseReceivedAt == null) {
                            turnStatement.setNull(22, Types.BIGINT);
                        } else {
                            turnStatement.setLong(22, responseReceivedAt);
                        }

                        if (latencyMs == null) {
                            turnStatement.setNull(23, Types.INTEGER);
                        } else {
                            turnStatement.setInt(23, latencyMs);
                        }

                        turnStatement.setString(24, status);
                        turnStatement.setString(25, errorMessage);

                        turnStatement.executeUpdate();
                    }

                    connection.commit();

                    plugin.getLogger().info(
                            "[OverworldAgent][ResearchDB] Stored agent chat research turn. " +
                                    "conversationId=" + conversationId +
                                    ", turnId=" + turnId +
                                    ", status=" + status
                    );

                } catch (SQLException e) {
                    connection.rollback();
                    throw e;

                } finally {
                    connection.setAutoCommit(true);
                }

            } catch (SQLException e) {
                plugin.getLogger().warning(
                        "[OverworldAgent][ResearchDB] Failed to store agent chat research turn: " +
                                e.getClass().getSimpleName() + ": " + e.getMessage()
                );
                e.printStackTrace();
            }
        });
    }

    public void storeAgentChatResearchTurnWithContextItems(
            String conversationId,
            String turnId,
            int turnIndex,
            long time,
            String uuid,
            String username,
            String playerResearchId,
            String sessionId,
            String worldName,
            String agentType,
            String agentName,
            String command,
            String userMessage,
            String assistantResponse,
            String llmProvider,
            String llmModel,
            String systemPromptVersion,
            String systemPromptHash,
            boolean ragEnabled,
            String ragQuery,
            long requestStartedAt,
            Long responseReceivedAt,
            Integer latencyMs,
            String status,
            String errorMessage,
            List<AgentChatContextItem> contextItems,
            List<AgentChatEvent> events
    ) {
        async(() -> {
            try (Connection connection = this.sqlConnection.getConnection()) {
                if (connection == null) {
                    plugin.getLogger().warning("[OverworldAgent][ResearchDB] Could not store chat turn/context: MySQL connection is null.");
                    return;
                }

                connection.setAutoCommit(false);

                try {
                    saveAgentChatConversation(
                            connection,
                            conversationId,
                            time,
                            uuid,
                            username,
                            playerResearchId,
                            sessionId,
                            worldName,
                            agentType
                    );

                    saveAgentChatTurn(
                            connection,
                            conversationId,
                            turnId,
                            turnIndex,
                            time,
                            uuid,
                            username,
                            playerResearchId,
                            sessionId,
                            worldName,
                            agentType,
                            agentName,
                            command,
                            userMessage,
                            assistantResponse,
                            llmProvider,
                            llmModel,
                            systemPromptVersion,
                            systemPromptHash,
                            ragEnabled,
                            ragQuery,
                            requestStartedAt,
                            responseReceivedAt,
                            latencyMs,
                            status,
                            errorMessage
                    );

                    if (contextItems != null && !contextItems.isEmpty()) {
                        saveAgentChatContextItems(connection, contextItems);
                    }

                    if (events != null && !events.isEmpty()) {
                        saveAgentChatEvents(connection, events);
                    }
                    connection.commit();

                    plugin.getLogger().info(
                            "[OverworldAgent][ResearchDB] Stored chat turn with context. " +
                                    "conversationId=" + conversationId +
                                    ", turnId=" + turnId +
                                    ", status=" + status +
                                    ", contextItems=" + (contextItems == null ? 0 : contextItems.size())
                    );

                } catch (SQLException e) {
                    connection.rollback();
                    throw e;

                } finally {
                    connection.setAutoCommit(true);
                }

            } catch (SQLException e) {
                plugin.getLogger().warning(
                        "[OverworldAgent][ResearchDB] Failed to store chat turn/context: " +
                                e.getClass().getSimpleName() + ": " + e.getMessage()
                );
                e.printStackTrace();
            }
        });
    }

    private void saveAgentChatConversation(
            Connection connection,
            String conversationId,
            long time,
            String uuid,
            String username,
            String playerResearchId,
            String sessionId,
            String worldName,
            String agentType
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(QUERY_SAVE_AGENT_CHAT_CONVERSATION)) {

            statement.setString(1, conversationId);
            statement.setLong(2, time);
            statement.setString(3, uuid);
            statement.setString(4, username);
            statement.setString(5, playerResearchId);
            statement.setString(6, sessionId);
            statement.setString(7, worldName);
            statement.setString(8, agentType);
            statement.setBoolean(9, true);

            statement.executeUpdate();
        }
    }

    private void saveAgentChatTurn(
            Connection connection,
            String conversationId,
            String turnId,
            int turnIndex,
            long time,
            String uuid,
            String username,
            String playerResearchId,
            String sessionId,
            String worldName,
            String agentType,
            String agentName,
            String command,
            String userMessage,
            String assistantResponse,
            String llmProvider,
            String llmModel,
            String systemPromptVersion,
            String systemPromptHash,
            boolean ragEnabled,
            String ragQuery,
            long requestStartedAt,
            Long responseReceivedAt,
            Integer latencyMs,
            String status,
            String errorMessage
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(QUERY_SAVE_AGENT_CHAT_TURN)) {

            statement.setString(1, turnId);
            statement.setString(2, conversationId);
            statement.setInt(3, turnIndex);
            statement.setLong(4, time);
            statement.setString(5, uuid);
            statement.setString(6, username);
            statement.setString(7, playerResearchId);
            statement.setString(8, sessionId);
            statement.setString(9, worldName);
            statement.setString(10, agentType);
            statement.setString(11, agentName);
            statement.setString(12, command);
            statement.setString(13, userMessage);
            statement.setString(14, assistantResponse);
            statement.setString(15, llmProvider);
            statement.setString(16, llmModel);
            statement.setString(17, systemPromptVersion);
            statement.setString(18, systemPromptHash);
            statement.setBoolean(19, ragEnabled);
            statement.setString(20, ragQuery);
            statement.setLong(21, requestStartedAt);

            if (responseReceivedAt == null) {
                statement.setNull(22, Types.BIGINT);
            } else {
                statement.setLong(22, responseReceivedAt);
            }

            if (latencyMs == null) {
                statement.setNull(23, Types.INTEGER);
            } else {
                statement.setInt(23, latencyMs);
            }

            statement.setString(24, status);
            statement.setString(25, errorMessage);

            statement.executeUpdate();
        }
    }

    private void saveAgentChatContextItems(
            Connection connection,
            List<AgentChatContextItem> items
    ) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(QUERY_SAVE_AGENT_CHAT_CONTEXT_ITEM)) {

            for (AgentChatContextItem item : items) {
                statement.setString(1, item.turnId());
                statement.setLong(2, item.time());
                statement.setInt(3, item.contextRank());
                statement.setString(4, item.contextType());
                statement.setString(5, item.sourceId());
                statement.setString(6, item.sourceTitle());
                statement.setString(7, item.worldName());

                if (item.x() == null) {
                    statement.setNull(8, Types.DOUBLE);
                } else {
                    statement.setDouble(8, item.x());
                }

                if (item.y() == null) {
                    statement.setNull(9, Types.DOUBLE);
                } else {
                    statement.setDouble(9, item.y());
                }

                if (item.z() == null) {
                    statement.setNull(10, Types.DOUBLE);
                } else {
                    statement.setDouble(10, item.z());
                }

                if (item.distance() == null) {
                    statement.setNull(11, Types.DOUBLE);
                } else {
                    statement.setDouble(11, item.distance());
                }

                statement.setString(12, item.traitType());
                statement.setString(13, item.contextText());
                statement.setString(14, item.contextHash());

                if (item.score() == null) {
                    statement.setNull(15, Types.DOUBLE);
                } else {
                    statement.setDouble(15, item.score());
                }

                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private void saveAgentChatEvents(
            Connection connection,
            List<AgentChatEvent> events
    ) throws SQLException {
        if (events == null || events.isEmpty()) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(QUERY_SAVE_AGENT_CHAT_EVENT)) {

            for (AgentChatEvent event : events) {
                statement.setString(1, event.turnId());
                statement.setLong(2, event.time());
                statement.setString(3, event.eventType());
                statement.setString(4, event.eventPayload());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private <T> void sync(Consumer<T> cons, T val) {
        Bukkit.getScheduler().runTask(this.plugin, () -> cons.accept(val));
    }

    private void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(this.plugin, runnable);
    }

    private void async(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, runnable);
    }

    /**
     * Loads learner activity from WHIMC research tables for LLM grounding.
     * Callback runs on the main thread. Missing tables yield empty sections (logged once per failure).
     */
    public void loadLearnerActivityContext(
            Player player,
            LearnerActivityContextProvider.QueryOptions options,
            Consumer<LearnerActivitySnapshot> callback
    ) {
        if (callback == null) {
            return;
        }
        if (player == null || options == null) {
            sync(callback, LearnerActivitySnapshot.empty());
            return;
        }

        final String uuid = player.getUniqueId().toString();
        final Location loc = player.getLocation();
        final String worldName = loc.getWorld() == null ? "" : loc.getWorld().getName();
        final double px = loc.getX();
        final double py = loc.getY();
        final double pz = loc.getZ();
        final long now = System.currentTimeMillis();

        async(() -> {
            ProgressScore progress = null;
            List<ObservationRow> ownObservations = List.of();
            List<ObservationRow> peerObservations = List.of();
            List<ScienceToolRow> scienceTools = List.of();
            PositionSummary positionSummary = null;

            try (Connection connection = this.sqlConnection.getConnection()) {
                if (connection == null) {
                    sync(callback, LearnerActivitySnapshot.empty());
                    return;
                }

                if (options.includeProgress()) {
                    progress = queryLatestProgress(connection, uuid);
                }
                if (options.maxOwnObservations() > 0) {
                    ownObservations = queryOwnObservations(connection, uuid, now, options.maxOwnObservations());
                }
                if (options.includePeerObservations() && options.maxPeerObservations() > 0 && !worldName.isBlank()) {
                    peerObservations = queryNearbyObservations(
                            connection,
                            uuid,
                            worldName,
                            px,
                            py,
                            pz,
                            now,
                            options.nearbyObservationRadius(),
                            options.maxPeerObservations());
                }
                if (options.maxScienceTools() > 0) {
                    scienceTools = queryRecentScienceTools(connection, uuid, options.maxScienceTools());
                }
                List<PositionSample> samples = queryRecentPositions(
                        connection,
                        uuid,
                        now - options.positionLookbackMs(),
                        options.positionRowLimit());
                positionSummary = LearnerActivityContextProvider.summarizePositions(samples, 5, 5);
            } catch (SQLException exc) {
                plugin.getLogger().warning("Failed to load learner activity context: " + exc.getMessage());
            }

            if (plugin.getConfig().getBoolean("llm.debug-log", false)) {
                plugin.getLogger().info(String.format(
                        Locale.ROOT,
                        "[OverworldAgent][LLM] activity-context load uuid=%s world=%s ownObs=%d nearbyObs=%d tools=%d positions=%s progress=%s",
                        uuid,
                        worldName,
                        ownObservations.size(),
                        peerObservations.size(),
                        scienceTools.size(),
                        positionSummary == null || positionSummary.isEmpty() ? "empty" : "ok",
                        progress == null ? "none" : "ok"));
            }

            sync(callback, new LearnerActivitySnapshot(
                    progress,
                    positionSummary,
                    ownObservations,
                    peerObservations,
                    scienceTools));
        });
    }

    private ProgressScore queryLatestProgress(Connection connection, String uuid) {
        try (PreparedStatement statement = connection.prepareStatement(QUERY_LATEST_PROGRESS)) {
            statement.setString(1, uuid);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                return new ProgressScore(
                        results.getDouble("observation"),
                        results.getDouble("science_tools"),
                        results.getDouble("exploration"),
                        results.getDouble("quest"),
                        results.getDouble("poi_exploration"),
                        results.getDouble("score"),
                        results.getLong("time"));
            }
        } catch (SQLException exc) {
            plugin.getLogger().warning("activity-context: whimc_progress query failed: " + exc.getMessage());
            return null;
        }
    }

    private List<ObservationRow> queryOwnObservations(Connection connection, String uuid, long now, int limit) {
        List<ObservationRow> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(QUERY_OWN_OBSERVATIONS)) {
            statement.setString(1, uuid);
            statement.setLong(2, now);
            statement.setInt(3, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    out.add(readObservation(results, null));
                }
            }
        } catch (SQLException exc) {
            plugin.getLogger().warning("activity-context: whimc_observations (own) query failed: " + exc.getMessage());
        }
        return out;
    }

    private List<ObservationRow> queryNearbyObservations(
            Connection connection,
            String uuid,
            String worldName,
            double px,
            double py,
            double pz,
            long now,
            double radius,
            int limit
    ) {
        List<ObservationRow> candidates = new ArrayList<>();
        // Wider recent fetch, then keep nearest (time-first SQL can miss a close older hologram).
        int fetchLimit = Math.max(limit * 8, 80);
        try (PreparedStatement statement = connection.prepareStatement(QUERY_NEARBY_OBSERVATIONS)) {
            statement.setString(1, worldName);
            statement.setLong(2, now);
            statement.setDouble(3, px - radius);
            statement.setDouble(4, px + radius);
            statement.setDouble(5, pz - radius);
            statement.setDouble(6, pz + radius);
            statement.setInt(7, fetchLimit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    double x = results.getDouble("x");
                    double y = results.getDouble("y");
                    double z = results.getDouble("z");
                    double dx = x - px;
                    double dy = y - py;
                    double dz = z - pz;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > radius) {
                        continue;
                    }
                    candidates.add(readObservation(results, distance));
                }
            }
        } catch (SQLException exc) {
            plugin.getLogger().warning("activity-context: whimc_observations (nearby) query failed: " + exc.getMessage());
            return List.of();
        }
        candidates.sort(Comparator.comparingDouble(o -> o.distance() == null ? Double.MAX_VALUE : o.distance()));
        if (candidates.size() <= limit) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, limit));
    }

    private ObservationRow readObservation(ResultSet results, Double distance) throws SQLException {
        String stripped = results.getString("observation_color_stripped");
        String raw = results.getString("observation");
        String text = (stripped != null && !stripped.isBlank()) ? stripped : raw;
        if (text != null) {
            text = ChatColor.stripColor(text.replace('&', ChatColor.COLOR_CHAR));
        }
        return new ObservationRow(
                text == null ? "" : text.trim(),
                results.getString("username"),
                results.getString("world"),
                results.getDouble("x"),
                results.getDouble("y"),
                results.getDouble("z"),
                results.getString("category"),
                results.getLong("time"),
                distance);
    }

    private List<ScienceToolRow> queryRecentScienceTools(Connection connection, String uuid, int limit) {
        List<ScienceToolRow> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(QUERY_RECENT_SCIENCE_TOOLS)) {
            statement.setString(1, uuid);
            statement.setInt(2, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    out.add(new ScienceToolRow(
                            results.getString("tool"),
                            results.getString("measurement"),
                            results.getString("world"),
                            results.getDouble("x"),
                            results.getDouble("y"),
                            results.getDouble("z"),
                            results.getLong("time")));
                }
            }
        } catch (SQLException exc) {
            plugin.getLogger().warning("activity-context: whimc_sciencetools query failed: " + exc.getMessage());
        }
        return out;
    }

    private List<PositionSample> queryRecentPositions(Connection connection, String uuid, long sinceTime, int limit) {
        List<PositionSample> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(QUERY_RECENT_POSITIONS)) {
            statement.setString(1, uuid);
            statement.setLong(2, sinceTime);
            statement.setInt(3, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    out.add(new PositionSample(
                            results.getInt("x"),
                            results.getInt("y"),
                            results.getInt("z"),
                            results.getString("world"),
                            results.getString("biome"),
                            results.getLong("time")));
                }
            }
        } catch (SQLException exc) {
            plugin.getLogger().warning("activity-context: whimc_player_positions query failed: " + exc.getMessage());
        }
        return out;
    }

    /**
     * WorldGuard MySQL POI regions ({@code poi-*} ids in {@code rg_region}) for worlds sharing a name prefix.
     */
    public void listPoiRegions(String worldNamePrefix, String poiRegionPrefix, String worldguardTablePrefix,
            Consumer<List<JourneyGuidanceCatalog.Destination>> callback) {
        if (callback == null) {
            return;
        }
        String worldPrefix = worldNamePrefix == null ? "" : worldNamePrefix.trim();
        String poiPrefix = poiRegionPrefix == null ? "poi-" : poiRegionPrefix;
        String tablePrefix = worldguardTablePrefix == null ? "rg_" : worldguardTablePrefix;
        async(() -> {
            List<JourneyGuidanceCatalog.Destination> out = new ArrayList<>();
            try (Connection connection = this.sqlConnection.getConnection()) {
                if (connection == null) {
                    sync(callback, out);
                    return;
                }
                String sql = String.format(QUERY_LIST_POI_REGIONS, tablePrefix, tablePrefix);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, poiPrefix + "%");
                    statement.setString(2, worldPrefix + "%");
                    try (ResultSet results = statement.executeQuery()) {
                        Map<String, JourneyGuidanceCatalog.Destination> byKey = new LinkedHashMap<>();
                        while (results.next()) {
                            String id = results.getString("id");
                            if (id == null || id.isBlank()) {
                                continue;
                            }
                            String key = id.toLowerCase(Locale.ROOT);
                            byKey.putIfAbsent(key, new JourneyGuidanceCatalog.Destination(
                                    key,
                                    JourneyGuidanceCatalog.formatPoiLabel(id, poiPrefix)));
                        }
                        out.addAll(byKey.values());
                    }
                }
            } catch (SQLException exc) {
                plugin.getLogger().warning("Failed to load POI regions from WorldGuard tables: " + exc.getMessage());
            }
            sync(callback, out);
        });
    }


}
