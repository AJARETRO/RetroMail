# RetroMail (PaperSMTP)

RetroMail is a powerful, enterprise-grade Minecraft plugin and proxy extension that integrates **in-game SMTP newsletter subscriptions and verification** with a **rich, responsive web dashboard** and a **secure external API**. It supports multi-server BungeeCord and Velocity proxy architectures, automated email delivery, in-game rewards, and granular security controls.

---

## Table of Contents
1. [Key Features](#1-key-features)
2. [Architecture Overview](#2-architecture-overview)
3. [Setup & Installation](#3-setup--installation)
4. [In-Game Commands & Permissions](#4-in-game-commands--permissions)
5. [SMTP & IMAP Mail Configuration](#5-smtp--imap-mail-configuration)
6. [Web Dashboard Usage](#6-web-dashboard-usage)
7. [Creating Custom HTML Mail Templates](#7-creating-custom-html-mail-templates)
8. [Creating Custom Web Dashboard Pages](#8-creating-custom-web-dashboard-pages)
9. [External API Documentation](#9-external-api-documentation)

---

## 1. Key Features
* **In-Game Verification:** Players verify their real-life emails in-game using custom GUI menus, receiving configured rewards (items, XP, commands) instantly.
* **Responsive HTML Newsletters:** Dispatch news, announcements, and maintenance alerts using beautifully styled HTML email templates.
* **Proxy-Hosted Web Dashboard:** Staff members access a centralized web inbox on the proxy to view, read, and search emails, send outbound messages, manage avatars, and configure two-factor authentication (2FA).
* **Granular Security Controls:** 
  * Admins cannot self-modify roles, update their own permissions, or self-delete.
  * Confirmation modals guard all critical actions (creating/deleting API keys, modifying staff permissions, deleting accounts).
* **API Access Tokens:** Generate API tokens with specific permission scopes (`read_mails`, `send_mails`) to integrate with external CI/CD systems or Discord bots.

---

## 2. Architecture Overview

RetroMail is designed to run efficiently in both single-server and multi-server environments:

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

## 3. Setup & Installation

### A. Prerequisites
* **Minecraft Version:** 1.20.x or higher
* **Java Version:** Java 8 minimum, Java 17+ recommended.
* **Database:** MariaDB / MySQL (mandatory for multi-server synchronization) or SQLite (for single-server mode).

### B. Installation Steps
1. Download the latest `RetroMail.jar` from the [Releases](https://github.com/AJARETRO/RetroMail/releases) page.
2. Place the jar in the `plugins/` directory of your Proxy server (Velocity/Bungee) and each backend Spigot/Paper/Folia server.
3. Start the servers to generate default configuration files, then stop them.
4. Configure the database connection parameters in `plugins/RetroMail/config.yml` on each server. Ensure they all point to the same database for synchronization.
5. In the Proxy server config, set `mail-handler.enabled: true` and define your web port (e.g., `8080`).
6. On backend Minecraft servers, set `mail-handler.enabled: false` to prevent port conflicts.
7. Start all servers.

---

## 4. In-Game Commands & Permissions

### A. General Commands
* **/email**
  * Opens the email subscription and verification GUI.
  * *Permission:* `smtp.user.use` (default: true)

### B. Admin Commands
* **/mass-email <template.html> [subject...]**
  * Sends a mass newsletter template to all verified subscribers in the database.
  * *Permission:* `smtp.admin.massmail` (default: op)
  * *Example:* `/mass-email new_season.html Season 15 Launch Announcement!`

---

## 5. SMTP & IMAP Mail Configuration

Edit the `config.yml` file to configure your mail relay and incoming reader:

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
  enabled: true
  port: 8080
  domain: "yourdomain.com"
  imap:
    host: "imap.gmail.com"
    port: 993
    ssl: true
    username: "your-catchall-email@gmail.com"
    password: "your-app-password"
    poll-interval-seconds: 30
```

---

## 6. Web Dashboard Usage

Access the web interface at `http://your-proxy-ip:port/` (e.g. `http://localhost:8080`).

### A. Pages
1. **Login Page (`index.html`):** Staff login utilizing secure hashed credentials. Supports Two-Factor Authentication checks.
2. **Inbox View (`inbox.html`):** Reads caught-reply emails and tracks threads. Contains a navigation bar for composing new outbound emails.
3. **Settings (`settings.html`):**
   * Change passwords or upload custom avatars.
   * Enable 2FA by scanning a generated TOTP QR code.
   * Create and delete API access tokens with specific scopes (`Read Emails`, `Send Emails`).
4. **Administration Panel (`admin.html`):**
   * Only accessible to accounts with the `ADMIN` role.
   * Allows managing staff permissions, restricting access to specific shared mailboxes (e.g., `support@domain.com`), configuring sender restrictions, and deleting staff accounts.

---

## 7. Creating Custom HTML Mail Templates

All email templates are stored as static `.html` files in the `plugins/RetroMail/` directory.

### Placeholders
You can use the following default placeholders inside your templates:
* `{player}`: Inserts the player's in-game name.
* `{code}`: Inserts the temporary 6-digit verification code.

### Example Template (Verification Email)
```html
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; background-color: #f4f4f4; color: #333; }
        .container { padding: 20px; background-color: #fff; border-radius: 8px; }
        .code { font-size: 24px; font-weight: bold; color: #dc2626; letter-spacing: 2px; }
    </style>
</head>
<body>
    <div class="container">
        <h2>Hey {player}!</h2>
        <p>Your verification code for Retro Network is:</p>
        <p class="code">{code}</p>
        <p>Enter this code in-game using <b>/email</b> to complete your registration.</p>
    </div>
</body>
</html>
```

---

## 8. Creating Custom Web Dashboard Pages

Static dashboard pages are located inside the plugin jar in `src/main/resources/web/`, which extract to your proxy directory under `plugins/retromail/web/`.

### Host a New Page
1. Place a new `.html` file (e.g. `custom.html`) inside the `plugins/retromail/web/` folder.
2. The proxy will automatically route and host it at `http://your-proxy-ip:port/custom.html`.

### Interact with the Backend API (JavaScript)
All API calls must contain the authentication token stored in `localStorage` (`retromail_token`):

```javascript
const token = localStorage.getItem('retromail_token');

// Fetch dashboard data
fetch('/api/dashboard', {
    headers: { 'Authorization': 'Bearer ' + token }
})
.then(response => response.json())
.then(data => {
    console.log('Profile:', data.profile);
    console.log('Mails:', data.mails);
});
```

---

## 9. External API Documentation

RetroMail provides developer-friendly external API endpoints to query or dispatch emails using generated access tokens.

### Authentication
Include one of the following headers in your HTTP request:
* `Authorization: Bearer <your_api_token>`
* `X-API-Key: <your_api_token>`

### A. Fetch Staff Mailbox Inbox
* **Endpoint:** `GET /api/external/mails`
* **Required Scope:** `read_mails`
* **Response Status:** `200 OK`
* **Response Body Example:**
```json
[
  {
    "id": 41,
    "mailFrom": "client@gmail.com",
    "mailTo": "support@yourdomain.com",
    "subject": "Question about ranks",
    "body": "Hello, how do I purchase a rank?",
    "isHtml": false,
    "receivedAt": "2026-06-24 04:12:00"
  }
]
```

### B. Send Outbound Email
* **Endpoint:** `POST /api/external/send`
* **Required Scope:** `send_mails`
* **Request Body Example:**
```json
{
  "to": "client@gmail.com",
  "subject": "Rank Purchase Help",
  "body": "<p>You can purchase ranks at our store!</p>",
  "isHtml": true
}
```
* **Response Body Example:**
```json
{
  "status": "success",
  "message": "Email sent successfully"
}
```
