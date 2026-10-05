const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const op = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; op.on('message', m => chat.push(m.toString()));
const ask = async (c, w = 700) => { chat.length = 0; op.chat(c); await sleep(w); return chat.join(' | '); };
const pos = async () => { const r = await ask('/data get entity EmberTester Pos', 250); const m = r.match(/\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/); return m ? m.slice(1).map(Number) : null; };
(async () => {
  await new Promise(r => op.once('spawn', r)); await sleep(4500);
  await ask('/gamemode survival EmberTester'); await ask('/effect give EmberTester minecraft:resistance 600 4 true');
  const pasted = await ask('/emberfall paste starter_arena', 2500); const slot = (pasted.match(/slot (\d+)/) || [])[1];
  await ask(`/emberfall join ${slot} EmberTester`, 2500); await ask(`/emberfall wavestop ${slot}`, 400); await sleep(2000);
  for (const off of [10.5, 11.5, 12.5]) {
    await ask(`/tp EmberTester ${10.5 + off} 65 10.5`, 200);
    const t0 = Date.now(); const s = [];
    for (let i = 0; i < 8; i++) { const p = await pos(); s.push(p ? (p[0] - 10.5).toFixed(2) : 'null'); await sleep(150); }
    console.log(`placed ${off} east of centre -> dx over time: ${s.join(', ')}`);
  }
  await ask(`/emberfall leave EmberTester`, 600); op.quit(); setTimeout(() => process.exit(0), 400);
})();
