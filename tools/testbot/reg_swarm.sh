#!/bin/bash
# Regression after the Final Swarm: suites that share WaveDirector, boss fights, payout and run end.
: > /tmp/reg_swarm.txt
for s in chest_test runhud_test relic_test bossdrop_test merchant_test merchant_test2 swarm_test; do
  rm -f /tmp/one_$s.txt
  bash one_suite.sh $s 420 > /dev/null 2>&1
  echo "== $s" >> /tmp/reg_swarm.txt
  grep -E "^(PASS|FAIL)|RESULT|ALL PASS|SOME FAIL" /tmp/one_$s.txt | cut -c1-150 >> /tmp/reg_swarm.txt
  echo "exceptions: $(grep -c Exception ../server_run.log 2>/dev/null)" >> /tmp/reg_swarm.txt
done
echo REG_SWARM_DONE >> /tmp/reg_swarm.txt
