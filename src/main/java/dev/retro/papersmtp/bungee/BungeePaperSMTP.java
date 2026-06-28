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
        
        if (securitySecretToken == null || securitySecretToken.isEmpty()) {
            getLogger().severe("=============================================================");
            getLogger().severe("RetroMail proxy module REQUIRES a security.secret-token!");
            getLogger().severe("For security, communication between proxy and backend must be");
            getLogger().severe("authenticated. Please set secret-token in your config.yml.");
            getLogger().severe("The plugin has generated a unique key commented out in config.");
            getLogger().severe("Plugin will go dark (no commands will sync).");
            getLogger().severe("=============================================================");
            return;
        }

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
                int port = 25577; // default fallback
                try {
                    port = getProxy().getConfig().getListeners().iterator().next().getHost().getPort();
                } catch (Exception ignored) {}
                
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

                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
                
                // Read and replace secret-token line
                java.util.List<String> lines = Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
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
                Files.write(file.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
            }
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            securitySecretToken = config.getString("security.secret-token", "");
        } catch (Exception e) {
            getLogger().warning("Failed to load config.yml on Bungee: " + e.getMessage());
        }
    }
}
