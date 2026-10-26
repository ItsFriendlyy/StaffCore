# 🛡️ StaffCore (v1.2.3)

**StaffCore** is a lightweight, all-in-one **Minecraft Moderation, Staff Utility, and Server Essentials Suite** built for **Spigot, Paper, and Purpur (`1.8.8` – `26.3`)**. It combines a complete punishment engine, interactive inspection GUIs, staff vanish/freeze tools, and essential player teleportation (`Spawn`, `Warps`, `Homes`) in a single zero-dependency plugin.

---

## 🌟 Key Highlights

* **🔨 Complete Punishment Suite**:
  * Permanent & temporary **Bans**, **IP-Bans**, **Mutes**, **Warnings** (with configurable auto-ban thresholds), and **Jails**.
  * Append `-s` to any punishment command for **silent staff-only broadcasts** (`[Silent]`).
* **🔒 Escape-Proof Jail System**:
  * Create multiple jails with `/setjail <name>` and jail players permanently (`/jail`) or temporarily (`/tempjail`) with automatic release and return-teleportation.
  * Jailed players respawn inside jail if they die, stay jailed across relogs, and cannot run commands, throw Ender Pearls/projectiles/potions, eat Chorus Fruit, use portals, or break/place blocks.
* **🕵️ Interactive `/check` & `/invsee` GUIs**:
  * **`/check <player>`**: View online/offline status, UUID, right-click IP reveal & alt-account detector, playtime, K/D combat stats, active punishments, and a clickable **54-slot Punishment History GUI** showing every past punishment reason, staff member, date, and duration.
  * **`/invsee <player>`**: Live two-way 54-slot inventory editor including armor and off-hand synchronization.
* **👻 Staff Vanish, Freeze & Staff Chat**:
  * **`/vanish` (`/v`)**: Full invisibility hidden from players, tablist, and server lists, with silent chest inspection.
  * **`/freeze <player>`**: Locks movement, combat, blocks, and commands with disconnect alerts for staff.
  * **`/chat <staff|all>`**: Toggleable staff chat channel or quick `#message` prefix.
* **🏠 Spawn, Warps & Player Homes**:
  * **`/setspawn` & `/spawn`**: Full spawn management with automatic first-join and non-bed respawn handling.
  * **`/setwarp`, `/delwarp` & `/warp`**: Server warps with full tab-completion and staff mass-warping (`/warp <name> *`).
  * **`/sethome`, `/delhome` & `/home`**: Personal player homes with configurable max-home limits and per-player tab-completion.
* **🛠️ 20+ Staff Utility Commands**:
  * `/gmc`, `/gms`, `/gmsp`, `/gma`, `/fly`, `/speed <1-10>`, `/invincible`, `/give` (full `1.8.8`–`1.21+` item support including Netherite, Tridents, Maces, Wind Charges & aliases), `/effect`, `/enchant` (up to level 255), `/heal`, `/feed`, `/repair`, `/clear`, `/cleargrounditems`, `/tphere`, `/tpall`, `/struck`, `/sudo`, and `/sendserver`.
  * Supports the `*` wildcard on utility commands to target all online players at once.
* **🌐 Network & Storage Ready**:
  * Supports local YAML (`punishments.yml` & `states.yml`) or **MySQL** synchronization + **BungeeCord/Velocity** `/sendserver` support.

---

## ⚡ Commands & Permissions

### 🔨 Moderation & Punishments
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ban <player> [reason] [-s]` | `staffcore.command.ban` | Permanently ban a player |
| `/tempban <player> <duration> [reason] [-s]` | `staffcore.command.tempban` | Temporarily ban a player |
| `/ipban <player\|ip> [reason] [-s]` | `staffcore.command.ipban` | Permanently IP-ban a player or IP |
| `/tempipban <player\|ip> <duration> [reason] [-s]` | `staffcore.command.tempipban` | Temporarily IP-ban a player or IP |
| `/unban <player>` | `staffcore.command.unban` | Unban a player |
| `/unbanip <player\|ip>` | `staffcore.command.unbanip` | Unban an IP by player IGN or IP |
| `/mute <player> [reason] [-s]` | `staffcore.command.mute` | Permanently mute a player |
| `/tempmute <player> <duration> [reason] [-s]` | `staffcore.command.tempmute` | Temporarily mute a player |
| `/unmute <player>` | `staffcore.command.unmute` | Unmute a player |
| `/warn <player> [reason] [-s]` | `staffcore.command.warn` | Warn a player |
| `/setjail <jail_name>` | `staffcore.command.setjail` | Save a jail location |
| `/deljail <jail_name>` | `staffcore.command.setjail` | Delete a jail location |
| `/jail <player> <jail_name> [reason] [-s]` | `staffcore.command.jail` | Permanently jail a player |
| `/tempjail <player> <jail_name> <duration> [reason] [-s]` | `staffcore.command.tempjail` | Temporarily jail a player |
| `/unjail <player>` | `staffcore.command.unjail` | Release a jailed player |

### 🏠 Spawn, Warps & Homes
| Command | Permission | Default | Description |
| :--- | :--- | :--- | :--- |
| `/setspawn` | `staffcore.command.setspawn` | `op` | Set the server spawn location |
| `/spawn [player\|*]` | `staffcore.command.spawn` | `true` | Teleport to spawn (`staffcore.command.spawn.others` for others) |
| `/setwarp <warp_name>` | `staffcore.command.setwarp` | `op` | Create or update a server warp |
| `/delwarp <warp_name>` | `staffcore.command.delwarp` | `op` | Delete a server warp |
| `/warp [warp_name] [player\|*]` | `staffcore.command.warp` | `true` | Teleport to a warp (`staffcore.command.warp.others` for others) |
| `/sethome [home_name]` | `staffcore.command.sethome` | `true` | Save a personal home (`staffcore.command.sethome.unlimited` for no limit) |
| `/delhome <home_name>` | `staffcore.command.delhome` | `true` | Delete a personal home |
| `/home [home_name]` | `staffcore.command.home` | `true` | Teleport to a personal home |

### 🕵️ Inspection & Staff Tools
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/check <player>` | `staffcore.command.check` | Open player stats & punishment history GUI (`staffcore.check.ip` to reveal IPs) |
| `/invsee <player>` | `staffcore.command.invsee` | Live 54-slot inventory, armor & off-hand editor |
| `/freeze <player>` | `staffcore.command.freeze` | Freeze or unfreeze a player |
| `/vanish` (`/v`) | `staffcore.command.vanish` | Toggle full vanish mode |
| `/chat <staff\|all>` | `staffcore.command.chat` | Switch between Staff Chat and Global Chat |
| `/gmc`, `/gms`, `/gmsp`, `/gma` | `staffcore.command.gamemode` | Change game modes (`staffcore.command.gamemode.others` for others) |
| `/fly [player\|*]` | `staffcore.command.fly` | Toggle persistent flight |
| `/speed <1-10> [player\|*]` | `staffcore.command.speed` | Set walk or fly speed (1–10) |
| `/invincible [player\|*]` | `staffcore.command.invincible` | Toggle invincibility & hunger lock |
| `/give <player\|*> <item> [amount]` | `staffcore.command.give` | Give any 1.8–1.21+ item |
| `/effect <player\|*> <potion\|clear> [sec] [lvl]` | `staffcore.command.effect` | Apply or clear potion effects up to level 255 |
| `/enchant <enchantment> [level]` | `staffcore.command.enchant` | Enchant held item up to level 255 |
| `/heal [player\|*]` | `staffcore.command.heal` | Fully heal player(s) |
| `/feed [player\|*]` | `staffcore.command.feed` | Fully feed player(s) |
| `/repair` | `staffcore.command.repair` | Repair held item to full durability |
| `/clear [player\|*]` | `staffcore.command.clear` | Clear inventory, armor & off-hand |
| `/cleargrounditems` | `staffcore.command.cleargrounditems` | Remove all dropped items across worlds |
| `/tphere <player\|*>` | `staffcore.command.teleport` | Teleport player(s) to you |
| `/tpall [player \| x y z]` | `staffcore.command.teleport` | Teleport all players to you, a player, or coordinates |
| `/struck [player\|*]` | `staffcore.command.struck` | Strike lightning |
| `/sudo <player\|*> <cmd \| m:msg>` | `staffcore.command.sudo` | Force command execution or chat |
| `/sendserver <player\|*> <server>` | `staffcore.command.sendserver` | Send player(s) to a BungeeCord/Velocity server |
| `/staffcore <reload\|update>` | `staffcore.admin` | Reload config/data or check for updates |

---

## 📦 Installation
1. Download the latest `StaffCore-1.2.3.jar` from **Releases**.
2. Drop it into your server's `/plugins/` directory.
3. Restart your server and customize `plugins/StaffCore/config.yml` as desired.
