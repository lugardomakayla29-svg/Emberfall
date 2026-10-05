#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/catch_check.txt
for n in 1 2 3 4 5 6; do
  PREBUILD=1 bash one_suite.sh worm_chain_test 260 > /dev/null 2>&1
  L=$(grep -E '^(PASS|FAIL) R[23]' /tmp/one_worm_chain_test.txt | cut -c1-90 | tr '\n' ' ')
  echo "run $n | $L" >> /tmp/catch_check.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
  if echo "$L" | grep -q FAIL; then echo CAUGHT >> /tmp/catch_check.txt; break; fi
done
echo CATCH_DONE >> /tmp/catch_check.txt
