#!/bin/bash
: > /tmp/reg_unl.txt
for s in chest_test relic_test; do
  bash one_suite.sh $s 260 > /dev/null 2>&1
  echo "== $s" >> /tmp/reg_unl.txt
  grep -E "^(FAIL)|RESULT|ALL PASS|SOME FAIL" /tmp/one_$s.txt | cut -c1-150 >> /tmp/reg_unl.txt
  echo "pass lines: $(grep -c '^PASS' /tmp/one_$s.txt), exceptions: $(grep -c Exception /tmp/one_${s}_server.log)" >> /tmp/reg_unl.txt
done
echo REG_UNL_DONE >> /tmp/reg_unl.txt
