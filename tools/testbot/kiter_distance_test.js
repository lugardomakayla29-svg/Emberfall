// Can a melee character ever reach the Marksman and the Magus? Sample the distance to the mob every 500 ms for 40 s with the
// player chasing it (pathing toward it via /tp steps is NOT used: the player stands still, as a run player does with the auto-weapon).
// The auto-weapon reach decides who is hit, so report: time inside 3 blocks (melee), inside 9 (bow/staff), and the mean distance.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const nums = r => [...r.matchAll(/(-?\d+\.?\d*(?:E-?\d+)?)[df]/g)].map(m => parseFloat(m[1]));
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  for (const id of ['blightfeather_marksman', 'umbral_magus']) {
    await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900); await sleep(1500);
    await ask(`/execute at @s run emberfall spawnelite ${id}`, 1500);
    await ask(`/tag @e[type=emberfall:${id},limit=1,sort=nearest] add probe`, 300);
    const d = []; const t0 = Date.now();
    while (Date.now() - t0 < 40000) {
      const p = nums(await ask('/data get entity @e[tag=probe,limit=1] Pos', 330)); const me = bot.entity.position;
      if (p.length >= 3) d.push(Math.hypot(p[0] - me.x, p[2] - me.z));
    }
    const near3 = d.filter(x => x <= 3).length / d.length, near9 = d.filter(x => x <= 9).length / d.length;
    console.log(`${id.padEnd(24)} samples ${d.length}  mean ${(d.reduce((a, b) => a + b, 0) / d.length).toFixed(1)}  min ${Math.min(...d).toFixed(1)}  max ${Math.max(...d).toFixed(1)}  within 3: ${(near3 * 100).toFixed(0)}%  within 9: ${(near9 * 100).toFixed(0)}%`);
  }
  await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
