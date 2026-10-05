// A run with time + a level + gold + real kills, then a lethal hit. The Silver on the wire must equal the shared formula.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 800) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let runEnd = null;
bot._client.on('packet', (d, meta) => { if (meta.name === 'custom_payload' && d.channel === 'emberfall:run_end') runEnd = Buffer.from(d.data); });
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
function vi(b, o) { let v = 0, s = 0, x; do { x = b[o.i++]; v |= (x & 127) << s; s += 7; } while (x & 128); return v; }
function us(b, o) { const n = vi(b, o); const t = b.toString('utf8', o.i, o.i + n); o.i += n; return t; }
function vl(b, o) { let v = 0n, s = 0n, x; do { x = b[o.i++]; v |= BigInt(x & 127) << s; s += 7n; } while (x & 128); return Number(v); }
// the shared formula, restated independently here (integer division, same constants) so the test is not circular
const formula = (s, lvl, k, g, hy, dv) => Math.floor(s / 10) * 1 + Math.floor(k / 5) + lvl * 3 + Math.floor(g / 20) + (hy ? 50 : 0) + (dv ? 150 : 0);
bot.once('spawn', async () => {
  await sleep(6000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut'); await ask('/expedition', 3000);
  await ask('/kill @e[type=!player,distance=..80]', 700);
  await ask('/xp set @s 4 levels', 500);
  await ask('/emberfall debugpickup EmberTester gold 47', 600);
  // real kills: weak mobs held in reach, the auto attack finishes them
  for (let i = 0; i < 6; i++) {
    await ask('/execute at @s run summon emberfall:horde_zombie ~2.5 ~ ~ {Tags:["k"],Silent:1b}', 500);
    await ask('/attribute @e[tag=k,limit=1] minecraft:max_health base set 1', 300);
    await ask('/effect give @e[tag=k] minecraft:instant_health 1 10 true', 300);
    for (let t = 0; t < 8; t++) { await ask('/execute at @s run tp @e[tag=k,limit=1] ~2.5 ~ ~', 450); }
    await ask('/kill @e[tag=k]', 200);
  }
  await sleep(12000);
  const hud = await ask('/emberfall hudstate EmberTester', 800);
  await ask('/damage @s 1000 minecraft:generic', 1500); await sleep(1500);
  R('W1 run_end arrived', !!runEnd);
  if (runEnd) {
    const o = { i: 0 }; us(runEnd, o); const secs = vi(runEnd, o), lvl = vi(runEnd, o), kills = vi(runEnd, o), gold = vi(runEnd, o);
    const hy = runEnd[o.i++], dv = runEnd[o.i++]; const earned = vl(runEnd, o), total = vl(runEnd, o);
    const want = formula(secs, lvl, kills, gold, hy, dv);
    console.log(`     secs=${secs} level=${lvl} kills=${kills} gold=${gold} earned=${earned} total=${total} expected=${want}`);
    R('W2 level, gold and kills all reached the payload', lvl >= 4 && gold >= 47 && kills >= 1, `(lvl ${lvl}, gold ${gold}, kills ${kills})`);
    R('W3 Silver paid equals the formula for those exact numbers', earned === want, `(${earned} vs ${want})`);
    R('W4 the run now pays clearly more than time alone', earned > Math.floor(secs / 10) + 5, `(time alone ${Math.floor(secs / 10)})`);
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
