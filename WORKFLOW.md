# Working together: two people, two agents, one repo

```
 owner's agent ──┐                       ┌── collaborator's agent
   (Koda)        ├──►  GitHub (issues, ◄─┤
 owner ──────────┘      PRs, STATUS.md)  └── collaborator
```
Agents cannot message each other. GitHub is the message bus, so every hand-off is an issue comment, a PR, or a line in
`STATUS.md`.

## The loop
1. **Plan in issues.** One issue per deliverable, labelled `area:*` (mobs, weapons, relics, map, bot, hud) and
   `status:ready|claimed|blocked|review`. The issue body states the goal and the acceptance test.
2. **Claim.** Comment `CLAIM` + the files you will touch. If a file is already claimed, pick another issue or ask in the thread.
3. **Branch and build.** `<owner>/<topic>`. Commit small. Push early so the other side can see the direction.
4. **Open a PR** with the template (what, proof, unverified, regression). CI builds the jar on every push.
5. **Cross-review.** The OTHER side's agent reviews (reads the diff, runs the suite, comments). The owner of the area approves.
6. **Merge** by squash. Delete the branch. Update `STATUS.md`. Close the issue with the PR link.

## Splitting the work so you do not collide
Give each side whole areas, not whole files. Suggested split: one side owns *content* (mobs, bosses, weapons, relics, map),
the other owns *systems* (party and gate, multiplier/endgame, bot, HUD, packaging). Shared files are listed in `AGENTS.md`.

## Hand-offs between agents
Write them as a comment on the issue, in this shape, so the next agent needs no other context:
```
STATE: done | blocked | needs-review
DONE: <what works, with the test that proves it>
NOT DONE: <what is missing>
UNVERIFIED: <what was not checked>
NEXT: <the one next step>
FILES: <paths touched>
```

## Cadence
- Start of day: pull, read `STATUS.md` + open PRs. End of day: update `STATUS.md`, push.
- Releases: tag `v0.1.x` on `main`; CI attaches the jar to the GitHub Release so testers download one file.

## Turning on CI
The build + maths-check workflow is saved as `docs/ci/build.yml.txt` because the agent's GitHub token cannot create files under
`.github/workflows/`. A human adds it once: copy it to `.github/workflows/build.yml` (GitHub web UI: Add file, paste, commit).
