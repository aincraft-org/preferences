# Preferences

Paper **1.21.7+** plugin that lets other plugins declare typed preferences and get dialog-based GUI, state management, and YAML persistence for free.

## Modules

| Module | Artifact | Role |
|--------|----------|------|
| `api` | `dev.mintychochip:preferences-api` | Public registration surface for hooking plugins |
| `common` | (internal) | Registry, storage, flusher, sessions |
| `paper` | `dev.mintychochip:preferences` | Shippable Preferences plugin jar (embeds api + common) |
| `test` | — | Fixture plugin + jpenilla `runServer` (CI must package this) |

## Build

```bash
./gradlew :api:build :common:build :paper:build :test:build test
# or
./gradlew ci
```

Local integration server (loads Preferences + PreferencesTest):

```bash
./gradlew :test:runServer
```

## Maven / Gradle (hooking plugins)

Publish the API to your machine (or use GitHub Packages — see below):

```bash
./gradlew :api:publishToMavenLocal
# optional plugin jar:
./gradlew :paper:publishToMavenLocal
```

### Coordinate

```
dev.mintychochip:preferences-api:0.2.0
```

### Gradle (consumer)

```kotlin
repositories {
    mavenLocal() // after publishToMavenLocal
    // GitHub Packages (auth required even for public packages):
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

### Maven (consumer)

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/aincraft-org/preferences</url>
  </repository>
</repositories>

<dependency>
  <groupId>dev.mintychochip</groupId>
  <artifactId>preferences-api</artifactId>
  <version>0.2.0</version>
  <scope>provided</scope>
</dependency>
```

For GitHub Packages, put a PAT with `read:packages` in `~/.m2/settings.xml` under server id `github` (or export `GITHUB_TOKEN` for Gradle).

### Runtime

1. Install the Preferences plugin jar (`paper` module output) on the server.
2. Declare `depend: [Preferences]` (or soft-depend) in your `plugin.yml`.
3. Load the service and register preferences:

```java
PreferencesService prefs = Bukkit.getServicesManager().load(PreferencesService.class);
Preference<Boolean> notifications = prefs.register(this, Boolean.class, b -> b
    .playerScoped("notifications")
    .label(Component.text("Notifications"))
    .codec(PreferenceCodec.booleanBox())
    .defaultValue(true));
```

Import only `dev.mintychochip.preferences.api.*` — never `dev.mintychochip.preferences.internal`.

## Publishing (maintainers)

```bash
# Local Maven repo (always available)
./gradlew :api:publishToMavenLocal :paper:publishToMavenLocal

# GitHub Packages (needs write:packages)
export GITHUB_ACTOR=mintychochip
export GITHUB_TOKEN=ghp_...   # classic PAT with write:packages, read:packages, repo
./gradlew :api:publish :paper:publish
```

Verify the publish path without GitHub credentials:

```bash
./scripts/verify-maven-publish.sh
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) on `master` / PRs:

- Builds `:api`, `:common`, `:paper`, and **`:test`**
- Runs unit tests
- Runs `scripts/verify-maven-publish.sh` (publishToMavenLocal + isolated consumer)

## License

See repository owner terms. All rights reserved unless stated otherwise.
