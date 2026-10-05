// Kraken stage 3: during a real Guardian attack the arms rear up, then land low on the resolve tick. Judged from the server's
// TENTACLE_TEST trace (one line per tick), read from server_run.log, not from chat polling.
const mineflayer = require('mineflayer');
const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
const REACH = 0.95 * 7, WIND = { FAN: 18, SPARKS: 24, BEAM: 40, RING: 50 };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  console.log('boss:', (await ask('/emberfall boss 0', 1200)).slice(0, 80)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  await ask('/kill @e[type=!player,type=!emberfall:ember_guardian,type=!emberfall:cinder_pylon,type=!minecraft:item_display]', 600);
  const G = '@e[type=emberfall:ember_guardian,limit=1]';
  await sleep(25000);   // phase 1 loop: FAN, SPARKS
  // Phase 2: a relight needs every pylon broken AND health under the mark, so break them, drop health, and wait for the re-light.
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 380f`, 500); await sleep(40000);
  // Phase 3: break the re-lit pylon, drop under a third, and leave the second re-lit pylon alive so the boss stays gated.
  await ask('/kill @e[type=emberfall:cinder_pylon]', 600); await sleep(800);
  await ask(`/data modify entity ${G} Health set value 190f`, 500); await sleep(70000);
  const log = fs.readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('TENTACLE_TEST'));
  const re = /TENTACLE_TEST (\w+) tick=(\d+) resolved=(true|false) boss=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+) tips(.*)$/;
  const rows = log.map(l => { const m = re.exec(l); if (!m) return null;
    const tips = m[7].trim().split(' ').map(t => t.split(',').map(Number));
    return { atk: m[1], tick: +m[2], resolved: m[3] === 'true', boss: [+m[4], +m[5], +m[6]], tips }; }).filter(Boolean);
  check('A0 the server traced tentacle poses during attacks', rows.length > 20, `rows=${rows.length}`);
  const kinds = {}; for (const r of rows) (kinds[r.atk] = kinds[r.atk] || []).push(r);
  console.log('attacks traced:', Object.keys(kinds).join(','), Object.fromEntries(Object.entries(kinds).map(([k, v]) => [k, v.length])));
  for (const need of ['FAN', 'SPARKS', 'BEAM', 'RING']) check(`A4 the ${need} attack was traced`, !!kinds[need] && kinds[need].length > 6, `rows=${(kinds[need] || []).length}`);
  for (const [atk, rs] of Object.entries(kinds)) {
    const wind = WIND[atk]; if (!wind) continue;
    const live = rs.filter(r => !r.resolved).sort((a, b) => a.tick - b.tick);
    if (live.length < 6) { check(`A1 ${atk} has enough wind-up samples`, false, `samples=${live.length}`); continue; }
    const avgY = r => r.tips.reduce((s, t) => s + (t[1] - r.boss[1]), 0) / r.tips.length;
    const start = avgY(live[0]);
    const peak = Math.max(...live.filter(r => r.tick < wind).map(avgY));
    check(`A1 ${atk}: arms rear up during the wind-up`, peak > start + 1.5, `start=${start.toFixed(2)} peak=${peak.toFixed(2)} (height over boss feet)`);
    const atResolve = live[live.length - 1];
    check(`A2 ${atk}: arms are low again by the resolve tick`, avgY(atResolve) < peak - 1.0, `atResolve=${avgY(atResolve).toFixed(2)} peak=${peak.toFixed(2)} tick=${atResolve.tick}/${wind}`);
    let over = 0; for (const r of rs) for (const t of r.tips) over = Math.max(over, Math.hypot(t[0] - r.boss[0], t[1] - r.boss[1] - 1.3, t[2] - r.boss[2]) - (REACH + 1.6));
    check(`A3 ${atk}: no tip strays beyond arm reach of the boss`, over < 0.3, `worst excess=${over.toFixed(2)}`);
  }
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
