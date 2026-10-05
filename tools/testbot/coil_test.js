// Devourer COIL: judged from REAL worm display positions and the live server log (server_run.log), not from chat.
const mineflayer = require('mineflayer'); const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const log = () => fs.readFileSync('../server_run.log', 'utf8');
const partPos = async i => { const r = await ask(`/data get entity @e[type=minecraft:item_display,tag=emberfall_worm_${i},limit=1] Pos`, 350); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/Expedition started/.test(log()) || /Wave Director started/.test(log())) break; } await sleep(1500);
  console.log('boss:', (await ask('/emberfall bossdevourer 0', 1200)).slice(0, 80)); await sleep(2500);
  await ask('/emberfall wavestop 0', 400);
  await ask('/kill @e[type=!player,type=!emberfall:devourer_brain,type=!emberfall:devourer_spawn,type=!minecraft:item_display]', 600);
  await ask('/effect give @e[type=emberfall:devourer_brain,limit=1] minecraft:resistance 999 4 true', 200);   // the boss must live through the test
  const parts = /worm parts/.exec(log()) ? +(/(\d+) worm parts/.exec(log())[1]) : NaN;
  check('C0 the Devourer has 20 parts', parts === 20, `log says ${parts} worm parts`);
  await ask('/data modify entity @e[type=emberfall:devourer_brain,limit=1] Health set value 150f', 500);   // 58%: phase 2
  // wait for a coil to start on its own (every 2nd surfaced window from phase 2)
  let t0 = Date.now(), started = false; while (Date.now() - t0 < 60000) { await sleep(300); if (/Devourer COIL windup/.test(log())) { started = true; break; } }
  check('C1 a coil starts by itself in phase 2', started, started ? 'COIL windup logged' : 'none in 60 s');
  if (!started) { console.log(res.every(x => x) ? 'ALL PASS' : 'SOME FAIL'); bot.quit(); return setTimeout(() => process.exit(0), 300); }
  const cm = /COIL windup: centre \(?(-?[\d.]+), (-?[\d.]+), (-?[\d.]+)\)? radius/.exec(log()); 
  await sleep(1300);   // past the 0.7 s wind-up, into the hold
  // The ring rotates and shrinks every tick and each /data get takes a moment, so 20 reads spread over different moments
  // would be judged as one shape. Freeze the server tick (entities stop moving), read all 20, then unfreeze.
  await ask('/tick freeze', 400);
  const P = []; for (let i = 0; i < 20; i++) P.push(await partPos(i));
  await ask('/tick unfreeze', 400);
  const ok = P.filter(Boolean); check('C2 all 20 part positions readable', ok.length === 20, `read ${ok.length}/20`);
  if (ok.length === 20) {
    // centre: the coil centre from the log when parseable, otherwise the mean of the ring (a ring with a gap biases the mean, so prefer the log)
    const cx = cm ? +cm[1] : ok.reduce((a, p) => a + p[0], 0) / 20, cz = cm ? +cm[3] : ok.reduce((a, p) => a + p[2], 0) / 20;
    const rad = ok.map(p => Math.hypot(p[0] - cx, p[2] - cz)); const rmin = Math.min(...rad), rmax = Math.max(...rad);
    check('C3 every part lies on one ring (radius spread small)', rmax - rmin < 0.6 && rmin > 4.9 && rmax < 6.4, `radius ${rmin.toFixed(2)} to ${rmax.toFixed(2)}`);
    let worst = 0, best = 99; for (let i = 1; i < 20; i++) { const d = Math.hypot(ok[i][0] - ok[i - 1][0], ok[i][2] - ok[i - 1][2]); worst = Math.max(worst, d); best = Math.min(best, d); }
    check('C4 neighbouring parts stay joined (no stretch, no overlap)', worst < 1.75 && best > 1.2, `neighbour gap ${best.toFixed(2)} to ${worst.toFixed(2)}`);
    const ang = ok.map(p => Math.atan2(p[2] - cz, p[0] - cx)).sort((a, b) => a - b); let gap = ang[0] + 2 * Math.PI - ang[19]; for (let i = 1; i < 20; i++) gap = Math.max(gap, ang[i] - ang[i - 1]);
    const gapBlocks = gap * ((rmin + rmax) / 2);
    check('C5 there is exactly one open gap wide enough to walk through (>= 3 blocks)', gapBlocks >= 3.0, `gap ${gapBlocks.toFixed(1)} blocks`);
    globalThis.RING = { cx, cz, P: ok };
  }
  // the hold must end and the worm must go back to normal (fewer than 8 blocks between head and part 5 proves it is no longer a ring arc... check the log instead)
  t0 = Date.now(); let ended = false; while (Date.now() - t0 < 12000) { await sleep(300); if (/Devourer COIL ended/.test(log())) { ended = true; break; } }
  check('C6 the coil ends by itself', ended, ended ? 'COIL ended logged' : 'no end line in 12 s');
  const ex = (log().match(/Exception|ERROR/g) || []).length; check('C7 no exceptions in the server log', ex === 0, `count=${ex}`);
  console.log(res.every(x => x) ? 'ALL PASS' : 'SOME FAIL'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
