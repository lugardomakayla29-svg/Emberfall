// CONTROL: can this bot dig a survival block at all (overworld, no protection)? Proves the harness before judging protection.
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const dim = process.argv[2] || 'minecraft:overworld', BASEY = 150;
const op = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const pl = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'PlainPlayer', version: '1.21.11', auth: 'offline' });
const chat = []; op.on('message', m => chat.push(m.toString()));
const ask = async c => { chat.length = 0; op.chat(c); await sleep(900); return chat.join(' | ').slice(0, 110); };
let n = 0; const go = async () => { if (++n < 2) return; await sleep(4000);
  const IN = `/execute in ${dim} run `;
  console.log('forceload:', await ask(IN + 'forceload add 190 190 210 210'));
  await sleep(2000);
  console.log('platform :', await ask(IN + `fill 195 ${BASEY - 1} 195 205 ${BASEY - 1} 205 dirt`));
  console.log('clear air:', await ask(IN + `fill 195 ${BASEY} 195 205 ${BASEY + 4} 205 air`));
  console.log('gamemode :', await ask('/gamemode survival PlainPlayer'));
  console.log('tp       :', await ask(`/execute as PlainPlayer in ${dim} run tp @s 200.5 ${BASEY} 200.5`));
  for (let i = 0; i < 40; i++) { const b = pl.blockAt(new Vec3(200, BASEY - 1, 200)); if (b && b.name === 'dirt' && pl.entity.onGround) break; await sleep(250); }
  const b = pl.blockAt(new Vec3(200, BASEY - 1, 200));
  console.log('bot: block under feet =', b && b.name, '| y =', pl.entity.position.y.toFixed(2), '| onGround =', pl.entity.onGround, '| canDig =', b && pl.canDigBlock(b), '| dimension =', pl.game.dimension);
  const target = pl.blockAt(new Vec3(201, BASEY - 1, 200));
  let res = 'completed'; const t0 = Date.now();
  try { await Promise.race([pl.dig(target), sleep(9000).then(() => { throw new Error('TIMEOUT'); })]); } catch (e) { res = e.message; }
  await sleep(600);
  const after = pl.blockAt(new Vec3(201, BASEY - 1, 200));
  console.log(`DIG RESULT [${dim}]: ${res} after ${Date.now() - t0} ms; target block now = ${after && after.name}`);
  console.log('SERVER says target is dirt?  ->', await ask(`/execute in ${dim} if block 201 ${BASEY - 1} 200 dirt`));
  console.log('SERVER says target is air?   ->', await ask(`/execute in ${dim} if block 201 ${BASEY - 1} 200 air`));
  process.exit(0); };
op.once('spawn', go); pl.once('spawn', go);
