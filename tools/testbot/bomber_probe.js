const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 600);
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnveteran horde_bomber', 500);
  await ask('/tag @e[type=emberfall:horde_bomber,limit=1] add bm', 200);
  await ask('/data merge entity @e[tag=bm,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  const t0 = Date.now(); let last = '';
  while (Date.now() - t0 < 9000) {
    const r = await ask('/data get entity @e[tag=bm,limit=1] {}', 200);
    const pos = /Pos: \[([^\]]*)\]/.exec(r), hp = /Health: ([\d.]+)f/.exec(r), fuse = /Fuse: (\d+)s/.exec(r), ign = /ignited: (\d)b/.exec(r);
    const d = pos ? Math.hypot(+pos[1].split('d,')[0] - bx, +pos[1].split('d,')[2].replace('d','') - bz).toFixed(1) : 'none';
    const row = pos ? `t=${((Date.now()-t0)/1000).toFixed(1)}s dist=${d} hp=${hp && hp[1]} fuse=${fuse && fuse[1]} ignited=${ign && ign[1]} myhp=${bot.health.toFixed(1)}` : `t=${((Date.now()-t0)/1000).toFixed(1)}s GONE myhp=${bot.health.toFixed(1)}`;
    if (row !== last) console.log(row); last = row;
    if (!pos) break;
  }
  clearInterval(pin); await ask('/kill @e[tag=bm]', 300); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
