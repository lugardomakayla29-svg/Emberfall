#!/bin/bash
# Safe test-server redeploy: kill the real JVM by PID, wait until it is truly gone, copy, verify, clear world.
W=${EMBERFALL_HOME}
pids() { ps -eo pid,args | awk '/\/java -Xmx2G -Demberfall.testMode=true -jar fabric-server-launch.jar nogui/ && !/awk|bash -c|tmux new/ {print $1}'; }
for P in $(pids); do kill $P; done
for i in $(seq 1 60); do [ -z "$(pids)" ] && break; sleep 1; done
for P in $(pids); do kill -9 $P; done
for i in $(seq 1 30); do [ -z "$(pids)" ] && break; sleep 1; done
sleep 3
if [ -n "$(pids)" ]; then echo "STILL RUNNING: $(pids)"; exit 1; fi
python3 -c "import shutil; shutil.rmtree('$W/run/server/world', ignore_errors=True)"
cp $W/build/libs/emberfall-0.1.2.jar $W/run/server/mods/emberfall-0.1.2.jar && cmp $W/build/libs/emberfall-0.1.2.jar $W/run/server/mods/emberfall-0.1.2.jar && echo "redeploy ok: no server running, jar verified, world cleared"
