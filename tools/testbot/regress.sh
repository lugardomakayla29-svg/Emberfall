#!/bin/bash
# Runs today's suites in order (slot-count sensitive ones first) and prints only the verdict lines.
cd ${EMBERFALL_HOME}/tools/testbot
for t in slots_test cap_test weapon_offer_test milestone_test loadout_test hud_test pickup_test gold_reroll_test hotbar_test death_drop_test; do
  echo "=== $t"; timeout 200 node $t.js 2>&1 | grep -E "PASS|FAIL|ALL PASS|FAILED|ERROR|expected" | cut -c1-150 | tail -25
  sleep 3
done
echo REGRESS_DONE
