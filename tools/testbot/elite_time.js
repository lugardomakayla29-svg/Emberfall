// Acquisition over time: 8 mobs at 12-16 blocks, count how many hold a target each ~1.2s for 12 samples.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join('\n'); };
const TYPE = process.argv[2] || 'horde_spider';
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/effect give @s minecraft:regeneration 900 4 true', 200);
  await ask('/character select duelist', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  for (const d of [20, 24, 26]) await ask(`/summon emberfall:${TYPE} ~${d} ~ ~`, 150);
  console.log('sample targeted/total  nearest  (type ' + TYPE + ')');
  for (let t = 1; t <= 12; t++) {
    await sleep(700);
    const r = await ask('/emberfall debugaggro EmberTester', 500);
    const m = /aggro summary: (\d+) mobs, (\d+) with a target/.exec(r);
    const near = (r.match(/dist=([\d.]+)/g) || []).map(x => +x.slice(5));
    if (m) console.log(String(t).padStart(2), m[2] + '/' + m[1], ' nearest', near.length ? Math.min(...near).toFixed(1) : 'n/a');
  }
  clearInterval(pin); await ask('/kill @e[type=!player]', 400); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
