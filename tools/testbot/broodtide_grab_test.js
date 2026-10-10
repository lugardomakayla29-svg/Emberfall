// Broodtide Grab live test (plan section 8 test 5 + phases). Judge = server replies only. The bot stands in reach, the Grab fires on its own (Ebb, cooldown).
// Needs -Demberfall.testMode=true. The player is given Resistance so the contact damage of the body cannot end the run; the Grab itself deals no damage.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
// 1.21.11 sends entity_velocity as lpVec3 = blocks per tick already, but mineflayer 4.39.0 still scales it by 1/8000, so the bot never moved and the
// pull checks below judged a bot that was standing still. Re-apply the true value AFTER mineflayer's own handler (setImmediate).
bot._client.on('entity_velocity', d => {
  if (!bot.entity || d.entityId !== bot.entity.id || !d.velocity) return;
  const v = { x: d.velocity.x, y: d.velocity.y, z: d.velocity.z };
  setImmediate(() => bot.entity.velocity.set(v.x, v.y, v.z));
});
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const SEL = '@e[type=emberfall:broodtide,limit=1]';
const st = async () => { for (let k = 0; k < 4; k++) { const r = await ask('/emberfall bosstide 0', 500); const m = /tick=(\d+) tide=(EBB|FLOOD) armour=([\d.]+) phase=(\w+) grabs=(\d+) impulses=(\d+) active=(\d+)/.exec(r); if (m) return { tick: +m[1], tide: m[2], phase: m[4], grabs: +m[5], impulses: +m[6], active: +m[7] }; } return null; };
const effects = async () => { const r = await ask('/data get entity EmberTester active_effects', 600); return { slow: /minecraft:slowness/.test(r), fat: /minecraft:mining_fatigue/.test(r), other: (r.match(/minecraft:[a-z_]+/g) || []).filter(e => !['minecraft:slowness', 'minecraft:mining_fatigue', 'minecraft:resistance', 'minecraft:regeneration'].includes(e) && !/minecraft:(id|duration|amplifier)/.test(e)), raw: r.slice(0, 160) }; };
const ppos = () => { const p = bot.entity.position; return { x: p.x, y: p.y, z: p.z }; };
const bpos = async () => { const r = await ask(`/data get entity ${SEL} Pos`, 500); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? { x: +m[1], y: +m[2], z: +m[3] } : null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss:', (await ask('/emberfall boss 0', 1500)).slice(0, 80)); await sleep(2000);
  await ask('/kill @e[type=!player,type=!emberfall:broodtide,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:block_display,type=!minecraft:text_display]', 600);
  const b = await bpos(); check('R0 the boss has a position', !!b, JSON.stringify(b));
  // stand 10 blocks from the body, in reach (4.5 < d <= 14)
  await ask(`/tp @s ${b.x + 10} ${b.y} ${b.z}`, 1200);
  const s0 = await st(); check('R1 phase ONE at full health', s0 && s0.phase === 'ONE', JSON.stringify(s0));
  let seenActive = false, maxActive = 0, seenSlow = false, seenFat = false, dists = [], others = new Set();
  const t0 = Date.now();
  while (Date.now() - t0 < 24000) {
    const s = await st();
    if (s && s.active > 0) { seenActive = true; maxActive = Math.max(maxActive, s.active); const e = await effects(); if (e.slow) seenSlow = true; if (e.fat) seenFat = true; e.other.forEach(o => others.add(o)); }
    const pp = ppos(); dists.push(Math.hypot(pp.x - b.x, pp.z - b.z));
    await ask(`/tp @s ${b.x + 10} ${b.y} ${b.z}`, 150);     // keep re-placing at 10 so each grab is measured from the same start
    if (s && s.grabs >= 2 && !s.active) break;
  }
  const s1 = await st();
  check('R2 a grab started on its own in Ebb', s1 && s1.grabs >= 1, JSON.stringify(s1));
  check('R3 the player got Slowness while grabbed', seenSlow, '');
  check('R4 the player got Mining Fatigue while grabbed', seenFat, '');
  check('R5 no OTHER effect was applied by the boss', others.size === 0, [...others].join(','));
  check('R6 at most ONE impulse per grab (impulses <= grabs)', s1 && s1.impulses <= s1.grabs, `impulses=${s1 && s1.impulses} grabs=${s1 && s1.grabs}`);
  check('R7 phase one allows one grab target at a time', maxActive <= 1, `maxActive=${maxActive}`);
  const closest = Math.min(...dists);
  check('R8 the pull never carried the player inside the stop radius 4.5 (closest ' + closest.toFixed(2) + ')', closest >= 4.5 - 0.6, '');
  // effects clear after the hold ends
  await ask(`/tp @s ${b.x + 20} ${b.y} ${b.z}`, 300); await sleep(5500);
  const e2 = await effects(); check('R9 Slowness and Mining Fatigue are CLEARED once the grab ends', !e2.slow && !e2.fat, e2.raw);
  // phases follow health
  await ask(`/data modify entity ${SEL} Health set value 500f`, 400); await sleep(600);
  const sp2 = await st(); check('R10 at ~62% health the boss is in phase TWO', sp2 && sp2.phase === 'TWO', JSON.stringify(sp2));
  await ask(`/data modify entity ${SEL} Health set value 200f`, 400); await sleep(600);
  const sp3 = await st(); check('R11 at ~25% health it is in phase THREE', sp3 && sp3.phase === 'THREE', JSON.stringify(sp3));
  await ask(`/data modify entity ${SEL} Health set value 800f`, 400); await sleep(600);
  const sp4 = await st(); check('R12 healing back up does NOT return it to an earlier phase', sp4 && sp4.phase === 'THREE', JSON.stringify(sp4));
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
