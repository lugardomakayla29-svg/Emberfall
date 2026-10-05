#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/r3_hunt.txt
for n in 1 2 3 4 5 6; do
  PREBUILD=1 bash one_suite.sh worm_chain_test 260 > /dev/null 2>&1
  if grep -q "head-off" /tmp/one_worm_chain_test.txt; then
    cp /tmp/one_worm_chain_test.txt /tmp/r3_fail.txt; cp /tmp/one_worm_chain_test_server.log /tmp/r3_fail_server.log
    echo "captured failing run $n" >> /tmp/r3_hunt.txt
    P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null
    break
  fi
  echo "run $n clean" >> /tmp/r3_hunt.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo HUNT_DONE >> /tmp/r3_hunt.txt
