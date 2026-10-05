#!/bin/bash
# Repeat block_probe on fresh worlds until a run reports STUCK (boss moved < 1 block), max 8 runs.
rm -f /tmp/stuck_hunt.txt
for n in 1 2 3 4 5 6 7 8; do
  rm -f /tmp/one_block_probe.txt
  bash one_suite.sh block_probe 120 >/dev/null 2>&1
  echo "== run $n" >> /tmp/stuck_hunt.txt
  grep -v ONE_DONE /tmp/one_block_probe.txt | cut -c1-160 >> /tmp/stuck_hunt.txt
  cp /tmp/one_block_probe_server.log /tmp/stuck_run_$n.log 2>/dev/null
  grep -q STUCK /tmp/one_block_probe.txt && { echo "FOUND_STUCK run $n" >> /tmp/stuck_hunt.txt; break; }
done
echo HUNT_DONE >> /tmp/stuck_hunt.txt
