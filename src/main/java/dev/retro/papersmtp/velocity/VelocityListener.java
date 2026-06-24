package dev.retro.papersmtp.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VelocityListener {
    private final VelocityPaperSMTP plugin;

    public VelocityListener(VelocityPaperSMTP plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(VelocityPaperSMTP.IDENTIFIER)) {
            return;
        }

        // Only allow messages from backend servers
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(event.getData());
            DataInputStream in = new DataInputStream(stream);
            String action = in.readUTF();
            String uuidStr = in.readUTF();
            String payload = in.readUTF();

            UUID uuid = UUID.fromString(uuidStr);
            Player player = plugin.getServer().getPlayer(uuid).orElse(null);

            if (action.equalsIgnoreCase("add")) {
                String[] commands = payload.split("\n");
                for (RegisteredServer registeredServer : plugin.getServer().getAllServers()) {
                    String serverName = registeredServer.getServerInfo().getName();
                    for (String cmd : commands) {
                        if (!cmd.trim().isEmpty()) {
                            plugin.getQueueDatabaseManager().addCommand(uuid, serverName, cmd.trim());
                        }
                    }
                }
                plugin.getLogger().info("Queued rewards commands for player " + uuidStr + " across " + plugin.getServer().getAllServers().size() + " servers.");

                if (player != null && player.getCurrentServer().isPresent()) {
                    pushPendingCommands(player);
                }
            } else if (action.equalsIgnoreCase("request")) {
                if (player != null) {
                    pushPendingCommands(player);
                }
            } else if (action.equalsIgnoreCase("confirm")) {
                List<Integer> ids = new ArrayList<>();
                for (String idStr : payload.split(",")) {
                    if (!idStr.trim().isEmpty()) {
                        ids.add(Integer.parseInt(idStr.trim()));
                    }
                }
                plugin.getQueueDatabaseManager().removeCommands(ids);
                plugin.getLogger().info("Successfully cleared " + ids.size() + " executed command(s) from the queue for " + uuidStr);
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error processing proxy queue message: " + e.getMessage(), e);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> pushPendingCommands(player))
                .delay(1500, TimeUnit.MILLISECONDS)
                .schedule();
    }

    private void pushPendingCommands(Player player) {
        if (player == null) return;
        
        ServerConnection serverConn = player.getCurrentServer().orElse(null);
        if (serverConn == null) return;

        String serverName = serverConn.getServerInfo().getName();
        List<VelocityDatabaseManager.QueuedCommand> pending = plugin.getQueueDatabaseManager().getPendingCommands(player.getUniqueId(), serverName);
        if (pending.isEmpty()) return;

        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);

            out.writeUTF("execute");
            out.writeUTF(player.getUniqueId().toString());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pending.size(); i++) {
                sb.append(pending.get(i).id).append(":").append(pending.get(i).command);
                if (i < pending.size() - 1) sb.append("\n");
            }
            out.writeUTF(sb.toString());

            serverConn.sendPluginMessage(VelocityPaperSMTP.IDENTIFIER, stream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to send pending commands to " + serverName + ": " + e.getMessage());
        }
    }
}
