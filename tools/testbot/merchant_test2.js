// Testificate merchant, part 2: time-out, natural arrival, spacing, and weapons never touching him.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const state = async () => (await ask('/emberfall relic merchantstate EmberTester', 700)).match(/merchantstate (.*)/)?.[1] ?? '?';
const pos = async () => { const m = (await ask('/data get entity @e[type=emberfall:testificate,limit=1] Pos', 700)).match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/); return m ? [+m[1], +m[2], +m[3]] : null; };
const startRun = async () => {
  await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500); await ask('/emberfall wavestop 0', 500);
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await startRun();
  // ---- natural arrival by the run clock: nothing at the start, exactly one by ~125 s
  check('T0 nothing has arrived at the start of a run', /visits=0/.test(await state()), await state());
  // ---- spacing from the player on a forced arrival
  await ask('/emberfall relic merchant EmberTester 2', 1500);
  const p = await pos(); const me = (await ask('/data get entity @s Pos', 700)).match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/);
  const d = Math.hypot(p[0] - +me[1], p[2] - +me[3]);
  check('T1 he appears at least 10 blocks from the player', d >= 10, 'distance ' + d.toFixed(1));
  check('T1b a Rare merchant: tier is RARE and the banner is in the name', /tier=RARE/.test(await state()), await state());
  const nm = await ask('/data get entity @e[type=emberfall:testificate,limit=1] CustomName', 700);
  check('T1c his name tag shows the tier and a countdown', /Rare Testificate/.test(nm) && /\ds/.test(nm), nm.slice(0, 160));
  check('T1d the party was told he arrived', lines.join(' | ').includes('Rare Testificate') && lines.join(' | ').includes('has arrived'), '');
  // ---- weapons never touch him: stand beside him with a zombie wave for 12 s and watch his hurt state and health
  await ask(`/tp @s ${p[0] + 2} ${p[1]} ${p[2]}`, 1200);
  const hp0 = await ask('/data get entity @e[type=emberfall:testificate,limit=1] Health', 700);
  await ask('/emberfall wavestart 0', 800);
  let maxHurt = 0, hurtSeen = 0;
  for (let i = 0; i < 12; i++) { const h = await ask('/data get entity @e[type=emberfall:testificate,limit=1] HurtTime', 600); const m = h.match(/: (\d+)s?/); if (m && +m[1] > 0) { hurtSeen++; maxHurt = Math.max(maxHurt, +m[1]); } await sleep(400); }
  const hp1 = await ask('/data get entity @e[type=emberfall:testificate,limit=1] Health', 700);
  check('T2 while a wave is fought beside him his health never changes', hp0.match(/[\d.]+f/)?.[0] === hp1.match(/[\d.]+f/)?.[0] && hurtSeen === 0, `hp ${hp0.match(/[\d.]+f/)?.[0]} -> ${hp1.match(/[\d.]+f/)?.[0]}, hurt readings ${hurtSeen}`);
  check('T2b he is still standing', (await state()).includes('phase=STANDING'), await state());
  // ---- the time-out path: wait out the rest of his 60 s with nobody buying
  await ask('/emberfall wavestop 0', 500);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!emberfall:testificate]', 600);
  let phase = ''; let sawSad = false;
  for (let i = 0; i < 90; i++) { phase = await state(); if (/LEAVING_SAD/.test(phase)) sawSad = true; if (/phase=NONE/.test(phase)) break; await sleep(1000); }
  check('T3 nobody buys: he leaves SAD and is gone within his minute', sawSad && /phase=NONE/.test(phase) && /bought=0/.test(phase), phase);
  const sad = lines.join(' | ');
  check('T3b no relic was given on the time-out', /bought=0/.test(phase), phase);
  // ---- the next natural visit: 180 s after he left the schedule allows one; wait for it by the run clock
  const t0 = Date.now(); let arrived = false;
  for (let i = 0; i < 200; i++) { const s = await state(); if (/visits=2/.test(s)) { arrived = true; break; } await sleep(1000); }
  check('T4 the next visit arrives on its own about 180 s after he left', arrived && (Date.now() - t0) / 1000 > 120, 'waited ' + ((Date.now() - t0) / 1000).toFixed(0) + ' s, ' + await state());
  await ask('/expedition leave', 1200);
  check('T5 leaving the run removes him', (await ask('/execute if entity @e[type=emberfall:testificate]', 600)).includes('failed'), '');
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
