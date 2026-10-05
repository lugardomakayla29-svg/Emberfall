// A plain (NOT veteran) horde zombie now lunges. Pin the player, spawn a zombie 5.5 blocks away, and sample its position every ~100 ms.
// Walking tops out near 0.25 blocks/tick; a lunge is 0.9 blocks/tick. So the proof is: (a) a crouch (nearly still) then (b) a burst of
// horizontal speed over 0.6 blocks/tick... measured as displacement across one 100 ms sample (2 ticks) above 1.1 blocks.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const pos = async () => { const r = await ask('/data get entity @e[tag=lz,limit=1] Pos', 160); const m = /\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d\]/.exec(r); return m ? [+m[1], +m[2], +m[3]] : null; };
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
  let bursts = 0, crouches = 0, maxStep = 0, trials = 0;
  for (let t = 0; t < 4; t++) {
    await ask('/kill @e[tag=lz]', 300);
    await ask('/execute at @s run summon emberfall:horde_zombie ~5.5 ~ ~ {Tags:["lz","keep"],PersistenceRequired:1b}', 500);
    trials++;
    let prev = await pos(); let still = 0, gotCrouch = false, gotBurst = false; const hist = [];
    const t0 = Date.now();
    while (Date.now() - t0 < 9000) {
      const p = await pos(); if (!p || !prev) { prev = p || prev; continue; }
      const step = Math.hypot(p[0] - prev[0], p[2] - prev[2]); hist.push(+step.toFixed(2));
      if (step > maxStep) maxStep = step;
      if (step < 0.05) still++; else still = 0;
      if (still >= 2) gotCrouch = true;
      if (step > 1.1) { gotBurst = true; break; }
      prev = p;
    }
    console.log(`  trial ${t}: last steps ${hist.slice(-9).join(' ')}`);
    if (gotCrouch) crouches++; if (gotBurst) bursts++;
  }
  clearInterval(pin);
  console.log(`trials ${trials}  crouch seen ${crouches}  burst seen ${bursts}  largest step ${maxStep.toFixed(2)} blocks`);
  R('Z1 a plain zombie makes a burst of over 1.1 blocks in one sample (walking cannot)', bursts >= 2, `${bursts}/${trials}`);
  // Z2 is judged on the SERVER trace, not on chat-polled positions: the crouch lasts 10 ticks (0.5 s), shorter than two chat
  // round trips, so a polled "still for 2 samples" check was a function of latency (it read 1/4, 3/4, 2/4 on one build).
  const LOG = (process.env.EMBERFALL_HOME || '.') + '/run/server_run.log';
  const tr = require('fs').readFileSync(LOG, 'utf8').split('\n').filter(l => l.includes('ZOMBIE_TEST'));
  const crouchT = tr.map(l => /crouch tick=(\d+)/.exec(l)).filter(Boolean).map(m => +m[1]);
  const launchT = tr.map(l => /launch tick=(\d+)/.exec(l)).filter(Boolean).map(m => +m[1]);
  const gaps = crouchT.map((c, i) => launchT[i] - c);
  console.log(`  server trace: ${crouchT.length} crouch, ${launchT.length} launch, crouch-to-launch gaps ${gaps.join(' ')} ticks`);
  R('Z2 every crouch is followed by a launch exactly 10 ticks later (0.5 s telegraph)', crouchT.length >= 2 && crouchT.length === launchT.length && gaps.every(g => g === 10), gaps.join(','));
  await ask('/kill @e[tag=keep]', 500); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
