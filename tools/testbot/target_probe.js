const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = m => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
bot.on('error', e => log('ERROR: ' + e));
// track positions: does the zombie move TOWARD the villager, or toward the player?
const ents = () => Object.values(bot.entities);
const find = n => ents().filter(e => (e.name||'') === n);
async function trial(label, type) {
  bot.chat('/kill @e[tag=vt]'); await sleep(400);
  bot.chat('/tp @s 200 119 215'); await sleep(1500);
  // villager NORTH of the zombie, player SOUTH: zombie must choose one. Distances: villager 8 from zombie, player 8.
  bot.chat('/summon minecraft:villager 200 119 192 {Tags:["vt"],Invulnerable:1b,NoAI:1b}'); await sleep(500);
  bot.chat(`/summon ${type} 200 119 204 {Tags:["vt"],Invulnerable:1b}`); await sleep(400);
  bot.chat('/effect give @e[tag=vt,type=!minecraft:villager] minecraft:slowness 3 0 true'); await sleep(200);
  await sleep(500);
  const z0 = ents().find(e => e !== bot.entity && e.type !== 'player' && e.name && e.name !== 'villager' && e.position.distanceTo(bot.entity.position) < 30);
  const name = type.split(':')[1];
  const start = z0 ? z0.position.clone() : null;
  await sleep(6000);
  const z1 = ents().find(e => e !== bot.entity && e.type !== 'player' && e.name !== 'villager' && e.position && e.position.distanceTo(bot.entity.position) < 30 && e.id === (z0 && z0.id));
  if (!z0 || !z1) { log(`${label}: could not track mob (start=${!!z0} end=${!!z1})`); return; }
  const dz = z1.position.z - start.z; // north is -z (toward villager at z=192); south is +z (toward player at z=215)
  log(`${label}: zombie z ${start.z.toFixed(1)} -> ${z1.position.z.toFixed(1)}  => ${dz < -1.5 ? 'HEADS TOWARD VILLAGER' : dz > 1.5 ? 'heads toward PLAYER' : 'barely moved'}`);
}
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 300 4 true'); await sleep(300);
  bot.chat('/difficulty hard'); await sleep(300);
  bot.chat('/tp @s 200 119 215'); await sleep(2000);
  await trial('CONTROL minecraft:zombie ', 'minecraft:zombie');
  await trial('TEST    horde_zombie     ', 'emberfall:horde_zombie');
  await trial('TEST    plague_colossus  ', 'emberfall:plague_colossus');
  bot.chat('/kill @e[tag=vt]'); await sleep(300);
  bot.quit(); await sleep(500); process.exit(0);
});
