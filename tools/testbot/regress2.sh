#!/bin/bash
# Fresh-world order: suites that assume 1 slot first, slot-buying suites last. Keeps raw tails for silent suites.
cd ${EMBERFALL_HOME}/tools/testbot
for t in weapon_offer_test hud_test gold_reroll_test pickup_test loadout_test cap_test slots_test; do
  echo "=== $t"; timeout 200 node $t.js > /tmp/r2_$t.txt 2>&1
  grep -E "PASS|FAIL|ALL PASS|FAILED" /tmp/r2_$t.txt | cut -c1-150 | tail -14
  [ -z "$(grep -E 'PASS|FAIL' /tmp/r2_$t.txt)" ] && { echo "(no verdict lines; raw tail:)"; tail -6 /tmp/r2_$t.txt | cut -c1-150; }
  sleep 3
done
echo REGRESS2_DONE
