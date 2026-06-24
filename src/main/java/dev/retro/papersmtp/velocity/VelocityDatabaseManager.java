package dev.retro.papersmtp.velocity;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;

public class VelocityDatabaseManager {
    private final VelocityPaperSMTP plugin;
    private final Logger logger;
    private final File dbFile;
    private Connection connection;

    public VelocityDatabaseManager(VelocityPaperSMTP plugin, Logger logger, Path dataDirectory) {
        this.plugin = plugin;
        this.logger = logger;
        this.dbFile = new File(dataDirectory.toFile(), "queue.db");
    }

    public synchronized void setup() {
        try {
            File folder = dbFile.getParentFile();
            if (!folder.exists()) {
                folder.mkdirs();
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            logger.info("Connected to local SQLite queue database successfully.");
            createTable();
        } catch (Exception e) {
            logger.error("Failed to connect to Velocity database: " + e.getMessage(), e);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            setup();
        }
        return connection;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warn("Failed to close Velocity database: " + e.getMessage());
        }
    }

    private void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS papersmtp_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "server VARCHAR(64) NOT NULL, " +
                "command TEXT NOT NULL" +
                ");";
        try {
            Connection conn = getConnection();
            try (Statement statement = conn.createStatement()) {
                statement.execute(query);
            }
        } catch (Exception e) {
            logger.error("Failed to create queue table: " + e.getMessage(), e);
        }
    }

    public synchronized void addCommand(UUID uuid, String server, String command) {
        String query = "INSERT INTO papersmtp_queue (uuid, server, command) VALUES (?, ?, ?);";
        try {
            Connection conn = getConnection();
            try (PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, server);
                statement.setString(3, command);
                statement.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Failed to queue command for " + uuid + ": " + e.getMessage(), e);
        }
    }

    public synchronized List<QueuedCommand> getPendingCommands(UUID uuid, String server) {
        List<QueuedCommand> list = new ArrayList<>();
        String query = "SELECT id, command FROM papersmtp_queue WHERE uuid = ? AND server = ?;";
        try {
            Connection conn = getConnection();
            try (PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, server);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        list.add(new QueuedCommand(rs.getInt("id"), rs.getString("command")));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to query queued commands for " + uuid + ": " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized void removeCommands(List<Integer> ids) {
        if (ids.isEmpty()) return;
        StringBuilder sb = new StringBuilder("DELETE FROM papersmtp_queue WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sb.append("?");
            if (i < ids.size() - 1) sb.append(",");
        }
        sb.append(");");

        try {
            Connection conn = getConnection();
            try (PreparedStatement statement = conn.prepareStatement(sb.toString())) {
                for (int i = 0; i < ids.size(); i++) {
                    statement.setInt(i + 1, ids.get(i));
                }
                statement.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Failed to remove commands from queue: " + e.getMessage(), e);
        }
    }

    public static class QueuedCommand {
        public final int id;
        public final String command;

        public QueuedCommand(int id, String command) {
            this.id = id;
            this.command = command;
        }
    }
}
