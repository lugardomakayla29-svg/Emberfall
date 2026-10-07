#!/bin/bash
# Runs the real grader (tools/testbot/party_survival_grade.sh) on each constructed log and prints its deaths/exceptions lines.
# Usage, from the repo root: bash docs/audit/party_survival_grade_cases/run_cases.sh
D=$(dirname "$0")
for f in "$D"/*.txt; do echo "## $(basename "$f")"; bash tools/testbot/party_survival_grade.sh x "$f" | grep -E "deaths|exceptions" | sed 's/^/   /'; done
echo "## does_not_exist.txt"; bash tools/testbot/party_survival_grade.sh x "$D/does_not_exist.txt" 2>&1 | grep -E "deaths|exceptions" | sed 's/^/   /'
