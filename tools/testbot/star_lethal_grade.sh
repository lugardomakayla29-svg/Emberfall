#!/bin/bash
# Grades star_lethal_test from the SERVER log. The test's own /damage loop keeps the player at 1 hp, so a client death event cannot tell a
# star from that loop. A star is lethal when ALL hold, read in log order:
#   (1) a STAR_TEST hit landed with hpBefore <= 1.5,
#   (2) a RUNEND_TEST cause=fallen is logged within the same second window, and
#   (3) NO test "Applied ... damage" line sits between the last teleport that preceded the hit and the run end (so the loop did not do it).
# NOTE: the run end resets health inside the death handling, so the trace line reads hpAfter=20 / dead=false AFTER the reset. That is
# expected and is why (2) and (3) are the proof, not the dead= field.
# Usage: star_lethal_grade.sh <server.log>
L=${1:?server log}
[ -s "$L" ] || { echo "FAIL S0 server log missing or empty: $L (nothing was graded)"; exit 2; }
hits=$(grep -a -c "STAR_TEST hit" "$L")
echo "star hits=$hits"
[ "$hits" -ge 1 ] && echo "PASS S1 at least one star landed on the player" || echo "FAIL S1 no star landed"
h=$(grep -a -n "STAR_TEST hit .*hpBefore=1\.[0-5]" "$L" | head -1 | cut -d: -f1)
if [ -z "$h" ]; then echo "FAIL S2 no star hit landed on a player at about 1 hp"; else
  r=$(grep -a -n "RUNEND_TEST .*cause=fallen" "$L" | awk -F: -v h="$h" '{d=$1-h; if (d<0) d=-d; if (d<=3) {print $1; exit}}')
  [ -n "$r" ] && echo "PASS S2 a star hit at about 1 hp and the run ended 'fallen' right next to it (lines $h and $r)" || echo "FAIL S2 the star hit at line $h but no 'fallen' run end is next to it"
  lo=$(( h < ${r:-$h} ? h : ${r:-$h} )); hi=$(( h > ${r:-$h} ? h : ${r:-$h} ))
  prev=$(head -n "$lo" "$L" | grep -a -n "Teleported EmberTester" | tail -1 | cut -d: -f1); prev=${prev:-1}
  own=$(sed -n "${prev},${hi}p" "$L" | grep -a -c "Applied .* damage to EmberTester")
  [ "$own" -eq 0 ] && echo "PASS S3 the test's own /damage did not fall between the last teleport and the death (so the star did it)" || echo "FAIL S3 the test's own /damage ran in that window ($own lines), so the star is not proven"
fi
exc=$(grep -a -c "ConcurrentModificationException" "$L")
[ "$exc" -eq 0 ] && echo "PASS S4 no ConcurrentModificationException" || echo "FAIL S4 $exc ConcurrentModificationException"
