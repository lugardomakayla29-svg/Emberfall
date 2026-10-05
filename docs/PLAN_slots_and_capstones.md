# Emberfall: always-active weapons, slots, shop, capstones (plan, 2026-09-29)

Decisions (user): start 1 weapon + 1 tome slot; slots 2-4 of each bought in the shop (rising price);
weapons are also offered on level-up; HUD panel bottom-left, vanilla-styled, locked slots drawn locked;
melee weapons use their OWN damage path; Piercing Laser -> Arcane Staff, Spin Barrage -> Hunting Bow,
both 3-stack capstones only offered when the player owns the weapon; Smoke Veil held off.

## Stage 1: capstone tomes (independent of slots, do first)
- Tome offer filter (weapon-gated) in TomeOfferGenerator, new tomes only.
- piercing_laser (Staff) and spin_barrage (Bow) in TomePool + CombatStats tiers + AutoAttackSystem.
- Spin Barrage: virtual bolts (TrackedProjectiles), no real arrows, no velocity override.

## Stage 2: server slot model
- PlayerLoadout: ordered weapon slots (max 4) + tome-slot cap, per-slot cooldown + streak + flags.
- Shop rows weapon_slots / tome_slots (ChargeUpgrade style, max level 3), start 1+1.
- Firing decoupled from MAINHAND: reach and cadence per weapon, melee on own damage path.
- Level-up offers weapons; full slots => replace choice.

## Stage 3: client HUD panel (bottom-left), locked slots, cooldown sweeps.

## Not testable headlessly: how the HUD looks. Needs the user on a real client.
