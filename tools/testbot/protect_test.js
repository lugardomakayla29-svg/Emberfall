// MapProtection test. Judges ONLY by what the SERVER reports (/execute if block), never by the bot's own view,
// because a survival client predicts breaks/placements locally and can look "successful" when the server refused.
// Every route runs twice: in emberfall:expedition (must be REFUSED) and in the overworld (CONTROL, must SUCCEED).
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = u => mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: u, version: '1.21.11', auth: 'offline' });
const op = mk('EmberTester'), pl = mk('PlainPlayer'); const opChat = [];
op.on('message', m => opChat.push(m.toString())); op.on('error', e => console.log('op err', e.message)); pl.on('error', e => console.log('pl err', e.message));
const ask = async (cmd, w = 700) => { opChat.length = 0; op.chat(cmd); await sleep(w); return opChat.join(' | '); };
let fails = 0; const check = (l, ok, x = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + l + (x ? '  ' + x : '')); if (!ok) fails++; };
const Y = 150;
const is = async (dim, x, y, z, id) => /Test passed/i.test(await ask(`/execute in ${dim} if block ${x} ${y} ${z} ${id}`, 500));
const stage = async dim => {
  const IN = `/execute in ${dim} run `;
  await ask(IN + 'forceload add 190 190 210 210', 900); await sleep(1500);
  await ask(IN + `fill 195 ${Y - 1} 195 205 ${Y - 1} 205 dirt`, 600); await ask(IN + `fill 195 ${Y} 195 205 ${Y + 6} 205 air`, 600);
  await ask(IN + `fill 195 ${Y - 1} 195 205 ${Y - 1} 205 dirt`, 600);
  await ask(IN + `setblock 203 ${Y - 1} 200 dirt`, 400);
  await ask('/gamemode survival PlainPlayer', 400);
  await ask(`/execute as PlainPlayer in ${dim} run tp @s 200.5 ${Y} 200.5`, 900);
  for (let i = 0; i < 40; i++) { const b = pl.blockAt(new Vec3(200, Y - 1, 200)); if (b && b.name === 'dirt' && pl.entity.onGround) break; await sleep(250); }
  await ask('/clear PlainPlayer', 400);
  await ask('/give PlainPlayer minecraft:cobblestone 16', 300); await ask('/give PlainPlayer minecraft:water_bucket 1', 300);
  await ask('/give PlainPlayer minecraft:flint_and_steel 1', 300); await ask('/give PlainPlayer minecraft:lava_bucket 1', 300); await sleep(800);
};
const tryDig = async pos => { try { await Promise.race([pl.dig(pl.blockAt(pos)), sleep(7000).then(() => { throw 0; })]); } catch (e) {} await sleep(600); };
const run = async (dim, expectBlocked) => {
  const tag = expectBlocked ? 'EXPEDITION' : 'CONTROL(overworld)';
  await stage(dim);
  check(`[${tag}] staged: server sees dirt at the dig target`, await is(dim, 201, Y - 1, 200, 'dirt'));
  await tryDig(new Vec3(201, Y - 1, 200));
  const broke = await is(dim, 201, Y - 1, 200, 'air');
  check(`[${tag}] mining: block ${expectBlocked ? 'SURVIVES' : 'is BROKEN (control)'}`, broke !== expectBlocked, `serverSaysAir=${broke}`);
  const cob = pl.inventory.items().find(i => i.name === 'cobblestone');
  if (cob) { await pl.equip(cob, 'hand'); try { await Promise.race([pl.placeBlock(pl.blockAt(new Vec3(199, Y - 1, 200)), new Vec3(0, 1, 0)), sleep(3000)]); } catch (e) {} }
  await sleep(700);
  const placed = await is(dim, 199, Y, 200, 'cobblestone');
  check(`[${tag}] placing: block ${expectBlocked ? 'is REFUSED' : 'is PLACED (control)'}`, placed !== expectBlocked, `serverSaysCobble=${placed}`);
  const fs = pl.inventory.items().find(i => i.name === 'flint_and_steel');
  if (fs) { await pl.equip(fs, 'hand'); try { await Promise.race([pl.activateBlock(pl.blockAt(new Vec3(198, Y - 1, 198)), new Vec3(0, 1, 0)), sleep(3000)]); } catch (e) {} }
  await sleep(700);
  const fire = await is(dim, 198, Y, 198, 'fire');
  check(`[${tag}] flint and steel: fire ${expectBlocked ? 'is REFUSED' : 'is LIT (control)'}`, fire !== expectBlocked, `serverSaysFire=${fire}`);
  const wb = pl.inventory.items().find(i => i.name === 'water_bucket');
  if (wb) { await pl.equip(wb, 'hand'); try { await pl.lookAt(new Vec3(197.5, Y - 1, 203.5), true); await sleep(300); pl.activateItem(); } catch (e) {} }
  await sleep(1200);
  const w = (await is(dim, 197, Y, 203, 'water')) || (await is(dim, 197, Y, 204, 'water')) || (await is(dim, 198, Y, 203, 'water'));
  check(`[${tag}] water bucket: water ${expectBlocked ? 'is REFUSED' : 'is POURED (control)'}`, w !== expectBlocked, `serverSaysWater=${w}`);
};
(async () => {
  await new Promise(r => { let n = 0; const t = () => { if (++n === 2) r(); }; op.once('spawn', t); pl.once('spawn', t); });
  await sleep(4500);
  await run('minecraft:overworld', false);
  await run('emberfall:expedition', true);
  console.log(fails ? `RESULT: ${fails} FAILED` : 'RESULT: ALL PASSED');
  op.quit(); pl.quit(); setTimeout(() => process.exit(fails ? 1 : 0), 500);
})();
