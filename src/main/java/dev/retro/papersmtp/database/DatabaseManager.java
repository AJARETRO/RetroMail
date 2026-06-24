package dev.retro.papersmtp.database;

import dev.retro.papersmtp.MailPluginInterface;
import dev.retro.papersmtp.config.PluginConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final MailPluginInterface plugin;
    private Connection connection;

    public DatabaseManager(MailPluginInterface plugin) {
        this.plugin = plugin;
    }

    public synchronized void setup() {
        PluginConfig config = plugin.getPluginConfig();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            
            if (config.dbType.equalsIgnoreCase("sqlite")) {
                File dbFile = new File(plugin.getDataFolder(), config.sqliteFile);
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                plugin.getLogger().info("Connected to local SQLite database successfully.");
            } else {
                Class.forName("com.mysql.cj.jdbc.Driver");
                String url = "jdbc:mysql://" + config.mysqlHost + ":" + config.mysqlPort + "/" + config.mysqlDatabase + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";
                connection = DriverManager.getConnection(url, config.mysqlUsername, config.mysqlPassword);
                plugin.getLogger().info("Connected to remote MySQL database successfully.");
            }

            createTable();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        boolean valid = false;
        try {
            if (connection != null && !connection.isClosed()) {
                valid = connection.isValid(2);
            }
        } catch (Exception e) {
            if (connection != null) {
                try (Statement s = connection.createStatement()) {
                    s.execute("SELECT 1;");
                    valid = true;
                } catch (Exception ignored) {}
            }
        }

        if (!valid) {
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
            plugin.getLogger().log(Level.WARNING, "Failed to close database connection: " + e.getMessage());
        }
    }

    private void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS papersmtp_subscriptions (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "email VARCHAR(255) NOT NULL, " +
                "verified TINYINT DEFAULT 0, " +
                "verification_code VARCHAR(10) DEFAULT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        boolean isSqlite = plugin.getPluginConfig().dbType.equalsIgnoreCase("sqlite");
        String autoIncrement = isSqlite ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INT AUTO_INCREMENT PRIMARY KEY";

        String staffTable = "CREATE TABLE IF NOT EXISTS papersmtp_staff (" +
                "id " + autoIncrement + ", " +
                "username VARCHAR(64) UNIQUE NOT NULL, " +
                "email VARCHAR(255) UNIQUE NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "salt VARCHAR(64) NOT NULL, " +
                "role VARCHAR(16) DEFAULT 'STAFF', " +
                "temp_password TINYINT DEFAULT 1, " +
                "totp_secret VARCHAR(32) DEFAULT NULL, " +
                "totp_enabled TINYINT DEFAULT 0, " +
                "avatar_path VARCHAR(255) DEFAULT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String permTable = "CREATE TABLE IF NOT EXISTS papersmtp_staff_permissions (" +
                "id " + autoIncrement + ", " +
                "staff_id INTEGER NOT NULL, " +
                "mailbox VARCHAR(255) NOT NULL" +
                ");";

        String filterTable = "CREATE TABLE IF NOT EXISTS papersmtp_staff_filters (" +
                "id " + autoIncrement + ", " +
                "staff_id INTEGER NOT NULL, " +
                "mailbox VARCHAR(255) NOT NULL, " +
                "allowed_sender VARCHAR(255) NOT NULL" +
                ");";

        String mailsTable = "CREATE TABLE IF NOT EXISTS papersmtp_mails (" +
                "id " + autoIncrement + ", " +
                "mail_from VARCHAR(255) NOT NULL, " +
                "mail_to VARCHAR(255) NOT NULL, " +
                "subject VARCHAR(255) DEFAULT '', " +
                "body TEXT, " +
                "is_html TINYINT DEFAULT 0, " +
                "received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String tokensTable = "CREATE TABLE IF NOT EXISTS papersmtp_api_tokens (" +
                "id " + autoIncrement + ", " +
                "staff_id INTEGER NOT NULL, " +
                "token VARCHAR(255) UNIQUE NOT NULL, " +
                "name VARCHAR(64) DEFAULT 'API Token', " +
                "permissions VARCHAR(255) DEFAULT 'read_mails,send_mails', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String sessionsTable = "CREATE TABLE IF NOT EXISTS papersmtp_sessions (" +
                "token VARCHAR(255) PRIMARY KEY, " +
                "username VARCHAR(64) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try {
            Connection conn = getConnection();
            try (Statement statement = conn.createStatement()) {
                statement.execute(query);
                statement.execute(staffTable);
                statement.execute(permTable);
                statement.execute(filterTable);
                statement.execute(mailsTable);
                statement.execute(tokensTable);
                statement.execute(sessionsTable);
                // Run migration to add permissions column if it does not exist
                try {
                    statement.execute("ALTER TABLE papersmtp_api_tokens ADD COLUMN permissions VARCHAR(255) DEFAULT 'read_mails,send_mails';");
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables: " + e.getMessage(), e);
        }
    }

    public synchronized void setPendingSubscription(UUID uuid, String email, String verificationCode) {
        String query = "INSERT INTO papersmtp_subscriptions (uuid, email, verified, verification_code, updated_at) " +
                "VALUES (?, ?, 0, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE email = ?, verified = 0, verification_code = ?, updated_at = CURRENT_TIMESTAMP;";
                
        if (plugin.getPluginConfig().dbType.equalsIgnoreCase("sqlite")) {
            query = "INSERT OR REPLACE INTO papersmtp_subscriptions (uuid, email, verified, verification_code, updated_at) " +
                    "VALUES (?, ?, 0, ?, CURRENT_TIMESTAMP);";
        }

        try {
            executeSetPending(query, uuid, email, verificationCode);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database write failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                executeSetPending(query, uuid, email, verificationCode);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save pending subscription on retry: " + ex.getMessage(), ex);
            }
        }
    }

    private void executeSetPending(String query, UUID uuid, String email, String verificationCode) throws SQLException {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, email);
            statement.setString(3, verificationCode);
            if (!plugin.getPluginConfig().dbType.equalsIgnoreCase("sqlite")) {
                statement.setString(4, email);
                statement.setString(5, verificationCode);
            }
            statement.executeUpdate();
        }
    }

    public synchronized boolean verifySubscription(UUID uuid, String code) {
        try {
            return executeVerify(uuid, code);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database verify query failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                return executeVerify(uuid, code);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to verify subscription on retry: " + ex.getMessage(), ex);
                return false;
            }
        }
    }

    private boolean executeVerify(UUID uuid, String code) throws SQLException {
        String selectQuery = "SELECT verification_code FROM papersmtp_subscriptions WHERE uuid = ?;";
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(selectQuery)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String storedCode = rs.getString("verification_code");
                    if (storedCode != null && storedCode.equalsIgnoreCase(code)) {
                        String updateQuery = "UPDATE papersmtp_subscriptions SET verified = 1, verification_code = NULL WHERE uuid = ?;";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                            updateStmt.setString(1, uuid.toString());
                            updateStmt.executeUpdate();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public synchronized void unsubscribe(UUID uuid) {
        String query = "DELETE FROM papersmtp_subscriptions WHERE uuid = ?;";
        try {
            executeUnsubscribe(query, uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database delete query failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                executeUnsubscribe(query, uuid);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to unsubscribe on retry: " + ex.getMessage(), ex);
            }
        }
    }

    private void executeUnsubscribe(String query, UUID uuid) throws SQLException {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    public synchronized SubscriptionState getSubscriptionState(UUID uuid) {
        try {
            return executeGetState(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database state query failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                return executeGetState(uuid);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query subscription state on retry: " + ex.getMessage(), ex);
                return new SubscriptionState(SubscriptionState.Type.NONE, null, null);
            }
        }
    }

    private SubscriptionState executeGetState(UUID uuid) throws SQLException {
        String query = "SELECT verified, email, verification_code FROM papersmtp_subscriptions WHERE uuid = ?;";
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    boolean verified = rs.getInt("verified") == 1;
                    String email = rs.getString("email");
                    String code = rs.getString("verification_code");
                    if (verified) {
                        return new SubscriptionState(SubscriptionState.Type.VERIFIED, email, null);
                    } else {
                        return new SubscriptionState(SubscriptionState.Type.PENDING, email, code);
                    }
                }
            }
        }
        return new SubscriptionState(SubscriptionState.Type.NONE, null, null);
    }

    public synchronized List<String> getSubscribedEmails() {
        try {
            return executeGetSubscribedEmails();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database emails query failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                return executeGetSubscribedEmails();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query subscribed emails on retry: " + ex.getMessage(), ex);
                return new ArrayList<>();
            }
        }
    }

    private List<String> executeGetSubscribedEmails() throws SQLException {
        List<String> emails = new ArrayList<>();
        String query = "SELECT email FROM papersmtp_subscriptions WHERE verified = 1;";
        Connection conn = getConnection();
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                emails.add(rs.getString("email"));
            }
        }
        return emails;
    }

    public synchronized void verifySubscriptionLocally(UUID uuid, String email) {
        String query = "INSERT INTO papersmtp_subscriptions (uuid, email, verified, verification_code, updated_at) " +
                "VALUES (?, ?, 1, NULL, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE email = ?, verified = 1, verification_code = NULL, updated_at = CURRENT_TIMESTAMP;";
                
        if (plugin.getPluginConfig().dbType.equalsIgnoreCase("sqlite")) {
            query = "INSERT OR REPLACE INTO papersmtp_subscriptions (uuid, email, verified, verification_code, updated_at) " +
                    "VALUES (?, ?, 1, NULL, CURRENT_TIMESTAMP);";
        }

        try {
            executeVerifyLocally(query, uuid, email);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database local verify failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                executeVerifyLocally(query, uuid, email);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to locally verify subscription on retry: " + ex.getMessage(), ex);
            }
        }
    }

    private void executeVerifyLocally(String query, UUID uuid, String email) throws SQLException {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, email);
            if (!plugin.getPluginConfig().dbType.equalsIgnoreCase("sqlite")) {
                statement.setString(3, email);
            }
            statement.executeUpdate();
        }
    }

    public synchronized void unsubscribeLocally(UUID uuid) {
        String query = "DELETE FROM papersmtp_subscriptions WHERE uuid = ?;";
        try {
            executeUnsubscribeLocally(query, uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database local unsubscribe failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                executeUnsubscribeLocally(query, uuid);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to locally unsubscribe on retry: " + ex.getMessage(), ex);
            }
        }
    }

    private void executeUnsubscribeLocally(String query, UUID uuid) throws SQLException {
        Connection conn = getConnection();
        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    public synchronized List<Subscriber> getVerifiedSubscribers() {
        try {
            return executeGetVerifiedSubscribers();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database verified subscribers query failed, reconnecting and retrying: " + e.getMessage());
            setup();
            try {
                return executeGetVerifiedSubscribers();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query verified subscribers on retry: " + ex.getMessage(), ex);
                return new ArrayList<>();
            }
        }
    }

    private List<Subscriber> executeGetVerifiedSubscribers() throws SQLException {
        List<Subscriber> list = new ArrayList<>();
        String query = "SELECT uuid, email FROM papersmtp_subscriptions WHERE verified = 1;";
        Connection conn = getConnection();
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                String uuidStr = rs.getString("uuid");
                String email = rs.getString("email");
                try {
                    list.add(new Subscriber(UUID.fromString(uuidStr), email));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    public static class Subscriber {
        public final UUID uuid;
        public final String email;

        public Subscriber(UUID uuid, String email) {
            this.uuid = uuid;
            this.email = email;
        }
    }

    // --- STAFF DASHBOARD MODULE DATABASE HELPERS ---

    public synchronized boolean createStaffAccount(String username, String email, String passwordHash, String salt, String role) {
        String query = "INSERT INTO papersmtp_staff (username, email, password_hash, salt, role) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, salt);
            statement.setString(5, role);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create staff account for " + username + ": " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized StaffAccount getStaffAccount(String username) {
        String query = "SELECT id, username, email, password_hash, salt, role, temp_password, totp_secret, totp_enabled, avatar_path FROM papersmtp_staff WHERE username = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new StaffAccount(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("role"),
                            rs.getInt("temp_password") == 1,
                            rs.getString("totp_secret"),
                            rs.getInt("totp_enabled") == 1,
                            rs.getString("avatar_path")
                    );
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get staff account: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized boolean updateStaffPassword(int id, String newPasswordHash, String salt) {
        String query = "UPDATE papersmtp_staff SET password_hash = ?, salt = ?, temp_password = 0 WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, newPasswordHash);
            statement.setString(2, salt);
            statement.setInt(3, id);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update password: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized boolean updateStaff2FA(int id, String secret, boolean enabled) {
        String query = "UPDATE papersmtp_staff SET totp_secret = ?, totp_enabled = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, secret);
            statement.setInt(2, enabled ? 1 : 0);
            statement.setInt(3, id);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update 2FA: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized boolean updateStaffAvatar(int id, String path) {
        String query = "UPDATE papersmtp_staff SET avatar_path = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, path);
            statement.setInt(2, id);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update avatar: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized boolean updateStaffRole(int id, String role) {
        String query = "UPDATE papersmtp_staff SET role = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, role);
            statement.setInt(2, id);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update role for staff account: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized List<StaffAccount> listStaff() {
        List<StaffAccount> list = new ArrayList<>();
        String query = "SELECT id, username, email, password_hash, salt, role, temp_password, totp_secret, totp_enabled, avatar_path FROM papersmtp_staff ORDER BY username ASC;";
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                list.add(new StaffAccount(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        rs.getString("role"),
                        rs.getInt("temp_password") == 1,
                        rs.getString("totp_secret"),
                        rs.getInt("totp_enabled") == 1,
                        rs.getString("avatar_path")
                ));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to list staff: " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized boolean deleteStaff(int id) {
        String query = "DELETE FROM papersmtp_staff WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete staff: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized void updateStaffPermissions(int staffId, List<String> mailboxes) {
        String deleteQuery = "DELETE FROM papersmtp_staff_permissions WHERE staff_id = ?;";
        String insertQuery = "INSERT INTO papersmtp_staff_permissions (staff_id, mailbox) VALUES (?, ?);";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery);
                 PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {

                deleteStmt.setInt(1, staffId);
                deleteStmt.executeUpdate();

                for (String mailbox : mailboxes) {
                    insertStmt.setInt(1, staffId);
                    insertStmt.setString(2, mailbox);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update staff permissions: " + e.getMessage(), e);
        }
    }

    public synchronized List<String> getStaffPermissions(int staffId) {
        List<String> list = new ArrayList<>();
        String query = "SELECT mailbox FROM papersmtp_staff_permissions WHERE staff_id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setInt(1, staffId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("mailbox"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get staff permissions: " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized void updateStaffFilters(int staffId, List<SenderFilter> filters) {
        String deleteQuery = "DELETE FROM papersmtp_staff_filters WHERE staff_id = ?;";
        String insertQuery = "INSERT INTO papersmtp_staff_filters (staff_id, mailbox, allowed_sender) VALUES (?, ?, ?);";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery);
                 PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {

                deleteStmt.setInt(1, staffId);
                deleteStmt.executeUpdate();

                for (SenderFilter filter : filters) {
                    insertStmt.setInt(1, staffId);
                    insertStmt.setString(2, filter.mailbox);
                    insertStmt.setString(3, filter.allowedSender);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update staff filters: " + e.getMessage(), e);
        }
    }

    public synchronized List<SenderFilter> getStaffFilters(int staffId) {
        List<SenderFilter> list = new ArrayList<>();
        String query = "SELECT mailbox, allowed_sender FROM papersmtp_staff_filters WHERE staff_id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setInt(1, staffId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new SenderFilter(rs.getString("mailbox"), rs.getString("allowed_sender")));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get staff filters: " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized void saveIncomingMail(String mailFrom, String mailTo, String subject, String body, boolean isHtml) {
        String query = "INSERT INTO papersmtp_mails (mail_from, mail_to, subject, body, is_html) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, mailFrom);
            statement.setString(2, mailTo);
            statement.setString(3, subject);
            statement.setString(4, body);
            statement.setInt(5, isHtml ? 1 : 0);
            statement.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save incoming mail: " + e.getMessage(), e);
        }
    }

    public synchronized List<StoredMail> getMailsForStaff(StaffAccount staff, String domain) {
        List<StoredMail> list = new ArrayList<>();

        // Build the list of allowed mailboxes
        List<String> allowedMailboxes = new ArrayList<>();

        // Personal company mailbox is username@domain
        String personalMailbox = (staff.username + "@" + domain).toLowerCase();
        allowedMailboxes.add(personalMailbox);

        if (staff.role.equalsIgnoreCase("ADMIN")) {
            // Admin can see everything
            String query = "SELECT id, mail_from, mail_to, subject, body, is_html, received_at FROM papersmtp_mails ORDER BY id DESC LIMIT 200;";
            try (Connection conn = getConnection();
                 Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {
                while (rs.next()) {
                    list.add(new StoredMail(
                            rs.getInt("id"),
                            rs.getString("mail_from"),
                            rs.getString("mail_to"),
                            rs.getString("subject"),
                            rs.getString("body"),
                            rs.getInt("is_html") == 1,
                            rs.getString("received_at")
                    ));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query mails for admin: " + e.getMessage(), e);
            }
            return list;
        }

        // For regular staff, load allowed mailboxes (permissions) and conditional filters
        List<String> permissions = getStaffPermissions(staff.id);
        for (String perm : permissions) {
            allowedMailboxes.add(perm.toLowerCase());
        }

        List<SenderFilter> filters = getStaffFilters(staff.id);

        // Fetch recent mails
        String query = "SELECT id, mail_from, mail_to, subject, body, is_html, received_at FROM papersmtp_mails ORDER BY id DESC LIMIT 200;";
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                String to = rs.getString("mail_to").toLowerCase();
                String from = rs.getString("mail_from").toLowerCase();

                // Check if this mail is addressed to or sent from one of the staff's allowed mailboxes
                boolean allowed = false;
                for (String allowedMailbox : allowedMailboxes) {
                    if (to.contains(allowedMailbox) || from.contains(allowedMailbox)) {
                        allowed = true;

                        // Apply conditional filters if any are defined for this mailbox
                        boolean filterRestricted = false;
                        boolean hasFiltersForMailbox = false;
                        for (SenderFilter filter : filters) {
                            if (filter.mailbox.equalsIgnoreCase(allowedMailbox)) {
                                hasFiltersForMailbox = true;
                                if (from.contains(filter.allowedSender.toLowerCase())) {
                                    filterRestricted = false; // meets the allowed sender condition
                                    break;
                                } else {
                                    filterRestricted = true;
                                }
                            }
                        }
                        if (hasFiltersForMailbox && filterRestricted) {
                            allowed = false; // failed the sender condition filter
                        }
                        break;
                    }
                }

                if (allowed) {
                    list.add(new StoredMail(
                            rs.getInt("id"),
                            rs.getString("mail_from"),
                            rs.getString("mail_to"),
                            rs.getString("subject"),
                            rs.getString("body"),
                            rs.getInt("is_html") == 1,
                            rs.getString("received_at")
                    ));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to query mails for staff: " + e.getMessage(), e);
        }

        return list;
    }

    // --- STAFF DATA MODELS ---

    public static class StaffAccount {
        public final int id;
        public final String username;
        public final String email;
        public final String passwordHash;
        public final String salt;
        public final String role;
        public final boolean tempPassword;
        public final String totpSecret;
        public final boolean totpEnabled;
        public final String avatarPath;

        public StaffAccount(int id, String username, String email, String passwordHash, String salt, String role,
                            boolean tempPassword, String totpSecret, boolean totpEnabled, String avatarPath) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.role = role;
            this.tempPassword = tempPassword;
            this.totpSecret = totpSecret;
            this.totpEnabled = totpEnabled;
            this.avatarPath = avatarPath;
        }
    }

    public static class SenderFilter {
        public final String mailbox;
        public final String allowedSender;

        public SenderFilter(String mailbox, String allowedSender) {
            this.mailbox = mailbox;
            this.allowedSender = allowedSender;
        }
    }

    public static class StoredMail {
        public final int id;
        public final String mailFrom;
        public final String mailTo;
        public final String subject;
        public final String body;
        public final boolean isHtml;
        public final String receivedAt;

        public StoredMail(int id, String mailFrom, String mailTo, String subject, String body, boolean isHtml, String receivedAt) {
            this.id = id;
            this.mailFrom = mailFrom;
            this.mailTo = mailTo;
            this.subject = subject;
            this.body = body;
            this.isHtml = isHtml;
            this.receivedAt = receivedAt;
        }
    }

    public synchronized List<StoredMail> getConversation(String email1, String email2) {
        List<StoredMail> list = new ArrayList<>();
        String clean1 = extractEmailAddress(email1).toLowerCase();
        String clean2 = extractEmailAddress(email2).toLowerCase();

        String query = "SELECT id, mail_from, mail_to, subject, body, is_html, received_at FROM papersmtp_mails " +
                "WHERE (LOWER(mail_from) LIKE ? AND LOWER(mail_to) LIKE ?) " +
                "   OR (LOWER(mail_from) LIKE ? AND LOWER(mail_to) LIKE ?) " +
                "ORDER BY id DESC LIMIT 500;";

        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, "%" + clean1 + "%");
            statement.setString(2, "%" + clean2 + "%");
            statement.setString(3, "%" + clean2 + "%");
            statement.setString(4, "%" + clean1 + "%");

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new StoredMail(
                            rs.getInt("id"),
                            rs.getString("mail_from"),
                            rs.getString("mail_to"),
                            rs.getString("subject"),
                            rs.getString("body"),
                            rs.getInt("is_html") == 1,
                            rs.getString("received_at")
                    ));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to query conversation: " + e.getMessage(), e);
        }
        return list;
    }

    public static class ApiToken {
        public final int id;
        public final int staffId;
        public final String token;
        public final String name;
        public final String permissions;
        public final String createdAt;

        public ApiToken(int id, int staffId, String token, String name, String permissions, String createdAt) {
            this.id = id;
            this.staffId = staffId;
            this.token = token;
            this.name = name;
            this.permissions = permissions;
            this.createdAt = createdAt;
        }
    }

    public synchronized boolean saveApiToken(int staffId, String token, String name, String permissions) {
        String query = "INSERT INTO papersmtp_api_tokens (staff_id, token, name, permissions) VALUES (?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setInt(1, staffId);
            statement.setString(2, token);
            statement.setString(3, name);
            statement.setString(4, permissions);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save API token: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized List<ApiToken> getApiTokens(int staffId) {
        List<ApiToken> list = new ArrayList<>();
        String query = "SELECT id, token, name, permissions, created_at FROM papersmtp_api_tokens WHERE staff_id = ? ORDER BY id DESC;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setInt(1, staffId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new ApiToken(
                            rs.getInt("id"),
                            staffId,
                            rs.getString("token"),
                            rs.getString("name"),
                            rs.getString("permissions"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to query API tokens: " + e.getMessage(), e);
        }
        return list;
    }

    public synchronized String getTokenPermissions(String token) {
        String query = "SELECT permissions FROM papersmtp_api_tokens WHERE token = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, token);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("permissions");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to query token permissions: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized boolean deleteApiToken(int staffId, int tokenId) {
        String query = "DELETE FROM papersmtp_api_tokens WHERE staff_id = ? AND id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setInt(1, staffId);
            statement.setInt(2, tokenId);
            int rows = statement.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete API token: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized StaffAccount getStaffByApiToken(String token) {
        String query = "SELECT s.id, s.username, s.email, s.password_hash, s.salt, s.role, s.temp_password, s.totp_secret, s.totp_enabled, s.avatar_path " +
                "FROM papersmtp_staff s " +
                "JOIN papersmtp_api_tokens t ON s.id = t.staff_id " +
                "WHERE t.token = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, token);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new StaffAccount(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("role"),
                            rs.getInt("temp_password") == 1,
                            rs.getString("totp_secret"),
                            rs.getInt("totp_enabled") == 1,
                            rs.getString("avatar_path")
                    );
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to resolve staff by API token: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized void saveSession(String token, String username) {
        String query = "INSERT INTO papersmtp_sessions (token, username, created_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE username = VALUES(username), created_at = CURRENT_TIMESTAMP;";
        if (plugin.getPluginConfig().dbType.equalsIgnoreCase("sqlite")) {
            query = "INSERT OR REPLACE INTO papersmtp_sessions (token, username, created_at) VALUES (?, ?, CURRENT_TIMESTAMP);";
        }
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, token);
            statement.setString(2, username);
            statement.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save session: " + e.getMessage(), e);
        }
    }

    public synchronized String getSessionUsername(String token) {
        String query = "SELECT username FROM papersmtp_sessions WHERE token = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, token);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to query session: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized void deleteSession(String token) {
        String query = "DELETE FROM papersmtp_sessions WHERE token = ?;";
        try (Connection conn = getConnection();
             PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, token);
            statement.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete session: " + e.getMessage(), e);
        }
    }

    private String extractEmailAddress(String email) {
        if (email == null) return "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        java.util.regex.Matcher matcher = pattern.matcher(email);
        if (matcher.find()) {
            return matcher.group();
        }
        return email.trim();
    }
}
