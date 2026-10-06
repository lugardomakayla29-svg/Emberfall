#!/bin/sh
set -e
cd /app

echo "=== Building Emberfall mod ==="
chmod +x gradlew
# no pipe here: `cmd | tail` reports tail's exit code and hides a failed build
if ! ./gradlew build --no-daemon > /app/preview/build.log 2>&1; then
  tail -20 /app/preview/build.log
  echo "BUILD FAILED"
  exit 1
fi
echo "=== Build succeeded ==="

echo "=== Starting Minecraft dev server ==="
mkdir -p run
echo "eula=true" > run/eula.txt
if [ ! -f run/server.properties ]; then
  cp tools/testserver/server.properties.example run/server.properties 2>/dev/null || true
fi

# Run the dev server in the foreground; output goes to the log file for the healthcheck
./gradlew runServer --no-daemon > /app/preview/server.log 2>&1
