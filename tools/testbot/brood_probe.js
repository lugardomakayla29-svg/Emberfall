// Brood repro: in a run, spawn a Broodmother, wait for Brood Call, then watch whether the player's mod weapon ever hurts the spiderlings.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const cnt = async sel => { await ask(`/execute store result score #n emberfall_t run execute if entity ${sel}`, 400); const r = await ask('/scoreboard players get #n emberfall_t', 400); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : -1; };
const hp = async sel => { const r = await ask(`/data get entity ${sel} Health`, 500); const m = /: ([\d.]+)f/.exec(r); return m ? +m[1] : null; };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/scoreboard objectives add emberfall_t dummy', 300);
  await ask('/gamemode survival'); await ask('/character select duelist', 700); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/effect give @s minecraft:resistance 900 4 true', 300); await ask('/effect give @s minecraft:regeneration 900 4 true', 300);
  await ask('/kill @e[type=!player]', 800);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  await ask('/emberfall spawnelite broodmother_stalker', 1500);
  console.log('broodmothers:', await cnt('@e[type=emberfall:broodmother_stalker]'));
  // Keep the mother alive long enough to reach Brood Call (14s cooldown), and hold her at range so the player's weapon only reaches spiderlings.
  await ask('/attribute @e[type=emberfall:broodmother_stalker,limit=1] minecraft:max_health base set 5000', 400);
  await ask('/effect give @e[type=emberfall:broodmother_stalker,limit=1] minecraft:instant_health 1 20 true', 400);
  let seenCave = 0, hurt = 0;
  const start = Date.now();
  while (Date.now() - start < 24000) {
    const c = await cnt('@e[type=minecraft:cave_spider]');
    if (c > 0) {
      seenCave = Math.max(seenCave, c);
      const h = await hp('@e[type=minecraft:cave_spider,limit=1,sort=nearest]');
      console.log(`t=${((Date.now() - start) / 1000).toFixed(1)}s cave_spiders=${c} nearest hp=${h}`);
      if (h !== null && h < 12) hurt++;
    }
    await sleep(1500);
  }
  console.log('RESULT max spiderlings seen', seenCave, 'samples below full hp', hurt);
  clearInterval(pin); await ask('/kill @e[type=!player]', 500); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
