# StreamLink
A Minecraft plugin that links your Twitch account to in-game accounts, syncs Streamlabs Cloudbot loyalty points, and enables server perks based on loyalty tiers.  

**Version:** v2.0  
**Platform:** Bukkit / Spigot / Paper  
**Java:** 17+  

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
2. Drop `StreamLink-2.0.jar` into your `plugins/` folder.
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

```

## 🔑 Commands
**/streamlink link**	- Link your Twitch account.

**/streamlink check**	-	Check link status.

**/streamlink unlink**	-	Unlink your Twitch account.

**/streamlink points**	-	Show stored loyalty data.

**/streamlink sync [file]**	-	Import loyalty CSV into playerdata.yml.

**/streamlink export [file]**	-	Export loyalty data to CSV.

**/streamlink debug on/off**	-	Toggle debug logging.

**/streamlink streamlabs**	-	Show Streamlabs OAuth helper link.

**/streamlink setup**	-	Setup guidance for LuckPerms/permissions.


---

## 📂 CSV Workflow

- Export Cloudbot data from Streamlabs dashboard using the bookmarklet or API.

- Save the CSV file as plugins/StreamLink/loyalty.csv.

- Run /streamlink sync --dry to preview changes.

- Run /streamlink sync to import.

- CSV headers supported:
Username, Points, Minutes

---

## 🔒 Permissions
- **streamlink.use** -	Default access for players
- **streamlink.admin** -	Full admin privileges

---

## 🧩 Dependencies
- LuckPerms (optional)
- Bukkit/Spigot/Paper
- Java 17+

---

📝 License

MIT License © 2025 Taffy
