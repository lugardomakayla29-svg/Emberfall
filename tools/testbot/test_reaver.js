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

bot.on('spawn', () => {
  log('spawned at ' + JSON.stringify(bot.entity.position) + ' dimension=' + bot.game.dimension);
  setTimeout(() => {
    log('sending gamemode creative');
    bot.chat('/gamemode creative');
  }, 1000);
  setTimeout(() => {
    log('sending spawnelite command');
    bot.chat('/emberfall spawnelite cinderbrand_reaver');
  }, 3000);
});
bot.on('health', () => log('health=' + bot.health + ' food=' + bot.food));
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED (raw): ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

bot.on('entitySpawn', (entity) => {
  if (entity.name && (entity.name.includes('vindicator') || entity.username === 'Cinderbrand Reaver')) {
    log('ENTITY SPAWN: type=' + entity.name + ' kind=' + entity.kind + ' pos=' + JSON.stringify(entity.position));
  }
});

setInterval(() => {
  if (!bot.entity) return;
  // Look for nearby entities each tick and report anything vindicator-shaped.
  for (const id in bot.entities) {
    const e = bot.entities[id];
    if (e && e.name === 'vindicator') {
      log('nearby vindicator-type entity id=' + id + ' pos=' + JSON.stringify(e.position)
          + ' metadata=' + JSON.stringify(e.metadata ? e.metadata.slice(0, 6) : null));
    }
  }
}, 4000);

setTimeout(() => {
  log('test window elapsed, disconnecting');
  bot.quit();
  process.exit(0);
}, 45000);

process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
