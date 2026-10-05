#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
: > /tmp/coil_reg.txt
for s in devourer_leap_test worm_chain_test; do
  PREBUILD=1 bash one_suite.sh $s 260 > /dev/null 2>&1
  echo "$s | pass $(grep -c '^PASS' /tmp/one_$s.txt) | fail $(grep -c '^FAIL' /tmp/one_$s.txt) | exceptions $(grep -ciE 'exception' /tmp/one_${s}_server.log) | $(grep -E 'ALL PASS|SOME FAIL' /tmp/one_$s.txt | tail -1)" >> /tmp/coil_reg.txt
  P=$(ps aux | grep -E "java -Xmx2G" | grep -v grep | awk '{print $2}'); [ -n "$P" ] && kill $P 2>/dev/null; sleep 4
done
echo COILREG_DONE >> /tmp/coil_reg.txt
