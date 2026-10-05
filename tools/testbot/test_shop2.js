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

function chat(msg, delay) {
  return new Promise(resolve => setTimeout(() => { log('CMD: ' + msg); bot.chat(msg); resolve(); }, delay));
}

bot.on('message', (jsonMsg) => log('CHAT: ' + jsonMsg.toString()));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', () => log('disconnected'));

bot.once('spawn', async () => {
  log('spawned, current maxHealth attr: ' + JSON.stringify(bot.entity.attributes && bot.entity.attributes['minecraft:max_health']));

  await chat('/attribute EmberTester minecraft:max_health get', 1000);
  await chat('/emberfall givecurrency EmberTester 500', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500); // -> level 5/5
  await chat('/attribute EmberTester minecraft:max_health get', 1500);
  await chat('/emberfall upgrades EmberTester', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500); // should now fail: maxed

  setTimeout(() => { log('=== TEST COMPLETE ==='); process.exit(0); }, 3000);
});
