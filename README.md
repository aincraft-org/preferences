# Preferences

![CI](https://github.com/aincraft-org/preferences/actions/workflows/ci.yml/badge.svg)

Paper **1.21.7+** plugin that lets other plugins declare typed preferences and get a native dialog GUI, state management, and YAML persistence for free.

## What it is

- **Hooking plugins** register typed preferences (booleans, numbers, enums, text, etc.) with a small builder.
- **Players** edit their own values with `/preferences`; admins edit server-wide values with `/preferences global`.
- **The Preferences plugin** owns the dialogs, validation, caching, permission checks, and debounced YAML persistence.

## Requirements

- Paper 1.21.7+
- Java 21

## Install

1. Download `paper/build/libs/preferences-0.2.0.jar` (or publish it locally).
2. Drop it in your server's `plugins/` directory.
3. Restart; hooking plugins will register their preferences at enable time.

For full admin docs (commands, permissions, configuration) see [`AGENTS.md`](AGENTS.md).

## Quickstart (plugin developers)

### 1. Add the API

```kotlin
repositories {
    mavenLocal() // after :api:publishToMavenLocal
    maven {
        url = uri("https://maven.pkg.github.com/aincraft-org/preferences")
        credentials {
            username = project.findProperty("gpr.user") as String?
                ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String?
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("dev.mintychochip:preferences-api:0.2.0")
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
}
```

Maven:

```xml
<dependency>
  <groupId>dev.mintychochip</groupId>
  <artifactId>preferences-api</artifactId>
  <version>0.2.0</version>
  <scope>provided</scope>
</dependency>
```

### 2. Register a preference

```java
PreferencesService prefs = Bukkit.getServicesManager()
    .load(PreferencesService.class);

Preference<Boolean> notifications = prefs.register(this, Boolean.class, b -> b
    .playerScoped("notifications")
    .label(Component.text("Notifications"))
    .description(Component.text("Receive notifications"))
    .codec(PreferenceCodec.booleanBox())
    .defaultValue(true));

boolean enabled = notifications.get(player);
notifications.set(player, false);
```

Import only `dev.mintychochip.preferences.api.*` — never `dev.mintychochip.preferences.internal.*`.

## Built-in types

| Factory | Java type | Dialog control |
|---|---|---|
| `PreferenceCodec.string(maxLength)` | `String` | Text field |
| `PreferenceCodec.booleanBox()` | `Boolean` | Checkbox |
| `PreferenceCodec.integerSlider(min, max, step)` | `Integer` | Slider |
| `PreferenceCodec.longSlider(min, max, step)` | `Long` | Slider |
| `PreferenceCodec.floatSlider(min, max, step)` | `Float` | Slider |
| `PreferenceCodec.doubleSlider(min, max, step)` | `Double` | Slider |
| `PreferenceCodec.enumerated(...)` | enum | Option picker |

Custom types: implement `StorageCodec<T>` (and optionally `DialogInputAdapter<T>`).

## Scopes & events

- **Player-scoped**: one value per player, editable by that player via `/preferences`.
- **Global**: one value per server, editable by players with `preferences.manage` (default: op) via `/preferences global`.
- `PreferenceChangeEvent` is fired before persistence and can be cancelled.

## Configuration

`plugins/Preferences/config.yml`:

```yaml
storage:
  flush-seconds: 5

gui:
  page-size: 20
```

## Build & test

```bash
./gradlew :api:build :common:build :paper:build :test:build test
./gradlew :test:runServer   # local integration server
```

Prove the full publish path (Maven Local + real consumer resolve):

```bash
./scripts/verify-maven-publish.sh
```

## Modules

| Module | Artifact | Role |
|---|---|---|
| `api` | `dev.mintychochip:preferences-api` | Public registration surface for hooking plugins |
| `common` | (internal) | Registry, storage, flusher, sessions |
| `paper` | `dev.mintychochip:preferences` | Shippable Preferences plugin jar (embeds api + common) |
| `test` | — | Fixture plugin + jpenilla `runServer` |

## Publishing (maintainers)

```bash
# Local Maven repo
./gradlew :api:publishToMavenLocal :paper:publishToMavenLocal

# GitHub Packages (needs write:packages)
export GITHUB_ACTOR=mintychochip
export GITHUB_TOKEN=ghp_...   # PAT with write:packages, read:packages, repo
./gradlew :api:publish :paper:publish
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) on `master` / PRs:

- Builds `:api`, `:common`, `:paper`, and **`:test`**
- Runs unit tests
- Runs `scripts/verify-maven-publish.sh`

## License

See repository owner terms. All rights reserved unless stated otherwise.
