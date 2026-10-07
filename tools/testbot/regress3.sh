#!/bin/bash
# Mobs, bosses and run lifecycle. EACH suite gets a freshly redeployed world: chaining them on one world caused
# false failures (leftover runs, slots, bosses). Raw output is kept per suite; no verdict line is reported as NO VERDICT, never a pass.
W=${EMBERFALL_HOME}
cd $W/tools/testbot
export JAVA_HOME=${JAVA_HOME} PATH=${JAVA_HOME}/bin:$PATH
rm -f /tmp/r3_summary.txt
for t in brood_test brood_orphan purge_test telegraph_test tiki_rebuild_test devourer_leap_test starbit_test mini_wave_test runhud_test reward_test death_screen_test witch_test tiki_voice_test summon_friendly_test blood_test; do
  bash redeploy.sh > /tmp/r3_redeploy.txt 2>&1 || { echo "$t | REDEPLOY FAILED $(tail -1 /tmp/r3_redeploy.txt)" >> /tmp/r3_summary.txt; continue; }
  : > $W/server_run.log
  (cd $W/run/server && nohup ${JAVA_HOME}/bin/java -Xmx2G -Demberfall.testMode=true -jar fabric-server-launch.jar nogui > $W/server_run.log 2>&1 &)
  for i in $(seq 1 90); do grep -q "Done (" $W/server_run.log 2>/dev/null && break; sleep 2; done
  grep -q "Done (" $W/server_run.log || { echo "$t | SERVER DID NOT START" >> /tmp/r3_summary.txt; continue; }
  sleep 5
  timeout 240 node $t.js > /tmp/r3_$t.txt 2>&1
  p=$(grep -cE "^PASS|^\s+PASS| PASS " /tmp/r3_$t.txt); f=$(grep -cE "^FAIL|FAILED|Error:|ECONNREFUSED" /tmp/r3_$t.txt)
  v=$(grep -E "ALL PASS|ALL PASSED|ALL OK|FAILED|SOME FAIL" /tmp/r3_$t.txt | tail -1 | cut -c1-50)
  ex=$(grep -c "Exception\|Ticking entity" $W/server_run.log)
  echo "$t | pass $p | fail $f | exceptions $ex | ${v:-no verdict line}" >> /tmp/r3_summary.txt
done
bash redeploy.sh > /dev/null 2>&1
echo REGRESS3_DONE >> /tmp/r3_summary.txt
