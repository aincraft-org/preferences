# Preferences — Usage Guide

Paper **1.21.7+** plugin: other plugins declare typed preferences; this plugin provides
the dialog GUI, state management, and YAML persistence for them. **v0.2.0.**

This file is the usage guide for AGENTS.md.

- [Server admins](#server-admins)
- [Plugin developers](#plugin-developers)
  - [1. Declare the dependency](#1-declare-the-dependency)
  - [2. Load the service](#2-load-the-service)
  - [3. Register preferences](#3-register-preferences)
  - [4. Built-in codecs and dialog controls](#4-built-in-codecs-and-dialog-controls)
  - [5. Programmatic access](#5-programmatic-access)
  - [6. React to changes](#6-react-to-changes)
  - [7. Don't do this](#7-dont-do-this)
- [Configuration](#configuration)
- [Stored data](#stored-data)
- [Building](#building)

---

## Server admins

### Install

1. Drop `preferences-0.2.0.jar` into `plugins/`.
2. Restart the server (or `/reload`).
3. Done — hooking plugins register their preferences at their own enable time.

### Commands

| Command | Permission | Effect |
|---|---|---|
| `/preferences` (alias `/prefs`) | `preferences.use` (default: everyone) | Open your own preference dialog |
| `/preferences global` | `preferences.manage` (default: op) | View/edit server-global preferences |

Dialogs are Paper 1.21.7 native dialogs. Non-players get a message that preferences
are edited in-game.

### Permissions

| Node | Default | Grants |
|---|---|---|
| `preferences.use` | `true` | Open `/preferences`, edit own player-scoped preferences |
| `preferences.manage` | `op` | Open `/preferences global`, edit global preferences |

Player-scoped preferences are only editable by their owner — no permission grants
cross-player edits in v1.

### Configuration

`plugins/Preferences/config.yml`:

```yaml
storage:
  # Trailing window before dirty data files are written asynchronously.
  flush-seconds: 5
gui:
  # Max entries per list dialog before pagination.
  page-size: 20
```

Values are read at enable time; edit then restart/reload.

---

## Plugin developers

### 1. Declare the dependency

In your `plugin.yml`:

```yaml
depend: [Preferences]      # fail fast if missing
# or
softdepend: [Preferences]  # degrade gracefully
```

Add the API dependency. From Maven Central / GitHub Packages:

```kotlin
// build.gradle.kts
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        url = uri("https://maven.pkg.github.com/aincraft-org/preferences")
        credentials {
            username = project.findProperty("gpr.user") as String?
                ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String?
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("dev.mintychochip:preferences-api:0.2.0")
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
}
```

### 2. Load the service

In your `onEnable()`:

```java
PreferencesService prefs = Bukkit.getServicesManager().load(PreferencesService.class);
if (prefs == null) {
    getLogger().severe("Preferences service missing!");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

### 3. Register preferences

```java
Preference<Boolean> notifications = prefs.register(this, Boolean.class, b -> b
    .playerScoped("notifications")
    .label(Component.text("Notifications"))
    .description(Component.text("Receive notifications"))
    .codec(PreferenceCodec.booleanBox())
    .defaultValue(true)
    .onChange(c -> getLogger().info("notifications: " + c.oldValue() + " -> " + c.newValue())));
```

- `.playerScoped(name)` — per-player value. `.global(name)` — server-wide.
- `label`, `codec`, `defaultValue` are required; `description` and `onChange` optional; `validate()` throws if incomplete.
- Keys are **namespaced per plugin** (`<plugin-name>:<name>`); duplicate registration throws `IllegalStateException`.
- Values persist as `StorageCodec.write(T)` strings in `plugins/Preferences/data/<plugin-name-lowercase>.yml`.

### 4. Built-in codecs and dialog controls

`PreferenceCodec` bundles a required `StorageCodec<T>` (type ↔ stored string) and an
optional `DialogInputAdapter<T>` (type ↔ dialog input). No adapter → persisted + programmatically
settable but shown read-only in the GUI.

| Factory | Type | Dialog control |
|---|---|---|
| `PreferenceCodec.string(maxLength)` | `String` | Text field |
| `PreferenceCodec.booleanBox()` | `Boolean` | Checkbox |
| `PreferenceCodec.integerSlider(min, max, step)` | `Integer` | Slider |
| `PreferenceCodec.longSlider(min, max, step)` | `Long` | Slider |
| `PreferenceCodec.floatSlider(min, max, step)` | `Float` | Slider |
| `PreferenceCodec.doubleSlider(min, max, step)` | `Double` | Slider |
| `PreferenceCodec.enumerated(EnumClass.class, e -> Component.text(...))` | enum | Option picker |
| `PreferenceCodec.storageOnly(storageCodec)` | any | none (read-only in GUI) |

Custom types: implement `StorageCodec<T>` (and optionally `DialogInputAdapter<T>`), then
`PreferenceCodec.storageOnly(myStorage)` or bundle both:

```java
public interface StorageCodec<T> {
    T parse(String stored);              // throws IllegalArgumentException on bad input
    String write(T value);               // must round-trip with parse()
}
```

### 5. Programmatic access

`Preference<T> drawDistance = ...`:

```java
int val = drawDistance.get(player);      // player-scoped
drawDistance.set(player, 12);            // validates, fires event, caches, queues async persist
drawDistance.reset(player);

// global-scoped
T g = pref.getGlobal();
pref.setGlobal(value);                   // editor == null in the event
pref.setGlobal(adminPlayer, value);      // attributes the change to an admin
pref.resetGlobal();
```

Wrong-scope accessors throw. `set` fires a cancellable `PreferenceChangeEvent` before
persistence; cancellation aborts the change. Values persist async — allow the flush
window (default 5s) before asserting hard on disk.

### 6. React to changes

Bukkit event:

```java
@EventHandler
public void onPreferenceChange(PreferenceChangeEvent e) {
    PreferenceKey key = e.key();         // namespace:name, e.g. "myplugin:volume"
    String oldV = e.oldValue();          // stored-string form
    String newV = e.newValue();
    e.setCancelled(true);                // block the change entirely
}
```

Or the per-preference callback: `b.onChange(c -> ...)` with `PreferenceChange(key, oldValue, newValue)`.

### 7. Don't do this

- Never import `dev.mintychochip.preferences.internal.*` — it is not part of the stable API.
- Don't hand-edit `plugins/Preferences/data/*.yml` while the server runs — a later flush
  overwrites it. Stop the server first.
- Don't bake the dialog `inputKey` — dialog input keys follow vanilla
  `[a-zA-Z0-9_]`; namespaced keys break dialog rendering.
- In v1 there is no per-player override of global preferences, no chat input fallback,
  and no cross-player edits.

---

## Configuration

See [Server admins → Configuration](#configuration). Config is read at enable time.

## Stored data

`plugins/Preferences/data/<namespace>.yml`, one file per hooking plugin:

```yaml
global:
  announce_logins: 'true'
players:
  <player-uuid>:
    notifications: 'true'
    volume: '70'
```

- Globals load at enable; player values load lazily on first access and are cached.
- Corrupt values on load → warn + fall back to default; entry rewritten on next flush.
- Unregistering a hooking plugin never deletes data files — values survive re-enable.
- A hard crash can lose at most one flush window of changes (5s default).

## Building

```bash
./gradlew :api:build :common:build :paper:build :test:build test   # CI-equivalent
./gradlew :test:runServer                                          # local Paper 1.21.7 smoke server
./scripts/verify-maven-publish.sh                                  # prove the publish path
```

Local publish of the API for hooking plugins: `./gradlew :api:publishToMavenLocal`.
