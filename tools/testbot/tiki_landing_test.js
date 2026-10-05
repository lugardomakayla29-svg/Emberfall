// After a levitation glide the Tiki must drop onto the player and the landing shove must fire.
// A standing bot's client overrides knockback, so judge from the server: the TIKI_TEST line is written (test server only)
// on the landing edge when the drop followed a glide, right before tantrumPulse runs.
const mineflayer = require('mineflayer');
const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 450) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
const nums = r => [...r.matchAll(/(-?\d+\.?\d*(?:E-?\d+)?)[df]/g)].map(m => parseFloat(m[1]));
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const count = tier => (fs.readFileSync(LOG, 'utf8').match(new RegExp('TIKI_TEST glide landing tier=' + tier, 'g')) || []).length;
async function trial(label, cmd, tier) {
  await ask('/kill @e[type=!player]', 700); await sleep(1500);
  const before = count(tier);
  await ask(cmd, 1500);
  await ask('/tag @e[type=emberfall:tiki_magma,limit=1] add mine', 300);
  await ask('/execute at @s run tp @e[tag=mine,limit=1] 14.5 200 0.5', 500);
  let hovered = false, landedAfter = false;
  const t0 = Date.now();
  while (Date.now() - t0 < 16000) {
    const p = nums(await ask('/data get entity @e[tag=mine,limit=1] Pos', 250));
    if (p.length >= 3) { const h = p[1] - 200; if (h >= 1.4) hovered = true; if (hovered && h < 0.3) landedAfter = true; }
    if (landedAfter) break;
  }
  await sleep(400);
  const n = count(tier) - before;
  check(label + ' P1 hovered then landed', hovered && landedAfter, `hovered ${hovered} landed ${landedAfter}`);
  check(label + ' P2 the drop from the glide ran the landing shove', n >= 1, `${n} glide-landing shove line(s)`);
}
bot.once('spawn', async () => {
  await sleep(5000); await ask('/op EmberTester'); await ask('/gamemode survival');
  await ask('/fill -20 199 -20 20 199 20 minecraft:stone', 1800); await ask('/tp @s 0.5 201 0.5', 1500);
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await trial('VETERAN', '/execute at @s run emberfall spawnveteran tiki_magma', 'NONE');
  await trial('ELITE', '/execute at @s run emberfall spawnelite tiki_magma', 'ELITE');
  await trial('CORRUPTED', '/execute at @s run emberfall spawnelite tiki_magma_corrupted', '(ELITE|CORRUPTED)');
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
