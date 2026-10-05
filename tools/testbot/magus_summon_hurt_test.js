// END TO END: does the player's auto-weapon hurt the REAL Umbral Magus summons (Umbral Thrall, Umbral Colossus)?
// Setup: a run, the real Magus spawned 6 blocks from the player (spawnelite), Magus frozen in place so it stays in reach of the
// weapon, the player shielded. Every 1.5 s we read the hp of every mob named "Umbral Thrall" / Colossus. The Magus itself is kept
// alive with a huge health pool so it keeps casting instead of dying to the weapon.
// PASS S1: at least one real summon existed (non vacuous). PASS S2: a summon lost hp or died while the Magus was alive.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const count = async sel => {
  await ask('/scoreboard objectives add msh dummy', 150); await ask('/scoreboard players set #n msh -1', 150);
  await ask(`/execute store result score #n msh if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n msh', 450); const m = /has (-?\d+) \[msh\]/.exec(r); return m ? +m[1] : NaN;
};
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select ranger'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/time set midnight', 200);
  await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  await ask('/execute at @s run emberfall spawnelite umbral_magus', 1200);
  await ask('/tag @e[type=emberfall:umbral_magus,limit=1] add um', 300);
  await ask('/execute at @s run tp @e[tag=um,limit=1] ~5 ~ ~0', 300);
  await ask('/attribute @e[tag=um,limit=1] minecraft:movement_speed base set 0', 300);
  await ask('/attribute @e[tag=um,limit=1] minecraft:max_health base set 20000', 300);
  await ask('/data modify entity @e[tag=um,limit=1] Health set value 20000.0f', 300);
  await ask('/effect give @e[tag=um] minecraft:fire_resistance 999 0 true', 200);
  let maxThrall = 0, maxColossus = 0, hurt = false, examples = [];
  for (let t = 0; t < 36 && !hurt; t++) {                 // up to about 60 s of casting
    await sleep(1500);
    const th = await count('@e[type=!player,type=!emberfall:umbral_magus,name="Umbral Thrall"]');
    const cl = await count('@e[type=minecraft:wither_skeleton]');
    if (!isNaN(th)) maxThrall = Math.max(maxThrall, th); if (!isNaN(cl)) maxColossus = Math.max(maxColossus, cl);
    // a summon counts as hurt when its Health is below its MaxHealth attribute, read from the SAME entity
    for (const sel of ['@e[name="Umbral Thrall",limit=1,sort=nearest]', '@e[type=minecraft:wither_skeleton,limit=1,sort=nearest]']) {
      const r = await ask(`/data get entity ${sel} Health`, 300); const m = /entity data: (-?\d+(?:\.\d+)?)[fd]/.exec(r);
      if (m) { const hp = parseFloat(m[1]); const mx = await ask(`/attribute ${sel} minecraft:max_health get`, 300); const mm = /value[^\d-]*(-?\d+(?:\.\d+)?)/i.exec(mx);
        if (mm && hp < parseFloat(mm[1]) - 0.5) { hurt = true; examples.push(`${sel.slice(0, 30)} hp ${hp}/${mm[1]}`); } }
    }
  }
  const magusHp = await ask('/data get entity @e[tag=um,limit=1] Health', 400);
  console.log('     most Thralls alive at once:', maxThrall, '| wither skeleton (Colossus) max:', maxColossus, '| magus:', magusHp.slice(-40));
  R('S1 real summons existed (non vacuous)', maxThrall + maxColossus > 0, `thrall ${maxThrall} colossus ${maxColossus}`);
  R('S2 a real summon lost hp to the player weapon', hurt, examples.join('; '));
  await ask('/kill @e[tag=um]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
