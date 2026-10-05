// A plain (NOT veteran) horde skeleton now backsteps. Pin the player, spawn an INVULNERABLE skeleton 2.5 blocks away (so the run
// player's auto-weapon cannot knock it off its hop), sample its position every ~100 ms, and count its arrows in the air.
// Vanilla backpedal is strafe 0.5 (about 0.1 blocks per tick); a backstep is a burst over 0.8 blocks between samples.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const pos = async () => { const r = await ask('/data get entity @e[tag=sk,limit=1] Pos', 160); const m = /\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const arrows = async () => { await ask('/execute store result score #a emberfall_t if entity @e[type=minecraft:arrow,distance=..40]', 120); const r = await ask('/scoreboard players get #a emberfall_t', 200); const m = /has (\d+)/.exec(r); return m ? +m[1] : 0; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/scoreboard objectives add emberfall_t dummy', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  let hops = 0, trials = 0, farther = 0, maxStep = 0, peakArrows = 0;
  for (let t = 0; t < 4; t++) {
    await ask('/kill @e[tag=sk]', 300); await ask('/kill @e[type=minecraft:arrow]', 300);
    await ask('/execute at @s run summon emberfall:horde_skeleton ~2.5 ~ ~ {Tags:["sk","keep"],PersistenceRequired:1b,Invulnerable:1b}', 600);
    // 1.21.11 has no HandItems key; the real spawner calls equipBow(), so give the plain skeleton the same bow with /item
    await ask('/item replace entity @e[tag=sk,limit=1] weapon.mainhand with minecraft:bow', 400);
    const eq = await ask('/data get entity @e[tag=sk,limit=1] equipment', 300);
    if (!/bow/.test(eq)) console.log('  WARNING no bow equipped:', eq.slice(0, 120));
    trials++;
    let prev = await pos(); const start = prev; let burst = false; const t0 = Date.now(); const series = [];
    while (Date.now() - t0 < 7000) {
      const p = await pos(); if (!p || !prev) { prev = p || prev; continue; }
      const step = Math.hypot(p[0] - prev[0], p[2] - prev[2]); if (step > maxStep) maxStep = step; series.push(Math.hypot(p[0] - bx, p[2] - bz).toFixed(1));
      if (step > 0.8) burst = true;
      const a = await arrows(); if (a > peakArrows) peakArrows = a;
      prev = p;
    }
    const end = await pos();
    const gain = end && start ? Math.hypot(end[0] - bx, end[2] - bz) - Math.hypot(start[0] - bx, start[2] - bz) : 0;
    console.log(`  trial ${t}: burst ${burst}  distance gained ${gain.toFixed(2)}  dist series ${series.filter((_, i) => i % 3 === 0).join(' ')}`);
    if (burst) hops++; if (gain > 2) farther++;
  }
  clearInterval(pin);
  console.log(`trials ${trials}  hops ${hops}  farther ${farther}  largest step ${maxStep.toFixed(2)}  peak arrows in the air ${peakArrows}`);
  R('S1 a plain skeleton makes a backward burst of over 0.8 blocks (backpedal cannot)', hops >= 3, `${hops}/${trials}`);
  R('S2 it ends up farther from the player than it started', farther >= 3, `${farther}/${trials}`);
  await ask('/kill @e[tag=keep]', 500); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
