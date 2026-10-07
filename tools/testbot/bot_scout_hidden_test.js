// The owner saw a visible NoAI zombie trailing the EmberTester. The scout is now a husk that is never broadcast to any client.
// Truth source: the spawn_entity packets a watching human client receives. Entity type ids are read from the registry login packet
// at run time, not hard-coded. CONTROL: ordinary horde zombies/husks DO appear, so a silent client would not pass this by accident.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const reg = op.registry.entitiesByName; const idOf = n => reg[n] && reg[n].id;
  const ZOMBIE = idOf('zombie'), HUSK = idOf('husk');
  check('T0 the registry knows zombie and husk', Number.isInteger(ZOMBIE) && Number.isInteger(HUSK), `zombie=${ZOMBIE} husk=${HUSK}`);
  const spawns = [];
  op._client.on('packet', (d, m) => { if (m.name === 'spawn_entity' && (d.type === ZOMBIE || d.type === HUSK)) spawns.push({ id: d.entityId, type: d.type, x: d.x, y: d.y, z: d.z, t: Date.now() }); });
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn HideBot', 2200);
  console.log('RUN', (await say('/emberfall bot run HideBot ranger', 1500)).slice(0, 50));
  let st = '';
  for (let i = 0; i < 40; i++) { await sleep(2000); st = await say('/emberfall bot state HideBot', 700); if (/scouts=1/.test(st) && /weapons=\w/.test(st)) break; }
  check('T1 the bot has a scout (so the next checks are about a scout that exists)', /scouts=1/.test(st), st.slice(-60));
  const m = /pos=(-?[\d.]+),(-?[\d.]+)/.exec(st);
  // put the human right next to the bot so the scout is well inside any tracking range, then watch for 12 s
  await say('/tp @s @a[name=HideBot,limit=1]', 600); const t0 = Date.now(); await sleep(12000);
  const scoutHere = (await say('/execute as @e[tag=emberfall_bot_scout] run say SCOUTALIVE', 700));
  check('T2 the scout entity really exists on the server', /SCOUTALIVE/.test(scoutHere), scoutHere.slice(0, 60));
  const q = await say('/execute if entity @e[tag=emberfall_bot_scout,type=minecraft:husk]', 600);
  check('T3 the scout is a husk (not a zombie that burns)', /Test passed/.test(q), q.slice(0, 60));
  const near = spawns.filter(s => Math.hypot(s.x - (m ? +m[1] : 0), s.z - (m ? +m[2] : 0)) < 6 && s.t >= t0 - 1000);
  // T4 is only meaningful if the bot's position was read: with m == null the filter measured from the world origin and could pass blind.
  check('T4 NO zombie or husk was ever sent to the client within 6 blocks of the bot', !!m && near.length === 0, `${near.length} spawn packets near the bot (bot pos ${m ? m[1] + ',' + m[2] : 'NOT READ'}), ${spawns.length} total`);
  // CONTROL: a horde mob IS sent. Summon a visible husk next to the human and require its packet, otherwise T4 proves nothing.
  const before = spawns.length;
  await say('/summon minecraft:husk ~ ~ ~3 {NoAI:1b,Tags:["ctl"]}', 1500);
  check('T5 CONTROL: an ordinary husk summoned beside the human IS sent to the client', spawns.length > before, `${spawns.length - before} new spawn packets`);
  await say('/kill @e[tag=ctl]', 400); await say('/emberfall wavestop 0', 400);
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails); process.exit(fails === 0 ? 0 : 1);
})();
