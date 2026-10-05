// Phase 2 Sweeping Beam. The boss is pushed into phase 2 (pylons broken, health set to 60%, which re-lights one pylon,
// then that pylon is broken). Bystanders stand where the beam will go. Hits are read from the server's BEAMDBG line,
// never from hp. A: on the beam line. B: 4 blocks off the line. The pillar is placed by the boss itself.
const mineflayer = require('mineflayer');
const mk = u => mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: u, version: '1.21.11', auth: 'offline' });
const bot = mk('EmberTester'); const onLine = mk('OnLine'); const offLine = mk('OffLine');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const num = async (sel, path) => { for (let k = 0; k < 4; k++) { const r = await ask(`/data get entity ${sel} ${path}`, 400); const m = /(-?[\d.]+)[fdb]?$/.exec(r.trim()); if (m) return +m[1]; } return NaN; };
bot.once('spawn', async () => {
  await sleep(8000);
  for (const u of ['OnLine', 'OffLine']) { await ask(`/gamemode survival ${u}`, 250); await ask(`/effect give ${u} minecraft:resistance 999 4 true`, 250); await ask(`/effect give ${u} minecraft:regeneration 999 4 true`, 250); }
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall boss 0', 900); await ask('/emberfall wavestop 0', 300);
  // Break the phase 1 pylons, then drop health below 66% to re-light one, and break that too: the boss is now in phase 2 and gated again.
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(1500);
  console.log('phase-2 pylons after relight:', (await ask('/execute if entity @e[type=emberfall:cinder_pylon]', 400)).slice(0, 60));
  // Make the ground a perfect flat test patch around the boss so a pillar can ALWAYS be placed: solid floor at the boss's feet
  // level - 1, clear air above it. This isolates the beam/cover logic from the arena's uneven terrain.
  const by = Math.floor(await num(G, 'Pos[1]'));
  await ask(`/execute as ${G} at @s run fill ~-4 ${by - 1} ~-4 ~26 ${by - 1} ~12 minecraft:stone`, 900);
  await ask(`/execute as ${G} at @s run fill ~-4 ${by} ~-4 ~26 ${by + 6} ~12 minecraft:air`, 900);
  // Freeze the boss (movement is verified separately) so the beam direction and the bystanders stay put: the test is about the beam and the cover.
  await ask(`/data modify entity ${G} NoAI set value 1b`, 400);

  // Hold the run player and the two bystanders near the boss every 150 ms: EmberTester 6 east (the aim target), OnLine 9 east
  // (on the line), OffLine 5 east and 5 north (off the line).
  let pin = true;
  (async () => { while (pin) {
    await ask(`/execute as ${G} at @s run tp EmberTester ~6 ~ ~`, 50);
    await ask(`/execute as ${G} at @s run tp OnLine ~9 ~ ~`, 50);
    await ask(`/execute as ${G} at @s run tp OffLine ~5 ~ ~5`, 50);
    await sleep(100); } })();
  await sleep(44000); pin = false; await sleep(500);
  bot.quit(); onLine.quit(); offLine.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
