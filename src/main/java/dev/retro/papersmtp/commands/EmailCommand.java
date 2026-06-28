package dev.retro.papersmtp.commands;

import dev.retro.papersmtp.PaperSMTP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmailCommand implements CommandExecutor, TabCompleter {
    private final PaperSMTP plugin;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> pendingConfirms = new java.util.concurrent.ConcurrentHashMap<>();

    public EmailCommand(PaperSMTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can open the email subscription menu.");
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("smtp.user.use")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            plugin.getEmailGUI().open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("verify")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can verify their subscription.");
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("smtp.user.use")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /email verify <code>");
                return true;
            }

            String code = args[1];
            dev.retro.papersmtp.compatibility.SchedulerUtil.runAsync(plugin, () -> {
                boolean success = plugin.getDatabaseManager().verifySubscription(player.getUniqueId(), code);
                dev.retro.papersmtp.compatibility.SchedulerUtil.runForPlayer(plugin, player, () -> {
                    if (success) {
                        String successMsg = plugin.getPluginConfig().getMessage("verification-success", "&a&l[RetroMail] Verification successful! You are now subscribed.");
                        player.sendMessage(successMsg);
                    } else {
                        String incorrectMsg = plugin.getPluginConfig().getMessage("incorrect-code", "&c[RetroMail] Incorrect code or no pending subscription found.");
                        player.sendMessage(incorrectMsg);
                    }
                });
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("unsubscribe") || args[0].equalsIgnoreCase("unlink")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can unsubscribe.");
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("smtp.user.use")) {
                player.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            dev.retro.papersmtp.compatibility.SchedulerUtil.runAsync(plugin, () -> {
                plugin.getDatabaseManager().unsubscribe(player.getUniqueId());
                plugin.broadcastSyncMessage("unsubscribe", player.getUniqueId().toString(), "");
                dev.retro.papersmtp.compatibility.SchedulerUtil.runForPlayer(plugin, player, () -> {
                    String msg = plugin.getPluginConfig().getMessage("unsubscribe-success", "&c[RetroMail] You have been unsubscribed from newsletters.");
                    player.sendMessage(msg);
                });
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("mass") || args[0].equalsIgnoreCase("massmail") || args[0].equalsIgnoreCase("sendall")) {
            if (!sender.hasPermission("smtp.admin.massmail")) {
                sender.sendMessage("§cYou do not have permission to send mass emails.");
                return true;
            }

            // License warning check
            boolean hasLicenseSet = plugin.getPluginConfig().licenseKey != null && !plugin.getPluginConfig().licenseKey.trim().isEmpty();
            boolean isLicenseValid = dev.retro.papersmtp.compatibility.GatewayValidator.isLicenseActive();
            
            if (hasLicenseSet && !isLicenseValid) {
                String senderKey = sender instanceof org.bukkit.entity.Player ? ((org.bukkit.entity.Player) sender).getUniqueId().toString() : "CONSOLE";
                long now = System.currentTimeMillis();
                Long lastWarning = pendingConfirms.get(senderKey);
                if (lastWarning == null || (now - lastWarning) > 30000) {
                    pendingConfirms.put(senderKey, now);
                    sender.sendMessage("§c[RetroMail] WARNING: The configured license key is invalid or inactive.");
                    sender.sendMessage("§eThe developer watermark will be appended to this mass mail.");
                    sender.sendMessage("§eTo confirm and send anyway, please run the command again within 30 seconds.");
                    return true;
                } else {
                    pendingConfirms.remove(senderKey);
                }
            }

            if (args.length < 3) {
                sender.sendMessage("§cUsage: /email mass <subject> <message...>");
                return true;
            }

            String subject = args[1];
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                bodyBuilder.append(args[i]).append(" ");
            }
            String body = bodyBuilder.toString().trim();

            dev.retro.papersmtp.compatibility.SchedulerUtil.runAsync(plugin, () -> {
                List<dev.retro.papersmtp.database.DatabaseManager.Subscriber> subscribers = plugin.getDatabaseManager().getVerifiedSubscribers();
                if (subscribers.isEmpty()) {
                    sender.sendMessage("§c[RetroMail] No verified subscribers found to receive emails.");
                    return;
                }
                plugin.getSMTPManager().sendMassEmailAsync(subscribers, subject, body, false);
                sender.sendMessage("§a[RetroMail] Sending mass email to " + subscribers.size() + " subscribers in the background...");
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            if (!sender.hasPermission("smtp.admin.massmail")) {
                sender.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage("§cUsage: /email test <template_file> <target_email>");
                return true;
            }

            String template = args[1];
            String targetEmail = args[2];

            if (template.contains("..") || template.contains("/") || template.contains("\\")) {
                sender.sendMessage("§c[RetroMail] Invalid template path. Directory traversal or subdirectories are not allowed.");
                return true;
            }

            if (!targetEmail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
                sender.sendMessage("§c[RetroMail] Invalid recipient email address format.");
                return true;
            }

            dev.retro.papersmtp.compatibility.SchedulerUtil.runAsync(plugin, () -> {
                File file = new File(plugin.getDataFolder(), template);
                if (!file.exists() || !file.isFile()) {
                    sender.sendMessage("§c[RetroMail] Template file not found: §e" + template);
                    return;
                }

                String content;
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    sender.sendMessage("§c[RetroMail] Failed to read template file: " + e.getMessage());
                    return;
                }

                boolean isHtml = template.toLowerCase().endsWith(".html") || template.toLowerCase().endsWith(".htm");
                plugin.getSMTPManager().sendEmailAsync(targetEmail, "RetroMail Design Test", content, isHtml);
                sender.sendMessage("§a[RetroMail] Dispatched test email using §e" + template + " §ato §b" + targetEmail + " §ain the background.");
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("create-staff") || args[0].equalsIgnoreCase("createstaff")) {
            if (sender instanceof Player) {
                sender.sendMessage("§cThis command can only be executed from the server console.");
                return true;
            }

            if (args.length < 4) {
                sender.sendMessage("§cUsage: /email create-staff <username> <email> <role>");
                return true;
            }

            String username = args[1].trim();
            String email = args[2].trim();
            String role = args[3].trim().toUpperCase();

            if (!username.matches("^[a-zA-Z0-9_-]{3,16}$")) {
                sender.sendMessage("§c[RetroMail] Invalid username. It must be 3-16 characters and only contain letters, numbers, underscores, and hyphens.");
                return true;
            }

            if (email.length() > 254 || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")) {
                sender.sendMessage("§c[RetroMail] Invalid email address format.");
                return true;
            }

            if (!role.equals("ADMIN") && !role.equals("STAFF")) {
                sender.sendMessage("§cInvalid role. Use 'ADMIN' or 'STAFF'.");
                return true;
            }

            dev.retro.papersmtp.compatibility.SchedulerUtil.runAsync(plugin, () -> {
                // Check if staff already exists
                if (plugin.getDatabaseManager().getStaffAccount(username) != null) {
                    sender.sendMessage("§c[RetroMail] Staff username already exists.");
                    return;
                }

                // Generate a secure temporary password
                String tempPassword = dev.retro.papersmtp.compatibility.EncryptionUtil.generateRandomPassword(10);

                // Hash and salt it
                String salt = dev.retro.papersmtp.compatibility.EncryptionUtil.generateSalt();
                String passwordHash = dev.retro.papersmtp.compatibility.EncryptionUtil.hashPassword(tempPassword, salt);

                boolean success = plugin.getDatabaseManager().createStaffAccount(username, email, passwordHash, salt, role);
                if (success) {
                    // Email the credentials using HTML template
                    String subject = "Your RetroMail Staff Account Details";
                    String link = "https://retro.ajaretro.dev:8081/";
                    String body = "";
                    boolean isHtml = false;

                    File templateFile = new File(plugin.getDataFolder(), "staff_created.html");
                    if (templateFile.exists()) {
                        try {
                            byte[] bytes = java.nio.file.Files.readAllBytes(templateFile.toPath());
                            body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                            isHtml = true;
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to read staff_created.html template file: " + e.getMessage());
                        }
                    }

                    if (!isHtml) {
                        try (java.io.InputStream in = plugin.getResource("staff_created.html")) {
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
                            plugin.getLogger().warning("Failed to read staff_created.html template resource: " + e.getMessage());
                        }
                    }

                    if (isHtml) {
                        body = body.replace("{username}", username)
                                   .replace("{password}", tempPassword)
                                   .replace("{link}", link);
                    } else {
                        body = "Hello " + username + ",\n\n" +
                                "An account has been created for you on the RetroMail Staff Dashboard.\n\n" +
                                "Username: " + username + "\n" +
                                "Temporary Password: " + tempPassword + "\n" +
                                "Dashboard Link: " + link + "\n\n" +
                                "Please login and change your password immediately.\n\n" +
                                "Regards,\n" +
                                "Server Administrator";
                    }

                    plugin.getSMTPManager().sendEmailAsync(email, subject, body, isHtml);
                    sender.sendMessage("§a[RetroMail] Successfully created staff account for " + username + ". Credentials have been emailed.");
                } else {
                    sender.sendMessage("§c[RetroMail] Failed to create staff account.");
                }
            });
            return true;
        }

        sender.sendMessage("§cUnknown subcommand. Use §e/email §cor §e/email verify <code> §cor §e/email test <file> <email>§c.");
        sender.sendMessage("§d§lMail System by AJA_RETRO §7- §bhttps://modrinth.com/user/AJA_R3TR0");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("menu");
            subs.add("verify");
            subs.add("unsubscribe");
            subs.add("unlink");
            if (sender.hasPermission("smtp.admin.massmail")) {
                subs.add("mass");
                subs.add("test");
            }
            if (!(sender instanceof Player)) {
                subs.add("create-staff");
            }
            String current = args[0].toLowerCase();
            for (String sub : subs) {
                if (sub.startsWith(current)) {
                    list.add(sub);
                }
            }
            return list;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("verify")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                dev.retro.papersmtp.database.SubscriptionState state = plugin.getDatabaseManager().getSubscriptionState(player.getUniqueId());
                if (state != null && state.getType() == dev.retro.papersmtp.database.SubscriptionState.Type.PENDING && state.getCode() != null) {
                    list.add(state.getCode());
                    return list;
                }
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            if (sender.hasPermission("smtp.admin.massmail")) {
                List<String> templates = new ArrayList<>();
                File folder = plugin.getDataFolder();
                if (folder.exists() && folder.isDirectory()) {
                    File[] files = folder.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && (f.getName().endsWith(".html") || f.getName().endsWith(".txt") || f.getName().endsWith(".htm"))) {
                                templates.add(f.getName());
                            }
                        }
                    }
                }
                String current = args[1].toLowerCase();
                for (String t : templates) {
                    if (t.toLowerCase().startsWith(current)) {
                        list.add(t);
                    }
                }
                return list;
            }
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("create-staff") || args[0].equalsIgnoreCase("createstaff"))) {
            if (!(sender instanceof Player)) {
                List<String> roles = new ArrayList<>();
                roles.add("ADMIN");
                roles.add("STAFF");
                String current = args[3].toLowerCase();
                for (String r : roles) {
                    if (r.toLowerCase().startsWith(current)) {
                        list.add(r);
                    }
                }
                return list;
            }
        }

        return Collections.emptyList();
    }
}
