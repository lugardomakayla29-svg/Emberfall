// Harness helper: builds the static map for slot 0 once, right after the server starts, so a suite's /expedition only has to START a run
// (as in normal play after the first run). Exits 0 when the build finished, 1 on timeout.
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
bot.once('spawn', async () => {
  await sleep(5000); bot.chat('/op EmberTester'); await sleep(600); bot.chat('/emberfall mapbuild 0');
  let done = null; for (let i = 0; i < 100 && !done; i++) { await sleep(2000); done = lines.find(l => /MAPDONE/.test(l)); }
  console.log(done ? 'PREBUILD OK ' + done.slice(0, 120) : 'PREBUILD TIMEOUT');
  bot.quit(); setTimeout(() => process.exit(done ? 0 : 1), 500);
});
