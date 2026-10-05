#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
for c in ranger battlemage reaper emberwarden; do
  timeout 90 node ring_test.js $c 25 > fx4_$c.log 2>&1
  sleep 3
done
echo done > fx4_done.flag
