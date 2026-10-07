#!/bin/bash
# Usage: party_survival_grade.sh <label> <log>   Reads the SERVER LOG of one survival run and prints deaths per bot (RUNEND_TEST), not bot self-reports.
L=$2
echo "== $1"
grep -E "RUNEND_TEST player=S[A-D] " "$L" | sed -E 's/.*(RUNEND_TEST player=S[A-D] cause=[^ ]*).*/\1/' | sort | uniq -c
echo "deaths (cause not leave/null): $(grep -E 'RUNEND_TEST player=S[A-D] ' "$L" | grep -vE 'cause=(null|leave|abandon)' | wc -l)"
echo "exceptions: $(grep -cE 'Exception|ERROR' "$L")"
