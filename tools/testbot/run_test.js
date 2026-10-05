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
});
bot.on('health', () => log('health=' + bot.health + ' food=' + bot.food));
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('experience', () => log('XP: level=' + bot.experience.level + ' points=' + bot.experience.points + ' progress=' + bot.experience.progress));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED (raw): ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

setInterval(() => {
  if (!bot.entity) return;
  log('pos=' + JSON.stringify(bot.entity.position) + ' health=' + bot.health);
}, 5000);

process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
