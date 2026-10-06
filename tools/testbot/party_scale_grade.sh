#!/bin/bash
# Usage: party_scale_grade.sh <server.log for PARTY=1> <server.log for PARTY=5>
# Grades the server-side PARTYFROZEN / PARTYHP lines. Exit 0 only if every check ran and passed.
S="$1"; P="$2"; fails=0; ran=0
chk() { ran=$((ran+1)); if [ "$2" = "1" ]; then echo "PASS $1"; else echo "FAIL $1 :: $3"; fails=$((fails+1)); fi; }
[ -s "$S" ] && [ -s "$P" ] || { echo "RESULT: NO LOG (solo=$S party=$P)"; exit 1; }
# Spawns in the first 15 s are solo-strength by design (the party is counted at tick 300). Grade only what comes AFTER PARTYFROZEN.
after() { awk '/PARTYFROZEN/{f=1;next} f' "$1"; }
SA=$(mktemp); PA=$(mktemp); after "$S" > "$SA"; after "$P" > "$PA"; S="$SA"; P="$PA"
FZS=$(grep -o "PARTYFROZEN slot=[0-9]* size=[0-9]*" "$1" | head -1); FZP=$(grep -o "PARTYFROZEN slot=[0-9]* size=[0-9]*" "$2" | head -1)
fz1="$FZS"; fz5="$FZP"
chk "G1 solo run froze size=1" "$([[ "$fz1" == *"size=1" ]] && echo 1 || echo 0)" "$fz1"
chk "G2 party run froze size=5" "$([[ "$fz5" == *"size=5" ]] && echo 1 || echo 0)" "$fz5"
n1=$(grep -c "PARTYHP type=emberfall:horde_" "$S"); n5=$(grep -c "PARTYHP type=emberfall:horde_" "$P")
chk "G3 solo logged >= 10 horde spawns" "$([ "$n1" -ge 10 ] && echo 1 || echo 0)" "n=$n1"
chk "G4 party logged >= 10 horde spawns" "$([ "$n5" -ge 10 ] && echo 1 || echo 0)" "n=$n5"
bad1=$(grep "PARTYHP type=emberfall:horde_" "$S" | grep -vc "mult=1.00")
chk "G5 solo: EVERY horde spawn has mult=1.00 and max==base" "$([ "$bad1" = "0" ] && [ "$n1" -ge 10 ] && echo 1 || echo 0)" "bad=$bad1"
same1=$(grep "PARTYHP type=emberfall:horde_" "$S" | awk '{for(i=1;i<=NF;i++){if($i ~ /^base=/)b=substr($i,6); if($i ~ /^max=/)m=substr($i,5)} if(b!=m)c++} END{print c+0}')
chk "G6 solo: max health equals base health on every spawn" "$([ "$same1" = "0" ] && echo 1 || echo 0)" "diff=$same1"
bad5=$(grep "PARTYHP type=emberfall:horde_" "$P" | grep -vc "mult=3.00")
chk "G7 party of 5: EVERY horde spawn has mult=3.00 (1+0.5*4)" "$([ "$bad5" = "0" ] && [ "$n5" -ge 10 ] && echo 1 || echo 0)" "bad=$bad5"
ratio=$(grep "PARTYHP type=emberfall:horde_" "$P" | awk '{for(i=1;i<=NF;i++){if($i ~ /^base=/)b=substr($i,6); if($i ~ /^max=/)m=substr($i,5)} if(b>0){r=m/b; if(r<2.95||r>3.05)c++}} END{print c+0}')
chk "G8 party of 5: max health is 3.0x the base on every spawn" "$([ "$ratio" = "0" ] && echo 1 || echo 0)" "off=$ratio"
echo "EXPECTED 8, ran $ran"
if [ "$fails" = "0" ] && [ "$ran" = "8" ]; then echo "RESULT: ALL PASSED ($ran checks)"; exit 0; fi
echo "RESULT: $fails FAILED (ran $ran of 8)"; exit 1
