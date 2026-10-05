const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | ').replace(/.*data: /, ''); };
const num = s => { const m = /(-?\d+\.?\d*)[df]?/.exec(s); return m ? parseFloat(m[1]) : NaN; };
const hp = async sel => num(await ask(`/data get entity ${sel} Health`, 700));
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await sleep(1500);
  await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/kill @e[type=!player]', 600); await sleep(2000);
  await ask('/emberfall spawnveteran tiki_magma', 1200); await sleep(2500);
  // C1: damage aimed at the second cube drains the MOB'S health (one shared pool)
  const mob0 = await hp('@e[type=emberfall:tiki_magma,limit=1]');
  await ask('/damage @e[type=emberfall:tiki_cube,limit=1] 5 minecraft:generic', 700);
  const mob1 = await hp('@e[type=emberfall:tiki_magma,limit=1]');
  console.log(`${mob1 < mob0 ? 'PASS' : 'FAIL'} C1 mob hp ${mob0} -> ${mob1} after 5 dmg to the cube`);
  // C2: the cube itself never has its own health pool drained (it must still exist)
  const alive = await ask('/execute if entity @e[type=emberfall:tiki_cube]', 600);
  console.log(`${/passed/.test(alive) ? 'PASS' : 'FAIL'} C2 cube still present after being hit`);
  // C3: sway moves the head over time while the mob stays put
  const y = [];
  for (let i = 0; i < 4; i++) { y.push(await ask('/data get entity @e[type=emberfall:tiki_cube,limit=1] Pos', 600)); await sleep(500); }
  console.log(`  cube Pos samples: ${y.map(s => s.slice(0, 44)).join(' ; ')}`);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
