// Clover, Ember Key and Ember Ledger on the REAL chest click path (ChestManager.tryOpen), each against a control run.
// The server trace OPEN_TEST (kind, rarity, key, cost, counter, luck) is graded by relic_econ_grade.py.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 500) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const nearest = async () => { const t = await ask('/emberfall relic chests EmberTester', 600); const m = t.match(/closed=(\d+) free=(\d+) nearest=(-?\d+) (-?\d+) (-?\d+) kind=(\w+)/); return m ? { closed: +m[1], free: +m[2], x: +m[3], y: +m[4], z: +m[5], kind: m[6] } : null; };
  const startRun = async tag => {
    await c('/expedition leave', 1200); await c('/expedition', 4000);
    for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
    await c('/effect give @s minecraft:resistance 999 4 true', 200); await c('/effect clear @s minecraft:slowness', 100);
    await c('/emberfall wavestop 0', 300); await c('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:item]', 300);
    await c('/say ECON_RUN ' + tag, 300); console.log('RUN ' + tag);
  };
  const openNearest = async () => {
    const ch = await nearest(); if (!ch || ch.closed === 0) return false;
    await c(`/tp @s ${ch.x + 0.5} ${ch.y} ${ch.z + 2.5} 180 0`, 1300);
    const blk = bot.blockAt(new Vec3(ch.x, ch.y, ch.z)); if (!blk) return false;
    try { await bot.activateBlock(blk); } catch (e) {} await sleep(800); return true;
  };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  // A: control, no relics: 8 paid chests, the price must climb
  await startRun('A_control'); await c('/emberfall relic gold EmberTester 100000', 400);
  for (let i = 0; i < 8; i++) await openNearest();
  // B: Ledger: same 8 openings, the price must stay flat
  await startRun('B_ledger'); await c('/emberfall relic give EmberTester ember_ledger 1', 400); await c('/emberfall relic gold EmberTester 100000', 400);
  for (let i = 0; i < 8; i++) await openNearest();
  // C: Key x5 (50%): all 16 paid chests, free openings expected
  await startRun('C_key'); await c('/emberfall relic give EmberTester ember_key 5', 400); await c('/emberfall relic gold EmberTester 1000000', 400);
  for (let i = 0; i < 16; i++) await openNearest();
  // D: Clover control vs 10 Clover, free chests only (no gold, no key), 12 per run, 3 runs each
  for (const [tag, stacks] of [['D0', 0], ['D10', 10], ['D0', 0], ['D10', 10], ['D0', 0], ['D10', 10]]) {
    await startRun(tag + '_clover'); if (stacks) await c(`/emberfall relic give EmberTester clover ${stacks}`, 400);
    for (let i = 0; i < 12; i++) { await c('/emberfall relic free EmberTester boss', 350); }
    for (let i = 0; i < 12; i++) await openNearest();
  }
  await c('/expedition leave', 800); console.log('DRIVER_DONE'); bot.quit(); setTimeout(() => process.exit(0), 400);
});
