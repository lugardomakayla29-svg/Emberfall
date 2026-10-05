// The real kill path: set the level, let the player kill a 1 hp zombie, read the "+N XP" the pickup pays.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, d) => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' :: ' + d); };
const mult = l => Math.min(2.5, 1 + 0.03 * Math.max(0, l - 20));
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select gravedigger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  const rows = [];
  for (const lv of [10, 20, 30, 50, 80]) {
    const xps = [];
    for (let k = 0; k < 3; k++) {
      await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 300);
      await ask(`/xp set @s ${lv} levels`, 250);
      const n = lines.length;
      // spawn a fodder zombie and kill it AS the player, so the kill is credited to him
      await ask('/execute at @s run summon emberfall:horde_zombie ~0 ~ ~1.5 {Tags:["xq"],NoAI:1b,Silent:1b,PersistenceRequired:1b}', 300);
      await ask('/execute as @e[tag=xq,limit=1] run damage @s 9999 minecraft:player_attack by @p', 300);
      await sleep(1800);                                             // the gem flies to the player and pays out
      const got = lines.slice(n).join(' ').match(/\+(\d+) XP/g);
      xps.push(got ? got.map(x => parseInt(x.slice(1))).reduce((a, b) => a + b, 0) : 0);
    }
    rows.push([lv, xps]); console.log('LEVEL', lv, 'xp per kill', JSON.stringify(xps));
  }
  const base = rows[0][1][0];
  R('L0 level 10 pays the base amount (not zero)', base >= 1, 'base ' + base);
  for (const [lv, xps] of rows) {
    const want = Math.round(base * mult(lv));
    R('L' + lv + ' kill pays ' + want + ' (base ' + base + ' x ' + mult(lv).toFixed(2) + ')', xps.every(x => x === want), JSON.stringify(xps));
  }
  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
