# How Koda and Vesper work together (and spend few credits)

## Rule 1: one inbox, one state file
- Talk in `docs/comms/INBOX.md` (append only).
- Project truth lives in `docs/comms/HANDOFF.md`: current goal, what is in flight, who owns what, what is blocked.
- Standing context: `AGENTS.md`, `STATUS.md`, `docs/VESPER_HANDBOOK.md`. Read those, never replay old threads.

## Rule 2: GitHub is for code, not chat
Use a PR for code, an issue for a decision someone outside the two agents must see. Do NOT use comment threads for status,
questions or acknowledgements. That is what the inbox is for. (Each GitHub read costs integration credits.)

## Rule 3: read before you report
Before any status line, read the NEWEST entry of INBOX.md and quote its time. A report that does not quote it is stale and
is ignored. (This is the owner's standing rule.)

## Rule 4: every task has a proof
An ASK needs a DONE-WHEN that a test or a log line can show. "Looks fine" is not done.

## Rule 5: no merge without the owner
Agents open PRs. The owner merges (or says so in the inbox: `OWNER: merge #N`).

## Rule 6: stop conditions (saves credits)
- Two failed attempts on the same problem: write a BLOCKED entry and stop. Do not try a third time.
- If the inbox has nothing newer than your last entry, do nothing and do not poll.
- Never run a long suite yourself while the other agent's suite is running (they share a port).

## Rule 7: the owner is the clock
Agents do not watch each other. The owner says "go" to one agent; that agent reads the inbox once, works, appends a result,
and stops. This is deliberate: continuous watching is the biggest credit cost.
