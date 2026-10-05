# Suite verdict audit (issue #17), 2026-10-05

Read-only audit of `tools/testbot/*_test.js` (200 files) and `tools/testbot/*.sh` (39 files) at `86c6c83`. **Nothing was fixed.**

`suite_verdict_audit_2026-10-05.csv` has one row per `*_test.js`:
- `static_class`: from the source only (does it define a PASS/FAIL helper, does it print a total, does it call `process.exit` with a non-zero value). A regex scan, hand-checked on about 30 files; some rows may be wrong.
- `dead_server_*`: **measured.** Each file was run with `node <file>` from `tools/testbot` with **no Minecraft server listening on 25565** (port confirmed closed), 25 s cap. Exit code, and whether a `FAIL` / `PASS` line was printed.
- `can_false_pass`: `YES` means exit 0 and no FAIL line with no server at all.

Not covered: the 285 non-`_test.js` scripts (probes, setup, diag files), which are not suites; shell wrappers were read, not run except `regress.sh`.
Tested headless, look unverified.
