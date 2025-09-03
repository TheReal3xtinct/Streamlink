# StreamLink
A Minecraft plugin that links your Twitch account to in-game accounts, syncs Streamlabs Cloudbot loyalty points, and enables server perks based on loyalty tiers.  

---

## ✨ Features
- 🔗 **Twitch Account Linking:** Players can securely link their Twitch account using OAuth.  
- 📊 **Streamlabs Loyalty Integration:** Fetch loyalty points and watch time from Streamlabs API.  
- 📥 **CSV Sync & Import:** Import/export loyalty data using Cloudbot CSV exports.  
- 🛠️ **Admin Tools:** Bulk data sync, backup player data, and debug mode.  
- 🎭 **LuckPerms Integration:** Automatically assign in-game permission tiers based on loyalty points.  

---

## 📥 Installation
1. Download the latest JAR from [Releases](https://github.com/TheReal3xtinct/Streamlink/releases).
2. Drop `StreamLink-v2.0.0.jar` into your `plugins/` folder.
3. Start the server to generate `config.yml`.
4. Configure Streamlabs OAuth and Twitch settings (see below).
5. Restart the server.

---

## ⚙️ Configuration
`config.yml`:

```yaml
streamlabs:
  channel: "your_twitch_username"
  access-token: ""
  refresh-token: ""
  oauth-helper: "https://your-worker-url" # Cloudflare Worker endpoint
  polling:
    enabled: true
    interval_seconds: 30

loyalty:
  csv_watch_minutes_headers: ["minutes", "time", "time_watched"]

debug: false
