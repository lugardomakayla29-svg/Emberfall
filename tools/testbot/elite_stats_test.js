// Read each elite's real attributes with the mob frozen (NoAI) and the run's auto-weapon not yet acting: no fight, no guesses.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const attr = async (name) => { for (let i = 0; i < 3; i++) { const r = await ask(`/attribute @e[tag=probe,limit=1] minecraft:${name} get`, 450 + i * 250); const m = /has a value of ([\d.]+)/.exec(r) || /value[^\d-]*([\d.]+)/.exec(r); if (m) return +m[1]; } return null; };
const ids = ['cinderbrand_reaver', 'blightfeather_marksman', 'umbral_magus', 'bonecaller_necromancer', 'corrupted_sentinel'];
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode creative');                       // creative: no run, no auto-weapon, nothing fights
  await ask('/fill -20 199 -20 20 199 20 minecraft:stone', 1800); await ask('/tp @s 0.5 201 0.5', 1500);
  for (const id of ids) {
    await ask('/kill @e[type=!player]', 800); await sleep(1000);
    await ask(`/execute at @s run emberfall spawnelite ${id}`, 1500);
    await ask(`/tag @e[type=emberfall:${id},limit=1,sort=nearest] add probe`, 300);          // by TYPE: the nearest entity can be the Necromancer's horse
    await ask('/data modify entity @e[tag=probe,limit=1] NoAI set value 1b', 200);
    const mh = await attr('max_health'), sc = await attr('scale'), ad = await attr('attack_damage'), sp = await attr('movement_speed'), ar = await attr('armor');
    console.log(`${id.padEnd(24)} max_health ${mh}  scale ${sc}  attack ${ad}  speed ${sp}  armor ${ar}`);
  }
  await ask('/kill @e[type=!player]', 500);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
