// Cinderfall. Reach phase 3 (two re-lights), keep the last pylon lit, stand in the fight 100 s. The server's CINDERDBG lines
// report each row: tick it landed, which lane was open, the player's along/across and whether they were struck.
const mineflayer = require('mineflayer');
const MODE = process.argv[2] || 'still';
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
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);   // 63%: first re-light (phase 2)
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 190f`, 500); await sleep(1500);   // 32%: second re-light (phase 3), keep this pylon lit
  console.log('phase 3 dodge started', new Date().toISOString());
  const fs = require('fs');
  const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
  const readAll = () => fs.readFileSync(LOG, 'utf8').split('\n');
  let seen = readAll().filter(l => l.includes('CINDERDBG begin')).length, dodged = 0;
  let lastCastAt = 0;
  const t0 = Date.now();
  while (Date.now() - t0 < 110000) {
    if (Date.now() - lastCastAt > 7000) { await ask(`/execute as ${G} at @s run tp EmberTester ~9 ~ ~`, 60); }   // never move the bot mid-attack
    await sleep(100);
    const all = readAll();
    const begins = all.filter(l => l.includes('CINDERDBG begin')).length;
    if (begins > seen) {
      seen = begins;
      lastCastAt = Date.now();
      const startedAt = Date.now();
      // The last 4 'safe' lines belong to this Cinderfall: the exact world centre of each row's open lane.
      const safe = all.filter(l => l.includes('CINDERDBG safe')).slice(-4).map(l => { const m = /x=(-?[\d.]+) y=(-?[\d.]+) z=(-?[\d.]+)/.exec(l); return { x: +m[1], y: +m[2], z: +m[3] }; });
      console.log('cinderfall', seen, 'safe lane centres', JSON.stringify(safe.map(s => [s.x, s.z])));
      dodged++;
      // WALK, do not teleport. Stand at the first safe centre, then for each later row walk (no sprint) toward that row's safe
      // centre, re-aiming every 50 ms, and hold position once there. The claim being tested is that plain walking is enough.
      bot.setControlState('sprint', false);
      if (MODE === 'still') { await sleep(36 * 50 + 3 * 24 * 50 + 300); continue; }
      await ask(`/tp EmberTester ${safe[0].x.toFixed(2)} ${safe[0].y.toFixed(2)} ${safe[0].z.toFixed(2)}`, 60);
      for (let r = 1; r < 4; r++) {
        const landPrev = startedAt + (36 + 24 * (r - 1)) * 50;
        while (Date.now() < landPrev + 50) { await sleep(10); }   // start walking as soon as the previous row has landed
        const landNext = startedAt + (36 + 24 * r) * 50;
        // Face the next safe centre ONCE, then just hold forward and stop when within reach: re-aiming every 30 ms made the
        // bot slower than a player (it ended 4 to 12 blocks short).
        { const p = bot.entity.position; await bot.lookAt(p.offset(safe[r].x - p.x, 0, safe[r].z - p.z), true); bot.setControlState('forward', true); }
        while (Date.now() < landNext - 30) {
          const p = bot.entity.position;
          if (Math.hypot(safe[r].x - p.x, safe[r].z - p.z) < 0.35) { bot.setControlState('forward', false); }
          await sleep(15);
        }
        bot.setControlState('forward', false);
        const p2 = bot.entity.position; console.log(`row ${r}: ended ${Math.hypot(safe[r].x - p2.x, safe[r].z - p2.z).toFixed(2)} from safe centre`);
      }
    }
  }
  console.log('cinderfalls dodged', dodged);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
