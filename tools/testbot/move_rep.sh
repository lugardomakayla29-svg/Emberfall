#!/bin/bash
rm -f /tmp/move_rep.txt
for n in 1 2 3; do
  rm -f /tmp/one_move_test.txt
  bash one_suite.sh move_test 200 >/dev/null 2>&1
  echo "=== run $n" >> /tmp/move_rep.txt
  grep -E "^(PASS|FAIL|walking|ALL|SOME)" /tmp/one_move_test.txt | cut -c1-150 >> /tmp/move_rep.txt
done
echo REP_DONE >> /tmp/move_rep.txt
