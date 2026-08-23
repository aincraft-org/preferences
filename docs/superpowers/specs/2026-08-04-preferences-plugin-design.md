# Preferences Plugin — Design

**Date:** 2026-08-04
**Status:** Approved

## Goal

A Paper plugin that owns *preferences* as a first-class concept. Other plugins hook in, declare typed preferences, and get GUI and state management for free: this plugin renders native Minecraft dialogs for editing, persists values, and enforces permissions. Hooking plugins write zero UI code.

## Platform decisions

| Decision | Choice | Rationale |
|---|---|---|
| Target API | Paper 26.2+ (`paper-api 26.2.build.+`), Java 21 | Dialog API (`io.papermc.paper.dialog`) is available in Paper 26.2; Java 21 is the matching server baseline. Dialog API is `@Experimental` — accept churn risk. |
| Input surface | Native Dialog API only | Dialogs cover all four client input controls (checkbox, slider, option picker, text field). Chat capture is deferred. |
| Scope per preference | Per-preference declaration: player-scoped or server-global | Covers both personal settings and server-wide knobs. |
| Access control | Player-scoped: owning player edits own values. Global: `preferences.manage` (default op) required | "If it's global it's likely the admin." |
| Persistence | This plugin stores everything (YAML, per-hooking-plugin file). Storage sits behind an internal `ValueStore` interface so a custom-store SPI can be added later without public API breaks | Hooking plugins stay stateless; escape hatch preserved. |
| Type conversion | Codecs supplied by hooking plugins | Per the requirement: *input → preference type → stored pref* requires plugin-supplied conversion. |

## Public API (hooking plugins)

Hooking plugins declare `depend: [Preferences]` in their `plugin.yml` and load the service:

```java
PreferencesService prefs = Bukkit.getServicesManager()
    .load(PreferencesService.class);
```

### Codec layers

Two distinct conversions, two interfaces, plus a facade:

```java
/** Required. Preference type <-> stored string. */
public interface StorageCodec<T> {
    T parse(@NotNull String stored) throws ParseException;
    @NotNull String write(@NotNull T value);
}

/** Optional. Preference type <-> dialog input/response. */
public interface DialogInputAdapter<T> {
    /** Builds the dialog input control pre-filled with the current value. */
    @NotNull DialogInput buildInput(@NotNull String inputKey, @NotNull T current);
    /** Parses the dialog response back into a value; null if absent/invalid. */
    @Nullable T parseResponse(@NotNull DialogResponseView response, @NotNull String inputKey);
}

/** Convenience facade bundling both for the common case. */
public record PreferenceCodec<T>(@NotNull StorageCodec<T> storage,
                                 @NotNull DialogInputAdapter<T> input) {}
```

- **No `DialogInputAdapter`** → the preference is persisted and programmatically settable, but read-only in the GUI (displayed as current value). This is the representation for types dialogs cannot edit (e.g. `ItemStack` — vanilla dialogs have no item picker input; `DialogBody.item` is display-only).
- **Built-in codecs** (static factories): `STRING`, `BOOLEAN`, `INTEGER`, `LONG`, `FLOAT`, `DOUBLE`, `ENUM` factory, and convenience dialog adapters: checkbox (boolean), slider with min/max/step (numbers), single-option picker (enums/string choices), text field (strings).

### Registration

Registration returns a typed handle — no stringly-typed lookups for consumers:

```java
Preference<Integer> drawDistance = prefs.register(myPlugin, builder -> builder
    .playerScoped("draw_distance")
    .label(Component.text("Draw Distance"))
    .description(Component.text("Chunk render distance for you"))
    .codec(PreferenceCodec.integer(2, 16, 1)) // slider 2–16, step 1
    .defaultValue(8));

int val = drawDistance.get(player);
drawDistance.set(player, 12); // validates, fires event, caches, queues async persistence
```

Rules:

- Keys are namespaced per registering plugin (`<plugin>:<name>`); duplicate registration → `IllegalStateException`.
- `builder.global(name)` instead of `playerScoped(name)` declares a server-global preference.
- Registration is open from our `onEnable` onward (dynamic registration supported).

### Programmatic access

`Preference<T>` handle exposes:

- `T get(Player)` / `T getGlobal()` (scope-dependent; wrong-scope accessor throws)
- `void set(Player, T)` / `void setGlobal(T)` — validates, fires cancellable event, updates cache, marks file dirty for async persistence
- `void reset(Player)` / `void resetGlobal()` — back to default
- Metadata accessors (label, description, default)

### Events

- `PreferenceChangeEvent` (Bukkit event, cancellable, fired before persistence): carries preference key, old value, new value, and editor (`Player` for player-scoped; `Console`/admin player for global).
- Optional per-preference callback (`Consumer<PreferenceChange>` on the builder) for plugins that don't want Bukkit event listeners.

## GUI and state management

### Navigation — dialogs all the way down

- **`/preferences`** (player, `preferences.use`, default true): `multi_action` dialog listing every plugin's player-scoped preferences, grouped by owning plugin. Each button: label; tooltip shows description + current value. Click → edit dialog.
- **`/preferences global`** (`preferences.manage`, default op): same layout for global preferences.
- **Edit dialog**: built from the preference's `DialogInputAdapter`, pre-filled with the current value (checkbox, slider, option picker, text field). Footer: `Save` + `Cancel`. Save → validate → persist → confirmation message → back to list. Read-only prefs (no adapter) show current value with a `Close` button.
- Pagination: vanilla `multi_action` lists scroll, but entries per dialog are capped (config `gui.page-size`, default 20) with prev/next navigation for long lists. Page position lives in the session, never in click payloads.

### Click routing — session-validated

`PlayerCustomClickEvent` also fires for plain chat click events, so dialog responses are never trusted blind:

1. Every dialog we open records a `DialogSession` (player UUID + session id) in an in-memory map.
2. `Save`/navigation buttons use our namespaced click keys; incoming events are matched to a live session for that player. No session → silently ignored.
3. Input values are read via `getDialogResponseView()` keyed by input `key` (Paper's docs guarantee nothing — we validate; missing/invalid → error message + reopen dialog with current value).
4. Sessions purge on `PlayerQuitEvent` and on dialog completion.

### State

In-memory value cache per (preference, player/global), loaded lazily from storage on first access. Write path: validate → fire cancellable `PreferenceChangeEvent` → abort if cancelled → update cache → mark file dirty → invoke per-preference callback. All dialog/state handling runs on the main thread; persistence is async (below).

## Storage

`plugins/Preferences/data/<namespace>.yml` — one file per hooking plugin:

```yaml
global:
  announce_logins: "true"
players:
  4b5f...uuid:
    draw_distance: "12"
```

Values are `StorageCodec.write(T)` strings — human-readable and migration-safe.

### Write lifecycle — no main-thread file I/O

YAML serialization and file writes never block the main thread:

1. **Change path (main thread):** update the in-memory cache, mark the owning plugin's file snapshot dirty. No serialization, no I/O.
2. **Debounced async flush:** a single scheduler coalesces dirty files on a trailing window (default 5s, configurable `storage.flush-seconds`). When the window elapses, the main thread takes a cheap snapshot of the value maps (values are immutable strings), hands it to an async executor, which serializes YAML and writes via temp file + atomic move (`Files.move(ATOMIC_MOVE)`).
3. **Disable path:** `onDisable` cancels pending timers, snapshots all dirty files, and writes them **synchronously**, blocking until every write completes before the method returns. No data loss across clean shutdowns or `/reload`.
4. **Crash window:** a hard crash can lose at most one debounce window of changes. Acceptable for preferences; documented.

### Load behavior

- Global values load at plugin enable; player values lazily on first access, cached in memory.
- Parse failure on load → warn in log + fall back to default; the corrupt entry is rewritten on next successful flush.
- Unregistration (hooking plugin disabled/removed) never deletes data files — values survive re-enable.

The storage layer sits behind an internal `ValueStore` interface (snapshot in / persist out) so a custom-store SPI can be added later without breaking the public API.

## Permissions

| Node | Default | Grants |
|---|---|---|
| `preferences.use` | true | Open `/preferences`, edit own player-scoped preferences |
| `preferences.manage` | op | Open `/preferences global`, edit global preferences |

Player-scoped values are only editable by the owning player; no permission can grant cross-player edits in v1.

## Lifecycle and error handling

- Hooking plugin `PluginDisableEvent`: unregister its preferences, kill its open sessions. Data files remain untouched (pending writes flush first).
- Our `onDisable`: synchronous final flush of all pending snapshots (see Storage).
- Duplicate preference key → `IllegalStateException` at registration time.
- Forged/stale dialog clicks (no matching session) → silently ignored.
- Invalid dialog response (missing key, unparseable value) → player sees an error message; dialog reopens with the unchanged current value.

## Out of scope for v1 (documented future work)

- Chat input fallback (dialog API covers all four input types; deprioritized per owner).
- Per-player overrides of global preferences.
- Custom storage backends (SPI escape hatch is preserved in the internal design).
- Item-typed preferences with dialog editing (no vanilla item-picker input exists; item prefs are supported as read-only-in-GUI + programmatic).

## Verification

- JUnit 5 unit tests for the Bukkit-free core:
  - Codecs: round-trip every built-in (`write(parse(x)) == x`), parse-failure cases.
  - Storage: snapshot/flush lifecycle — debounced coalescing writes once per window, disable path flushes everything synchronously, parse-failure fallback to default.
  - Session validation: clicks without a live session are rejected; expired/quit sessions purge.
- Smoke test on a real local Paper 26.2 server with a demo plugin registering bool / number / enum / text preferences, exercised through the actual dialogs: open list, edit each type, save, verify persisted YAML (after flush window), restart, verify reload.
