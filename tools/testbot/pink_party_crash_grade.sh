#!/bin/bash
# Usage: pink_party_crash_grade.sh <server log>   Prints whether the PinkPools CME is present, from the SERVER LOG.
L=$1
echo "CME in PinkPools.tickAll: $(grep -c 'PinkPools.tickAll' "$L")  | ConcurrentModificationException lines: $(grep -c 'ConcurrentModificationException' "$L") | crash reports: $(grep -c 'crash report has been saved' "$L") | fallen: $(grep -c 'cause=fallen' "$L")"
