# Rift Expedition (replaces the Ember Hearth hub)

Status: DESIGN, now grounded in the owner's own proposal (2026-10-07 17:31 CT). Supersedes my earlier guess (a green swirl frame).
Owner words, kept verbatim where they decide something: "a vertical rip in the world, where it might spawn naturally or spawned via
shard [craftable item]"; "maybe you could do a Horizontal shaped rift?"; "when a rift event triggers naturally or on purpose, there would be
an opening animation, you can use particles, heavy particles, light, whatever, needs to look like a rift"; "plays opening animation, some
ominous sounds, an indicator in chat"; "make it look and sound and animate awesome, please go deep on that"; "improve on it".

## What the proposal image shows (read from the picture, not guessed)
A flat, JAGGED tear in the world: stepped rectangles of different sizes fused into one irregular silhouette (a tall block in the middle top,
wide wings left and right, small detached shards floating off the edge). Soft PINK-LILAC fill you can see the world through, a bright
WARM white-orange rim, a few tiny square sparks drifting off it. NOT green, NOT a swirl, NOT a frame. It looks like reality cracked.

## The look (decided; Vesper refines, owner judges)
1. **Shape**: a generated jagged silhouette on a 2D grid of cells (a few big rectangles plus small satellites), different every time (seeded),
   same family as the picture. Stored as a list of cell rectangles, so it is DATA, not entities.
2. **Orientation**: VERTICAL by default (the picture). HORIZONTAL variant (a tear in the ground or sky, the owner asked for it): both use the same
   cell list rotated; a natural Rift picks vertical on flat walls of open air and horizontal over open ground, a shard Rift faces the player.
3. **Drawn with particles only, ZERO entities** (house style: `wave/SwarmPortal`, `combat/Fx`). Fill = sparse dust/end-rod haze in lilac and pink,
   rim = dense bright orange-white line particles along the cell edges, satellites = single-cell flickers. Budget: a hard cap on particles per tick,
   spawned only when a player is within render range, so an idle Rift costs almost nothing.
4. **Light**: a light block is NOT allowed (it edits terrain). Use the glow of the particles themselves (end rod / glow). Check it reads in a dark cave.

## The opening animation (the part the owner wants awesome), about 5 seconds
t=0.0 s  a single bright point and a low drone starts, the ground gives a tiny tremor (screen shake via a short vanilla effect, no new asset).
t=0.5 s  a hairline crack grows OUTWARD along the silhouette, one rim segment at a time, with a crackle per segment.
t=2.0 s  the cracks reach the full outline; the rim flares white-orange; a deep boom; a ring of dust blasts outward on the ground.
t=2.5 s  the fill floods in from the centre as a pink-lilac wave; debris-like satellite cells pop out and drift.
t=4.0 s  a pulse pushes entities near it back a tiny bit (no damage); the idle hum begins.
t=5.0 s  OPEN: players can enter. Chat indicator at t=0 (warning) and at t=5 (open). CLOSING is the same animation played in reverse, faster.

## Sound (ominous, no new audio files needed for v1)
Layer vanilla events with pitch and volume shaped over time: a low sustained drone (portal ambient / beacon ambient at low pitch), crackle (amethyst
break + lightning), a boom at the flare (warden sonic / explosion at low pitch), an idle hum that SWELLS as you get closer. Play positionally so it
is heard from far away and a Rift is findable by ear. If the owner later wants custom audio it goes into `sounds.json`.

## Chat indicator
At t=0: a coloured line naming the direction and rough distance to everyone within a wide radius ("A Rift is tearing open to the north-east, about
40 blocks away"). At open: "The Rift is open." On close: "The Rift collapses." Wording is Vesper's call, keep it short.

## How it appears
- **On purpose**: the craftable **Rift Shard** (recipe in the old section below), used on the ground in front of you; the Rift faces you.
- **Naturally**: a world EVENT. A Rift can tear open near a player in survival on a slow random timer (rare, tunable constants in `RiftRules`, a pure
  check proves the timing), never inside an active run arena, never closer than N blocks to another Rift, only on a loadable chunk with open sky or
  air. It stays open a limited time (`RiftRules.IDLE_TICKS`), then closes and leaves the world as it was (no terrain edits at all, so nothing to restore).
- Creative: the shard is in the creative tab, and a /emberfall command opens a Rift for testing (non-op cannot).

## The loop after you step in (unchanged and proven)
Step in -> Character Select screen (with the shop as a second tab) -> confirm -> `GateRules` countdown (3 s, a move cancels) -> run. Return puts you
BESIDE the Rift (lockout 200 ticks). Party = whoever steps into the same Rift within the window (max 10), frozen 15 s into the run (`PartyScaling`).
`RiftRules` already exists and holds all of these as pure constants and predicates: reuse it, do not copy it.

## What gets removed (last, after the Rift works end to end)
Ember Hearth block and recipe, `HubBuilder`, `HubSiteAnalyzer/Finder/Search`, the 8 busts, the shop keeper entity, the holograms.
Kept: `GateRules`, the countdown/party logic (retargeted to the Rift), `CharacterSelectManager`, `CharacterType`.
Old worlds with a placed Hearth: breaking it drops a Rift Shard.

## Rift Shard recipe (unchanged, survival-accessible)
1 ender pearl + 1 amethyst shard + (1 echo shard OR 4 gold ingots). One shard opens one Rift and is consumed.

## Build order (each step proven before the next; every step has a check that can FAIL)
1. `RiftShape` (pure): seeded jagged cell list. Checks: always connected, within a bounding box, never empty, different seeds differ, vertical and
   horizontal are the same cells rotated. Mutant: break connectivity, the check must go red.
2. `RiftRules` additions (pure): natural-event timer, spacing, particle budget per tick. Pure check.
3. `RiftFx`: the 5 s opening and the reverse closing as a pure TIMELINE (list of time, action) with a server executor that spends the particles and sounds.
   Live test: count particles per tick against the budget, and a client packet check that NO entity is spawned (like `bot_scout_hidden_test`).
4. Shard item + recipe + creative tab + `/emberfall rift` command. Live test: opens on uneven ground, ground byte-identical after it closes.
5. Natural event. Live test with a forced timer.
6. Character Select screen + packet, retarget the gate. Re-run `gate_test`, party tests, `boundary_test`.
7. Remove the Hearth and hub code. Full regression.

## Honest limits
Particles and sounds cannot be judged headless: the LOOK and the SOUND are UNVERIFIED until the owner (or a graphical client) sees them. Budget,
entity count, timing and openness are provable and must be proven. Do not claim it looks right; say it is built to the picture and unseen.
