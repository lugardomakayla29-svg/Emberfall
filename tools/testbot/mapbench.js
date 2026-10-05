// MAPBENCH driver: runs /emberfall mapbench <perTick> and waits for the server log line.
const mineflayer = require('mineflayer');
const fs = require('fs');
const perTick = parseInt(process.argv[2] || '2000', 10);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.once('spawn', async () => {
  await sleep(5000);
  bot.chat('/emberfall mapbench ' + perTick);
  const t0 = Date.now();
  while (Date.now() - t0 < 240000) {
    await sleep(2000);
    const log = fs.readFileSync('/tmp/mapbench_server.log', 'utf8');
    const m = log.split('\n').filter(l => l.includes('MAPBENCH done')).pop();
    if (m && log.lastIndexOf('MAPBENCH done') > (global.startPos || 0)) { console.log(m.replace(/^.*MAPBENCH done: /, 'perTick=' + perTick + ' -> ')); break; }
  }
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('bot error', e.message); process.exit(1); });
