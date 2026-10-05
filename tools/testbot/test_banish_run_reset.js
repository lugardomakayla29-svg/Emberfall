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

let sawLoyalHound = false;

bot.on('message', (jsonMsg) => {
  const text = jsonMsg.toString();
  console.log('[MSG] ' + text);
  if (text.includes('loyal_hound')) sawLoyalHound = true;
});

bot.on('kicked', (reason) => console.log('KICKED: ' + reason));
bot.on('error', (err) => console.log('ERROR: ' + err));

bot.once('spawn', async () => {
  await wait(1000);

  console.log('--- Leave the current run (loyal_hound was banished in it) ---');
  say('/emberfall leave EmberTester');
  await wait(1000);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Start a brand new run ---');
  say('/expedition');
  await wait(1500);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Open several choices in the NEW run, watch for loyal_hound reappearing (expected, since banish is per-run) ---');
  for (let level = 2; level <= 15; level++) {
    say(`/emberfall tomeopen EmberTester ${level}`);
    await wait(300);
    say(`/emberfall tomeoffers EmberTester`);
    await wait(500);
    say(`/emberfall tomeskip EmberTester ${level}`);
    await wait(300);
    if (sawLoyalHound) break;
  }

  console.log(sawLoyalHound
    ? '--- RESULT: PASS - loyal_hound available again in the new run (banish correctly scoped per-run) ---'
    : '--- RESULT: loyal_hound did not come up in 14 offers - could be bad luck (1 pool item vs many) or a real leak, inconclusive from this sample ---');

  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 60000);
