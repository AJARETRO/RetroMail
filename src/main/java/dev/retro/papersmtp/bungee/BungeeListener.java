package dev.retro.papersmtp.bungee;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BungeeListener implements Listener {
    private final BungeePaperSMTP plugin;

    public BungeeListener(BungeePaperSMTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("papersmtp:queue")) {
            return;
        }

        // Only allow messages from backend servers
        if (!(event.getSender() instanceof net.md_5.bungee.api.connection.Server)) {
            return;
        }

        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(event.getData());
            DataInputStream in = new DataInputStream(stream);
            String action = in.readUTF();
            String uuidStr = in.readUTF();
            String payload = in.readUTF();

            UUID uuid = UUID.fromString(uuidStr);
            ProxiedPlayer player = plugin.getProxy().getPlayer(uuid);

            if (action.equalsIgnoreCase("add")) {
                // Command format payload: a list of commands separated by \n
                String[] commands = payload.split("\n");
                for (String serverName : plugin.getProxy().getServers().keySet()) {
                    for (String cmd : commands) {
                        if (!cmd.trim().isEmpty()) {
                            plugin.getDatabaseManager().addCommand(uuid, serverName, cmd.trim());
                        }
                    }
                }
                plugin.getLogger().info("Queued rewards commands for player " + uuidStr + " across " + plugin.getProxy().getServers().size() + " servers.");
                
                // Immediately try to push to their current server if online
                if (player != null && player.getServer() != null) {
                    pushPendingCommands(player);
                }
            } else if (action.equalsIgnoreCase("request")) {
                if (player != null) {
                    pushPendingCommands(player);
                }
            } else if (action.equalsIgnoreCase("confirm")) {
                // Payload is a list of integer IDs separated by commas
                List<Integer> ids = new ArrayList<>();
                for (String idStr : payload.split(",")) {
                    if (!idStr.trim().isEmpty()) {
                        ids.add(Integer.parseInt(idStr.trim()));
                    }
                }
                plugin.getDatabaseManager().removeCommands(ids);
                plugin.getLogger().info("Successfully cleared " + ids.size() + " executed command(s) from the queue for " + uuidStr);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing proxy queue message: " + e.getMessage(), e);
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        final ProxiedPlayer player = event.getPlayer();
        // Delay to allow Spigot server to load player profile completely
        plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                pushPendingCommands(player);
            }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    private void pushPendingCommands(ProxiedPlayer player) {
        if (player == null || player.getServer() == null) return;

        String serverName = player.getServer().getInfo().getName();
        List<BungeeDatabaseManager.QueuedCommand> pending = plugin.getDatabaseManager().getPendingCommands(player.getUniqueId(), serverName);
        if (pending.isEmpty()) return;

        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            // Build execute payload: id1:command1\nid2:command2\n...
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pending.size(); i++) {
                sb.append(pending.get(i).id).append(":").append(pending.get(i).command);
                if (i < pending.size() - 1) sb.append("\n");
            }

            String action = "execute";
            String uuidStr = player.getUniqueId().toString();
            String payload = sb.toString();
            long timestamp = System.currentTimeMillis();
            String signature = dev.retro.papersmtp.compatibility.SignatureUtil.calculateSignature(
                    action + ":" + uuidStr + ":" + payload + ":" + timestamp,
                    plugin.getSecuritySecretToken()
            );

            // If secret token is set, wrap the payload with security signature and timestamp
            if (plugin.getSecuritySecretToken() != null && !plugin.getSecuritySecretToken().isEmpty()) {
                out.writeUTF("secure-msg");
                out.writeUTF(signature);
                out.writeLong(timestamp);
            }

            out.writeUTF(action);
            out.writeUTF(uuidStr);
            out.writeUTF(payload);

            player.getServer().sendData("papersmtp:queue", stream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send pending commands to " + serverName + ": " + e.getMessage());
        }
    }
}
