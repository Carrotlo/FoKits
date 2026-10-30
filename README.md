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

FoKits exposes `me.foesio.foKits.api.FoKitsApi` through Bukkit's services registry. It is registered after FoKits has loaded its kits and menus and removed during disable. Do not construct or retain FoKits' internal `KitService`, `KitRepository`, `GuiManager`, or model classes.

### Consumer setup

Compile against the matching FoKits jar as `provided` (Maven) or `compileOnly` (Gradle); do not shade it into the consumer. The project coordinates are `me.foesio:FoKits:2.2`, but this project does not publish a Maven repository. Install or reference the actual built jar locally. A mandatory `plugin.yml` integration uses `depend: [FoKits]`. An optional one uses `softdepend: [FoKits]` and must load classes that reference FoKits only after checking that the provider is enabled.

For Maven, install the local built jar with `mvn install:install-file -Dfile=FoKits-2.2.jar -DgroupId=me.foesio -DartifactId=FoKits -Dversion=2.2 -Dpackaging=jar`, then declare:

```xml
<dependency>
    <groupId>me.foesio</groupId>
    <artifactId>FoKits</artifactId>
    <version>2.2</version>
    <scope>provided</scope>
</dependency>
```

```java
RegisteredServiceProvider<FoKitsApi> registration =
        getServer().getServicesManager().getRegistration(FoKitsApi.class);
if (registration != null) {
    FoKitsApi kits = registration.getProvider();
    KitClaimOutcome result = kits.claimKit(player, "starter");
    if (result.successful()) {
        // Claim state and items have been applied.
    }
}
```

Imports: `org.bukkit.plugin.RegisteredServiceProvider`, `me.foesio.foKits.api.FoKitsApi`, and `me.foesio.foKits.api.KitClaimOutcome`. Reacquire the service after a provider disable/enable; a cached object returns unavailable results while disabled. The API does not send player feedback or play feedback sounds. It enforces `fokits.use` and all per-kit restrictions. The menu opener also enforces `fokits.use`; its `true` result means the open request was accepted, not that a rendered inventory was guaranteed.

`listKits` and `findKit` return detached metadata snapshots. `KitSnapshot.icon()` returns a fresh item clone each time. `evaluateClaim` returns `AVAILABLE` when FoKits' current rules pass; it does not dispatch a pre-claim event. `claimKit` returns `SUCCESS` only after the claim path applies items and claim state. It may return `CANCELLED` if a listener vetoes the pre-event, `BUSY` for a nested claim for the same player, or another specific denial. A missing key returns `NOT_FOUND`. A disabled provider or offline player returns its corresponding status. Null player or UUID arguments are invalid and throw `NullPointerException`.

`resetCooldown(UUID, key)` clears one saved last-claim timestamp. `resetAllCooldowns(UUID)` clears all saved last-claim timestamps and reports the count. Neither clears claimed-once flags. `resetOneTimeClaim(UUID, key)` clears the claimed-once flag even if that kit currently uses cooldown mode; it leaves the last-claim timestamp intact. Per-kit resets return `NOT_FOUND` for unknown kits and `UNCHANGED` when there is nothing to clear. A reset returns `PERSISTENCE_FAILED` if its synchronous YAML save fails; its cached change is rolled back. Resets do not deliver items or fire claim events. The legacy claim path still logs save failure without reporting it, so claim `SUCCESS` and `KitClaimedEvent` do not guarantee disk durability in that failure case.

Call player methods from the player's owning server execution context. Do not call any method from an arbitrary async task. On Folia, use the player's entity scheduler for player methods; for an online player's UUID resets, also use that player's scheduler. Offline resets and kit metadata queries must run in a server controlled synchronous context. FoKits' current multi-region behavior has not been live verified.

### Events

`KitClaimPreEvent` is cancellable. FoKits fires it after its normal eligibility checks and before inventory delivery, claim-state mutation, configured commands, or broadcast. After listeners return, FoKits re-reads the kit and rechecks its rules. Cancellation returns `CANCELLED` and has no claim side effects.

`KitClaimedEvent` is not cancellable. It fires after items are delivered and in-memory claim state is updated and a YAML save is attempted, but before configured claim commands and broadcast. No claim event is fired for denied or no-op requests. Both events include the player and an immutable metadata snapshot. The post event also includes the claim timestamp.

Events dispatch on the actual claim caller's server context. Their Bukkit asynchronous flag is set from `Bukkit.isPrimaryThread()` at dispatch, so a Folia player-region event may be marked asynchronous even though it runs on an owning region. Listeners should only touch that player's Bukkit state directly; schedule work for other entities through their owners. Other plugins can listen as usual:

```java
@EventHandler
public void onKitClaim(KitClaimedEvent event) {
    String key = event.kit().key();
    Player player = event.player();
    // Award integration-specific progress for key.
}
```

Imports: `org.bukkit.event.EventHandler`, `org.bukkit.entity.Player`, and `me.foesio.foKits.api.event.KitClaimedEvent`.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoKitsApi`

```java
List<KitSnapshot> listKits();
Optional<KitSnapshot> findKit(String);
KitClaimOutcome evaluateClaim(Player, String);
KitClaimOutcome claimKit(Player, String);
KitResetResult resetCooldown(UUID, String);
KitResetBatchOutcome resetAllCooldowns(UUID);
KitResetResult resetOneTimeClaim(UUID, String);
boolean openKitsGui(Player);
```

**Other public contract types:** `KitClaimOutcome`, `KitClaimStatus`, `KitResetBatchOutcome`, `KitResetResult`, `KitSnapshot`.

### Complete Bukkit event index

- `KitClaimPreEvent`.
- `KitClaimedEvent`.

</details>
