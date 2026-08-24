---
name: preference-creation
description: Use when creating or registering a typed preference for a Paper plugin with the Preferences plugin — choosing player vs global scope, picking a PreferenceCodec, setting label and default value, wiring an onChange callback, or debugging why a preference fails to register or show in the /preferences dialog. Triggers include PreferencesService.register, PreferenceBuilder, PreferenceCodec, StorageCodec, playerScoped, global, and /preferences.
---

# Preference Creation (Preferences Plugin)

Create a typed, GUI-editable, persisted preference for your Paper plugin by registering it with
the **Preferences** plugin. You declare the preference once at enable time; Preferences owns the
dialog GUI, validation, caching, permissions, and debounced YAML persistence.

Core principle: **one `register` call in `onEnable` per preference — pick scope, codec, label, and default; the returned `Preference<T>` handle is your only access to it.**

Pins verified 2026-08-24 against this repository (AGENTS.md usage guide, `preferences-api` sources, `preferences-paper` plugin.yml, root build).

## Pinned versions

| Component | Version |
|---|---|
| preferences-api | `dev.mintychochip:preferences-api:0.2.0` |
| paper-api | `io.papermc.paper:paper-api:26.2.build.+` |
| Java toolchain | **25** |

The API coordinate is documented in the Preferences usage guide (AGENTS.md). Resolve locally
with `./gradlew :preferences-api:publishToMavenLocal` from the Preferences repo when testing
against an unpublished build.

## Prerequisites

`plugin.yml`:

```yaml
depend: [Preferences]      # preferences are core — fail fast
# or
softdepend: [Preferences]  # degrade gracefully when absent
```

Use `depend` when your plugin's core behavior needs preferences; `softdepend` when optional.
Preferences loads first in either case.

`build.gradle.kts`:

```kotlin
repositories {
    mavenLocal() // after :preferences-api:publishToMavenLocal
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
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}
```

Load the service once in `onEnable`, before registering:

```java
PreferencesService prefs = Bukkit.getServicesManager().load(PreferencesService.class);
if (prefs == null) {
    getLogger().severe("Preferences service missing!");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

## Create a preference

```java
Preference<Boolean> notifications = prefs.register(this, Boolean.class, b -> b
    .playerScoped("notifications")
    .label(Component.text("Notifications"))
    .description(Component.text("Receive notifications"))
    .codec(PreferenceCodec.booleanBox())
    .defaultValue(true)
    .onChange(c -> getLogger().info("notifications: " + c.oldValue() + " -> " + c.newValue())));
```

Required builder fields — `validate()` throws if any is missing:

- `.playerScoped(name)` — per-player value. `.global(name)` — server-wide. Pick one.
- `.label(Component)` — shown in the dialog.
- `.codec(PreferenceCodec<T>)` — persistence + dialog control.
- `.defaultValue(T)` — used when no stored value exists.

Optional: `.description(Component)` (shown under the label) and `.onChange(Consumer<PreferenceChange>)`.

The returned `Preference<T>` handle is the only way to read/write the value later
(`pref.get(player)`, `pref.set(player, v)`, `pref.getGlobal()`, `pref.setGlobal(v)` — match the
accessor to the declared scope; wrong-scope accessors throw).

## Key naming

Keys are **namespaced per plugin**: `PreferenceKey` = `<plugin-name-lowercase>:<name>`
(e.g. `myplugin:volume`). The namespace derives from `plugin.getName().toLowerCase()`, so the
same `name` in two plugins never collides. Names must match `[a-z0-9_-]+` — no dots (Bukkit YAML
path separator corrupts storage). Duplicate registration of the same key throws
`IllegalStateException`.

## Pick a codec

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

Custom types: implement `StorageCodec<T>` (`parse(String)` / `write(T)`, must round-trip) and
optionally `DialogInputAdapter<T>`, then bundle with `PreferenceCodec.storageOnly(...)` or a
custom `PreferenceCodec`.

## Verify

```bash
./gradlew :preferences-api:build :preferences-common:build :preferences-paper:build :preferences-test:build test
./gradlew :preferences-test:runServer   # local Paper 26.2 smoke server
```

Start the server, run `/preferences` as a player, and confirm your preference appears with the
right control and label. Confirm the data file
`plugins/Preferences/data/<plugin-name-lowercase>.yml` is created.

## Common mistakes (observed in baseline testing)

| Wrong | Right | Why |
|---|---|---|
| `depend` when preferences are optional | `softdepend` | A missing Preferences plugin hard-fails startup for no reason |
| Omitting `label`, `codec`, or `defaultValue` | Set all required builder fields | `validate()` throws `IllegalStateException` at registration |
| Registering outside `onEnable` (lazily from a request path) | Register once in `onEnable` | Preferences must exist before players interact with dialogs |
| Duplicate `name` in the same plugin | Unique names per plugin | Same key throws `IllegalStateException` |
| Dots or uppercase in the preference name | `[a-z0-9_-]+` only | Dots corrupt YAML storage keys; uppercase breaks key conventions |
| Calling `get(player)` on a global preference | `getGlobal()` (and vice versa) | Wrong-scope accessors throw |
| Importing `dev.mintychochip.preferences.common.internal.*` | Import only `dev.mintychochip.preferences.api.*` | Internal classes are not part of the stable API |
| Baking a namespaced key into dialog input | Vanilla `[a-zA-Z0-9_]` input keys | Namespaced keys break dialog rendering |
| Guessing the namespace for keys/filtering | `<plugin-name-lowercase>:<name>` | Derived from `plugin.getName().toLowerCase()` |
