#!/bin/bash
# Pink slime + cauldron + the suites those changes can reach. Fresh world per suite (copies regress4.sh's method).
W=${EMBERFALL_HOME}
cd $W/tools/testbot
export JAVA_HOME=${JAVA_HOME} PATH=${JAVA_HOME}/bin:$PATH
rm -f /tmp/rp_summary.txt
for t in pink_grow_test pink_moves_test names_test; do
  for P in $(ps -eo pid,args | grep -E "java -Xmx2G|one_suite" | grep -v grep | awk '{print $1}'); do kill $P 2>/dev/null; done
  sleep 3
  bash one_suite.sh $t 330 > /dev/null 2>&1
  f=/tmp/one_$t.txt
  p=$(grep -cE "^PASS|^\s+PASS| PASS " $f); x=$(grep -cE "^FAIL" $f)
  v=$(grep -E "ALL PASS|ALL PASSED|ALL OK|SOME FAIL|FAILED" $f | tail -1 | cut -c1-40)
  ex=$(grep -c "Exception\|Ticking entity" $W/server_run.log)
  echo "$t | pass $p | fail $x | exceptions $ex | ${v:-no verdict line}" >> /tmp/rp_summary.txt
done
for P in $(ps -eo pid,args | grep -E "java -Xmx2G|one_suite" | grep -v grep | awk '{print $1}'); do kill $P 2>/dev/null; done
echo REGRESS_PINK_DONE >> /tmp/rp_summary.txt
