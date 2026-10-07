#!/bin/bash
# Usage: pinkcrash_driver.sh <label> <rounds> [stage|old]   Runs pink_party_crash_probe N times, saves each server log to /tmp/pinkc_<label>_<n>.log
L=$1; R=$2; W=${3:-stage}
LOG=${EMBERFALL_LOG:-run/server/logs/latest.log}
S=guarded_suite_stage.sh; [ "$W" = old ] && S=guarded_suite_old.sh
rm -f /tmp/pinkc_${L}_*.log /tmp/pinkc_${L}_done.txt
for n in $(seq 1 $R); do
  /tmp/$S pink_party_crash_probe 220
  cp "$LOG" /tmp/pinkc_${L}_$n.log 2>/dev/null
  echo "$L round $n $(date +%H:%M:%S)" >> /tmp/pinkc_progress.txt
done
echo DONE > /tmp/pinkc_${L}_done.txt
