#!/bin/bash
# Runs the Spin Barrage test at tiers 1..3 sequentially. Outputs land in emberfall/bot/out/.
cd ${EMBERFALL_HOME}/tools/testbot
mkdir -p out; rm -f out/bow_done
for T in 1 2 3; do NOEXEC=1 timeout 120 node ability_test.js bow $T 32 > out/bow_$T.log 2>&1; sleep 3; done
echo done > out/bow_done
