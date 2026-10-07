#!/bin/bash
# Usage: party_survival_grade.sh <label> <server log>
# Reads the SERVER LOG of one survival run (not bot self-reports). Exit 0 = graded, 2 = could not grade (missing log or no test-mode evidence).
# Causes the game can log (RunEndHandler): "fallen" (died), "escaped" (left through the Final Swarm portal, a WIN), null (silent leave, no line).
L=$2
echo "== $1"
if [ ! -s "$L" ]; then echo "CANNOT GRADE: log '$L' is missing or empty"; exit 2; fi
# RUNEND_TEST lines exist only with -Demberfall.testMode=true. Without that flag a run with deaths prints nothing, which must not read as "0 deaths".
if ! grep -qE 'testMode|PINK_TEST|BOT_TEST|RUNEND_TEST|EmberBot .* joined' "$L"; then echo "CANNOT GRADE: no sign the server ran in test mode or with bots in $L"; exit 2; fi
fallen=$(grep -E 'RUNEND_TEST player=S[A-D] cause=fallen' "$L" | sed -E 's/.*player=(S[A-D]) .*/\1/' | sort -u)
escaped=$(grep -E 'RUNEND_TEST player=S[A-D] cause=escaped' "$L" | sed -E 's/.*player=(S[A-D]) .*/\1/' | sort -u)
echo "fallen bots: $(echo -n "$fallen" | grep -c .) [$(echo $fallen)]"
echo "escaped bots: $(echo -n "$escaped" | grep -c .) [$(echo $escaped)]"
echo "bots that joined: $(grep -cE 'EmberBot S[A-D] joined' "$L")"
# a real exception is a Java exception class or a Server-thread ERROR line, not a chat line that happens to contain the word
echo "exceptions: $(grep -cE '^[[:space:]]*(java|net|com|org)[.A-Za-z0-9_$]*(Exception|Error)\b|\] \[[^]]*/ERROR\]' "$L")"
