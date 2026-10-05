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

  console.log('--- Leave any existing run, start a clean fresh one ---');
  say('/emberfall leave EmberTester');
  await wait(500);
  say('/emberfall buildinfo EmberTester');
  await wait(500);
  say('/expedition');
  await wait(1500);
  say('/emberfall buildinfo EmberTester');
  await wait(500);

  console.log('--- Jump 3 levels in ONE command (one server tick): 0 -> 3 ---');
  say('/experience add EmberTester 3 levels');
  await wait(2000);

  console.log('--- 3 levels queued now, each drains one at a time via its own 8s grace window (fix: no more overwriting) ---');
  await wait(27000);

  console.log('--- Check final build: real bug-free behavior would show 3 Tomes granted (levels 1, 2, 3) ---');
  say('/emberfall buildinfo EmberTester');
  await wait(1500);

  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 60000);
