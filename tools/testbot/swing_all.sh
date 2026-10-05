#!/bin/bash
# Fresh world, then swing_fx_test for each melee character. Output: /tmp/swing_all_<char>.txt, summary /tmp/swing_all_summary.txt
W=${EMBERFALL_HOME}
export JAVA_HOME=${JAVA_HOME} PATH=${JAVA_HOME}/bin:$PATH
cd $W/tools/testbot
rm -f /tmp/swing_all_summary.txt
bash redeploy.sh > /tmp/swing_redeploy.txt 2>&1 || { echo "REDEPLOY FAILED" > /tmp/swing_all_summary.txt; exit 1; }
: > $W/server_run.log
(cd $W/run/server && nohup ${JAVA_HOME}/bin/java -Xmx2G -Demberfall.testMode=true -jar fabric-server-launch.jar nogui > $W/server_run.log 2>&1 &)
for i in $(seq 1 90); do grep -q "Done (" $W/server_run.log 2>/dev/null && break; sleep 2; done
sleep 5
for c in broadsword duelist juggernaut gravedigger; do
  timeout 80 node swing_fx_test.js $c 12 > /tmp/swing_all_$c.txt 2>&1
  echo "== $c: $(tail -4 /tmp/swing_all_$c.txt | tr '\n' ' ' | cut -c1-260)" >> /tmp/swing_all_summary.txt
  sleep 3
done
echo "exceptions $(grep -c 'Exception\|Ticking entity' $W/server_run.log)" >> /tmp/swing_all_summary.txt
echo SWING_DONE >> /tmp/swing_all_summary.txt
