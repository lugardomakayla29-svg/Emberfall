const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const count = async sel => {
  await ask('/scoreboard objectives add gwc dummy', 200); await ask('/scoreboard players set #n gwc -1', 200);
  await ask(`/execute store result score #n gwc if entity ${sel}`, 300);
  const r = await ask('/scoreboard players get #n gwc', 500); const m = /has (-?\d+) \[gwc\]/.exec(r) || /(-?\d+)/.exec(r); return m ? +m[1] : NaN; };
const countR = async sel => { for (let k = 0; k < 4; k++) { const v = await count(sel); if (!Number.isNaN(v) && v >= 0) return v; } return NaN; };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const PYL = '@e[type=emberfall:cinder_pylon]';
const hp1 = async () => { const r = await ask(`/data get entity ${G} Health`, 500); const m = /: ([\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
const hp = async () => { for (let k = 0; k < 4; k++) { const v = await hp1(); if (!Number.isNaN(v)) return v; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  // The map builds async (about 32 s). Poll the dimension so the checks below run in the RUN, not in the hub, and assert it (R0).
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  await sleep(1500);
  check('R0 the bot is inside a run (nothing below means anything in the hub)', inRun, `inRun=${inRun}`);
  console.log('boss:', (await ask('/emberfall boss 0', 1200)).slice(0, 60)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  await ask('/kill @e[type=!player,type=!emberfall:ember_guardian,type=!emberfall:cinder_pylon,type=!minecraft:item_display]', 600);
  const p0 = await countR(PYL); console.log('pylons at start:', p0);
  check('W0 pylons present', p0 >= 1, `pylons=${p0}`);
  // Stand the player next to the nearest pylon and let the REAL auto-weapon work. No kill/damage command touches a pylon or the boss.
  let last = p0; const log = [];
  for (let t = 0; t < 30 && last > 0; t++) {
    await ask('/execute as @e[type=emberfall:cinder_pylon,limit=1,sort=nearest] at @s run tp @p ~1.2 ~ ~', 300);
    await sleep(1500); last = await countR(PYL); log.push(last);
  }
  console.log('pylons over time (weapon only):', log.join(' '));
  check('W1 the auto-weapon alone breaks every pylon', last === 0, `pylons left=${last}`);
  // The last pylon is ~8-12 blocks from the boss, beyond melee reach, so bring the player to the boss before judging weapon damage.
  await ask(`/execute as ${G} at @s run tp @p ~1.5 ~ ~`, 500);
  const h = await hp(); await sleep(3500); const h2 = await hp();
  check('W2 with pylons gone the auto-weapon now damages the boss', h2 < h || h < 600, `hp ${h} -> ${h2}`);
  await ask('/kill @e[type=emberfall:ember_guardian]', 800); await sleep(3500);
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
