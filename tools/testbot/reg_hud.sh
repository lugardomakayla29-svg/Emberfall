#!/bin/bash
: > /tmp/reg_hud.txt
for s in relic_hud_test runhud_test; do
  bash one_suite.sh $s 260 > /dev/null 2>&1
  echo "== $s" >> /tmp/reg_hud.txt
  grep -E "^(PASS|FAIL)" /tmp/one_$s.txt | cut -c1-200 >> /tmp/reg_hud.txt
  echo "exceptions: $(grep -c Exception /tmp/one_${s}_server.log 2>/dev/null)" >> /tmp/reg_hud.txt
done
echo REG_HUD_DONE >> /tmp/reg_hud.txt
