package dev.retro.papersmtp.bungee;

import net.md_5.bungee.api.plugin.Plugin;

public class BungeePaperSMTP extends Plugin {
    private BungeeDatabaseManager databaseManager;

    @Override
    public void onEnable() {
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
}
