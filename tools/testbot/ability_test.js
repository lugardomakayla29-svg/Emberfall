// Usage: node ability_test.js <staff|bow> <tier 1-3> <seconds>
// staff => Arcane Staff (battlemage) + piercing_laser xN ; bow => Hunting Bow (ranger) + spin_barrage xN
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const KIND = process.argv[2], TIER = parseInt(process.argv[3] || '1', 10), SECS = parseInt(process.argv[4] || '40', 10);
const CHAR = KIND === 'staff' ? 'battlemage' : 'ranger';
const TOME = KIND === 'staff' ? 'piercing_laser' : 'spin_barrage';
const pk = [], snd = []; let on = false; const chat = [];
bot._client.on('packet', (d, m) => {
  if (!on) return;
  if (m.name === 'world_particles' && d.particle) pk.push({ t: Date.now(), x: d.x, y: d.y, z: d.z, type: d.particle.type, c: d.particle.data && d.particle.data.color });
  if (m.name === 'sound_effect') snd.push({ t: Date.now(), vol: d.volume, pitch: d.pitch, id: d.soundId });
});
bot.on('message', m => { const t = m.toString(); chat.push(t); if (/entity data|Unknown|Started|expedition|granted|Health|Tome/i.test(t)) console.log('CHAT:', t.slice(0, 170)); });
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  await c('/gamemode survival', 700);
  await c('/character select ' + CHAR, 700);
  await c('/expedition leave', 800);
  await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  await c('/kill @e[type=!player,tag=!keep,distance=..60]', 800);
  for (let i = 0; i < (process.env.NOTOME === "1" ? 0 : TIER); i++) await c(`/emberfall granttome EmberTester ${TOME}`, 700);
  // Three held-still targets in a rough line so the beam can pierce more than one.
  const places = process.env.RING === '1'
    ? [['t1', 4.0, 0], ['t2', -2.0, 3.46], ['t3', -2.0, -3.46]]
    : [['t1', 4.5, 0], ['t2', 9.0, 0], ['t3', 13.5, 0]];
  for (const [t, x, z] of places) {
    await c(`/execute at @s run summon emberfall:horde_zombie ~${x} ~ ~${z} {Tags:["${t}"],Silent:1b,PersistenceRequired:1b}`, 500);
    await c(`/attribute @e[tag=${t},limit=1] minecraft:movement_speed base set 0`, 400);
    await c(`/attribute @e[tag=${t},limit=1] minecraft:max_health base set 1000`, 400);
    await c(`/effect give @e[tag=${t}] minecraft:instant_health 1 10 true`, 400);
  }
  // Make t3 nearly dead so the execute (under 30% health) is observable.
  if (process.env.NOEXEC !== '1') await c('/data merge entity @e[tag=t3,limit=1] {Health:100f}', 500);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1000);
  // Wave director keeps spawning real mobs; remove anything that is not one of our three targets so the
  // beam's "nearest hostile" is always ours. tag=!t* would need three clauses, so kill by absence of tags.
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,tag=!t1,tag=!t2,tag=!t3,tag=!keep,distance=..60]'), 1500);
  const hold = setInterval(() => {
    for (const [t, x, z] of places) setTimeout(() => bot.chat(`/execute at @s run tp @e[tag=${t},limit=1] ~${x} ~ ~${z}`), 150 * places.findIndex(p => p[0] === t));
  }, 1000);
  on = true; await sleep(SECS * 1000); on = false; clearInterval(pin); clearInterval(hold); clearInterval(purge);
  await c('/data get entity @e[tag=t1,limit=1] Health', 1400);
  await c('/data get entity @e[tag=t2,limit=1] Health', 1400);
  await c('/data get entity @e[tag=t3,limit=1] Health', 1400);
  await c('/execute if entity @e[type=minecraft:arrow]', 1400);
  await c('/kill @e[tag=t1]', 300); await c('/kill @e[tag=t2]', 300); await c('/kill @e[tag=t3]', 300);
  await c('/expedition leave', 800);
  require('fs').writeFileSync(`${process.env.EMBERFALL_HOME}/bot/out/ability_${KIND}_${TIER}.json`, JSON.stringify({ pk, snd, chat }));
  console.log(KIND, 'tier', TIER, 'particles', pk.length, 'sounds', snd.length);
  bot.quit(); process.exit(0);
});
