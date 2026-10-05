#!/bin/bash
# Curse II, 6 fresh fights: old rounding always gave 2 minis, the new rule gives 2 or 3 (expected mean 2.4)
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/curse2_counts.txt
for i in 1 2 3; do
  PREBUILD=1 bash one_suite.sh dcurse2 230 > /dev/null 2>&1
  grep "phase 2 wave" /tmp/one_dcurse2.txt >> /tmp/curse2_counts.txt
  grep -E "^(PASS|FAIL) C[0-3]" /tmp/one_dcurse2.txt | awk '{print $1,$2}' | tr '\n' ' ' >> /tmp/curse2_counts.txt; echo >> /tmp/curse2_counts.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo DONE2 >> /tmp/curse2_counts.txt
