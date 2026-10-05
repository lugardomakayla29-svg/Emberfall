// Rear-up pose: sample the mother's two rig displays (head, back sac) relative to her while she lays.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const num = s => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(s); return m ? [+m[1], +m[2], +m[3]] : null; };
const MOM = '@e[type=emberfall:broodmother_stalker,limit=1]';
const DISP = '@e[type=minecraft:item_display,tag=emberfall_run,limit=1,sort=nearest,nbt={item:{id:"minecraft:player_head"}}]';
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/effect give @s minecraft:regeneration 900 4 true', 200);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  await ask('/emberfall spawnelite broodmother_stalker', 1200);
  const rows = [];
  const t0 = Date.now();
  // Read Pos of the mother, then of every player-head display within 1.2 blocks of her (the rig parts only).
  while (Date.now() - t0 < 15000) {
    const mom = num(await ask(`/data get entity ${MOM} Pos`, 110));
    const heads = [];
    for (const k of [1, 2, 3]) {
      // k-th nearest display to her, measured from her position
      const sel = `@e[type=minecraft:item_display,sort=nearest,limit=${k}]`;
      const r = await ask(`/execute at ${MOM} as ${sel} run data get entity @s Pos`, 110);
      const all = [...r.matchAll(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/g)].map(m => [+m[1], +m[2], +m[3]]);
      if (all.length) heads.push(all[all.length - 1]);
    }
    if (!mom) continue;
    const near = heads.map(h => ({ up: +(h[1] - mom[1]).toFixed(2), h: +Math.hypot(h[0] - mom[0], h[2] - mom[2]).toFixed(2) })).filter(x => x.h < 1.2);
    rows.push({ t: ((Date.now() - t0) / 1000).toFixed(1), my: +mom[1].toFixed(2), near });
  }
  clearInterval(pin);
  console.log('samples', rows.length);
  for (const r of rows) console.log(r.t.padStart(5), 'mother y', String(r.my).padStart(6), ' rig parts (up,horiz):', r.near.map(n => n.up + ',' + n.h).join('  ') || 'none within 1.2');
  await ask('/kill @e[type=!player]', 400);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
