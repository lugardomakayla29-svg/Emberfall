#!/bin/bash
# usage: meter_pair.sh <tag> <char> <char> ...   results /tmp/meter<tag>_<char>.txt, flag /tmp/meter<tag>_done.txt
cd ${EMBERFALL_HOME}/tools/testbot
TAG=$1; shift
rm -f /tmp/meter${TAG}_done.txt
for C in "$@"; do
  rm -f /tmp/one_dps_meter.txt
  METER_WINDOW=${METER_WINDOW:-6000} METER_CHAR=$C PREBUILD=1 bash one_suite.sh dps_meter 330 > /dev/null 2>&1
  cp /tmp/one_dps_meter.txt /tmp/meter${TAG}_$C.txt
done
echo done > /tmp/meter${TAG}_done.txt
