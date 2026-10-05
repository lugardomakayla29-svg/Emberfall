#!/bin/bash
# Kraken stages: fresh world + map per suite (PREBUILD=1), summary in /tmp/rk_summary.txt
cd ${EMBERFALL_HOME}/tools/testbot
rm -f /tmp/rk_summary.txt
for t in "$@"; do
  for P in $(ps -eo pid,args | grep -E "java -Xmx2G|one_suite" | grep -v grep | awk '{print $1}'); do kill $P 2>/dev/null; done
  sleep 3
  rm -f /tmp/one_$t.txt
  PREBUILD=1 bash one_suite.sh $t ${SUITE_SECS:-300} > /dev/null 2>&1
  f=/tmp/one_$t.txt
  p=$(grep -cE "^PASS" $f); x=$(grep -cE "^FAIL" $f)
  v=$(grep -E "ALL PASS|SOME FAIL" $f | tail -1 | cut -c1-30)
  ex=$(grep -ci "exception" /tmp/one_${t}_server.log 2>/dev/null)
  echo "$t | pass $p | fail $x | exceptions $ex | ${v:-no verdict line}" >> /tmp/rk_summary.txt
done
for P in $(ps -eo pid,args | grep -E "java -Xmx2G|one_suite" | grep -v grep | awk '{print $1}'); do kill $P 2>/dev/null; done
echo KRAKEN_DONE >> /tmp/rk_summary.txt
