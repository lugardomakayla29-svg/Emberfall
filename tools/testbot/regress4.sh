#!/bin/bash
# Mobs, bosses and run lifecycle. EACH suite gets a freshly redeployed world: chaining them on one world caused
# false failures (leftover runs, slots, bosses). Raw output is kept per suite; no verdict line is reported as NO VERDICT, never a pass.
W=${EMBERFALL_HOME}
cd $W/tools/testbot
export JAVA_HOME=${JAVA_HOME} PATH=${JAVA_HOME}/bin:$PATH
rm -f /tmp/r4_summary.txt
for t in map_build_test map_run_test map_boundary_test shrine_test shrine_payout_test guardian_test gate_test gate_noterrain_test gate_weapon_test move_test ledge_test attack_test attack_gate_test beam_test ring_phase3_test ring_tower_test cinder_still_test calm_test brood_test brood_orphan purge_test boundary_test telegraph_test tiki_rebuild_test devourer_leap_test starbit_test mini_wave_test runhud_test reward_test death_screen_test witch_test tiki_voice_test summon_friendly_test blood_test music_test weapon_offer_test hud_test gold_reroll_test pickup_test loadout_test cap_test slots_test broadsword_test halberd_test dagger_test bow_test staff_test chain_test sickle_test beacon_test hud_level_test weapon_growth_test summon_damage_test magus_summon_hurt_test quake_test phantom_test rite_test legion_test ring_particles_test late_xp_test tome_fix_test; do
  bash redeploy.sh > /tmp/r4_redeploy.txt 2>&1 || { echo "$t | REDEPLOY FAILED $(tail -1 /tmp/r4_redeploy.txt)" >> /tmp/r4_summary.txt; continue; }
  : > $W/server_run.log
  (cd $W/run/server && nohup ${JAVA_HOME}/bin/java -Xmx2G -Demberfall.testMode=true -jar fabric-server-launch.jar nogui > $W/server_run.log 2>&1 &)
  for i in $(seq 1 90); do grep -q "Done (" $W/server_run.log 2>/dev/null && break; sleep 2; done
  grep -q "Done (" $W/server_run.log || { echo "$t | SERVER DID NOT START" >> /tmp/r4_summary.txt; continue; }
  sleep 5
  case $t in map_build_test|map_run_test) ;; *) node prebuild.js > /tmp/r4_prebuild.txt 2>&1 || { echo "$t | PREBUILD FAILED" >> /tmp/r4_summary.txt; continue; } ;; esac
  sleep 3
  timeout 280 node $t.js > /tmp/r4_$t.txt 2>&1
  # measurement-style suites print numbers, not verdicts: judge them from the permanent server trace so they CAN fail
  case $t in phantom_test|rite_test|legion_test) node trace_verdict.js $t $W/server_run.log >> /tmp/r4_$t.txt 2>&1 ;; esac
  p=$(grep -cE "^PASS|^\s+PASS| PASS " /tmp/r4_$t.txt); f=$(grep -cE "^FAIL|FAILED|Error:|ECONNREFUSED" /tmp/r4_$t.txt)
  v=$(grep -E "ALL PASS|ALL PASSED|ALL OK|FAILED|SOME FAIL" /tmp/r4_$t.txt | tail -1 | cut -c1-50)
  ex=$(grep -c "Exception\|Ticking entity" $W/server_run.log)
  # a suite that ran NO checks is never a pass (issue #17): it is reported as its own loud state
  flag=""; [ "$p" -eq 0 ] && flag=" | NO CHECKS RAN"
  echo "$t | pass $p | fail $f | exceptions $ex | ${v:-no verdict line}$flag" >> /tmp/r4_summary.txt
done
bash redeploy.sh > /dev/null 2>&1
echo "TALLY: $(grep -c 'NO CHECKS RAN' /tmp/r4_summary.txt) suites ran no checks, $(grep -cE '\| fail [1-9]' /tmp/r4_summary.txt) suites failed, $(grep -cE 'exceptions [1-9]' /tmp/r4_summary.txt) with server exceptions" >> /tmp/r4_summary.txt
echo REGRESS4_DONE >> /tmp/r4_summary.txt
