// Star Bit Lob test. Spawns a real Umbral Magus, pins a survival player 9 blocks away, and watches
// what the SERVER reports: the log line for the ability, and live counts of displays (stars) tagged emberfall_starbit.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const count = async sel => {
  await ask('/scoreboard objectives add sbc dummy', 150); await ask('/scoreboard players set #n sbc -1', 150);
  await ask(`/execute store result score #n sbc if entity ${sel}`, 250);
  const r = await ask('/scoreboard players get #n sbc', 400); const m = /has (-?\d+) \[sbc\]/.exec(r); return m ? +m[1] : NaN;
};
const STARS = '@e[type=minecraft:item_display,tag=emberfall_starbit]';
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall wavestop 0', 400); await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);   // no wave mobs: a wave Horde Witch shares the star tag
  check('S0 no star displays before the Magus acts', (await count(STARS)) === 0, `stars=${await count(STARS)}`);
  console.log('spawn:', (await ask('/emberfall spawnelite umbral_magus', 900)).slice(0, 90));
  let peak = 0, samples = 0, sawLog = false;
  for (let i = 0; i < 150; i++) {
    await ask('/execute at @e[type=emberfall:umbral_magus,limit=1] run tp @s ~9 ~ ~', 200);   // hold the player at range
    const c = await count(STARS); samples++; if (c > peak) peak = c;
    if (i % 10 === 0) console.log(`  t=${i} stars now=${c} peak=${peak}`);
  }
  console.log(`samples=${samples} peak simultaneous star displays=${peak}`);
  check('S1 stars appeared at all', peak > 0, `peak=${peak}`);
  check('S2 never more than 3 stars at once', peak <= 3, `peak=${peak} (cap 3)`);
  await ask('/kill @e[type=emberfall:umbral_magus]', 800); await sleep(2500);
  check('S3 no star display survives the Magus', (await count(STARS)) === 0, `stars=${await count(STARS)}`);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
