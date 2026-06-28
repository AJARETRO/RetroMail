package dev.retro.papersmtp.commands;

import dev.retro.papersmtp.PaperSMTP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class MassEmailCommand implements CommandExecutor, TabCompleter {
    private final PaperSMTP plugin;

    public MassEmailCommand(PaperSMTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smtp.admin.massmail")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (!dev.retro.papersmtp.compatibility.GatewayValidator.isLicenseActive()) {
            sender.sendMessage("§c[RetroMail] Mass mailing requires an active RetroMail Network License.");
            sender.sendMessage("§cPlease purchase a license key at §elicense.ajaretro.dev §cand configure it in config.yml.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /mass-email <filename> [subject...]");
            sender.sendMessage("§d§lMail System by AJA_RETRO §7- §bhttps://modrinth.com/user/AJA_R3TR0");
            return true;
        }

        String filename = args[0];
        File file = new File(plugin.getDataFolder(), filename);

        if (!file.exists() || !file.isFile()) {
            sender.sendMessage("§c[RetroMail] File not found in plugins/RetroMail: §e" + filename);
            return true;
        }

        String subject = "Network Announcement";
        if (args.length >= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            subject = sb.toString().trim();
        }

        String content;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            content = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            sender.sendMessage("§c[RetroMail] Failed to read template file: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Failed to read file for mass mail: " + file.getAbsolutePath(), e);
            return true;
        }

        List<dev.retro.papersmtp.database.DatabaseManager.Subscriber> subscribers = plugin.getDatabaseManager().getVerifiedSubscribers();
        if (subscribers.isEmpty()) {
            sender.sendMessage("§c[RetroMail] No verified subscribers found to receive emails.");
            return true;
        }

        boolean isHtml = filename.toLowerCase().endsWith(".html") || filename.toLowerCase().endsWith(".htm");

        plugin.getSMTPManager().sendMassEmailAsync(subscribers, subject, content, isHtml);
        sender.sendMessage("§a[RetroMail] Sending mass email using §e" + filename + " §ato " + subscribers.size() + " subscribers in the background...");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("smtp.admin.massmail")) {
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
            String current = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (String t : templates) {
                if (t.toLowerCase().startsWith(current)) {
                    list.add(t);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
