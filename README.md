# EMBERFALL

A 3D roguelike for Minecraft (Fabric, Minecraft 1.21.11; compiles to Java 21 bytecode; building needs JDK 25). Pick a character, enter the Expedition Gate, survive waves,
collect tomes, weapons and relics, open chests, trade with Testificates, defeat the bosses, and choose when to cash out.

## Layout

| Path | What |
|---|---|
| `src/` | The mod (Java, assets, data). Builds with `./gradlew build`. |
| `tools/testbot/` | Headless end-to-end tests. Each `*_test.js` joins a real server as a client and asserts behaviour. |
| `tools/mapwork/` | Scripts that generate the arena map data. |
| `docs/` | Design docs, research and the playtest report. Read `docs/main.md` first. |
| `AGENTS.md` | Rules for AI agents working in this repo. **Read before editing.** |
| `docs/VESPER_HANDBOOK.md` | Full onboarding and assignments for the collaborator agent (Vesper). |
| `WORKFLOW.md` | How humans and both agents share work. |
| `STATUS.md` | What is done, in progress and blocked. Updated by whoever finishes something. |

## Build

```
./gradlew build          # jar lands in build/libs/
```

## Check a fresh clone in one command

```
export JAVA_HOME=/path/to/jdk-25
bash tools/fresh_clone_check.sh
```

It builds, runs the pure-maths checks, sets up and starts the test server (fetching the Fabric launcher itself), then runs
`relic_test` and `chest_test`. Each of the 8 steps ends `PASS`, `FAIL` or `NOT RUN`; the exit code is 0 only if all 8 are `PASS`.
Suite verdicts are read from the output (result line, PASS count, no FAIL), never from an exit code, because the live suites
exit 0 even when they did nothing. Raw output goes to a folder under `/tmp` (set `FCC_LOG` to choose). `FCC_SKIP_BUILD=1`
reuses the existing jar and reports the build as `NOT RUN`. Port 25565 must be free. Tested headless, look unverified.

## Run the tests

```
export EMBERFALL_HOME=$(pwd) JAVA_HOME=/path/to/jdk-25
bash tools/setup.sh                       # one time: test server + node modules
bash tools/testbot/one_suite.sh relic_test 200   # build, fresh world, run ONE suite
cat /tmp/one_relic_test.txt               # ends with ONE_DONE
```

Test output must show `PASS`/`FAIL` lines. A suite that prints nothing has proven nothing.
