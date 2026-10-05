#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/snap_cmp.txt
for n in 1 2 3 4 5; do
  PREBUILD=1 bash one_suite.sh worm_chain_test 260 > /dev/null 2>&1
  echo "run $n | $(grep -E '^(PASS|FAIL) R[23]' /tmp/one_worm_chain_test.txt | cut -c1-120 | tr '\n' ' ') | coils $(grep -c 'COIL windup' /tmp/one_worm_chain_test_server.log) | $(grep -E 'head-off' /tmp/one_worm_chain_test.txt | wc -l) head-off samples" >> /tmp/snap_cmp.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo SNAP_DONE >> /tmp/snap_cmp.txt
