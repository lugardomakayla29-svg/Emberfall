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
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

function wait(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned at ' + JSON.stringify(bot.entity.position));

  cmd('/gamemode creative'); await wait(600);
  cmd('/tp @s 500 105 500'); await wait(1500);
  cmd('/fill 495 100 495 505 104 505 minecraft:stone'); await wait(800);
  cmd('/fill 496 101 496 504 104 504 minecraft:air'); await wait(800);
  cmd('/tp @s 500 101 500'); await wait(1000);

  cmd('/emberfall spawnelite bonecaller_necromancer'); await wait(1500);

  await wait(30000);
  log('CHECK 30s');

  await wait(30000);
  log('CHECK 60s');

  await wait(30000);
  log('CHECK 90s');

  await wait(20000);
  log('TEST COMPLETE');
  bot.quit();
  process.exit(0);
}

run().catch((e) => { log('FATAL: ' + e); process.exit(1); });
process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
