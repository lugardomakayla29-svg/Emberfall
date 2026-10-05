// Is a scaled Magus being hurt by the Ranger, and does it heal or shield? Sample its hp and the player's distance every second for 70 s.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const num = (r, re) => { const m = re.exec(r); return m ? parseFloat(m[1]) : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900); await sleep(1500);
  await ask('/execute at @s run emberfall spawnelite umbral_magus', 1500);
  await ask('/tag @e[type=emberfall:umbral_magus,limit=1,sort=nearest] add probe', 300);
  const t0 = Date.now(); const rows = [];
  while (Date.now() - t0 < 70000) {
    const h = num(await ask('/data get entity @e[tag=probe,limit=1] Health', 330), /entity data: (-?[\d.]+)f/);
    const ab = num(await ask('/data get entity @e[tag=probe,limit=1] AbsorptionAmount', 330), /entity data: (-?[\d.]+)f/);
    rows.push([((Date.now() - t0) / 1000).toFixed(0), h, ab]);
  }
  console.log('t(s)  hp   absorption');
  rows.filter((_, i) => i % 3 === 0).forEach(r => console.log(r.join('   ')));
  const hs = rows.map(r => r[1]).filter(x => x !== null);
  console.log(`first ${hs[0]}  last ${hs[hs.length - 1]}  min ${Math.min(...hs)}  max ${Math.max(...hs)}  (max hp 80)`);
  await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
