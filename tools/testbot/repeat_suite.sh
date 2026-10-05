#!/bin/bash
# Repeat ONE suite N times, each on a fresh world. Usage: repeat_suite.sh <suite> <n> [timeout]
cd ${EMBERFALL_HOME}/tools/testbot
rm -f /tmp/rep_summary.txt
for n in $(seq 1 $2); do
  rm -f /tmp/one_$1.txt
  bash one_suite.sh $1 ${3:-200} > /dev/null 2>&1
  cp /tmp/one_$1.txt /tmp/rep_$1_$n.txt
  echo "run $n: $(grep -c '^PASS' /tmp/rep_$1_$n.txt) pass, $(grep -c '^FAIL' /tmp/rep_$1_$n.txt) fail, exceptions $(grep -c 'Exception\|Ticking entity' /tmp/one_$1_server.log) $(grep '^FAIL' /tmp/rep_$1_$n.txt | cut -c1-90)" >> /tmp/rep_summary.txt
done
echo REPEAT_DONE >> /tmp/rep_summary.txt
