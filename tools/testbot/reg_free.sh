#!/bin/bash
# Regression after the free chest work: run each suite on a fresh world, keep verdict lines.
: > /tmp/reg_free.txt
for s in chest_test runhud_test relic_test guardian_test; do
  rm -f /tmp/one_$s.txt
  bash one_suite.sh $s 260 > /dev/null 2>&1
  echo "== $s" >> /tmp/reg_free.txt
  grep -E "^(PASS|FAIL)|RESULT|ALL PASS|SOME FAIL" /tmp/one_$s.txt | cut -c1-150 >> /tmp/reg_free.txt
  echo "exceptions: $(grep -c Exception /tmp/one_${s}_server.log 2>/dev/null)" >> /tmp/reg_free.txt
done
echo REG_FREE_DONE >> /tmp/reg_free.txt
