package dev.retro.papersmtp.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.retro.papersmtp.MailPluginInterface;
import dev.retro.papersmtp.config.PluginConfig;
import dev.retro.papersmtp.database.DatabaseManager;
import dev.retro.papersmtp.smtp.IMAPListener;
import dev.retro.papersmtp.smtp.MailHandlerServer;
import dev.retro.papersmtp.smtp.SMTPManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

@Plugin(id = "retromail", name = "RetroMail", version = "1.0.6-nightMare", description = "Secure double-opt-in SMTP gateway integration that verifies player emails in-game and coordinates multi-server proxy queues.", authors = {"Retro"})
public class VelocityPaperSMTP implements MailPluginInterface {
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("papersmtp:queue");

    private final ProxyServer server;
    private final org.slf4j.Logger logger;
    private final Path dataDirectory;
    private final java.util.logging.Logger jdkLogger;

    private VelocityDatabaseManager queueDatabaseManager;
    private DatabaseManager databaseManager;
    private PluginConfig pluginConfig;
    private SMTPManager smtpManager;
    private IMAPListener imapListener;
    private MailHandlerServer mailHandlerServer;

    @Inject
    public VelocityPaperSMTP(ProxyServer server, org.slf4j.Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.jdkLogger = java.util.logging.Logger.getLogger("RetroMail");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Enforce config directory creation
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            // Generate secure random key based on time and port
            int port = 25577; // default fallback
            java.net.InetSocketAddress addr = server.getBoundAddress();
            if (addr != null) {
                port = addr.getPort();
            }
            
            long time = System.currentTimeMillis();
            String raw = time + ":" + port + ":" + new java.security.SecureRandom().nextLong();
            String generatedKey;
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                generatedKey = sb.toString();
            } catch (Exception e) {
                generatedKey = java.util.UUID.randomUUID().toString().replace("-", "");
            }

            try (InputStream in = getResource("config.yml")) {
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
                logger.error("Failed to copy default config.yml on Velocity", e);
            }
            
            // Read and replace secret-token line in config.yml
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                boolean foundSec = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("security:")) {
                        foundSec = true;
                    }
                    if (foundSec && line.startsWith("secret-token:")) {
                        lines.set(i, "  # Auto-generated secret key (Uncomment to activate):");
                        lines.add(i + 1, "  # secret-token: \"" + generatedKey + "\"");
                        lines.add(i + 2, "  secret-token: \"\"");
                        break;
                    }
                }
                java.nio.file.Files.write(configFile.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error("Failed to write auto-generated token in config.yml on Velocity", e);
            }
        }

        // Load platform-independent config
        pluginConfig = new PluginConfig(this);

        if (pluginConfig.securitySecretToken == null || pluginConfig.securitySecretToken.isEmpty()) {
            logger.error("=============================================================");
            logger.error("RetroMail proxy module REQUIRES a security.secret-token!");
            logger.error("For security, communication between proxy and backend must be");
            logger.error("authenticated. Please set secret-token in your config.yml.");
            logger.error("The plugin has generated a unique key commented out in config.");
            logger.error("Plugin will go dark (no commands will sync).");
            logger.error("=============================================================");
            return;
        }

        // Setup databases
        queueDatabaseManager = new VelocityDatabaseManager(this, logger, dataDirectory);
        queueDatabaseManager.setup();

        databaseManager = new DatabaseManager(this);
        databaseManager.setup();

        // Setup managers
        smtpManager = new SMTPManager(this);

        // Register channel and listener
        server.getChannelRegistrar().register(IDENTIFIER);
        server.getEventManager().register(this, new VelocityListener(this));

        // Start Web Handler and IMAP listener if enabled
        if (pluginConfig.mailHandlerEnabled) {
            mailHandlerServer = new MailHandlerServer(this);
            mailHandlerServer.start();

            imapListener = new IMAPListener(this);
            imapListener.start();
        }

        logger.info("=============================================");
        logger.info("VelocityRetroMail (Proxy Version) Enabled!");
        logger.info("Mail Sync Queue & Web Dashboard Server by AJA_RETRO");
        logger.info("=============================================");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (mailHandlerServer != null) {
            mailHandlerServer.stop();
        }
        if (imapListener != null) {
            imapListener.stop();
        }
        if (smtpManager != null) {
            smtpManager.shutdown();
        }
        if (queueDatabaseManager != null) {
            queueDatabaseManager.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        server.getChannelRegistrar().unregister(IDENTIFIER);
        logger.info("VelocityRetroMail Disabled.");
    }

    public ProxyServer getServer() {
        return server;
    }

    // --- MailPluginInterface Implementation ---

    @Override
    public File getDataFolder() {
        return dataDirectory.toFile();
    }

    @Override
    public java.util.logging.Logger getLogger() {
        return jdkLogger;
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    @Override
    public SMTPManager getSMTPManager() {
        return smtpManager;
    }

    @Override
    public InputStream getResource(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    @Override
    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found.");
        }

        File outFile = new File(getDataFolder(), resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(getDataFolder(), resourcePath.substring(0, lastIndex >= 0 ? lastIndex : 0));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                in.close();
            } else {
                in.close();
            }
        } catch (Exception e) {
            jdkLogger.log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, e);
        }
    }

    @Override
    public String getPlayerName(UUID uuid) {
        return server.getPlayer(uuid).map(p -> p.getUsername()).orElse("Player");
    }

    public VelocityDatabaseManager getQueueDatabaseManager() {
        return queueDatabaseManager;
    }

    @Override
    public void triggerVerificationRewards(UUID uuid) {
        List<String> commands = getPluginConfig().rewardCommands;
        if (getPluginConfig().rewardsEnabled && !commands.isEmpty()) {
            for (com.velocitypowered.api.proxy.server.RegisteredServer s : server.getAllServers()) {
                String serverName = s.getServerInfo().getName();
                for (String cmd : commands) {
                    getQueueDatabaseManager().addCommand(uuid, serverName, cmd);
                }
            }
            server.getPlayer(uuid).ifPresent(p -> {
                triggerVelocityCommandPush(p);
            });
        }
    }

    private void triggerVelocityCommandPush(com.velocitypowered.api.proxy.Player player) {
        com.velocitypowered.api.proxy.ServerConnection serverConn = player.getCurrentServer().orElse(null);
        if (serverConn == null) return;
        String serverName = serverConn.getServerInfo().getName();
        List<dev.retro.papersmtp.velocity.VelocityDatabaseManager.QueuedCommand> pending = getQueueDatabaseManager().getPendingCommands(player.getUniqueId(), serverName);
        if (pending.isEmpty()) return;

        try {
            java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(stream);
            out.writeUTF("execute");
            out.writeUTF(player.getUniqueId().toString());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pending.size(); i++) {
                sb.append(pending.get(i).id).append(":").append(pending.get(i).command);
                if (i < pending.size() - 1) sb.append("\n");
            }
            out.writeUTF(sb.toString());
            serverConn.sendPluginMessage(IDENTIFIER, stream.toByteArray());
        } catch (Exception e) {
            jdkLogger.log(Level.SEVERE, "Failed to push commands to " + serverName + ": " + e.getMessage());
        }
    }
}

