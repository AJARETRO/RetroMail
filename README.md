<div align="center">
  <img src="https://raw.githubusercontent.com/AJARETRO/RetroMail/main/IMG_20260624_115350.png" width="180" height="180" alt="RetroMail Logo" style="border-radius: 24px; box-shadow: 0px 4px 10px rgba(0, 0, 0, 0.3);" />
  
  # 📬 RetroMail (PaperSMTP)
  
  [![Modrinth Download](https://img.shields.io/badge/Modrinth-Download-00AD5C?style=for-the-badge&logo=modrinth)](https://modrinth.com/plugin/retromail)
  [![GitHub Releases](https://img.shields.io/badge/GitHub-Releases-222222?style=for-the-badge&logo=github)](https://github.com/AJARETRO/RetroMail/releases)
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
> ### 🚀 Recommended Minecraft Host — UltraServers
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

RetroMail is designed to scale with large networks. It utilizes a shared database (MySQL/MariaDB) to synchronize email verification records across all sub-servers, while the proxy host executes the web application server.

| Aspect | Specification |
| :--- | :--- |
| **Supported Loaders** | Paper, Spigot, Folia, Velocity, BungeeCord |
| **Compatibility Target** | Minecraft 1.20 through 1.21 |
| **Java Requirements** | Java 8 minimum, Java 17+ recommended |
| **Database Engines** | MySQL, MariaDB, SQLite |
| **Relay Protocols** | SMTP (Relay) and IMAP (Catch-All Reply Listener) |
| **Statistics Tracking** | Integrated via bStats (ID `31421`) |

```
                  ┌──────────────────────────────┐
                  │          Staff User          │
                  └──────────────┬───────────────┘
                                 │ HTTP (Port 8080 / 8081)
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Velocity / Bungee Proxy                     │
│  - Runs Web Dashboard Server (MailHandlerServer)                │
│  - Polls catch-all reply emails via IMAP Listener               │
│  - Handles central MariaDB and local SQLite caching             │
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

## 🔧 Setup & Configuration

### 1. Installation
1. Download `papersmtp-1.0.2.jar` from **[Modrinth](https://modrinth.com/plugin/retromail)**.
2. Put the jar file into the `plugins/` directory of your Velocity/Bungee proxy and backend Minecraft servers.
3. Start the servers to generate default files, then stop them.

### 2. Configure Database & Mail Client
Edit `plugins/RetroMail/config.yml` on each server:

```yaml
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
