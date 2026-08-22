# server-development-skills

Curated, verified skills for Minecraft/Paper server development. Agents working in this repository maintain and add skills; the skills themselves encode the conventions and workflows applied when building server projects.

## Skills

| Skill | Purpose |
|---|---|
| [project-setup](project-setup/SKILL.md) | Scaffold a Paper 26.2 plugin: pinned Gradle/run-paper/Spotless versions, executable Spotless/Checkstyle/PMD/SpotBugs quality gates via `./gradlew clean check`, CalVer versioning, plugin.yml, and the recommended multi-module Java layout |
| [ci-release](ci-release/SKILL.md) | CI and release engineering: GitHub Actions with mandatory static-analysis CI gates via `./gradlew clean check`, CalVer `YYYY.MM.DD.<github_run_number>` versioning, rolling nightly releases, stable releases, and public-project shields.io badges |
| [autonomous-testing](autonomous-testing/SKILL.md) | Autonomous Minecraft bots in Rust with Azalea: task classification, packet-based completion validation, security hardening, and testing |
| [database-integration](database-integration/SKILL.md) | Paper plugin persistence: HikariCP pooling, async SQLite/MySQL access, schema migrations |
| [performance-optimization](performance-optimization/SKILL.md) | Diagnose and fix Paper lag: Spark profiling, main-thread discipline, async chunk loading, listener hygiene |
| [docs-maintenance](docs-maintenance/SKILL.md) | End-user Fumadocs documentation for every repo: content/docs contract with a reduced fallback for libraries and internal tools, floor-to-ceiling page ladder (idea → basics → everyday → advanced), design-token styling, and an executable gate covering content, navigation, links, and styling |
| [spec-driven-development](spec-driven-development/SKILL.md) | Turn non-trivial feature requests into reviewed artifacts before code: triage → specify (`FR-xxx` requirements with acceptance criteria) → clarify → plan → tasks with inline requirement traceability → gated implementation, using the repo's dated `docs/superpowers/specs|plans` convention |

## Conventions

- One directory per skill: `<name>/SKILL.md`.
- Frontmatter: `name` plus a `description` written as trigger conditions ("Use when ..."), not a prose summary — the description is what makes the skill discoverable.
- Version pins must be verified against official docs before committing; record the verification date in the skill.
- Capture observed failures as a "Common mistakes" table in the skill.

## Usage

Skills are consumed by agent harnesses that load `SKILL.md` files (e.g. superpowers-style skill directories). Each skill is self-contained: read the skill, follow its pinned versions and conventions, and apply its verify commands before reporting completion.


## Contributing

To add or update a skill:

1. Create or edit `<name>/SKILL.md`.
2. Write the `description` as trigger conditions, not a summary.
3. Verify every version pin against official docs and record the verification date.
4. Include a "Common mistakes" table from observed failures.
5. Commit one skill change per commit.
