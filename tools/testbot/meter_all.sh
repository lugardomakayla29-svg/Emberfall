#!/bin/bash
# Runs the damage meter for every remaining weapon, one fresh server each. Results: /tmp/meter_<char>.txt
cd ${EMBERFALL_HOME}/tools/testbot
for C in juggernaut duelist gravedigger reaper ranger battlemage emberwarden; do
  rm -f /tmp/one_dps_meter.txt
  METER_WINDOW=6000 METER_CHAR=$C PREBUILD=1 bash one_suite.sh dps_meter 300 > /dev/null 2>&1
  cp /tmp/one_dps_meter.txt /tmp/meter_$C.txt
  cp /tmp/one_dps_meter_server.log /tmp/meter_${C}_server.log 2>/dev/null
done
echo METER_ALL_DONE > /tmp/meter_all_done.txt
