const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 800) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode creative'); await ask('/effect give @s resistance 600 4 true', 400);
  await ask('/tp @s 0 80 0', 500); await ask('/kill @e[type=!player]', 600);
  await ask('/emberfall spawnelite bonecaller_necromancer', 1500);
  const eq = await ask('/data get entity @e[type=minecraft:zombie_horse,limit=1] equipment', 900);
  check('S1 mount carries a saddle', /saddle/i.test(eq), eq.slice(0, 160));
  const dc = await ask('/data get entity @e[type=minecraft:zombie_horse,limit=1] drop_chances', 900);
  check('S2 saddle never drops (chance 0)', /saddle: 0\.0f/.test(dc), dc.slice(0, 200));
  const rid = await ask('/execute if entity @e[type=minecraft:zombie_horse,nbt={Passengers:[{}]}]', 700);
  check('S3 rider still mounted', /passed/i.test(rid), rid.slice(0, 50));
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
