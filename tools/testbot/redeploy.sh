#!/bin/bash
# Safe test-server redeploy: kill the real JVM by PID, wait until it is truly gone, copy, verify, clear world.
W=${EMBERFALL_HOME}
# PIDs of the test server JVM. Scans /proc so it works where `ps` is not installed (minimal containers):
# an empty result from a missing `ps` once made this script report "no server running" while one was up.
# A process is selected only if the FIRST word of its command line is `java` or ends in `/java` AND the line contains both
# flags below. So a wrapper (`bash -c "/abs/java ..."`, `tmux new ... java ...`) is NOT selected, because its first word is
# bash/tmux. Limit: a `java ... -jar fabric-server-launch.jar` started by hand for another purpose has the same first word and
# flags, so it WOULD be stopped. (The first version tested `*/java *` anywhere in the line and did match wrappers.)
pids() { for d in /proc/[0-9]*; do c=$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null); case "${c%% *}" in java|*/java) case "$c" in *"-Demberfall.testMode=true"*"-jar fabric-server-launch.jar"*) echo "${d#/proc/}";; esac;; esac; done; }
for P in $(pids); do kill $P; done
for i in $(seq 1 60); do [ -z "$(pids)" ] && break; sleep 1; done
for P in $(pids); do kill -9 $P; done
for i in $(seq 1 30); do [ -z "$(pids)" ] && break; sleep 1; done
sleep 3
if [ -n "$(pids)" ]; then echo "STILL RUNNING: $(pids)"; exit 1; fi
python3 -c "import shutil; shutil.rmtree('$W/run/server/world', ignore_errors=True)"
cp $W/build/libs/emberfall-0.1.2.jar $W/run/server/mods/emberfall-0.1.2.jar && cmp $W/build/libs/emberfall-0.1.2.jar $W/run/server/mods/emberfall-0.1.2.jar && echo "redeploy ok: no server running, jar verified, world cleared"
