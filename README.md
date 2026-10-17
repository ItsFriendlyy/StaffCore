<div align="center">

# 🛡️ StaffCore

**The Ultimate All-in-One Moderation & Staff Utility Suite for Spigot and Paper**

[[https://img.shields.io/github/v/release/ItsFriendlyy/StaffCore?color=red&logo=github]](https://github.com/ItsFriendlyy/StaffCore/releases)
[[https://img.shields.io/badge/Minecraft-1.8.8%20--%201.20%2B-brightgreen?logo=curseforge]](https://www.spigotmc.org/resources/staffcore-the-ultimate-moderation-staff-suite-1-8-8-1-20.139680/)
[[https://img.shields.io/badge/License-MIT-blue.svg]](LICENSE)
[[https://img.shields.io/badge/Platform-Paper%20%7C%20Spigot%20%7C%20BungeeCord-orange]](https://papermc.io)

[SpigotMC Resource](https://www.spigotmc.org/resources/staffcore-the-ultimate-moderation-staff-suite-1-8-8-1-20.139680/) • [Releases](https://github.com/ItsFriendlyy/StaffCore/releases) • [Issue Tracker](https://github.com/ItsFriendlyy/StaffCore/issues)

</div>

---

## 📖 Overview

**StaffCore** is a high-performance Minecraft moderation, administration, and utility plugin built for servers running **Minecraft 1.8.8 through 1.20+**. It replaces bloated multi-plugin setups by combining punishment management, freeze checks, ghost vanish, telemetry inspection, interactive `/invsee`, unsafe enchanting, and network commands into a single, clean build.

### ✨ Key Features

- **Dual-Jar Build System**: 
  - `StaffCore-Legacy.jar` (`<1.13`): Optimized for 1.8.8–1.12.2 legacy servers.
  - `StaffCore-Modern.jar` (`1.13–1.20+`): Compiled with native `api-version: '1.20'` to prevent Spigot legacy compatibility lag.
- **Interactive `/invsee`**: Open and directly edit another player's inventory or armor in real time. Items transferred between inventories stay permanently without disappearing.
- **Unsafe `/enchant`**: Apply enchantments up to level **32,767** with full tab auto-completion for every enchantment in the game.
- **Complete Punishment Engine**: Ban, Tempban, IP-Ban, Temp-IP-Ban, Mute, Temp-Mute, and Warn with optional silent (`-s`) execution.
- **Interactive Freeze System**: Locks player movement and camera interactions while displaying an on-screen title. Dispatches instant alerts if a player disconnects while frozen.
- **Ghost Vanish**: Hides staff from non-staff, server tab lists, and server list ping counters without forcing spectator mode or giving potion particles.
- **Inspection GUI (`/check`)**: Comprehensive chest interface displaying playtime, combat stats (Kills, Deaths, K/D), active punishments, and infraction history.
- **BungeeCord / Velocity Ready**: Transfer players across proxy instances using `/sendserver <player> <server>` and sync data across servers with MySQL.

---

## 🛠️ Commands & Syntax

### Staff Utilities
| Command | Usage | Description |
| :--- | :--- | :--- |
| `/invsee` | `/invsee <player> [armor]` | Inspect and edit player inventory or armor directly. |
| `/enchant` | `/enchant <name> <level>` | Apply unsafe enchantments up to level 32,767. |
| `/speed` | `/speed <1-10> [player]` | Set walk or fly speed (`1` = normal, `10` = max). |
| `/fly` | `/fly [player]` | Toggle flight mode without switching gamemodes. |
| `/invincible` | `/invincible [player]` | Toggle immunity against all damage and hunger (`/god`). |
| `/gmc` / `/gms` | `/gmc [player]` / `/gms [player]` | Switch game mode to Creative or Survival. |
| `/gmsp` / `/gma` | `/gmsp [player]` / `/gma [player]` | Switch game mode to Spectator or Adventure. |
| `/tphere` | `/tphere <player>` | Teleport a player to your current location. |
| `/tpall` | `/tpall [player \| x y z]` | Teleport all players to you or to coordinates. |
| `/sendserver` | `/sendserver <player> <server>` | Send a player to another BungeeCord/Velocity server. |

### Moderation & Punishments
| Command | Usage | Description |
| :--- | :--- | :--- |
| `/ban` | `/ban <player> [reason] [-s]` | Permanently ban a player. |
| `/tempban` | `/tempban <player> <duration> [reason] [-s]` | Temporarily ban a player (e.g. `1d`, `2h`, `30m`). |
| `/ipban` | `/ipban <player\|ip> [reason] [-s]` | Permanently IP-ban a player or address. |
| `/tempipban` | `/tempipban <player\|ip> <duration> [reason] [-s]` | Temporarily IP-ban a player or address. |
| `/unban` | `/unban <player>` | Remove a permanent or temporary ban. |
| `/unbanip` | `/unbanip <ip>` | Remove an active IP ban. |
| `/mute` | `/mute <player> [reason] [-s]` | Mute a player permanently in chat. |
| `/tempmute` | `/tempmute <player> <duration> [reason] [-s]` | Temporarily mute a player in chat. |
| `/unmute` | `/unmute <player>` | Unmute a muted player. |
| `/warn` | `/warn <player> [reason] [-s]` | Issue an official warning. |
| `/freeze` | `/freeze <player>` | Freeze a player for staff inspection. |
| `/check` | `/check <player>` | Open the telemetry inspection and history GUI. |
| `/chat` | `/chat <staff\|all>` | Switch between private staff chat and global chat. |
| `/vanish` | `/vanish` or `/v` | Toggle complete invisibility and tab list hiding. |
| `/staffcore` | `/staffcore reload` | Reload configuration and punishment data. |

---

## 🔑 Permissions

| Node | Default | Description |
| :--- | :--- | :--- |
| `staffcore.staff` | `op` | General staff permission node. |
| `staffcore.admin` | `op` | Permission to reload the plugin via `/staffcore reload`. |
| `staffcore.command.invsee` | `op` | Permission to use `/invsee`. |
| `staffcore.command.enchant` | `op` | Permission to use `/enchant`. |
| `staffcore.command.speed` | `op` | Permission to use `/speed`. |
| `staffcore.command.fly` | `op` | Permission to use `/fly`. |
| `staffcore.command.invincible` | `op` | Permission to use `/invincible` and `/god`. |
| `staffcore.command.gamemode` | `op` | Permission to use `/gmc`, `/gms`, `/gmsp`, `/gma`. |
| `staffcore.command.teleport` | `op` | Permission to use `/tphere` and `/tpall`. |
| `staffcore.command.sendserver`| `op` | Permission to use `/sendserver`. |
| `staffcore.command.ban` | `op` | Permission to ban players. |
| `staffcore.command.tempban` | `op` | Permission to tempban players. |
| `staffcore.command.mute` | `op` | Permission to mute players. |
| `staffcore.command.freeze` | `op` | Permission to freeze players. |
| `staffcore.command.check` | `op` | Permission to open the `/check` telemetry GUI. |
| `staffcore.command.vanish` | `op` | Permission to toggle vanish. |
| `staffcore.vanish.see` | `op` | Allows seeing vanished staff members. |
| `staffcore.broadcast.silent` | `op` | Allows seeing silent (`-s`) punishment broadcasts. |
| `staffcore.bypass.ban` | `false` | Protects player from bans. |
| `staffcore.bypass.mute` | `false` | Protects player from mutes. |
| `staffcore.bypass.freeze` | `false` | Protects player from being frozen. |

---

## 🔨 Building from Source

StaffCore uses Maven with automated dual-jar packaging:

```bash
# Clone repository
git clone [https://github.com/ItsFriendlyy/StaffCore.git](https://github.com/ItsFriendlyy/StaffCore.git)
cd StaffCore

# Compile and package both jars
mvn clean package
