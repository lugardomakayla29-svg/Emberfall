// Does a flame's pull move a foe? Two variants of ONE foe placed 5 blocks from the beacon: NoAI (held) and ordinary AI (frozen target not needed: Slowness 255 is NOT used).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const pos = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Pos`, 420); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  const mk = async (tag, dx, dz, ai) => { await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],${ai ? '' : 'NoAI:1b,'}Silent:1b,PersistenceRequired:1b}`, 300);
    await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 100000`, 120); await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value 100000.0f`, 120); };
  await mk('prim', 0, 3.0, false);           // the beacon lands here and is held
  await mk('held', 6.0, 3.0, true);          // ordinary AI, rooted by Slowness so it cannot walk off, but physics still applies
  await ask('/effect give @e[tag=held] minecraft:slowness 999 6 true', 200);
  await ask('/data modify entity @e[tag=held,limit=1] Invulnerable set value 1b', 150);
  const h0 = await pos('held'); const t0 = Date.now();
  const samples = [];
  for (let i = 0; i < 8; i++) { await sleep(1000); const p = await pos('held'); if (p) samples.push(+(Math.hypot(p[0] - 0.5, p[2] - 3.5)).toFixed(2)); }
  console.log('CONTROL_DIST_FROM_SAME_POINT', JSON.stringify(samples), 'start', h0 && h0.map(x => +x.toFixed(1)));
  clearInterval(pin); await ask('/kill @e[tag=keep]', 500); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
