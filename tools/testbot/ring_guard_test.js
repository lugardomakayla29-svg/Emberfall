// Magma Ring. One player, phase 2 reached the same way as the beam test. The server's RINGDBG lines are the judge:
// each burst reports the player's distance from the fight centre and whether the server put them in the band.
// The player is held at three distances around the safe radius 11: inside (6), in the band (16), and beyond the outer edge (30).
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);
  // The re-lit pylon stays lit on purpose: a lit pylon keeps the boss gated, and only a gated boss attacks.
  await sleep(300);
  console.log('started phase-2 hold at', new Date().toISOString());
  // The ring is anchored to the point where the fight STARTED, not to the boss, which walks toward the player. So read the
  // boss's start position once (before it walks far) and hold the player at fixed offsets from THAT point.
  // The boss has already walked a little during setup, so take the centre from the server's own RINGDBG line instead:
  // it is printed as centre=(x, y, z). Here we read it from the first burst by holding the player in the band first.
  const C = { x: null, y: null, z: null };
  // Phase A: hold the player 16 blocks east of the boss until the server prints a centre.
  const centreFromLog = () => { const l = require('fs').readFileSync((process.env.EMBERFALL_HOME || '.') + '/run/server_run.log', 'utf8').split('\n').filter(x => x.includes('RINGDBG resolve')); if (!l.length) return null; const m = /centre=\(([-\d.]+), ([-\d.]+), ([-\d.]+)\)/.exec(l[l.length - 1]); return m ? { x: +m[1], y: +m[2], z: +m[3] } : null; };
  let t0 = Date.now();
  while (!centreFromLog() && Date.now() - t0 < 40000) { await ask(`/execute as ${G} at @s run tp EmberTester ~16 ~ ~`, 60); await sleep(150); }
  const c = centreFromLog();
  console.log('centre from server log:', JSON.stringify(c));
  if (!c) { console.log('NO CENTRE'); bot.quit(); setTimeout(() => process.exit(0), 300); return; }
  // Phase B: hold at fixed distances from the centre, due east, for 30 s each: inside 5, band 15, just beyond the edge 27.
  for (const d of [5, 15, 27]) {
    const t1 = Date.now();
    console.log('HOLD centre+' + d, 'at', new Date().toISOString());
    while (Date.now() - t1 < 30000) {
      await ask(`/tp EmberTester ${(c.x + d).toFixed(2)} ${c.y.toFixed(2)} ${c.z.toFixed(2)}`, 60);
      await sleep(150);
    }
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
