# EMBERFALL

A 3D roguelike for Minecraft (Fabric, Java 25, Minecraft 1.21.11). Pick a character, enter the Expedition Gate, survive waves,
collect tomes, weapons and relics, open chests, trade with Testificates, defeat the bosses, and choose when to cash out.

## Layout

| Path | What |
|---|---|
| `src/` | The mod (Java, assets, data). Builds with `./gradlew build`. |
| `tools/testbot/` | Headless end-to-end tests. Each `*_test.js` joins a real server as a client and asserts behaviour. |
| `tools/mapwork/` | Scripts that generate the arena map data. |
| `docs/` | Design docs, research and the playtest report. Read `docs/main.md` first. |
| `AGENTS.md` | Rules for AI agents working in this repo. **Read before editing.** |
| `WORKFLOW.md` | How humans and both agents share work. |
| `STATUS.md` | What is done, in progress and blocked. Updated by whoever finishes something. |

## Build

```
./gradlew build          # jar lands in build/libs/
```

## Run the tests

```
export EMBERFALL_HOME=$(pwd) JAVA_HOME=/path/to/jdk-25
bash tools/setup.sh                       # one time: test server + node modules
bash tools/testbot/one_suite.sh relic_test 200   # build, fresh world, run ONE suite
cat /tmp/one_relic_test.txt               # ends with ONE_DONE
```

Test output must show `PASS`/`FAIL` lines. A suite that prints nothing has proven nothing.
