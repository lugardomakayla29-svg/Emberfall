#!/bin/bash
# Run ONE suite on a freshly redeployed world and keep the server log. Usage: one_suite.sh <suite> [seconds]
W=${EMBERFALL_HOME:?set EMBERFALL_HOME to the repo checkout}
export JAVA_HOME=${JAVA_HOME} PATH=${JAVA_HOME}/bin:$PATH
cd $W/tools/testbot
bash redeploy.sh > /tmp/one_redeploy.txt 2>&1 || { echo "REDEPLOY FAILED" > /tmp/one_$1.txt; exit 1; }
: > $W/server_run.log
(cd $W/run/server && nohup ${JAVA_HOME}/bin/java -Xmx2G -Demberfall.testMode=true $EXTRA_JVM -jar fabric-server-launch.jar nogui > $W/server_run.log 2>&1 &)
for i in $(seq 1 90); do grep -q "Done (" $W/server_run.log 2>/dev/null && break; sleep 2; done
sleep 5
if [ "$PREBUILD" = "1" ]; then node prebuild.js > /tmp/one_prebuild.txt 2>&1; sleep 3; fi
timeout ${2:-200} node $1.js > /tmp/one_$1.txt 2>&1
cp $W/server_run.log /tmp/one_$1_server.log
echo ONE_DONE >> /tmp/one_$1.txt
