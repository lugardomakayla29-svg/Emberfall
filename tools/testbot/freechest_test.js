// Free chests end to end. A: a boss drop places a FREE chest and the run counter rises. B: opening it costs 0 gold and gives a relic.
// C: a REAL director elite (tagged) killed by the player leaves free chests, and an UNTAGGED elite of the same kind (spawnelite) leaves none.
// D: the cap of 12 holds. Kills use `damage ... by @p`, the path that credits the player.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
let fails = 0; const check = (l, ok, e = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (e ? '  ' + e : '')); if (!ok) fails++; };
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 500) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 700) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
  const chests = async () => { const t = await ask('/emberfall relic chests EmberTester', 700); const m = t.match(/total=(\d+) closed=(\d+) free=(\d+) nearest=(-?\d+) (-?\d+) (-?\d+) kind=(\w+)/); return m ? { total: +m[1], closed: +m[2], free: +m[3], x: +m[4], y: +m[5], z: +m[6], kind: m[7] } : { raw: t }; };
  const state = async () => { const t = await ask('/emberfall relic state EmberTester', 700); return { owned: t.match(/owned=(\{[^}]*\})/)?.[1] ?? '?', wallet: +(t.match(/wallet=(\d+)/)?.[1] ?? NaN), raw: t }; };
  await c('/gamemode survival'); await c('/character select vanguard', 500);
  await c('/expedition leave', 900); await c('/expedition', 4000);
  for (let i = 0; i < 30; i++) { await sleep(1500); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; } await sleep(2500);
  // no wavestop here: the real elite path needs the director alive (wavestop removes it and `relic elite` then answers none)
  await c('/effect give @s minecraft:resistance 999 4 true', 200); await c('/time set midnight', 200);
  let ch = await chests();
  check('run starts with 16 chests and no free ones given', ch.total === 16 && ch.free === 0, JSON.stringify(ch));
  // A: boss drop (chance 1.0)
  let r = await ask('/emberfall relic free EmberTester boss', 900);
  check('A: a boss drop places a chest and the counter rises to 1', /placed=true given=1/.test(r), r.slice(-60));
  ch = await chests();
  check('A: the registry grew by exactly one (17 chests) and free=1', ch.total === 17 && ch.free === 1, JSON.stringify(ch));
  // B: find the free chest (nearest closed one is not necessarily it), so look next to the player where it was dropped
  const px = Math.floor(bot.entity.position.x), py = Math.floor(bot.entity.position.y), pz = Math.floor(bot.entity.position.z);
  let fx = null;
  for (let dx = -4; dx <= 4 && !fx; dx++) for (let dz = -4; dz <= 4 && !fx; dz++) for (let dy = -2; dy <= 2 && !fx; dy++) {
    const t = await ask(`/execute if block ${px + dx} ${py + dy} ${pz + dz} emberfall:ember_chest_free run say FREEBLOCK`, 120);
    if (t.includes('FREEBLOCK')) fx = [px + dx, py + dy, pz + dz];
  }
  check('B: a FREE chest block really stands next to the player', !!fx, JSON.stringify(fx));
  if (fx) {
    await c(`/tp @s ${fx[0] + 0.5} ${fx[1]} ${fx[2] + 2.5} 180 0`, 1200);
    await c('/emberfall relic gold EmberTester 0', 400);
    const s0 = await state();
    try { await bot.activateBlock(bot.blockAt(new Vec3(fx[0], fx[1], fx[2]))); } catch (e) {} await sleep(1000);
    const s1 = await state();
    check('B: opening the free chest costs 0 gold', s1.wallet === s0.wallet && s0.wallet === 0, `${s0.wallet} -> ${s1.wallet}`);
    check('B: and still hands over a relic', s1.owned !== s0.owned, `${s0.owned} -> ${s1.owned}`);
  }
  // C: real elite vs untagged elite, 40 kills each (chance ~0.15 falling 10% per chest: expect several from the real ones, none from the control)
  const killElites = async (cmdSpawn, n) => {
    for (let i = 0; i < n; i++) {
      await c('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:item]', 200);
      await c(cmdSpawn, 500);
      await c('/execute as @e[type=!player,type=!minecraft:item,type=!minecraft:item_display,type=!minecraft:interaction,tag=emberfall_elite,limit=1,sort=nearest] run damage @s 99999 minecraft:player_attack by @p', 500);
    }
  };
  const before = (await chests()).free;
  // control first: an untagged elite made by the debug command carries no emberfall_elite tag
  for (let i = 0; i < 25; i++) {
    await c('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:item]', 200);
    await c('/emberfall spawnelite cinderbrand_reaver', 600);
    await c('/execute as @e[type=emberfall:cinderbrand_reaver,limit=1,sort=nearest] run damage @s 99999 minecraft:player_attack by @p', 500);
  }
  const afterCtl = (await chests()).free;
  check('C control: 25 kills of an UNTAGGED elite leave no free chest', afterCtl === before, `${before} -> ${afterCtl}`);
  // the real path: the director's own spawn (tagged). spawn, kill, repeat
  let realSpawns = 0;
  for (let i = 0; i < 40; i++) {
    await c('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:item]', 200);
    const t = await ask('/emberfall relic elite EmberTester', 500); if (!/spawned/.test(t)) continue; realSpawns++;
    await c('/execute as @e[tag=emberfall_elite,limit=1] run damage @s 99999 minecraft:player_attack by @p', 600);
  }
  const afterReal = (await chests()).free;
  check('C: 40 director elites killed by the player leave free chests (expected ~4 to 6)', afterReal - afterCtl >= 1 && afterReal - afterCtl <= 12, `spawned ${realSpawns}, free ${afterCtl} -> ${afterReal}`);
  // D: the cap
  for (let i = 0; i < 20; i++) await ask('/emberfall relic free EmberTester boss', 350);
  r = await ask('/emberfall relic free EmberTester boss', 700);
  ch = await chests();
  check('D: free chests stop at the cap of 12', ch.free === 12 && /placed=false/.test(r), `free ${ch.free}; ${r.slice(-50)}`);
  await c('/expedition leave', 800);
  console.log(fails === 0 ? 'RESULT: ALL PASS' : 'RESULT: ' + fails + ' FAILED'); bot.quit(); setTimeout(() => process.exit(0), 400);
});
