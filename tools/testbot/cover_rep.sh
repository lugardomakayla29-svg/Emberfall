#!/bin/bash
rm -f /tmp/cover_rep.txt
for n in 1 2 3 4 5; do
  rm -f /tmp/one_cover_test.txt
  bash one_suite.sh cover_test 200 >/dev/null 2>&1
  echo "run $n: passes=$(grep -c '^PASS' /tmp/one_cover_test.txt)/19 | $(grep -E '^hill' /tmp/one_cover_test.txt | cut -c1-120) | fails: $(grep '^FAIL' /tmp/one_cover_test.txt | cut -c6-9 | tr '\n' ' ')" >> /tmp/cover_rep.txt
done
echo REP_DONE >> /tmp/cover_rep.txt
