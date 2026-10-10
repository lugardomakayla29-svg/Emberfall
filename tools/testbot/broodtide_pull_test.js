// Broodtide PULL measurement (the weak spot of broodtide_grab_test R8). The player is placed ONCE at 10 blocks and then left alone (no re-teleport), so the
// single impulse is the only thing that can change the distance. Judge = server replies: /data get entity EmberTester Pos and /emberfall bosstide.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
// 1.21.11 sends entity_velocity as lpVec3 = blocks per tick already, but mineflayer 4.39.0 still scales it by 1/8000 (the old wire format), so the bot
// never moved and this test failed on every build, old ones included. Re-apply the true value AFTER mineflayer's own handler has run (setImmediate),
// because our listener fires first and mineflayer would otherwise overwrite it.
bot._client.on('entity_velocity', d => {
  if (!bot.entity || d.entityId !== bot.entity.id || !d.velocity) return;
  const v = { x: d.velocity.x, y: d.velocity.y, z: d.velocity.z };
  setImmediate(() => bot.entity.velocity.set(v.x, v.y, v.z));
});
const vel = []; bot._client.on('packet', (d, m) => { if (m.name === 'entity_velocity' && bot.entity && d.entityId === bot.entity.id) vel.push({ t: Date.now(), v: d.velocity || [d.velocityX, d.velocityY, d.velocityZ] }); });
const SEL = '@e[type=emberfall:broodtide,limit=1]';
const st = async () => { for (let k = 0; k < 4; k++) { const r = await ask('/emberfall bosstide 0', 450); const m = /tick=(\d+) tide=(EBB|FLOOD) .*grabs=(\d+) impulses=(\d+) active=(\d+)/.exec(r); if (m) return { tick: +m[1], tide: m[2], grabs: +m[3], impulses: +m[4], active: +m[5] }; } return null; };
const pos = async who => { for (let k = 0; k < 3; k++) { const r = await ask(`/data get entity ${who} Pos`, 450); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); if (m) return { x: +m[1], y: +m[2], z: +m[3] }; } return null; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  await ask('/emberfall boss 0', 1500); await sleep(1500);
  await ask('/kill @e[type=!player,type=!emberfall:broodtide,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:block_display,type=!minecraft:text_display]', 600);
  const b = await pos(SEL); check('P0 boss position read', !!b, JSON.stringify(b));
  // Wait for a quiet moment (no grab active), then place the player once at 11 blocks and watch.
  let s = await st();
  for (let i = 0; i < 40 && s && s.active > 0; i++) { await sleep(500); s = await st(); }
  await ask(`/tp @s ${b.x + 11} ${b.y} ${b.z}`, 900);
  const p0 = await pos('EmberTester'); const d0 = Math.hypot(p0.x - b.x, p0.z - b.z);
  const g0 = (await st()).grabs, i0 = (await st()).impulses;
  console.log(`placed at ${d0.toFixed(2)} blocks, grabs so far ${g0}, impulses ${i0}`);
  let landed = null, dMin = d0, minY = p0.y;
  for (let i = 0; i < 70 && !landed; i++) {
    const x = await st();
    if (x && x.impulses > i0) { landed = x; break; }
    await sleep(120);
  }
  check('P1 a new grab landed with an impulse', !!landed, JSON.stringify(landed));
  for (let i = 0; i < 12; i++) { const p = await pos('EmberTester'); if (p) { dMin = Math.min(dMin, Math.hypot(p.x - b.x, p.z - b.z)); minY = Math.min(minY, p.y); } await sleep(150); }
  const pEnd = await pos('EmberTester'); const dEnd = Math.hypot(pEnd.x - b.x, pEnd.z - b.z);
  console.log(`start ${d0.toFixed(2)}  closest ${dMin.toFixed(2)}  end ${dEnd.toFixed(2)}  minY ${minY.toFixed(2)} (start y ${p0.y})`);
  console.log('velocity packets received: ' + JSON.stringify(vel.slice(0, 4)));
  check('P1b the server SENT a velocity packet to the player', vel.length >= 1, 'count=' + vel.length);
  check('P2 the pull moved the player TOWARD the boss (closest < start - 0.8)', dMin < d0 - 0.8, `start=${d0.toFixed(2)} closest=${dMin.toFixed(2)}`);
  check('P3 it never carried the player inside the stop radius (closest >= 4.5 - 0.3)', dMin >= 4.5 - 0.3, `closest=${dMin.toFixed(2)}`);
  check('P4 it never drove the player below where it stood (minY >= start y - 0.6)', minY >= p0.y - 0.6, `minY=${minY.toFixed(2)} start=${p0.y}`);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
