// A plain (NOT veteran) horde spider now skitter-dodges when hit: a sideways hop, perpendicular to the attacker.
// Pin the player, place a spider 6 blocks away (east), hurt it with /damage attributed to the player, and measure how far it moved
// along the player->spider line (radial) versus across it (lateral) right after. A dodge is mostly lateral, about 3 blocks.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const pos = async () => { const r = await ask('/data get entity @e[tag=sp,limit=1] Pos', 160); const m = /\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 800);
  let dodged = 0, lateralOk = 0, trials = 0; const rows = [];
  for (let t = 0; t < 5; t++) {
    const CONTROL = process.env.CONTROL === '1';
    await ask('/kill @e[tag=sp]', 300);
    await ask(`/execute at @s run summon ${CONTROL ? 'minecraft:spider' : 'emberfall:horde_spider'} ~6 ~ ~ {CustomName:"ctl",Tags:["sp","keep"],PersistenceRequired:1b,NoAI:1b}`, 600);
    trials++;
    const a = await pos();
    // NoAI would stop the hop's steering lock from mattering, but the velocity still applies; the spider is tested as hit, not as hunting.
    await ask('/data merge entity @e[tag=sp,limit=1] {NoAI:0b}', 300);
    const before = await pos();
    await ask('/damage @e[tag=sp,limit=1] 1 minecraft:player_attack by @s', 200);
    await sleep(330);
    const after = await pos();
    await sleep(900);
    const later = await pos();
    if (later && after) console.log(`  path: hop end (${(after[0]-before[0]).toFixed(2)}, ${(after[2]-before[2]).toFixed(2)}) then 0.9 s later (${(later[0]-before[0]).toFixed(2)}, ${(later[2]-before[2]).toFixed(2)})`);
    if (!before || !after) continue;
    const dx = after[0] - before[0], dz = after[2] - before[2];
    // the attacker is at (bx,bz); radial = along spider minus attacker, lateral = across it
    const rx = before[0] - bx, rz = before[2] - bz, rl = Math.hypot(rx, rz) || 1;
    const radial = (dx * rx + dz * rz) / rl, lateral = Math.abs((-dx * rz + dz * rx) / rl);
    rows.push({ radial: +radial.toFixed(2), lateral: +lateral.toFixed(2) });
    if (Math.hypot(dx, dz) > 1.5) dodged++;
    if (lateral > Math.abs(radial) && lateral > 1.5) lateralOk++;
  }
  clearInterval(pin);
  console.log('per trial (radial, lateral):', rows.map(r => `(${r.radial}, ${r.lateral})`).join(' '));
  R('P1 a hit spider hops more than 1.5 blocks', dodged >= 4, `${dodged}/${trials}`);
  R('P2 the hop is mostly sideways (lateral beats radial)', lateralOk >= 4, `${lateralOk}/${trials}`);
  await ask('/kill @e[tag=keep]', 500); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
