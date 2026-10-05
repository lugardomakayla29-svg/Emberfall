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

let lastOffers = null;

bot.on('message', (jsonMsg) => {
  const text = jsonMsg.toString();
  console.log('[MSG] ' + text);
  const m = text.match(/current offers: \[(.*)\]/);
  if (m) {
    lastOffers = m[1].split(',').map(s => s.trim()).filter(s => s.length > 0);
  }
});

bot.on('kicked', (reason) => console.log('KICKED: ' + reason));
bot.on('error', (err) => console.log('ERROR: ' + err));

bot.once('spawn', async () => {
  console.log('Bot spawned');
  await wait(1000);

  say('/emberfall givecurrency EmberTester 200');
  await wait(500);
  say('/emberfall buyupgrade EmberTester banishers_mark');
  await wait(500);
  say('/expedition');
  await wait(1500);

  console.log('--- Open level 2 choice, read real offers ---');
  say('/emberfall tomeopen EmberTester 2');
  await wait(500);
  say('/emberfall tomeoffers EmberTester');
  await wait(1000);

  if (!lastOffers || lastOffers.length === 0) {
    console.log('FAIL: could not read offers');
    bot.quit();
    process.exit(1);
  }
  const target = lastOffers[0];
  console.log('--- Banishing a REAL current offer: ' + target + ' ---');
  say(`/emberfall tomebanish EmberTester 2 ${target}`);
  await wait(1000);
  say('/emberfall tomecharges EmberTester');
  await wait(1000);

  console.log('--- Skip this now-modified offer, then open several more choices and confirm banished tome never reappears ---');
  say('/emberfall tomeskip EmberTester 2');
  await wait(1000);

  let sawBanishedAgain = false;
  for (let level = 3; level <= 12; level++) {
    lastOffers = null;
    say(`/emberfall tomeopen EmberTester ${level}`);
    await wait(400);
    say(`/emberfall tomeoffers EmberTester`);
    await wait(700);
    if (lastOffers && lastOffers.includes(target)) {
      sawBanishedAgain = true;
      console.log(`FAIL: banished tome '${target}' reappeared at level ${level}: [${lastOffers.join(', ')}]`);
    }
    say(`/emberfall tomeskip EmberTester ${level}`);
    await wait(400);
  }

  console.log(sawBanishedAgain
    ? '--- RESULT: FAIL - banished tome reappeared ---'
    : '--- RESULT: PASS - banished tome never reappeared across 10 more offers ---');

  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 60000);
