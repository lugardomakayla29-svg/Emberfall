#!/bin/bash
# packet cost per worm length: segments 5, 11, 19, 23 (= 6, 12, 20, 24 parts), each on a fresh world
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/wormcost.txt
for seg in 11 19 23; do
  EXTRA_JVM="-Demberfall.wormParts=$seg" PREBUILD=1 bash one_suite.sh worm_cost_test 200 > /dev/null 2>&1
  echo "segments=$seg | $(grep -E 'worm displays|RESULT' /tmp/one_worm_cost_test.txt | tr '\n' ' ')" >> /tmp/wormcost.txt
  echo "   exceptions: $(grep -ciE 'exception' /tmp/one_worm_cost_test_server.log)" >> /tmp/wormcost.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo WORMCOST_DONE >> /tmp/wormcost.txt
