# FoKits

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fokits) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fokits/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Configurable player kits with a claim GUI and in-game editor.

*Developed by Carrotio.*

## Features

- Provides a player-friendly GUI for browsing and claiming kits.
- Supports configurable cooldowns, one-time claims, and per-kit access.
- Includes an in-game editor for kit contents and behavior.
- Stores claim data and exposes per-kit PlaceholderAPI values.
- Keeps the player GUI, messages, and sounds configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fokits). Check the [versions page](https://modrinth.com/plugin/fokits/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `PlaceholderAPI`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fokits [kit]` — Open kits GUI or claim a specific kit Aliases: `/kits`, `/kit`.
- `/fokitsadmin <version|reload|editor|resetcooldown> [player] [kit]` — FoKits admin command Aliases: `/kitsadmin`, `/kitadmin`.

### Permissions

- `fokits.use` — Use FoKits player command Default: `true`.
- `fokits.admin` — Use FoKits admin command Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%fokits_time_remaining_seconds_<kit>%` — Cooldown remaining in seconds.
- `%fokits_time_remaining_<kit>%` — Formatted cooldown remaining.
- `%fokits_can_claim_<kit>%` — Whether the player can claim the kit.
- `%fokits_next_claim_at_<kit>%` — Next claim timestamp.
- `%fokits_claimed_once_<kit>%` — Whether a one-time kit has been claimed.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `config-version`, `inventory`, and `native-dialogs`.
The player kit selector is configured in `guis/player-kits.yml`; the preview is
configured in `guis/kit-preview.yml`. `messages.yml` contains the configurable
duration wording used in cooldown messages and the selector.

</details>

<details>
<summary><strong>Developer API</strong></summary>

This plugin does not expose a public developer API.

</details>
