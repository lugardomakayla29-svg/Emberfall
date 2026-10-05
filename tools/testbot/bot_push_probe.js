// PROBE (not a pass/fail suite): which ways of moving a client-less ServerPlayer actually change its position?
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const where = async n => { const r = await say(`/data get entity ${n} Pos`, 600); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? { x: +m[1], y: +m[2], z: +m[3] } : null; };
  await say('/gamemode creative', 300);
  for (const n of ['NoPush', 'ModeZza', 'ModeVel', 'ModeTp']) { await say(`/emberfall bot spawn ${n}`, 1800);  }
  await sleep(3000);
  const start = {}; for (const n of ['NoPush', 'ModeZza', 'ModeVel', 'ModeTp']) start[n] = await where(n);
  console.log('START ' + JSON.stringify(start));
  console.log(await say('/emberfall bot push ModeZza zza', 600));
  console.log(await say('/emberfall bot push ModeVel velocity', 600));
  console.log(await say('/emberfall bot push ModeTp teleport', 600));
  await sleep(2500);
  for (const n of ['NoPush', 'ModeZza', 'ModeVel', 'ModeTp']) { const e = await where(n); console.log(`MOVED ${n} dz=${e && start[n] ? (e.z - start[n].z).toFixed(2) : 'n/a'} dx=${e && start[n] ? (e.x - start[n].x).toFixed(2) : 'n/a'}`); }
  for (const n of ['NoPush', 'ModeZza', 'ModeVel', 'ModeTp']) await say(`/emberfall bot remove ${n}`, 500);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
