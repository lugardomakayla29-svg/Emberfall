// Devourer COIL damage band: who is hit, judged from the 'Devourer COIL hit' server log lines (not from hp, regeneration hides it).
const mineflayer = require('mineflayer'); const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
const log = () => fs.readFileSync('../server_run.log', 'utf8');
const hits = () => (log().match(/Devourer COIL hit/g) || []).length;
const partPos = async i => { const r = await ask(`/data get entity @e[type=minecraft:item_display,tag=emberfall_worm_${i},limit=1] Pos`, 300); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
const coilStart = async () => { const n = (log().match(/Devourer COIL windup/g) || []).length; const t0 = Date.now(); while (Date.now() - t0 < 60000) { await sleep(200); if ((log().match(/Devourer COIL windup/g) || []).length > n) return true; } return false; };
const coilEnd = async () => { const n = (log().match(/Devourer COIL ended/g) || []).length; const t0 = Date.now(); while (Date.now() - t0 < 15000) { await sleep(200); if ((log().match(/Devourer COIL ended/g) || []).length > n) return true; } return false; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/Wave Director started/.test(log())) break; } await sleep(1500);
  await ask('/emberfall bossdevourer 0', 1200); await sleep(2500); await ask('/emberfall wavestop 0', 400);
  await ask('/kill @e[type=!player,type=!emberfall:devourer_brain,type=!emberfall:devourer_spawn,type=!minecraft:item_display]', 600);
  await ask('/effect give @e[type=emberfall:devourer_brain,limit=1] minecraft:resistance 999 4 true', 200);
  await ask('/data modify entity @e[type=emberfall:devourer_brain,limit=1] Health set value 150f', 500);
  // ---- round 1: the bot stays in the OPEN MIDDLE. A coil is started by the boss round the bot, so the centre is the bot's own spot.
  const h0 = hits(); const s1 = await coilStart(); check('D0 a coil started', s1, s1 ? 'ok' : 'none in 60 s');
  if (!s1) { console.log('SOME FAIL'); bot.quit(); return setTimeout(() => process.exit(0), 300); }
  const cm = /COIL windup: centre \(?(-?[\d.]+), (-?[\d.]+), (-?[\d.]+)\)?/.exec(log().slice(log().lastIndexOf('COIL windup') - 40));
  const e1 = await coilEnd(); const hMiddle = hits() - h0;
  check('D1 standing in the open middle of the ring takes NO coil hit', hMiddle === 0, `hits=${hMiddle}`);
  // ---- round 2: stand ON the ring line. The ring is locked at the coil centre, so after the windup, teleport onto radius 5.8 opposite the gap.
  const h1 = hits(); const s2 = await coilStart();
  if (s2 && cm) {
    const cx = +(/COIL windup: centre \(?(-?[\d.]+)/.exec(log().slice(log().lastIndexOf('COIL windup') - 5))[1]);
    const cz = +(/COIL windup: centre \(?-?[\d.]+, -?[\d.]+, (-?[\d.]+)/.exec(log().slice(log().lastIndexOf('COIL windup') - 5))[1]);
    await sleep(900);   // past the wind-up, hold started
    // find the ring's nearest part to the centre line: read 6 parts and stand ON part 10 (a mid-body part, never the gap)
    const p10 = await partPos(10);
    if (p10) { await ask(`/tp @s ${p10[0].toFixed(2)} ${(p10[1] - 0.5).toFixed(2)} ${p10[2].toFixed(2)}`, 300); }
    await coilEnd();
  }
  const hRing = hits() - h1; check('D2 standing ON a body part takes at least one coil hit', hRing >= 1, `hits=${hRing}`);
  check('D3 at most ONE hit per pulse per player (never one per overlapping part): hits in round 2 <= 7', hRing <= 7, `hits=${hRing} (3.5 s hold, pulse every 0.5 s = 7 pulses)`);
  const ex = (log().match(/Exception|ERROR/g) || []).length; check('D4 no exceptions in the server log', ex === 0, `count=${ex}`);
  console.log(res.every(x => x) ? 'ALL PASS' : 'SOME FAIL'); bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
