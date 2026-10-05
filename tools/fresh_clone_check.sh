#!/bin/bash
# One command from a clean clone to a result:   bash tools/fresh_clone_check.sh
#
# Steps: 1 tools, 2 JDK 25, 3 build, 4 pure maths checks, 5 test-server setup, 6 start server, 7 relic_test, 8 chest_test.
# Every step ends as PASS, FAIL or NOT RUN (with the reason). The summary counts them and the exit code is 0 ONLY when every
# step is PASS. A step that did not run is never counted as a pass.
#
# Verdicts are read from the OUTPUT, never from an exit code: the live suites exit 0 even when they did nothing
# (issue #17 measured 191 of 200 exiting 0 with no server at all). A suite counts as PASS only if it printed its RESULT line
# AND enough PASS lines AND no FAIL line.
#
# Environment (all optional):
#   JAVA_HOME          a JDK 25 (needed to BUILD; the mod itself targets Java 21). Else `java` on PATH must be 25.
#   EMBERFALL_HOME     the checkout (default: the directory above this script).
#   FCC_LOG            where raw output goes (default: a fresh folder under /tmp).
#   FCC_SKIP_BUILD=1   reuse build/libs/emberfall-*.jar (reported as NOT RUN for the build, so the run is not all-green).
# Not tested from a cold machine: the first Minecraft/Gradle download (several minutes, ~600 MB). See the README section.

set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export EMBERFALL_HOME="${EMBERFALL_HOME:-$(cd "$HERE/.." && pwd)}"
cd "$EMBERFALL_HOME" || { echo "FAIL: cannot cd to $EMBERFALL_HOME"; exit 2; }
LOG="${FCC_LOG:-$(mktemp -d /tmp/fresh_clone_check.XXXXXX)}"; mkdir -p "$LOG"
PORT=25565
STEPS=(); RESULTS=(); NOTES=()
SERVER_PID=""
# The relic_test / chest_test counts this script expects (handbook V0: relic_test 27 assertions, chest_test 19 PASS).
RELIC_MIN=27; CHEST_MIN=19

record() { STEPS+=("$1"); RESULTS+=("$2"); NOTES+=("$3"); printf '%-9s %s%s\n' "[$2]" "$1" "${3:+  ($3)}"; }
not_run() { record "$1" "NOT RUN" "$2"; }

cleanup() { [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null; return 0; }
trap cleanup EXIT

summary() {
  local p=0 f=0 n=0 i
  for i in "${!RESULTS[@]}"; do case "${RESULTS[$i]}" in PASS) p=$((p+1));; FAIL) f=$((f+1));; *) n=$((n+1));; esac; done
  echo
  echo "================ fresh_clone_check summary ================"
  printf '%-4s %-44s %-8s %s\n' "No." "Step" "Result" "Note"
  for i in "${!STEPS[@]}"; do printf '%-4s %-44s %-8s %s\n' "$((i+1))" "${STEPS[$i]}" "${RESULTS[$i]}" "${NOTES[$i]}"; done
  echo "-----------------------------------------------------------"
  echo "PASS: $p   FAIL: $f   NOT RUN: $n   (of ${#STEPS[@]} steps)   raw output: $LOG"
  if [ "$f" -eq 0 ] && [ "$n" -eq 0 ] && [ "${#STEPS[@]}" -eq 8 ]; then
    echo "RESULT: ALL 8 STEPS PASSED"; exit 0
  fi
  echo "RESULT: NOT GREEN ($f failed, $n not run)"; exit 1
}
# After a FAIL, the later steps that need it are recorded as NOT RUN, then the summary is printed.
rest_not_run() { local from=$1 reason=$2; shift 2; local s; for s in "$@"; do not_run "$s" "$reason"; done; summary; }

ALL=("tools present" "JDK 25" "./gradlew build" "pure maths checks" "test server setup" "start test server" "relic_test" "chest_test")

# ---- 1. tools -------------------------------------------------------------------------------------------------------
miss=""
for t in bash curl python3 node npm git; do command -v "$t" >/dev/null 2>&1 || miss="$miss $t"; done
# `ps` is listed on purpose: an earlier redeploy script silently did nothing when `ps` was missing. This script scans /proc
# instead, but a missing `ps` is still reported so nobody is surprised by another tool that needs it.
command -v ps >/dev/null 2>&1 || echo "note: 'ps' is not installed; this script uses /proc instead"
[ -r /proc/self/cmdline ] || miss="$miss /proc"
if [ -n "$miss" ]; then
  record "${ALL[0]}" FAIL "missing:$miss"
  rest_not_run 1 "step 1 failed" "${ALL[@]:1}"
fi
nmaj=$(node -v 2>/dev/null | sed 's/^v//; s/\..*//')
if [ -z "$nmaj" ] || [ "$nmaj" -lt 20 ] 2>/dev/null; then
  record "${ALL[0]}" FAIL "node 20+ needed, found: $(node -v 2>&1)"
  rest_not_run 1 "step 1 failed" "${ALL[@]:1}"
fi
record "${ALL[0]}" PASS "node $(node -v), $(command -v curl >/dev/null && echo curl), python3, git"

# ---- 2. JDK 25 ------------------------------------------------------------------------------------------------------
JAVA_BIN=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then JAVA_BIN="$(command -v java)"; fi
if [ -z "$JAVA_BIN" ]; then
  record "${ALL[1]}" FAIL "no java: set JAVA_HOME to a JDK 25 (or put java 25 on PATH)"
  rest_not_run 2 "step 2 failed" "${ALL[@]:2}"
fi
JV=$("$JAVA_BIN" -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
if [ "${JV:-0}" -lt 25 ] 2>/dev/null; then
  record "${ALL[1]}" FAIL "found Java ${JV:-unknown} at $JAVA_BIN; building needs 25 (Loom 1.18.2). The mod targets 21 but cannot be BUILT with 21."
  rest_not_run 2 "step 2 failed" "${ALL[@]:2}"
fi
export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"; export PATH="$JAVA_HOME/bin:$PATH"
command -v javac >/dev/null 2>&1 || { record "${ALL[1]}" FAIL "java is 25 but javac is missing: that is a JRE, a JDK is needed"; rest_not_run 2 "step 2 failed" "${ALL[@]:2}"; }
record "${ALL[1]}" PASS "Java $JV at $JAVA_HOME"

# ---- 3. build -------------------------------------------------------------------------------------------------------
if [ "${FCC_SKIP_BUILD:-}" = "1" ]; then
  ls build/libs/emberfall-*.jar >/dev/null 2>&1 || { record "${ALL[2]}" FAIL "FCC_SKIP_BUILD=1 but no jar in build/libs"; rest_not_run 3 "step 3 failed" "${ALL[@]:3}"; }
  not_run "${ALL[2]}" "FCC_SKIP_BUILD=1: reused the existing jar, the build itself was NOT proven"
else
  chmod +x ./gradlew 2>/dev/null
  t0=$(date +%s)
  ./gradlew build --no-daemon > "$LOG/build.txt" 2>&1; rc=$?
  dt=$(( $(date +%s) - t0 ))
  # Exit code AND the words: a quiet or stale build once hid a stale jar (handbook 3.1). Require BUILD SUCCESSFUL and a fresh jar.
  JAR=$(ls -t build/libs/emberfall-*[0-9].jar 2>/dev/null | head -1)
  if [ "$rc" -eq 0 ] && grep -q "BUILD SUCCESSFUL" "$LOG/build.txt" && [ -n "$JAR" ] && [ "$JAR" -nt gradle.properties ] && [ -z "$(find src -newer "$JAR" -name '*.java' 2>/dev/null | head -1)" ]; then
    record "${ALL[2]}" PASS "${dt}s, $(basename "$JAR")"
  else
    record "${ALL[2]}" FAIL "exit $rc after ${dt}s; see $LOG/build.txt: $(grep -E 'FAILED|error:|What went wrong' -A1 "$LOG/build.txt" | head -3 | tr '\n' ' ' | cut -c1-200)"
    rest_not_run 3 "step 3 failed" "${ALL[@]:3}"
  fi
fi
JAR=$(ls -t build/libs/emberfall-*[0-9].jar 2>/dev/null | head -1)
[ -n "$JAR" ] || { echo "no jar"; rest_not_run 3 "no jar" "${ALL[@]:3}"; }
VER=$(basename "$JAR" .jar | sed 's/^emberfall-//')

# ---- 4. pure maths checks (same loop as docs/ci/build.yml.txt) ------------------------------------------------------
(
  cd tools/testbot/relic_math || exit 9
  OUT="$LOG/math"; mkdir -p "$OUT"; fail=0; n_ok=0; n_skip=0
  for c in *Check.java; do
    n=${c%.java}
    case "$n" in AirCheck|ChestSpotCheck|PlanCheck) n_skip=$((n_skip+1)); continue;; esac
    mkdir -p "$OUT/$n"
    if ! javac -sourcepath ../../../src/main/java -d "$OUT/$n" "$c" > "$OUT/$n.compile.txt" 2>&1; then echo "FAIL $n (does not compile)"; fail=1; continue; fi
    java -cp "$OUT/$n" "$n" > "$OUT/$n.txt" 2>&1
    if grep -q "^FAIL" "$OUT/$n.txt" || ! grep -qE "ALL PASS|ALL PASSED" "$OUT/$n.txt"; then echo "FAIL $n"; fail=1; else n_ok=$((n_ok+1)); fi
  done
  echo "$n_ok $n_skip $fail" > "$OUT/tally.txt"
  [ "$fail" -eq 0 ] && [ "$n_ok" -eq 21 ]
) > "$LOG/math.txt" 2>&1
mrc=$?; read -r m_ok m_skip m_fail < "$LOG/math/tally.txt" 2>/dev/null || { m_ok=0; m_skip=0; m_fail=1; }
if [ "$mrc" -eq 0 ]; then record "${ALL[3]}" PASS "$m_ok passed, $m_skip skipped on purpose (AirCheck, ChestSpotCheck, PlanCheck need the Minecraft jar)"
else record "${ALL[3]}" FAIL "$m_ok passed (21 expected), fail flag $m_fail; see $LOG/math.txt"; fi

# ---- 5. test server setup -------------------------------------------------------------------------------------------
S="$EMBERFALL_HOME/run/server"
if bash tools/setup.sh > "$LOG/setup.txt" 2>&1; then setup_ok=1; else setup_ok=0; fi
if [ ! -s "$S/fabric-server-launch.jar" ]; then
  # tools/setup.sh only PRINTS a hint for this. Fetch the launcher for the loader in gradle.properties; reject anything not a zip.
  LV=$(grep '^loader_version=' gradle.properties | cut -d= -f2); MV=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
  IV=$(curl -sfm 30 "https://meta.fabricmc.net/v2/versions/installer" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['version'])" 2>/dev/null)
  if [ -n "$LV" ] && [ -n "$MV" ] && [ -n "$IV" ] && curl -sfLm 120 -o "$S/fabric-server-launch.jar" "https://meta.fabricmc.net/v2/versions/loader/$MV/$LV/$IV/server/jar" && [ "$(head -c 2 "$S/fabric-server-launch.jar")" = "PK" ]; then :
  else rm -f "$S/fabric-server-launch.jar"; fi
fi
if [ "$setup_ok" -eq 1 ] && [ -s "$S/fabric-server-launch.jar" ] && ls "$S"/mods/fabric-api-*.jar >/dev/null 2>&1 && grep -q '"EmberTester"' "$S/ops.json" 2>/dev/null; then
  record "${ALL[4]}" PASS "launcher $(wc -c < "$S/fabric-server-launch.jar") bytes, fabric-api present, EmberTester is op"
else
  record "${ALL[4]}" FAIL "setup.sh exit $([ $setup_ok -eq 1 ] && echo 0 || echo non-zero); launcher: $([ -s "$S/fabric-server-launch.jar" ] && echo ok || echo MISSING); see $LOG/setup.txt: $(tail -2 "$LOG/setup.txt" | tr '\n' ' ' | cut -c1-180)"
  rest_not_run 5 "step 5 failed" "${ALL[@]:5}"
fi

# ---- 6. start the test server ---------------------------------------------------------------------------------------
# The port must be free, otherwise suites would talk to someone else's server and this script would report on it.
if (exec 3<>/dev/tcp/127.0.0.1/$PORT) 2>/dev/null; then
  record "${ALL[5]}" FAIL "port $PORT is already in use: another Minecraft server is running. Stop it; this script will not test a server it did not start."
  rest_not_run 6 "step 6 failed" "${ALL[@]:6}"
fi
rm -rf "$S/world"; cp "$JAR" "$S/mods/$(basename "$JAR")" && cmp -s "$JAR" "$S/mods/$(basename "$JAR")" || { record "${ALL[5]}" FAIL "could not deploy the jar into $S/mods"; rest_not_run 6 "step 6 failed" "${ALL[@]:6}"; }
# only ONE emberfall jar may be in mods/, otherwise the server refuses to start with a duplicate-mod error
for old in "$S"/mods/emberfall-*.jar; do [ "$old" = "$S/mods/$(basename "$JAR")" ] || rm -f "$old"; done
: > "$LOG/server.txt"
( cd "$S" && exec "$JAVA_HOME/bin/java" -Xmx2G -Demberfall.testMode=true -jar fabric-server-launch.jar nogui < /dev/null > "$LOG/server.txt" 2>&1 ) &
SERVER_PID=$!
up=0
for i in $(seq 1 120); do
  grep -q 'Done (' "$LOG/server.txt" 2>/dev/null && { up=1; break; }
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 2
done
if [ "$up" -eq 1 ] && kill -0 "$SERVER_PID" 2>/dev/null && (exec 3<>/dev/tcp/127.0.0.1/$PORT) 2>/dev/null; then
  sleep 5
  record "${ALL[5]}" PASS "pid $SERVER_PID, listening on $PORT, 'Done (' in the log"
else
  record "${ALL[5]}" FAIL "server did not come up (process alive: $(kill -0 "$SERVER_PID" 2>/dev/null && echo yes || echo no)); last log: $(tail -3 "$LOG/server.txt" | tr '\n' ' ' | cut -c1-220)"
  rest_not_run 6 "step 6 failed" "${ALL[@]:6}"
fi

# ---- 7 and 8. the two live suites -------------------------------------------------------------------------------------
# relic_test / chest_test run against the SAME server, in this order. Each needs its RESULT line, its minimum PASS count and 0 FAIL.
run_suite() {
  local idx=$1 name=$2 min=$3 okline=$4 out="$LOG/$2.txt"
  ( cd tools/testbot && timeout 240 node "$name.js" > "$out" 2>&1 )
  local rc=$?
  local p f
  # A check line STARTS with PASS or FAIL. 'RESULT: ALL PASSED' is the verdict, not a check, so it is not counted.
  p=$(grep -c '^PASS' "$out"); f=$(grep -cE '^FAIL|^RESULT: [0-9]+ FAILED' "$out")
  if grep -q "$okline" "$out" && [ "$f" -eq 0 ] && [ "$p" -ge "$min" ]; then
    record "${ALL[$idx]}" PASS "$p PASS, $f FAIL, '$okline'"
  else
    record "${ALL[$idx]}" FAIL "$p PASS (need >= $min), $f FAIL, result line '$okline' $(grep -q "$okline" "$out" && echo seen || echo NOT seen), node exit $rc; tail: $(tail -2 "$out" | tr '\n' ' ' | cut -c1-160)"
  fi
}
run_suite 6 relic_test "$RELIC_MIN" "RESULT: ALL PASSED"
sleep 3
run_suite 7 chest_test "$CHEST_MIN" "RESULT: ALL PASS"
exc=$(grep -c "Exception\|Ticking entity" "$LOG/server.txt")
if [ "$exc" -ne 0 ]; then
  # Exceptions in the server log make BOTH live SUITES (steps 7 and 8, array indexes 6 and 7) untrustworthy; the server start (step 6) did happen.
  for k in 6 7; do RESULTS[$k]="FAIL"; NOTES[$k]="${NOTES[$k]}; BUT $exc exception line(s) in the server log ($LOG/server.txt)"; done
fi
summary
