package dev.retro.papersmtp.config;

import dev.retro.papersmtp.MailPluginInterface;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class PluginConfig {
    private final MailPluginInterface plugin;

    public String dbType;
    public String sqliteFile;
    public String mysqlHost;
    public int mysqlPort;
    public String mysqlDatabase;
    public String mysqlUsername;
    public String mysqlPassword;

    public String smtpHost;
    public int smtpPort;
    public String smtpUsername;
    public String smtpPassword;
    public boolean smtpSsl;
    public boolean smtpStarttls;
    public String smtpFromAddress;
    public String smtpFromName;

    public String verificationSubject;
    public String verificationBody;
    public boolean useHtml;
    public String htmlFile;

    public boolean rewardsEnabled;
    public List<String> rewardCommands;
    public List<String> rewardMessages;

    public int emailCooldown;

    public boolean mailHandlerEnabled;
    public int mailHandlerPort;
    public String mailHandlerDomain;
    public List<String> mailHandlerRestricted;

    public String imapHost;
    public int imapPort;
    public boolean imapSsl;
    public String imapUsername;
    public String imapPassword;
    public int imapPollInterval;

    public String serverName;
    public String discordLink;
    public String documentationLink;
    public String forumLink;
    public String securitySecretToken;
    public boolean multiServer;
    public String licenseKey;

    private Map<String, Object> messagesConfig = new HashMap<>();

    public PluginConfig(MailPluginInterface plugin) {
        this.plugin = plugin;
        load();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    try (FileOutputStream out = new FileOutputStream(configFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save default config.yml", e);
            }
        }

        File messagesFile = new File(dataFolder, "messages.yml");
        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.yml")) {
                if (in != null) {
                    try (FileOutputStream out = new FileOutputStream(messagesFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> config = new HashMap<>();
        Yaml yaml = new Yaml();
        try (FileInputStream in = new FileInputStream(configFile)) {
            config = yaml.loadAs(in, Map.class);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load config.yml", e);
        }

        try (FileInputStream in = new FileInputStream(messagesFile)) {
            messagesConfig = yaml.loadAs(in, Map.class);
        } catch (Exception ignored) {}

        if (config == null) config = new HashMap<>();
        if (messagesConfig == null) messagesConfig = new HashMap<>();

        dbType = getString(config, "database.type", "sqlite").toLowerCase();
        sqliteFile = getString(config, "database.sqlite.file", "subscriptions.db");
        mysqlHost = getString(config, "database.mysql.host", "127.0.0.1");
        mysqlPort = getInt(config, "database.mysql.port", 3306);
        mysqlDatabase = getString(config, "database.mysql.database", "papersmtp");
        mysqlUsername = getString(config, "database.mysql.username", "root");
        mysqlPassword = getString(config, "database.mysql.password", "");

        smtpHost = getString(config, "smtp.host", "smtp.gmail.com");
        smtpPort = getInt(config, "smtp.port", 587);
        smtpUsername = getString(config, "smtp.username", "");
        smtpPassword = getString(config, "smtp.password", "");
        smtpSsl = getBoolean(config, "smtp.ssl", false);
        smtpStarttls = getBoolean(config, "smtp.starttls", true);
        smtpFromAddress = getString(config, "smtp.from-address", "noreply@retro.ajaretro.dev");
        smtpFromName = getString(config, "smtp.from-name", "Retro Network");

        verificationSubject = getString(config, "verification-email.subject", "Verify your email");
        verificationBody = getString(config, "verification-email.body", "Code: {code}");
        useHtml = getBoolean(config, "verification-email.use-html", true);
        htmlFile = getString(config, "verification-email.html-file", "email_template.html");

        rewardsEnabled = getBoolean(config, "rewards.enabled", false);
        rewardCommands = getStringList(config, "rewards.commands");
        rewardMessages = getStringList(config, "rewards.messages");

        emailCooldown = getInt(config, "email-cooldown", 60);

        mailHandlerEnabled = getBoolean(config, "mail-handler.enabled", false);
        mailHandlerPort = getInt(config, "mail-handler.port", 8080);
        mailHandlerDomain = getString(config, "mail-handler.domain", "localhost");
        mailHandlerRestricted = getStringList(config, "mail-handler.restricted-mailboxes");

        imapHost = getString(config, "mail-handler.imap.host", "");
        imapPort = getInt(config, "mail-handler.imap.port", 993);
        imapSsl = getBoolean(config, "mail-handler.imap.ssl", true);
        imapUsername = getString(config, "mail-handler.imap.username", "");
        imapPassword = getString(config, "mail-handler.imap.password", "");
        imapPollInterval = getInt(config, "mail-handler.imap.poll-interval-seconds", 30);

        serverName = getString(config, "branding.server-name", "Retro Network");
        discordLink = getString(config, "branding.discord-link", "https://discord.gg/retro");
        documentationLink = getString(config, "branding.documentation-link", "https://docs.ajaretro.dev");
        forumLink = getString(config, "branding.forum-link", "https://forum.ajaretro.dev");
        securitySecretToken = getString(config, "security.secret-token", "");
        multiServer = getBoolean(config, "multi-server.enabled", false);
        licenseKey = getString(config, "license-key", "");
    }

    private String getString(Map<String, Object> map, String path, String def) {
        Object val = getPath(map, path);
        return val != null ? val.toString() : def;
    }

    private int getInt(Map<String, Object> map, String path, int def) {
        Object val = getPath(map, path);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(val.toString());
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private boolean getBoolean(Map<String, Object> map, String path, boolean def) {
        Object val = getPath(map, path);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val != null) {
            return Boolean.parseBoolean(val.toString());
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String path) {
        Object val = getPath(map, path);
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                if (o != null) result.add(o.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Object getPath(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return null;
            }
        }
        return current.get(parts[parts.length - 1]);
    }

    public String getMessage(String key, String def) {
        String msg = getString(messagesConfig, key, def);
        return translateColorCodes(msg);
    }

    public List<String> getMessageList(String key) {
        List<String> raw = getStringList(messagesConfig, key);
        List<String> formatted = new ArrayList<>();
        for (String line : raw) {
            formatted.add(translateColorCodes(line));
        }
        return formatted;
    }

    private String translateColorCodes(String text) {
        if (text == null) return null;
        char[] b = text.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }
}
