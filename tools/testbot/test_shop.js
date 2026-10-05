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
  log('spawned at ' + JSON.stringify(bot.entity.position));

  await chat('/emberfall givecurrency EmberTester 1000', 1000);
  await chat('/emberfall balance EmberTester', 1500);

  // Weapon catalog + a purchase.
  await chat('/emberfall weapons EmberTester', 1500);
  await chat('/emberfall buyweapon EmberTester twin_daggers', 1500);
  await chat('/emberfall weapons EmberTester', 1500);
  await chat('/emberfall buyweapon EmberTester twin_daggers', 1500); // should now fail (already owned)

  // Upgrade catalog + purchases, including maxing one out to test the maxed-out rejection.
  await chat('/emberfall upgrades EmberTester', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500);
  await chat('/data get entity EmberTester Attributes', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500);
  await chat('/emberfall buyupgrade EmberTester vitality', 1500);
  await chat('/emberfall upgrades EmberTester', 1500); // should show vitality maxed 5/5
  await chat('/emberfall buyupgrade EmberTester vitality', 1500); // should now fail (maxed)
  await chat('/data get entity EmberTester Attributes', 1500);

  // Insufficient-funds path: drain to near zero then try a big buy.
  await chat('/emberfall balance EmberTester', 1500);
  await chat('/emberfall buyweapon EmberTester arcane_staff', 1500);
  await chat('/emberfall balance EmberTester', 1500);
  await chat('/emberfall buyupgrade EmberTester power', 1500); // likely insufficient funds now

  // Exercise the real player-facing /shop command + payload round-trip (no GUI to click,
  // but this proves the S2C OpenShopPayload encode/send doesn't throw server-side).
  await chat('/shop', 1500);

  setTimeout(() => { log('=== TEST COMPLETE ==='); process.exit(0); }, 3000);
});
