// Rift show, live, CONTROL-SUBTRACTED. The idle world spawns entities on its own (measured: 171 -> 219 in 8 s with no Rift), so a raw
// "entity count unchanged" check is invalid. Instead: an idle control window and a Rift window of the SAME length, compared by the
// entity TYPES the client is told to spawn. The Rift must add no spawn_entity of any type that the idle window did not also have,
// and must add the particle and sound packets the schedule promises.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
let pk = {}, spawnTypes = {}, spawnPos = [], counting = false;
bot._client.on('packet', (d, m) => { if (!counting) return; pk[m.name] = (pk[m.name] || 0) + 1; if (m.name === 'spawn_entity') { spawnTypes[d.type] = (spawnTypes[d.type] || 0) + 1; spawnPos.push({ type: d.type, x: d.x, y: d.y, z: d.z }); } });
const window = async (ms, cmd) => { pk = {}; spawnTypes = {}; spawnPos = []; counting = true; const r = cmd ? await ask(cmd, 400) : ''; await sleep(ms); counting = false; return { r, pk: { ...pk }, types: { ...spawnTypes }, pos: [...spawnPos] }; };
const state = async () => { const r = await ask('/emberfall rift state', 600); const m = /RIFT active=(\d+) entities=(\d+)/.exec(r); return m ? { active: +m[1], entities: +m[2] } : null; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode creative'); await ask('/emberfall rift clear', 200);
  await ask('/gamerule spawn_mobs false', 200); await ask('/gamerule mob_spawning false', 200); await ask('/gamerule doMobSpawning false', 200);
  await ask('/difficulty peaceful', 200); await ask('/kill @e[type=!player]', 800); await sleep(3000);   // let chunk-load spawns settle
  const s0 = await state(); R('T0 the state command answers', !!s0, JSON.stringify(s0)); if (!s0) process.exit(1);
  const sum = ws => ws.reduce((a, w) => { for (const [k, v] of Object.entries(w.pk)) a.pk[k] = (a.pk[k] || 0) + v; for (const [k, v] of Object.entries(w.types)) a.types[k] = (a.types[k] || 0) + v; a.r += w.r; a.pos.push(...w.pos); return a; }, { pk: {}, types: {}, pos: [], r: '' });
  const idles = [], opens = [];
  for (let i = 0; i < 3; i++) { idles.push(await window(7000, null)); opens.push(await window(7000, '/emberfall rift open')); }   // interleaved so drift hits both alike
  const idle = sum(idles), open = sum(opens);
  const mid = await state();
  R('T1 every opening reported a schedule', opens.every(w => /RIFT opening events=(\d+)/.test(w.r) && +/events=(\d+)/.exec(w.r)[1] > 50), opens.map(w => w.r.slice(0, 24)).join(' / '));
  R('T2 the openings finished by themselves', mid && mid.active === 0, JSON.stringify(mid));
  const idleP = idle.pk.world_particles || 0, openP = open.pk.world_particles || 0;
  R('T3 the Rift adds particle packets over the idle control', openP > idleP + 120, `3 idle=${idleP} 3 rift=${openP}`);
  const idleS = idle.pk.sound_effect || 0, openS = open.pk.sound_effect || 0;
  R('T4 the Rift adds sound packets over the idle control', openS > idleS + 12, `3 idle=${idleS} 3 rift=${openS}`);
  R('T5 the Rift adds chat lines', (open.pk.system_chat || 0) >= (idle.pk.system_chat || 0) + 9, `3 idle=${idle.pk.system_chat || 0} 3 rift=${open.pk.system_chat || 0}`);
  const tot = o => Object.values(o.types).reduce((a, b) => a + b, 0);
  // Per type: the Rift windows may not spawn more of any type than the idle windows did, beyond a small noise allowance of 3.
  // The Rift sits 6 blocks in front of the player and 5 up. Anything the Rift could have spawned would be within a few blocks of it.
  const me = bot.entity.position, yaw = bot.entity.yaw;
  const ax = me.x - Math.sin(yaw) * 6, ay = me.y + 5, az = me.z - Math.cos(yaw) * 6;
  const near = open.pos.filter(p => Math.hypot(p.x - ax, p.y - ay, p.z - az) < 12);
  R('T6 no entity of ANY type spawned within 12 blocks of the Rift during three openings', near.length === 0, `near=${JSON.stringify(near.slice(0, 4))} (all spawns this run: ${JSON.stringify(open.types)})`);
  const idleNear = idle.pos.filter(p => Math.hypot(p.x - ax, p.y - ay, p.z - az) < 12);
  R('T7 control: the same zone saw nothing during the idle windows either', idleNear.length === 0, `near=${JSON.stringify(idleNear.slice(0, 4))}`);
  const close = await window(5000, '/emberfall rift close');
  R('T8 closing reports a schedule and sends particles', /RIFT closing events=(\d+)/.test(close.r) && (close.pk.world_particles || 0) > 20, `${close.r.slice(0, 40)} particles=${close.pk.world_particles || 0}`);
  const s1 = await state(); R('T9 nothing left running', s1 && s1.active === 0, JSON.stringify(s1));
  console.log(fails === 0 ? 'ALL PASS' : `FAILED ${fails}`); bot.quit(); process.exit(fails ? 1 : 0);
});
