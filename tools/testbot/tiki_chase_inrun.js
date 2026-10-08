// In-run Tiki chase: waits for Dimension=expedition (the map takes about 32 s to build) before spawning. Measured 2026-10-08: 6 of 6 veterans chase,
// 143 of 144 server trace samples have a target. Edit DIST / ONLY below for other tiers (fodder, veteran, elite, corrupted).
// Tiki lineup end to end, inside a real run: each case starts DIST blocks away. It must (A) close the distance to a
// pinned player. Facing and target are judged from the server trace TIKI_FACE afterwards (hasTarget, head vs bearing).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const pos = s => { const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(s); return m ? [+m[1], +m[2], +m[3]] : null; };
const DIST = parseInt('26', 10);
const ONLY = ('veteran').split(',');
const CASES = { fodder: 'summon emberfall:tiki_magma ~ ~ ~ {Tags:["probe"],PersistenceRequired:1b}',
  veteran: 'emberfall spawnveteran tiki_magma', elite: 'emberfall spawnelite tiki_magma',
  corrupted: 'emberfall spawnelite tiki_magma_corrupted', pink_slime: 'emberfall spawnelite pink_slime' };
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/character select vanguard', 700); await ask('/expedition leave', 800); await ask('/expedition', 1500);
  { const t0 = Date.now(); let inRun = false;
    for (let i = 0; i < 80; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) { inRun = true; break; } }
    console.log('INRUN', inRun, 'after', ((Date.now() - t0) / 1000).toFixed(1), 's'); if (!inRun) { console.log('RESULT NOT IN A RUN'); bot.quit(); setTimeout(() => process.exit(0), 300); return; }
    await sleep(2500); }
  await ask('/effect give @s minecraft:resistance 900 4 true', 300); await ask('/effect give @s minecraft:regeneration 900 4 true', 300);
  await ask('/emberfall wavestop 0', 500);
  await ask('/kill @e[type=!player]', 800);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 500);
  const me = pos(await ask('/data get entity @s Pos', 700));
  console.log('player', me);
  for (const name of ONLY) {
    await ask('/kill @e[type=!player]', 700); await sleep(500);
    console.log('CASE_BEGIN', name);
    if (name === 'fodder') {
      // the real fodder path is the egg (TikiMagma.spawn(..., false)); click the floor at the bot's feet, then move the mob out
      await ask('/gamemode creative', 400);
      await ask('/item replace entity @s hotbar.0 with emberfall:tiki_magma_egg', 500);
      bot.setQuickBarSlot(0); await sleep(300);
      const { Vec3 } = require('vec3');
      const under = bot.blockAt(bot.entity.position.offset(0, -1, 0).floored());
      try { await Promise.race([bot.activateBlock(under, new Vec3(0, 1, 0)), sleep(2500)]); } catch (e) {}
      await sleep(900);
      await ask('/gamemode survival', 400);
      await ask('/tag @e[type=emberfall:tiki_magma,limit=1,sort=nearest] add probe', 400);
      await ask(`/execute at @s run tp @e[tag=probe,limit=1] ~${DIST} ~ ~`, 600);
    } else {
      await ask(`/execute at @s positioned ~${DIST} ~ ~ run ${CASES[name]}`, 1500);
      await ask('/tag @e[type=!player,type=!minecraft:item_display,type=!minecraft:block_display,type=!emberfall:tiki_cube,limit=1,sort=nearest,distance=..60] add probe', 400);
    }
    const p0 = pos(await ask('/data get entity @e[tag=probe,limit=1] Pos', 700));
    if (!p0) { console.log('RESULT', name, 'NOT FOUND after spawn'); continue; }
    const d0 = Math.hypot(p0[0] - me[0], p0[2] - me[2]);
    await sleep(9000);
    const p1 = pos(await ask('/data get entity @e[tag=probe,limit=1] Pos', 700));
    if (!p1) { console.log('RESULT', name, 'died/gone'); continue; }
    const d1 = Math.hypot(p1[0] - me[0], p1[2] - me[2]);
    console.log(`RESULT ${name.padEnd(11)} start ${d0.toFixed(1)} -> after 9s ${d1.toFixed(1)}  closed ${(d0 - d1).toFixed(1)}`);
    console.log('CASE_END', name);
  }
  clearInterval(pin);
  await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
