package dev.retro.papersmtp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private final String currentVersion;
    private final String repo = "AJARETRO/RetroMail";
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(String currentVersion) {
        this.currentVersion = currentVersion.startsWith("v") ? currentVersion : "v" + currentVersion;
    }

    public void checkForUpdates() {
        try {
            URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RetroMail-UpdateChecker");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String json = sb.toString();
                // Simple parsing for "tag_name":"..."
                int index = json.indexOf("\"tag_name\":\"");
                if (index != -1) {
                    int start = index + 12;
                    int end = json.indexOf("\"", start);
                    if (end != -1) {
                        latestVersion = json.substring(start, end);
                        // Clean version strings for comparison
                        String cleanCurrent = currentVersion.replace("v", "").trim();
                        String cleanLatest = latestVersion.replace("v", "").trim();
                        if (!cleanCurrent.equalsIgnoreCase(cleanLatest)) {
                            updateAvailable = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fail silently
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
