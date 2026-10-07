// V3 live: a looted PAID/GOLD chest comes back once per run, and a second respawn does not happen.
// Start the server with EXTRA_JVM="-Demberfall.chestRespawnRoll=0.0" so every roll is a hit; then only the one-per-run cap can stop
// a second respawn. The server log is graded by chestrespawn_grade.py (exactly one CHEST_RESPAWN line).
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 600) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const state = async () => { const t = await ask('/emberfall relic state EmberTester', 700); return { opened: +(t.match(/opened=(\d+)/)?.[1] ?? NaN), price: +(t.match(/price=(\d+)/)?.[1] ?? NaN), owned: t.match(/owned=(\{[^}]*\})/)?.[1] ?? '' }; };
  const setGold = async n => { await c(`/emberfall relic gold EmberTester ${n}`, 400); };
  const chests = async () => { const t = await ask('/emberfall relic chests EmberTester', 700); const m = t.match(/total=(\d+) closed=(\d+) free=(\d+) nearest=(-?\d+) (-?\d+) (-?\d+) kind=(\w+)/); return m ? { total: +m[1], closed: +m[2], free: +m[3], x: +m[4], y: +m[5], z: +m[6], kind: m[7] } : { raw: t }; };
  const isOpen = async (x, y, z) => { for (const b of ['ember_chest_paid', 'ember_chest_gold']) if ((await ask(`/execute if block ${x} ${y} ${z} emberfall:${b}[opened=true] run say OPENYES`, 400)).includes('OPENYES')) return true; return false; };
  const isClosedChest = async (x, y, z) => { for (const b of ['ember_chest_paid', 'ember_chest_gold']) if ((await ask(`/execute if block ${x} ${y} ${z} emberfall:${b}[opened=false] run say CLOSEDYES`, 400)).includes('CLOSEDYES')) return true; return false; };
  const click = async (x, y, z) => { const blk = bot.blockAt(new Vec3(x, y, z)); if (!blk) return 'noblock'; try { await bot.activateBlock(blk); } catch (e) { return 'err ' + e.message; } await sleep(1000); return 'ok'; };
  const goBeside = async ch => { await c(`/tp @s ${ch.x + 0.5} ${ch.y} ${ch.z + 2.5} 180 0`, 1500); };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  await c('/emberfall wavestop 0', 300); await c('/effect give @s minecraft:resistance 999 4 true', 200);

  let ch = await chests();
  check('V0 run start registers 16 closed chests', ch.total === 16 && ch.closed === 16, JSON.stringify(ch));
  const A = { x: ch.x, y: ch.y, z: ch.z, kind: ch.kind };
  check('V0b the first chest is a kind that can come back (PAID or GOLD)', A.kind === 'PAID' || A.kind === 'GOLD', A.kind);

  // 1 first loot: gold given, forced roll hits, the chest is re-armed
  await setGold(100000); await goBeside(A);
  const s1a = await state();
  check('R1 click delivered', (await click(A.x, A.y, A.z)) === 'ok');
  const s1b = await state(); const ch1 = await chests();
  check('R1a a relic was handed over by the first loot', s1b.owned !== s1a.owned, s1a.owned + ' -> ' + s1b.owned);
  check('R1b the chest came back: block reads opened=false again', await isClosedChest(A.x, A.y, A.z));
  check('R1c and it is not left looking opened', !(await isOpen(A.x, A.y, A.z)));
  check('R1d the registry still shows all 16 closed (the loot was undone by the respawn)', ch1.closed === 16, JSON.stringify(ch1));
  check('R1e the player was told (action bar line seen)', lines.some(l => /shimmers and fills again/.test(l)));

  // 2 loot the SAME chest again: it opens for real this time, and no second respawn
  const s2a = await state();
  check('R2 click delivered', (await click(A.x, A.y, A.z)) === 'ok');
  const s2b = await state(); const ch2 = await chests();
  check('R2a the re-armed chest gives a second relic (it is a real chest again)', s2b.owned !== s2a.owned, s2a.owned + ' -> ' + s2b.owned);
  check('R2b it paid the price again: the opened counter rose by 1 (or Ember Ledger froze it)', s2b.opened === s2a.opened + 1 || /ember_ledger/.test(s2b.owned), s2a.opened + ' -> ' + s2b.opened);
  check('R2c NO second respawn: the block now stays opened=true', await isOpen(A.x, A.y, A.z));
  check('R2d and the registry shows 15 closed', ch2.closed === 15, JSON.stringify(ch2));

  // 3 a DIFFERENT chest: the cap is per run, not per chest, so this one stays open too
  const ch3 = await chests(); const B = { x: ch3.x, y: ch3.y, z: ch3.z, kind: ch3.kind };
  check('R3 the next chest is a different one', !(B.x === A.x && B.y === A.y && B.z === A.z), JSON.stringify(B));
  await setGold(100000); await goBeside(B);
  check('R3a click delivered', (await click(B.x, B.y, B.z)) === 'ok');
  const ch3b = await chests();
  check('R3b a different chest does NOT respawn either (cap is one per run)', await isOpen(B.x, B.y, B.z) && ch3b.closed === 14, JSON.stringify(ch3b));

  await c('/expedition leave', 800);
  console.log(fails === 0 ? 'RESULT: ALL PASS (exactly-one-respawn graded from the server log by chestrespawn_grade.py)' : 'RESULT: ' + fails + ' FAILED');
  bot.quit(); setTimeout(() => process.exit(fails === 0 ? 0 : 1), 400);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 240000);
