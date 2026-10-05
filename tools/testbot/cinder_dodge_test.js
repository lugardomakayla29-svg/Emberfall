// Cinderfall. Reach phase 3 (two re-lights), keep the last pylon lit, stand in the fight 100 s. The server's CINDERDBG lines
// report each row: tick it landed, which lane was open, the player's along/across and whether they were struck.
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
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);   // 63%: first re-light (phase 2)
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 190f`, 500); await sleep(1500);   // 32%: second re-light (phase 3), keep this pylon lit
  console.log('phase 3 dodge started', new Date().toISOString());
  const fs = require('fs');
  const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
  const readAll = () => fs.readFileSync(LOG, 'utf8').split('\n');
  let seen = readAll().filter(l => l.includes('CINDERDBG begin')).length, dodged = 0;
  const t0 = Date.now();
  while (Date.now() - t0 < 110000) {
    await ask(`/execute as ${G} at @s run tp EmberTester ~9 ~ ~`, 60);
    await sleep(100);
    const all = readAll();
    const begins = all.filter(l => l.includes('CINDERDBG begin')).length;
    if (begins > seen) {
      seen = begins;
      const startedAt = Date.now();
      // The last 4 'safe' lines belong to this Cinderfall: the exact world centre of each row's open lane.
      const safe = all.filter(l => l.includes('CINDERDBG safe')).slice(-4).map(l => { const m = /x=(-?[\d.]+) y=(-?[\d.]+) z=(-?[\d.]+)/.exec(l); return { x: +m[1], y: +m[2], z: +m[3] }; });
      console.log('cinderfall', seen, 'safe lane centres', JSON.stringify(safe.map(s => [s.x, s.z])));
      dodged++;
      for (let r = 0; r < 4; r++) {
        const landAt = startedAt + (36 + 16 * r) * 50;
        // Stand in row r's open lane from just before it lands until just after.
        while (Date.now() < landAt - 250) { await sleep(20); }
        const until = landAt + 350;
        while (Date.now() < until) { await ask(`/tp EmberTester ${safe[r].x.toFixed(2)} ${safe[r].y.toFixed(2)} ${safe[r].z.toFixed(2)}`, 40); await sleep(40); }
      }
    }
  }
  console.log('cinderfalls dodged', dodged);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
