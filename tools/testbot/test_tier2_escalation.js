const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25565,
  username: 'EmberTester',
  version: '1.21.11'
});

function say(cmd) {
  console.log('>> ' + cmd);
  bot.chat(cmd);
}

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

bot.on('message', (jsonMsg) => {
  const text = jsonMsg.toString();
  console.log('[MSG] ' + text);
});

bot.on('kicked', (reason) => console.log('KICKED: ' + reason));
bot.on('error', (err) => console.log('ERROR: ' + err));

bot.once('spawn', async () => {
  console.log('Bot spawned');
  await wait(1000);
  say('/expedition');
  await wait(1500);
  say('/gamemode creative');
  await wait(1000);

  // Find our slot via wavestatus scan (try slot 0 first, that's the norm for first run)
  say('/emberfall wavestatus 0');
  await wait(1000);

  console.log('--- Forcing Hydra spawn now ---');
  say('/emberfall boss 0');
  await wait(3000);

  console.log('--- Locating and killing the Hydra brain ---');
  say('/kill @e[type=emberfall:hydra_brain]');
  await wait(3000);

  console.log('--- Checking wave status after Hydra kill (expect tier=2) ---');
  say('/emberfall wavestatus 0');
  await wait(2000);

  console.log('--- Waiting 65s for automatic Devourer spawn in tier 2 ---');
  await wait(65000);

  say('/emberfall wavestatus 0');
  await wait(1000);
  console.log('--- Checking for Devourer brain entity ---');
  // list entities near player via /data get, simpler: try kill and see success message
  say('/kill @e[type=emberfall:devourer_brain]');
  await wait(3000);
  say('/emberfall wavestatus 0');
  await wait(2000);

  console.log('--- Test sequence complete, disconnecting ---');
  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 130000);
