# What's new

Newest first. One line per merged PR, written by the author of the PR.

## Unreleased (import branch)
- **Rift (built, not yet seen):** `/emberfall rift open|close|clear|state` (gamemaster only) now plays a Rift as particles, sounds and chat lines, with no entity spawned (#145). Underneath are three tested layers: a seeded jagged tear shape (#133), natural-event timing, spacing and a particle budget (#137), and the 5-second opening and closing as timed data (#138). **Not visible yet:** nobody has seen it in a real client. The rim shows plain white, not the warm orange the design asks for, because two of its particle types ignore colour; only the fill is pink and lilac. The sounds are placeholders picked by the author. The Rift does not appear on its own yet, and its outward push on players was never tested on a player.
- **Broodtide goo grid (built, not visible):** the data layer for goo on the ground, with a 400-cell cap and one damage tick a second per player (#141). It is not placed in the world or drawn yet.
- **Chest reveal (sequence built, no screen):** the order the slot-machine reveal would play, tier first and then the item, as pure timed data (#143). The item reel stays hidden until the tier has locked. **There is no reveal screen and nothing sends it to a player yet**, so opening a chest looks the same as before.
- **Test bot (EmberTester) changes (#94):** the bot now scouts the map hidden, has different personalities, spreads out in a party and moves more smoothly. This changes only the test tool, not the game.
- **Test fixes (no change for players):** automated checks that could never fail now can, each shown red on a deliberately broken version (#92, #100, #121). The party-survival grader now counts each bot that fell and refuses to grade without a log (#107). A stray-mob test now reads its radius from the code (#97), and a spitter test now measures the wall instead of the leap (#118). New slope and band checks cover fleeing and jumping (#105), and the grader's own cases now assert exact output (#111).
- Chests now use the iron, copper and gold chest textures: paid is iron, free is copper, gold is gold (#90).
- Tiki masks now show their face to the player, and the roof slab was removed (#117).
- Fixed a rare crash hazard: seven places that hurt players looped over the live player list while it could change, and now loop over a snapshot (#112).
- Final Swarm now ramps to 5.0x in five minutes (an ease-in: slow start, faster later) instead of sixteen (V1, PR #63).
- A looted paid or gold chest now has a 10% chance to stand up again, once per run (V3, PR #70). Free chests never come back.
- After the first boss dies the horde spawns about 1.4x faster (interval x0.7), and the player gets a WARNING line and a sound (V2, PR #66).
- Five moments now have a sound: a soft bell counting down 3, 2, 1 (rising in pitch) at the Expedition Gate, picking a Tome, a free chest appearing, a shrine trial being cleared, and the Final Swarm beginning (V4, PR #81).
- On the static map, a hostile mob that ends up past the edge circle is now moved back just inside it, checked once a second, so nothing is left stranded where you cannot reach it (PR #85).
- Imported the full mod (0.1.2), test bot, map tools and design docs.
- Added the gradle wrapper jar so `./gradlew build` works from a fresh clone (found by Vesper, V0).
- Removed a secret-shaped value from `tools/testserver/server.properties.example`.
