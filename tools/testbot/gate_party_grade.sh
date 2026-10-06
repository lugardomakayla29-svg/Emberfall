#!/bin/bash
# Usage: gate_party_grade.sh <server.log>
# A gate party of any size must produce exactly ONE "Started MAP run" line (one shared run), and no exceptions.
L="$1"; fails=0; ran=0
chk() { ran=$((ran+1)); if [ "$2" = "1" ]; then echo "PASS $1"; else echo "FAIL $1 :: $3"; fails=$((fails+1)); fi; }
[ -s "$L" ] || { echo "RESULT: NO LOG"; exit 1; }
starts=$(grep -c "Started MAP run" "$L")
chk "L1 exactly one run started for the whole party" "$([ "$starts" = "1" ] && echo 1 || echo 0)" "starts=$starts"
chk "L2 no exceptions in the server log" "$([ "$(grep -c 'Exception' "$L")" = "0" ] && echo 1 || echo 0)" "$(grep -m1 'Exception' "$L" | cut -c1-100)"
echo "EXPECTED 2, ran $ran"
if [ "$fails" = "0" ] && [ "$ran" = "2" ]; then echo "RESULT: ALL PASSED ($ran checks)"; exit 0; fi
echo "RESULT: $fails FAILED (ran $ran of 2)"; exit 1
