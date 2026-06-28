<div align="center">
  <img src="https://raw.githubusercontent.com/AJARETRO/RetroMail/main/IMG_20260624_115350.png" width="180" height="180" alt="RetroMail Logo" style="border-radius: 24px; box-shadow: 0px 4px 10px rgba(0, 0, 0, 0.3);" />
  
  # 📬 RetroMail (PaperSMTP)
  
  [![Modrinth Download](https://img.shields.io/badge/Modrinth-Download-00AD5C?style=for-the-badge&logo=modrinth)](https://modrinth.com/plugin/retromail)
  [![GitHub Releases](https://img.shields.io/badge/GitHub-Releases-222222?style=for-the-badge&logo=github)](https://github.com/AJARETRO/RetroMail/releases)
  [![Licensing Portal](https://img.shields.io/badge/Licensing-Portal-indigo?style=for-the-badge&logo=auth0)](https://license.ajaretro.dev)
  [![Platform Support](https://img.shields.io/badge/Platforms-Paper%20%7C%20Spigot%20%7C%20Velocity%20%7C%20Bungee-007ACC?style=for-the-badge&logo=minecraft)](https://modrinth.com/plugin/retromail)
  [![bStats Metrics](https://img.shields.io/badge/bStats-ID%2031421-orange?style=for-the-badge&logo=chartmogul)](https://bstats.org/plugin/bukkit/RetroMail/31421)
  
  **The ultimate enterprise-grade Minecraft plugin for SMTP verification rewards, multi-server newsletter dispatches, responsive staff mail dashboard, and secure tokenized API integration.**
</div>

---

> [!IMPORTANT]
> **Looking to download the plugin?**
> Please obtain the official compiled `.jar` releases directly from our **[Modrinth Page](https://modrinth.com/plugin/retromail)**. We support Paper, Spigot, Folia, Velocity, and BungeeCord proxy networks out of the box.

---

> [!TIP]
> **🚀 Recommended Minecraft Host — UltraServers**
> Looking for a high-performance, lag-free server to run your Minecraft network and RetroMail dashboard? We are proudly affiliated with **UltraServers**!
> 
> Get enterprise-grade hosting with ultra-fast NVMe storage, dedicated CPUs, custom control panels, and 24/7 support.
> 👉 **[Deploy your high-performance server on UltraServers now!](https://ultraservers.com/aff.php?code=7013x7afNvlfx2p5)**

---

## 🌟 Why Choose RetroMail?

Maintaining player engagement and real-life connections has never been easier. RetroMail bridges the gap between your Minecraft server and real-life email clients, providing a secure, automated link.

* **🚀 Drive Player Registrations:** Reward players with items, XP, or custom commands for verifying their email address in-game.
* **📢 Multi-Server Newsletters:** Send beautiful, responsive HTML announcements and maintenance logs to all subscribed players simultaneously.
* **💻 Self-Hosted Staff Inbox:** Proxy server starts an isolated web dashboard allowing your admins to manage mail, read replies, and create API tokens.
* **🔒 Enterprise Security Hardening:** Strict API key token scopes, mandatory confirmation modals, and admin self-modify protection (no self-deletions or role modifications).

---

## 💡 Typical Use Cases

* **Email Verification Rewards:** Let players run `/email` in-game, input their email, and receive a verification code. Upon successful verification, reward them with key items, money, or custom rank commands.
* **Newsletter Subscriptions:** Build a mailing list of your player base to dispatch server updates, store sales, and event announcements directly to their real-life mailboxes.
* **Support Ticketing Sync:** Read email replies from players directly in the proxy web dashboard, forming centralized thread histories.
* **External Integrations:** Automate announcements or check subscriber status from external Discord bots or website panels using secure, scoped API keys.

---

## ⚙️ Technical Specifications & Architecture

RetroMail supports both **single-server (standalone)** setups for smaller networks and **multi-server (proxy)** setups for large Velocity/BungeeCord networks.

### 🔌 Architecture Options

#### Option A: Single-Server (Standalone Spigot/Paper/Folia)
For standalone servers, no proxy is required. RetroMail executes all operations inside the single server instance:
* **Web Dashboard:** Hosted directly by the Spigot/Paper server instance on the assigned port.
* **Email & Polling Workers:** Outbound SMTP queues and inbound IMAP listeners run asynchronously in background thread pools on the server instance.
* **Database:** Connects directly to local SQLite (`.db` files) or a local MySQL server.
* **In-Game Systems:** GUI menus, rewards, and player verification logic run on the same server.

```
┌─────────────────────────────────────────────────────────────────┐
│           Single Standalone Server (Paper/Spigot/Folia)          │
│  - Hosts Netty Web Dashboard Server directly                    │
│  - Executes IMAP incoming reply poll listener                   │
│  - Handles SQLite / MySQL database storage                      │
│  - Runs in-game chest GUI settings & rewards                    │
└─────────────────────────────────────────────────────────────────┘
```

#### Option B: Multi-Server Proxy Networks (Velocity/BungeeCord)
For large networks, RetroMail delegates tasks to prevent port collision and reduce resources on sub-servers:
* **Web Dashboard & IMAP Listener:** Hosted only on the Proxy server (Velocity/Bungee).
* **Database:** Syncs dynamically across all backend nodes using a shared MySQL/MariaDB database.
* **In-Game Systems:** Backend Spigot/Paper/Folia nodes communicate with the proxy via plugin messaging channels to execute rewards when players verify or transition servers.

```
                  ┌──────────────────────────────┐
                  │          Staff User          │
                  └──────────────┬───────────────┘
                                 │ HTTP (Port 8080)
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Velocity / Bungee Proxy                     │
│  - Runs Web Dashboard Server (MailHandlerServer)                │
│  - Polls catch-all reply emails via IMAP Listener               │
│  - Handles central MariaDB database and local SQLite caches     │
└────────────────────────────────┬────────────────────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Folia Server   │     │  Paper Server   │     │  Spigot Server  │
│  - Reads DB     │     │  - Reads DB     │     │  - Reads DB     │
│  - Handles GUI  │     │  - Handles GUI  │     │  - Handles GUI  │
│  - Gives Reward │     │  - Gives Reward │     │  - Gives Reward │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

---

| Aspect | Specification |
| :--- | :--- |
| **Supported Loaders** | Paper, Spigot, Folia, Velocity, BungeeCord |
| **Compatibility Target** | Minecraft 1.8.8 through 26.2 |
| **Java Requirements** | Java 8 minimum, Java 17+ recommended |
| **Database Engines** | MySQL, MariaDB, SQLite |
| **Relay Protocols** | SMTP (Relay) and IMAP (Catch-All Reply Listener) |
| **Statistics Tracking** | Integrated via bStats (ID `31421`) |

---

## 🔧 Setup & Configuration

### 1. Installation
1. Download `papersmtp-1.0.4.jar` from **[Modrinth](https://modrinth.com/plugin/retromail)**.
2. Put the jar file into the `plugins/` directory of your Velocity/Bungee proxy and backend Minecraft servers.
3. Start the servers to generate default files, then stop them.

### 2. Configure Database & Mail Client
Edit `plugins/RetroMail/config.yml` on each server:

```yaml
# Multi-Server Sync Settings (Set to false for standalone setups)
multi-server:
  enabled: false

# Security Token (Must match on all proxy and backend servers if multi-server sync is enabled)
security:
  secret-token: "your-shared-secret-token-here"

database:
  type: 'mysql'
  mysql:
    host: 'localhost'
    port: 3306
    database: 'retromail'
    username: 'root'
    password: 'password'

smtp:
  host: 'smtp-relay.brevo.com'
  port: 587
  username: 'your-smtp-username'
  password: 'your-smtp-password'
  ssl: false
  starttls: true
  from-address: 'newsletter@yourdomain.com'
  from-name: 'Retro Server'

mail-handler:
  enabled: true   # Keep TRUE on Proxy server, set to FALSE on backend servers to avoid port collision
  port: 8080      # The web dashboard dashboard port
  domain: "yourdomain.com"
  imap:
    host: "imap.gmail.com"
    port: 993
    ssl: true
    username: "your-catchall-email@gmail.com"
    password: "your-app-password"
    poll-interval-seconds: 30

# Web Dashboard Branding & Links (Customizes the staff portal links dynamically)
branding:
  server-name: 'Retro Network'
  discord-link: 'https://discord.gg/retro'
  documentation-link: 'https://docs.ajaretro.dev'
  forum-link: 'https://forum.ajaretro.dev'
```

### 3. Brevo (Sendinblue) SMTP Setup Guide
Setting up Brevo to work as your SMTP relay is straightforward. Follow these steps:

1. **Create a Free Account:** Sign up at [Brevo.com](https://www.brevo.com/) (the free plan includes 300 free emails per day).
2. **Add & Verify Your Sender Domain:**
   * Go to **Senders & IP** in the top-right account dropdown.
   * Click **Domains** -> **Add a Domain**.
   * Enter your server domain (e.g. `yourdomain.com`) and add the generated DNS TXT records to your domain provider (Cloudflare, GoDaddy, etc.) to verify ownership and secure email deliverability (DKIM/SPF).
3. **Get Your SMTP Credentials:**
   * In the top-right account menu, click **SMTP & API**.
   * Go to the **SMTP** tab.
   * You will see your SMTP Host (`smtp-relay.brevo.com`) and Port (`587`).
   * Click **Generate a new SMTP key** (Master password) and copy the generated key string.
4. **Update config.yml:**
   * Insert these credentials into your `plugins/RetroMail/config.yml` configuration:
     ```yaml
     smtp:
       host: 'smtp-relay.brevo.com'
       port: 587
       username: 'your-brevo-account-email@domain.com' # Your Brevo account email
       password: 'your-generated-smtp-master-key'      # The key you copied in Step 3
       ssl: false
       starttls: true
       from-address: 'newsletter@yourdomain.com'       # Must match your verified sender domain!
       from-name: 'Your Server Name'
     ```
5. **Restart Server:** Restart the server or proxy. RetroMail will now use Brevo to securely send HTML newsletters and verification codes!

### 4. Cloudflare DNS Setup for Brevo (Prevent Spam Folders)

To ensure that the emails sent by RetroMail via Brevo do not land in your players' spam folders, you must authenticate your sending domain. This is done by adding SPF, DKIM, and DMARC records to your Cloudflare DNS settings.

Follow these step-by-step instructions to configure your records:

#### Step A: Access Cloudflare DNS Settings
1. Log in to your [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. Select your domain from the list.
3. Click on the **DNS** option in the left-hand sidebar, then click on **Records**.

#### Step B: Add the SPF Record
SPF (Sender Policy Framework) defines which servers are authorized to send mail on behalf of your domain.
* **Type:** `TXT`
* **Name (Host):** `@` (representing your root domain)
* **TTL:** `Auto`
* **Content (Value):** `v=spf1 include:spf.sendinblue.com ~all`

*Note: If your domain already has an existing SPF record (a TXT record beginning with `v=spf1`), do not create a duplicate record. Instead, edit your existing record to merge the Brevo include block. For example, if your current record is `v=spf1 include:_spf.google.com ~all`, update it to: `v=spf1 include:_spf.google.com include:spf.sendinblue.com ~all`.*

#### Step C: Add the DKIM Record
DKIM (DomainKeys Identified Mail) cryptographically signs outbound emails to verify they were sent by your domain and were not tampered with during transit.
1. Log in to your Brevo account, go to the top-right menu, select **Senders & IPs**, and click on **Domains**.
2. Click **Configure** next to your domain. Under the DKIM section, Brevo will provide a host/name (usually `mail._domainkey`) and a long key value starting with `v=DKIM1; k=rsa; p=...`.
3. In Cloudflare DNS, add a new record:
   * **Type:** `TXT`
   * **Name (Host):** `mail._domainkey` (or whatever selector Brevo specifies)
   * **TTL:** `Auto`
   * **Content (Value):** *(Paste the exact long key value provided by Brevo)*

#### Step D: Add the DMARC Record
DMARC (Domain-based Message Authentication, Reporting, and Conformance) provides instructions to receiving mail servers on how to handle emails that fail SPF or DKIM checks. Having a DMARC record is highly recommended by email clients like Gmail and Yahoo to prevent your emails from being flagged as spam.
1. In Cloudflare DNS, add a new record:
   * **Type:** `TXT`
   * **Name (Host):** `_dmarc`
   * **TTL:** `Auto`
   * **Content (Value):** `v=DMARC1; p=none; rua=mailto:dmarc-reports@yourdomain.com`
2. Replace `dmarc-reports@yourdomain.com` with a valid email address on your domain where you want to receive deliverability reports, or simply omit the reporting block and use `v=DMARC1; p=none` if you do not want to receive reports.

#### Step E: Add MX Records
MX (Mail Exchange) records specify the mail servers responsible for receiving email on behalf of your domain. Having valid MX records configured is crucial for overall domain reputation and ensures you can receive replies to your server emails.
1. If you are using Brevo to receive incoming emails, add the MX records displayed in your Brevo domain configuration page.
2. If you use another mail provider (like Google Workspace or Outlook) for receiving emails, ensure those MX records are set up in your Cloudflare DNS settings.

---

## 🎮 Commands & Permissions

### Player Commands
* **/email**
  * Description: Opens the verification GUI or verifies a code.
  * Permission: `smtp.user.use` *(default: true)*

### Staff Administration
* **/mass-email <template.html> [subject...]**
  * Description: Sends a beautifully styled HTML newsletter to all verified users.
  * Permission: `smtp.admin.massmail` *(default: op)*
  * Example: `/mass-email welcome.html Welcome to Retro Network!`

* **Console-Only Command: `email create-staff <username> <email> <role>`**
  * Description: Registers a new staff account on the shared database and sends an automated welcome email with credentials.
  * Execute Location: Run directly from any backend Spigot/Paper server console (not game chat).
  * Valid Roles: `ADMIN` or `STAFF`.
  * Note: The email is sent from the verified SMTP relay address so that it delivers directly to the staff member's inbox safely.

---

## 💻 Web Dashboard Customization

Dashboard template files extract automatically to `plugins/retromail/web/` on the proxy server.

### Adding Custom HTML Pages
1. Save your custom page (e.g. `guide.html`) in `plugins/retromail/web/`.
2. The proxy web server will host it immediately at `http://your-proxy-ip:port/guide.html`.

### JavaScript API Integrations
Always retrieve and supply the stored JWT bearer token in request headers:

```javascript
const token = localStorage.getItem('retromail_token');

fetch('/api/dashboard', {
    headers: { 'Authorization': 'Bearer ' + token }
})
.then(response => response.json())
.then(data => {
    console.log('Active Mailboxes:', data.mails);
});
```

---

## 🔗 External REST API & Access Tokens

Create tokens via the Web Dashboard Settings page to integrate RetroMail with external scripts or systems.

### Authentication Headers
Supply one of the following in your HTTP requests:
* `Authorization: Bearer <token>`
* `X-API-Key: <token>`

### Endpoints

#### Get Inbox Messages
* **Endpoint:** `GET /api/external/mails`
* **Required Scope:** `read_mails`
* **Response Body Example:**
```json
[
  {
    "id": 12,
    "mailFrom": "player@gmail.com",
    "mailTo": "support@yourdomain.com",
    "subject": "Help with rewards",
    "body": "Hi, I didn't get my verification items.",
    "isHtml": false,
    "receivedAt": "2026-06-24 05:44:00"
  }
]
```

#### Send Outbound Email
* **Endpoint:** `POST /api/external/send`
* **Required Scope:** `send_mails`
* **Request Body Example:**
```json
{
  "to": "player@gmail.com",
  "subject": "Support Ticket Reply",
  "body": "<h1>Support Response</h1><p>We have credited the items to your inventory.</p>",
  "isHtml": true
}
```
* **Response Body:**
```json
{
  "status": "success",
  "message": "Email sent successfully"
}
```

---

## 📊 Data Collection & Telemetry

RetroMail collects anonymous usage statistics and checks for updates. You can opt-out of these features at any time:

* **bStats Metrics:** Tracks anonymous data (e.g. server software, player count, Java version, country). This helps us monitor plugin adoption. You can disable this by setting `enabled: false` in your server's `plugins/bStats/config.yml`.
* **Update Checker:** Automatically queries the GitHub Releases API at server startup to check for the latest versions. No personal or server identification data is sent.

---

## 🔒 Security Hardening & Administrative Protection

RetroMail is designed to operate safely inside enterprise network topologies, enforcing rigorous boundaries to shield servers from compromise:

* **Plugin Channel Signature Verification:** Communication payloads between Proxy and backend servers on the `papersmtp:queue` channel are authenticated using a shared secret key via HmacSHA256. All packets include a signed timestamp to prevent replay attacks (validated within a 30-second window), blocking malicious players from spoofing reward dispatches or console commands if backend ports are misconfigured.
* **Administrative Self-Modification Safeguards:** The web dashboard backend blocks active staff members from deleting their own user account to prevent locking out all administrator paths. Staff members are also blocked from promoting themselves or modifying other administrators' permissions.
* **REST API Constraint Auditing:** All input payloads sent to the dashboard REST endpoints are validated. Registration usernames must match `^[a-zA-Z0-9_-]{3,16}$` and passwords must be between 6 and 128 characters, protecting system CPU threads from hashing resource-exhaustion attacks.
* **Directory Traversal Guards:** In-game test subcommands (e.g. `/email test`) validate template file inputs, checking for double dots (`..`), forward slashes (`/`), or backslashes (`\`) to block directory traversal attacks.

---

## 🚀 Convenience & Usability Focus

RetroMail is built to simplify developer and administrator workflows:

* **Dynamic Branding (No HTML Editing):** Customize the server name, Discord invite link, forums URL, and documentation links in `config.yml`. The proxy web server automatically renders these values dynamically in the staff portal frontend.
* **Self-Contained Web Server:** The Velocity/Bungee proxy starts an embedded, lightweight Netty HTTP server to host the dashboard interface without requiring external web server software like Nginx or Apache.
* **Automated Template Extraction:** Default HTML templates (newsletters, maintenance notifications) extract automatically to the plugin directory on first boot.
* **Intuitive Settings Chest GUI:** Players toggle News, Surveys, and Sales notifications directly through a clean 27-slot chest GUI.

---

## ⚡ Enterprise Architecture & Reliability

Built to scale on high-traffic networks hosting thousands of concurrent players:

* **Relocated HikariCP Connection Pooling:** Shaded and relocated inside the compiled JAR to prevent dependency version conflicts. Employs connection pools of up to 10 connections for MySQL, and restricts SQLite pools to 1 connection to ensure synchronous thread pipeline operations and prevent SQLite database write-locking issues.
* **Socket Timeout Hardening:** Connection and read/write socket timeouts (5 seconds) are configured on both IMAP and SMTP configurations to prevent worker threads from blocking indefinitely when mail servers go offline.
* **Off-Thread Compliance (Folia & Bukkit):** Database reads/writes are entirely offloaded to background worker thread pools, protecting the main Minecraft tick loop from lag spikes.
* **Command Queue Backoff Manager:** Outgoing emails that fail during relay downtime are saved locally and retried automatically every 60 seconds using a thread-safe exponential backoff schedule (up to 5 attempts).

---

## 🇪🇺 Data Security & Privacy (GDPR & CCPA Architecture)

While RetroMail provides the architectural tools necessary to maintain strict privacy standards (GDPR / CCPA), the server network/administrator operates as the sole Data Controller. By deploying this software, you assume full legal responsibility for player data management. The plugin provides the following compliance mechanisms:

* **The Right to Be Forgotten (Hard Purging):** When a player runs `/email unsubscribe` or unlinks their account in the GUI, RetroMail immediately deletes their subscription status from `papersmtp_subscriptions` and deletes all entries in `papersmtp_mails` containing their email address. No soft-deletes or persistent logs containing PII are retained.
* **Automated Daily Log Rotation:** Email contents are pruned automatically. A daily background scheduler purges all sent/received logs in `papersmtp_mails` older than **30 days** to enforce data minimization.
* **Self-Hosted Sovereignty:** All data is stored on databases exclusively owned, hosted, and controlled by the server network. The software transmits no data to the developers or third-party networks.

---

## ⚖️ Legal Compliance & Admin Responsibility Guidelines

RetroMail is a self-hosted utility provided "as-is". It is the sole responsibility of the server administrator to ensure its deployment complies with local and international regulations.

* **CAN-SPAM Act & Anti-Spam Compliance:** Outgoing emails utilize transactional or newsletter frameworks with instant database-driven opt-outs. **Admin Requirement:** To comply with global anti-spam laws, administrators must manually update the default HTML templates (located in `plugins/retromail/web/`) to include their network's valid physical postal address or PO Box in the email footer. The plugin developers are not liable for any blacklisting or spam violations incurred by your broadcasts.
* **COPPA (Child Online Privacy Protection Act):** RetroMail does not perform age verification and cannot determine the age of players executing in-game commands. If your Minecraft network caters to or is accessible to players under the age of 13, you must implement age-gating or parental consent checkpoints before granting access to the `/email` permission node.
* **Domain Authentication Reputation:** The software mandates the configuration of SPF, DKIM, and DMARC records on your domain (e.g., through Cloudflare) to prevent spoofing. Server owners assume all liability for domain reputation damages resulting from bulk mail dispatches.
* **Limitation of Liability:** In no event shall the authors or copyright holders of RetroMail be held liable for any data leaks, regulatory fines, legal actions, or server infrastructure compromises resulting from the use, misuse, or misconfiguration of this software.

---

## 💎 Commercial Licensing & Portal Tiers

RetroMail commercial licenses can be purchased and managed directly via our licensing portal at [license.ajaretro.dev](https://license.ajaretro.dev). Payments are processed securely via NOWPayments (Crypto).

### 🛠️ Portal License Tiers

* **Tier 1 (Personal) | $20 / 30 Days**  
  Supports up to **4** concurrent server instances. Perfect for small setups and developer environments.
  
* **Tier 2 (Pro) | $35 / 30 Days**  
  Supports up to **8** concurrent server instances. Includes 15-minute zero-downtime key rotation overlap.
  
* **Tier 3 (Enterprise) | $60 / 30 Days**  
  Supports up to **14** concurrent server instances. Includes 15-minute zero-downtime key rotation overlap and priority rate limit validations.
  
* **Tier 4 (Overdrive) | $90 / 30 Days**  
  Supports up to **24** concurrent server instances. Includes all Enterprise benefits plus remote key emergency freezing and instant reactivation control.

---

### 🔑 Features & Self-Service Operations

The licensing portal includes comprehensive self-service features:
* **Zero-Downtime Key Rotation:** Rotate keys instantly with a 15-minute validity overlap for active server node sessions.
* **Emergency Remote Freezing:** Temporarily freeze or unfreeze keys instantly from your dashboard to protect your endpoints.
* **Real-time Session Monitoring:** Track active connection IPs, ports, and validation heartbeat pings in real-time.
* **Security & Multi-Factor Auth:** Strengthen account access using Email OTP validation codes and Google Authenticator 2FA.

---

## 🏷️ Release History

### v1.0.5 (Current)
* **Dedicated Standalone Sync Toggle:** Added a dedicated `multi-server.enabled` configuration property (default: `false`). When set to `false` for standalone setups, RetroMail disables all proxy plugin channel messaging (`papersmtp:queue`) and executes reward commands locally and immediately.
* **Plugin Channel Signature Hardening:** Secured cross-server plugin messaging queues with HmacSHA256 signatures and timestamp validations using a shared `security.secret-token` to prevent packet spoofing and replay attacks.

### v1.0.4 - BloodMoney
* **HikariCP Connection Pooling:** Integrated robust SQL connection pooling on MySQL databases (and SQLite pools of size 1) to enable non-blocking concurrent API queries, improving proxy dashboard latency and server stability under heavy load.
* **IMAP/SMTP Socket Timeout Hardening:** Configured explicit connection and read/write socket timeouts (5 seconds) across all inbound and outbound JavaMail configurations to prevent polling threads from hanging indefinitely during network drops.
* **GDPR & CCPA PII Scrubbing:** Wiped all email log entries matching a player's address directly upon unsubscription or unlink commands to ensure full PII data privacy compliance.
* **Auto-Log Pruning:** Added an automated daily database cleanup job to prune sent/received email logs older than 30 days, avoiding HTML-induced database storage bloat.

### v1.0.3 - Bonefire
* **API Hardening:** Injected strict length limits and character format enforcements across all REST endpoints to protect against malformed data and ensure robust operation.
* **Directory Traversal Protection:** Hardened the `/email test` console subcommand from path traversal inputs using folder safety blocks.
* **Client-Side Form Constraints:** Synchronized HTML element inputs (`maxlength`, `minlength`, `pattern`) in all web forms (`index.html`, `inbox.html`, `settings.html`, `admin.html`) to reject invalid inputs early.
* **UI Polish:** Integrated glowing glassmorphic components, responsive form invalid highlights, smooth active transitions, and sleek styling.

### v1.0.2 - BloodBath
* **Dynamic Branding:** Introduced customizable dashboard settings for branding properties (server name, Discord link, wiki, and forums) without hardcoding HTML.
* **Folia Off-Thread Compliance:** Offloaded all synchronous SQL preference/subscription updates from the Minecraft main/region thread.
* **Outbound Mail logs:** Integrated logging details for outbound SMTP verification dispatches directly inside console warnings and staff dashboards.
