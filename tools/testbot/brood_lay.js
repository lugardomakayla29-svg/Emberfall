// Sample the mother's position every 100ms across her first Brood Call: does she actually stop (root) and rear up (hop) while laying?
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const pos = s => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(s); return m ? [+m[1], +m[2], +m[3]] : null; };
const cnt = async sel => { await ask(`/execute store result score #n emberfall_t run execute if entity ${sel}`, 150); const r = await ask('/scoreboard players get #n emberfall_t', 150); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : -1; };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/scoreboard objectives add emberfall_t dummy', 300);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 800);
  await ask('/emberfall spawnelite broodmother_stalker', 700);
  // teleport the mother 12 blocks from the player so she has to RUN toward them, then watch
  await ask('/tp @e[type=emberfall:broodmother_stalker,limit=1] ~12 ~ ~', 400);
  const rows = []; let eggsSeen = false;
  const t0 = Date.now();
  while (Date.now() - t0 < 9000) {
    const r = await ask('/data get entity @e[type=emberfall:broodmother_stalker,limit=1] Pos', 90);
    const p = pos(r); const t = (Date.now() - t0) / 1000;
    const e = await cnt('@e[type=minecraft:item_display]');
    if (p) rows.push({ t, x: p[0], y: p[1], z: p[2], displays: e });
  }
  clearInterval(pin);
  let out = [];
  for (let i = 1; i < rows.length; i++) {
    const d = Math.hypot(rows[i].x - rows[i - 1].x, rows[i].z - rows[i - 1].z), dt = rows[i].t - rows[i - 1].t;
    out.push({ t: rows[i].t, speed: d / dt, y: rows[i].y, displays: rows[i].displays });
  }
  console.log('t(s)  speed(b/s)  y      displays');
  out.filter((_, i) => i % 2 === 0).forEach(o => console.log(o.t.toFixed(1).padStart(4), o.speed.toFixed(1).padStart(8), o.y.toFixed(2).padStart(8), String(o.displays).padStart(5)));
  await ask('/kill @e[type=!player]', 400);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
