// BROODTIDE PROTOTYPE A (MELEE zombie): can a mob be HIDDEN and brought BACK with its AI, aggro and team status intact?
// Hide = invisible + Silent + Invulnerable + NoGravity-free (no NoAI), parked INSIDE the arena box (RunMobTeam.isTeamMob is positional).
// Reveal = clear those flags. Every state is read back from the server, nothing is assumed.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => { const t = m.toString(); if (!/^Teleported EmberTester/.test(t)) lines.push(t); }); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
async function waitInExpedition(ms = 90000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { const r = await ask('/data get entity @s Dimension', 400); if (/emberfall:expedition/.test(r)) return true; await sleep(1500); } return false; }
const num = s => { const m = String(s).match(/(-?\d+(\.\d+)?)[df]?\s*$/m) || String(s).match(/:\s*(-?\d+(\.\d+)?)/); return m ? parseFloat(m[1]) : NaN; };
let hurtAt = []; bot._client.on('packet', (d, m) => { if (m.name === 'damage_event') hurtAt.push(Date.now()); });
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select battlemage'); await ask('/expedition leave', 800); await ask('/expedition', 800);
  const arrived = await waitInExpedition(); R('A0 the player reached the expedition', arrived); if (!arrived) { bot.quit(); process.exit(0); }
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/emberfall wavestop 0', 300); await ask('/kill @e[type=!player,distance=..90]', 900);
  // (player is not pinned: pinning spams chat and fights the mob)
  // The subject: a horde spitter (the v1 Brood-Kin type), 12 blocks away so it has to WALK to reach the player.
  await ask('/execute at @s positioned ~12 ~ ~ run emberfall spawnveteran horde_zombie', 1200);
  await ask('/tag @e[type=emberfall:horde_zombie,limit=1,sort=nearest] add subj', 300);
  const exists = await ask('/execute if entity @e[tag=subj]', 400); R('A1 the subject exists (tagged)', /Test passed/i.test(exists), exists.slice(0, 50));
  await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health base set 2000', 250); await ask('/effect give @e[tag=subj,limit=1] minecraft:instant_health 1 10 true', 300);
  // Baseline: it hunts. Distance must shrink while visible.
  const px = async () => { const r = await ask('/data get entity @e[tag=subj,limit=1] Pos[0]', 400); if (!/has the following entity data/.test(r)) return NaN; return num(r); };
  const hb = hurtAt.length; await sleep(9000); const baseHits = hurtAt.length - hb;
  R('A2b baseline: a visible zombie HURTS the player in 9 s (so the later check is not vacuous)', baseHits > 0, `hits=${baseHits}`);
  const aliveTeam = async () => /passed/i.test(await ask('/execute if entity @e[tag=subj,limit=1]', 350));
  // HIDE: invisible, silent, invulnerable (so no weapon or purge touches it); do NOT remove it, do NOT set NoAI.
  await ask('/data merge entity @e[tag=subj,limit=1] {Invisible:1b,Silent:1b,Invulnerable:1b}', 400);
  await ask('/effect give @e[tag=subj,limit=1] minecraft:invisibility 999 0 true', 300);
  const h0 = await px(); await sleep(3000); const h1 = await px();
  R('A3 HIDDEN: it is still in the world (not removed)', await aliveTeam());
  R('A4 HIDDEN: its AI still runs (it keeps moving toward the player)', Math.abs(h1 - h0) > 0.8, `x ${h0} -> ${h1}`);
  const dump = await ask('/data get entity @e[tag=subj,limit=1]', 600);
  R('A5 HIDDEN: the server reports the invisibility effect on it', /invisibility/i.test(dump), (dump.match(/invisibility/i)||['no effect found'])[0]);
  // Hold it in place for a "swallowed" interval, as the boss would: freeze with NoAI for 4 s, then release.
  await ask('/data merge entity @e[tag=subj,limit=1] {NoAI:1b}', 300); const f0 = await px(); await sleep(3500); const f1 = await px();
  R('A6 SWALLOWED (NoAI): it holds still', Math.abs(f1 - f0) < 0.2, `x ${f0} -> ${f1}`);
  // REVEAL
  await ask('/data merge entity @e[tag=subj,limit=1] {NoAI:0b,Invisible:0b,Silent:0b,Invulnerable:0b}', 400);
  await ask('/effect clear @e[tag=subj,limit=1] minecraft:invisibility', 300);
  const r0 = await px(); await sleep(3500); const r1 = await px();
  R('A7 REVEALED: its AI resumes (it moves again)', Math.abs(r1 - r0) > 0.8, `x ${r0} -> ${r1}`);
  // AGGRO: after the reveal it must hurt the player again within the same 9 s window it managed before. Fails if it lost its target.
  const ha = hurtAt.length; await sleep(9000); const afterHits = hurtAt.length - ha;
  R('A9 REVEALED: it attacks the player again (aggro intact)', afterHits > 0, `hits=${afterHits} (baseline ${baseHits})`);
  const tm = await ask('/data get entity @e[tag=subj,limit=1] Invulnerable', 350);
  R('A10 REVEALED: it can be hurt again (Invulnerable cleared)', /0b|0/.test(tm), tm.slice(0, 60));
  await ask('/kill @e[tag=subj]', 400); await ask('/expedition leave', 800); bot.quit(); process.exit(0);
});
