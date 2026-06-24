package dev.retro.papersmtp;

import dev.retro.papersmtp.commands.EmailCommand;
import dev.retro.papersmtp.commands.MassEmailCommand;
import dev.retro.papersmtp.config.PluginConfig;
import dev.retro.papersmtp.database.DatabaseManager;
import dev.retro.papersmtp.gui.EmailGUI;
import dev.retro.papersmtp.listeners.PlayerListener;
import dev.retro.papersmtp.smtp.SMTPManager;
import dev.retro.papersmtp.smtp.IMAPListener;
import dev.retro.papersmtp.smtp.MailHandlerServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PaperSMTP extends JavaPlugin implements PluginMessageListener, MailPluginInterface {
    private PluginConfig pluginConfig;
    private DatabaseManager databaseManager;
    private SMTPManager smtpManager;
    private EmailGUI emailGUI;
    private IMAPListener imapListener;
    private MailHandlerServer mailHandlerServer;
    private UpdateChecker updateChecker;

    private final ConcurrentHashMap<UUID, Boolean> pendingEmailInputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingCodeInputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> emailCooldowns = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // Load configurations
        pluginConfig = new PluginConfig(this);
        saveResource("email_template.html", false);
        saveResource("announcement.html", false);
        saveResource("maintenance.html", false);
        saveResource("new_season.html", false);
        saveResource("staff_created.html", false);

        // Save default clean templates inside templates/ directory (force update/overwrite on every boot)
        saveResource("templates/email_template.html", true);
        saveResource("templates/announcement.html", true);
        saveResource("templates/maintenance.html", true);
        saveResource("templates/new_season.html", true);
        saveResource("templates/staff_created.html", true);

        // Setup Database
        databaseManager = new DatabaseManager(this);
        databaseManager.setup();

        // Setup SMTP Manager
        smtpManager = new SMTPManager(this);

        // Setup IMAP Listener
        imapListener = new IMAPListener(this);
        imapListener.start();

        // Setup Mail Handler Server
        mailHandlerServer = new MailHandlerServer(this);
        mailHandlerServer.start();

        // Setup GUI
        emailGUI = new EmailGUI(this);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Register Commands
        if (getCommand("email") != null) {
            EmailCommand emailCommand = new EmailCommand(this);
            getCommand("email").setExecutor(emailCommand);
            getCommand("email").setTabCompleter(emailCommand);
        }
        if (getCommand("mass-email") != null) {
            MassEmailCommand massEmailCommand = new MassEmailCommand(this);
            getCommand("mass-email").setExecutor(massEmailCommand);
            getCommand("mass-email").setTabCompleter(massEmailCommand);
        }

        // Register BungeeCord plugin messaging channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        
        // Register custom sync queue messaging channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, "papersmtp:queue");
        getServer().getMessenger().registerIncomingPluginChannel(this, "papersmtp:queue", this);

        // Setup bStats
        try {
            int pluginId = 31421;
            new org.bstats.bukkit.Metrics(this, pluginId);
            getLogger().info("bStats metrics initialized successfully for ID 31421.");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats: " + e.getMessage());
        }

        // Setup Update Checker
        updateChecker = new UpdateChecker(getDescription().getVersion());
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            updateChecker.checkForUpdates();
            if (updateChecker.isUpdateAvailable()) {
                getLogger().warning("====================================================");
                getLogger().warning("A new update for RetroMail is available!");
                getLogger().warning("Current version: " + getDescription().getVersion());
                getLogger().warning("Latest version: " + updateChecker.getLatestVersion());
                getLogger().warning("Download it here: https://github.com/AJARETRO/RetroMail/releases");
                getLogger().warning("====================================================");
            }
        });

        getLogger().info("=============================================");
        getLogger().info("RetroMail has been successfully enabled!");
        getLogger().info("Mail System by AJA_RETRO");
        getLogger().info("https://modrinth.com/user/AJA_R3TR0");
        getLogger().info("=============================================");
    }

    @Override
    public void onDisable() {
        if (imapListener != null) {
            imapListener.stop();
        }
        if (mailHandlerServer != null) {
            mailHandlerServer.stop();
        }
        if (smtpManager != null) {
            smtpManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        // Unregister BungeeCord plugin messaging channels
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().unregisterIncomingPluginChannel(this, "BungeeCord", this);
        
        // Unregister custom sync queue messaging channels
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "papersmtp:queue");
        getServer().getMessenger().unregisterIncomingPluginChannel(this, "papersmtp:queue", this);
        
        getLogger().info("RetroMail has been disabled.");
    }

    public void broadcastSyncMessage(String subCommand, String playerUuid, String email) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeUTF("Forward");
            dos.writeUTF("ONLINE");
            dos.writeUTF("PaperSMTPSync");

            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgDos = new DataOutputStream(msgBytes);
            msgDos.writeUTF(subCommand);
            msgDos.writeUTF(playerUuid);
            msgDos.writeUTF(email);

            byte[] data = msgBytes.toByteArray();
            dos.writeShort(data.length);
            dos.write(data);

            // Send via any online player
            Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (player != null) {
                player.sendPluginMessage(this, "BungeeCord", baos.toByteArray());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send BungeeCord sync message: " + e.getMessage());
        }
    }

    public void queueRewardsOnBungee(UUID uuid, List<String> commands) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF("add");
            out.writeUTF(uuid.toString());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < commands.size(); i++) {
                sb.append(commands.get(i));
                if (i < commands.size() - 1) sb.append("\n");
            }
            out.writeUTF(sb.toString());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendPluginMessage(this, "papersmtp:queue", stream.toByteArray());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send queue add message to Bungee: " + e.getMessage());
        }
    }

    public void requestPendingCommands(UUID uuid) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF("request");
            out.writeUTF(uuid.toString());
            out.writeUTF(""); // empty payload

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendPluginMessage(this, "papersmtp:queue", stream.toByteArray());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send queue request message to Bungee: " + e.getMessage());
        }
    }

    private void sendConfirmMessage(UUID uuid, List<Integer> ids) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF("confirm");
            out.writeUTF(uuid.toString());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                sb.append(ids.get(i));
                if (i < ids.size() - 1) sb.append(",");
            }
            out.writeUTF(sb.toString());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendPluginMessage(this, "papersmtp:queue", stream.toByteArray());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to send command confirmation to Bungee: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("BungeeCord")) {
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
                String subChannel = in.readUTF();
                if (subChannel.equals("PaperSMTPSync")) {
                    short len = in.readShort();
                    byte[] msgbytes = new byte[len];
                    in.readFully(msgbytes);

                    DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                    String command = msgIn.readUTF();
                    String uuidStr = msgIn.readUTF();
                    String email = msgIn.readUTF();
                    
                    UUID uuid = UUID.fromString(uuidStr);

                    if (command.equalsIgnoreCase("verify")) {
                        databaseManager.verifySubscriptionLocally(uuid, email);
                    } else if (command.equalsIgnoreCase("unsubscribe")) {
                        databaseManager.unsubscribeLocally(uuid);
                    } else if (command.equalsIgnoreCase("pending")) {
                        String[] parts = email.split(":", 2);
                        if (parts.length == 2) {
                            databaseManager.setPendingSubscription(uuid, parts[0], parts[1]);
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        } else if (channel.equals("papersmtp:queue")) {
            handleQueueMessage(message);
        }
    }

    private void handleQueueMessage(byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String action = in.readUTF();
            String uuidStr = in.readUTF();
            String payload = in.readUTF();

            UUID uuid = UUID.fromString(uuidStr);
            Player target = Bukkit.getPlayer(uuid);

            if (action.equalsIgnoreCase("execute")) {
                String[] lines = payload.split("\n");
                final java.util.List<Integer> executedIds = new java.util.ArrayList<>();

                dev.retro.papersmtp.compatibility.SchedulerUtil.runSync(this, () -> {
                    for (String line : lines) {
                        if (line.trim().isEmpty()) continue;
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            try {
                                int id = Integer.parseInt(parts[0].trim());
                                String cmd = parts[1].trim();

                                String targetName = (target != null) ? target.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                                if (targetName != null) {
                                    String parsedCmd = cmd.replace("{player}", targetName);
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                                }
                                executedIds.add(id);
                            } catch (Exception ex) {
                                getLogger().log(Level.WARNING, "Failed to execute queued command: " + line);
                            }
                        }
                    }

                    if (!executedIds.isEmpty()) {
                        sendConfirmMessage(uuid, executedIds);
                    }
                });
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error processing queue message from Bungee: " + e.getMessage());
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public SMTPManager getSMTPManager() {
        return smtpManager;
    }

    public EmailGUI getEmailGUI() {
        return emailGUI;
    }

    public IMAPListener getIMAPListener() {
        return imapListener;
    }

    public MailHandlerServer getMailHandlerServer() {
        return mailHandlerServer;
    }

    public ConcurrentHashMap<UUID, Boolean> getPendingEmailInputs() {
        return pendingEmailInputs;
    }

    public ConcurrentHashMap<UUID, Boolean> getPendingCodeInputs() {
        return pendingCodeInputs;
    }

    public ConcurrentHashMap<UUID, Long> getEmailCooldowns() {
        return emailCooldowns;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    @Override
    public String getPlayerName(UUID uuid) {
        org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return (player != null && player.getName() != null) ? player.getName() : "Player";
    }

    @Override
    public void triggerVerificationRewards(UUID uuid) {
        dev.retro.papersmtp.database.SubscriptionState subState = getDatabaseManager().getSubscriptionState(uuid);
        String email = (subState != null) ? subState.getEmail() : "";
        broadcastSyncMessage("verify", uuid.toString(), email);
        
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            if (getPluginConfig().rewardsEnabled) {
                for (String msg : getPluginConfig().rewardMessages) {
                    onlinePlayer.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
                }
                if (!getPluginConfig().rewardCommands.isEmpty()) {
                    queueRewardsOnBungee(uuid, getPluginConfig().rewardCommands);
                }
            }
        }
    }
}

