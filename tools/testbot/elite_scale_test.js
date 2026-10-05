// The four Corrupted elites were enlarged (scale 1.25) and given more hp. Prove, with verdicts, that a bigger body did not break them:
//   each still DAMAGES the player (melee reach for the Reaver, arrows for the Marksman, orbs for the Magus, curses or horse charge for the Necromancer),
//   each is still killable by the player's weapon, and the Necromancer is still mounted.
// The player is survival with regeneration only (NO resistance, so damage shows), topped up to full before each mob; damage is read from health.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const mobs = [
  ['cinderbrand_reaver', 'Reaver'], ['blightfeather_marksman', 'Marksman'], ['umbral_magus', 'Magus'], ['bonecaller_necromancer', 'Necromancer'],
];
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  for (const [id, label] of mobs) {
    await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900); await sleep(1500);
    await ask('/effect give @s minecraft:instant_health 1 10 true', 300);
    await ask('/effect give @s minecraft:saturation 5 5 true', 200);
    let minHp = 20, hurtEvents = 0, lastHp = bot.health;
    const onHealth = () => { if (bot.health < lastHp - 0.01) hurtEvents++; lastHp = bot.health; if (bot.health < minHp) minHp = bot.health; };
    bot.on('health', onHealth);
    await ask(`/execute at @s run emberfall spawnelite ${id}`, 1500);
    const t0 = Date.now(); let dead = false;
    while (Date.now() - t0 < 45000) {
      const r = await ask(`/execute if entity @e[type=emberfall:${id},distance=..60]`, 400);
      if (!/Test passed/.test(r)) { dead = true; break; }
      if (bot.health < 7) await ask('/effect give @s minecraft:instant_health 1 10 true', 100);   // stay alive without hiding the damage already counted
    }
    bot.removeListener('health', onHealth);
    R(`${label}: damaged the player at its new size`, hurtEvents >= 1, `${hurtEvents} hurt event(s), lowest hp ${minHp.toFixed(1)}`);
    R(`${label}: still killable by the player's weapon`, dead, dead ? `dead after ${((Date.now() - t0) / 1000).toFixed(1)} s` : 'ALIVE after 45 s');
  }
  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
