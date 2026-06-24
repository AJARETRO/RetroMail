package dev.retro.papersmtp.smtp;

import dev.retro.papersmtp.MailPluginInterface;
import dev.retro.papersmtp.config.PluginConfig;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import java.util.Properties;
import java.util.logging.Level;

public class IMAPListener {
    private final MailPluginInterface plugin;
    private boolean running = false;
    private Thread pollThread;

    public IMAPListener(MailPluginInterface plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.mailHandlerEnabled || config.imapHost == null || config.imapHost.isEmpty()) {
            return;
        }

        running = true;
        pollThread = new Thread(this::pollLoop, "PaperSMTP-IMAP-Poll-Thread");
        pollThread.start();
        plugin.getLogger().info("Started IMAP Email Polling listener.");
    }

    public void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        plugin.getLogger().info("Stopped IMAP Email Polling listener.");
    }

    private void pollLoop() {
        while (running) {
            try {
                pollInbox();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during IMAP poll: " + e.getMessage());
            }

            try {
                Thread.sleep(plugin.getPluginConfig().imapPollInterval * 1000L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void pollInbox() throws Exception {
        PluginConfig config = plugin.getPluginConfig();
        Properties properties = new Properties();
        properties.put("mail.store.protocol", config.imapSsl ? "imaps" : "imap");
        properties.put("mail.imap.host", config.imapHost);
        properties.put("mail.imap.port", String.valueOf(config.imapPort));

        Session session = Session.getDefaultInstance(properties, null);
        Store store = session.getStore(config.imapSsl ? "imaps" : "imap");
        store.connect(config.imapHost, config.imapUsername, config.imapPassword);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        // Search for UNSEEN (unread) messages
        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        for (Message message : messages) {
            try {
                processMessage(message);
                // Mark message as SEEN (read)
                message.setFlag(Flags.Flag.SEEN, true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to process incoming email: " + e.getMessage(), e);
            }
        }

        inbox.close(false);
        store.close();
    }

    private void processMessage(Message message) throws Exception {
        String from = "";
        Address[] fromAddrs = message.getFrom();
        if (fromAddrs != null && fromAddrs.length > 0) {
            from = fromAddrs[0].toString();
        }

        String to = "";
        Address[] toAddrs = message.getRecipients(Message.RecipientType.TO);
        if (toAddrs != null && toAddrs.length > 0) {
            to = toAddrs[0].toString();
        }

        String subject = message.getSubject();
        if (subject == null) subject = "";

        StringBuilder bodyBuilder = new StringBuilder();
        boolean isHtml = false;

        if (message.isMimeType("text/plain")) {
            bodyBuilder.append(message.getContent().toString());
        } else if (message.isMimeType("text/html")) {
            bodyBuilder.append(message.getContent().toString());
            isHtml = true;
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            isHtml = getTextFromMimeMultipart(mimeMultipart, bodyBuilder);
        }

        String body = bodyBuilder.toString();

        // Save incoming mail
        plugin.getDatabaseManager().saveIncomingMail(from, to, subject, body, isHtml);
    }

    private boolean getTextFromMimeMultipart(MimeMultipart mimeMultipart, StringBuilder builder) throws Exception {
        int count = mimeMultipart.getCount();
        boolean isHtml = false;
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                builder.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("text/html")) {
                String html = bodyPart.getContent().toString();
                builder.setLength(0); // prefer HTML formatting
                builder.append(html);
                isHtml = true;
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                isHtml = isHtml || getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent(), builder);
            }
        }
        return isHtml;
    }
}
