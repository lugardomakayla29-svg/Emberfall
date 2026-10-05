// Does the lunge END near the player? Spawn a plain zombie 5.5 blocks away, record the position when the burst finishes (step back under 0.3),
// and report the distance to the pinned player. A useful lunge ends within melee reach (about 2 blocks), not past or short.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const pos = async () => { const r = await ask('/data get entity @e[tag=lz,limit=1] Pos', 160); const m = /\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  for (const d0 of [4.5, 5.5, 6.5, 6.5, 5.0]) {
    await ask('/kill @e[tag=lz]', 300);
    await ask(`/execute at @s run summon emberfall:horde_zombie ~${d0} ~ ~ {Tags:["lz","keep"],PersistenceRequired:1b,Invulnerable:1b}`, 500);
    let prev = await pos(), burst = false, landed = null, startX = prev ? prev[0] : 0, startZ = prev ? prev[2] : 0, before = null, after = null; const t0 = Date.now();
    while (Date.now() - t0 < 9000) {
      const p = await pos(); if (!p || !prev) { prev = p || prev; continue; }
      const step = Math.hypot(p[0] - prev[0], p[2] - prev[2]);
      if (step > 0.8 && !burst) { burst = true; before = Math.hypot(prev[0] - bx, prev[2] - bz); }
      if (burst && step < 0.3) { await sleep(1200); const q = await pos(); landed = q ? Math.hypot(q[0] - bx, q[2] - bz) : null; after = landed; break; }
      prev = p;
    }
    console.log(`start ${d0} blocks: before the leap ${before === null ? '?' : before.toFixed(2)} blocks away, 1.2 s after it stops ${landed === null ? '?' : landed.toFixed(2)} blocks away (closed ${before !== null && landed !== null ? (before - landed).toFixed(2) : '?'})`);
  }
  clearInterval(pin);
  await ask('/kill @e[tag=keep]', 500); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
