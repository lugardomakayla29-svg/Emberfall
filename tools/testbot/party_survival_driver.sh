#!/bin/bash
# Runs the survival probe on the NEW jar then the OLD jar (guarded_suite_old.sh), saving each server log BEFORE the next start.
# Usage: party_survival_driver.sh <rounds>   Output: /tmp/psurv_<new|old>_<n>.log, /tmp/psurv_<new|old>_<n>.out, and PSURV_ALL_DONE in /tmp/psurv_done.txt
R=${1:-2}
BOT=${EMBERFALL_BOT:-tools/testbot}
LOG=${EMBERFALL_LOG:-run/server/logs/latest.log}
rm -f /tmp/psurv_done.txt
for n in $(seq 1 $R); do
  for which in new old; do
    S=guarded_suite_stage.sh; [ "$which" = old ] && S=guarded_suite_old.sh
    WINDOW=180 /tmp/$S bot_party_survival_probe 330
    cp /tmp/one_bot_party_survival_probe.txt /tmp/psurv_${which}_$n.out 2>/dev/null
    cp "$LOG" /tmp/psurv_${which}_$n.log 2>/dev/null
    echo "$which round $n copied $(date +%H:%M:%S)" >> /tmp/psurv_progress.txt
  done
done
echo PSURV_ALL_DONE > /tmp/psurv_done.txt
