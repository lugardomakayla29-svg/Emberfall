// EmberTester step 2b: what do idle bots cost? Server tick time with 0, 1, 5, 9 bots, and heap growth (packet leak check).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(4000);
  const say = async (cmd, w = 900) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  const mspt = async () => { await say('/tick rate 20', 200); const r = await say('/tick query', 900); const m = /Average time per tick: ([\d.]+)ms/.exec(r); return m ? parseFloat(m[1]) : NaN; };
  const mem = async () => { const r = await say('/emberfall bot mem', 2500); const m = /usedMb=(\d+)/.exec(r); return m ? parseInt(m[1]) : NaN; };
  const settle = async () => { await sleep(7000); };
  const rows = [];
  let n = 0;
  for (const target of [0, 1, 5, 9]) {
    while (n < target) { n++; await say(`/emberfall bot spawn CostBot${n}`, 1200); }
    await settle();
    const t = []; for (let i = 0; i < 3; i++) { t.push(await mspt()); await sleep(2500); }
    rows.push({ bots: target, msptAvg: +(t.reduce((a, b) => a + b, 0) / t.length).toFixed(2), msptMax: Math.max(...t) });
  }
  // leak check: 9 bots online, heap after GC now and after a 30 s window
  const m0 = await mem(); await sleep(30000); const m1 = await mem();
  console.log('COST rows ' + JSON.stringify(rows));
  console.log('LEAK heapMb start=' + m0 + ' after30s=' + m1 + ' delta=' + (m1 - m0));
  for (let i = 1; i <= 9; i++) await say(`/emberfall bot remove CostBot${i}`, 400);
  op.quit(); setTimeout(() => process.exit(0), 400);
})();
