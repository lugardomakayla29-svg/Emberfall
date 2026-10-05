#!/bin/bash
# Repeat until a run moves < 0.3 blocks; save that run's server log.
rm -f /tmp/zero_hunt.txt
for n in 1 2 3 4 5 6 7 8 9 10; do
  rm -f /tmp/one_block_probe.txt
  bash one_suite.sh block_probe 120 >/dev/null 2>&1
  line=$(grep -v ONE_DONE /tmp/one_block_probe.txt | head -1 | cut -c1-120)
  echo "run $n: $line" >> /tmp/zero_hunt.txt
  cp /tmp/one_block_probe_server.log /tmp/zero_run_$n.log
  mv=$(echo "$line" | sed -n 's/.*moved \([0-9.]*\).*/\1/p')
  if [ -n "$mv" ] && python3 -c "import sys; sys.exit(0 if float('$mv')<0.3 else 1)"; then echo "FOUND_ZERO run $n" >> /tmp/zero_hunt.txt; break; fi
done
echo HUNT_DONE >> /tmp/zero_hunt.txt
