package dev.retro.papersmtp.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

public class BungeePaperSMTP extends Plugin {
    private BungeeDatabaseManager databaseManager;
    private String securitySecretToken = "";

    @Override
    public void onEnable() {
        loadConfig();
        databaseManager = new BungeeDatabaseManager(this);
        databaseManager.setup();

        // Register custom plugin channel
        getProxy().registerChannel("papersmtp:queue");

        // Register listener
        getProxy().getPluginManager().registerListener(this, new BungeeListener(this));

        getLogger().info("=============================================");
        getLogger().info("BungeeRetroMail (Proxy Version) Enabled!");
        getLogger().info("Mail Sync Queue by AJA_RETRO");
        getLogger().info("=============================================");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getProxy().unregisterChannel("papersmtp:queue");
        getLogger().info("BungeeRetroMail Disabled.");
    }

    public BungeeDatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public String getSecuritySecretToken() {
        return securitySecretToken;
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            File file = new File(getDataFolder(), "config.yml");
            if (!file.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            }
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            securitySecretToken = config.getString("security.secret-token", "");
        } catch (Exception e) {
            getLogger().warning("Failed to load config.yml on Bungee: " + e.getMessage());
        }
    }
}
