#!/bin/bash
# One-time setup of the local test server and the test bot. Needs: JDK 25, node 20+, curl.
set -e
: "${EMBERFALL_HOME:?set EMBERFALL_HOME to the repo checkout}"
cd "$EMBERFALL_HOME"
(cd tools/testbot && npm install)
S=run/server; mkdir -p $S/mods
cp -n tools/testserver/server.properties.example $S/server.properties 2>/dev/null || true
echo "eula=true" > $S/eula.txt
# The live suites drive the game through the bot EmberTester, which must be an operator (level 4).
# NoPermGuest / PlainPlayer are the NON-op permission-test names: never add them here.
if [ ! -s $S/ops.json ] || [ "$(cat $S/ops.json)" = "[]" ]; then
  echo '[{"uuid":"d3eeca9b-f438-32aa-95be-84060e2a1371","name":"EmberTester","level":4,"bypassesPlayerLimit":false}]' > $S/ops.json
fi
# The mod declares fabric-api as required, so the server needs the same Fabric API jar the build uses.
FAPI=$(grep '^fabric_api_version=' gradle.properties | cut -d= -f2)
if ! ls $S/mods/fabric-api-*.jar >/dev/null 2>&1; then
  echo "Fetching fabric-api $FAPI for the server (needs curl + python3)"
  URL=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22${MC:-1.21.11}%22%5D&loaders=%5B%22fabric%22%5D" \
    | python3 -c "import sys,json;v=[x for x in json.load(sys.stdin) if x['version_number']=='$FAPI'];print(v[0]['files'][0]['url'] if v else '')")
  [ -n "$URL" ] && curl -sL -o "$S/mods/fabric-api-$FAPI.jar" "$URL" || echo "COULD NOT FETCH fabric-api $FAPI: download it into $S/mods by hand"
fi
echo "Download a Fabric server launcher for Minecraft 1.21.11 into $S as fabric-server-launch.jar"
echo "  https://fabricmc.net/use/server/   then re-run a suite with tools/testbot/one_suite.sh"
