#!/bin/bash
rm -f /tmp/walk_hunt.txt
for n in 1 2 3 4 5 6 7 8; do
  rm -f /tmp/one_block_probe.txt
  bash one_suite.sh block_probe 120 >/dev/null 2>&1
  echo "run $n: $(grep -v ONE_DONE /tmp/one_block_probe.txt | head -1 | cut -c1-120)" >> /tmp/walk_hunt.txt
done
echo HUNT_DONE >> /tmp/walk_hunt.txt
