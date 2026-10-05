// Time to kill: each of the 4 elites (+ Sentinel as the reference) against the Broadsword character at level 1, mob free to fight back.
// The player has resistance 4 + regeneration so it survives; the clock runs from spawn to death. Lower = weaker mob.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const alive = async tag => { const r = await ask(`/execute if entity @e[tag=${tag}]`, 350); return /Test passed/.test(r); };
const mobs = (process.env.MOBS || 'cinderbrand_reaver,blightfeather_marksman,umbral_magus,bonecaller_necromancer,corrupted_sentinel').split(',');
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  for (const id of mobs) {
    await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900); await sleep(1200);
    await ask(`/execute at @s run emberfall spawnelite ${id}`, 1200);
    const hpr = await ask('/data get entity @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,limit=1,sort=nearest] Health', 500);
    const t0 = Date.now(); let dead = false;
    while (Date.now() - t0 < 60000) {
      const r = await ask('/execute if entity @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:item,distance=..40]', 350);
      if (!/Test passed/.test(r)) { dead = true; break; }
    }
    console.log(`${id.padEnd(24)} start ${(/entity data: ([\d.]+)f/.exec(hpr) || [])[1]} hp, ${dead ? 'dead after ' + ((Date.now() - t0) / 1000).toFixed(1) + ' s' : 'ALIVE after 60 s'}`);
  }
  await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
