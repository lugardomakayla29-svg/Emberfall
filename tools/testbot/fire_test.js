// Does an explosion that sets fires (large fireball, fire=true) light fire? Control in the overworld must show fire.
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const op = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; op.on('message', m => chat.push(m.toString()));
const ask = async (c, w = 700) => { chat.length = 0; op.chat(c); await sleep(w); return chat.join(' | '); };
// EXPECTED_CHECKS is the number of checks a complete run makes (measured live: 2). A run with fewer did not finish, and a run
// with none (no server) must never read as a pass: the verdict needs fails === 0 AND ran >= EXPECTED_CHECKS.
const EXPECTED_CHECKS = 2;
let fails = 0; let ran = 0;
const check = (l, ok, x = '') => { ran++; console.log((ok ? 'PASS ' : 'FAIL ') + l + (x ? '  ' + x : '')); if (!ok) fails++; };
// Print the verdict and return the exit code that agrees with it. Every exit goes through here so none can disagree.
function finish() {
  const short_ = ran < EXPECTED_CHECKS;
  if (fails === 0 && !short_) { console.log('RESULT: ALL PASSED (' + ran + ' checks)'); return 0; }
  console.log('RESULT: ' + (fails || 1) + ' FAILED' + (short_ ? ' (only ' + ran + ' of ' + EXPECTED_CHECKS + ' checks ran)' : ''));
  return 1;
}
// A refused connection never fires 'spawn', so the awaited promise below would never resolve and node would exit 0 with the
// async main still pending (measured in #35). The error handler must end the run itself.
op.on('error', e => { console.log('FAIL connection error: ' + e.message); fails++; process.exit(finish()); });
op.on('kicked', r => { console.log('FAIL kicked: ' + JSON.stringify(r).slice(0, 160)); fails++; process.exit(finish()); });
const Y = 160;
const fireCount = async dim => { let n = 0; for (let dx = -5; dx <= 5; dx++) for (let dz = -5; dz <= 5; dz++) for (const dy of [0, 1]) if (/Test passed/i.test(await ask(`/execute in ${dim} if block ${400 + dx} ${Y + dy} ${400 + dz} fire`, 60))) n++; return n; };
const run = async (dim, expectNone) => {
  const tag = expectNone ? 'EXPEDITION' : 'CONTROL(overworld)'; const IN = `/execute in ${dim} run `;
  await ask(IN + 'forceload add 390 390 410 410', 900); await sleep(1500);
  await ask(IN + `fill 395 ${Y - 1} 395 405 ${Y - 1} 405 oak_planks`, 500);       // flammable floor
  await ask(IN + `fill 395 ${Y} 395 405 ${Y + 4} 405 air`, 500);
  await ask(IN + `fill 395 ${Y - 1} 395 405 ${Y - 1} 405 oak_planks`, 500);
  // a fireball with explosion power 3 detonates on contact; place it just above the planks moving down
  await ask(IN + `summon minecraft:fireball 400.5 ${Y + 1} 400.5 {ExplosionPower:3b,Motion:[0.0,-1.0,0.0],power:[0.0,-0.1,0.0]}`, 1800);
  await sleep(800);
  const n = await fireCount(dim);
  const floor = /Test passed/i.test(await ask(`/execute in ${dim} if block 400 ${Y - 1} 400 oak_planks`, 400));
  console.log(`   [${tag}] fire blocks=${n}, centre plank still there=${floor}`);
  return { n, floor };
};
(async () => {
  // Backstop: a server that takes the socket but never spawns the bot would hang the same way.
  const spawnTimer = setTimeout(() => { console.log('FAIL the bot did not spawn within 60 s'); fails++; process.exit(finish()); }, 60000);
  await new Promise(r => op.once('spawn', r)); clearTimeout(spawnTimer); await sleep(4000);
  await ask('/gamerule fire_damage true'); await ask('/gamerule mob_griefing true');
  const c = await run('minecraft:overworld', false);
  check('CONTROL: the overworld fireball lit fire or burned the floor (so the check CAN fail)', c.n > 0 || !c.floor, `fire=${c.n} floorIntact=${c.floor}`);
  const e = await run('emberfall:expedition', true);
  check('EXPEDITION: no fire and the floor is intact', e.n === 0 && e.floor, `fire=${e.n} floorIntact=${e.floor}`);
  const code = finish();
  op.quit(); setTimeout(() => process.exit(code), 500);
})().catch(e => { console.log('FAIL suite crashed: ' + (e && e.stack || e)); console.log('RESULT: crashed'); process.exit(1); });
