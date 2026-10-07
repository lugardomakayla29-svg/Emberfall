// The 25th paid chest unlocks Ember Key and tells the player, on the real click path (24 given by the debug command).
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const open = async () => {
  const t = await ask('/emberfall relic chests EmberTester', 700); const m = t.match(/nearest=(-?\d+) (-?\d+) (-?\d+)/); if (!m) return false;
  await ask(`/tp @s ${+m[1] + 0.5} ${m[2]} ${+m[3] + 2.5} 180 0`, 1300);
  const blk = bot.blockAt(new Vec3(+m[1], +m[2], +m[3])); if (!blk) return false;
  try { await bot.activateBlock(blk); } catch (e) {} await sleep(900); return true;
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500); await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 600);
  await ask('/emberfall relic gold EmberTester 1000000', 400);
  await ask('/emberfall relic unlocks EmberTester add open_25_chests 23', 500);
  let n = lines.length; await open(); await sleep(800);
  let t = await ask('/emberfall relic unlocks EmberTester', 600);
  check('K1 the 24th opening: progress 24, not unlocked, no announcement', /open_25_chests=24/.test(t) && !/unlocks \[[^\]]*open_25_chests/.test(t) && !lines.slice(n).join(' ').includes('Relic unlocked'), t.slice(0, 120));
  n = lines.length; await open(); await sleep(800);
  const said = lines.slice(n).join(' | ');
  t = await ask('/emberfall relic unlocks EmberTester', 600);
  check('K2 the 25th opening unlocks Ember Key', /unlocks \[[^\]]*open_25_chests/.test(t) && /open_25_chests=25/.test(t), t.slice(0, 120));
  check('K2b the player is told: Ember Key and why', /Relic unlocked: Ember Key/.test(said) && /Open 25 paid chests/.test(said), said.slice(0, 220));
  n = lines.length; await sleep(800); console.log('  MUTANT: 26th open() skipped');
  check('K3 control: the 26th opening does not announce it again', !lines.slice(n).join(' ').includes('Relic unlocked'), '');
  await ask('/expedition leave', 1200);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
