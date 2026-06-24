package dev.retro.papersmtp.smtp;

import dev.retro.papersmtp.MailPluginInterface;
import dev.retro.papersmtp.config.PluginConfig;
import dev.retro.papersmtp.database.DatabaseManager;
import java.util.List;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SMTPManager {
    private final MailPluginInterface plugin;
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RetroMail-OutboundQueue");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private boolean isProcessing = false;

    public SMTPManager(MailPluginInterface plugin) {
        this.plugin = plugin;
        startQueueProcessor();
    }

    public void startQueueProcessor() {
        // Run retry processor every 60 seconds
        scheduler.scheduleWithFixedDelay(this::processOutboundQueue, 15, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public void processOutboundQueue() {
        if (isProcessing) return;
        isProcessing = true;
        try {
            List<DatabaseManager.OutboundMail> pending = plugin.getDatabaseManager().getPendingOutboundMails();
            if (pending.isEmpty()) {
                isProcessing = false;
                return;
            }
            plugin.getLogger().info("Processing " + pending.size() + " queued outbound email(s)...");
            for (DatabaseManager.OutboundMail mail : pending) {
                try {
                    sendEmail(mail.fromEmail, mail.fromName, mail.toEmail, mail.subject, mail.body, mail.isHtml);
                    plugin.getDatabaseManager().removeOutboundMail(mail.id);
                    plugin.getLogger().info("Successfully delivered queued email to " + mail.toEmail);
                    plugin.getDatabaseManager().saveIncomingMail(mail.fromEmail, mail.toEmail, "[DELIVERED] " + mail.subject, mail.body, mail.isHtml);
                } catch (Exception e) {
                    int nextRetryDelay = (mail.retries + 1) * 60; // Exponential backup
                    plugin.getDatabaseManager().incrementMailRetry(mail.id, nextRetryDelay);
                    plugin.getLogger().log(Level.WARNING, "Failed to deliver queued email to " + mail.toEmail + " (Attempt " + (mail.retries + 1) + "/5). Retry in " + nextRetryDelay + "s. Error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error while processing outbound email queue: " + e.getMessage());
        } finally {
            isProcessing = false;
        }
    }

    private boolean isBukkit() {
        try {
            Class.forName("org.bukkit.Bukkit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void sendEmailAsync(String toEmail, String subject, String body, boolean isHtml) {
        sendEmailAsync(plugin.getPluginConfig().smtpFromAddress, plugin.getPluginConfig().smtpFromName, toEmail, subject, body, isHtml);
    }

    public void sendEmailAsync(String fromEmail, String fromName, String toEmail, String subject, String body, boolean isHtml) {
        CompletableFuture.runAsync(() -> {
            try {
                sendEmail(fromEmail, fromName, toEmail, subject, body, isHtml);
                plugin.getDatabaseManager().saveIncomingMail(fromEmail, toEmail, "[SENT] " + subject, body, isHtml);
                plugin.getLogger().log(Level.INFO, "Email successfully sent to " + toEmail + " with subject: " + subject);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send async email to " + toEmail + ", queueing for retry: " + e.getMessage());
                plugin.getDatabaseManager().addOutboundMailToQueue(fromEmail, fromName, toEmail, subject, body, isHtml ? 1 : 0);
                plugin.getDatabaseManager().saveIncomingMail(fromEmail, toEmail, "[QUEUED] " + subject, body, isHtml);
            }
        });
    }

    public void sendVerificationEmailAsync(String toEmail, UUID playerUuid, String code) {
        CompletableFuture.runAsync(() -> {
            PluginConfig config = plugin.getPluginConfig();
            String subject = config.verificationSubject;
            String body = "";
            boolean isHtml = false;

            String playerName = plugin.getPlayerName(playerUuid);

            if (config.useHtml) {
                File file = new File(plugin.getDataFolder(), config.htmlFile);
                if (file.exists()) {
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        body = new String(bytes, StandardCharsets.UTF_8);
                        isHtml = true;
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to read HTML template file: " + e.getMessage());
                    }
                }
                if (!isHtml) {
                    try (InputStream in = plugin.getResource(config.htmlFile)) {
                        if (in != null) {
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }
                            body = out.toString("UTF-8");
                            isHtml = true;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to read HTML template resource: " + e.getMessage());
                    }
                }
            }

            if (!isHtml) {
                body = config.verificationBody;
            }

            // Custom placeholder replacement
            String link = "https://" + config.mailHandlerDomain + ":" + config.mailHandlerPort + "/api/verify-link?uuid=" + playerUuid + "&code=" + code;
            body = body.replace("{player}", playerName).replace("{code}", code).replace("{link}", link);
            subject = subject.replace("{player}", playerName).replace("{code}", code).replace("{link}", link);

            // PlaceholderAPI support if enabled and running on Bukkit
            if (isBukkit() && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(playerUuid);
                    body = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, body);
                    subject = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, subject);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "PlaceholderAPI failed to parse verification placeholder: " + t.getMessage());
                }
            }

            try {
                sendEmail(toEmail, subject, body, isHtml);
                plugin.getDatabaseManager().saveIncomingMail(config.smtpFromAddress, toEmail, "[SENT] " + subject, body, isHtml);
                plugin.getLogger().log(Level.INFO, "Verification email successfully sent to " + toEmail + " for player " + playerName + " (Code: " + code + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send verification email to " + toEmail + ", queueing for retry: " + e.getMessage());
                plugin.getDatabaseManager().addOutboundMailToQueue(config.smtpFromAddress, config.smtpFromName, toEmail, subject, body, isHtml ? 1 : 0);
                plugin.getDatabaseManager().saveIncomingMail(config.smtpFromAddress, toEmail, "[QUEUED] " + subject, body, isHtml);
            }
        });
    }

    public void sendMassEmailAsync(List<dev.retro.papersmtp.database.DatabaseManager.Subscriber> subscribers, String subject, String body, boolean isHtml) {
        CompletableFuture.runAsync(() -> {
            plugin.getLogger().info("Sending mass newsletter to " + subscribers.size() + " subscribers...");
            int successCount = 0;
            for (dev.retro.papersmtp.database.DatabaseManager.Subscriber sub : subscribers) {
                try {
                    String playerName = plugin.getPlayerName(sub.uuid);

                    String personalBody = body.replace("{player}", playerName);
                    String personalSubject = subject.replace("{player}", playerName);

                    // PlaceholderAPI support if enabled and running on Bukkit
                    if (isBukkit() && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        try {
                            org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(sub.uuid);
                            personalBody = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, personalBody);
                            personalSubject = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, personalSubject);
                        } catch (Throwable t) {
                            plugin.getLogger().log(Level.WARNING, "PlaceholderAPI failed to parse mass mail placeholder: " + t.getMessage());
                        }
                    }

                    sendEmail(sub.email, personalSubject, personalBody, isHtml);
                    successCount++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to send mass mail to " + sub.email + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Newsletter mass mail completed: " + successCount + "/" + subscribers.size() + " sent successfully.");
        });
    }

    public void sendEmail(String toEmail, String subject, String body, boolean isHtml) {
        PluginConfig config = plugin.getPluginConfig();
        sendEmail(config.smtpFromAddress, config.smtpFromName, toEmail, subject, body, isHtml);
    }

    public void sendEmail(String fromEmail, String fromName, String toEmail, String subject, String body, boolean isHtml) {
        PluginConfig config = plugin.getPluginConfig();
        
        Properties props = new Properties();
        props.put("mail.smtp.host", config.smtpHost);
        props.put("mail.smtp.port", String.valueOf(config.smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        
        if (config.smtpStarttls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        if (config.smtpSsl) {
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.smtpPort));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        }

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.smtpUsername, config.smtpPassword);
            }
        };

        Session session = Session.getInstance(props, auth);

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(config.smtpFromAddress, fromName));
            if (fromEmail != null && !fromEmail.equalsIgnoreCase(config.smtpFromAddress)) {
                msg.setReplyTo(new javax.mail.Address[] { new InternetAddress(fromEmail, fromName) });
            }
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            msg.setSubject(subject, "UTF-8");
            
            String formattedBody = getFormattedEmailBody(subject, body, isHtml);
            msg.setContent(formattedBody, "text/html; charset=utf-8");
            msg.setHeader("Content-Transfer-Encoding", "quoted-printable");
            
            Transport.send(msg);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send email to " + toEmail + ": " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public String getFormattedEmailBody(String subject, String body, boolean isHtml) {
        String finalBody = body;
        boolean wrapInTemplate = false;
        
        if (!isHtml) {
            // Convert plain text newlines to html line breaks
            finalBody = finalBody.replace("\n", "<br>");
            wrapInTemplate = true;
        } else {
            String lower = finalBody.toLowerCase();
            if (!lower.contains("<html") && !lower.contains("<!doctype")) {
                wrapInTemplate = true;
            }
        }

        if (wrapInTemplate) {
            finalBody = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>" + subject + "</title>\n" +
                "</head>\n" +
                "<body style=\"background-color: #0f1115; font-family: 'Inter', Helvetica, Arial, sans-serif; margin: 0; padding: 0; -webkit-font-smoothing: antialiased;\">\n" +
                "    <div class=\"wrapper\" style=\"background-color: #0f1115; width: 100%; padding: 40px 0;\">\n" +
                "        <table class=\"container\" align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 600px; margin: 0 auto; background-color: #161920; border-radius: 12px; border: 1px solid #232835; overflow: hidden; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.5);\">\n" +
                "            <tr>\n" +
                "                <td class=\"header\" style=\"background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%); padding: 35px 30px; text-align: center;\">\n" +
                "                    <h1 style=\"color: #ffffff; margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px;\">" + subject + "</h1>\n" +
                "                </td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "                <td class=\"content\" style=\"padding: 40px 30px; color: #d1d5db; line-height: 1.6; font-size: 15px;\">\n" +
                "                    " + finalBody + "\n" +
                "                </td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "                <td class=\"footer\" style=\"background-color: #0f1115; padding: 25px 30px; text-align: center; border-top: 1px solid #232835;\">\n" +
                "                    <p style=\"margin: 0; color: #6b7280; font-size: 12px;\">&copy; 2026 Retro Network. All rights reserved.</p>\n" +
                "                </td>\n" +
                "            </tr>\n" +
                "        </table>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
        }

        // Remove any previous branding blocks if they exist to prevent duplicates or bypass attempts
        finalBody = finalBody.replaceAll("(?s)<div[^>]*>\\s*Mail System by.*?AJA_R3TR0.*?</div>\\s*</div>", "");
        finalBody = finalBody.replaceAll("(?s)<div[^>]*>\\s*Mail System by.*?AJA_RETRO.*?</div>\\s*</div>", "");
        
        String brandingHtml = 
            "<div style=\"margin-top: 30px; border-top: 1px solid #232835; padding-top: 20px; text-align: center; font-family: sans-serif;\">\n" +
            "    <div style=\"display: inline-block; background-color: #1a1e26; border-radius: 8px; padding: 15px; border: 1px solid #232835; text-align: center;\">\n" +
            "        <div style=\"font-size: 14px; color: #9ca3af; font-weight: 500; margin-bottom: 8px;\">\n" +
            "            Mail System by <span style=\"color: #a855f7; font-weight: 700;\">AJA_RETRO</span>\n" +
            "        </div>\n" +
            "        <div>\n" +
            "            <a href=\"https://modrinth.com/user/AJA_R3TR0\" target=\"_blank\" style=\"color: #6366f1; text-decoration: none; font-size: 13px; font-weight: 600; background-color: #2e354f; padding: 6px 12px; border-radius: 4px; display: inline-block;\">https://modrinth.com/user/AJA_R3TR0</a>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</div>";
        if (finalBody.contains("</body>")) {
            finalBody = finalBody.replace("</body>", brandingHtml + "\n</body>");
        } else if (finalBody.contains("</div>")) {
            int lastDiv = finalBody.lastIndexOf("</div>");
            finalBody = finalBody.substring(0, lastDiv) + brandingHtml + "\n" + finalBody.substring(lastDiv);
        } else {
            finalBody = finalBody + brandingHtml;
        }

        return finalBody;
    }
}
