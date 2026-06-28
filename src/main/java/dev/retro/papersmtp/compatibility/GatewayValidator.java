package dev.retro.papersmtp.compatibility;

import dev.retro.papersmtp.MailPluginInterface;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class GatewayValidator {

    private static GatewayValidator instance;

    // XOR / Base64 Encoded Endpoint URL: "http://license.ajaretro.dev/api/check-license"
    private static final String OBFUSCATED_URL = "aHR0cDovL2xpY2Vuc2UuYWphcmV0cm8uZGV2L2FwaS9jaGVjay1saWNlbnNl";
    
    // Obfuscated Public Key
    private static final String OBFUSCATED_PUBKEY = 
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArYcwJMD1jZ2eThgkFWmD" +
        "la0moqxW49alKqHld2w+ARy0rifM/MqK4K+wUCKivc/pBnDK8XyNdBVml5bH0MiM" +
        "0ASq95DBosAjIwl6SaGVGhmTplj2g/vODilOhKDcJj7isWqgwVMQfXC87XKAY0X1" +
        "ctV/6wazIERo+Xm9zDcPl/j/wfweDmxpfbT5sSz9RI1RNxQaLKfYLsZ9mSdNC7zd" +
        "fdnZzv2m1CtYfy/VfA5Taf+Txq6tbV6Vo4D4YgAUFxv6HAOZOUZ389ZgNqi0kQbr" +
        "/JPn8UuG88BNFLWO2LFvXKwOw5WrjTXe3hW1Z+2VbK9LpA560YTYTHIohi/wyNaY" +
        "vwIDAQAB";

    // Obfuscated watermark HTML
    private static final String OBFUSCATED_WATERMARK = 
        "PGRpdiBzdHlsZT0ibWFyZ2luLXRvcDogMzBweDsgYm9yZGVyLXRvcDogMXB4IHNvbGlkICMyMzI4" +
        "MzU7IHBhZGRpbmctdG9wOiAyMHB4OyB0ZXh0LWFsaWduOiBjZW50ZXI7IGZvbnQtZmFtaWx5OiBz" +
        "YW5zLXNlcmlmOyI+CiAgICA8ZGl2IHN0eWxlPSJkaXNwbGF5OiBpbmxpbmUtYmxvY2s7IGJhY2tn" +
        "cm91bmQtY29sb3I6ICMxYTFlMjY7IGJvcmRlci1yYWRpdXM6IDhweDsgcGFkZGluZzogMTVweDsg" +
        "Ym9yZGVyOiAxcHggc29saWQgIzIzMjgzNTsgdGV4dC1hbGlnbjogY2VudGVyOyI+CiAgICAgICAg" +
        "PGRpdiBzdHlsZT0iZm9udC1zaXplOiAxNHB4OyBjb2xvcjogIzljYTNhZjsgZm9udC13ZWlnaHQ6" +
        "IDUwMDsgbWFyZ2luLWJvdHRvbTogOHB4OyI+CiAgICAgICAgICAgIE1haWwgU3lzdGVtIGJ5IDxz" +
        "cGFuIHN0eWxlPSJjb2xvcjogI2E4NTVmNzsgZm9udC13ZWlnaHQ6IDcwMDsiPkFKQV9SRVRSTzwv" +
        "c3Bhbj4KICAgICAgICA8L2Rpdj4KICAgICAgICA8ZGl2PgogICAgICAgICAgICA8YSBocmVmPSJo" +
        "dHRwczovL21vZHJpbnRoLmNvbS91c2VyL0FKQV9SM1RSMCIgdGFyZ2V0PSJfYmxhbmsiIHN0eWxl" +
        "PSJjb2xvcjogIzYzNjZmMTsgdGV4dC1kZWNvcmF0aW9uOiBub25lOyBmb250LXNpemU6IDEzcHg7" +
        "IGZvbnQtd2VpZ2h0OiA2MDA7IGJhY2tncm91bmQtY29sb3I6ICMyZTM1NGY7IHBhZGRpbmc6IDZw" +
        "eCAxMnB4OyBib3JkZXItcmFkaXVzOiA0cHg7IGRpc3BsYXk6IGlubGluZS1ibG9jazsgbWFyZ2lu" +
        "LXJpZ2h0OiA4cHg7Ij5Nb2RyaW50aCBQcm9maWxlPC9hPgogICAgICAgICAgICA8YSBocmVmPSJo" +
        "dHRwczovL2FqYXJldHJvLmRldiIgdGFyZ2V0PSJfYmxhbmsiIHN0eWxlPSJjb2xvcjogIzYzNjZm" +
        "MTsgdGV4dC1kZWNvcmF0aW9uOiBub25lOyBmb250LXNpemU6IDEzcHg7IGZvbnQtd2VpZ2h0OiA2" +
        "MDA7IGJhY2tncm91bmQtY29sb3I6ICMyZTM1NGY7IHBhZGRpbmc6IDZweCAxMnB4OyBib3JkZXIt" +
        "cmFkaXVzOiA0cHg7IGRpc3BsYXk6IGlubGluZS1ibG9jazsiPmFqYXJldHJvLmRldjwvYT4KICAg" +
        "ICAgICA8L2Rpdj4KICAgIDwvZGl2Pgo8L2Rpdj4=";

    private final MailPluginInterface plugin;
    private final String licenseKey;
    private final int port;
    private final ScheduledExecutorService scheduler;

    // Runtime state (checked dynamically)
    private boolean verifiedActive = false;
    private boolean initialCheckCompleted = false;
    private long lastSuccessfulVerificationTime = 0;
    private String cachedStatus = "invalid";
    private int cachedAllowed = 0;
    private int cachedActive = 0;
    private long cachedTimestamp = 0;
    private String cachedSignature = "";

    public GatewayValidator(MailPluginInterface plugin, String licenseKey, int port) {
        this.plugin = plugin;
        this.licenseKey = licenseKey != null ? licenseKey.trim() : "";
        this.port = port;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RetroMail-GatewayValidator");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    public static void start(MailPluginInterface plugin, String licenseKey, int port) {
        if (instance != null) {
            instance.stop();
        }
        instance = new GatewayValidator(plugin, licenseKey, port);
        instance.init();
    }

    public static void stopValidator() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    private void init() {
        if (licenseKey.isEmpty()) {
            verifiedActive = false;
            return;
        }
        // Poll every 45 seconds
        scheduler.scheduleAtFixedRate(this::checkLicense, 2, 45, TimeUnit.SECONDS);
    }

    private void stop() {
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
    }

    private void checkLicense() {
        HttpURLConnection conn = null;
        try {
            String decodedUrl = new String(Base64.getDecoder().decode(OBFUSCATED_URL), StandardCharsets.UTF_8);
            URL url = new URL(decodedUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "RetroMail-GatewayValidator/1.0.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);

            String jsonPayload = "{\"license_key\":\"" + licenseKey + "\",\"port\":" + port + "}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    parseAndVerifyResponse(sb.toString());
                }
            } else {
                handleFailure(responseCode >= 500 || responseCode == 404 || responseCode == 503);
            }
        } catch (Throwable t) {
            handleFailure(true);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void handleFailure(boolean isOffline) {
        if (isOffline && initialCheckCompleted) {
            long offlineDuration = System.currentTimeMillis() - lastSuccessfulVerificationTime;
            if (offlineDuration <= 86400000L) { // 24 hours
                if (!verifiedActive) {
                    plugin.getLogger().log(Level.WARNING, "[RetroMail] License authentication server unreachable. Operating in Offline Grace Period (Premium active for " + ((86400000L - offlineDuration) / 60000L) + " more minutes).");
                }
                verifiedActive = true;
                return;
            } else {
                plugin.getLogger().log(Level.SEVERE, "[RetroMail] Offline grace period expired. Defaulting to free Community Edition (watermarks enabled).");
            }
        }
        verifiedActive = false;
    }

    private void parseAndVerifyResponse(String jsonStr) {
        try {
            String status = extractJsonString(jsonStr, "status");
            int allowed = extractJsonInt(jsonStr, "allowed_servers");
            int active = extractJsonInt(jsonStr, "active_servers");
            long timestamp = extractJsonLong(jsonStr, "timestamp");
            String signature = extractJsonString(jsonStr, "signature");
            String warning = extractJsonString(jsonStr, "warning");

            if (!warning.isEmpty()) {
                plugin.getLogger().log(Level.WARNING, "[RetroMail] WARNING: " + warning);
            }

            boolean isSignatureValid = verifySignature(licenseKey, status, allowed, active, timestamp, signature);
            if (isSignatureValid && "valid".equals(status)) {
                if (!verifiedActive) {
                    plugin.getLogger().log(Level.INFO, "[RetroMail] Commercial network license key verified successfully. Watermarks disabled.");
                }
                verifiedActive = true;
                initialCheckCompleted = true;
                lastSuccessfulVerificationTime = System.currentTimeMillis();
            } else {
                if ("frozen".equals(status)) {
                    plugin.getLogger().log(Level.WARNING, "[RetroMail] NOTICE: This license key has been frozen by the owner. Operating in Free Community Edition.");
                } else if (verifiedActive || "limit_exceeded".equals(status)) {
                    plugin.getLogger().log(Level.WARNING, "[RetroMail] License verification signature verification failed (Status: " + status + "). Defaulting to free Community Edition (watermarks enabled).");
                }
                verifiedActive = false;
            }

            this.cachedStatus = status;
            this.cachedAllowed = allowed;
            this.cachedActive = active;
            this.cachedTimestamp = timestamp;
            this.cachedSignature = signature;

        } catch (Throwable t) {
            handleFailure(true);
        }
    }

    private boolean verifySignature(String key, String status, int allowed, int active, long timestamp, String signatureB64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(OBFUSCATED_PUBKEY.replace("\n", "").replace("\r", ""));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);

            String message = key + ":" + status + ":" + allowed + ":" + active + ":" + timestamp;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(message.getBytes(StandardCharsets.UTF_8));

            return sig.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean verifyCachedPayload() {
        if (!verifiedActive || cachedSignature.isEmpty()) {
            return false;
        }
        return verifySignature(licenseKey, cachedStatus, cachedAllowed, cachedActive, cachedTimestamp, cachedSignature);
    }

    public static boolean isLicenseActive() {
        return instance != null && instance.verifyCachedPayload();
    }

    public static String getWatermarkOrEmpty() {
        if (isLicenseActive()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(OBFUSCATED_WATERMARK), StandardCharsets.UTF_8);
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) {
            search = "\"" + key + "\":";
            start = json.indexOf(search);
            if (start == -1) return "";
            int valStart = start + search.length();
            int end = json.indexOf(",", valStart);
            if (end == -1) end = json.indexOf("}", valStart);
            return json.substring(valStart, end).replace("\"", "").trim();
        }
        int valStart = start + search.length();
        int end = json.indexOf("\"", valStart);
        return json.substring(valStart, end);
    }

    private int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return 0;
        int valStart = start + search.length();
        int end = json.indexOf(",", valStart);
        if (end == -1) end = json.indexOf("}", valStart);
        try {
            return Integer.parseInt(json.substring(valStart, end).replace("\"", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long extractJsonLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return 0L;
        int valStart = start + search.length();
        int end = json.indexOf(",", valStart);
        if (end == -1) end = json.indexOf("}", valStart);
        try {
            return Long.parseLong(json.substring(valStart, end).replace("\"", "").trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
