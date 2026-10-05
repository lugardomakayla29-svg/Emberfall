#!/bin/bash
# Cinderfall on the current jar: still (must be struck), calm beat. The walking dodge is judged by cinder_geometry/, not a bot.
cd ${EMBERFALL_HOME}/tools/testbot
rm -f /tmp/regress_cinder.txt
for s in cinder_still_test calm_test; do
  rm -f /tmp/one_$s.txt
  bash one_suite.sh $s 260 >/dev/null 2>&1
  L=/tmp/one_${s}_server.log
  echo "=== $s" >> /tmp/regress_cinder.txt
  echo "attacks begun: $(grep -c 'CINDERDBG begin' $L)" >> /tmp/regress_cinder.txt
  grep "cinder row" $L | sed 's/.*cinder row //' | awk '{a=int((NR-1)/4)+1; if ($4=="struck=true") h[a]++} END {t=0; for (i=1;i<=a;i++) if (h[i]>0) t++; printf "attacks that struck a standing player: %d of %d\n", t, a}' >> /tmp/regress_cinder.txt
  grep -E "CINDERDBG calm|calm beat" $L | cut -c12-140 >> /tmp/regress_cinder.txt
  tail -4 /tmp/one_$s.txt | cut -c1-110 >> /tmp/regress_cinder.txt
  echo "exceptions: $(grep -ci exception $L)" >> /tmp/regress_cinder.txt
  for p in $(ps aux | grep -E "java -Xmx2G|one_suite" | grep -v grep | awk '{print $2}'); do kill $p 2>/dev/null; done; sleep 3
done
echo CINDER_DONE >> /tmp/regress_cinder.txt
