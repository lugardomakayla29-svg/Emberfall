#!/bin/bash
# Runs ultimate_msg_test.js once per character (fresh world each), results in /tmp/umsg_<char>.txt, flag /tmp/umsg_done.txt
rm -f /tmp/umsg_done.txt
for C in vanguard duelist juggernaut gravedigger reaper ranger battlemage emberwarden; do
  rm -f /tmp/one_ultimate_msg_test.txt
  MSG_CHAR=$C PREBUILD=1 bash ${EMBERFALL_HOME}/tools/testbot/one_suite.sh ultimate_msg_test 200 > /dev/null 2>&1
  cp /tmp/one_ultimate_msg_test.txt /tmp/umsg_$C.txt 2>/dev/null
  cp /tmp/one_ultimate_msg_test_server.log /tmp/umsg_${C}_server.log 2>/dev/null
done
echo done > /tmp/umsg_done.txt
