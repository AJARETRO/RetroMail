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
            boolean success = plugin.getDatabaseManager().verifySubscription(player.getUniqueId(), code);
            if (success) {
                String successMsg = plugin.getPluginConfig().getMessage("verification-success", "&a&l[RetroMail] Verification successful! You are now subscribed.");
                player.sendMessage(successMsg);
            } else {
                String incorrectMsg = plugin.getPluginConfig().getMessage("incorrect-code", "&c[RetroMail] Incorrect code or no pending subscription found.");
                player.sendMessage(incorrectMsg);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("mass") || args[0].equalsIgnoreCase("massmail") || args[0].equalsIgnoreCase("sendall")) {
            if (!sender.hasPermission("smtp.admin.massmail")) {
                sender.sendMessage("§cYou do not have permission to send mass emails.");
                return true;
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

            List<dev.retro.papersmtp.database.DatabaseManager.Subscriber> subscribers = plugin.getDatabaseManager().getVerifiedSubscribers();
            if (subscribers.isEmpty()) {
                sender.sendMessage("§c[RetroMail] No verified subscribers found to receive emails.");
                return true;
            }

            plugin.getSMTPManager().sendMassEmailAsync(subscribers, subject, body, false);
            sender.sendMessage("§a[RetroMail] Sending mass email to " + subscribers.size() + " subscribers in the background...");
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
            File file = new File(plugin.getDataFolder(), template);

            if (!file.exists() || !file.isFile()) {
                sender.sendMessage("§c[RetroMail] Template file not found: §e" + template);
                return true;
            }

            String content;
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                sender.sendMessage("§c[RetroMail] Failed to read template file: " + e.getMessage());
                return true;
            }

            boolean isHtml = template.toLowerCase().endsWith(".html") || template.toLowerCase().endsWith(".htm");
            plugin.getSMTPManager().sendEmailAsync(targetEmail, "RetroMail Design Test", content, isHtml);
            sender.sendMessage("§a[RetroMail] Dispatched test email using §e" + template + " §ato §b" + targetEmail + " §ain the background.");
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

            if (!role.equals("ADMIN") && !role.equals("STAFF")) {
                sender.sendMessage("§cInvalid role. Use 'ADMIN' or 'STAFF'.");
                return true;
            }

            // Check if staff already exists
            if (plugin.getDatabaseManager().getStaffAccount(username) != null) {
                sender.sendMessage("§c[RetroMail] Staff username already exists.");
                return true;
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
