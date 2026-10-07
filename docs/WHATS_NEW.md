# What's new

Newest first. One line per merged PR, written by the author of the PR.

## Unreleased (import branch)
- Final Swarm now ramps to 5.0x in five minutes (an ease-in: slow start, faster later) instead of sixteen (V1, PR #63).
- A looted paid or gold chest now has a 10% chance to stand up again, once per run (V3, PR #70). Free chests never come back.
- After the first boss dies the horde spawns about 1.4x faster (interval x0.7), and the player gets a WARNING line and a sound (V2, PR #66).
- Five moments now have a sound: a soft bell counting down 3, 2, 1 (rising in pitch) at the Expedition Gate, picking a Tome, a free chest appearing, a shrine trial being cleared, and the Final Swarm beginning (V4, PR #81).
- On the static map, a hostile mob that ends up past the edge circle is now moved back just inside it, checked once a second, so nothing is left stranded where you cannot reach it (PR #85).
- Imported the full mod (0.1.2), test bot, map tools and design docs.
- Added the gradle wrapper jar so `./gradlew build` works from a fresh clone (found by Vesper, V0).
- Removed a secret-shaped value from `tools/testserver/server.properties.example`.
