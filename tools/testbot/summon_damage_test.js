// Can the player's auto-weapon hurt VANILLA-typed summons (the Umbral Magus's Thrall = zombie/skeleton/husk, Colossus = wither
// skeleton, the Bonecaller's zombie horse / zombie villager)? Before the fix isEmberfallHostile only accepted the emberfall
// namespace, so no weapon ever targeted them.
// The weapon picks ONE nearest target per swing, so each subject is tested ALONE: 1) place exactly one named, frozen,
// fire proof 200 hp subject 2.5 blocks from the player, 2) let the weapon work for up to 15 s, 3) PASS when it lost hp or died.
// CONTROL = emberfall:horde_zombie (must be hurt, else the weapon never fired). NEGATIVE CONTROL = a tamed wolf (must NOT be hurt).
// Facts learned the hard way: instant_health HARMS undead (killed every target); undead burn in daylight (fake damage), so
// midnight + fire_resistance; a plain emberfall mob dies in 2 s unless its health is raised away from the player first;
// a NAME is what lets a vanilla-typed mob survive RunMobPurge (the Magus names its Thrall); /data replies can be mixed with
// '+5 XP' chat lines, so parse 'entity data: <n>f' by text.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const SUBJECTS = ['minecraft:zombie', 'minecraft:husk', 'minecraft:skeleton', 'minecraft:wither_skeleton', 'minecraft:zombie_horse', 'minecraft:zombie_villager'];
const NAME = `CustomName:'{"text":"Test Summon"}'`;
const hp = async () => {
  for (let k = 0; k < 3; k++) {
    const r = await ask('/data get entity @e[tag=subj,limit=1] Health', 320);
    const m = /entity data: (-?\d+(?:\.\d+)?)[fd]/.exec(r); if (m) return parseFloat(m[1]);
    if (/No entity was found/.test(r)) return NaN;
  }
  return NaN;
};
// place one subject 2.5 blocks from the player, prepared so nothing but the weapon can change its hp
const boost = async () => {
  await ask('/attribute @e[tag=subj,limit=1] minecraft:max_health base set 200', 250);
  await ask('/data modify entity @e[tag=subj,limit=1] Health set value 200.0f', 250);
};
async function trial(type, { away = false, owner = null } = {}) {
  await ask('/kill @e[tag=subj]', 300);
  const common = `Tags:["subj"],NoAI:1b,Silent:1b,PersistenceRequired:1b`;
  const own = owner ? `,Owner:${owner}` : '';
  if (away) {                   // emberfall mobs are killed in 2 s, so raise health 60 blocks away first then walk it in
    await ask(`/execute at @s run summon ${type} ~60 ~ ~0 {${common},${NAME}}`, 300);
    await boost();
    await ask('/effect give @e[tag=subj] minecraft:fire_resistance 999 0 true', 200);
    await ask('/execute at @s run tp @e[tag=subj,limit=1] ~2.5 ~ ~0', 300);
  } else {
    await ask(`/execute at @s run summon ${type} ~2.5 ~ ~0 {${common},${NAME}${own}}`, 300);
    await boost();
    await ask('/effect give @e[tag=subj] minecraft:fire_resistance 999 0 true', 200);
  }
  const before = await hp(); let after = before, died = false;
  for (let t = 0; t < 15; t++) {
    await sleep(1000); const h = await hp();
    if (isNaN(h)) { died = true; break; } after = h; if (after < before - 1) break;
  }
  await ask('/kill @e[tag=subj]', 200);
  return { before, after, died };
}
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300);
  await ask('/time set midnight', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  // control: must be hurt or killed, else the weapon is not firing at all
  const c = await trial('emberfall:horde_zombie', { away: true });
  R('S1 control (emberfall horde zombie) is hurt, so the weapon fires', !isNaN(c.before) && c.before >= 190 && (c.died || c.after < c.before - 1), JSON.stringify(c));
  for (const t of SUBJECTS) {
    const r = await trial(t);
    R(`S2 ${t} is hurt by the weapon`, !isNaN(r.before) && r.before >= 190 && (r.died || r.after < r.before - 1), JSON.stringify(r));
  }
  // negative control: a tamed wolf (a player's companion) must never be hurt
  const uu = await ask('/data get entity @s UUID', 500); const um = /\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/.exec(uu);
  R('S3a read the player UUID', !!um);
  const owner = um ? `[I;${um[1]},${um[2]},${um[3]},${um[4]}]` : '[I;0,0,0,0]';
  const w = await trial('minecraft:wolf', { owner });
  R('S3b negative control: a tamed wolf is NOT hurt', !isNaN(w.before) && !w.died && w.after >= w.before - 0.01, JSON.stringify(w));
  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
