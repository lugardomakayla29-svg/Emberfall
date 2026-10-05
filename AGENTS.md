# Rules for AI agents working on EMBERFALL

You share this repo with another agent and two humans. The repo is the ONLY channel between you. Anything not written in a
commit, issue, PR or `STATUS.md` does not exist for the next agent.

## On arrival (every session)
1. `git pull`, then read `STATUS.md`, then `gh issue list` / open PRs. Find what is claimed before choosing work.
2. Claim a task by assigning the issue to your human and commenting `CLAIM <agent> <files you will touch>`. Never edit a file
   another open PR touches.
3. Work on a branch `<owner>/<topic>` (never commit to `main`). One topic per branch.

## Standing rules (from the owner)
- **No half-baked features.** Ship a feature complete, or do not open the PR.
- **Reproduce before fixing.** A bug PR must include the failing output (test, log line, or bytecode finding). No guessing.
- **Measure, do not assume.** Performance claims need numbers (ms/tick, entity counts). Prefer zero or minimal entities.
- **Deep-dive engine mechanics first** (read the decompiled method, not memory of it) before building on an API.
- **Anti-tamper on every custom PDC-tagged item.** Custom mobs mirror player behaviour and stay off the tab list.
- A test that prints no verdict is not a test. A suite you write or touch must print `PASS`/`FAIL` lines and a total, so that `grep FAIL` is meaningful. Not every existing suite meets this yet: 56 of 200 are measurements and 17 print no total (issue #17). An exit code is not a verdict.
- Do not loosen an assertion unless the measured numbers show the test was wrong. Say so in the PR.

## Definition of done (PR checklist)
- `./gradlew build` passes.
- The suite(s) you touched pass, and the regression list in the PR description passes with **0 exceptions** in the server log.
- New behaviour has a live test in `tools/testbot/`. Pure maths has a `*Check.java` under `tools/testbot/relic_math/`.
- `STATUS.md` updated. Patch note line added to `docs/WHATS_NEW.md`.
- State plainly what is **unverified** (e.g. "look on a real client not checked").

## Do not
- Commit jars, worlds, logs, tokens, or `run/`.
- Force-push, rewrite history, or merge your own PR without the other side's review when the change touches shared systems
  (`WaveDirector`, `RunManager`, `relic/`, `tome/`, networking payloads).
- Start a server flow that blocks longer than your tool limit: launch it detached and poll.

## Shared systems (conflict hotspots: announce in the issue before touching)
`world/RunManager`, `wave/WaveDirector`, `combat/AutoAttackSystem`, `EmberfallMod` (registration), `lang/en_us.json`, any
network payload. Register new things by adding a NEW line/file, never by reformatting an existing block.

## Agents on this project
- **Koda**: the repo owner's agent (lead). Owns `tools`-side EmberTester (`bot/`), reviews all Vesper PRs.
- **Vesper**: the collaborator's agent. Start with `docs/VESPER_HANDBOOK.md`; it has your assignments (V0 to V5).
