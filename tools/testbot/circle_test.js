// CircleBoundary test on a REAL pasted (inPlace=false) arena. Positions are read from the SERVER (/data get entity Pos).
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const op = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const vels = []; const chat = []; op.on('message', m => chat.push(m.toString())); op.on('error', e => console.log('err', e.message));
const ask = async (c, w = 700) => { chat.length = 0; op.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (l, ok, x = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (x ? '  ' + x : '')); if (!ok) fails++; };
const pos = async () => { const r = await ask('/data get entity EmberTester Pos', 500); const m = r.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/); return m ? m.slice(1).map(Number) : null; };
const dim = async () => (await ask('/data get entity EmberTester Dimension', 400)).match(/"([^"]+)"/)?.[1];
op._client.on('packet', (d, meta) => { if (meta.name === 'entity_velocity' && d.entityId === op.entity.id) vels.push(d.velocity); });
(async () => {
  await new Promise(r => op.once('spawn', r)); await sleep(4500);
  await ask('/gamemode survival EmberTester'); await ask('/effect give EmberTester minecraft:resistance 600 4 true'); await ask('/effect give EmberTester minecraft:regeneration 600 4 true');
  const pasted = await ask('/emberfall paste starter_arena', 2500); console.log('paste:', pasted.slice(0, 150));
  const slot = (pasted.match(/slot (\d+)/) || [])[1]; check('paste returned a slot', slot !== undefined, 'slot=' + slot);
  console.log('join :', (await ask(`/emberfall join ${slot} EmberTester`, 2500)).slice(0, 100));
  await ask(`/emberfall wavestop ${slot}`, 500);
  await sleep(2500);
  check('player is in emberfall:expedition after joining', (await dim()) === 'emberfall:expedition', 'dim=' + (await dim()));
  const c = await pos(); console.log('   entry position', c);
  // centre of the pasted 21x21 box: use the entry point as reference, then measure distance from the box centre via the arenabox-free method:
  // the guard's centre is the box centre; the entry marker is near it, so measure from the entry.
  await sleep(3000);
  const still = await pos();
  check('CONTROL: an idle player inside is NOT moved over 3 s', Math.hypot(still[0] - c[0], still[2] - c[2]) < 0.5, `moved ${Math.hypot(still[0] - c[0], still[2] - c[2]).toFixed(2)}`);
  const R = 8.5;
  const dist = p => Math.hypot(p[0] - c[0], p[2] - c[2]);
  // 1) just over the line (soft push). A mineflayer bot keeps sending its own position and never obeys a push,
  //    so judge the server's entity_velocity packet (a real client applies it), as boundary_test.js does.
  //    x = centre + 10.5 -> overshoot 2.0 (inside the 3.0 soft band). Designed push = 0.35 + 2.0*0.15 = 0.65 inward.
  vels.length = 0;
  await ask(`/tp EmberTester ${c[0] + 10.5} ${c[1]} ${c[2]}`, 300); await sleep(1200);
  const inward = vels.filter(v => v.x < -0.3);
  console.log('   velocity packets', vels.length, 'inward', inward.length, inward[0] ? JSON.stringify(inward[0]) : '');
  check('SOFT: a player 2.0 past the line gets an inward push packet', inward.length > 0);
  // The formula is push = 0.35 + overshoot*0.15, so the packet itself tells us the overshoot the server saw.
  // Check that overshoot is inside the soft band (0 < over <= 3) and that the push points straight inward.
  const impliedOver = inward.length ? (-inward[0].x - 0.35) / 0.15 : NaN;
  check('SOFT: the push strength implies an overshoot inside the soft band (0 to 3)', impliedOver > 0 && impliedOver <= 3.0, `implied overshoot=${impliedOver.toFixed(3)}`);
  check('SOFT: the push points straight inward (z about 0)', inward.length > 0 && Math.abs(inward[0].z) < 0.06);
  await ask(`/tp EmberTester ${c[0]} ${c[1]} ${c[2]}`, 300); await sleep(600);   // back to the centre, inside
  vels.length = 0; await sleep(2000);
  const idlePush = vels.filter(v => Math.abs(v.x) > 0.3 || Math.abs(v.z) > 0.3).length;
  check('SOFT (control): an idle player at the centre gets NO strong push packets', idlePush === 0, `strong packets=${idlePush}`);
  // 2) far out (hard clamp)
  await ask(`/tp EmberTester ${c[0] + 45} ${c[1]} ${c[2] + 30}`, 300); await sleep(1500);
  p = await pos(); console.log('   after far overshoot', p, 'dist', dist(p).toFixed(2));
  check('HARD: a player 54 blocks out is put back inside the circle', dist(p) < R + 0.5, `dist=${dist(p).toFixed(2)}`);
  check('HARD: ... and stays in the expedition dimension', (await dim()) === 'emberfall:expedition');
  // 3) too high (over the wall)
  await ask(`/tp EmberTester ${c[0]} ${c[1] + 45} ${c[2]}`, 300); await sleep(1500);
  p = await pos(); console.log('   after height', p);
  check('HEIGHT: a player 45 blocks up is brought back down', p[1] < c[1] + 12, `y=${p[1].toFixed(2)} (floor ${c[1]})`);
  // 4) after all of that the player is alive and still in the run
  check('player is still alive', !/Died|dead/i.test(await ask('/data get entity EmberTester Health', 500)));
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  await ask(`/emberfall leave EmberTester`, 800);
  op.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
})();
