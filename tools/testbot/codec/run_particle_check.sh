#!/usr/bin/env bash
# Runs RiftParticleMapCheck by hand. CI does NOT run this: it needs the Minecraft and Fabric jars, which the CI "math-checks" job does not have
# (a check that does not compile there would fail that job), so it lives outside tools/testbot/relic_math/.
# Usage (from the repo root, after a Gradle build has populated .gradle/loom-cache and run/server/libraries):  bash tools/testbot/codec/run_codec_check.sh [outdir]
set -u
OUT=${1:-$(mktemp -d)}
G=.gradle/loom-cache/minecraftMaven/net/minecraft
COMMON=$(ls $G/minecraft-common-*/*/*.jar | head -1)
FAPI=$(find .gradle/loom-cache/remapped_mods -name '*.jar' ! -name '*sources*' | tr '\n' ':')
LIBS=$(find run/server/libraries -name '*.jar' | tr '\n' ':')
LOADER=$(ls run/server/.fabric/server/fabric-loader-server-*.jar | head -1)
CP="$COMMON:$LIBS$LOADER:$FAPI"
mkdir -p "$OUT"
javac -cp "$CP" -sourcepath src/main/java -d "$OUT" tools/testbot/codec/RiftParticleMapCheck.java 2>&1 | grep -E "error" && exit 2
java -cp "$OUT:$CP" com.solme.emberfall.rift.RiftParticleMapCheck
