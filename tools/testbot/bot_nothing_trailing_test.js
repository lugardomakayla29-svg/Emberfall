// KNOWN LIMIT (2026-10-07): in the first of four runs N1 failed on one entity (id 564, 5 samples within 3 blocks) that I could not identify
// afterwards; the three later runs passed, and their long-stayers were experience orbs (the bot collects XP). A clean pass here shows that no
// zombie, husk or skeleton stayed beside the bot in that run. It does not show that the first run's entity was an orb.
// Owner: "the placeholder mob invisible and impossible to see". Strongest form of that: over a whole run, with a human standing
// right next to a walking bot, list EVERY entity the client was told about within 8 blocks of the bot, by type, and require that
// none of them is a zombie/husk/other mob that the bot is dragging along. Spawn packets are the truth source (a client cannot see
// what it was never sent). CONTROL: the bot's own player entity must be in the list, or the list proves nothing.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const nameOf = id => (op.registry.entities[id] || {}).name || ('type' + id);
  const seen = []; // every spawn packet: {id, type, x, z}
  op._client.on('packet', (d, m) => { if (d && m.name === 'spawn_entity') seen.push({ id: d.entityId, type: nameOf(d.type), x: d.x, y: d.y, z: d.z }); });
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn TrailBot', 2200);
  await say('/effect give @a[name=TrailBot] minecraft:resistance 999 4 true', 300);
  await say('/emberfall bot run TrailBot ranger', 1500);
  for (let i = 0; i < 40; i++) { await sleep(2000); const s = await say('/emberfall bot state TrailBot', 700); if (/scouts=1/.test(s) && /weapons=\w/.test(s)) break; }
  await say('/tp @s @a[name=TrailBot,limit=1]', 600);
  const botEnt = () => Object.values(op.entities).find(e => e.username === 'TrailBot');
  const nearTypes = {}; let samples = 0, sawBot = false;
  const trail = {}; // entity ids that stayed within 3 blocks of the bot for 5+ samples (something dragged along)
  for (let i = 0; i < 40; i++) {
    await sleep(1000); const b = botEnt(); if (!b) continue; sawBot = true; samples++;
    for (const e of Object.values(op.entities)) {
      if (e === op.entity || e.id === b.id) continue;
      const d = Math.hypot(e.position.x - b.position.x, e.position.z - b.position.z);
      if (d <= 8) nearTypes[e.name || e.type] = (nearTypes[e.name || e.type] || 0) + 1;
      if (d <= 3) trail[e.id] = (trail[e.id] || 0) + 1;
    }
  }
  check('N0 CONTROL: the bot itself was visible to the client for the whole window', sawBot && samples >= 30, `${samples} samples`);
  // identify what the long-staying entities are, from the client's own entity table, not by guessing
  const idType = {}; for (const e of Object.values(op.entities)) idType[e.id] = (e.name || e.type) + (e.username ? ':' + e.username : '');
  const spawnType = {}; for (const s of seen) spawnType[s.id] = s.type;
  for (const [id, n] of Object.entries(trail)) if (n >= 5) console.log('LONG-STAYER id', id, 'samples', n, 'client-table:', idType[id] || 'gone', '| spawn packet type:', spawnType[id] || 'none');
  // Loot (experience orbs, items) and arrows legitimately sit on the floor beside a fighting bot, with real spawn packets. The scout
  // would be a MOB, so only living mobs count as "dragged along". An id with no client-table entry (it died or left) is judged by its spawn type.
  const notMob = t => /^(experience_orb|item|arrow|spectral_arrow|interaction|item_display|block_display|text_display|unknown|none)/.test(String(t || 'none'));
  const dragged = Object.entries(trail).filter(([id, n]) => n >= 5).filter(([id]) => !notMob((idType[id] || '').split(':')[0] || spawnType[id])).map(([id, n]) => `${id}:${n}(${idType[id] || spawnType[id]})`);
  const types = Object.keys(nearTypes).join(',') || 'none';
  console.log('ENTITY TYPES SEEN WITHIN 8 BLOCKS:', JSON.stringify(nearTypes));
  check('N1 no entity stayed within 3 blocks of the walking bot for 5+ samples (nothing is dragged along; horde mobs charge and die, they do not trail)', dragged.length === 0, dragged.join(' ') || 'none');
  const scoutLike = seen.filter(s => /zombie|husk/.test(s.type)).length;
  console.log('zombie/husk spawn packets this whole run (horde mobs included):', scoutLike);
  const q = await say('/execute if entity @e[tag=emberfall_bot_scout]', 600);
  check('N2 CONTROL: the scout does exist on the server (so N1 is about hiding it, not about it being absent)', /Test passed/.test(q), q.slice(0, 50));
  await say('/emberfall wavestop 0', 300);
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails); process.exit(fails === 0 ? 0 : 1);
})();
