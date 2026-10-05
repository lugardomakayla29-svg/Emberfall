// A Star Bit that KILLS the player ends the run inside StarBitLob.tickAll; the run teardown discards the caster, whose
// remove() calls StarBitLob.clearFor, which edits STARS while tickAll is still iterating it (ConcurrentModificationException).
// This test puts a survival player with NO protection at 1 hp under a real Umbral Magus and waits for a star to land.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 300) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let deaths = 0; bot.on('death', () => deaths++);
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/op EmberTester');
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(2000);
  await ask('/emberfall wavestop 0', 500);
  console.log('spawn:', (await ask('/emberfall spawnelite umbral_magus', 900)).slice(0, 80));
  // stand 9 blocks from the Magus, keep hp at 1 so the first star that lands kills the player
  for (let i = 0; i < 160 && deaths === 0; i++) {
    await ask('/execute at @e[type=emberfall:umbral_magus,limit=1] run tp @s ~9 ~ ~', 120);
    await ask('/effect clear @s', 60);
    // /data merge cannot edit a player ('Unable to modify player data'), so lower hp with /damage instead (hp read from the reply)
    const h = /: ([\d.]+)f/.exec(await ask('/data get entity @s Health', 120));
    if (h && +h[1] > 1.5) await ask(`/damage @s ${(+h[1] - 1).toFixed(1)} minecraft:generic`, 100);
  }
  console.log('deaths seen by the bot:', deaths);
  // The client death event is unreliable here; the run-end is the proof: outside a run the expedition command reports it.
  const left = await ask('/emberfall debugloadout', 500);
  console.log('after-death state reply:', left.slice(0, 100));
  console.log(deaths > 0 ? 'PASS L0 the player died to a star' : 'INFO L0 no client death event (judge by the server log run-end line)');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
