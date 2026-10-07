// The owner: bot movement is "jittery, not smooth". Measure what a WATCHING client receives for the bot's body, packet by packet:
// the change in facing between consecutive updates, and the change in position. Reported as the 95th and 99th percentile and the max,
// so the same run on the old jar and the new jar can be compared. Flip-flops (turn right then back left) are counted separately.
const mineflayer = require('mineflayer'); const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
const dAng = (a, b) => { let d = (b - a) % 360; if (d > 180) d -= 360; if (d <= -180) d += 360; return d; };
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 700) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn JitBot', 2200);
  await say('/effect give @a[name=JitBot] minecraft:resistance 999 4 true', 300);
  console.log('RUN', (await say('/emberfall bot run JitBot ranger', 1500)).slice(0, 40));
  for (let i = 0; i < 40; i++) { await sleep(2000); const s = await say('/emberfall bot state JitBot', 700); if (/scouts=1/.test(s) && /weapons=\w/.test(s)) break; }
  await say('/tp @s @a[name=JitBot,limit=1]', 500);
  let botId = null; const ev = [];
  op._client.on('packet', (d, m) => {
    if (!d || typeof d !== 'object') return;
    if (m.name === 'spawn_entity' && d.type === op.registry.entitiesByName.player.id && /JitBot/.test(JSON.stringify(op.players && Object.keys(op.players)))) { /* id taken from op.entities below */ }
    if (botId === null) return;
    if (d.entityId === undefined || d.entityId !== botId) return;
    const deg = v => (Math.abs(v) <= 256 && Number.isInteger(v) && d.__raw) ? v * 360 / 256 : v;
    if (m.name === 'entity_look' || m.name === 'entity_move_look') ev.push({ yaw: d.yaw, k: m.name, t: Date.now() });
    else if (m.name === 'entity_head_rotation') ev.push({ yaw: d.headYaw * 360 / 256, k: 'head', t: Date.now() });
    else if (m.name === 'entity_teleport' || m.name === 'sync_entity_position') ev.push({ yaw: d.yaw, k: m.name, t: Date.now() });
  });
  // find the bot entity id from the server side, in case the spawn packet was missed
  const e = Object.values(op.entities).find(x => x.username === 'JitBot' || (x.type === 'player' && x.username === 'JitBot')); if (e) botId = e.id;
  for (let i = 0; i < 30; i++) { if (botId === null) { const f = Object.values(op.entities).find(x => x.username === 'JitBot'); if (f) botId = f.id; } await sleep(1000); }
  const yaws = ev.filter(x => Number.isFinite(x.yaw)).map(x => x.yaw);
  const steps = []; for (let i = 1; i < yaws.length; i++) steps.push(Math.abs(dAng(yaws[i - 1], yaws[i])));
  steps.sort((a, b) => a - b); const q = p => steps.length ? steps[Math.min(steps.length - 1, Math.floor(p * steps.length))] : NaN;
  let flips = 0; for (let i = 2; i < yaws.length; i++) { const a = dAng(yaws[i - 2], yaws[i - 1]), b = dAng(yaws[i - 1], yaws[i]); if (Math.abs(a) > 20 && Math.abs(b) > 20 && Math.sign(a) !== Math.sign(b)) flips++; }
  const kinds = {}; for (const x of ev) kinds[x.k] = (kinds[x.k] || 0) + 1;
  const out = { kinds, botId, packets: ev.length, yawSteps: steps.length, p50: +q(0.5).toFixed(1), p95: +q(0.95).toFixed(1), p99: +q(0.99).toFixed(1), max: steps.length ? +steps[steps.length - 1].toFixed(1) : NaN, over45: steps.filter(s => s > 45).length, flips };
  console.log('JITTER', JSON.stringify(out)); fs.writeFileSync('/tmp/jitter_' + (process.env.TAG || 'x') + '.json', JSON.stringify(out));
  await say('/emberfall wavestop 0', 300); process.exit(0);
})();
