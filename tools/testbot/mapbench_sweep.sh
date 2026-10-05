#!/bin/bash
# For each rate: fresh world, start server, run the driver, stop. Results append to /tmp/mapbench_results.txt
W=${EMBERFALL_HOME}
: > /tmp/mapbench_results.txt
for RATE in "$@"; do
  bash $W/tools/testbot/redeploy.sh > /dev/null 2>&1
  : > /tmp/mapbench_server.log
  ( cd $W/run/server && nohup ${JAVA_HOME}/bin/java -Xmx2G -Demberfall.testMode=true -jar fabric-server-launch.jar nogui > /tmp/mapbench_server.log 2>&1 & )
  for i in $(seq 1 60); do grep -q "Done (" /tmp/mapbench_server.log && break; sleep 1; done
  sleep 4
  cd $W/tools/testbot && timeout 200 node mapbench.js $RATE >> /tmp/mapbench_results.txt 2>&1
  echo "exceptions=$(grep -c Exception /tmp/mapbench_server.log)" >> /tmp/mapbench_results.txt
done
bash $W/tools/testbot/redeploy.sh > /dev/null 2>&1
echo SWEEP_DONE >> /tmp/mapbench_results.txt
