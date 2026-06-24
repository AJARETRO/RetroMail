package dev.retro.papersmtp.listeners;

import dev.retro.papersmtp.PaperSMTP;
import dev.retro.papersmtp.UpdateChecker;
import dev.retro.papersmtp.compatibility.CompatibilityUtil;
import dev.retro.papersmtp.database.SubscriptionState;
import dev.retro.papersmtp.gui.EmailGUIHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

public class PlayerListener implements Listener {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private final PaperSMTP plugin;

    public PlayerListener(PaperSMTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Request pending commands from proxy 1.5 seconds after joining
        dev.retro.papersmtp.compatibility.SchedulerUtil.runLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.requestPendingCommands(player.getUniqueId());
            }
        }, 30L);

        // Notify OP players of updates
        if (player.isOp() && plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
            player.sendMessage("§c§l[RetroMail] §aA new update is available! Current: §7" + 
                               plugin.getDescription().getVersion() + " §aLatest: §e" + 
                               plugin.getUpdateChecker().getLatestVersion());
            player.sendMessage("§aDownload it from: §bhttps://github.com/AJARETRO/RetroMail/releases");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Use custom EmailGUIHolder check instead of title comparison
        if (!(event.getInventory().getHolder() instanceof EmailGUIHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().name().contains("GLASS_PANE")) {
            return;
        }

        if (event.getRawSlot() == 8) {
            player.closeInventory();
            CompatibilityUtil.playSound(player, "UI_BUTTON_CLICK", "CLICK");
            net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent("§d§l[Mail System] §7Created by §bAJA_RETRO§7. ");
            net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent("§b§n[Click to open Modrinth Profile]");
            link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, "https://modrinth.com/user/AJA_R3TR0"));
            link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.ComponentBuilder("§dGo to AJA_RETRO's Modrinth Profile").create()));
            message.addExtra(link);
            player.spigot().sendMessage(message);
            return;
        }

        if (event.getRawSlot() == 4) {
            SubscriptionState state = plugin.getDatabaseManager().getSubscriptionState(player.getUniqueId());
            player.closeInventory();
            CompatibilityUtil.playSound(player, "UI_BUTTON_CLICK", "CLICK");

            if (state.getType() == SubscriptionState.Type.NONE) {
                plugin.getPendingEmailInputs().put(player.getUniqueId(), true);
                String msg = plugin.getPluginConfig().getMessage("enter-email-prompt", "&e[RetroMail] Please type your email address in chat. Type &c'cancel' &eto abort.");
                player.sendMessage(msg);
            } else if (state.getType() == SubscriptionState.Type.PENDING) {
                if (event.getClick() == ClickType.RIGHT) {
                    plugin.getDatabaseManager().unsubscribe(player.getUniqueId());
                    plugin.broadcastSyncMessage("unsubscribe", player.getUniqueId().toString(), "");
                    String msg = plugin.getPluginConfig().getMessage("reset-success", "&c[RetroMail] Verification cancelled and reset.");
                    player.sendMessage(msg);
                    CompatibilityUtil.playSound(player, "BLOCK_ANVIL_LAND", "ANVIL_LAND");
                } else {
                    plugin.getPendingCodeInputs().put(player.getUniqueId(), true);
                    String msg = plugin.getPluginConfig().getMessage("enter-code-prompt", "&e[RetroMail] Please type the verification code sent to your email. Type &c'cancel' &eto abort.");
                    player.sendMessage(msg);
                }
            } else if (state.getType() == SubscriptionState.Type.VERIFIED) {
                plugin.getDatabaseManager().unsubscribe(player.getUniqueId());
                plugin.broadcastSyncMessage("unsubscribe", player.getUniqueId().toString(), "");
                String msg = plugin.getPluginConfig().getMessage("unsubscribe-success", "&c[RetroMail] You have been unsubscribed from newsletters.");
                player.sendMessage(msg);
                CompatibilityUtil.playSound(player, "BLOCK_ANVIL_LAND", "ANVIL_LAND");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String message = event.getMessage().trim();

        if (plugin.getPendingEmailInputs().containsKey(uuid)) {
            event.setCancelled(true);
            plugin.getPendingEmailInputs().remove(uuid);

            if (message.equalsIgnoreCase("cancel")) {
                String cancelMsg = plugin.getPluginConfig().getMessage("cancelled-action", "&c[RetroMail] Cancelled.");
                player.sendMessage(cancelMsg);
                CompatibilityUtil.playSound(player, "UI_BUTTON_CLICK", "CLICK");
                return;
            }

            if (!EMAIL_PATTERN.matcher(message).matches()) {
                String invalidEmailMsg = plugin.getPluginConfig().getMessage("invalid-email", "&c[RetroMail] Invalid email format. Please try again by typing &e/email&c.");
                player.sendMessage(invalidEmailMsg);
                CompatibilityUtil.playSound(player, "BLOCK_NOTE_BLOCK_BASS", "NOTE_BASS");
                return;
            }

            // Cooldown Rate-Limiting Check
            long now = System.currentTimeMillis();
            if (plugin.getEmailCooldowns().containsKey(uuid)) {
                long nextAllowed = plugin.getEmailCooldowns().get(uuid);
                if (now < nextAllowed) {
                    int remaining = (int) Math.ceil((nextAllowed - now) / 1000.0);
                    String cooldownMsg = plugin.getPluginConfig().getMessage("cooldown-message", "&c[RetroMail] Please wait {time} seconds before requesting another email.")
                            .replace("{time}", String.valueOf(remaining));
                    player.sendMessage(cooldownMsg);
                    CompatibilityUtil.playSound(player, "BLOCK_NOTE_BLOCK_BASS", "NOTE_BASS");
                    return;
                }
            }

            // Set verification code and send email
            String code = String.format("%06d", new Random().nextInt(999999));
            plugin.getDatabaseManager().setPendingSubscription(uuid, message, code);
            plugin.broadcastSyncMessage("pending", uuid.toString(), message + ":" + code);

            plugin.getSMTPManager().sendVerificationEmailAsync(message, uuid, code);

            // Apply Cooldown
            plugin.getEmailCooldowns().put(uuid, now + (plugin.getPluginConfig().emailCooldown * 1000L));

            String verificationSentMsg = plugin.getPluginConfig().getMessage("verification-sent", "&a[RetroMail] Verification code sent to &b{email}&a. Please check your inbox and type &e/email &ato enter the code.")
                    .replace("{email}", message);
            player.sendMessage(verificationSentMsg);
            CompatibilityUtil.playSound(player, "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");

        } else if (plugin.getPendingCodeInputs().containsKey(uuid)) {
            event.setCancelled(true);
            plugin.getPendingCodeInputs().remove(uuid);

            if (message.equalsIgnoreCase("cancel")) {
                String cancelMsg = plugin.getPluginConfig().getMessage("cancelled-action", "&c[RetroMail] Cancelled.");
                player.sendMessage(cancelMsg);
                CompatibilityUtil.playSound(player, "UI_BUTTON_CLICK", "CLICK");
                return;
            }

            SubscriptionState state = plugin.getDatabaseManager().getSubscriptionState(uuid);
            boolean success = plugin.getDatabaseManager().verifySubscription(uuid, message);
            if (success) {
                String successMsg = plugin.getPluginConfig().getMessage("verification-success", "&a&l[RetroMail] Verification successful! You are now subscribed.");
                player.sendMessage(successMsg);
                CompatibilityUtil.playSound(player, "UI_TOAST_CHALLENGE_COMPLETE", "LEVEL_UP");

                plugin.broadcastSyncMessage("verify", uuid.toString(), state.getEmail());

                // Send reward notification messages instantly to the player on this server
                if (plugin.getPluginConfig().rewardsEnabled) {
                    for (String msg : plugin.getPluginConfig().rewardMessages) {
                        player.sendMessage(msg);
                    }
                    
                    // Queue reward commands on the proxy to execute across the entire server network
                    if (!plugin.getPluginConfig().rewardCommands.isEmpty()) {
                        plugin.queueRewardsOnBungee(uuid, plugin.getPluginConfig().rewardCommands);
                    }
                }
            } else {
                String incorrectMsg = plugin.getPluginConfig().getMessage("incorrect-code", "&c[RetroMail] Incorrect code. Please reopen the menu with &e/email &cto try again.");
                player.sendMessage(incorrectMsg);
                CompatibilityUtil.playSound(player, "BLOCK_NOTE_BLOCK_BASS", "NOTE_BASS");
            }
        }
    }
}
