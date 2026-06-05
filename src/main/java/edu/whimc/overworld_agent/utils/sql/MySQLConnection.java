package edu.whimc.overworld_agent.utils.sql;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.utils.sql.migration.SchemaManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class MySQLConnection {

    public static final String URL_TEMPLATE =
            "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci";

    private Connection connection;
    private final String host;
    private final String database;
    private final String username;
    private final String password;
    private final String url;
    private final int port;

    private final OverworldAgent plugin;

    public MySQLConnection(OverworldAgent plugin) {
        this.host = plugin.getConfig().getString("mysql.host");
        this.port = plugin.getConfig().getInt("mysql.port");
        this.database = plugin.getConfig().getString("mysql.database");
        this.username = plugin.getConfig().getString("mysql.username");
        this.password = plugin.getConfig().getString("mysql.password");

        this.url = String.format(URL_TEMPLATE, this.host, this.port, this.database);

        this.plugin = plugin;
    }

    public boolean initialize() {
        if (getConnection() == null) {
            return false;
        }

        SchemaManager manager = new SchemaManager(this.plugin, this.connection);
        if (!manager.initialize()) {
            return false;
        }
        repairDriftedDialogScienceColumns();
        return true;
    }

    /**
     * Migration progress is stored in {@code plugins/.../.schema_version}, but the actual MySQL schema may
     * lag (shared DB, restored dump, manual edits). Ensure columns the query layer expects exist.
     */
    private void repairDriftedDialogScienceColumns() {
        try {
            if (!tableExists(this.connection, "whimc_dialog_science")) {
                return;
            }
            if (columnExists(this.connection, "whimc_dialog_science", "agent_response")) {
                return;
            }
            plugin.getLogger().info(
                    "Adding missing column whimc_dialog_science.agent_response (database out of sync with local schema version).");
            try (Statement st = this.connection.createStatement()) {
                st.executeUpdate(
                        "ALTER TABLE whimc_dialog_science ADD COLUMN agent_response TEXT "
                                + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not add whimc_dialog_science.agent_response — dialogue logging may fail until the DB is fixed.", e);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = meta.getTables(catalog, null, table, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet rs = meta.getColumns(catalog, null, table, column)) {
            return rs.next();
        }
    }

    public Connection getConnection() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                return this.connection;
            }
            this.connection = DriverManager.getConnection(this.url, this.username, this.password);
            try (Statement statement = this.connection.createStatement()) {
                statement.execute("SET NAMES utf8mb4");
            }
        } catch (SQLException ignored) {
            return null;
        }

        return this.connection;
    }

}
