// Probe: does a veteran zombie's max health stay at a value a test sets? Reads it right after the set and again later.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const mx = async () => { const r = await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health get', 500); const m = /value[^\d-]*(-?[\d.]+)/i.exec(r) || /(-?[\d.]+)\s*$/.exec(r); return (m ? m[1] : r.slice(-80)); };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(800);
  await ask('/gamemode creative'); await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 400);
  await ask('/emberfall spawnveteran horde_zombie', 900);
  await ask('/tag @e[type=emberfall:horde_zombie,limit=1] add subj', 300);
  console.log('P0 right after spawn      max =', await mx());
  await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health base set 40', 400);
  console.log('P1 right after set 40     max =', await mx());
  await sleep(3000);
  console.log('P2 3 s later              max =', await mx());
  await sleep(8000);
  console.log('P3 11 s later             max =', await mx());
  console.log('PROBE_DONE'); bot.quit(); process.exit(0);
});
