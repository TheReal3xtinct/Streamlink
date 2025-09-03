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
5. Authenticate your Streamlabs account with /streamlink streamlabs.
6. Export loyalty data (see below).
7. Place the CSV in your plugin folder.
8. Restart the server.
9. Run /streamlink sync to update all linked players’ data.

---

## 📂 CSV Workflow

- Create a new bookmark in your browser.
  
- Set the URL to this code:

javascript:(async()=>{const totalPages=100;let allUsers=[];for(let page=1;page<=totalPages;page++){const res=await fetch(`https://streamlabs.com/api/v5/cloudbot/loyalty?page=${page}`,{credentials:"include"});const json=await res.json();if(json?.data?.data){allUsers=allUsers.concat(json.data.data);console.log(`Fetched page ${page}, total so far: ${allUsers.length}`);}else{console.warn(`No data found on page ${page}`);}}const headers=["Username","Points","WatchTimeMinutes","UpdatedAt","CreatedAt"];const rows=allUsers.map(u=>[u.viewer.name,u.points,u.time,u.updated_at,u.created_at]);let csv=headers.join(",")+"\n"+rows.map(r=>r.join(",")).join("\n");const blob=new Blob([csv],{type:"text/csv"});const url=URL.createObjectURL(blob);const a=document.createElement("a");a.href=url;a.download="loyalty.csv";a.click();URL.revokeObjectURL(url);console.log("✅ loyalty.csv downloaded with",allUsers.length,"users.");})();

- Log in to Streamlabs Dashboard
  
- Go to Cloudbot → Loyalty → Users.
  
- Click the bookmarklet.

- Wait until loyalty.csv is downloaded.

- You’ll now have a loyalty.csv with all user points and watch time.

- Move the loyalty.csv file into your plugin’s data folder, where config.yml and playerdata.yml are stored.

- Run /streamlink sync --dry to preview changes.

- Run /streamlink sync to import.

- CSV headers supported:
Username, Points, Minutes

---

## Sreamlabs Access Token and Refresh Token

- Visit this [link](https://streamlink.3xtaffy.workers.dev)

- Click on Authenticate with Streamlabs (HTML)
  
- Log in to your Streamlabs (if you haven't)
  
- Click on Authorize
  
- Copy the access_token and refresh_token into the config.yml

---

## ⚙️ Configuration
`config.yml`:

```yaml
streamlabs:
  channel: "your_twitch_username"
  access-token: ""
  refresh-token: ""
  oauth-helper: "https://streamlink.3xtaffy.workers.dev" # DO NOT CHANGE UNLESS YOU KNOW WHAT YOU ARE DOING
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

## 🔒 Permissions
- **streamlink.use** -	Default access for players
- **streamlink.admin** -	Full admin privileges

---

## 🧩 Dependencies
- LuckPerms (optional)
- Bukkit/Spigot/Paper
- Java 17+

---

## 🔑 Notes

- The plugin can still fetch points directly from Streamlabs via their API, but CSV sync ensures bulk updates are accurate.

- Supports custom header names for watch time (configurable in config.yml).

- Perfect for servers that want accurate loyalty stats without relying on API limits.

---

📝 License

MIT License © 2025 Taffy
