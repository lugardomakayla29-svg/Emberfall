// Spectral Sickles dead zone: 3 held-still targets at 1.0 (inside the ring), 2.2 (on the blade line) and 3.4 (outside) blocks.
// The verdict is read from the server trace SICKLE_TEST (cut tag=.. flat=..) by sickle_inner_an.py, not from chat.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); const r = lines.slice(n).join(' | '); if (/^\/(character|expedition|data|execute|attribute)/.test(x)) console.log('ASK', x.slice(0, 60), '=>', r.slice(0, 140)); return r; };
const SECS = parseInt(process.argv[2] || '20', 10);
const SET = [['inner', 1.0], ['edge', 2.2], ['outer', 3.4]];
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/character select reaper', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/effect give @s minecraft:resistance 900 4 true', 300); await ask('/effect give @s minecraft:regeneration 900 4 true', 300);
  await ask('/emberfall wavestop 0', 500); await ask('/kill @e[type=!player]', 800);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  for (const [tag, d] of SET) {
    await ask(`/execute at @s run summon emberfall:horde_zombie ~${d} ~ ~ {Tags:["${tag}"],Silent:1b,NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,CustomName:"${tag}"}`, 700);
    await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 100000`, 300);
  }
  console.log('SICKLE_PHASE_BEGIN');
  const hold = setInterval(() => { for (const [tag, d] of SET) bot.chat(`/execute at @s run tp @e[tag=${tag},limit=1] ~${d} ~ ~`); }, 1000);
  await sleep(SECS * 1000);
  clearInterval(hold); console.log('SICKLE_PHASE_END');
  clearInterval(pin); await ask('/kill @e[type=!player]', 500); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
