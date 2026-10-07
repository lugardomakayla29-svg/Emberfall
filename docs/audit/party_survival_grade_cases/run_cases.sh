#!/bin/bash
# Regression test for tools/testbot/party_survival_grade.sh. Runs the REAL grader on each constructed log and compares the exact
# output and exit code with the expected values below. Exit 0 only if every case matches; exit 1 on any mismatch, or on a case file
# that has no expectation here (so a log added without an assertion cannot pass silently).
# Usage (any directory): bash docs/audit/party_survival_grade_cases/run_cases.sh   Set GRADER=<path> to test another copy of the grader.
D=$(cd "$(dirname "$0")" && pwd); G=${GRADER:-$D/../../../tools/testbot/party_survival_grade.sh}
pass=0; fail=0; seen=""

# expect <case file> <expected exit code> <expected output, exactly, after the "== x" header line>
expect() {
  local f="$D/$1"; local want_code=$2; local want_out=$3
  seen="$seen $1"
  local got_out; got_out=$(bash "$G" x "$f" 2>&1); local got_code=$?
  got_out=$(printf '%s\n' "$got_out" | sed '1{/^== x$/d}' | sed "s#$D/##g")
  if [ "$got_code" = "$want_code" ] && [ "$got_out" = "$want_out" ]; then pass=$((pass+1)); echo "PASS $1 (exit $got_code)"
  else fail=$((fail+1)); echo "FAIL $1: expected exit $want_code got $got_code"; echo "  --- expected"; printf '%s\n' "$want_out" | sed 's/^/  | /'; echo "  --- got"; printf '%s\n' "$got_out" | sed 's/^/  | /'; fi
}

expect A_empty.txt 2 "CANNOT GRADE: no sign the server ran in test mode or with bots in A_empty.txt"
expect B_four_fallen.txt 0 "fallen bots: 4 [SA SB SC SD]
escaped bots: 0 []
bots that joined: 0
exceptions: 0"
expect C_two_escaped.txt 0 "fallen bots: 0 []
escaped bots: 2 [SA SB]
bots that joined: 0
exceptions: 0"
expect D_one_fallen_one_escaped.txt 0 "fallen bots: 1 [SA]
escaped bots: 1 [SB]
bots that joined: 0
exceptions: 0"
expect E_same_bot_twice.txt 0 "fallen bots: 1 [SA]
escaped bots: 0 []
bots that joined: 0
exceptions: 0"
expect F_other_names.txt 0 "fallen bots: 0 []
escaped bots: 0 []
bots that joined: 0
exceptions: 0"
expect G_chat_with_ERROR.txt 2 "CANNOT GRADE: no sign the server ran in test mode or with bots in G_chat_with_ERROR.txt"
expect H_testmode_off_bots_died.txt 2 "CANNOT GRADE: no sign the server ran in test mode or with bots in H_testmode_off_bots_died.txt"
expect I_chat_ERROR_with_testmode.txt 0 "fallen bots: 1 [SA]
escaped bots: 0 []
bots that joined: 0
exceptions: 0"
expect J_real_exception.txt 0 "fallen bots: 1 [SA]
escaped bots: 0 []
bots that joined: 0
exceptions: 2"
expect K_joined_and_one_fallen.txt 0 "fallen bots: 1 [SA]
escaped bots: 0 []
bots that joined: 2
exceptions: 0"
expect does_not_exist.txt 2 "CANNOT GRADE: log 'does_not_exist.txt' is missing or empty"

# every case file must have an expectation above
for f in "$D"/*.txt; do b=$(basename "$f"); case "$seen" in *" $b"*) ;; *) fail=$((fail+1)); echo "FAIL $b: no expectation in run_cases.sh";; esac; done

echo "run_cases: $pass PASS, $fail FAIL"
[ "$fail" = 0 ] && [ "$pass" -gt 0 ]
