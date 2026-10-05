#!/bin/bash
: > /tmp/reg_unl2.txt
for s in chest_test unlock_chest_test; do
  bash one_suite.sh $s 260 > /dev/null 2>&1
  echo "== $s" >> /tmp/reg_unl2.txt
  grep -E "^(PASS|FAIL)|RESULT|ALL PASS|SOME FAIL" /tmp/one_$s.txt | cut -c1-200 >> /tmp/reg_unl2.txt
  echo "exceptions: $(grep -c Exception /tmp/one_${s}_server.log)" >> /tmp/reg_unl2.txt
done
echo REG_UNL2_DONE >> /tmp/reg_unl2.txt
