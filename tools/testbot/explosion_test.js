// Explosions in emberfall:expedition must NOT destroy blocks or light fire; the same blast in the overworld
// (CONTROL) must. Judged only by the server (/execute if block). The player must still take blast damage.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = u => mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: u, version: '1.21.11', auth: 'offline' });
const op = mk('EmberTester'); const chat = [];
op.on('message', m => chat.push(m.toString())); op.on('error', e => console.log('err', e.message));
const ask = async (c, w = 700) => { chat.length = 0; op.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (l, ok, x = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (x ? '  ' + x : '')); if (!ok) fails++; };
const Y = 150;
const count = async (dim, box, id) => {   // count matching blocks by filling into a scoreboard-free test: use 'execute if blocks' via clone compare is heavy; sample a grid instead
  let n = 0;
  for (let dx = -3; dx <= 3; dx += 1) for (let dz = -3; dz <= 3; dz += 1) {
    if (/Test passed/i.test(await ask(`/execute in ${dim} if block ${box.x + dx} ${box.y} ${box.z + dz} ${id}`, 120))) n++;
  }
  return n;
};
const run = async (dim, expectIntact) => {
  const tag = expectIntact ? 'EXPEDITION' : 'CONTROL(overworld)';
  const IN = `/execute in ${dim} run `;
  const cx = 200, cz = 200;
  await ask(IN + `forceload add 190 190 210 210`, 900); await sleep(1500);
  await ask(IN + `fill 195 ${Y - 1} 195 205 ${Y - 1} 205 stone`, 500);
  await ask(IN + `fill 195 ${Y} 195 205 ${Y + 6} 205 air`, 500);
  await ask(IN + `fill 195 ${Y - 1} 195 205 ${Y - 1} 205 stone`, 500);
  await ask(IN + `fill 198 ${Y} 198 202 ${Y} 202 oak_planks`, 500);         // a 5x5 wooden pad to blow up
  const before = await count(dim, { x: cx, y: Y, z: cz }, 'oak_planks');
  check(`[${tag}] staged: server sees wood before the blast`, before >= 25, `planks(7x7 sample)=${before}`);
  // one TNT-strength creeper explosion (charged creeper = power 6) centred on the pad, plus primed TNT
  await ask(IN + `summon minecraft:creeper ${cx}.5 ${Y + 1} ${cz}.5 {powered:1b,Fuse:1,ignited:1b,ExplosionRadius:6b}`, 1500);
  await ask(IN + `summon minecraft:tnt ${cx}.5 ${Y + 1} ${cz}.5 {fuse:1}`, 1500);
  await sleep(1500);
  const after = await count(dim, { x: cx, y: Y, z: cz }, 'oak_planks');
  const stone = await count(dim, { x: cx, y: Y - 1, z: cz }, 'stone');
  const fire = await count(dim, { x: cx, y: Y, z: cz }, 'fire');
  console.log(`   [${tag}] planks ${before} -> ${after}, floor stone left ${stone}/49, fire blocks ${fire}`);
  if (expectIntact) { check(`[${tag}] blast left every plank in place`, after === before); check(`[${tag}] blast left the floor whole`, stone === 49); check(`[${tag}] no fire lit`, fire === 0); }
  else { check(`[${tag}] control: the same blast DID destroy blocks`, after < before / 2 || stone < 49, `planks ${before}->${after} stone=${stone}`); }
};
(async () => {
  await new Promise(r => op.once('spawn', r)); await sleep(4000);
  await ask('/gamerule mob_griefing true'); await ask('/gamerule tnt_explodes true');
  await run('minecraft:overworld', false);
  await run('emberfall:expedition', true);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  op.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
})();
