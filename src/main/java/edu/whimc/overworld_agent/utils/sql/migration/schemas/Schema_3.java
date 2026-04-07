package edu.whimc.overworld_agent.utils.sql.migration.schemas;

import edu.whimc.overworld_agent.utils.sql.migration.SchemaVersion;

import java.sql.*;

public class Schema_3 extends SchemaVersion {


    private static final String ADD_CATEGORY =
            "ALTER TABLE whimc_dialog_science ADD COLUMN agent_response TEXT;";


    public Schema_3() {
        super(3, new Schema_4());
    }

    @Override
    protected void migrateRoutine(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADD_CATEGORY)) {
            statement.execute();
        } catch (SQLException e) {
            // Already applied on this database, or repair added the column before this migration ran.
            if (!isDuplicateColumnName(e)) {
                throw e;
            }
        }
    }

    private static boolean isDuplicateColumnName(SQLException e) {
        return e.getErrorCode() == 1060 || "42S21".equals(e.getSQLState());
    }

}
