package edu.whimc.overworld_agent.utils.sql.migration.schemas;
import edu.whimc.overworld_agent.utils.sql.migration.SchemaVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Schema_7 extends SchemaVersion {
    private static final String CREATE_TEMPLATES =
            "CREATE TABLE IF NOT EXISTS `whimc_build_templates` (" +
                    "  `rowid`       INT    AUTO_INCREMENT NOT NULL," +
                    "  `uuid`        VARCHAR(36)           NOT NULL," +
                    "  `username`    VARCHAR(16)           NOT NULL," +
                    "  `template_name`    VARCHAR(36)           NOT NULL," +
                    "  `start_time`        BIGINT                NOT NULL," +
                    "  `end_time`    BIGINT           NOT NULL," +
                    "  PRIMARY KEY    (`rowid`));";

    private static final String CREATE_INTERACTION =
            "CREATE TABLE IF NOT EXISTS `whimc_dialogue_builder_interaction` (" +
                    "  `rowid`       INT    AUTO_INCREMENT NOT NULL," +
                    "  `uuid`        VARCHAR(36)           NOT NULL," +
                    "  `username`    VARCHAR(16)           NOT NULL," +
                    "  `world`    VARCHAR(36)           NOT NULL," +
                    "  `x`           DOUBLE                NOT NULL," +
                    "  `y`           DOUBLE                NOT NULL," +
                    "  `z`           DOUBLE                NOT NULL," +
                    "  `time`        BIGINT                NOT NULL," +
                    "  `interaction`    TEXT           NOT NULL," +
                    "  `build_id`    INT          ," +
                    "  PRIMARY KEY    (`rowid`));";

    /**
     * Constructor to specify which migrations to do
     */
    public Schema_7() {
        super(7, new Schema_8());
    }
    @Override
    protected void migrateRoutine(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_TEMPLATES)) {
            statement.execute();
        }
        try (PreparedStatement statement = connection.prepareStatement(CREATE_INTERACTION)) {
            statement.execute();
        }

    }
}
