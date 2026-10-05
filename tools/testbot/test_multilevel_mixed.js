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
  await wait(1000);

  console.log('--- Fresh run, then jump straight from level 0 to level 6 in one command (crosses the level-5 weapon-offer boundary) ---');
  say('/emberfall leave EmberTester');
  await wait(500);
  say('/expedition');
  await wait(1500);
  say('/experience add EmberTester 6 levels');
  await wait(2000);

  console.log('--- Drain all 6 queued choices (levels 1-4,6 = Tome auto-resolve; level 5 = Weapon auto-resolve), ~8s apart ---');
  await wait(52000);

  console.log('--- Final state ---');
  say('/emberfall buildinfo EmberTester');
  await wait(1000);

  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 65000);
