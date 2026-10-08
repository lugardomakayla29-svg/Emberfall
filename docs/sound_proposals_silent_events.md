# Sound proposals for the silent events (NOTHING IS ADDED; the owner picks)

From docs/sound_audit.md (#148). One line per row. Every sound name below was checked to exist in the 1.21.11 jar (javap of SoundEvents). Where the mod already uses a sound I say so, so a cue keeps one meaning.
Labels: VERIFIED = read or checked now. PROPOSAL = my suggestion. I cannot hear any of these, so how they sound is the owner's call.

| Event | Proposed vanilla sound | Why | Do not reuse (VERIFIED already used) |
|---|---|---|---|
| Death | `ENDER_DRAGON_DEATH` quiet, low pitch, or `WITHER_DEATH` at volume 0.5 | A run player never really dies (ALLOW_DEATH cancels the lethal hit), so vanilla's death sound never plays. A deep, short sound says "fallen" without implying a respawn. | `TOTEM_USE` is the saved-from-death cue (RelicDefenceEvents) |
| Run end | Only for cause `"fallen"`: the same cue as death. For `"escaped"` play nothing new. | RunEndHandler.finishRun has THREE causes: `"fallen"`, `"escaped"` (Final Swarm portal, a win) and `null` (leave or disconnect, no screen). SwarmPortal already plays `PORTAL_TRAVEL` just before `escapeRun`. One sound on finishRun would play the same cue for a win, a loss and a silent leave. | `PORTAL_TRAVEL` (SwarmPortal:140) |
| Shrine choice (Curse or Greed) | `BELL_BLOCK` for a Curse (low pitch) and `AMETHYST_BLOCK_CHIME` for a Greed | The two choices should sound different from each other, and from the trial cleared. | `UI_TOAST_CHALLENGE_COMPLETE` is the trial cleared (MapShrines); `BEACON_ACTIVATE` and `BEACON_DEACTIVATE` belong to the Guardian |
| Shop purchase | `VILLAGER_TRADE`, or `ARMOR_EQUIP_GOLD` for a quieter click | The player is trading, and the merchant already plays a sound when approached. | `EXPERIENCE_ORB_PICKUP` is the gold pickup (PickupSystem), so a purchase must not reuse it |

## How any of these would be wired (PROPOSAL, not done)
Through `pickup.Cue.play(level, id, sound, source, at, volume, pitch)`. It plays the sound and writes the `SOUND_TEST` log line in the same call, so a live test can prove the call was made. The log line proves the call, not that anyone can hear it.

Level up is not in this table: the chime is vanilla's, not this mod's, and I cannot say it is audible in the expedition dimension.

Tested headless, look (and sound) unverified.
