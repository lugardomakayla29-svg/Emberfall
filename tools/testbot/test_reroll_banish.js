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

  console.log('--- Check charges before buying anything (expect 0/0) ---');
  say('/emberfall tomecharges EmberTester');
  await wait(500);

  console.log('--- Fund currency and buy 2 reroll levels + 1 banish level ---');
  say('/emberfall givecurrency EmberTester 500');
  await wait(500);
  say('/emberfall buyupgrade EmberTester reroll_mastery');
  await wait(500);
  say('/emberfall buyupgrade EmberTester reroll_mastery');
  await wait(500);
  say('/emberfall buyupgrade EmberTester banishers_mark');
  await wait(500);
  say('/emberfall upgrades EmberTester');
  await wait(1000);

  console.log('--- Join a run so charges get seeded for the run (expect reroll=2 banish=1) ---');
  say('/expedition');
  await wait(1500);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Open a Tome Choice at level 2 ---');
  say('/emberfall tomeopen EmberTester 2');
  await wait(1000);

  console.log('--- Reroll it (charge 2->1) ---');
  say('/emberfall tomereroll EmberTester 2');
  await wait(1000);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Reroll again (charge 1->0) ---');
  say('/emberfall tomereroll EmberTester 2');
  await wait(1000);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Try a 3rd reroll with 0 charges left (expect rejection message, no crash) ---');
  say('/emberfall tomereroll EmberTester 2');
  await wait(1000);

  console.log('--- Banish granite_ward-ish? use a real tome id: fetch pool via granttome error to see valid ids - try banishing "vitality_charm" (guess) ---');
  say('/emberfall tomebanish EmberTester 2 vitality_charm');
  await wait(1000);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Try a 2nd banish with 0 charges left (expect rejection message) ---');
  say('/emberfall tomebanish EmberTester 2 vitality_charm');
  await wait(1000);

  console.log('--- Skip the offer entirely (expect free skip, no Tome granted) ---');
  say('/emberfall tomeskip EmberTester 2');
  await wait(1500);

  console.log('--- Test sequence complete, disconnecting ---');
  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 60000);
