// PROBE: how many blocks per tick does a human walk at movement-speed attribute 0.1 (base) ? Measured on a real client.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode survival', 300);
  await sleep(3000);
  console.log('ATTR ' + await say('/attribute EmberTester minecraft:movement_speed get', 500));
  // sample the position every 250 ms while walking forward, in several directions, and report the fastest clean interval
  const samples = [];
  for (const yaw of [0, Math.PI / 2, Math.PI, -Math.PI / 2]) {
    await op.look(yaw, 0, true);
    op.setControlState('forward', true);
    let last = op.entity.position.clone(), lastT = Date.now();
    for (let i = 0; i < 14; i++) {
      await sleep(250);
      const now = op.entity.position.clone(), t = Date.now();
      const d = Math.hypot(now.x - last.x, now.z - last.z);
      samples.push({ yaw, perTick: d / ((t - lastT) / 1000) / 20, d });
      last = now; lastT = t;
    }
    op.setControlState('forward', false);
    await sleep(300);
  }
  const moving = samples.filter(x => x.d > 0.05).map(x => x.perTick).sort((p, q) => p - q);
  const median = moving.length ? moving[Math.floor(moving.length / 2)] : NaN;
  console.log(`SAMPLES ${samples.length}, moving ${moving.length}, median ${median.toFixed(4)} blocks/tick, max ${(moving[moving.length - 1] || 0).toFixed(4)}, min ${(moving[0] || 0).toFixed(4)}`);
  op.quit(); setTimeout(() => process.exit(0), 300);
})();
