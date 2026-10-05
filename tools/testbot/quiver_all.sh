#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
rm -f /tmp/quiver_all_done.txt
for ch in vanguard juggernaut reaper emberwarden; do
  pkill -f "fabric-server-launch" 2>/dev/null; sleep 3
  rm -f /tmp/one_relic_quiver_test.txt
  Q_CHAR=$ch bash one_suite.sh relic_quiver_test 290 > /dev/null 2>&1
  cp /tmp/one_relic_quiver_test.txt /tmp/quiver_$ch.txt 2>/dev/null
done
echo done > /tmp/quiver_all_done.txt
