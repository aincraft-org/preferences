# Preferences Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Paper 1.21.7+ plugin that lets other plugins declare typed preferences and get dialog-based GUI, state management, and YAML persistence for free.

**Architecture:** Hooking plugins load a `PreferencesService` from the Bukkit services manager and register preferences with a required `StorageCodec<T>` (type ↔ stored string) and optional `DialogInputAdapter<T>` (type ↔ dialog input). This plugin owns navigation dialogs, session-validated click routing, permissions, and debounced async YAML persistence.

**Tech Stack:** Java 21 (compile `--release 21` on local JDK 25), Gradle 9.6.1 (wrapper), Paper API `1.21.7-R0.1-SNAPSHOT`, SnakeYAML, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-04-preferences-plugin-design.md`

## Global Constraints

- Paper API floor: `1.21.7-R0.1-SNAPSHOT`; Dialog API is `@Experimental` — compile against it, do not suppress warnings project-wide.
- Java: `options.release = 21`. Do NOT use Gradle toolchains (they download JDKs); compile with the installed JDK.
- No main-thread YAML serialization or file I/O on the change path. Writes go through the debounced async flusher; `onDisable` flushes synchronously.
- Vanilla dialog input `key` values must match `[a-zA-Z0-9_]` (template-argument rule) — never pass namespaced keys as dialog input keys.
- `PlayerCustomClickEvent` also fires for plain chat click events: every click is validated against a live session before acting. No session → silently ignore.
- Packages: public API in `dev.jlo.preferences.api` (+ `.api.codec`, `.api.event`); everything else in `dev.jlo.preferences.internal.*`. Hooking plugins import only `dev.jlo.preferences.api`.
- Storage values are always `StorageCodec.write(T)` strings; files live at `plugins/Preferences/data/<namespace>.yml`.
- Commit style: `feat:`, `test:`, `chore:`, `docs:` prefixes; one commit per task unless noted.
- Never run project-wide verification inside a task except that task's own `gradle test` filter.

## File Structure

```
settings.gradle.kts                 root + :demo
build.gradle.kts                    plugin build (paper-api compileOnly, JUnit 5)
gradle/wrapper/...                  wrapper pinned to 9.6.1
src/main/resources/plugin.yml       name, main, api-version 1.21, permissions, command
src/main/java/dev/jlo/preferences/
  PreferencesPlugin.java            JavaPlugin wiring (Task 7)
  api/
    PreferencesService.java         registration + query surface
    Preference.java                 typed handle
    PreferenceBuilder.java          fluent registration builder
    PreferenceScope.java            PLAYER | GLOBAL
    PreferenceKey.java              record(namespace, name) — public, exposed via handles/events
    PreferenceChange.java           record for per-preference change callback
    codec/StorageCodec.java         type <-> stored string (required)
    codec/DialogInputAdapter.java   type <-> dialog input (optional)
    codec/PreferenceCodec.java      facade record + built-in factories
    codec/BuiltInCodecs.java        string/bool/int/long/float/double/enum
    codec/BuiltInAdapters.java      checkbox/slider/option-picker/text adapters
    event/PreferenceChangeEvent.java
  internal/
    RegisteredPreference.java       Preference<T> implementation
    PreferenceRegistry.java         key -> registration map (main thread)
    ValueStore.java                 internal storage SPI boundary
    YamlValueStore.java             Bukkit YamlConfiguration load/snapshot
    DebouncedFlusher.java           dirty tracking + async/sync flush
    FlushScheduler.java             scheduler abstraction (prod: Bukkit, test: manual)
    session/DialogSessionManager.java   single-slot sessions per player
    session/DialogSession.java      kind + page + target key
    dialog/DialogFactories.java     ALL Paper Dialog construction lives here (probe fixes land here)
    dialog/DialogScreens.java       list/edit screen composition
    dialog/ClickRouter.java         PlayerCustomClickEvent handler
    command/PreferencesCommand.java /preferences + /preferences global
demo/
  build.gradle.kts                  demo hooking plugin
  src/main/resources/plugin.yml
  src/main/java/dev/jlo/preferences/demo/DemoPlugin.java
  src/test/...                      (none — demo is smoke-test only)
src/test/java/dev/jlo/preferences/  unit tests (codec, store, flusher, sessions)
```

---

### Task 1: Project skeleton + Paper Dialog API probe

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, wrapper files, `.gitignore`
- Create: `src/main/java/dev/jlo/preferences/internal/dialog/DialogFactories.java` (probe)
- Create: `src/main/resources/plugin.yml` (minimal, final fields filled in Task 7)

**Interfaces:**
- Produces: a compiling Gradle project; `DialogFactories` static methods `multiAction(...)`, `notice(...)`, `editDialog(...)`, `optionEntry(...)` — all later dialog code calls ONLY this class.

- [ ] **Step 1: Write the Gradle skeleton**

`settings.gradle.kts`:
```kotlin
rootProject.name = "preferences"
include("demo")
```

`build.gradle.kts`:
```kotlin
plugins { `java-library` }

group = "dev.jlo"
version = "0.1.0-SNAPSHOT"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { withSourcesJar() }

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.test { useJUnitPlatform() }
```

`demo/build.gradle.kts`:
```kotlin
plugins { `java` }

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly(rootProject)
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }
```

`.gitignore`:
```
.gradle/
build/
run/
*.iml
.idea/
```

`src/main/resources/plugin.yml`:
```yaml
name: Preferences
version: '0.1.0'
main: dev.jlo.preferences.PreferencesPlugin
api-version: '1.21'
description: Typed preferences with dialog GUI, owned by this plugin.
```

- [ ] **Step 2: Generate the wrapper with the cached 9.6.1 distribution**

Run:
```bash
cd /home/jlo/dev/preferences
DIST=$(echo ~/.gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/bin/gradle)
"$DIST" wrapper --gradle-version 9.6.1
./gradlew --version
```
Expected: wrapper files created; `Gradle 9.6.1` printed.

- [ ] **Step 3: Write the probe class**

`src/main/java/dev/jlo/preferences/internal/dialog/DialogFactories.java`:
```java
package dev.jlo.preferences.internal.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * ALL Paper Dialog API construction for this plugin lives here.
 * If a Paper API signature differs from this file, fix it HERE only.
 */
public final class DialogFactories {

    public static final Key KEY_SAVE = Key.key("preferences", "save");
    public static final Key KEY_CANCEL = Key.key("preferences", "cancel");
    public static final Key KEY_LIST_PREV = Key.key("preferences", "list_prev");
    public static final Key KEY_LIST_NEXT = Key.key("preferences", "list_next");

    public static Key editKey(int index) {
        return Key.key("preferences", "edit/" + index);
    }

    /** Scrollable button list (preferences list screens). */
    public static Dialog multiAction(Component title, List<ActionButton> actions, ActionButton exit) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title).build())
            .type(DialogType.multiAction(actions, exit, 1)));
    }

    /** Notice dialog with a single action button (read-only preference view). */
    public static Dialog notice(Component title, List<io.papermc.paper.registry.data.dialog.body.DialogBody> body) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title).body(body).build())
            .type(DialogType.notice()));
    }

    /** Edit dialog: one input + Save (custom click) + Cancel. */
    public static Dialog editDialog(Component title, List<Component> description, DialogInput input) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title)
                .body(description.stream()
                    .map(io.papermc.paper.registry.data.dialog.body.DialogBody::plainMessage)
                    .toList())
                .inputs(List.of(input))
                .build())
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Save"))
                    .action(DialogAction.customClick(KEY_SAVE, null))
                    .build(),
                ActionButton.builder(Component.text("Cancel"))
                    .action(DialogAction.customClick(KEY_CANCEL, null))
                    .build())));
    }

    /** PROBE: SingleOptionDialogInput.OptionEntry construction is UNVERIFIED.
     *  Expected shape: OptionEntry.of(id, display, initial).
     *  If compilation fails, read the compile error for the actual factory and fix here. */
    public static SingleOptionDialogInput.OptionEntry optionEntry(String id, Component display, boolean initial) {
        return SingleOptionDialogInput.OptionEntry.of(id, display, initial);
    }

    private DialogFactories() {}
}
```

NOTE: All Paper dialog calls above (`Dialog.create` builder chain, `DialogBase.builder`, `DialogType.multiAction(actions, exit, columns)`, `DialogType.confirmation(yes, no)`, `DialogType.notice()`, `ActionButton.builder().action()`, `DialogAction.customClick(Key, null)`) are verified against Paper's official Dialog API docs and 1.21.7 javadoc. The ONLY unverified call is `OptionEntry.of(...)` below — that is the probe.

- [ ] **Step 4: Compile the probe**

Run: `./gradlew compileJava --console=plain`
Expected: BUILD SUCCESSFUL. If compilation fails, the error names the actual signature (most likely `OptionEntry.of` arity or `Dialog.create` builder shape — `Dialog.create(b -> b.empty().base(...).type(...))` is verified against Paper's official docs). Fix `DialogFactories` until green; this is the point of the probe.

- [ ] **Step 5: Sanity-check headless construction (optional, informative)**

Add a scratch JUnit test that calls `DialogFactories.multiAction(...)` with an empty list and assert it returns non-null. If it throws because Paper internals require a server, DELETE the test and record in the commit message: "dialog construction requires live server; covered by smoke test". Either outcome is acceptable — do not fight it.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "chore: gradle skeleton + dialog API probe"
```

---

### Task 2: Codec layer + built-ins (TDD)

**Files:**
- Create: `api/codec/StorageCodec.java`, `api/codec/DialogInputAdapter.java`, `api/codec/PreferenceCodec.java`, `api/codec/BuiltInCodecs.java`, `api/codec/BuiltInAdapters.java`, `api/PreferenceScope.java`
- Test: `src/test/java/dev/jlo/preferences/codec/BuiltInCodecsTest.java`, `src/test/java/dev/jlo/preferences/codec/BuiltInAdaptersTest.java`

**Interfaces:**
- Produces (used by Tasks 3–6):
  - `interface StorageCodec<T> { T parse(String) throws IllegalArgumentException; String write(T); }`
  - `interface DialogInputAdapter<T> { DialogInput buildInput(String inputKey, Component label, T current); @Nullable T parseResponse(DialogResponseView view, String inputKey); }`
  - `record PreferenceCodec<T>(StorageCodec<T> storage, @Nullable DialogInputAdapter<T> input)` with static factories `string(int maxLength)`, `booleanBox()`, `integerSlider(int,int,int)`, `longSlider(long,long,long)`, `floatSlider(float,float,float)`, `doubleSlider(double,double,double)`, `enumerated(Class<E>, Function<E,Component>)`, `storageOnly(StorageCodec<T>)`
  - `enum PreferenceScope { PLAYER, GLOBAL }`

- [ ] **Step 1: Write failing codec tests**

`src/test/java/dev/jlo/preferences/codec/BuiltInCodecsTest.java`:
```java
package dev.jlo.preferences.codec;

import static org.junit.jupiter.api.Assertions.*;

import dev.jlo.preferences.api.codec.BuiltInCodecs;
import dev.jlo.preferences.api.codec.StorageCodec;
import org.junit.jupiter.api.Test;

class BuiltInCodecsTest {

    @Test void booleanRoundTrip() {
        StorageCodec<Boolean> c = BuiltInCodecs.BOOLEAN;
        assertEquals(Boolean.TRUE, c.parse("true"));
        assertEquals(Boolean.FALSE, c.parse("false"));
        assertEquals("true", c.write(true));
    }

    @Test void booleanRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> BuiltInCodecs.BOOLEAN.parse("yes"));
    }

    @Test void integerRoundTrip() {
        assertEquals(Integer.valueOf(42), BuiltInCodecs.INTEGER.parse("42"));
        assertEquals("-7", BuiltInCodecs.INTEGER.write(-7));
        assertThrows(IllegalArgumentException.class, () -> BuiltInCodecs.INTEGER.parse("1.5"));
    }

    @Test void longRoundTrip() {
        assertEquals(Long.MAX_VALUE, BuiltInCodecs.LONG.parse(String.valueOf(Long.MAX_VALUE)));
    }

    @Test void floatRoundTrip() {
        assertEquals(Float.valueOf(1.5f), BuiltInCodecs.FLOAT.parse("1.5"));
        assertEquals("2.25", BuiltInCodecs.FLOAT.write(2.25f));
    }

    @Test void doubleRoundTrip() {
        assertEquals(Double.valueOf(0.125), BuiltInCodecs.DOUBLE.parse("0.125"));
    }

    @Test void stringRoundTrip() {
        assertEquals("hello world", BuiltInCodecs.STRING.parse("hello world"));
        assertEquals("", BuiltInCodecs.STRING.write(""));
    }

    enum Mode { FAST, SLOW }

    @Test void enumRoundTrip() {
        StorageCodec<Mode> c = BuiltInCodecs.enumerated(Mode.class);
        assertEquals(Mode.SLOW, c.parse("SLOW"));
        assertEquals("FAST", c.write(Mode.FAST));
        assertThrows(IllegalArgumentException.class, () -> c.parse("medium"));
    }
}
```

- [ ] **Step 2: Run tests, expect failure**

Run: `./gradlew test --tests 'dev.jlo.preferences.codec.BuiltInCodecsTest' --console=plain`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement codec layer**

`api/PreferenceScope.java`:
```java
package dev.jlo.preferences.api;

public enum PreferenceScope { PLAYER, GLOBAL }
```

`api/codec/StorageCodec.java`:
```java
package dev.jlo.preferences.api.codec;

/** Converts between a preference's typed value and its stored string form. */
public interface StorageCodec<T> {
    /** Parse a stored string. Throws IllegalArgumentException on invalid input. */
    T parse(String stored);
    /** Serialize to the stored string form. Must round-trip with parse(). */
    String write(T value);
}
```

`api/codec/DialogInputAdapter.java`:
```java
package dev.jlo.preferences.api.codec;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/** Converts between a preference's typed value and a native dialog input. */
public interface DialogInputAdapter<T> {
    /** Build the dialog input control, pre-filled with the current value. */
    DialogInput buildInput(String inputKey, Component label, T current);
    /** Read the typed value back from a dialog response; null if absent/invalid. */
    @Nullable T parseResponse(DialogResponseView response, String inputKey);
}
```

`api/codec/PreferenceCodec.java`:
```java
package dev.jlo.preferences.api.codec;

import dev.jlo.preferences.api.PreferenceScope;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/** Bundles the storage codec (required) and dialog adapter (optional). */
public record PreferenceCodec<T>(StorageCodec<T> storage, @Nullable DialogInputAdapter<T> input) {

    public static PreferenceCodec<String> string(int maxLength) {
        return new PreferenceCodec<>(BuiltInCodecs.STRING, BuiltInAdapters.text(maxLength));
    }

    public static PreferenceCodec<Boolean> booleanBox() {
        return new PreferenceCodec<>(BuiltInCodecs.BOOLEAN, BuiltInAdapters.checkbox());
    }

    public static PreferenceCodec<Integer> integerSlider(int min, int max, int step) {
        return new PreferenceCodec<>(BuiltInCodecs.INTEGER,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> Math.round(f)));
    }

    public static PreferenceCodec<Long> longSlider(long min, long max, long step) {
        return new PreferenceCodec<>(BuiltInCodecs.LONG,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> Math.round(f)));
    }

    public static PreferenceCodec<Float> floatSlider(float min, float max, float step) {
        return new PreferenceCodec<>(BuiltInCodecs.FLOAT,
            BuiltInAdapters.slider(min, max, step, Function.identity(), Function.identity()));
    }

    public static PreferenceCodec<Double> doubleSlider(double min, double max, double step) {
        return new PreferenceCodec<>(BuiltInCodecs.DOUBLE,
            BuiltInAdapters.slider(min, max, step, v -> v.floatValue(), f -> (double) f));
    }

    public static <E extends Enum<E>> PreferenceCodec<E> enumerated(
            Class<E> type, Function<E, Component> display) {
        return new PreferenceCodec<>(BuiltInCodecs.enumerated(type), BuiltInAdapters.optionPicker(type, display));
    }

    /** Persistable but not dialog-editable (read-only in GUI). */
    public static <T> PreferenceCodec<T> storageOnly(StorageCodec<T> storage) {
        return new PreferenceCodec<>(storage, null);
    }
}
```

`api/codec/BuiltInCodecs.java`:
```java
package dev.jlo.preferences.api.codec;

public final class BuiltInCodecs {

    public static final StorageCodec<String> STRING = new StorageCodec<>() {
        @Override public String parse(String stored) { return stored; }
        @Override public String write(String value) { return value; }
    };

    public static final StorageCodec<Boolean> BOOLEAN = new StorageCodec<>() {
        @Override public Boolean parse(String stored) {
            if ("true".equals(stored)) return Boolean.TRUE;
            if ("false".equals(stored)) return Boolean.FALSE;
            throw new IllegalArgumentException("not a boolean: " + stored);
        }
        @Override public String write(Boolean value) { return value.toString(); }
    };

    public static final StorageCodec<Integer> INTEGER = parsing(Integer::parseInt, String::valueOf);
    public static final StorageCodec<Long> LONG = parsing(Long::parseLong, String::valueOf);
    public static final StorageCodec<Float> FLOAT = parsing(Float::parseFloat, String::valueOf);
    public static final StorageCodec<Double> DOUBLE = parsing(Double::parseDouble, String::valueOf);

    public static <E extends Enum<E>> StorageCodec<E> enumerated(Class<E> type) {
        return new StorageCodec<>() {
            @Override public E parse(String stored) {
                try { return Enum.valueOf(type, stored); }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("no " + type.getSimpleName() + " constant: " + stored);
                }
            }
            @Override public String write(E value) { return value.name(); }
        };
    }

    private static <T> StorageCodec<T> parsing(java.util.function.Function<String, T> parse,
                                               java.util.function.Function<T, String> write) {
        return new StorageCodec<>() {
            @Override public T parse(String stored) {
                try { return parse.apply(stored); }
                catch (RuntimeException e) { throw new IllegalArgumentException("invalid value: " + stored, e); }
            }
            @Override public String write(T value) { return write.apply(value); }
        };
    }

    private BuiltInCodecs() {}
}
```

`api/codec/BuiltInAdapters.java`:
```java
package dev.jlo.preferences.api.codec;

import dev.jlo.preferences.internal.dialog.DialogFactories;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

public final class BuiltInAdapters {

    public static DialogInputAdapter<Boolean> checkbox() {
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, Boolean current) {
                return DialogInput.bool(key, label, current, "true", "false");
            }
            @Override public @Nullable Boolean parseResponse(DialogResponseView r, String key) {
                return r.getBoolean(key);
            }
        };
    }

    public static <N extends Number> DialogInputAdapter<N> slider(
            float min, float max, float step,
            Function<N, Float> toFloat, Function<Float, N> fromFloat) {
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, N current) {
                return DialogInput.numberRange(key, 200, label, "options.generic_value", min, max, toFloat.apply(current), step);
            }
            @Override public @Nullable N parseResponse(DialogResponseView r, String key) {
                Float f = r.getFloat(key);
                return f == null ? null : fromFloat.apply(f);
            }
        };
    }

    public static <E extends Enum<E>> DialogInputAdapter<E> optionPicker(
            Class<E> type, Function<E, Component> display) {
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, E current) {
                List<io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry> entries = Arrays.stream(type.getEnumConstants())
                    .map(e -> DialogFactories.optionEntry(e.name(), display.apply(e), e == current))
                    .toList();
                return DialogInput.singleOption(key, 200, entries, label, true);
            }
            @Override public @Nullable E parseResponse(DialogResponseView r, String key) {
                String id = r.getText(key);
                if (id == null) return null;
                try { return Enum.valueOf(type, id); }
                catch (IllegalArgumentException e) { return null; }
            }
        };
    }

    public static DialogInputAdapter<String> text(int maxLength) {
        return new DialogInputAdapter<>() {
            @Override public DialogInput buildInput(String key, Component label, String current) {
                return DialogInput.text(key, 200, label, true, current, maxLength, null);
            }
            @Override public @Nullable String parseResponse(DialogResponseView r, String key) {
                return r.getText(key);
            }
        };
    }

    private BuiltInAdapters() {}
}
```

NOTE: `"options.generic_value"` is the vanilla default label format for sliders (label + current value). `OptionEntry` is fully qualified above to avoid an extra import; if the Task 1 probe found a different factory shape, update `DialogFactories.optionEntry` only.

- [ ] **Step 4: Verify jspecify dependency**

jspecify was added to `build.gradle.kts` in Task 1 (`compileOnly("org.jspecify:jspecify:1.0.0")`). Verify it is present — the codec/adapter code above uses `org.jspecify.annotations.Nullable`.

- [ ] **Step 5: Run codec tests, expect pass**

Run: `./gradlew test --tests 'dev.jlo.preferences.codec.BuiltInCodecsTest' --console=plain`
Expected: PASS (8 tests). Adapter tests need `DialogResponseView` — that is a Paper interface with no public constructor; skip its unit test here and cover `parseResponse` in Task 8's smoke test. If Step 5 of Task 1 showed headless dialog construction works, optionally assert `checkbox().buildInput(...)` returns non-null.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: codec layer with built-in storage codecs and dialog adapters"
```

---

### Task 3: Value store + debounced flusher (TDD)

**Files:**
- Create: `internal/ValueStore.java`, `internal/YamlValueStore.java`, `internal/FlushScheduler.java`, `internal/DebouncedFlusher.java`
- Test: `src/test/java/dev/jlo/preferences/internal/YamlValueStoreTest.java`, `src/test/java/dev/jlo/preferences/internal/DebouncedFlusherTest.java`

**Interfaces:**
- Produces (consumed by Tasks 4, 6, 7):
  - `class YamlValueStore(Path dataDir)`: `void load(String namespace)`; `String getGlobal(ns, name)`; `String getPlayer(ns, uuid, name)`; `void setGlobal(ns, name, value)`; `void setPlayer(ns, uuid, name, value)`; `void removePlayerData(ns, uuid)`; `Snapshot snapshot(String ns)` where `record Snapshot(Map<String,String> global, Map<UUID, Map<String,String>> players)`; `void write(String ns)` (snapshot + serialize + atomic write).
  - Storage uses Bukkit's `YamlConfiguration` (bundled with Paper) — NO third-party YAML dependency, no shading.
  - `interface FlushScheduler { interface Cancellable { void cancel(); } Cancellable schedule(Runnable r); }` — production impl wraps `Bukkit.getScheduler().runTaskLater`; test impl is manual.
  - `class DebouncedFlusher(YamlValueStore store, FlushScheduler scheduler, long delayTicks, Executor async)`: `void markDirty(String ns)`, `void flushNamespaceSync(String ns)`, `void flushAllSync()`, `void shutdown()`.
  - Headless requirement: `YamlConfiguration` works without a running server (Bukkit's config classes are static utilities backed by SnakeYAML, no server required). Task 3's tests prove this headlessly; there is no alternative storage path in v1.

- [ ] **Step 1: Write failing store tests**

`src/test/java/dev/jlo/preferences/internal/YamlValueStoreTest.java`:
```java
package dev.jlo.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlValueStoreTest {

    @Test void setAndSnapshot(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setGlobal("demo", "announce_logins", "true");
        store.setPlayer("demo", uuid, "draw_distance", "12");

        YamlValueStore.Snapshot snap = store.snapshot("demo");
        assertEquals("true", snap.global().get("announce_logins"));
        assertEquals("12", snap.players().get(uuid).get("draw_distance"));
    }

    @Test void persistAndReload(@TempDir Path dir) throws IOException {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setGlobal("demo", "a", "1");
        store.setPlayer("demo", uuid, "b", "2");
        store.write("demo"); // synchronous write for tests

        assertTrue(Files.exists(dir.resolve("demo.yml")));

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("1", reloaded.getGlobal("demo", "a"));
        assertEquals("2", reloaded.getPlayer("demo", uuid, "b"));
    }

    @Test void corruptPlayerUuidSectionSkipped(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("demo.yml"), """
            players:
              not-a-uuid:
                x: "1"
            """);
        YamlValueStore store = new YamlValueStore(dir);
        store.load("demo"); // must not throw; bad uuid section skipped with warning
        assertNull(store.getGlobal("demo", "anything"));
    }

    @Test void missingFileLoadsEmpty(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        store.load("nope");
        assertNull(store.getGlobal("nope", "x"));
    }
}
```

`src/test/java/dev/jlo/preferences/internal/DebouncedFlusherTest.java`:
```java
package dev.jlo.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebouncedFlusherTest {

    static final class ManualScheduler implements FlushScheduler {
        final List<Runnable> pending = new ArrayList<>();
        @Override public Cancellable schedule(Runnable r) { pending.add(r); return () -> pending.remove(r); }
        void fireAll() { for (Runnable r : List.copyOf(pending)) r.run(); pending.clear(); }
    }

    @Test void coalescesMarksIntoOneFlush(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        ManualScheduler scheduler = new ManualScheduler();
        List<String> flushed = new ArrayList<>();
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, Runnable::run) {
            @Override protected void persist(String ns) { flushed.add(ns); }
        };

        flusher.markDirty("demo");
        flusher.markDirty("demo"); // second mark within window: no new schedule
        assertEquals(1, scheduler.pending.size());

        scheduler.fireAll();
        assertEquals(List.of("demo"), flushed);
    }

    @Test void flushAllSyncWritesEverythingAndCancelsTimers(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        ManualScheduler scheduler = new ManualScheduler();
        List<String> flushed = new ArrayList<>();
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, Runnable::run) {
            @Override protected void persist(String ns) { flushed.add(ns); }
        };
        flusher.markDirty("a");
        flusher.markDirty("b");
        flusher.flushAllSync();
        assertEquals(List.of("a", "b"), flushed.stream().sorted().toList());
        assertTrue(scheduler.pending.isEmpty(), "timers cancelled");
    }
}
```

- [ ] **Step 2: Run tests, expect failure**

Run: `./gradlew test --tests 'dev.jlo.preferences.internal.*' --console=plain`
Expected: FAIL — classes missing.

- [ ] **Step 3: Implement**

`internal/ValueStore.java` — marker SPI boundary (kept tiny so a custom-store SPI can grow here later):
```java
package dev.jlo.preferences.internal;

/** Internal storage boundary. v1 has exactly one implementation (YamlValueStore). */
public interface ValueStore {}
```

`internal/YamlValueStore.java`:
```java
package dev.jlo.preferences.internal;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Per-namespace YAML storage using Bukkit's YamlConfiguration (bundled with Paper).
 * Thread-safe: main thread mutates maps; async threads only read snapshots.
 */
public final class YamlValueStore implements ValueStore {

    public record Snapshot(Map<String, String> global, Map<UUID, Map<String, String>> players) {}

    private static final Logger LOG = Logger.getLogger("Preferences");
    private final Path dataDir;
    private final Map<String, Map<String, String>> globals = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Map<String, String>>> players = new ConcurrentHashMap<>();

    public YamlValueStore(Path dataDir) { this.dataDir = dataDir; }

    public void load(String ns) {
        Path file = dataDir.resolve(ns + ".yml");
        if (!Files.exists(file)) return;
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(file));
        } catch (IOException | InvalidConfigurationException e) {
            LOG.log(Level.SEVERE, "failed to load " + file + "; starting empty", e);
            return;
        }
        ConfigurationSection g = config.getConfigurationSection("global");
        if (g != null) {
            Map<String, String> target = globals.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
            for (String key : g.getKeys(false)) {
                String value = g.getString(key);
                if (value != null) target.put(key, value);
            }
        }
        ConfigurationSection p = config.getConfigurationSection("players");
        if (p != null) {
            Map<UUID, Map<String, String>> target = players.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
            for (String uuidKey : p.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    ConfigurationSection prefs = p.getConfigurationSection(uuidKey);
                    if (prefs == null) continue;
                    Map<String, String> values = target.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
                    for (String key : prefs.getKeys(false)) {
                        String value = prefs.getString(key);
                        if (value != null) values.put(key, value);
                    }
                } catch (IllegalArgumentException e) {
                    LOG.warning("Skipping unreadable player section '" + uuidKey + "' in " + ns + ".yml");
                }
            }
        }
    }

    public String getGlobal(String ns, String name) {
        Map<String, String> m = globals.get(ns);
        return m == null ? null : m.get(name);
    }

    public String getPlayer(String ns, UUID uuid, String name) {
        Map<UUID, Map<String, String>> nsMap = players.get(ns);
        if (nsMap == null) return null;
        Map<String, String> m = nsMap.get(uuid);
        return m == null ? null : m.get(name);
    }

    public void setGlobal(String ns, String name, String value) {
        globals.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(name, value);
    }

    public void setPlayer(String ns, UUID uuid, String name, String value) {
        players.computeIfAbsent(ns, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
            .put(name, value);
    }

    public void removePlayerData(String ns, UUID uuid) {
        Map<UUID, Map<String, String>> nsMap = players.get(ns);
        if (nsMap != null) nsMap.remove(uuid);
    }

    /** Immutable copy for async serialization. Called on the main thread. */
    public Snapshot snapshot(String ns) {
        Map<String, String> g = new LinkedHashMap<>();
        Map<String, String> gm = globals.get(ns);
        if (gm != null) g.putAll(gm);
        Map<UUID, Map<String, String>> p = new LinkedHashMap<>();
        Map<UUID, Map<String, String>> pm = players.get(ns);
        if (pm != null) pm.forEach((uuid, values) -> p.put(uuid, new LinkedHashMap<>(values)));
        return new Snapshot(g, p);
    }

    /** Serialize + atomic write. Called OFF the main thread (or synchronously on disable). */
    public void write(String ns) {
        Snapshot snap = snapshot(ns);
        YamlConfiguration config = new YamlConfiguration();
        snap.global().forEach((name, value) -> config.set("global." + name, value));
        snap.players().forEach((uuid, values) ->
            values.forEach((name, value) -> config.set("players." + uuid + "." + name, value)));
        try {
            Files.createDirectories(dataDir);
            Path target = dataDir.resolve(ns + ".yml");
            Path tmp = dataDir.resolve(ns + ".yml.tmp");
            Files.writeString(tmp, config.saveToString());
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "failed to persist " + ns + ".yml", e);
        }
    }
}
```

`internal/FlushScheduler.java`:
```java
package dev.jlo.preferences.internal;

/** Scheduler abstraction so the flusher is unit-testable without Bukkit. */
public interface FlushScheduler {
    interface Cancellable { void cancel(); }
    Cancellable schedule(Runnable task);
}
```

`internal/DebouncedFlusher.java`:
```java
package dev.jlo.preferences.internal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Trailing-window debouncer: dirty marks coalesce; flush runs async; disable flushes synchronously. */
public class DebouncedFlusher {

    private final YamlValueStore store;
    private final FlushScheduler scheduler;
    private final long delayTicks;
    private final Executor async;
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    private volatile boolean shutdown;

    public DebouncedFlusher(YamlValueStore store, FlushScheduler scheduler, long delayTicks, Executor async) {
        this.store = store;
        this.scheduler = scheduler;
        this.delayTicks = delayTicks;
        this.async = async;
    }

    public synchronized void markDirty(String ns) {
        if (shutdown) return;
        dirty.add(ns);
        if (scheduled.add(ns)) {
            scheduler.schedule(() -> {
                scheduled.remove(ns);
                if (dirty.remove(ns)) persist(ns);
            });
        }
    }

    /** Synchronously flush exactly one namespace (hooking plugin disabling). */
    public synchronized void flushNamespaceSync(String ns) {
        scheduled.remove(ns);
        if (dirty.remove(ns)) store.write(ns);
    }

    /** Synchronous final flush; blocks until all writes complete. Called from onDisable. */
    public synchronized void flushAllSync() {
        shutdown = true;
        for (String ns : Set.copyOf(dirty)) {
            if (dirty.remove(ns)) store.write(ns);
        }
    }

    public synchronized void shutdown() {
        shutdown = true;
        dirty.clear();
        scheduled.clear();
    }

    /** Overridable seam for tests; production persists via async executor. */
    protected void persist(String ns) {
        async.execute(() -> store.write(ns));
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests 'dev.jlo.preferences.internal.*' --console=plain`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: yaml value store + debounced async flusher"
```

---

### Task 4: Registry, Preference handle, change event

**Files:**
- Create: `api/PreferencesService.java`, `api/Preference.java`, `api/PreferenceBuilder.java`, `api/PreferenceKey.java`, `api/PreferenceChange.java`, `api/event/PreferenceChangeEvent.java`, `internal/RegisteredPreference.java`, `internal/PreferenceRegistry.java`
- Test: `src/test/java/dev/jlo/preferences/internal/PreferenceRegistryTest.java`

**Interfaces:**
- Produces (consumed by Tasks 5–7):
  - `record PreferenceKey(String namespace, String name)` with `String asString()` → `namespace:name`
  - `Preference<T>`: `PreferenceKey key(); PreferenceScope scope(); Class<T> type(); Component label(); Component description(); T defaultValue(); T get(Player); T getGlobal(); void set(Player, T); void setGlobal(T); void reset(Player); void resetGlobal();` — wrong-scope accessors throw `IllegalStateException`.
  - `PreferenceBuilder<T>` fluent: `.playerScoped(name)` / `.global(name)`, `.label(Component)`, `.description(Component)`, `.codec(PreferenceCodec<T>)`, `.defaultValue(T)`, `.onChange(Consumer<PreferenceChange>)`; terminal `build()` handled by service.
  - `PreferencesService`: `<T> Preference<T> register(Plugin owner, Class<T> type, Consumer<PreferenceBuilder<T>> configure)`; `Collection<? extends Preference<?>> all()`; `void unregisterPlugin(Plugin plugin)` — plus the internal registry class backing it.
  - `PreferenceChangeEvent extends Event`: `PreferenceKey key(), String oldValue(), String newValue(), UUID editor()` (editor null when console/global-set via API), static HandlerList. Values exposed as stored strings to stay type-erasure-free for listeners.
  - `api/PreferenceChange.java`: `record PreferenceChange(PreferenceKey key, String oldValue, String newValue)` for the per-pref callback.

- [ ] **Step 1: Write failing registry test (Bukkit-free core of registration semantics)**

The registry's duplicate-key and lookup rules are testable without Bukkit by constructing registrations directly. `src/test/java/dev/jlo/preferences/internal/PreferenceRegistryTest.java`:
```java
package dev.jlo.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import dev.jlo.preferences.api.PreferenceKey;
import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.api.codec.BuiltInCodecs;
import dev.jlo.preferences.api.codec.PreferenceCodec;
import org.junit.jupiter.api.Test;

class PreferenceRegistryTest {

    private RegisteredPreference<Boolean> boolPref(String ns, String name) {
        return new RegisteredPreference<>(
            new PreferenceKey(ns, name), PreferenceScope.PLAYER,
            net.kyori.adventure.text.Component.text(name),
            net.kyori.adventure.text.Component.text("desc"),
            PreferenceCodec.booleanBox(), Boolean.class, false, null);
    }

    @Test void registerAndLookup() {
        PreferenceRegistry registry = new PreferenceRegistry();
        RegisteredPreference<Boolean> pref = boolPref("demo", "flag");
        registry.register(pref);
        assertSame(pref, registry.byKey(pref.key()));
        assertEquals(1, registry.all().size());
    }

    @Test void duplicateKeyRejected() {
        PreferenceRegistry registry = new PreferenceRegistry();
        registry.register(boolPref("demo", "flag"));
        assertThrows(IllegalStateException.class, () -> registry.register(boolPref("demo", "flag")));
    }

    @Test void unregisterPluginRemovesOnlyItsPrefs() {
        PreferenceRegistry registry = new PreferenceRegistry();
        registry.register(boolPref("demo", "a"));
        registry.register(boolPref("other", "b"));
        registry.unregisterNamespace("demo");
        assertNull(registry.byKey(new PreferenceKey("demo", "a")));
        assertNotNull(registry.byKey(new PreferenceKey("other", "b")));
    }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `./gradlew test --tests 'dev.jlo.preferences.internal.PreferenceRegistryTest' --console=plain`
Expected: FAIL — missing classes.

- [ ] **Step 3: Implement**

`api/PreferenceKey.java`:
```java
package dev.jlo.preferences.api;

public record PreferenceKey(String namespace, String name) {
    public PreferenceKey {
        // No dots: dot is Bukkit YamlConfiguration's path separator and would corrupt storage keys.
        if (!namespace.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("bad namespace: " + namespace);
        if (!name.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("bad preference name: " + name);
    }
    public String asString() { return namespace + ":" + name; }
}
```

`api/PreferenceChange.java`:
```java
package dev.jlo.preferences.api;

/** Payload for per-preference change callbacks. Values are stored-string form. */
public record PreferenceChange(PreferenceKey key, String oldValue, String newValue) {}
```

`api/event/PreferenceChangeEvent.java`:
```java
package dev.jlo.preferences.api.event;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Fired before a preference value is persisted. Cancel to reject the change. */
public class PreferenceChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PreferenceKey key;
    private final String oldValue;
    private final String newValue;
    private final @Nullable UUID editor; // null when set programmatically/console
    private boolean cancelled;

    public PreferenceChangeEvent(PreferenceKey key, String oldValue, String newValue, @Nullable UUID editor) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.editor = editor;
    }

    public PreferenceKey key() { return key; }
    public String oldValue() { return oldValue; }
    public String newValue() { return newValue; }
    public @Nullable UUID editor() { return editor; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
```

`internal/RegisteredPreference.java`:
```java
package dev.jlo.preferences.internal;

import dev.jlo.preferences.api.Preference;
import dev.jlo.preferences.api.PreferenceChange;
import dev.jlo.preferences.api.PreferenceKey;
import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.api.codec.PreferenceCodec;
import dev.jlo.preferences.api.event.PreferenceChangeEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public final class RegisteredPreference<T> implements Preference<T> {



    private final PreferenceKey key;
    private final PreferenceScope scope;
    private final Component label;
    private final Component description;
    private final PreferenceCodec<T> codec;
    private final Class<T> type;
    private final T defaultValue;
    private final @Nullable Consumer<PreferenceChange> onChange;
    private final Map<UUID, T> playerValues = new ConcurrentHashMap<>();
    private volatile @Nullable T globalValue;

    /** Callbacks injected by the plugin wiring (storage + event bridge). */
    public @Nullable java.util.function.Function<String, String> storedValueLookup; // returns stored string or null
    public @Nullable Consumer<Applied> appliedHook; // persistence + dirty marking

    public record Applied(PreferenceKey key, PreferenceScope scope, @Nullable UUID player, String storedValue) {}

    public RegisteredPreference(PreferenceKey key, PreferenceScope scope, Component label,
                                Component description, PreferenceCodec<T> codec, Class<T> type,
                                T defaultValue, @Nullable Consumer<PreferenceChange> onChange) {
        this.key = key;
        this.scope = scope;
        this.label = label;
        this.description = description;
        this.codec = codec;
        this.type = type;
        this.defaultValue = defaultValue;
        this.onChange = onChange;
    }

    @Override public PreferenceKey key() { return key; }
    @Override public PreferenceScope scope() { return scope; }
    @Override public Class<T> type() { return type; }
    @Override public Component label() { return label; }
    @Override public Component description() { return description; }
    @Override public T defaultValue() { return defaultValue; }
    public PreferenceCodec<T> codec() { return codec; }

    @Override public T get(Player player) {
        checkScope(PreferenceScope.PLAYER);
        return playerValues.computeIfAbsent(player.getUniqueId(), uuid -> {
            String stored = storedValueLookup == null ? null
                : storedValueLookup.apply(key.namespace() + "\u0000" + uuid + "\u0000" + key.name());
            return parseOrDefault(stored);
        });
    }

    @Override public T getGlobal() {
        checkScope(PreferenceScope.GLOBAL);
        T value = globalValue;
        if (value == null) {
            String stored = storedValueLookup == null ? null
                : storedValueLookup.apply(key.namespace() + "\u0000\ud83c\udf10\u0000" + key.name());
            value = parseOrDefault(stored);
            globalValue = value;
        }
        return value;
    }

    @Override public void set(Player player, T newValue) {
        checkScope(PreferenceScope.PLAYER);
        apply(player.getUniqueId(), newValue);
    }

    @Override public void setGlobal(T newValue) {
        checkScope(PreferenceScope.GLOBAL);
        apply(null, newValue);
    }

    @Override public void reset(Player player) { set(player, defaultValue); }
    @Override public void resetGlobal() { setGlobal(defaultValue); }

    private void apply(@Nullable UUID player, T newValue) {
        if (!type.isInstance(newValue)) throw new IllegalArgumentException("value is not " + type.getSimpleName());
        String newStored = codec.storage().write(newValue);
        String oldStored = codec.storage().write(currentStoredTarget(player));

        PreferenceChangeEvent event = new PreferenceChangeEvent(key, oldStored, newStored, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (player == null) globalValue = newValue;
        else playerValues.put(player, newValue);

        if (appliedHook != null) appliedHook.accept(new Applied(key, scope, player, newStored));
        if (onChange != null) onChange.accept(new PreferenceChange(key, oldStored, newStored));
    }

    private T currentStoredTarget(@Nullable UUID player) {
        if (player == null) { T v = globalValue; return v == null ? defaultValue : v; }
        T v = playerValues.get(player);
        return v == null ? defaultValue : v;
    }

    private T parseOrDefault(@Nullable String stored) {
        if (stored == null) return defaultValue;
        try { return codec.storage().parse(stored); }
        catch (RuntimeException e) {
            Bukkit.getLogger().warning("Invalid stored value for " + key.asString() + ": '" + stored + "'; using default");
            return defaultValue;
        }
    }

    private void checkScope(PreferenceScope expected) {
        if (scope != expected) throw new IllegalStateException(key.asString() + " is " + scope + "-scoped");
    }

    /** Session/dialog support: evict cached value so next read reloads from store. */
    public void invalidatePlayer(UUID uuid) { playerValues.remove(uuid); }
}
```

`internal/PreferenceRegistry.java`:
```java
package dev.jlo.preferences.internal;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class PreferenceRegistry {

    private final Map<PreferenceKey, RegisteredPreference<?>> prefs = new ConcurrentHashMap<>();

    public void register(RegisteredPreference<?> pref) {
        if (prefs.putIfAbsent(pref.key(), pref) != null) {
            throw new IllegalStateException("preference already registered: " + pref.key().asString());
        }
    }

    public @Nullable RegisteredPreference<?> byKey(PreferenceKey key) { return prefs.get(key); }

    public Collection<RegisteredPreference<?>> all() { return prefs.values(); }

    public void unregisterNamespace(String namespace) {
        prefs.keySet().removeIf(k -> k.namespace().equals(namespace));
    }
}
```

`api/Preference.java`:
```java
package dev.jlo.preferences.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Typed handle for a registered preference. */
public interface Preference<T> {
    PreferenceKey key();
    PreferenceScope scope();
    Class<T> type();
    Component label();
    Component description();
    T defaultValue();
    T get(Player player);
    T getGlobal();
    void set(Player player, T value);
    void setGlobal(T value);
    void reset(Player player);
    void resetGlobal();
}
```

`api/PreferenceBuilder.java`:
```java
package dev.jlo.preferences.api;

import dev.jlo.preferences.api.codec.PreferenceCodec;
import dev.jlo.preferences.internal.RegisteredPreference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

public final class PreferenceBuilder<T> {

    private final String namespace;
    private final Class<T> type;
    private String name;
    private PreferenceScope scope;
    private Component label;
    private Component description = Component.empty();
    private PreferenceCodec<T> codec;
    private T defaultValue;
    private Consumer<PreferenceChange> onChange;

    public PreferenceBuilder(String namespace, Class<T> type) {
        this.namespace = namespace;
        this.type = type;
    }

    public PreferenceBuilder<T> playerScoped(String name) { this.name = name; this.scope = PreferenceScope.PLAYER; return this; }
    public PreferenceBuilder<T> global(String name) { this.name = name; this.scope = PreferenceScope.GLOBAL; return this; }
    public PreferenceBuilder<T> label(Component label) { this.label = label; return this; }
    public PreferenceBuilder<T> description(Component description) { this.description = description; return this; }
    public PreferenceBuilder<T> codec(PreferenceCodec<T> codec) { this.codec = codec; return this; }
    public PreferenceBuilder<T> defaultValue(T defaultValue) { this.defaultValue = defaultValue; return this; }
    public PreferenceBuilder<T> onChange(Consumer<PreferenceChange> onChange) { this.onChange = onChange; return this; }

    RegisteredPreference<T> build() {
        if (name == null || scope == null || label == null || codec == null || defaultValue == null) {
            throw new IllegalStateException("name/scope/label/codec/defaultValue are all required");
        }
        return new RegisteredPreference<>(new PreferenceKey(namespace, name), scope, label,
            description, codec, type, defaultValue, onChange);
    }
}
```

`api/PreferencesService.java`:
```java
package dev.jlo.preferences.api;

import dev.jlo.preferences.internal.PreferenceRegistry;
import dev.jlo.preferences.internal.RegisteredPreference;
import java.util.Collection;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** Service loaded via Bukkit.getServicesManager().load(PreferencesService.class). */
public final class PreferencesService {

    private final PreferenceRegistry registry;

    public PreferencesService(PreferenceRegistry registry) { this.registry = registry; }

    @SuppressWarnings("unchecked")
    public <T> Preference<T> register(Plugin owner, Class<T> type, Consumer<PreferenceBuilder<T>> configure) {
        PreferenceBuilder<T> builder = new PreferenceBuilder<>(owner.getName().toLowerCase(java.util.Locale.ROOT), type);
        configure.accept(builder);
        RegisteredPreference<T> pref = builder.build();
        registry.register(pref);
        return pref;
    }

    public Collection<? extends Preference<?>> all() { return registry.all(); }

    public void unregisterPlugin(Plugin plugin) {
        registry.unregisterNamespace(plugin.getName().toLowerCase(java.util.Locale.ROOT));
    }
}
```

NOTE on the lookup-key sentinel strings (`\u0000` separators): the plugin wiring (Task 7) installs `storedValueLookup` and parses the sentinel format. Keep exactly as written so Task 7's parser matches.

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests 'dev.jlo.preferences.internal.PreferenceRegistryTest' --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: preference registry, typed handles, change event"
```

---

### Task 5: Session manager (TDD)

**Files:**
- Create: `internal/session/DialogSession.java`, `internal/session/DialogSessionManager.java`
- Test: `src/test/java/dev/jlo/preferences/internal/session/DialogSessionManagerTest.java`

**Interfaces:**
- Produces (consumed by Task 6):
  - `DialogSession(UUID player, Kind kind, int page, @Nullable PreferenceKey target)` with `enum Kind { PLAYER_LIST, GLOBAL_LIST, EDIT }`
  - `DialogSessionManager`: `void open(DialogSession)`, `@Nullable DialogSession current(UUID)`, `void close(UUID)`, `boolean matches(UUID, Kind)` — single-slot per player; opening replaces.

- [ ] **Step 1: Failing test**

`src/test/java/dev/jlo/preferences/internal/session/DialogSessionManagerTest.java`:
```java
package dev.jlo.preferences.internal.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogSessionManagerTest {

    @Test void openAndQuery() {
        DialogSessionManager mgr = new DialogSessionManager();
        UUID p = UUID.randomUUID();
        mgr.open(new DialogSession(p, DialogSession.Kind.PLAYER_LIST, 0, null));
        assertNotNull(mgr.current(p));
        assertTrue(mgr.matches(p, DialogSession.Kind.PLAYER_LIST));
    }

    @Test void openingReplacesPreviousSession() {
        DialogSessionManager mgr = new DialogSessionManager();
        UUID p = UUID.randomUUID();
        mgr.open(new DialogSession(p, DialogSession.Kind.PLAYER_LIST, 2, null));
        PreferenceKey key = new PreferenceKey("demo", "flag");
        mgr.open(new DialogSession(p, DialogSession.Kind.EDIT, 0, key));
        assertEquals(DialogSession.Kind.EDIT, mgr.current(p).kind());
        assertEquals(key, mgr.current(p).target());
    }

    @Test void unknownPlayerHasNoSession() {
        DialogSessionManager mgr = new DialogSessionManager();
        assertNull(mgr.current(UUID.randomUUID()));
        assertFalse(mgr.matches(UUID.randomUUID(), DialogSession.Kind.EDIT));
    }

    @Test void closeRemoves() {
        DialogSessionManager mgr = new DialogSessionManager();
        UUID p = UUID.randomUUID();
        mgr.open(new DialogSession(p, DialogSession.Kind.PLAYER_LIST, 0, null));
        mgr.close(p);
        assertNull(mgr.current(p));
    }
}
```

- [ ] **Step 2: Run, expect failure; Step 3: implement**

`internal/session/DialogSession.java`:
```java
package dev.jlo.preferences.internal.session;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DialogSession(UUID player, Kind kind, int page, @Nullable PreferenceKey target) {
    public enum Kind { PLAYER_LIST, GLOBAL_LIST, EDIT }
}
```

`internal/session/DialogSessionManager.java`:
```java
package dev.jlo.preferences.internal.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Single-slot sessions: opening any dialog replaces the player's previous one. */
public final class DialogSessionManager {

    private final Map<UUID, DialogSession> sessions = new ConcurrentHashMap<>();

    public void open(DialogSession session) { sessions.put(session.player(), session); }

    public @Nullable DialogSession current(UUID player) { return sessions.get(player); }

    public void close(UUID player) { sessions.remove(player); }

    public boolean matches(UUID player, DialogSession.Kind kind) {
        DialogSession s = sessions.get(player);
        return s != null && s.kind() == kind;
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests 'dev.jlo.preferences.internal.session.*' --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: dialog session manager with single-slot sessions"
```

---

### Task 6: Dialog screens + click routing

**Files:**
- Create: `internal/dialog/DialogScreens.java`, `internal/dialog/ClickRouter.java`
- Test: headless coverage limited (see Task 1 Step 5 outcome); routing logic that is Bukkit-free gets tested via a small seam.

**Interfaces:**
- Consumes: `DialogFactories` (Task 1), `DialogSessionManager` (Task 5), `PreferenceRegistry` + `RegisteredPreference` (Task 4), `DebouncedFlusher`/`YamlValueStore` (Task 3).
- Produces: `DialogScreens.showPlayerList(Player, int page)`, `showGlobalList(Player, int page)`, `showEdit(Player, RegisteredPreference<?>, int returnPage)`; `ClickRouter implements Listener` handling `PlayerCustomClickEvent` + `PlayerQuitEvent`.

- [ ] **Step 1: Implement DialogScreens**

`internal/dialog/DialogScreens.java`:
```java
package dev.jlo.preferences.internal.dialog;

import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.internal.PreferenceRegistry;
import dev.jlo.preferences.internal.RegisteredPreference;
import dev.jlo.preferences.internal.session.DialogSession;
import dev.jlo.preferences.internal.session.DialogSessionManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class DialogScreens {

    public static final int PAGE_SIZE = 20;

    private final PreferenceRegistry registry;
    private final DialogSessionManager sessions;

    public DialogScreens(PreferenceRegistry registry, DialogSessionManager sessions) {
        this.registry = registry;
        this.sessions = sessions;
    }

    public void showPlayerList(Player player, int page) {
        showList(player, PreferenceScope.PLAYER, page, DialogSession.Kind.PLAYER_LIST);
    }

    public void showGlobalList(Player player, int page) {
        showList(player, PreferenceScope.GLOBAL, page, DialogSession.Kind.GLOBAL_LIST);
    }

    private void showList(Player player, PreferenceScope scope, int page, DialogSession.Kind kind) {
        List<RegisteredPreference<?>> prefs = registry.all().stream()
            .filter(p -> p.scope() == scope)
            .sorted(Comparator.comparing(p -> p.key().asString()))
            .toList();

        int pages = Math.max(1, (prefs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, pages - 1));
        List<RegisteredPreference<?>> slice = prefs.subList(
            clampedPage * PAGE_SIZE, Math.min(prefs.size(), (clampedPage + 1) * PAGE_SIZE));

        List<ActionButton> actions = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            RegisteredPreference<?> pref = slice.get(i);
            actions.add(ActionButton.builder(pref.label())
                .tooltip(pref.description().append(Component.newline())
                    .append(currentValueLine(player, pref)).color(NamedTextColor.GRAY)))
                .action(DialogAction.customClick(DialogFactories.editKey(i), null))
                .build());
        }

        ActionButton exit = ActionButton.builder(Component.text("Close"))
            .action(DialogAction.customClick(DialogFactories.KEY_CANCEL, null))
            .build();

        List<ActionButton> withNav = new ArrayList<>(actions);
        if (clampedPage > 0) {
            withNav.add(ActionButton.builder(Component.text("« Previous"))
                .action(DialogAction.customClick(DialogFactories.KEY_LIST_PREV, null)).build());
        }
        if (clampedPage < pages - 1) {
            withNav.add(ActionButton.builder(Component.text("Next »"))
                .action(DialogAction.customClick(DialogFactories.KEY_LIST_NEXT, null)).build());
        }

        sessions.open(new DialogSession(player.getUniqueId(), kind, clampedPage, null));
        Dialog dialog = DialogFactories.multiAction(
            Component.text(scope == PreferenceScope.GLOBAL ? "Server Preferences" : "Your Preferences"),
            withNav, exit);
        player.showDialog(dialog);
    }

    private Component currentValueLine(Player player, RegisteredPreference<?> pref) {
        Object value = pref.scope() == PreferenceScope.GLOBAL ? pref.getGlobal() : pref.get(player);
        return Component.text("Current: " + pref.codec().storage().write(cast(value)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }

    public <T> void showEdit(Player player, RegisteredPreference<T> pref, int returnPage) {
        var adapter = pref.codec().input();
        if (adapter == null) {
            // Read-only in GUI: display current value, single Close button.
            Object value = pref.scope() == PreferenceScope.GLOBAL ? pref.getGlobal() : pref.get(player);
            Dialog dialog = DialogFactories.notice(pref.label(), List.of(
                DialogBody.plainMessage(pref.description()),
                DialogBody.plainMessage(Component.text("Current: " + pref.codec().storage().write(cast(value))))));
            sessions.open(new DialogSession(player.getUniqueId(), DialogSession.Kind.EDIT, returnPage, pref.key()));
            player.showDialog(dialog);
            return;
        }
        T current = pref.scope() == PreferenceScope.GLOBAL ? pref.getGlobal() : pref.get(player);
        var input = adapter.buildInput("value", pref.label(), current);
        Dialog dialog = DialogFactories.editDialog(pref.label(), List.of(pref.description()), input);
        sessions.open(new DialogSession(player.getUniqueId(), DialogSession.Kind.EDIT, returnPage, pref.key()));
        player.showDialog(dialog);
    }
}
```

- [ ] **Step 2: Implement ClickRouter**

`internal/dialog/ClickRouter.java`:
```java
package dev.jlo.preferences.internal.dialog;

import dev.jlo.preferences.internal.PreferenceRegistry;
import dev.jlo.preferences.internal.RegisteredPreference;
import dev.jlo.preferences.internal.session.DialogSession;
import dev.jlo.preferences.internal.session.DialogSessionManager;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

public final class ClickRouter implements Listener {

    private final PreferenceRegistry registry;
    private final DialogSessionManager sessions;
    private final DialogScreens screens;

    public ClickRouter(PreferenceRegistry registry, DialogSessionManager sessions, DialogScreens screens) {
        this.registry = registry;
        this.sessions = sessions;
        this.screens = screens;
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof org.bukkit.entity.Player)) return; // play phase only
        Player player = (Player) event.getCommonConnection();
        Key id = event.getIdentifier();
        if (!"preferences".equals(id.namespace())) return;

        DialogSession session = sessions.current(player.getUniqueId());
        if (session == null) return; // forged/stale click: no live session

        String path = id.value();
        switch (path) {
            case "save" -> handleSave(player, session, event.getDialogResponseView());
            case "cancel" -> sessions.close(player.getUniqueId());
            case "list_prev" -> navigate(player, session, session.page() - 1);
            case "list_next" -> navigate(player, session, session.page() + 1);
            default -> {
                if (path.startsWith("edit/") && session.kind() != DialogSession.Kind.EDIT) {
                    openEditByIndex(player, session, path.substring("edit/".length()));
                }
            }
        }
    }

    private void navigate(Player player, DialogSession session, int newPage) {
        switch (session.kind()) {
            case PLAYER_LIST -> screens.showPlayerList(player, newPage);
            case GLOBAL_LIST -> screens.showGlobalList(player, newPage);
            case EDIT -> {} // nav buttons never appear in edit dialogs
        }
    }

    private void openEditByIndex(Player player, DialogSession session, String indexStr) {
        int index;
        try { index = Integer.parseInt(indexStr); }
        catch (NumberFormatException e) { return; }

        var scopePrefs = registry.all().stream()
            .filter(p -> p.scope() == (session.kind() == DialogSession.Kind.GLOBAL_LIST
                ? dev.jlo.preferences.api.PreferenceScope.GLOBAL
                : dev.jlo.preferences.api.PreferenceScope.PLAYER))
            .sorted(java.util.Comparator.comparing(p -> p.key().asString()))
            .toList();
        int absolute = session.page() * DialogScreens.PAGE_SIZE + index;
        if (absolute < 0 || absolute >= scopePrefs.size()) return; // out of range: ignore

        screens.showEdit(player, cast(scopePrefs.get(absolute)), session.page());
    }

    @SuppressWarnings("unchecked")
    private static <T> RegisteredPreference<T> cast(RegisteredPreference<?> pref) {
        return (RegisteredPreference<T>) pref;
    }

    private void handleSave(Player player, DialogSession session, @Nullable DialogResponseView view) {
        if (session.kind() != DialogSession.Kind.EDIT || session.target() == null) return;
        RegisteredPreference<?> pref = registry.byKey(session.target());
        if (pref == null) return;
        saveTyped(player, cast(pref), view, session.page());
    }

    private <T> void saveTyped(Player player, RegisteredPreference<T> pref,
                               @Nullable DialogResponseView view, int returnPage) {
        var adapter = pref.codec().input();
        T parsed = (adapter == null || view == null) ? null : adapter.parseResponse(view, "value");
        if (parsed == null) {
            player.sendMessage(Component.text("Invalid value — nothing was changed.", NamedTextColor.RED));
            screens.showEdit(player, pref, returnPage);
            return;
        }
        if (pref.scope() == dev.jlo.preferences.api.PreferenceScope.GLOBAL
                && !player.hasPermission("preferences.manage")) {
            player.sendMessage(Component.text("You don't have permission to change server preferences.", NamedTextColor.RED));
            return;
        }
        if (pref.scope() == dev.jlo.preferences.api.PreferenceScope.GLOBAL) pref.setGlobal(parsed);
        else pref.set(player, parsed);
        player.sendMessage(Component.text("Saved ", NamedTextColor.GREEN)
            .append(pref.label()).append(Component.text(".", NamedTextColor.GREEN)));
        if (pref.scope() == dev.jlo.preferences.api.PreferenceScope.GLOBAL) {
            screens.showGlobalList(player, returnPage);
        } else {
            screens.showPlayerList(player, returnPage);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.close(event.getPlayer().getUniqueId());
    }
}
```

NOTE on `event.getCommonConnection() instanceof Player`: `PlayerCommonConnection` is the play-phase connection interface; verify during compilation whether it IS a `Player` or exposes one (`getPlayer()`/`getAudience()`). Paper docs' example uses `getCommonConnection() instanceof PlayerConfigurationConnection`; for the play phase the equivalent check is whatever the compiler accepts. Fix this one line per compiler output; everything else stands.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --console=plain`
Expected: BUILD SUCCESSFUL. Fix only the `instanceof` line per compiler guidance if needed.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: dialog screens and session-validated click routing"
```

---

### Task 7: Command, plugin wiring, config

**Files:**
- Create: `internal/command/PreferencesCommand.java`, `PreferencesPlugin.java`
- Modify: `src/main/resources/plugin.yml` (permissions + command)

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: runnable plugin jar; `PreferencesService` registered in the services manager.

- [ ] **Step 1: Command**

`internal/command/PreferencesCommand.java`:
```java
package dev.jlo.preferences.internal.command;

import dev.jlo.preferences.internal.dialog.DialogScreens;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import java.util.List;

public final class PreferencesCommand implements TabExecutor {

    private final DialogScreens screens;

    public PreferencesCommand(DialogScreens screens) { this.screens = screens; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Preferences are edited in-game via dialogs.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("global")) {
            if (!player.hasPermission("preferences.manage")) {
                player.sendMessage(net.kyori.adventure.text.Component.text(
                    "You don't have permission to manage server preferences.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
                return true;
            }
            screens.showGlobalList(player, 0);
            return true;
        }
        if (!player.hasPermission("preferences.use")) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "You don't have permission to use preferences.",
                net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        screens.showPlayerList(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("preferences.manage")) return List.of("global");
        return List.of();
    }
}
```

- [ ] **Step 2: Plugin wiring**

`PreferencesPlugin.java`:
```java
package dev.jlo.preferences;

import dev.jlo.preferences.api.PreferencesService;
import dev.jlo.preferences.internal.DebouncedFlusher;
import dev.jlo.preferences.internal.FlushScheduler;
import dev.jlo.preferences.internal.PreferenceRegistry;
import dev.jlo.preferences.internal.RegisteredPreference;
import dev.jlo.preferences.internal.YamlValueStore;
import dev.jlo.preferences.internal.command.PreferencesCommand;
import dev.jlo.preferences.internal.dialog.ClickRouter;
import dev.jlo.preferences.internal.dialog.DialogScreens;
import dev.jlo.preferences.internal.session.DialogSessionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

public final class PreferencesPlugin extends JavaPlugin implements Listener {

    private PreferenceRegistry registry;
    private YamlValueStore store;
    private DebouncedFlusher flusher;
    private ExecutorService io;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int flushSeconds = getConfig().getInt("storage.flush-seconds", 5);

        registry = new PreferenceRegistry();
        store = new YamlValueStore(getDataFolder().toPath().resolve("data"));
        io = Executors.newSingleThreadExecutor(r -> new Thread(r, "Preferences IO"));
        flusher = new DebouncedFlusher(store, new BukkitFlushScheduler(), flushSeconds * 20L, io);

        DialogSessionManager sessions = new DialogSessionManager();
        DialogScreens screens = new DialogScreens(registry, sessions);

        wireStorageLookup();

        PreferencesService service = new PreferencesService(registry);
        Bukkit.getServicesManager().register(PreferencesService.class, service, this, ServicePriority.Normal);

        ClickRouter router = new ClickRouter(registry, sessions, screens);
        Bukkit.getPluginManager().registerEvents(router, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        var command = getCommand("preferences");
        if (command != null) {
            PreferencesCommand exec = new PreferencesCommand(screens);
            command.setExecutor(exec);
            command.setTabCompleter(exec);
        }
        getLogger().info("Preferences enabled.");
    }

    /** Bridges RegisteredPreference lazy loads + persistence into the store/flusher. */
    private void wireStorageLookup() {
        // RegisteredPreference uses sentinel format: ns + \0 + (uuid|🌐) + \0 + name
        for (RegisteredPreference<?> pref : registry.all()) wire(pref);
        // Dynamic registrations happen after enable; hook via registry callback:
        registry.onRegister(this::wire);
    }

    private void wire(RegisteredPreference<?> pref) {
        pref.storedValueLookup = lookupKey -> {
            String[] parts = lookupKey.split("\u0000");
            String ns = parts[0], target = parts[1], name = parts[2];
            if (target.equals("\ud83c\udf10")) return store.getGlobal(ns, name);
            return store.getPlayer(ns, java.util.UUID.fromString(target), name);
        };
        pref.appliedHook = applied -> {
            if (applied.player() == null) store.setGlobal(applied.key().namespace(), applied.key().name(), applied.storedValue());
            else store.setPlayer(applied.key().namespace(), applied.player(), applied.key().name(), applied.storedValue());
            flusher.markDirty(applied.key().namespace());
        };
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) return;
        String ns = event.getPlugin().getName().toLowerCase(java.util.Locale.ROOT);
        // Flush this plugin's pending writes before dropping its registrations.
        flusher.markDirty(ns);
        flusher.flushNamespaceSync(ns);
        registry.unregisterNamespace(ns);
    }

    @Override
    public void onDisable() {
        flusher.flushAllSync();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        getLogger().info("Preferences disabled; all data flushed.");
    }

    private final class BukkitFlushScheduler implements FlushScheduler {
        @Override
        public Cancellable schedule(Runnable task) {
            var bukkitTask = Bukkit.getScheduler().runTaskLater(PreferencesPlugin.this, task, flushDelayTicks());
            return bukkitTask::cancel;
        }
    }

    private long flushDelayTicks() { return getConfig().getInt("storage.flush-seconds", 5) * 20L; }
}
```

This task also ADDS to `PreferenceRegistry`:
```java
    private final java.util.List<java.util.function.Consumer<RegisteredPreference<?>>> onRegister =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public void onRegister(java.util.function.Consumer<RegisteredPreference<?>> hook) {
        onRegister.add(hook);
        all().forEach(hook); // wire anything registered before the hook attached
    }
```
and inside `register(...)`, after `putIfAbsent` succeeds: `onRegister.forEach(h -> h.accept(pref));`

And ADDS to `DebouncedFlusher`:
```java
    /** Synchronously flush exactly one namespace (used when a hooking plugin disables). */
    public synchronized void flushNamespaceSync(String ns) {
        if (dirty.remove(ns)) {
            scheduled.remove(ns);
            store.write(ns);
        }
    }
```

- [ ] **Step 3: plugin.yml final form**

Replace `src/main/resources/plugin.yml` with:
```yaml
name: Preferences
version: '0.1.0'
main: dev.jlo.preferences.PreferencesPlugin
api-version: '1.21'
description: Typed preferences with dialog GUI, owned by this plugin.
commands:
  preferences:
    description: Open your preferences dialog.
    usage: /preferences [global]
    aliases: [prefs]
permissions:
  preferences.use:
    description: Open and edit your own preferences.
    default: true
  preferences.manage:
    description: View and edit server-global preferences.
    default: op
```

Create `src/main/resources/config.yml`:
```yaml
storage:
  # Trailing window before dirty data files are written asynchronously.
  flush-seconds: 5
gui:
  # Max entries per list dialog before pagination.
  page-size: 20
```

NOTE: `DialogScreens.PAGE_SIZE` is currently a constant; wire `gui.page-size` by passing the config value into the `DialogScreens` constructor instead. Make that change in this task (constructor param, replace the constant's usages).

- [ ] **Step 4: Full build + all tests**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, all unit tests pass, jar produced at `build/libs/preferences-0.1.0-SNAPSHOT.jar`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: command, permissions, plugin wiring with async persistence"
```

---

### Task 8: Demo plugin + smoke test on a real server

**Files:**
- Create: `demo/src/main/resources/plugin.yml`, `demo/src/main/java/dev/jlo/preferences/demo/DemoPlugin.java`
- Manual: local Paper 1.21.7 server under `run/` (gitignored)

**Interfaces:**
- Consumes: the full public API exactly as a third-party plugin would.

- [ ] **Step 1: Demo plugin**

`demo/src/main/resources/plugin.yml`:
```yaml
name: PreferencesDemo
version: '0.1.0'
main: dev.jlo.preferences.demo.DemoPlugin
api-version: '1.21'
depend: [Preferences]
```

`demo/src/main/java/dev/jlo/preferences/demo/DemoPlugin.java`:
```java
package dev.jlo.preferences.demo;

import dev.jlo.preferences.api.Preference;
import dev.jlo.preferences.api.PreferencesService;
import dev.jlo.preferences.api.codec.PreferenceCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class DemoPlugin extends JavaPlugin {

    public enum Weather { SUNNY, RAINY, STORMY }

    @Override
    public void onEnable() {
        PreferencesService prefs = Bukkit.getServicesManager().load(PreferencesService.class);
        if (prefs == null) {
            getLogger().severe("Preferences service missing!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Preference<Boolean> notifications = prefs.register(this, Boolean.class, b -> b
            .playerScoped("notifications")
            .label(Component.text("Notifications"))
            .description(Component.text("Receive demo notifications"))
            .codec(PreferenceCodec.booleanBox())
            .defaultValue(true)
            .onChange(c -> getLogger().info("notifications: " + c.oldValue() + " -> " + c.newValue())));

        Preference<Integer> volume = prefs.register(this, Integer.class, b -> b
            .playerScoped("volume")
            .label(Component.text("Volume"))
            .description(Component.text("Demo sound volume"))
            .codec(PreferenceCodec.integerSlider(0, 100, 5))
            .defaultValue(70));

        Preference<Weather> weather = prefs.register(this, Weather.class, b -> b
            .playerScoped("weather")
            .label(Component.text("Weather"))
            .description(Component.text("Preferred demo weather"))
            .codec(PreferenceCodec.enumerated(Weather.class, w -> Component.text(w.name().toLowerCase())))
            .defaultValue(Weather.SUNNY));

        Preference<String> nickname = prefs.register(this, String.class, b -> b
            .playerScoped("nickname")
            .label(Component.text("Nickname"))
            .description(Component.text("Display name in demos"))
            .codec(PreferenceCodec.string(32))
            .defaultValue("Player"));

        Preference<Boolean> announce = prefs.register(this, Boolean.class, b -> b
            .global("announce_logins")
            .label(Component.text("Announce Logins"))
            .description(Component.text("Broadcast join messages"))
            .codec(PreferenceCodec.booleanBox())
            .defaultValue(false));

        getLogger().info("Registered " + 5 + " demo preferences; notifications default="
            + notifications.defaultValue());
    }
}
```

- [ ] **Step 2: Build both jars**

Run: `./gradlew build --console=plain`
Expected: `build/libs/preferences-0.1.0-SNAPSHOT.jar` and `demo/build/libs/demo-0.1.0-SNAPSHOT.jar` exist.

- [ ] **Step 3: Provision a local Paper 1.21.7 server**

```bash
mkdir -p run/plugins
curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/1.21.7/builds/latest" -o /tmp/latest.json
URL=$(python3 -c "import json;d=json.load(open('/tmp/latest.json'));print(d['downloads']['application:shaded']['url'])")
curl -fsSL "$URL" -o run/paper.jar
echo eula=true > run/eula.txt
cp build/libs/preferences-*.jar run/plugins/
cp demo/build/libs/demo-*.jar run/plugins/
```
Expected: jars copied into `run/plugins/`.

- [ ] **Step 4: Start server and drive the smoke test**

Start the server as a supervised process (hub `start`, ready.log `Done`), then in the server console:
1. Join with a client (or use the server console `dialog show` is NOT sufficient — dialog interaction requires a real client; use a local Minecraft 1.21.7 client).
2. Run `/preferences`: list dialog opens with the 4 player prefs.
3. Edit each: toggle Notifications (checkbox), move Volume slider to a new value, pick a different Weather, type a Nickname. Save each; confirm the green "Saved" message and return to the list.
4. Run `/preferences global` as an op player: Announce Logins appears; toggle it; save.
5. Run `/preferences global` as a NON-op player: expect the red permission message.
6. Verify YAML: `run/plugins/Preferences/data/preferencesdemo.yml` contains the values after the 5s flush window.
7. Stop the server; confirm the file is complete (disable-path flush). Restart; run `/preferences`; all values must show the saved values.

Expected: every step passes. Record outcomes; any failure → fix and repeat from the relevant step.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "test: demo plugin + smoke test procedure verified on Paper 1.21.7"
```

---

## Post-plan notes

- The Dialog API is `@Experimental`; when Paper bumps past 1.21.7, recompile and re-run the Task 8 smoke test — signature drift is isolated to `DialogFactories`.
- Future SPI work (custom storage backends, chat input fallback) grows from the `ValueStore` boundary and `DialogInputAdapter` seam without breaking hooking plugins.
