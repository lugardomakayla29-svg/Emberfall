const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('kicked', (r) => log('KICKED: ' + JSON.stringify(r)));
let started = false;
bot.on('spawn', () => {
  if (started) { log('(ignoring duplicate spawn)'); return; }
  started = true;
  log('spawn at ' + JSON.stringify(bot.entity.position));
  const t = (ms, f) => setTimeout(f, ms);
  // Rapid back-to-back start/leave cycles, minimal gaps, to stress-test
  // the teleport/teardown ordering fix under worst-case timing.
  for (let i = 0; i < 6; i++) {
    const base = 500 + i * 900;
    t(base, () => { log(`CMD ${i}: /expedition`); bot.chat('/expedition'); });
    t(base + 300, () => { log(`CMD ${i}: /expedition leave`); bot.chat('/expedition leave'); });
  }
  t(500 + 6*900 + 1500, () => { log('DONE stress cycle'); bot.quit(); process.exit(0); });
});
