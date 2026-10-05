const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25565,
  username: 'EmberTester',
  version: '1.21.11',
  auth: 'offline'
});

let startTime = Date.now();
function log(msg) {
  console.log(`[${((Date.now() - startTime) / 1000).toFixed(1)}s] ${msg}`);
}
function cmd(c) {
  log('CMD: ' + c);
  bot.chat(c);
}
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (err) => log('ERROR: ' + err.message || err));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

function wait(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned at ' + JSON.stringify(bot.entity.position));

  cmd('/gamemode survival'); await wait(600);
  cmd('/effect give EmberTester minecraft:resistance 999999 4 true'); await wait(600);
  cmd('/effect give EmberTester minecraft:saturation 999999 0 true'); await wait(600);
  cmd('/tp @s 500 101 500'); await wait(2000);

  for (let i = 0; i < 9; i++) {
    await wait(20000);
    cmd('/effect give EmberTester minecraft:resistance 999999 4 true'); // keep it topped up
    log('CHECK ' + ((i + 1) * 20) + 's');
  }

  log('TEST COMPLETE');
  bot.quit();
  process.exit(0);
}

run().catch((e) => { log('FATAL: ' + e); process.exit(1); });
process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
