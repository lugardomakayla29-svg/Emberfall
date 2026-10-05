#!/bin/bash
# Grades bot_runend_test from the SERVER log. Usage: runend_grade.sh <server log>
L=${1:-/tmp/one_bot_runend_test_server.log}
T=$(grep "RUNEND_TEST player=Faller" "$L")
n=$(echo "$T" | grep -c "RUNEND_TEST")
[ "$n" = "1" ] && echo "PASS G1 exactly one run-end for Faller" || echo "FAIL G1 run-ends for Faller: $n"
echo "$T" | grep -q "cause=null screen=false" && echo "PASS G2 leave path: cause null, no screen (the fallen/screen path is NOT covered)" || echo "FAIL G2 cause/screen: $(echo "$T" | cut -c1-120)"
echo "$T" | grep -q "slotAfter=null goldAfter=0" && echo "PASS G3 left the run (slot null) and gold cleared to 0" || echo "FAIL G3 after-state: $(echo "$T" | cut -c1-140)"
# the stayer's own end happens only in the test's final cleanup, strictly AFTER the faller's: order proves it was not ended with him
F=$(grep -n 'RUNEND_TEST player=Faller' "$L" | head -1 | cut -d: -f1); S=$(grep -n 'RUNEND_TEST player=Stayer' "$L" | head -1 | cut -d: -f1)
[ -n "$F" ] && [ -n "$S" ] && [ "$S" -gt "$F" ] && echo "PASS G4 the stayer's run-end came AFTER the faller's, not with it" || echo "FAIL G4 order faller=$F stayer=$S"
[ "$(grep -c 'Exception' "$L")" = "0" ] && echo "PASS G5 0 exceptions in the server log" || echo "FAIL G5 exceptions: $(grep -c Exception "$L")"
echo "$T" | cut -c1-200
