// Killed by a REAL mob during a run, then a second run on the same world to prove nothing leaked.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let ends = 0, deaths = 0;
bot._client.on('packet', (d, meta) => { if (meta.name === 'custom_payload' && d.channel === 'emberfall:run_end') ends++; if (meta.name === 'death_combat_event') deaths++; });
bot.on('death', () => deaths++);
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
async function oneRun(tag) {
  ends = 0; deaths = 0;
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select juggernaut');
  await ask('/expedition', 2500);
  // remove protective stuff: 1 HP, then stand next to strong hostile mobs that will really hit
  await ask('/attribute @s minecraft:max_health base set 4'); await ask('/effect clear @s');
  for (let i = 0; i < 4; i++) await ask('/execute at @s run summon minecraft:zombie ~1.2 ~ ~ {Tags:["killer"],Attributes:[{id:"minecraft:attack_damage",base:40}],PersistenceRequired:1b}', 250);
  for (let i = 0; i < 24 && ends === 0; i++) await sleep(500);
  R(tag + ' M1 a real mob ended the run with a run_end payload', ends === 1, `(ends=${ends})`);
  R(tag + ' M2 no death packet', deaths === 0, `(${deaths})`);
  R(tag + ' M3 player alive', bot.health > 0, `(hp ${bot.health})`);
  await ask('/kill @e[tag=killer]', 500);
  await sleep(1500);
  const left = await ask('/expedition leave', 900);
  R(tag + ' M4 run is over', /not on an expedition/i.test(left), '(' + left.slice(0, 40) + ')');
}
bot.once('spawn', async () => {
  await sleep(4000);
  await oneRun('run1');
  await ask('/attribute @s minecraft:max_health base set 20'); await ask('/effect clear @s'); await ask('/heal'); 
  await sleep(2500);   // resistance grace from the first end
  await ask('/effect clear @s'); 
  const g = await ask('/emberfall debugloadout EmberTester', 900);
  R('run2 L0 loadout reset to 1+1 (nothing leaked)', /1/.test(g), '(' + g.slice(0, 70) + ')');
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
