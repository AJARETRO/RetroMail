package dev.retro.papersmtp.smtp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.retro.papersmtp.MailPluginInterface;
import dev.retro.papersmtp.config.PluginConfig;
import dev.retro.papersmtp.database.DatabaseManager;
import dev.retro.papersmtp.database.DatabaseManager.StaffAccount;
import dev.retro.papersmtp.database.DatabaseManager.SenderFilter;
import dev.retro.papersmtp.database.DatabaseManager.StoredMail;
import dev.retro.papersmtp.compatibility.EncryptionUtil;
import dev.retro.papersmtp.compatibility.TOTPUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MailHandlerServer {
    private final MailPluginInterface plugin;
    private HttpServer server;
    private final ConcurrentHashMap<String, StaffAccount> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> tempSecrets2FA = new ConcurrentHashMap<>();

    public MailHandlerServer(MailPluginInterface plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PluginConfig config = plugin.getPluginConfig();
        if (!config.mailHandlerEnabled) {
            return;
        }

        try {
            // Extract static frontend folder
            File webDir = new File(plugin.getDataFolder(), "web");
            if (!webDir.exists()) {
                webDir.mkdirs();
            }
            File avatarsDir = new File(webDir, "avatars");
            if (!avatarsDir.exists()) {
                avatarsDir.mkdirs();
            }

            // Create index.html if not exists
            plugin.saveResource("web/index.html", false);
            plugin.saveResource("web/inbox.html", false);
            plugin.saveResource("web/settings.html", false);
            plugin.saveResource("web/admin.html", false);

            server = HttpServer.create(new InetSocketAddress(config.mailHandlerPort), 0);
            
            // API Routes
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/logout", new LogoutHandler());
            server.createContext("/api/change-password", new ChangePasswordHandler());
            server.createContext("/api/setup-2fa", new Setup2FAHandler());
            server.createContext("/api/enable-2fa", new Enable2FAHandler());
            server.createContext("/api/dashboard", new DashboardHandler());
            server.createContext("/api/upload-avatar", new UploadAvatarHandler());
            server.createContext("/api/send", new SendMailHandler());
            server.createContext("/api/tokens", new ApiTokensHandler());
            server.createContext("/api/info", new InfoHandler());
            
            // Admin Routes
            server.createContext("/api/admin/permissions", new AdminPermissionsHandler());
            server.createContext("/api/admin/delete-staff", new AdminDeleteStaffHandler());
            server.createContext("/api/admin/conversation", new AdminConversationHandler());

            // External API Routes
            server.createContext("/api/external/mails", new ExternalMailsHandler());
            server.createContext("/api/external/send", new ExternalSendMailHandler());

            // Static Files
            server.createContext("/", new StaticFileHandler(webDir));

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("Mail Handler Dashboard Server running on port " + config.mailHandlerPort);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start Mail Handler Server: " + e.getMessage(), e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            plugin.getLogger().info("Mail Handler Dashboard Server stopped.");
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void sendJSONResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = new Gson().toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private StaffAccount authenticateRequest(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            StaffAccount session = activeSessions.get(token);
            if (session != null) {
                // Refresh local session account record
                StaffAccount fresh = plugin.getDatabaseManager().getStaffAccount(session.username);
                if (fresh != null) {
                    activeSessions.put(token, fresh);
                    return fresh;
                }
                return session;
            } else {
                // Try to resolve from database session storage
                String username = plugin.getDatabaseManager().getSessionUsername(token);
                if (username != null) {
                    StaffAccount fresh = plugin.getDatabaseManager().getStaffAccount(username);
                    if (fresh != null) {
                        activeSessions.put(token, fresh);
                        return fresh;
                    }
                }
            }
        }
        return null;
    }

    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.sendResponseHeaders(204, -1);
    }

    // --- ENDPOINTS IMPLEMENTATIONS ---

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String username = json.has("username") ? json.get("username").getAsString().trim() : "";
                String password = json.has("password") ? json.get("password").getAsString() : "";
                int code = json.has("totp_code") && !json.get("totp_code").getAsString().isEmpty() ? json.get("totp_code").getAsInt() : -1;

                StaffAccount account = plugin.getDatabaseManager().getStaffAccount(username);
                if (account == null) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Invalid credentials.");
                    sendJSONResponse(exchange, 401, err);
                    return;
                }

                String computedHash = EncryptionUtil.hashPassword(password, account.salt);
                if (!computedHash.equals(account.passwordHash)) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Invalid credentials.");
                    sendJSONResponse(exchange, 401, err);
                    return;
                }

                if (account.totpEnabled) {
                    if (code == -1 || !TOTPUtil.verifyCode(account.totpSecret, code, 1)) {
                        Map<String, String> err = new HashMap<>();
                        err.put("status", "error");
                        err.put("message", "Invalid or missing 2FA code.");
                        sendJSONResponse(exchange, 401, err);
                        return;
                    }
                }

                // Credentials valid, build session token
                String token = UUID.randomUUID().toString().replace("-", "");
                activeSessions.put(token, account);
                plugin.getDatabaseManager().saveSession(token, account.username);

                Map<String, Object> resp = new HashMap<>();
                resp.put("status", "success");
                resp.put("token", token);
                resp.put("username", account.username);
                resp.put("role", account.role);
                resp.put("tempPassword", account.tempPassword);
                resp.put("totpEnabled", account.totpEnabled);

                sendJSONResponse(exchange, 200, resp);
            } catch (Exception e) {
                Map<String, String> err = new HashMap<>();
                err.put("status", "error");
                err.put("message", "Server error: " + e.getMessage());
                sendJSONResponse(exchange, 500, err);
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                activeSessions.remove(token);
                plugin.getDatabaseManager().deleteSession(token);
            }
            Map<String, String> resp = new HashMap<>();
            resp.put("status", "success");
            sendJSONResponse(exchange, 200, resp);
        }
    }

    private class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String password = json.has("password") ? json.get("password").getAsString() : "";

                if (password.trim().length() < 6) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Password must be at least 6 characters.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                String salt = EncryptionUtil.generateSalt();
                String hash = EncryptionUtil.hashPassword(password, salt);

                boolean success = plugin.getDatabaseManager().updateStaffPassword(user.id, hash, salt);
                if (success) {
                    Map<String, String> resp = new HashMap<>();
                    resp.put("status", "success");
                    resp.put("message", "Password changed successfully.");
                    sendJSONResponse(exchange, 200, resp);
                } else {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Failed to update database.");
                    sendJSONResponse(exchange, 500, err);
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class Setup2FAHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            String secret = TOTPUtil.generateSecretKey();
            tempSecrets2FA.put(user.username, secret);

            String qrUrl = "https://chart.googleapis.com/chart?cht=qr&chs=200x200&chl=" +
                    "otpauth://totp/PaperSMTP:" + user.username + "?secret=" + secret + "&issuer=PaperSMTP";

            Map<String, String> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("secret", secret);
            resp.put("qrCodeUrl", qrUrl);

            sendJSONResponse(exchange, 200, resp);
        }
    }

    private class Enable2FAHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                String secret = tempSecrets2FA.get(user.username);

                if (secret == null || code == -1) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Please setup 2FA first before enabling.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                if (TOTPUtil.verifyCode(secret, code, 1)) {
                    plugin.getDatabaseManager().updateStaff2FA(user.id, secret, true);
                    tempSecrets2FA.remove(user.username);
                    Map<String, String> resp = new HashMap<>();
                    resp.put("status", "success");
                    sendJSONResponse(exchange, 200, resp);
                } else {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Invalid 2FA code.");
                    sendJSONResponse(exchange, 400, err);
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                PluginConfig config = plugin.getPluginConfig();
                Map<String, Object> data = new HashMap<>();
                
                // 1. Staff Info
                Map<String, Object> staffInfo = new HashMap<>();
                staffInfo.put("username", user.username);
                staffInfo.put("email", user.email);
                staffInfo.put("role", user.role);
                staffInfo.put("avatarPath", user.avatarPath);
                data.put("profile", staffInfo);

                // Add branding configuration
                Map<String, String> branding = new HashMap<>();
                branding.put("serverName", config.serverName);
                branding.put("discordLink", config.discordLink);
                branding.put("documentationLink", config.documentationLink);
                branding.put("forumLink", config.forumLink);
                data.put("branding", branding);

                // 2. Filtered Mail List
                List<StoredMail> mails = plugin.getDatabaseManager().getMailsForStaff(user, config.mailHandlerDomain);
                data.put("mails", mails);

                // 3. Admin-only Management Panels
                if (user.role.equalsIgnoreCase("ADMIN")) {
                    List<StaffAccount> staffList = plugin.getDatabaseManager().listStaff();
                    List<Map<String, Object>> staffData = new ArrayList<>();
                    for (StaffAccount s : staffList) {
                        Map<String, Object> sm = new HashMap<>();
                        sm.put("id", s.id);
                        sm.put("username", s.username);
                        sm.put("email", s.email);
                        sm.put("role", s.role);
                        sm.put("totpEnabled", s.totpEnabled);
                        sm.put("permissions", plugin.getDatabaseManager().getStaffPermissions(s.id));
                        sm.put("filters", plugin.getDatabaseManager().getStaffFilters(s.id));
                        staffData.add(sm);
                    }
                    data.put("staffManagement", staffData);
                }

                sendJSONResponse(exchange, 200, data);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class UploadAvatarHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String base64Image = json.has("image") ? json.get("image").getAsString() : "";

                if (base64Image.isEmpty() || !base64Image.contains(",")) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Invalid image payload.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                String[] parts = base64Image.split(",");
                String meta = parts[0];
                String base64Data = parts[1];

                // Size validation: 30MB base64 is ~40MB characters
                if (base64Data.length() > 41943040) { // roughly 30MB bytes decoded
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "File size exceeds 30MB.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                byte[] data;
                try {
                    data = Base64.getDecoder().decode(base64Data);
                } catch (IllegalArgumentException e) {
                    data = Base64.getMimeDecoder().decode(base64Data);
                }

                // Safe filename sanitization
                String safeName = user.username.replaceAll("[^a-zA-Z0-9_-]", "") + ".png";
                File file = new File(new File(plugin.getDataFolder(), "web/avatars"), safeName);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                }

                String webPath = "/avatars/" + safeName;
                plugin.getDatabaseManager().updateStaffAvatar(user.id, webPath);

                Map<String, String> resp = new HashMap<>();
                resp.put("status", "success");
                resp.put("avatarPath", webPath);
                sendJSONResponse(exchange, 200, resp);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to upload avatar: " + e.getMessage(), e);
                Map<String, String> err = new HashMap<>();
                err.put("status", "error");
                err.put("message", "Server error: " + e.getMessage());
                try {
                    sendJSONResponse(exchange, 500, err);
                } catch (Exception ignored) {}
            }
        }
    }

    private class SendMailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String to = json.has("to") ? json.get("to").getAsString().trim() : "";
                String subject = json.has("subject") ? json.get("subject").getAsString().trim() : "";
                String content = json.has("body") ? json.get("body").getAsString() : "";
                boolean isHtml = json.has("isHtml") && json.get("isHtml").getAsBoolean();

                if (to.isEmpty()) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Recipient email is required.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                String companyEmail = user.username + "@" + plugin.getPluginConfig().mailHandlerDomain;
                String formattedBody = plugin.getSMTPManager().getFormattedEmailBody(subject, content, isHtml);
                plugin.getSMTPManager().sendEmailAsync(companyEmail, user.username, to, subject, formattedBody, true);
                plugin.getDatabaseManager().saveIncomingMail(companyEmail + " (" + user.email + ")", to, subject, formattedBody, true);
                
                Map<String, String> resp = new HashMap<>();
                resp.put("status", "success");
                sendJSONResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class AdminPermissionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount admin = authenticateRequest(exchange);
            if (admin == null || !admin.role.equalsIgnoreCase("ADMIN")) {
                sendResponse(exchange, 403, "Forbidden");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String targetUsername = json.has("username") ? json.get("username").getAsString().trim() : "";
                
                StaffAccount target = plugin.getDatabaseManager().getStaffAccount(targetUsername);
                if (target == null) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Target staff member not found.");
                    sendJSONResponse(exchange, 404, err);
                    return;
                }

                // Idk how it works now, forgot to commen5 before, but it works - wait, this is for blocking self-modification
                if (target.id == admin.id) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "You cannot modify your own permissions or role.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                // 1. Role update (if provided)
                if (json.has("role")) {
                    String newRole = json.get("role").getAsString().toUpperCase();
                    if (newRole.equals("ADMIN") || newRole.equals("STAFF")) {
                        plugin.getDatabaseManager().updateStaffRole(target.id, newRole);
                        // Refresh target account record
                        target = plugin.getDatabaseManager().getStaffAccount(targetUsername);
                    }
                }

                // 2. Mailbox Permissions
                if (json.has("permissions")) {
                    List<String> perms = new ArrayList<>();
                    JsonArray arr = json.getAsJsonArray("permissions");
                    for (JsonElement el : arr) {
                        perms.add(el.getAsString().trim().toLowerCase());
                    }
                    plugin.getDatabaseManager().updateStaffPermissions(target.id, perms);
                }

                // 3. Sender Filters
                if (json.has("filters")) {
                    List<SenderFilter> filters = new ArrayList<>();
                    JsonArray arr = json.getAsJsonArray("filters");
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        String mailbox = obj.get("mailbox").getAsString().trim().toLowerCase();
                        String allowed = obj.get("allowedSender").getAsString().trim().toLowerCase();
                        filters.add(new SenderFilter(mailbox, allowed));
                    }
                    plugin.getDatabaseManager().updateStaffFilters(target.id, filters);
                }

                Map<String, String> resp = new HashMap<>();
                resp.put("status", "success");
                sendJSONResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class AdminDeleteStaffHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount admin = authenticateRequest(exchange);
            if (admin == null || !admin.role.equalsIgnoreCase("ADMIN")) {
                sendResponse(exchange, 403, "Forbidden");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String targetUsername = json.has("username") ? json.get("username").getAsString().trim() : "";

                StaffAccount target = plugin.getDatabaseManager().getStaffAccount(targetUsername);
                if (target == null) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Target staff member not found.");
                    sendJSONResponse(exchange, 404, err);
                    return;
                }

                if (target.id == admin.id) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "You cannot modify your own permissions or role.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                if (target.username.equalsIgnoreCase(admin.username)) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "You cannot delete yourself.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                plugin.getDatabaseManager().deleteStaff(target.id);
                Map<String, String> resp = new HashMap<>();
                resp.put("status", "success");
                sendJSONResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    // --- STATIC FILES HANDLER ---

    private class StaticFileHandler implements HttpHandler {
        private final File baseDir;

        public StaticFileHandler(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File(baseDir, path.substring(1));
            // Prevent path traversal
            if (!file.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                sendResponse(exchange, 403, "Forbidden");
                return;
            }

            if (!file.exists() || !file.isFile()) {
                sendResponse(exchange, 404, "Not Found");
                return;
            }

            String mimeType = getMimeType(path);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, file.length());

            try (java.io.OutputStream os = exchange.getResponseBody();
                 java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private String getMimeType(String path) {
            String lowercase = path.toLowerCase();
            if (lowercase.endsWith(".html") || lowercase.endsWith(".htm")) return "text/html; charset=utf-8";
            if (lowercase.endsWith(".css")) return "text/css; charset=utf-8";
            if (lowercase.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lowercase.endsWith(".json")) return "application/json; charset=utf-8";
            if (lowercase.endsWith(".png")) return "image/png";
            if (lowercase.endsWith(".jpg") || lowercase.endsWith(".jpeg")) return "image/jpeg";
            if (lowercase.endsWith(".gif")) return "image/gif";
            if (lowercase.endsWith(".svg")) return "image/svg+xml";
            if (lowercase.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    private class AdminConversationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount admin = authenticateRequest(exchange);
            if (admin == null || !admin.role.equalsIgnoreCase("ADMIN")) {
                sendResponse(exchange, 403, "Forbidden");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                String email1 = "";
                String email2 = "";
                if (query != null) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length == 2) {
                            String key = java.net.URLDecoder.decode(parts[0], "UTF-8");
                            String val = java.net.URLDecoder.decode(parts[1], "UTF-8");
                            if (key.equalsIgnoreCase("email1")) {
                                email1 = val;
                            } else if (key.equalsIgnoreCase("email2")) {
                                email2 = val;
                            }
                        }
                    }
                }

                if (email1.isEmpty() || email2.isEmpty()) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Parameters email1 and email2 are required.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                List<StoredMail> convo = plugin.getDatabaseManager().getConversation(email1, email2);
                sendJSONResponse(exchange, 200, convo);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private StaffAccount authenticateExternalRequest(HttpExchange exchange) {
        // 1. Check Authorization Bearer header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return plugin.getDatabaseManager().getStaffByApiToken(token);
        }
        // 2. Check X-API-Key header
        String apiKeyHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKeyHeader != null && !apiKeyHeader.trim().isEmpty()) {
            return plugin.getDatabaseManager().getStaffByApiToken(apiKeyHeader.trim());
        }
        return null;
    }

    private class ApiTokensHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            StaffAccount user = authenticateRequest(exchange);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    List<dev.retro.papersmtp.database.DatabaseManager.ApiToken> tokens = 
                            plugin.getDatabaseManager().getApiTokens(user.id);
                    sendJSONResponse(exchange, 200, tokens);
                } else if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    String body = readRequestBody(exchange);
                    JsonObject json = new Gson().fromJson(body, JsonObject.class);
                    String name = json.has("name") ? json.get("name").getAsString().trim() : "External API Token";
                    if (name.isEmpty()) name = "External API Token";
                    String permissions = json.has("permissions") ? json.get("permissions").getAsString().trim() : "read_mails,send_mails";
                    if (permissions.isEmpty()) permissions = "read_mails,send_mails";

                    // I did , dont remember what we say it, but its cool - making random uuid string for API keys
                    // Generate a secure 48-character API token (prefix pt_ + random string)
                    String token = "pt_" + java.util.UUID.randomUUID().toString().replace("-", "") + 
                                   java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);

                    boolean success = plugin.getDatabaseManager().saveApiToken(user.id, token, name, permissions);
                    if (success) {
                        Map<String, String> resp = new HashMap<>();
                        resp.put("status", "success");
                        resp.put("token", token);
                        sendJSONResponse(exchange, 200, resp);
                    } else {
                        Map<String, String> err = new HashMap<>();
                        err.put("status", "error");
                        err.put("message", "Failed to generate token.");
                        sendJSONResponse(exchange, 500, err);
                    }
                } else if (exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                    String body = readRequestBody(exchange);
                    JsonObject json = new Gson().fromJson(body, JsonObject.class);
                    int tokenId = json.has("id") ? json.get("id").getAsInt() : -1;

                    boolean success = plugin.getDatabaseManager().deleteApiToken(user.id, tokenId);
                    if (success) {
                        Map<String, String> resp = new HashMap<>();
                        resp.put("status", "success");
                        sendJSONResponse(exchange, 200, resp);
                    } else {
                        Map<String, String> err = new HashMap<>();
                        err.put("status", "error");
                        err.put("message", "Failed to delete token.");
                        sendJSONResponse(exchange, 400, err);
                    }
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class ExternalMailsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            } else {
                String apiKeyHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (apiKeyHeader != null) token = apiKeyHeader.trim();
            }

            if (token == null) {
                sendResponse(exchange, 401, "Unauthorized - Missing Token");
                return;
            }

            String permissions = plugin.getDatabaseManager().getTokenPermissions(token);
            if (permissions == null) {
                sendResponse(exchange, 401, "Unauthorized - Invalid API Token");
                return;
            }

            if (!permissions.contains("read_mails")) {
                sendResponse(exchange, 403, "Forbidden - Token missing read_mails permission");
                return;
            }

            StaffAccount user = plugin.getDatabaseManager().getStaffByApiToken(token);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized - Invalid API Token Owner");
                return;
            }

            try {
                PluginConfig config = plugin.getPluginConfig();
                List<StoredMail> mails = plugin.getDatabaseManager().getMailsForStaff(user, config.mailHandlerDomain);
                sendJSONResponse(exchange, 200, mails);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class ExternalSendMailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            } else {
                String apiKeyHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (apiKeyHeader != null) token = apiKeyHeader.trim();
            }

            if (token == null) {
                sendResponse(exchange, 401, "Unauthorized - Missing Token");
                return;
            }

            String permissions = plugin.getDatabaseManager().getTokenPermissions(token);
            if (permissions == null) {
                sendResponse(exchange, 401, "Unauthorized - Invalid API Token");
                return;
            }

            if (!permissions.contains("send_mails")) {
                sendResponse(exchange, 403, "Forbidden - Token missing send_mails permission");
                return;
            }

            StaffAccount user = plugin.getDatabaseManager().getStaffByApiToken(token);
            if (user == null) {
                sendResponse(exchange, 401, "Unauthorized - Invalid API Token Owner");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = new Gson().fromJson(body, JsonObject.class);
                String to = json.has("to") ? json.get("to").getAsString().trim() : "";
                String subject = json.has("subject") ? json.get("subject").getAsString().trim() : "";
                String content = json.has("body") ? json.get("body").getAsString() : "";
                boolean isHtml = json.has("isHtml") && json.get("isHtml").getAsBoolean();

                if (to.isEmpty()) {
                    Map<String, String> err = new HashMap<>();
                    err.put("status", "error");
                    err.put("message", "Recipient email ('to') is required.");
                    sendJSONResponse(exchange, 400, err);
                    return;
                }

                String companyEmail = user.username + "@" + plugin.getPluginConfig().mailHandlerDomain;
                String formattedBody = plugin.getSMTPManager().getFormattedEmailBody(subject, content, isHtml);
                plugin.getSMTPManager().sendEmailAsync(companyEmail, user.username, to, subject, formattedBody, true);
                plugin.getDatabaseManager().saveIncomingMail(companyEmail + " (" + user.email + ")", to, subject, formattedBody, true);

                Map<String, String> resp = new HashMap<>();
                resp.put("status", "success");
                resp.put("message", "Email sent successfully");
                sendJSONResponse(exchange, 200, resp);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class InfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                handleOptions(exchange);
                return;
            }
            try {
                PluginConfig config = plugin.getPluginConfig();
                Map<String, String> info = new HashMap<>();
                info.put("serverName", config.serverName);
                info.put("discordLink", config.discordLink);
                info.put("documentationLink", config.documentationLink);
                info.put("forumLink", config.forumLink);
                sendJSONResponse(exchange, 200, info);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }
}
