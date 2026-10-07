// (Foes are emberfall:horde_zombie: RunMobPurge deletes any VANILLA mob inside the box, so a vanilla zombie vanishes and proves nothing.)
// Does anything keep MOBS inside the static map circle (PLAY_RADIUS 93)? The player is guarded by CircleBoundary; the old box guard (ArenaBoundary) pulled strays back
// but skips map arenas. This test measures the answer instead of assuming it.
// Foes are NoAI zombies so ONLY a guard can move them (mob AI cannot walk them home): a NoAI foe outside that is never moved proves there is no guard.
// Positions are read from the SERVER with /data get entity <uuid-free selector by tag>.
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const posOf = async (sel) => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} Pos`, 500); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); if (m) return [+m[1], +m[2], +m[3]]; } return null; };
const radius = p => p ? Math.hypot(p[0], p[2]) : NaN;
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500);
  // Chunks only tick near a player, so the PLAYER stands beside the foes (x=100): both the inside foe (r=88) and the outside foe (r=104) are within a few blocks.
  await ask('/kill @e[tag=stray_test]', 300);
  await ask('/tp @s 96 80 0', 1500); await sleep(2500);
  await ask('/summon emberfall:horde_zombie 88 66 4 {NoAI:1b,Invulnerable:1b,Tags:["stray_test","stray_in"],PersistenceRequired:1b}', 700);
  await ask('/summon emberfall:horde_zombie 104 66 4 {NoAI:1b,Invulnerable:1b,Tags:["stray_test","stray_out"],PersistenceRequired:1b}', 700);
  // The guard runs every second, so ONE chat command must both place the foe outside and read it back in the same server tick.
  // /execute store result score ... run data get entity <foe> Pos[0] gives its x at that instant (x is the radius: z is about 4).
  await ask('/scoreboard objectives add stray_x dummy', 400);
  await ask('/tp @e[tag=stray_out,limit=1] 107 66 4', 0);
  const placed = await ask('/execute as @e[tag=stray_out,limit=1] store result score #placed stray_x run data get entity @s Pos[0]', 0);
  await sleep(300);
  const placedX = await ask('/scoreboard players get #placed stray_x', 500);
  const mm = /has (-?\d+) \[stray_x\]/.exec(placedX); const px = mm ? +mm[1] : NaN;
  check('S0 METHOD: the foe was placed outside the circle (x 107 read back in the same tick, before the guard ran)', px >= 100 && px <= 108, `x=${px} raw="${placedX.slice(0, 80)}"`);
  const in0 = await posOf('@e[tag=stray_in,limit=1]');
  check('S0b the inside foe exists and is readable (r~88)', in0 && Math.abs(radius(in0) - 88) < 2, `r=${radius(in0).toFixed(1)}`);
  // The old guard checked mobs once a second; wait 6 s so a once-a-second guard has had 6 chances.
  await sleep(6000);
  const in1 = await posOf('@e[tag=stray_in,limit=1]'), out1 = await posOf('@e[tag=stray_out,limit=1]');
  check('S1 CONTROL: the foe inside the circle was not moved', in1 && in0 && in1 && Math.abs(radius(in1) - radius(in0)) < 1.5, `r=${radius(in1).toFixed(2)} (was ${radius(in0).toFixed(2)})`);
  check('S2 the foe outside the circle (r~107) was brought back inside (r<=94)', out1 && radius(out1) <= 94, `r=${radius(out1).toFixed(2)} (placed at x=${px})`);
  await ask('/kill @e[tag=stray_test]', 300);
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails); bot.quit(); process.exit(0);
});
