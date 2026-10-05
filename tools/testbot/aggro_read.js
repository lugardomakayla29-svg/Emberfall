// Read each run mob's real target + follow range via /emberfall debugaggro, mobs placed far from a survival player inside a run.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join('\n'); };
const DIST = +process.argv[2] || 22;
const TYPES = ['horde_zombie', 'horde_skeleton', 'horde_spider'];
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/effect give @s minecraft:regeneration 900 4 true', 200);
  await ask('/character select duelist', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  for (const t of TYPES) await ask(`/summon emberfall:${t} ~${DIST} ~ ~ {Tags:["t1"]}`, 250);
  for (const w of [1500, 6000]) {
    await sleep(w);
    const r = await ask('/emberfall debugaggro EmberTester', 900);
    console.log(`--- after ~${w / 1000}s`); console.log(r.split('\n').filter(l => /aggro/.test(l)).map(l => l.replace(/.*aggro/, 'aggro')).join('\n'));
  }
  clearInterval(pin); await ask('/kill @e[type=!player]', 400); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
