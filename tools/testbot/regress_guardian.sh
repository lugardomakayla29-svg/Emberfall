#!/bin/bash
rm -f /tmp/regress_guardian.txt
for s in guardian_test gate_noterrain_test gate_weapon_test move_test ledge_test attack_test attack_gate_test beam_test ring_phase3_test ring_tower_test cinder_still_test calm_test; do
  rm -f /tmp/one_$s.txt
  PREBUILD=1 bash one_suite.sh $s 300 >/dev/null 2>&1
  echo "=== $s" >> /tmp/regress_guardian.txt
  grep -E "^(PASS|FAIL|ALL PASS|SOME FAIL)" /tmp/one_$s.txt | cut -c1-150 >> /tmp/regress_guardian.txt
  echo "exceptions: $(grep -ci exception /tmp/one_${s}_server.log)" >> /tmp/regress_guardian.txt
done
# Cinderfall fairness is judged by the deterministic geometry check on the mod's real source, not a walking bot (which stalls).
echo "=== cinder_geometry" >> /tmp/regress_guardian.txt
(cd cinder_geometry && export JAVA_HOME=${JAVA_HOME} PATH=${JAVA_HOME}/bin:$PATH && python3 - <<'PY'
import re
src=open('${EMBERFALL_HOME}/mod/src/main/java/com/solme/emberfall/entity/EmberGuardian.java').read()
def grab(sig):
    i=src.index(sig); j=src.index('{',i); d=0; k=j
    while True:
        if src[k]=='{': d+=1
        elif src[k]=='}':
            d-=1
            if d==0: break
        k+=1
    return src[i:k+1]
consts="\n".join(re.findall(r'^\s*static final (?:double|int) CINDER_(?:ROW_DEPTH|LANE_WIDTH)\s*=\s*[^;]+;',src,re.M))
open('Lanes.java','w').write("import java.util.Random;\npublic class Lanes {\n"+consts+"\n    "+grab('static boolean cinderStrikes(')+"\n    "+grab('static int[] pickOpenLanes(')+"\n}\n")
PY
javac Lanes.java Check.java && java -cp . Check | tail -1 >> /tmp/regress_guardian.txt)
echo REGRESS_DONE >> /tmp/regress_guardian.txt
