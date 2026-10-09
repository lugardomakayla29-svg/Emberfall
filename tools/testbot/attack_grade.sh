#!/bin/bash
# Grades attack_test from the SERVER log (ATKDBG lines from EmberGuardian.resolveFan, TEST_MODE only). Never judged from hp.
# The test pins the inside bystander 36 degrees off the aim line (half angle is 40) and the outside one 44 degrees off. The server prints
# the angle it measured, so every grade below uses THAT number, not the test's intent.
# Usage: attack_grade.sh <server.log>
L=${1:?server log}
[ -s "$L" ] || { echo "FAIL A0 server log missing or empty: $L (nothing was graded)"; exit 2; }
n=$(grep -a -c "ATKDBG fan" "$L"); echo "fan evaluations=$n"
[ "$n" -ge 2 ] && echo "PASS A1 the Fan resolved and evaluated players" || { echo "FAIL A1 the Fan never evaluated anyone ($n lines): the boss did not fire"; exit 1; }
# A2: every evaluation that was a HIT measured an angle <= 40. A3: every evaluation rejected for angle measured an angle > 40.
bad_hit=$(grep -a "ATKDBG fan .*hit=true" "$L" | sed -E 's/.*angle=([0-9.]+).*/\1/' | awk '$1>40.0{c++} END{print c+0}')
bad_miss=$(grep -a "ATKDBG fan .*why=angle" "$L" | sed -E 's/.*angle=([0-9.]+).*/\1/' | awk '$1<=40.0{c++} END{print c+0}')
[ "$bad_hit" -eq 0 ] && echo "PASS A2 no hit landed outside the 40 degree half angle" || echo "FAIL A2 $bad_hit hits outside the cone"
[ "$bad_miss" -eq 0 ] && echo "PASS A3 no player inside the cone was rejected for angle" || echo "FAIL A3 $bad_miss players inside the cone were rejected"
hit_in=$(grep -a "ATKDBG fan player=PlainInside hit=true" "$L" | wc -l)
miss_out=$(grep -a "ATKDBG fan player=PlainOutside hit=false why=angle" "$L" | wc -l)
hit_out=$(grep -a "ATKDBG fan player=PlainOutside hit=true" "$L" | wc -l)
[ "$hit_in" -ge 1 ] && echo "PASS A4 the inside bystander was hit ($hit_in times)" || echo "FAIL A4 the inside bystander was never hit"
[ "$miss_out" -ge 1 ] && [ "$hit_out" -eq 0 ] && echo "PASS A5 the outside bystander was rejected for angle ($miss_out times) and never hit" || echo "FAIL A5 outside: rejected=$miss_out hit=$hit_out"
