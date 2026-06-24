package dev.retro.papersmtp;

import dev.retro.papersmtp.config.PluginConfig;
import dev.retro.papersmtp.database.DatabaseManager;
import dev.retro.papersmtp.smtp.SMTPManager;
import java.io.File;
import java.io.InputStream;
import java.util.UUID;
import java.util.logging.Logger;

public interface MailPluginInterface {
    File getDataFolder();
    Logger getLogger();
    DatabaseManager getDatabaseManager();
    PluginConfig getPluginConfig();
    SMTPManager getSMTPManager();
    void saveResource(String resourcePath, boolean replace);
    InputStream getResource(String filename);
    String getPlayerName(UUID uuid);
}
