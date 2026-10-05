#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/nocoil_cmp.txt
for n in 1 2; do
  EXTRA_JVM="-Demberfall.noCoil=true" PREBUILD=1 bash one_suite.sh worm_chain_test 260 > /dev/null 2>&1
  echo "--- coil OFF run $n" >> /tmp/nocoil_cmp.txt
  grep -E "head-off|^(PASS|FAIL) R|rows=" /tmp/one_worm_chain_test.txt | cut -c1-170 >> /tmp/nocoil_cmp.txt
  echo "coil lines in server log: $(grep -c 'COIL windup' /tmp/one_worm_chain_test_server.log)" >> /tmp/nocoil_cmp.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo NOCOIL_DONE >> /tmp/nocoil_cmp.txt
