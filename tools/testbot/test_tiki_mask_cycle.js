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

  console.log('--- Clear the area of any stray item_displays from prior tests ---');
  say('/kill @e[type=item_display]');
  await wait(500);
  say('/kill @e[type=emberfall:tiki_segment]');
  await wait(500);
  say('/kill @e[type=emberfall:tiki_magma]');
  await wait(500);

  console.log('--- Spawn a plain fodder Tiki Magma (only topper masked, cycling) right in front of the bot ---');
  say('/emberfall spawnveteran tiki_magma');
  await wait(1500);

  for (let t = 0; t < 8; t++) {
    say('/data get entity @e[type=item_display,limit=1,sort=nearest] item');
    await wait(2600); // sample every ~2.6s, straddling the 2.5s cycle boundary
  }

  bot.quit();
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT - exiting');
  process.exit(1);
}, 30000);
