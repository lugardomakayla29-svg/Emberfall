#!/bin/bash
# One-time setup of the local test server and the test bot. Needs: JDK 25, node 20+, curl.
set -e
: "${EMBERFALL_HOME:?set EMBERFALL_HOME to the repo checkout}"
cd "$EMBERFALL_HOME"
(cd tools/testbot && npm install)
S=run/server; mkdir -p $S/mods
cp -n tools/testserver/server.properties.example $S/server.properties 2>/dev/null || true
echo "eula=true" > $S/eula.txt
echo "Download a Fabric server launcher for Minecraft 1.21.11 into $S as fabric-server-launch.jar"
echo "  https://fabricmc.net/use/server/   then re-run a suite with tools/testbot/one_suite.sh"
