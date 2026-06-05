package edu.whimc.overworld_agent.utils.sql.migration.schemas;

import edu.whimc.overworld_agent.utils.sql.migration.SchemaVersion;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ensures plugin tables accept full Unicode (e.g. LLM em-dashes and smart punctuation).
 */
public class Schema_9 extends SchemaVersion {

    private static final String[] WHIMC_TABLES = {
            "whimc_agents",
            "whimc_dialog_science",
            "whimc_tags",
            "whimc_dialogue_interaction",
            "whimc_build_templates",
            "whimc_dialogue_builder_interaction",
            "whimc_agent_chat_conversations",
            "whimc_agent_chat_turns",
            "whimc_agent_chat_retrieved_chunks",
            "whimc_agent_chat_events",
            "whimc_agent_chat_context_items",
    };

    public Schema_9() {
        super(9, null);
    }

    @Override
    protected void migrateRoutine(Connection connection) throws SQLException {
        for (String table : WHIMC_TABLES) {
            if (!tableExists(connection, table)) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE `" + table + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException e) {
                Logger.getLogger(Schema_9.class.getName()).log(Level.WARNING,
                        "Could not convert " + table + " to utf8mb4; Unicode dialogue logging may fail.", e);
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = meta.getTables(catalog, null, table, new String[] { "TABLE" })) {
            return rs.next();
        }
    }
}
