// Horde Bomber: a creeper-based runner. Claims: (B1) it is faster than a vanilla creeper (0.33 vs 0.25) and has 10 hp;
// (B2) it detonates when it reaches a player and the player loses health; (B3) the blast changes NO block (the mixin);
// (B4) it is gone afterwards (an exploded creeper discards itself).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const num = (s, re) => { const m = re.exec(s); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  // B1: stats of a plain (non-veteran) Bomber, read from a raw summon (speed and health come from the attribute set)
  await ask('/execute at @s run summon emberfall:horde_bomber ~12 ~ ~ {Tags:["bm"],PersistenceRequired:1b,NoAI:1b}', 700);
  const spd = await ask('/attribute @e[tag=bm,limit=1] minecraft:movement_speed get', 400);
  const hp = await ask('/attribute @e[tag=bm,limit=1] minecraft:max_health get', 400);
  console.log('  speed reply:', spd.slice(0, 90), '| hp reply:', hp.slice(0, 90));
  R('B1a Bomber speed is 0.33', Math.abs(num(spd, /is ([\d.]+)/) - 0.33) < 0.005, String(num(spd, /is ([\d.]+)/)));
  R('B1b Bomber max health is 10', num(hp, /is ([\d.]+)/) === 10, String(num(hp, /is ([\d.]+)/)));
  await ask('/kill @e[tag=bm]', 400);
  // B2-B4: a live Bomber vs a pinned player. A marker block 1 block beside the player must survive the blast.
  await ask('/effect give @s minecraft:regeneration 999 0 true', 200);
  await ask('/setblock ~2 ~ ~ minecraft:air', 200);
  await ask(`/setblock ${Math.floor(bx) + 2} ${Math.floor(by)} ${Math.floor(bz)} minecraft:glass`, 300);
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 600);
  const hp0 = bot.health;
  // real spawn path (prepare + veteran blast); invulnerable so the run player's auto-weapon cannot kill it before it arrives
  await ask('/execute at @s positioned ~8 ~ ~ run emberfall spawnveteran horde_bomber', 500);
  await ask('/tag @e[type=emberfall:horde_bomber,limit=1] add bm', 200);
  await ask('/data merge entity @e[tag=bm,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 300);
  let minHp = bot.health, gone = false; const t0 = Date.now();
  while (Date.now() - t0 < 12000) {
    await sleep(150); if (bot.health < minHp) minHp = bot.health;
    const c = await ask('/execute if entity @e[tag=bm]', 120);
    if (/Test failed/.test(c)) { gone = true; break; }
  }
  await sleep(500); clearInterval(pin);
  const glass = await ask(`/execute if block ${Math.floor(bx) + 2} ${Math.floor(by)} ${Math.floor(bz)} minecraft:glass`, 400);
  console.log(`  hp before ${hp0}, lowest ${minHp}, bomber gone ${gone}, glass reply: ${glass.slice(0, 60)}`);
  R('B2 the Bomber reached the player and hurt them', minHp < hp0, `${hp0} -> ${minHp}`);
  R('B3 the blast left the glass block standing (no block damage)', /Test passed/.test(glass), glass.slice(0, 40));
  R('B4 the Bomber is gone after exploding', gone);
  await ask('/kill @e[tag=bm]', 300); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
