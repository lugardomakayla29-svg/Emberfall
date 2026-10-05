#!/bin/bash
# control (tier 0) then Curse IV, each on a freshly redeployed world
cd ${EMBERFALL_HOME}/tools/testbot
for t in 0 4; do
  PREBUILD=1 bash one_suite.sh dcurse$t 230 > /dev/null 2>&1
  cp ${EMBERFALL_HOME}/server_run.log /tmp/curse_tier${t}_server.log 2>/dev/null
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo CURSE_RUNS_DONE > /tmp/curse_done.txt
