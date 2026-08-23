# Preferences GUI Menu and Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat preference dialog with a plugin menu, plugin-scoped lists, click-to-run search, and safe navigation for both player and global preferences.

**Architecture:** Keep preference querying in a Paper-independent `PreferenceCatalog` that returns namespace and preference-key data only. Paper-facing `DialogScreens` resolves namespace display names through an injected resolver, renders dialogs, and stores immutable snapshots in `DialogSession`. `ClickRouter` accepts only live sessions and opaque index-based action IDs, resolving every item against the exact snapshot that was rendered.

**Tech Stack:** Java 25 (`options.release = 25`), Gradle 9.6.1 wrapper, Paper API 26.2+, Adventure Components, Paper Dialog API, JUnit 5, Mockito.

## Global Constraints

- `/prefs` and `/preferences` always open the home menu in the MVP, including when only one plugin has preferences.
- Do not add `skip-home-when-single-plugin` until shortcut behavior is implemented, validated, documented, and tested.
- Keep only the existing `gui.page-size` configuration for this feature.
- Search is click-to-update; Paper dialogs do not provide live keystroke callbacks.
- Search matches namespace, preference name, label text, and description text.
- The same navigation model applies to player and global scopes.
- Public API and YAML storage formats do not change.
- Never embed namespace, plugin name, or preference name in an Adventure action key.
- Every rendered list stores an immutable displayed-item snapshot in the active session.
- A click resolves against the session snapshot; it never recomputes a possibly changed registry list.
- `PreferenceCatalog` must not depend on Bukkit, Paper, or plugin objects.
- Display-name resolution belongs in `DialogScreens` or another Paper-injected resolver.
- Every click requires a live session and re-checks the relevant permission.

## File Map

- Modify `preferences-common/src/main/java/dev/mintychochip/preferences/internal/session/DialogSession.java`: navigation screen, scope, parent context, and immutable displayed snapshots.
- Create `preferences-common/src/main/java/dev/mintychochip/preferences/internal/PreferenceCatalog.java`: deterministic, scope-aware namespace grouping and text search over registered preferences.
- Create `preferences-common/src/test/java/dev/mintychochip/preferences/internal/PreferenceCatalogTest.java`: catalog behavior tests.
- Modify `preferences-common/src/test/java/dev/mintychochip/preferences/internal/session/DialogSessionManagerTest.java`: session snapshot and namespace-close behavior.
- Modify `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogFactories.java`: opaque action-key factories and search-input dialog construction.
- Modify `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java`: home, plugin list, search input, search results, pagination, and edit-parent rendering.
- Modify `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/ClickRouter.java`: opaque action handling, search submission, navigation, stale-target checks, and permission checks.
- Modify `preferences-paper/src/main/java/dev/mintychochip/preferences/PreferencesPlugin.java`: construct the catalog, inject the Paper display-name resolver, and preserve `gui.page-size`.
- Modify `preferences-paper/src/test/...` if an existing Paper dialog test source set is available; otherwise cover routing through pure/session tests and run the Paper smoke server.
- Modify `README.md` and `AGENTS.md`: document the menu and click-to-search UX.

---

### Task 1: Add immutable navigation session state

**Files:**
- Modify: `preferences-common/src/main/java/dev/mintychochip/preferences/internal/session/DialogSession.java`
- Modify: `preferences-common/src/main/java/dev/mintychochip/preferences/internal/session/DialogSessionManager.java`
- Test: `preferences-common/src/test/java/dev/mintychochip/preferences/internal/session/DialogSessionManagerTest.java`

**Interfaces:**
- Produces `DialogSession.Screen` values: `HOME`, `PLUGIN_LIST`, `SEARCH_INPUT`, `SEARCH_RESULTS`, `EDIT`.
- Produces immutable session fields for `PreferenceScope`, nullable namespace/query/edit target, parent context, `List<PreferenceKey> displayedItems`, and `List<String> displayedNamespaces`.
- Existing callers must migrate from `kind()` to the new screen/scope fields; do not retain a parallel legacy session model.

- [ ] **Step 1: Write tests for immutable snapshots and parent context**

Construct a session with mutable source lists, mutate the sources, and assert the session lists do not change. Assert nullable edit targets are accepted only for non-edit screens or according to the chosen constructor invariant. Assert `closeForNamespace` closes sessions whose edit target belongs to the namespace while leaving unrelated home/list sessions open.

- [ ] **Step 2: Run the focused session tests and verify failure**

Run:

```bash
./gradlew :preferences-common:test --tests '*DialogSessionManagerTest' --console=plain
```

Expected: FAIL because the new session fields and constructors do not exist yet.

- [ ] **Step 3: Implement the session record and manager migration**

Use compact immutable records for parent context and snapshots. Copy incoming lists with `List.copyOf`. Keep the existing one-session-per-player invariant. Ensure edit sessions retain the parent screen, scope, namespace, query, and originating page so save/cancel can return to the correct screen.

- [ ] **Step 4: Run focused tests**

Run the same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add preferences-common/src/main/java/dev/mintychochip/preferences/internal/session preferences-common/src/test/java/dev/mintychochip/preferences/internal/session
git commit -m "feat: add immutable preference dialog navigation sessions"
```

### Task 2: Implement the Paper-independent preference catalog

**Files:**
- Create: `preferences-common/src/main/java/dev/mintychochip/preferences/internal/PreferenceCatalog.java`
- Create: `preferences-common/src/test/java/dev/mintychochip/preferences/internal/PreferenceCatalogTest.java`

**Interfaces:**
- Constructor consumes `PreferenceRegistry` and no Bukkit/Paper resolver.
- `List<String> namespaces(PreferenceScope scope)` returns sorted namespaces containing at least one preference in the scope.
- `List<PreferenceKey> preferencesForNamespace(PreferenceScope scope, String namespace)` returns sorted keys.
- `List<PreferenceKey> search(PreferenceScope scope, String query)` returns sorted keys matching namespace/name and catalog-owned searchable text.
- Catalog results contain keys and registered preference data only; display names are not resolved here.

- [ ] **Step 1: Write catalog tests**

Cover player/global scope filtering, namespace grouping, deterministic key sorting, case-insensitive matching, matching namespace/name, matching visible label and description text, empty registry results, and a query containing only whitespace.

Use Adventure’s `PlainTextComponentSerializer` inside the catalog for component text extraction; this dependency already exists in the common module through the API/Paper compile setup. Do not add a Bukkit plugin lookup to the catalog.

- [ ] **Step 2: Run focused tests and verify failure**

```bash
./gradlew :preferences-common:test --tests '*PreferenceCatalogTest' --console=plain
```

Expected: FAIL because `PreferenceCatalog` does not exist.

- [ ] **Step 3: Implement catalog queries**

Snapshot `registry.all()` at query time, filter by scope, group namespace strings, and sort by `PreferenceKey.asString()`. Search against lowercased namespace, name, serialized label, and serialized description. Return `List.copyOf` results. Treat null/blank query as no filtering only if the caller explicitly requests all results; otherwise return an empty search result for blank input.

- [ ] **Step 4: Run focused tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add preferences-common/src/main/java/dev/mintychochip/preferences/internal/PreferenceCatalog.java preferences-common/src/test/java/dev/mintychochip/preferences/internal/PreferenceCatalogTest.java
git commit -m "feat: add scoped preference catalog and search"
```

### Task 3: Add opaque dialog action factories

**Files:**
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogFactories.java`

**Interfaces:**
- `Key itemKey(int index)` produces `preferences:item/<index>`.
- `Key pluginKey(int index)` produces `preferences:plugin/<index>`.
- Add fixed keys for `home_search`, `search_run`, `back`, and existing pagination/save/cancel actions.
- No factory accepts namespace, plugin name, or preference name as part of an action key.

- [ ] **Step 1: Add key-format tests if the Paper module has a unit test source set**

Assert generated keys contain only fixed path segments and decimal indexes. If no suitable Paper test source set exists, use the compile-time implementation and cover key behavior through the router tests/smoke path in Task 6.

- [ ] **Step 2: Implement factories**

Reject negative indexes with `IllegalArgumentException`. Keep action namespaces fixed at `preferences`.

- [ ] **Step 3: Compile the Paper module**

```bash
./gradlew :preferences-paper:compileJava --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogFactories.java
git commit -m "feat: add opaque preference dialog action keys"
```

### Task 4: Render the home and plugin-list screens

**Files:**
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java`
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/PreferencesPlugin.java`

**Interfaces:**
- `DialogScreens` consumes `PreferenceCatalog`, `DialogSessionManager`, `pageSize`, and a Paper-side `Function<String, Component>` namespace display resolver.
- `showPlayerHome(Player, int)` and `showGlobalHome(Player, int)` render `HOME`.
- `showPluginList(Player, PreferenceScope, String namespace, int page, ParentContext)` renders `PLUGIN_LIST`.
- Every list render stores the exact visible `displayedItems` or `displayedNamespaces` snapshot before showing the dialog.

- [ ] **Step 1: Define render-level test cases**

Document and, where existing test infrastructure permits, test: home buttons map to namespace snapshots; plugin-list buttons map to preference-key snapshots; one-plugin home still renders; empty scope renders a notice; pagination stores only the current page’s items.

- [ ] **Step 2: Implement Paper-side namespace display resolution**

Pass a resolver from `PreferencesPlugin`, backed by Bukkit’s plugin manager. Resolve a namespace case-insensitively to the actual plugin name and fall back to a formatted namespace when unavailable. Keep this logic out of `PreferenceCatalog`.

- [ ] **Step 3: Implement HOME rendering**

Query catalog namespaces for the requested scope, paginate them, store `displayedNamespaces`, create `plugin/N` buttons, add a fixed Search button, and add Close. Always render home even for one namespace. Keep only `gui.page-size`; do not add an unused shortcut config.

- [ ] **Step 4: Implement PLUGIN_LIST rendering**

Query catalog keys for scope + namespace, paginate, store the current page in `displayedItems`, create `item/N` buttons, and include Back, pagination, and Close. Render a notice for an empty namespace rather than passing an empty action list to `multiAction`.

- [ ] **Step 5: Compile and run relevant tests**

```bash
./gradlew :preferences-paper:build :preferences-common:test --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java preferences-paper/src/main/java/dev/mintychochip/preferences/PreferencesPlugin.java
git commit -m "feat: add plugin grouped preference menu"
```

### Task 5: Implement search input and results screens

**Files:**
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogFactories.java`
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java`

**Interfaces:**
- `showSearchInput(Player, PreferenceScope, ParentContext, String initialQuery)` renders a text input whose input key is a fixed vanilla-safe value such as `query`.
- `showSearchResults(Player, PreferenceScope, String query, int page, ParentContext)` stores visible result keys in `displayedItems`.
- Search execution reads the fixed `query` field only when the player clicks `search_run`.

- [ ] **Step 1: Add search dialog construction**

Build a confirmation dialog with one `query` input, Search action, and Cancel action. Do not use namespace-qualified input keys.

- [ ] **Step 2: Add result rendering**

Use `PreferenceCatalog.search`, paginate the returned keys, decorate each result with the Paper-side namespace resolver and registered preference label, and store the exact visible key slice in the session. Render a no-results notice for empty results.

- [ ] **Step 3: Compile the Paper module**

```bash
./gradlew :preferences-paper:compileJava --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogFactories.java preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java
git commit -m "feat: add click-to-search preference results"
```

### Task 6: Migrate click routing and edit return paths

**Files:**
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/ClickRouter.java`
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java`
- Modify: `preferences-paper/src/main/java/dev/mintychochip/preferences/internal/command/PreferencesCommand.java`

**Interfaces:**
- Router handles fixed actions only: `plugin/N`, `item/N`, `home_search`, `search_run`, `back`, pagination, save, cancel.
- `item/N` resolves through `session.displayedItems()` and then `registry.byKey(key)`.
- Router never sorts or reconstructs a registry list to resolve an action.

- [ ] **Step 1: Write routing tests or deterministic test fixtures**

Cover: item mapping from plugin list, item mapping from search results, stale/unregistered target no-op or notice, plugin mapping from home snapshot, search submission, back transitions, pagination preserving scope, and permission loss between render and click.

- [ ] **Step 2: Replace legacy list navigation**

Migrate command entry points from `showPlayerList`/`showGlobalList` to home methods. Preserve `/prefs global` permission behavior and `/prefs` alias behavior.

- [ ] **Step 3: Implement opaque item/plugin resolution**

Parse only decimal indexes after fixed prefixes. Reject malformed or out-of-range indexes. Resolve keys/namespaces from the session’s immutable snapshots. Confirm the registry still contains the key and that its scope matches the session before opening edit.

- [ ] **Step 4: Implement search submission**

Read `query` from `DialogResponseView`, enforce the configured adapter’s maximum length, and call `showSearchResults` with the current scope and parent context. Empty/blank queries produce the documented no-results or all-results behavior consistently with `PreferenceCatalog` tests.

- [ ] **Step 5: Implement Back and edit returns**

Back returns HOME from a plugin list, SEARCH_INPUT from results, and the recorded parent from edit. Save/cancel reopen the parent with fresh snapshots. Re-check `preferences.use` or `preferences.manage` before every transition.

- [ ] **Step 6: Run focused build/tests**

```bash
./gradlew :preferences-api:test :preferences-common:test :preferences-paper:build --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/ClickRouter.java preferences-paper/src/main/java/dev/mintychochip/preferences/internal/dialog/DialogScreens.java preferences-paper/src/main/java/dev/mintychochip/preferences/internal/command/PreferencesCommand.java
git commit -m "feat: route grouped and searched preference dialogs"
```

### Task 7: Document the feature and run complete verification

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Document user behavior**

Explain that `/prefs` opens a plugin menu, plugin buttons open scoped preference lists, Search requires clicking Search to refresh results, and `/prefs global` uses the same flow with manage permission.

- [ ] **Step 2: Document constraints**

State that search is not live/as-you-type because Paper dialogs submit inputs through actions. Document that the MVP always shows HOME and has no shortcut configuration. Do not document a nonexistent `skip-home-when-single-plugin` key.

- [ ] **Step 3: Run full verification**

```bash
./gradlew --no-daemon --stacktrace ci
./scripts/verify-maven-publish.sh
```

Expected: both commands complete successfully.

- [ ] **Step 4: Run the Paper smoke server**

```bash
./gradlew :test:runServer
```

Exercise `/prefs`, plugin selection, Back, Search, search results, edit/save, `/prefs global`, and permission denial. Stop the server after verification.

- [ ] **Step 5: Commit documentation**

```bash
git add README.md AGENTS.md
git commit -m "docs: document grouped preference navigation and search"
```

## Final Verification Checklist

- [ ] Home always appears for `/prefs`, including one-plugin installations.
- [ ] Home plugin buttons resolve through immutable namespace snapshots.
- [ ] Plugin lists resolve through immutable preference-key snapshots.
- [ ] No raw namespace/name appears in an Adventure action key.
- [ ] Catalog has no Bukkit/Paper dependency or plugin-name resolution.
- [ ] Display-name resolution occurs in the Paper layer.
- [ ] Search matches namespace, name, label, and description.
- [ ] Search updates only after clicking Search.
- [ ] Player and global scopes share the same navigation.
- [ ] Permissions are rechecked on every click transition.
- [ ] Save/cancel/back return to the correct parent screen.
- [ ] Only `gui.page-size` is used; no unused shortcut setting exists.
- [ ] `./gradlew --no-daemon --stacktrace ci` passes.
- [ ] `./scripts/verify-maven-publish.sh` passes.
- [ ] Paper smoke flow passes.
