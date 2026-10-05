#!/bin/bash
rm -f /tmp/walk_hunt2.txt
for n in 1 2 3 4 5 6 7 8; do
  rm -f /tmp/one_block_probe.txt
  bash one_suite.sh block_probe 120 >/dev/null 2>&1
  echo "run $n: $(grep -v ONE_DONE /tmp/one_block_probe.txt | head -2 | tr "\n" " " | cut -c1-200) | hops=$(grep -c HOPDBG /tmp/one_block_probe_server.log)" >> /tmp/walk_hunt2.txt
  cp /tmp/one_block_probe_server.log /tmp/walk2_run_$n.log
done
echo HUNT_DONE >> /tmp/walk_hunt2.txt
