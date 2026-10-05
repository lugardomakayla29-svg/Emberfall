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

const SEL = '@e[type=emberfall:cinderbrand_reaver,limit=1]';

bot.on('spawn', () => {
  log('spawned dimension=' + bot.game.dimension);
  setTimeout(() => { log('gamemode creative'); bot.chat('/gamemode creative'); }, 500);
  setTimeout(() => { log('spawnelite'); bot.chat('/emberfall spawnelite cinderbrand_reaver'); }, 1500);

  // 50 base HP. Bring it down in steps, pausing between so we can watch it
  // actually act (cleave/lunge/plume) and not just insta-kill it, then check
  // the health readout after each hit and stop once Last Blaze should have
  // fired (<=25% = 12.5 HP).
  const damageSteps = [10, 10, 10, 10]; // 50 -> 40 -> 30 -> 20 -> 10 (10 HP = 20%, under the 25% threshold)
  let t = 3000;
  for (const dmg of damageSteps) {
    setTimeout(() => {
      log(`damaging reaver by ${dmg}`);
      bot.chat(`/damage ${SEL} ${dmg} minecraft:generic by @s`);
    }, t);
    t += 2500;
  }
  setTimeout(() => {
    log('querying reaver health via /data get');
    bot.chat(`/data get entity ${SEL} Health`);
  }, t + 500);
});

bot.on('health', () => log('bot health=' + bot.health));
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

setTimeout(() => {
  log('test window elapsed, disconnecting');
  bot.quit();
  process.exit(0);
}, 20000);

process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
