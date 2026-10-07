# Rift Expedition (replaces the Ember Hearth hub)

Status: DESIGN, decided by Koda while the owner is away. Not built. The owner's own proposal image could not be read (no text
found by OCR, no way to view it), so this is built from what the owner has said: "green Rift portal replacing Hearth/Gate",
"more roguelike-y, still accessible in survival", "a new character selection". Swap any decision below for the proposal's.

## Why the Hearth does not click (from the code, not a guess)
The Ember Hearth is a block you craft (magma block + 2 blaze powder + gold ingot + obsidian), place on flat ground, and it builds a
9x9 hub: 8 character busts on a ring, a shop keeper, a Departure Plate. It is a place you BUILD. A roguelike is a place you ENTER.
So the fix is to take the building away, not to reskin it.

## The loop (decided)
1. **Find a Rift.** A Rift is a green portal frame that exists in the world. In survival you craft a **Rift Shard** and use it on the
   ground: it opens a Rift at that spot (no flat-ground requirement, no terrain edits). Creative: the shard is in the tab.
2. **Step in.** Walking into the Rift opens the **Character Select screen** (a client screen, not 8 busts). One pick, then Confirm.
3. **Gate rules stay.** The proven rules carry over unchanged: a deliberate right click or step-in confirm, 3 s countdown that cancels
   if you move, return BESIDE the Rift not on it, 200-tick lockout after a run. (`GateRules`, proven.)
4. **Party.** Anyone who steps into the same Rift within the countdown is the party (max 10). Party size freezes 15 s into the run
   (`PartyScaling`, proven). The EmberTester Egg still works the same way.
5. **Run ends** -> you are put beside the Rift, which collapses after a short delay (so the world is left as it was).

## Survival access (decided)
- **Rift Shard recipe:** 1 ender pearl + 1 amethyst shard + 1 echo shard OR 4 gold ingots + 1 ender pearl + 1 amethyst shard
  (accessible in early survival; the echo shard path is the cheap-to-find-late option). One shard = one Rift. Consumed on use.
- The Rift lasts until the run ends or 10 minutes idle, then closes and restores the ground exactly (reuse `BlockJournal`).
- Shop: moves INTO the Character Select screen as a second tab (the shop keeper entity goes away).

## What gets removed
Ember Hearth block + recipe, `HubBuilder`, `HubSiteAnalyzer/Finder/Search`, the 8 busts, the shop keeper entity, the holograms.
Kept: `GateRules`, the gate countdown/party logic (retargeted from the Departure block to the Rift), `CharacterSelectManager`,
`CharacterType`.

## Open (needs the owner)
- The owner's proposal image: anything in it that contradicts the above wins.
- Look and sound of the Rift (green swirl, hum): UNVERIFIED without a graphical client.
- Whether old worlds with a placed Hearth should auto-convert (proposed: yes, the Hearth drops a Rift Shard when broken).

## Build order (each step proven before the next)
1. `RiftRules` (pure): shard cost, idle timeout, close delay, party window. Pure check first.
2. `RiftBlock` + shard item + recipe, open/close with the journal. Live test: opens on uneven ground, closes, ground identical.
3. Character Select screen + packet. Live test with the bot: picks a character, run starts as that character.
4. Retarget the gate countdown/party to the Rift. Re-run gate_test, party tests, boundary_test.
5. Remove the Hearth and hub code. Full regression.
