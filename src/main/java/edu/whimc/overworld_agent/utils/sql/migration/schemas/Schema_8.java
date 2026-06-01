package edu.whimc.overworld_agent.utils.sql.migration.schemas;

import edu.whimc.overworld_agent.utils.sql.migration.SchemaVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Schema_8 extends SchemaVersion {

    private static final String CREATE_AGENT_CHAT_CONVERSATIONS =
            "CREATE TABLE IF NOT EXISTS `whimc_agent_chat_conversations` (" +
                    "  `rowid`              INT AUTO_INCREMENT NOT NULL," +
                    "  `conversation_id`    VARCHAR(36)        NOT NULL," +
                    "  `time`               BIGINT             NOT NULL," +
                    "  `uuid`               VARCHAR(36)        NOT NULL," +
                    "  `username`           VARCHAR(16)        NOT NULL," +
                    "  `player_research_id` VARCHAR(128)       NULL," +
                    "  `session_id`         VARCHAR(128)       NULL," +
                    "  `world_name`         VARCHAR(128)       NULL," +
                    "  `agent_type`         VARCHAR(64)        NULL," +
                    "  `research_allowed`   TINYINT(1)         NOT NULL DEFAULT 1," +
                    "  PRIMARY KEY (`rowid`)," +
                    "  UNIQUE KEY conversation_id (`conversation_id`)," +
                    "  INDEX uuid (`uuid`)," +
                    "  INDEX username (`username`)," +
                    "  INDEX player_research_id (`player_research_id`)," +
                    "  INDEX time (`time`)" +
                    ");";

    private static final String CREATE_AGENT_CHAT_TURNS =
            "CREATE TABLE IF NOT EXISTS `whimc_agent_chat_turns` (" +
                    "  `rowid`                 INT AUTO_INCREMENT NOT NULL," +
                    "  `turn_id`               VARCHAR(36)        NOT NULL," +
                    "  `conversation_id`       VARCHAR(36)        NOT NULL," +
                    "  `turn_index`            INT                NOT NULL," +
                    "  `time`                  BIGINT             NOT NULL," +
                    "  `uuid`                  VARCHAR(36)        NOT NULL," +
                    "  `username`              VARCHAR(16)        NOT NULL," +
                    "  `player_research_id`    VARCHAR(128)       NULL," +
                    "  `session_id`            VARCHAR(128)       NULL," +
                    "  `world_name`            VARCHAR(128)       NULL," +
                    "  `agent_type`            VARCHAR(64)        NULL," +
                    "  `agent_name`            VARCHAR(64)        NULL," +
                    "  `command`               VARCHAR(64)        NULL," +
                    "  `user_message`          TEXT               NULL," +
                    "  `assistant_response`    MEDIUMTEXT         NULL," +
                    "  `llm_provider`          VARCHAR(64)        NULL," +
                    "  `llm_model`             VARCHAR(128)       NULL," +
                    "  `system_prompt_version` VARCHAR(128)       NULL," +
                    "  `system_prompt_hash`    CHAR(64)           NULL," +
                    "  `rag_enabled`           TINYINT(1)         NOT NULL DEFAULT 0," +
                    "  `rag_query`             TEXT               NULL," +
                    "  `request_started_at`    BIGINT             NULL," +
                    "  `response_received_at`  BIGINT             NULL," +
                    "  `latency_ms`            INT                NULL," +
                    "  `status`                VARCHAR(32)        NOT NULL," +
                    "  `error_message`         TEXT               NULL," +
                    "  PRIMARY KEY (`rowid`)," +
                    "  UNIQUE KEY turn_id (`turn_id`)," +
                    "  INDEX conversation_id (`conversation_id`)," +
                    "  INDEX uuid (`uuid`)," +
                    "  INDEX username (`username`)," +
                    "  INDEX player_research_id (`player_research_id`)," +
                    "  INDEX time (`time`)," +
                    "  INDEX status (`status`)" +
                    ");";

    private static final String CREATE_AGENT_CHAT_RETRIEVED_CHUNKS =
            "CREATE TABLE IF NOT EXISTS `whimc_agent_chat_retrieved_chunks` (" +
                    "  `rowid`           INT AUTO_INCREMENT NOT NULL," +
                    "  `turn_id`         VARCHAR(36)        NOT NULL," +
                    "  `chunk_rank`      INT                NOT NULL," +
                    "  `source_id`       VARCHAR(255)       NULL," +
                    "  `source_title`    VARCHAR(255)       NULL," +
                    "  `chunk_id`        VARCHAR(255)       NULL," +
                    "  `chunk_text_hash` CHAR(64)           NULL," +
                    "  `score`           DOUBLE             NULL," +
                    "  `chunk_text`      MEDIUMTEXT         NULL," +
                    "  PRIMARY KEY (`rowid`)," +
                    "  INDEX turn_id (`turn_id`)," +
                    "  INDEX source_id (`source_id`)," +
                    "  INDEX chunk_id (`chunk_id`)" +
                    ");";

    private static final String CREATE_AGENT_CHAT_EVENTS =
            "CREATE TABLE IF NOT EXISTS `whimc_agent_chat_events` (" +
                    "  `rowid`         INT AUTO_INCREMENT NOT NULL," +
                    "  `turn_id`       VARCHAR(36)        NULL," +
                    "  `time`          BIGINT             NOT NULL," +
                    "  `event_type`    VARCHAR(64)        NOT NULL," +
                    "  `event_payload` MEDIUMTEXT         NULL," +
                    "  PRIMARY KEY (`rowid`)," +
                    "  INDEX turn_id (`turn_id`)," +
                    "  INDEX time (`time`)," +
                    "  INDEX event_type (`event_type`)" +
                    ");";

    private static final String CREATE_AGENT_CHAT_CONTEXT_ITEMS =
            "CREATE TABLE IF NOT EXISTS `whimc_agent_chat_context_items` (" +
                    "  `rowid`           INT AUTO_INCREMENT NOT NULL," +
                    "  `turn_id`         VARCHAR(36)        NOT NULL," +
                    "  `time`            BIGINT             NOT NULL," +
                    "  `context_rank`    INT                NOT NULL," +
                    "  `context_type`    VARCHAR(64)        NOT NULL," +
                    "  `source_id`       VARCHAR(255)       NULL," +
                    "  `source_title`    VARCHAR(255)       NULL," +
                    "  `world_name`      VARCHAR(128)       NULL," +
                    "  `x`               DOUBLE             NULL," +
                    "  `y`               DOUBLE             NULL," +
                    "  `z`               DOUBLE             NULL," +
                    "  `distance`        DOUBLE             NULL," +
                    "  `trait_type`      VARCHAR(128)       NULL," +
                    "  `context_text`    MEDIUMTEXT         NULL," +
                    "  `context_hash`    CHAR(64)           NULL," +
                    "  `score`           DOUBLE             NULL," +
                    "  PRIMARY KEY (`rowid`)," +
                    "  INDEX turn_id (`turn_id`)," +
                    "  INDEX context_type (`context_type`)," +
                    "  INDEX source_id (`source_id`)," +
                    "  INDEX world_name (`world_name`)," +
                    "  INDEX time (`time`)" +
                    ");";

    public Schema_8() {
        super(8, null);
    }

    @Override
    protected void migrateRoutine(Connection connection) throws SQLException {
        execute(connection, CREATE_AGENT_CHAT_CONVERSATIONS);
        execute(connection, CREATE_AGENT_CHAT_TURNS);
        execute(connection, CREATE_AGENT_CHAT_RETRIEVED_CHUNKS);
        execute(connection, CREATE_AGENT_CHAT_EVENTS);
        execute(connection, CREATE_AGENT_CHAT_CONTEXT_ITEMS);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}