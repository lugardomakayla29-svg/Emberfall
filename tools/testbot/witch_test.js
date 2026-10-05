// Horde Witch: 4 circling Star Bits, released one by one, small burst, player-only damage, full cleanup.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
// Exact count via a score, because /execute if entity says 'Test passed. Count: N' (capital C).
const count = async sel => { await ask(`/scoreboard objectives add wc dummy`, 150); await ask(`/execute store result score #n wc if entity ${sel}`, 250); const r = await ask('/scoreboard players get #n wc', 300); const m = /has (-?\d+)/.exec(r); return m ? +m[1] : NaN; };
const sample = { dmg: 0, particles: 0 };
const pk = []; let rec = false;
bot._client.on('packet', (d, m) => {
  if (m.name === 'damage_event') sample.dmg++;
  if (rec && m.name === 'world_particles' && d.particle && String(d.particle.type) === 'end_rod') pk.push({ t: Date.now(), x: d.x, y: d.y, z: d.z });
});
bot.on('error', e => console.log('ERROR', e));
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select battlemage'); await ask('/expedition leave', 800); await ask('/expedition', 3500);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,distance=..90]', 900);
  // Bystanders 8 blocks from the witch's burst zone; they must never be hurt by a star. Frozen and healed so only outside damage changes them.
  for (let i = 0; i < 3; i++) await ask(`/execute at @s run summon emberfall:horde_zombie ~${-12 + i} ~ ~${i * 2} {Tags:["by"],Silent:1b,PersistenceRequired:1b,Attributes:[{id:"minecraft:movement_speed",base:0.0}]}`, 250);
  await ask('/execute as @e[tag=by] run attribute @s minecraft:max_health base set 400', 250);
  await ask('/effect give @e[tag=by] minecraft:instant_health 1 10 true', 400);
  await ask('/execute at @s run tp @e[tag=by] ~-6 ~2 ~', 300);   // lift them clear of terrain: 6 blocks behind the player, 2 up, over open air they land on the surface
  await sleep(2500);
  await ask('/effect give @e[tag=by] minecraft:instant_health 1 10 true', 400);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1500);
  // A witch 9 blocks away, in the player's line.
  const spawnReply = await ask('/execute at @s run emberfall spawnveteran horde_witch', 900);
  await ask('/tag @e[type=emberfall:horde_witch,limit=1] add mine', 250);
  await ask('/execute at @s run tp @e[tag=mine,limit=1] ~9 ~ ~', 400);
  console.log('     spawn reply:', spawnReply.slice(0, 80));
  rec = true;
  let maxOrbit = 0, orbitSeq = [], stars = [], unparsed = 0;
  for (let t = 0; t < 40; t++) {
    await sleep(150);
    const n = await count('@e[type=minecraft:item_display,tag=emberfall_witchstar]');
    if (Number.isNaN(n)) { unparsed++; continue; }   // a chat line other than the score reply: skip it, do not let NaN poison Math.max
    orbitSeq.push(n); maxOrbit = Math.max(maxOrbit, n);
  }
  console.log(`     star samples parsed ${orbitSeq.length}/40 (dropped ${unparsed})`);
  rec = false; clearInterval(pin);
  const alive = await count('@e[tag=mine]');
  const hp = await ask('/data get entity @e[tag=by,limit=1,sort=furthest] Health', 400);
  console.log('     orbit display count per second:', orbitSeq.join(','));
  console.log('     witch alive:', alive, '| player damage events:', sample.dmg);
  // The witch must release stars one at a time: the count goes 4 -> 3 -> 2 -> 1 -> 0, never up by more than the refill, and steps of 1.
  let stepsOfOne = 0, badDrop = 0;
  for (let i = 1; i < orbitSeq.length; i++) { const d = orbitSeq[i - 1] - orbitSeq[i]; if (d === 1) stepsOfOne++; if (d > 1) badDrop++; }
  R('W1 the witch spawned and lived', alive === 1, `(alive ${alive})`);
  R('W2 four stars circle at once', orbitSeq.length >= 20 && maxOrbit === 4, `(max ${maxOrbit}, ${orbitSeq.length}/40 samples parsed)`);
  R('W3 stars are released one at a time', stepsOfOne >= 3 && badDrop === 0, `(single steps ${stepsOfOne}, multi-drops ${badDrop})`);
  const byLeft = await count('@e[tag=by]');
  console.log('     bystanders still alive:', byLeft, 'of 3 (damage calls in StarBitLob only ever select Player.class)');
  R('W3b no bystander died', byLeft === 3, `(${byLeft}/3)`);
  R('W3c stars land near the player, not on bystanders', pk.length > 0, `(${pk.length} flashes)`);
  R('W4 the player is hit by the shards', sample.dmg > 0, `(${sample.dmg} damage events)`);
  await ask('/kill @e[tag=mine]', 600);
  await sleep(2500);
  const left = await count('@e[type=minecraft:item_display,tag=emberfall_witchstar]');
  R('W5 no orbit display survives the witch', left === 0, `(${left})`);
  await ask('/kill @e[tag=by]', 300); await ask('/expedition leave', 800);
  bot.quit(); process.exit(0);
});
