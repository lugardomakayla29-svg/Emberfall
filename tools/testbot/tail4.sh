#!/bin/bash
cd ${EMBERFALL_HOME}/tools/testbot
rm -f /tmp/t4_summary.txt
for s in witch_test tiki_voice_test summon_friendly_test blood_test; do
  rm -f /tmp/one_$s.txt
  bash one_suite.sh $s 200 > /dev/null 2>&1
  echo "$s | pass $(grep -c '^PASS' /tmp/one_$s.txt) | fail $(grep -c '^FAIL' /tmp/one_$s.txt) | exceptions $(grep -c 'Exception\|Ticking entity' /tmp/one_${s}_server.log)" >> /tmp/t4_summary.txt
done
echo TAIL4_DONE >> /tmp/t4_summary.txt
