# Package / Group Rename — Design

**Date:** 2026-08-17
**Status:** Approved

## Goal

Rename the Maven group and Java package root from `dev.jlo` to `dev.mintychochip`, and bump the project version to `0.2.0` to signal the breaking public-API change.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| New package root | `dev.mintychochip.preferences` | Mirrors the existing `dev.jlo.preferences` root and the GitHub identity `mintychochip`. |
| New Maven group | `dev.mintychochip` | Same as the package root's top-level domain. Keeps the published coordinate consistent. |
| New version | `0.2.0` | The package move breaks imports for consumers; pre-1.0 minor bump is the standard signal. |
| GitHub Packages URL | `https://maven.pkg.github.com/aincraft-org/preferences` | Matches the repository's actual remote (`origin`) and avoids the stale `mintychochip/Preferences` path. |
| Scope of files | All tracked files | Source, tests, build files, `plugin.yml`, scripts, README, AGENTS.md, and the `docs/superpowers/` plan/spec all updated so no stale `dev.jlo` references remain. |

## Files to change

- `build.gradle.kts` — `group` and `version`.
- `api/build.gradle.kts`, `paper/build.gradle.kts`, `common/build.gradle.kts`, `test/build.gradle.kts` — review/confirm no hard-coded `dev.jlo`.
- All Java source and test packages under `*/src/*/java/dev/jlo/preferences` — package declarations, imports, and physical path.
- `paper/src/main/resources/plugin.yml` — `main:` class.
- `test/src/main/resources/plugin.yml` — `main:` class.
- `scripts/verify-maven-publish.sh` — consumer coordinate and Java imports.
- `README.md` and `AGENTS.md` — group, package, Maven/Gradle/XML snippets, GitHub Packages URL.
- `docs/superpowers/plans/2026-08-04-preferences-plugin.md` and `docs/superpowers/specs/2026-08-04-preferences-plugin-design.md` — historical package examples.

## Approach

1. `git mv` each `dev/jlo/preferences` directory tree to `dev/mintychochip/preferences`.
2. Use `ast_edit` to rewrite all `package dev.jlo.preferences...` and `import dev.jlo.preferences...` statements to `dev.mintychochip.preferences`.
3. Manually update the non-Java files listed above.
4. Run `./gradlew ci` to compile all modules and run unit tests.
5. Search for `dev\.jlo` across the repo to confirm no tracked references remain.
6. Commit as one atomic change.

## Verification

- `./gradlew ci` passes.
- `grep -R 'dev\.jlo'` (excluding `.git/`, `build/`, `.gradle/`) returns no matches.
- Consumer snippets show `dev.mintychochip:preferences-api:0.2.0`.
