#!/bin/bash
# Grades bot_shrine_test from the SERVER log: exactly one trial started, by the bot, and no exception. Usage: shrine_grade.sh <server log>
L=${1:-/tmp/one_bot_shrine_test_server.log}
n=$(grep -c "SHRINE_TEST challenge start player=Pilgrim" "$L")
e=$(grep -c "Exception" "$L")
[ "$n" = "1" ] && echo "PASS G1 exactly one trial started by Pilgrim" || echo "FAIL G1 trials started by Pilgrim: $n"
[ "$e" = "0" ] && echo "PASS G2 0 exceptions in the server log" || echo "FAIL G2 exceptions: $e"
grep "SHRINE_TEST" "$L" | cut -c1-150 | head -4
