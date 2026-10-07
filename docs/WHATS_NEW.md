# What's new

Newest first. One line per merged PR, written by the author of the PR.

## Unreleased (import branch)
- Final Swarm now ramps to 5.0x in five minutes (an ease-in: slow start, faster later) instead of sixteen (V1, PR #63).
- After the first boss dies the horde spawns about 1.4x faster (interval x0.7), and the player gets a WARNING line and a sound (V2, PR #66).
- Imported the full mod (0.1.2), test bot, map tools and design docs.
- Added the gradle wrapper jar so `./gradlew build` works from a fresh clone (found by Vesper, V0).
- Removed a secret-shaped value from `tools/testserver/server.properties.example`.
