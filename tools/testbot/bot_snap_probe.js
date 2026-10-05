// PROBE: does snapTo move a bot, and does a SECOND real client (the watcher) see the bot move? teleportTo is the control.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const where = async n => { const r = await say(`/data get entity ${n} Pos`, 600); const m = /\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/.exec(r); return m ? +m[3] : NaN; };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn SnapBot', 1800); await say('/emberfall bot spawn TpBot', 1800);
  await sleep(2500);
  const uuids = {};
  const seen = n => { const e = Object.values(op.entities).find(x => x.type === 'player' && x !== op.entity && ((x.username === n) || (x.uuid && x.uuid === uuids[n]))); return e ? e.position.z : NaN; };
  const players = () => Object.values(op.entities).filter(x => x.type === 'player' && x !== op.entity).map(x => `${x.username || '?'}@${x.position.x.toFixed(1)},${x.position.z.toFixed(1)}`);
  console.log('WATCHER ENTITIES BEFORE ' + JSON.stringify(players()));
  const s0 = await where('SnapBot'), t0 = await where('TpBot');
  const w0s = seen('SnapBot'), w0t = seen('TpBot');
  await say('/emberfall bot push SnapBot snap', 500); await say('/emberfall bot push TpBot teleport', 500);
  await sleep(2500);
  const s1 = await where('SnapBot'), t1 = await where('TpBot');
  console.log('WATCHER ENTITIES AFTER ' + JSON.stringify(players()));
  console.log(`SERVER snap dz=${(s1 - s0).toFixed(2)} teleport dz=${(t1 - t0).toFixed(2)}`);
  console.log(`WATCHER (a real client) sees snap z ${w0s} -> ${seen('SnapBot')}, teleport z ${w0t} -> ${seen('TpBot')}`);
  await say('/emberfall bot remove SnapBot', 500); await say('/emberfall bot remove TpBot', 500);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
