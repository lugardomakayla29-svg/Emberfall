// Real-path elite (spawnelite -> factory spawn, rig attached) teleported 22 blocks away: does it get a target and close in?
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join('\n'); };
const NAMES = process.argv.slice(2);
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 900 4 true', 200); await ask('/effect give @s minecraft:regeneration 900 4 true', 200);
  await ask('/character select duelist', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/kill @e[type=!player]', 700);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  for (const n of NAMES) {
    await ask('/kill @e[type=!player]', 500);
    const sp = await ask(`/emberfall spawnelite ${n}`, 900);
    await ask(`/kill @e[type=!player,type=!emberfall:${n}]`, 300);
    await ask(`/execute at @s run tp @e[type=emberfall:${n},limit=1] ~22 ~ ~`, 300);
    const rows = [];
    for (let t = 0; t < 4; t++) {
      await sleep(900);
      const r = await ask('/emberfall debugaggro EmberTester', 500);
      const l = r.split('\n').find(x => x.includes('aggro ' + n + ' ')) || 'NOT LISTED (dead or gone)';
      rows.push(l.replace(/.*aggro /, ''));
    }
    console.log(n.padEnd(24), rows.map(x => x.replace(/follow=\d+/, '').trim()).join(' | ').slice(0, 200));
  }
  clearInterval(pin); await ask('/kill @e[type=!player]', 400); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
