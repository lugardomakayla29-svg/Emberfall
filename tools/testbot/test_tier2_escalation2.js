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
  console.log('[MSG] ' + jsonMsg.toString());
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

  say('/emberfall boss 0');
  await wait(3000);

  say('/kill @e[type=emberfall:hydra_brain]');
  await wait(2000);

  say('/emberfall wavestatus 0');
  await wait(1000);

  console.log('--- Polling every 10s for automatic Devourer spawn ---');
  for (let i = 0; i < 12; i++) {
    await wait(10000);
    say('/emberfall wavestatus 0');
    await wait(1500);
  }

  console.log('--- Final check for Devourer brain entity ---');
  say('/kill @e[type=emberfall:devourer_brain]');
  await wait(2000);

  console.log('--- Test sequence complete, disconnecting ---');
  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 160000);
