// Does an explosion that sets fires (large fireball, fire=true) light fire? Control in the overworld must show fire.
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const op = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; op.on('message', m => chat.push(m.toString()));
const ask = async (c, w = 700) => { chat.length = 0; op.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (l, ok, x = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (x ? '  ' + x : '')); if (!ok) fails++; };
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
  await new Promise(r => op.once('spawn', r)); await sleep(4000);
  await ask('/gamerule fire_damage true'); await ask('/gamerule mob_griefing true');
  const c = await run('minecraft:overworld', false);
  check('CONTROL: the overworld fireball lit fire or burned the floor (so the check CAN fail)', c.n > 0 || !c.floor, `fire=${c.n} floorIntact=${c.floor}`);
  const e = await run('emberfall:expedition', true);
  check('EXPEDITION: no fire and the floor is intact', e.n === 0 && e.floor, `fire=${e.n} floorIntact=${e.floor}`);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  op.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
})();
