#!/usr/bin/env bash
# Prove the real publish path: publish API to mavenLocal, then resolve that
# coordinate from a separate consumer project (not project(":api")) and exercise
# a real public type from the published jar.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

CONSUMER="$(mktemp -d "${TMPDIR:-/tmp}/preferences-consumer.XXXXXX")"
cleanup() { rm -rf "$CONSUMER"; }
trap cleanup EXIT

echo "==> Publishing :api to mavenLocal"
./gradlew --no-daemon :api:publishToMavenLocal

# Locate the published jar under the user's local Maven repo.
GROUP_PATH="dev/jlo/preferences-api"
VERSION="$(grep -E '^\s*version\s*=' build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
if [[ -z "$VERSION" ]]; then
  VERSION="0.1.0-SNAPSHOT"
fi
M2="${HOME}/.m2/repository/${GROUP_PATH}/${VERSION}"
JAR="${M2}/preferences-api-${VERSION}.jar"
POM="${M2}/preferences-api-${VERSION}.pom"

echo "==> Published artifacts"
ls -la "$M2" || {
  echo "ERROR: expected published files under $M2" >&2
  exit 1
}
test -f "$JAR"
test -f "$POM"

echo "==> Building isolated consumer against mavenLocal only"
mkdir -p "$CONSUMER/src/main/java/consume"
cat >"$CONSUMER/settings.gradle.kts" <<'EOF'
rootProject.name = "preferences-api-consumer"
EOF

cat >"$CONSUMER/build.gradle.kts" <<EOF
plugins { java }

repositories {
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    // Real published coordinate — NOT project(":api")
    implementation("dev.jlo:preferences-api:${VERSION}")
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
}

java {
    toolchain {
        // Prefer installed JDK; release 21 for bytecode.
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.register<JavaExec>("runConsumer") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("consume.Main")
}
EOF

cat >"$CONSUMER/src/main/java/consume/Main.java" <<'EOF'
package consume;

import dev.jlo.preferences.api.PreferenceKey;
import dev.jlo.preferences.api.codec.BuiltInCodecs;
import dev.jlo.preferences.api.codec.StorageCodec;

/**
 * Consumer of the *published* preferences-api jar. If this compiles and runs,
 * the Maven coordinate is real and the public API is loadable off that jar.
 */
public final class Main {
    public static void main(String[] args) {
        PreferenceKey key = new PreferenceKey("consumer", "demo_flag");
        if (!"consumer".equals(key.namespace()) || !"demo_flag".equals(key.name())) {
            throw new AssertionError("PreferenceKey broken: " + key);
        }
        if (!"consumer:demo_flag".equals(key.asString())) {
            throw new AssertionError("PreferenceKey.asString unexpected: " + key.asString());
        }

        StorageCodec<Boolean> codec = BuiltInCodecs.BOOLEAN;
        Boolean parsed = codec.parse("true");
        if (!Boolean.TRUE.equals(parsed)) {
            throw new AssertionError("BuiltInCodecs.BOOLEAN.parse failed: " + parsed);
        }
        String written = codec.write(false);
        if (!"false".equals(written)) {
            throw new AssertionError("BuiltInCodecs.BOOLEAN.write failed: " + written);
        }

        // Prove class came from the published jar path when possible.
        String location = PreferenceKey.class.getProtectionDomain()
            .getCodeSource().getLocation().toString();
        System.out.println("OK consumer resolved PreferenceKey from: " + location);
        System.out.println("OK BuiltInCodecs.BOOLEAN round-trip true/false");
        if (!location.contains("preferences-api")) {
            System.err.println("WARN: code source does not look like preferences-api jar: " + location);
            // Still fail hard if it looks like a project build dir instead of m2.
            if (location.contains("/api/build/") || location.contains("project")) {
                throw new AssertionError("loaded from project output, not published jar: " + location);
            }
        }
    }
}
EOF

# Use the same Gradle wrapper from the main project so we don't depend on a system install.
(cd "$CONSUMER" && "$ROOT/gradlew" --no-daemon -p "$CONSUMER" runConsumer --stacktrace)

echo "==> verify-maven-publish: SUCCESS (published + consumed dev.jlo:preferences-api:${VERSION})"
