#!/bin/bash
# Usage: party_boss_grade.sh <boss entity id, e.g. broodtide> <solo hit output> <party-of-5 hit output> <party-of-5 server.log>
# Inputs come from party_boss_hit_test.js (the "HIT boss=.. party=.. before=.. after=.. taken=.." line) run with PARTY=1 and PARTY=5.
# A boss at 5 players has an effective pool of base x 4.0 (1 + 0.75 x 4). Vanilla caps max_health at 1024, so the surplus is a damage
# factor: the SAME hit must take factor x as much, and factor must equal min(1, 1024 / (base x 4)).
B="$1"; SO="$2"; PO="$3"; PL="$4"; fails=0; ran=0
chk() { ran=$((ran+1)); if [ "$2" = "1" ]; then echo "PASS $1"; else echo "FAIL $1 :: $3"; fails=$((fails+1)); fi; }
[ -s "$SO" ] && [ -s "$PO" ] && [ -s "$PL" ] || { echo "RESULT: NO LOG"; exit 1; }
ts=$(grep -o "taken=[0-9.]*" "$SO" | head -1 | cut -d= -f2); tp=$(grep -o "taken=[0-9.]*" "$PO" | head -1 | cut -d= -f2)
line=$(grep "PARTYBOSS type=emberfall:$B " "$PL" | head -1)
eff=$(echo "$line" | grep -o "effective=[0-9.]*" | cut -d= -f2); fac=$(echo "$line" | grep -o "damageFactor=[0-9.]*" | cut -d= -f2)
chk "G1 solo hit landed" "$(awk -v t="$ts" 'BEGIN{print (t>0)?1:0}')" "taken=$ts"
chk "G2 party hit landed" "$(awk -v t="$tp" 'BEGIN{print (t>0)?1:0}')" "taken=$tp"
chk "G3 party logged an effective pool" "$([ -n "$eff" ] && echo 1 || echo 0)" "no PARTYBOSS line"
chk "G4 the party hit took factor x the solo hit" "$(awk -v s="$ts" -v p="$tp" -v f="$fac" 'BEGIN{r=(s>0&&f>0)?p/(s*f):0; print (r>0.97&&r<1.03)?1:0}')" "solo=$ts party=$tp factor=$fac"
solo_pool=$(grep -o "before=[0-9.]*" "$SO" | head -1 | cut -d= -f2)
chk "G5 effective pool is 4.0x the solo pool" "$(awk -v e="$eff" -v b="$solo_pool" 'BEGIN{r=(b>0)?e/b:0; print (r>3.97&&r<4.03)?1:0}')" "effective=$eff solo_pool=$solo_pool"
echo "EXPECTED 5, ran $ran"
if [ "$fails" = "0" ] && [ "$ran" = "5" ]; then echo "RESULT: ALL PASSED ($ran checks)"; exit 0; fi
echo "RESULT: $fails FAILED (ran $ran of 5)"; exit 1
